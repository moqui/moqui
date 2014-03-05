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
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.ObjectAlreadyExistsException
import org.quartz.SchedulerConfigException
import org.quartz.SchedulerException
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.spi.ClassLoadHelper
import org.quartz.spi.JobStore
import org.quartz.spi.OperableTrigger
import org.quartz.spi.SchedulerSignaler
import org.quartz.spi.TriggerFiredResult

import javax.sql.rowset.serial.SerialBlob

class EntityJobStore implements JobStore {

    protected SchedulerSignaler schedulerSignaler
    protected ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()

    protected boolean schedulerRunning = false
    protected boolean shutdown = false

    protected String instanceId, instanceName

    static final String STATE_WAITING = "WAITING"
    static final String STATE_ACQUIRED = "ACQUIRED"
    static final String STATE_EXECUTING = "EXECUTING"
    static final String STATE_COMPLETE = "COMPLETE"
    static final String STATE_BLOCKED = "BLOCKED"
    static final String STATE_ERROR = "ERROR"
    static final String STATE_PAUSED = "PAUSED"
    static final String STATE_PAUSED_BLOCKED = "PAUSED_BLOCKED"
    static final String STATE_DELETED = "DELETED"
    static final String STATE_MISFIRED = "MISFIRED"

    static final String TTYPE_SIMPLE = "SIMPLE"
    static final String TTYPE_CRON = "CRON"
    static final String TTYPE_CAL_INT = "CAL_INT"
    static final String TTYPE_DAILY_TIME_INT = "DAILY_I"
    static final String TTYPE_BLOB = "BLOB"

    @Override
    void initialize(ClassLoadHelper classLoadHelper, SchedulerSignaler schedulerSignaler) throws SchedulerConfigException {
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
        storeTrigger(operableTrigger, false)
    }

    @Override
    void storeJob(JobDetail job, boolean replaceExisting) throws ObjectAlreadyExistsException, JobPersistenceException {
        Map jobMap = [schedName:instanceName, jobName:job.key.name, jobGroup:job.key.group,
                description:job.description, jobClassName:job.jobClass.name,
                isDurable:(job.isDurable() ? "T" : "F"),
                isNonconcurrent:(job.isConcurrentExectionDisallowed() ? "T" : "F"),
                isUpdateData:(job.isPersistJobDataAfterExecution() ? "T" : "F"),
                requestsRecovery:(job.requestsRecovery() ? "T" : "F")]
        if (checkExists(job.getKey())) {
            if (replaceExisting) {
                ecfi.serviceFacade.sync().name("update#moqui.service.quartz.QrtzJobDetails").parameters(jobMap).call()
            } else {
                throw new ObjectAlreadyExistsException(job)
            }
        } else {
            ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzJobDetails").parameters(jobMap).call()
        }
    }

    @Override
    void storeTrigger(OperableTrigger trigger, boolean replaceExisting) throws ObjectAlreadyExistsException, JobPersistenceException {
        String triggerState = "TODO"
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        if (trigger.jobDataMap != null) {
            ObjectOutputStream out = new ObjectOutputStream(baos)
            out.writeObject(trigger.jobDataMap)
            out.flush()
        }
        byte[] jobData = baos.toByteArray()

        Map triggerMap = [schedName:instanceName, triggerName:trigger.key.name, triggerGroup:trigger.key.group,
                jobName:trigger.jobKey.name, jobGroup:trigger.jobKey.group, description:trigger.description,
                nextFireTime:trigger.nextFireTime?.time, prevFireTime:trigger.previousFireTime?.time,
                priority:trigger.priority, triggerState:triggerState, triggerType:TTYPE_BLOB,
                startTime:trigger.startTime?.time, endTime:trigger.endTime?.time, calendarName:trigger.calendarName,
                misfireInstr:trigger.misfireInstruction, jobData:new SerialBlob(jobData)]

        if (checkExists(trigger.getKey())) {
            if (replaceExisting) {
                ecfi.serviceFacade.sync().name("update#moqui.service.quartz.QrtzTriggers").parameters(triggerMap).call()
            } else {
                throw new ObjectAlreadyExistsException(trigger)
            }
        } else {
            ecfi.serviceFacade.sync().name("create#moqui.service.quartz.QrtzTriggers").parameters(triggerMap).call()
        }
    }

    @Override
    void storeJobsAndTriggers(Map<JobDetail, Set<? extends Trigger>> jobDetailSetMap, boolean replaceExisting) throws ObjectAlreadyExistsException, JobPersistenceException {
        for(Map.Entry<JobDetail, Set<? extends Trigger>> e: jobDetailSetMap.entrySet()) {
            storeJob(e.getKey(), true)
            for(Trigger trigger: e.getValue()) storeTrigger((OperableTrigger) trigger, true)
        }
    }

