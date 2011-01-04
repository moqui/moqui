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

import org.moqui.service.ServiceFacade
import org.moqui.service.ServiceResultReceiver
import org.moqui.service.ServiceResultWaiter
import org.moqui.service.ServiceCallback
import org.moqui.impl.context.ExecutionContextFactoryImpl

import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobDataMap
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.matchers.NameMatcher

import org.quartz.ScheduleBuilder
import org.quartz.SimpleScheduleBuilder
import org.moqui.service.ServiceCallSync
import org.moqui.service.ServiceCallAsync
import org.moqui.service.ServiceCallSchedule
import org.moqui.service.ServiceCallSpecial

class ServiceFacadeImpl implements ServiceFacade {

    protected final ExecutionContextFactoryImpl ecfi

    protected static final Map<String, List<ServiceCallback>> callbackRegistry = new HashMap()

    protected Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler()

    public ServiceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi

        // init quartz scheduler
        scheduler.start()
        // TODO: load SECA rule tables
    }

    public void destroy() {
        // destroy quartz scheduler, after allowing currently executing jobs to complete
        scheduler.shutdown(true)
    }

    ServiceCallSync sync(String serviceName) {
        // TODO: implement this
        return null
    }

    ServiceCallAsync async(String serviceName) {
        // TODO: implement this
        return null
    }

    ServiceCallSchedule schedule(String serviceName) {
        // TODO: implement this
        return null
    }

    ServiceCallSpecial special(String serviceName) {
        // TODO: implement this
        return null
    }

    public void callAsync(String serviceName, Map<String, Object> context, ServiceResultReceiver resultReceiver, boolean persist,
                          Integer transactionIsolation) {
        // TODO: how to do transactionIsolation?
        // TODO: how to handle persist on a per-job bases? seems like the volatile Job concept matched this, but that is deprecated in 2.0

        // NOTE: is this the best way to get a unique job name? (needed to register a listener below)
        String uniqueJobName = UUID.randomUUID()
        // NOTE: don't store durably, ie tell it to get rid of it after it is run
        JobBuilder jobBuilder = JobBuilder.newJob(ServiceQuartzJob.class)
                .withIdentity(uniqueJobName, serviceName)
                .usingJobData(new JobDataMap(context))
                .requestRecovery().storeDurably(false)
        JobDetail job = jobBuilder.build()

        Trigger nowTrigger = TriggerBuilder.newTrigger()
                .withIdentity(uniqueJobName, "NowTrigger").startNow().withPriority(5)
                .forJob(job).build()

        if (resultReceiver) {
            ServiceRequesterListener sqjl = new ServiceRequesterListener(resultReceiver)
            // NOTE: is this the best way to get this to run for ONLY this job?
            scheduler.getListenerManager().addJobListener(sqjl, NameMatcher.matchNameEquals(uniqueJobName))
        }

        scheduler.scheduleJob(job, nowTrigger)
    }

    public ServiceResultWaiter callAsync(String serviceName, Map<String, Object> context, boolean persist,
                                         Integer transactionIsolation) {
        ServiceResultWaiter requester = new ServiceResultWaiter()
        this.callAsync(serviceName, context, requester, persist, transactionIsolation)
        return requester
    }

    public void schedule(String jobName, String poolName, String serviceName, Map<String, Object> context,
                         Long startTime, Integer frequency, Integer interval, Integer count, Long endTime,
                         Integer maxRetry) {
        // TODO poolName: any way to handle the pool concept? or get rid of that? multiple schedulers?
        // TODO maxRetry: any way to set and then track the number of retries?

        // NOTE: get existing job based on jobName/serviceName pair IFF a jobName is specified
        JobKey jk = JobKey.jobKey(jobName, serviceName)
        JobDetail job
        if (jobName && scheduler.checkExists(jk)) {
            job = scheduler.getJobDetail(jk)
        } else {
            JobBuilder jobBuilder = JobBuilder.newJob(ServiceQuartzJob.class)
                    .withIdentity(jobName, serviceName)
                    .requestRecovery().storeDurably(jobName ? true : false)
            job = jobBuilder.build()
        }

        // do we have to have an identity?: .withIdentity(TODO, "ScheduleTrigger")
        TriggerBuilder tb = TriggerBuilder.newTrigger()
                .withPriority(3)
                .usingJobData(new JobDataMap(context))
                .forJob(job)
        if (startTime) tb.startAt(new Date(startTime))
        if (endTime) tb.endAt(new Date(endTime))

        ScheduleBuilder sb = ScheduleBuilder
        SimpleScheduleBuilder ssb = SimpleScheduleBuilder.simpleSchedule()
        if (count) ssb.withRepeatCount(count)
        if (!count && !endTime) ssb.repeatForever()
        tb.withSchedule(ssb)
        Trigger trigger = tb.build()

        scheduler.scheduleJob(job, trigger)
    }

    /** @see org.moqui.service.ServiceFacade#registerCallback(String, ServiceCallback) */
    public synchronized void registerCallback(String serviceName, ServiceCallback serviceCallback) {
        List<ServiceCallback> callbackList = callbackRegistry.get(serviceName)
        if (callbackList == null) {
            callbackList = new ArrayList()
            callbackRegistry.put(serviceName, callbackList)
        }
        callbackList.add(serviceCallback)
    }
}
