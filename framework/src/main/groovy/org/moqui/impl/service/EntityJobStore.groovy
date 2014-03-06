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
import org.quartz.spi.TriggerFiredResult

import javax.sql.rowset.serial.SerialBlob

class EntityJobStore implements JobStore {

    protected ClassLoadHelper classLoadHelper
    protected SchedulerSignaler schedulerSignaler
    protected ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()

    protected boolean schedulerRunning = false
    protected boolean shutdown = false

    protected String instanceId, instanceName

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
        // TODO
        return false
    }

    @Override
    boolean removeTriggers(List<TriggerKey> triggerKeys) throws JobPersistenceException {
        // TODO
        return false
    }

    @Override
    boolean replaceTrigger(TriggerKey triggerKey, OperableTrigger operableTrigger) throws JobPersistenceException {
        // TODO
        return false
    }

    @Override
    OperableTrigger retrieveTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
        return null
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
    }

    @Override
    void storeCalendar(String s, org.quartz.Calendar calendar, boolean b, boolean b2) throws ObjectAlreadyExistsException, JobPersistenceException {
        // TODO
    }

    @Override
    boolean removeCalendar(String s) throws JobPersistenceException {
        // TODO
        return false
    }

    @Override
    org.quartz.Calendar retrieveCalendar(String s) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    int getNumberOfJobs() throws JobPersistenceException {
        // TODO
        return 0
    }

    @Override
    int getNumberOfTriggers() throws JobPersistenceException {
        // TODO
        return 0
    }

    @Override
    int getNumberOfCalendars() throws JobPersistenceException {
        // TODO
        return 0
    }

    @Override
    Set<JobKey> getJobKeys(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    Set<TriggerKey> getTriggerKeys(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    List<String> getJobGroupNames() throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    List<String> getTriggerGroupNames() throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    List<String> getCalendarNames() throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    List<OperableTrigger> getTriggersForJob(JobKey jobKey) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    Trigger.TriggerState getTriggerState(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void pauseTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
    }

    @Override
    Collection<String> pauseTriggers(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void pauseJob(JobKey jobKey) throws JobPersistenceException {
        // TODO
    }

    @Override
    Collection<String> pauseJobs(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void resumeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        // TODO
    }

    @Override
    Collection<String> resumeTriggers(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    Set<String> getPausedTriggerGroups() throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void resumeJob(JobKey jobKey) throws JobPersistenceException {
        // TODO
    }

    @Override
    Collection<String> resumeJobs(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void pauseAll() throws JobPersistenceException {
        // TODO
    }

    @Override
    void resumeAll() throws JobPersistenceException {
        // TODO
    }

    @Override
    List<OperableTrigger> acquireNextTriggers(long l, int i, long l2) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void releaseAcquiredTrigger(OperableTrigger operableTrigger) {
        // TODO
    }

    @Override
    List<TriggerFiredResult> triggersFired(List<OperableTrigger> operableTriggers) throws JobPersistenceException {
        // TODO
        return null
    }

    @Override
    void triggeredJobComplete(OperableTrigger operableTrigger, JobDetail jobDetail, Trigger.CompletedExecutionInstruction completedExecutionInstruction) {
        // TODO
    }

    @Override
    void setInstanceId(String s) { instanceId = s }
    @Override
    void setInstanceName(String s) { instanceName = s }
    @Override
    void setThreadPoolSize(int i) { }
}