    @Override
    boolean removeJob(JobKey jobKey) throws JobPersistenceException {
        return false
    }

    @Override
    boolean removeJobs(List<JobKey> jobKeys) throws JobPersistenceException {
        return false
    }

    @Override
    JobDetail retrieveJob(JobKey jobKey) throws JobPersistenceException {
        return null
    }

    @Override
    boolean removeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        return false
    }

    @Override
    boolean removeTriggers(List<TriggerKey> triggerKeys) throws JobPersistenceException {
        return false
    }

    @Override
    boolean replaceTrigger(TriggerKey triggerKey, OperableTrigger operableTrigger) throws JobPersistenceException {
        return false
    }

    @Override
    OperableTrigger retrieveTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        return null
    }

    @Override
    boolean checkExists(JobKey jobKey) throws JobPersistenceException {
        return ecfi.getEntityFacade().makeFind("moqui.service.quartz.QrtzJobDetails")
                .condition([schedName:instanceName, jobName:jobKey.name, jobGroup:jobKey.group]).one() != null
    }

    @Override
    boolean checkExists(TriggerKey triggerKey) throws JobPersistenceException {
        return ecfi.getEntityFacade().makeFind("moqui.service.quartz.QrtzTriggers")
                .condition([schedName:instanceName, triggerName:triggerKey.name, triggerGroup:triggerKey.group])
                .one() != null
    }

    @Override
    void clearAllSchedulingData() throws JobPersistenceException {

    }

    @Override
    void storeCalendar(String s, org.quartz.Calendar calendar, boolean b, boolean b2) throws ObjectAlreadyExistsException, JobPersistenceException {

    }

    @Override
    boolean removeCalendar(String s) throws JobPersistenceException {
        return false
    }

    @Override
    org.quartz.Calendar retrieveCalendar(String s) throws JobPersistenceException {
        return null
    }

    @Override
    int getNumberOfJobs() throws JobPersistenceException {
        return 0
    }

    @Override
    int getNumberOfTriggers() throws JobPersistenceException {
        return 0
    }

    @Override
    int getNumberOfCalendars() throws JobPersistenceException {
        return 0
    }

    @Override
    Set<JobKey> getJobKeys(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        return null
    }

    @Override
    Set<TriggerKey> getTriggerKeys(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        return null
    }

    @Override
    List<String> getJobGroupNames() throws JobPersistenceException {
        return null
    }

    @Override
    List<String> getTriggerGroupNames() throws JobPersistenceException {
        return null
    }

    @Override
    List<String> getCalendarNames() throws JobPersistenceException {
        return null
    }

    @Override
    List<OperableTrigger> getTriggersForJob(JobKey jobKey) throws JobPersistenceException {
        return null
    }

    @Override
    Trigger.TriggerState getTriggerState(TriggerKey triggerKey) throws JobPersistenceException {
        return null
    }

    @Override
    void pauseTrigger(TriggerKey triggerKey) throws JobPersistenceException {

    }

    @Override
    Collection<String> pauseTriggers(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        return null
    }

    @Override
    void pauseJob(JobKey jobKey) throws JobPersistenceException {

    }

    @Override
    Collection<String> pauseJobs(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        return null
    }

    @Override
    void resumeTrigger(TriggerKey triggerKey) throws JobPersistenceException {

    }

    @Override
    Collection<String> resumeTriggers(GroupMatcher<TriggerKey> triggerKeyGroupMatcher) throws JobPersistenceException {
        return null
    }

    @Override
    Set<String> getPausedTriggerGroups() throws JobPersistenceException {
        return null
    }

    @Override
    void resumeJob(JobKey jobKey) throws JobPersistenceException {

    }

    @Override
    Collection<String> resumeJobs(GroupMatcher<JobKey> jobKeyGroupMatcher) throws JobPersistenceException {
        return null
    }

    @Override
    void pauseAll() throws JobPersistenceException {

    }

    @Override
    void resumeAll() throws JobPersistenceException {

    }

    @Override
    List<OperableTrigger> acquireNextTriggers(long l, int i, long l2) throws JobPersistenceException {
        return null
    }

    @Override
    void releaseAcquiredTrigger(OperableTrigger operableTrigger) {

    }

    @Override
    List<TriggerFiredResult> triggersFired(List<OperableTrigger> operableTriggers) throws JobPersistenceException {
        return null
    }

    @Override
    void triggeredJobComplete(OperableTrigger operableTrigger, JobDetail jobDetail, Trigger.CompletedExecutionInstruction completedExecutionInstruction) {

    }

    @Override
    void setInstanceId(String s) { instanceId = s }
    @Override
    void setInstanceName(String s) { instanceName = s }
    @Override
    void setThreadPoolSize(int i) { }
}
