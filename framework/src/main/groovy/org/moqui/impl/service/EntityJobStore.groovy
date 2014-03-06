/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.service

import org.moqui.Moqui
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityConditionFactory
import org.moqui.entity.EntityException
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.ObjectAlreadyExistsException
import org.quartz.Scheduler
import org.quartz.SchedulerConfigException
import org.quartz.SchedulerException
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.JobDetailImpl
import org.quartz.impl.jdbcjobstore.Constants
import org.quartz.impl.jdbcjobstore.FiredTriggerRecord
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.spi.ClassLoadHelper
import org.quartz.spi.JobStore
import org.quartz.spi.OperableTrigger
import org.quartz.spi.SchedulerSignaler
import org.quartz.spi.TriggerFiredBundle
import org.quartz.spi.TriggerFiredResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.sql.rowset.serial.SerialBlob

// NOTE: Implementing a Quartz JobStore is a HUGE PITA, Quartz puts a lot of scheduler and state handling logic in the
//     JobStore. The code here is based mostly on the JobStoreSupport class to replicate that logic.

class EntityJobStore implements JobStore {
    protected final static Logger logger = LoggerFactory.getLogger(EntityJobStore.class)

    protected ClassLoadHelper classLoadHelper
    protected SchedulerSignaler schedulerSignaler

    protected boolean schedulerRunning = false
    protected boolean shutdown = false

    protected String instanceId, instanceName
    private long misfireThreshold = 60000L; // one minute

    public long getMisfireThreshold() { return misfireThreshold }
    public void setMisfireThreshold(long misfireThreshold) {
        if (misfireThreshold < 1) throw new IllegalArgumentException("MisfireThreshold must be larger than 0")
        this.misfireThreshold = misfireThreshold;
    }
    protected long getMisfireTime() {
        long misfireTime = System.currentTimeMillis()
        if (getMisfireThreshold() > 0) misfireTime -= getMisfireThreshold()
        return misfireTime > 0 ? misfireTime : 0
    }

    ExecutionContextFactoryImpl getEcfi() {
        ExecutionContextFactoryImpl executionContextFactory = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()
        if (executionContextFactory == null) throw new IllegalStateException("ExecutionContextFactory not yet initialized")
        return executionContextFactory
    }

    @Override
    void initialize(ClassLoadHelper classLoadHelper, SchedulerSignaler schedulerSignaler) throws SchedulerConfigException {
        this.classLoadHelper = classLoadHelper
        this.schedulerSignaler = schedulerSignaler
    }

    @Override
    void schedulerStarted() throws SchedulerException {
        // TODO recover jobs

        schedulerRunning = true
    }

    @Override
    void schedulerPaused() { schedulerRunning = false }
    @Override
    void schedulerResumed() { schedulerRunning = true }
    @Override
    void shutdown() { shutdown = true }

    @Override
    boolean supportsPersistence() { return true }
    @Override
    long getEstimatedTimeToReleaseAndAcquireTrigger() { return 70 }
    @Override
    boolean isClustered() { return true }

    @Override
    void storeJobAndTrigger(JobDetail jobDetail, OperableTrigger operableTrigger) throws ObjectAlreadyExistsException, JobPersistenceException {
        storeJob(jobDetail, false)
        storeTrigger(operableTrigger, jobDetail, false, Constants.STATE_WAITING, false, false)
    }

    @Override
    void storeJob(JobDetail job, boolean replaceExisting) throws ObjectAlreadyExistsException, JobPersistenceException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        if (job.jobDataMap) {
            ObjectOutputStream out = new ObjectOutputStream(baos)
            out.writeObject(job.jobDataMap)
            out.flush()
        }
        byte[] jobData = baos.toByteArray()
        Map jobMap = [schedName:instanceName, jobName:job.key.name, jobGroup:job.key.group,
                description:job.description, jobClassName:job.jobClass.name,
                isDurable:(job.isDurable() ? "T" : "F"),
                isNonconcurrent:(job.isConcurrentExectionDisallowed() ? "T" : "F"),
                isUpdateData:(job.isPersistJobDataAfterExecution() ? "T" : "F"),
                requestsRecovery:(job.requestsRecovery() ? "T" : "F"), jobData:new SerialBlob(jobData)]
        if (checkExists(job.getKey())) {
            if (replaceExisting) {
                ecfi.serviceFacade.sync().name("update#moqui.service.quartz.QrtzJobDetails").parameters(jobMap).disableAuthz().call()
            } else {
                throw new ObjectAlreadyExistsException(job)
            }
        } else {
            ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzJobDetails").parameters(jobMap).disableAuthz().call()
        }
    }

    @Override
    void storeTrigger(OperableTrigger trigger, boolean replaceExisting) throws ObjectAlreadyExistsException, JobPersistenceException {
        storeTrigger(trigger, null, replaceExisting, Constants.STATE_WAITING, false, false)
    }
    protected void storeTrigger(OperableTrigger trigger, JobDetail job, boolean replaceExisting, String state,
                                boolean forceState, boolean recovering)
            throws ObjectAlreadyExistsException, JobPersistenceException {
        // TODO: handle shouldBePaused, job update (see JobStoreSupport:1184)
        boolean triggerExists = checkExists(trigger.getKey())
        if (triggerExists && !replaceExisting) throw new ObjectAlreadyExistsException(trigger)

        state = state ?: Constants.STATE_WAITING

        try {
            boolean shouldBePaused
            if (!forceState) {
                shouldBePaused = isTriggerGroupPaused(trigger.getKey().getGroup())
                if(!shouldBePaused) {
                    shouldBePaused = isTriggerGroupPaused(Constants.ALL_GROUPS_PAUSED)
                    if (shouldBePaused) insertPausedTriggerGroup(trigger.getKey().getGroup());
                }
                if (shouldBePaused && (state == Constants.STATE_WAITING || state == Constants.STATE_ACQUIRED))
                    state = Constants.STATE_PAUSED
            }

            if (job == null) job = retrieveJob(trigger.getJobKey())
            if (job == null) throw new JobPersistenceException("The job (${trigger.getJobKey()}) referenced by the trigger does not exist.")

            if (job.isConcurrentExectionDisallowed() && !recovering) state = checkBlockedState(job.getKey(), state)

            ByteArrayOutputStream baos = new ByteArrayOutputStream()
            if (trigger.jobDataMap) {
                ObjectOutputStream out = new ObjectOutputStream(baos)
                out.writeObject(trigger.jobDataMap)
                out.flush()
            }
            byte[] jobData = baos.toByteArray()
            Map triggerMap = [schedName:instanceName, triggerName:trigger.key.name, triggerGroup:trigger.key.group,
                    jobName:trigger.jobKey.name, jobGroup:trigger.jobKey.group, description:trigger.description,
                    nextFireTime:trigger.nextFireTime?.time, prevFireTime:trigger.previousFireTime?.time,
                    priority:trigger.priority, triggerState:state, triggerType:Constants.TTYPE_BLOB,
                    startTime:trigger.startTime?.time, endTime:trigger.endTime?.time, calendarName:trigger.calendarName,
                    misfireInstr:trigger.misfireInstruction, jobData:new SerialBlob(jobData)]

            ByteArrayOutputStream baosTrig = new ByteArrayOutputStream()
            if (trigger.jobDataMap != null) {
                ObjectOutputStream out = new ObjectOutputStream(baosTrig)
                out.writeObject(trigger)
                out.flush()
            }
            byte[] triggerData = baosTrig.toByteArray()
            Map triggerBlobMap = [schedName:instanceName, triggerName:trigger.key.name, triggerGroup:trigger.key.group,
                    blobData:new SerialBlob(triggerData)]

            if (triggerExists) {
                ecfi.serviceFacade.sync().name("update#moqui.service.quartz.QrtzTriggers").parameters(triggerMap)
                        .disableAuthz().call()
                // TODO handle TriggerPersistenceDelegate (for create and update)?
                // uses QrtzSimpleTriggers, QrtzCronTriggers, QrtzSimpropTriggers
                // TriggerPersistenceDelegate tDel = findTriggerPersistenceDelegate(trigger)
                // tDel.updateExtendedTriggerProperties(conn, trigger, state, jobDetail)
                ecfi.serviceFacade.sync().name("update#moqui.service.quartz.QrtzBlobTriggers").parameters(triggerBlobMap)
                        .disableAuthz().call()
            } else {
                ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzTriggers").parameters(triggerMap)
                        .disableAuthz().call()
                ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzBlobTriggers").parameters(triggerBlobMap)
                        .disableAuthz().call()
            }
        } catch (Exception e) {
            throw new JobPersistenceException("Couldn't store trigger '${trigger.getKey()}' for '${trigger.getJobKey()}' job: ${e.getMessage()}", e)
        }
    }

    protected String checkBlockedState(JobKey jobKey, String currentState) throws JobPersistenceException {
        // State can only transition to BLOCKED from PAUSED or WAITING.
        if (currentState != Constants.STATE_WAITING && currentState != Constants.STATE_PAUSED) return currentState

        List<FiredTriggerRecord> lst = selectFiredTriggerRecordsByJob(jobKey.getName(), jobKey.getGroup())
        if (lst.size() > 0) {
            FiredTriggerRecord rec = lst.get(0)
            if (rec.isJobDisallowsConcurrentExecution()) { // OLD_TODO: worry about failed/recovering/volatile job  states?
                return (Constants.STATE_PAUSED == currentState) ? Constants.STATE_PAUSED_BLOCKED : Constants.STATE_BLOCKED
            }
        }

        return currentState;
    }
    protected List<FiredTriggerRecord> selectFiredTriggerRecordsByJob(String jobName, String jobGroup) {
        List<FiredTriggerRecord> lst = new LinkedList<FiredTriggerRecord>()

        Map ftMap = [schedName:instanceName, jobGroup:jobGroup]
        if (jobName) ftMap.put("jobName", jobName)
        EntityList qftList = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzFiredTriggers").condition(ftMap).disableAuthz().list()

        for (EntityValue qft in qftList) {
            FiredTriggerRecord rec = new FiredTriggerRecord()
            rec.setFireInstanceId((String) qft.entryId)
            rec.setFireInstanceState((String) qft.state)
            rec.setFireTimestamp((long) qft.firedTime)
            rec.setScheduleTimestamp((long) qft.schedTime)
            rec.setPriority((int) qft.priority)
            rec.setSchedulerInstanceId((String) qft.instanceName)
            rec.setTriggerKey(new TriggerKey((String) qft.triggerName, (String) qft.triggerGroup))
            if (!rec.getFireInstanceState().equals(Constants.STATE_ACQUIRED)) {
                rec.setJobDisallowsConcurrentExecution(qft.isNonconcurrent == "T")
                rec.setJobRequestsRecovery(qft.requestsRecovery == "T")
                rec.setJobKey(new JobKey((String) qft.jobName, (String) qft.jobGroup))
            }
            lst.add(rec);
        }

        return lst
    }

    protected boolean isTriggerGroupPaused(String triggerGroup) {
        return ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzPausedTriggerGrps")
                .condition([schedName:instanceName, triggerGroup:triggerGroup]).disableAuthz().count() > 0
    }
    protected void insertPausedTriggerGroup(String triggerGroup) {
        ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzPausedTriggerGrps")
                .parameters([schedName:instanceName, triggerGroup:triggerGroup]).disableAuthz().call()
    }

    @Override
    void storeJobsAndTriggers(Map<JobDetail, Set<? extends Trigger>> jobDetailSetMap, boolean replaceExisting) throws ObjectAlreadyExistsException, JobPersistenceException {
        for (Map.Entry<JobDetail, Set<? extends Trigger>> e: jobDetailSetMap.entrySet()) {
            storeJob(e.getKey(), replaceExisting)
            for (Trigger trigger: e.getValue()) storeTrigger((OperableTrigger) trigger, replaceExisting)
        }
    }

    @Override
    boolean removeJob(JobKey jobKey) throws JobPersistenceException {
        Map jobMap = [schedName:instanceName, jobName:jobKey.name, jobGroup:jobKey.group]
        // remove all job triggers
        ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers").condition(jobMap).disableAuthz().deleteAll()
        // remove job
        return ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzJobDetails").condition(jobMap).disableAuthz().deleteAll() as boolean
    }

    @Override
    boolean removeJobs(List<JobKey> jobKeys) throws JobPersistenceException {
        boolean allFound = true
        for (JobKey jobKey in jobKeys) allFound = removeJob(jobKey) && allFound
        return allFound
    }

    @Override
    JobDetail retrieveJob(JobKey jobKey) throws JobPersistenceException {
        try {
            Map jobMap = [schedName:instanceName, jobName:jobKey.name, jobGroup:jobKey.group]
            EntityValue jobValue = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzJobDetails").condition(jobMap).disableAuthz().one()
            JobDetailImpl job = new JobDetailImpl()

            job.setName((String) jobValue.jobName)
            job.setGroup((String) jobValue.jobGroup)
            job.setDescription((String) jobValue.description)
            job.setJobClass(classLoadHelper.loadClass((String) jobValue.jobClassName, Job.class));
            job.setDurability(jobValue.isDurable == "T")
            job.setRequestsRecovery(jobValue.requestsRecovery == "T")
            // TODO: StdJDBCDelegate doesn't set these, but do we need them? isNonconcurrent, isUpdateData

            ObjectInputStream ois = new ObjectInputStream(jobValue.getSerialBlob("jobData").binaryStream)
            Map jobDataMap
            try { jobDataMap = (Map) ois.readObject() } finally { ois.close() }
            // TODO: need this? if (canUseProperties()) map = getMapFromProperties(rs);
            if (jobDataMap) job.setJobDataMap(new JobDataMap(jobDataMap))

            return job
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException("Couldn't retrieve job because a required class was not found: ${e.getMessage()}", e)
        } catch (IOException e) {
            throw new JobPersistenceException("Couldn't retrieve job because the BLOB couldn't be deserialized: ${e.getMessage()}", e)
        } catch (EntityException e) {
            throw new JobPersistenceException("Couldn't retrieve job: ${e.getMessage()}", e)
        }
    }

    @Override
    boolean removeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        // delete the trigger
        Map triggerMap = [schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group]
        EntityValue triggerValue = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers").condition(triggerMap).disableAuthz().one()
        if (triggerValue == null) return false
        triggerValue.delete()

        // if there are no other triggers for the job, delete the job
        Map jobMap = [schedName:instanceName, jobName:triggerValue.jobName, jobGroup:triggerValue.jobGroup]
        EntityList jobTriggers = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers").condition(jobMap).disableAuthz().list()
        if (!jobTriggers) ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzJobDetails").condition(jobMap).disableAuthz().deleteAll()

        return true
    }

    @Override
    boolean removeTriggers(List<TriggerKey> triggerKeys) throws JobPersistenceException {
        boolean allFound = true
        for (TriggerKey triggerKey in triggerKeys) allFound = removeTrigger(triggerKey) && allFound
        return allFound
    }

    @Override
    boolean replaceTrigger(TriggerKey triggerKey, OperableTrigger operableTrigger) throws JobPersistenceException {
        // get the existing trigger and job
        Map triggerMap = [schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group]
        EntityValue triggerValue = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers").condition(triggerMap).disableAuthz().one()
        if (triggerValue == null) return false
        JobDetail job = retrieveJob(new JobKey((String) triggerValue.jobName, (String) triggerValue.jobGroup))

        // delete the old trigger
        removeTrigger(triggerKey)

        // create the new one
        storeTrigger(operableTrigger, job, false, Constants.STATE_WAITING, false, false)

        return true
    }

    @Override
    OperableTrigger retrieveTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        Map triggerMap = [schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group]
        EntityValue triggerValue = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers").condition(triggerMap).disableAuthz().one()
        if (triggerValue == null) return null

        if (triggerValue.triggerType != Constants.TTYPE_BLOB)
            throw new JobPersistenceException("Trigger ${triggerValue.triggerName}:${triggerValue.triggerGroup} with type ${triggerValue.triggerType} cannot be retrieved, only blob type triggers currently supported.")

        EntityValue blobTriggerValue = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzBlobTriggers").condition(triggerMap).disableAuthz().one()
        if (!blobTriggerValue) throw new JobPersistenceException("Count not find trigger ${triggerValue.triggerName}:${triggerValue.triggerGroup}")

        OperableTrigger trigger = null
        ObjectInputStream ois = new ObjectInputStream(blobTriggerValue.getSerialBlob("blobData").binaryStream)
        try { trigger = (OperableTrigger) ois.readObject() } finally { ois.close() }
        return trigger
    }

    @Override
    boolean checkExists(JobKey jobKey) throws JobPersistenceException {
        return ecfi.getEntityFacade().makeFind("moqui.service.quartz.QrtzJobDetails")
                .condition([schedName:instanceName, jobName:jobKey.name, jobGroup:jobKey.group]).disableAuthz().count() > 0
    }

    @Override
    boolean checkExists(TriggerKey triggerKey) throws JobPersistenceException {
        return ecfi.getEntityFacade().makeFind("moqui.service.quartz.QrtzTriggers")
                .condition([schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group])
                .disableAuthz().count() > 0
    }

    @Override
    void clearAllSchedulingData() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void storeCalendar(String s, org.quartz.Calendar calendar, boolean b, boolean b2) throws ObjectAlreadyExistsException, JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    boolean removeCalendar(String s) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    org.quartz.Calendar retrieveCalendar(String s) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    int getNumberOfJobs() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    int getNumberOfTriggers() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    int getNumberOfCalendars() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Set<JobKey> getJobKeys(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Set<TriggerKey> getTriggerKeys(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    List<String> getJobGroupNames() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    List<String> getTriggerGroupNames() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    List<String> getCalendarNames() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    List<OperableTrigger> getTriggersForJob(JobKey jobKey) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Trigger.TriggerState getTriggerState(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void pauseTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Collection<String> pauseTriggers(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void pauseJob(JobKey jobKey) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Collection<String> pauseJobs(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void resumeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Collection<String> resumeTriggers(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Set<String> getPausedTriggerGroups() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void resumeJob(JobKey jobKey) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    Collection<String> resumeJobs(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void pauseAll() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void resumeAll() throws JobPersistenceException {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    List<OperableTrigger> acquireNextTriggers(final long noLaterThan, final int maxCount, final long timeWindow) throws JobPersistenceException {
        // this will happen during init because Quartz is initialized before ECFI init is final, so don't blow up
        try { getEcfi() } catch (Exception e) { return new ArrayList<OperableTrigger>() }

        boolean beganTransaction = ecfi.transactionFacade.begin(0)
        try {
            return acquireNextTriggersInternal(noLaterThan, maxCount, timeWindow)
        } catch (Throwable t) {
            ecfi.transactionFacade.rollback(beganTransaction, "Error in acquireNextTriggers", t)
            throw t
        } finally {
            if (ecfi.transactionFacade.isTransactionInPlace()) ecfi.transactionFacade.commit(beganTransaction)
        }
    }
    protected List<OperableTrigger> acquireNextTriggersInternal(final long noLaterThan, final int maxCount, final long timeWindow) throws JobPersistenceException {
        if (timeWindow < 0) throw new IllegalArgumentException("timeWindow cannot be less than 0")

        List<OperableTrigger> acquiredTriggers = new ArrayList<OperableTrigger>()

        Set<JobKey> acquiredJobKeysForNoConcurrentExec = new HashSet<JobKey>()
        final int MAX_DO_LOOP_RETRY = 3
        int currentLoopCount = 0
        long firstAcquiredTriggerFireTime = 0

        while (true) {
            currentLoopCount ++
            try {
                List<TriggerKey> keys = selectTriggerToAcquire(noLaterThan + timeWindow, getMisfireTime(), maxCount)

                // No trigger is ready to fire yet.
                if (keys == null || keys.size() == 0) return acquiredTriggers

                for (TriggerKey triggerKey in keys) {
                    // If our trigger is no longer available, try a new one.
                    OperableTrigger nextTrigger = retrieveTrigger(triggerKey)
                    if (nextTrigger == null) continue // next trigger

                    // If trigger's job is set as @DisallowConcurrentExecution, and it has already been added to result, then
                    // put it back into the timeTriggers set and continue to search for next trigger.
                    JobKey jobKey = nextTrigger.getJobKey()
                    JobDetail job = retrieveJob(jobKey)
                    if (job.isConcurrentExectionDisallowed()) {
                        if (acquiredJobKeysForNoConcurrentExec.contains(jobKey)) {
                            continue // next trigger
                        } else {
                            acquiredJobKeysForNoConcurrentExec.add(jobKey)
                        }
                    }

                    // We now have a acquired trigger, let's add to return list.
                    // If our trigger was no longer in the expected state, try a new one.
                    int rowsUpdated = updateTriggerStateFromOtherState(triggerKey, Constants.STATE_ACQUIRED, Constants.STATE_WAITING)
                    if (rowsUpdated <= 0) continue // next trigger

                    nextTrigger.setFireInstanceId(getFiredTriggerRecordId())

                    ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzFiredTriggers")
                            .parameters([schedName:instanceName, entryId:nextTrigger.getFireInstanceId(),
                                triggerName:nextTrigger.key.name, triggerGroup:nextTrigger.key.group,
                                instanceName:instanceId, firedTime:System.currentTimeMillis(),
                                schedTime:nextTrigger.getNextFireTime().getTime(), priority:nextTrigger.priority,
                                state:Constants.STATE_ACQUIRED, jobName:nextTrigger.jobKey?.name,
                                jobGroup:nextTrigger.jobKey?.group, isNonconcurrent:"F", requestsRecovery:"F"])
                            .disableAuthz().call()

                    acquiredTriggers.add(nextTrigger)
                    if (firstAcquiredTriggerFireTime == 0) firstAcquiredTriggerFireTime = nextTrigger.getNextFireTime().getTime()
                }

                // if we didn't end up with any trigger to fire from that first
                // batch, try again for another batch. We allow with a max retry count.
                if (acquiredTriggers.size() != 0 || currentLoopCount >= MAX_DO_LOOP_RETRY) break
            } catch (Exception e) {
                throw new JobPersistenceException("Couldn't acquire next trigger: " + e.getMessage(), e)
            }
        }

        // Return the acquired trigger list
        return acquiredTriggers
    }

    /**
     * @param noLaterThan highest value of <code>getNextFireTime()</code> of the triggers (exclusive)
     * @param noEarlierThan highest value of <code>getNextFireTime()</code> of the triggers (inclusive)
     * @param maxCount maximum number of trigger keys allow to acquired in the returning list.
     */
    protected List<TriggerKey> selectTriggerToAcquire(long noLaterThan, long noEarlierThan, int maxCount) {
        EntityConditionFactory ecf = ecfi.entityFacade.getConditionFactory()
        List<TriggerKey> nextTriggers = new LinkedList<TriggerKey>()

        EntityList triggerList = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers")
                .selectFields(['triggerName', 'triggerGroup', 'nextFireTime', 'priority'])
                .condition([schedName:instanceName, triggerState:Constants.STATE_WAITING])
                .condition(ecf.makeCondition("nextFireTime", EntityCondition.LESS_THAN_EQUAL_TO, noLaterThan))
                .condition(ecf.makeCondition(ecf.makeCondition("misfireInstr", EntityCondition.EQUALS, -1), EntityCondition.OR,
                    ecf.makeCondition(ecf.makeCondition("misfireInstr", EntityCondition.NOT_EQUAL, -1), EntityCondition.AND,
                            ecf.makeCondition("nextFireTime", EntityCondition.GREATER_THAN_EQUAL_TO, noEarlierThan))))
                .orderBy(['nextFireTime', '-priority'])
                .maxRows(maxCount).fetchSize(maxCount).disableAuthz().list()

        for (EntityValue triggerValue in triggerList) {
            if (nextTriggers.size() >= maxCount) break
            nextTriggers.add(new TriggerKey((String) triggerValue.triggerName, (String) triggerValue.triggerGroup))
        }

        return nextTriggers
    }

    protected static long ftrCtr = System.currentTimeMillis()
    protected synchronized String getFiredTriggerRecordId() { return instanceId + ftrCtr++ }

    protected int updateTriggerState(TriggerKey triggerKey, String state) {
        return ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers")
                .condition([schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group])
                .disableAuthz().updateAll([triggerState:state])
    }
    protected int updateTriggerStateFromOtherState(TriggerKey triggerKey, String newState, String oldState) {
        return ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers")
                .condition([schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group, triggerState:oldState])
                .disableAuthz().updateAll([triggerState:newState])
    }
    protected int updateTriggerStatesForJobFromOtherState(JobKey jobKey, String newState, String oldState) {
        return ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers")
                .condition([schedName:instanceName, jobName:jobKey.name, jobGroup:jobKey.group, triggerState:oldState])
                .disableAuthz().updateAll([triggerState:newState])
    }


    @Override
    void releaseAcquiredTrigger(OperableTrigger operableTrigger) {
        boolean beganTransaction = ecfi.transactionFacade.begin(0)
        try {
            updateTriggerStateFromOtherState(operableTrigger.getKey(), Constants.STATE_WAITING, Constants.STATE_ACQUIRED)
            deleteFiredTrigger(operableTrigger.getFireInstanceId());
        } catch (Throwable t) {
            ecfi.transactionFacade.rollback(beganTransaction, "Error in releaseAcquiredTrigger", t)
            throw t
        } finally {
            if (ecfi.transactionFacade.isTransactionInPlace()) ecfi.transactionFacade.commit(beganTransaction)
        }
    }
    protected int deleteFiredTrigger(String entryId) {
        return ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzFiredTriggers")
                .condition([schedName:instanceName, entryId:entryId]).disableAuthz().deleteAll()
    }

    @Override
    List<TriggerFiredResult> triggersFired(List<OperableTrigger> operableTriggers) throws JobPersistenceException {
        List<TriggerFiredResult> results = new ArrayList<TriggerFiredResult>()

        boolean beganTransaction = ecfi.transactionFacade.begin(0)
        try {
            TriggerFiredResult result
            for (OperableTrigger trigger : operableTriggers) {
                try {
                    TriggerFiredBundle bundle = triggerFired(trigger)
                    result = new TriggerFiredResult(bundle)
                } catch (JobPersistenceException jpe) {
                    result = new TriggerFiredResult(jpe)
                } catch(RuntimeException re) {
                    result = new TriggerFiredResult(re)
                }
                results.add(result)
            }
        } catch (Throwable t) {
            ecfi.transactionFacade.rollback(beganTransaction, "Error in triggersFired", t)
            throw t
        } finally {
            if (ecfi.transactionFacade.isTransactionInPlace()) ecfi.transactionFacade.commit(beganTransaction)
        }

        return results
    }
    protected TriggerFiredBundle triggerFired(OperableTrigger trigger) throws JobPersistenceException {
        JobDetail job
        org.quartz.Calendar cal = null

        // Make sure trigger wasn't deleted, paused, or completed...
        // if trigger was deleted, state will be STATE_DELETED
        String state = selectTriggerState(trigger.getKey())
        if (!state.equals(Constants.STATE_ACQUIRED)) return null

        try {
            job = retrieveJob(trigger.getJobKey())
            if (job == null) return null
        } catch (JobPersistenceException jpe) {
            try {
                logger.error("Error retrieving job, setting trigger state to ERROR.", jpe)
                updateTriggerState(trigger.getKey(), Constants.STATE_ERROR)
            } catch (Exception sqle) {
                logger.error("Unable to set trigger state to ERROR.", sqle)
            }
            throw jpe
        }

        if (trigger.getCalendarName() != null) {
            cal = retrieveCalendar(trigger.getCalendarName())
            if (cal == null) return null
        }

        ecfi.serviceFacade.sync().name("update#moqui.service.quartz.QrtzFiredTriggers")
                .parameters([schedName:instanceName, entryId:trigger.getFireInstanceId(),
                    triggerName:trigger.key.name, triggerGroup:trigger.key.group,
                    instanceName:instanceId, firedTime:System.currentTimeMillis(),
                    schedTime:trigger.getNextFireTime().getTime(), priority:trigger.priority,
                    state:Constants.STATE_EXECUTING, jobName:job.key.name,
                    jobGroup:job.key.group, isNonconcurrent:(job.isConcurrentExectionDisallowed() ? "T" : "F"),
                    requestsRecovery:(job.requestsRecovery() ? "T" : "F")])
                .disableAuthz().call()

        Date prevFireTime = trigger.getPreviousFireTime()

        // call triggered - to update the trigger's next-fire-time state...
        trigger.triggered(cal)

        state = Constants.STATE_WAITING
        boolean force = true

        if (job.isConcurrentExectionDisallowed()) {
            state = Constants.STATE_BLOCKED;
            force = false;
            updateTriggerStatesForJobFromOtherState(job.getKey(), Constants.STATE_BLOCKED, Constants.STATE_WAITING)
            updateTriggerStatesForJobFromOtherState(job.getKey(), Constants.STATE_BLOCKED, Constants.STATE_ACQUIRED)
            updateTriggerStatesForJobFromOtherState(job.getKey(), Constants.STATE_PAUSED_BLOCKED, Constants.STATE_PAUSED)
        }

        if (trigger.getNextFireTime() == null) {
            state = Constants.STATE_COMPLETE
            force = true
        }

        storeTrigger(trigger, job, true, state, force, false)

        job.getJobDataMap().clearDirtyFlag()

        return new TriggerFiredBundle(job, trigger, cal, trigger.getKey().getGroup()
                .equals(Scheduler.DEFAULT_RECOVERY_GROUP), new Date(), trigger
                .getPreviousFireTime(), prevFireTime, trigger.getNextFireTime())
    }
    protected String selectTriggerState(TriggerKey triggerKey) {
        Map triggerMap = [schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group]
        EntityValue triggerValue = ecfi.entityFacade.makeFind("moqui.service.quartz.QrtzTriggers").condition(triggerMap).disableAuthz().one()
        if (!triggerValue) return Constants.STATE_DELETED
        return triggerValue.triggerState
    }

    @Override
    void triggeredJobComplete(OperableTrigger operableTrigger, JobDetail jobDetail, Trigger.CompletedExecutionInstruction completedExecutionInstruction) {
        // TODO
        logger.warn("Not yet implemented", new Exception())
        throw new IllegalStateException("Not yet implemented")
    }

    @Override
    void setInstanceId(String s) { instanceId = s }
    @Override
    void setInstanceName(String s) { instanceName = s }
    @Override
    void setThreadPoolSize(int i) { }
}
