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

import org.moqui.service.ServiceCall.TimeUnit
import org.moqui.service.ServiceCallSchedule

import org.quartz.Trigger
import org.quartz.SimpleScheduleBuilder
import org.quartz.JobDataMap
import org.quartz.TriggerBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.CalendarIntervalScheduleBuilder
import org.quartz.DateBuilder.IntervalUnit
import org.quartz.CronScheduleBuilder
import org.moqui.impl.context.ExecutionContextImpl

class ServiceCallScheduleImpl extends ServiceCallImpl implements ServiceCallSchedule {
    protected String jobName = null
    /* leaving this out for now, not easily supported by Quartz Scheduler: protected String poolName = null */
    protected Long startTime = null
    protected Integer count = null
    protected Long endTime = null
    protected Integer interval = null
    protected TimeUnit intervalUnit = null
    protected String cronString = null
    protected int maxRetry = 1

    ServiceCallScheduleImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallSchedule name(String serviceName) { this.setServiceName(serviceName); return this }

    @Override
    ServiceCallSchedule name(String v, String n) { path = null; verb = v; noun = n; return this }

    @Override
    ServiceCallSchedule name(String p, String v, String n) { path = p; verb = v; noun = n; return this }

    @Override
    ServiceCallSchedule parameters(Map<String, Object> map) { parameters.putAll(map); return this }

    @Override
    ServiceCallSchedule parameter(String name, Object value) { parameters.put(name, value); return this }

    @Override
    ServiceCallSchedule jobName(String jn) { jobName = jn; return this }

    /* leaving this out for now, not easily supported by Quartz Scheduler:
    @Override
    ServiceCallSchedule poolName(String pn) { poolName = pn; return this }
    */

    @Override
    ServiceCallSchedule startTime(long st) { startTime = st; return this }

    @Override
    ServiceCallSchedule count(int c) { count = c; return this }

    @Override
    ServiceCallSchedule endTime(long et) { endTime = et; return this }

    @Override
    ServiceCallSchedule interval(int i, TimeUnit iu) { interval = i; intervalUnit = iu; return this }

    @Override
    ServiceCallSchedule cron(String cs) { cronString = cs; return this }

    @Override
    ServiceCallSchedule maxRetry(int mr) { maxRetry = mr; return this }

    @Override
    void call() {
        // TODO maxRetry: any way to set and then track the number of retries?

        // Before scheduling the service check a few basic things so they show up sooner than later:
        ServiceDefinition sd = sfi.getServiceDefinition(getServiceName())
        if (sd == null && !((verb == "create" || verb == "update" || verb == "delete") && sfi.ecfi.entityFacade.getEntityDefinition(noun) != null)) {
            throw new IllegalArgumentException("Could not find service with name [${getServiceName()}]")
        }
        if (sd != null) {
            String serviceType = sd.serviceNode."@type" ?: "inline"
            if (serviceType == "interface") throw new IllegalArgumentException("Cannot run interface service [${getServiceName()}]")
            ServiceRunner sr = sfi.getServiceRunner(serviceType)
            if (sr == null) throw new IllegalArgumentException("Could not find service runner for type [${serviceType}] for service [${getServiceName()}]")
            // validation
            ExecutionContextImpl eci = (ExecutionContextImpl) sfi.ecfi.executionContext
            sd.convertValidateCleanParameters(this.parameters, eci)
            // if error(s) in parameters, return now with no results
            if (eci.message.errors) return
        }

        // NOTE: get existing job based on jobName/serviceName pair IFF a jobName is specified
        JobKey jk = JobKey.jobKey(jobName, serviceName)
        JobDetail job
        if (jobName && sfi.scheduler.checkExists(jk)) {
            job = sfi.scheduler.getJobDetail(jk)
        } else {
            JobBuilder jobBuilder = JobBuilder.newJob(ServiceQuartzJob.class)
                    .withIdentity(jobName, serviceName)
                    .requestRecovery().storeDurably(jobName ? true : false)
            job = jobBuilder.build()
        }

        // do we have to have an identity?: .withIdentity(..., "ScheduleTrigger")
        TriggerBuilder tb = TriggerBuilder.newTrigger()
                .withPriority(3)
                .usingJobData(new JobDataMap(parameters))
                .forJob(job)
        if (startTime) tb.startAt(new Date(startTime))
        if (endTime) tb.endAt(new Date(endTime))

        // NOTE: this allows combinations of different schedules, which may not be allowed...
        if (interval != null) {
            IntervalUnit qiu = IntervalUnit.HOUR
            switch (intervalUnit) {
                case TimeUnit.SECONDS: qiu = IntervalUnit.SECOND; break;
                case TimeUnit.MINUTES: qiu = IntervalUnit.MINUTE; break;
                case TimeUnit.HOURS: qiu = IntervalUnit.HOUR; break;
                case TimeUnit.DAYS: qiu = IntervalUnit.DAY; break;
                case TimeUnit.WEEKS: qiu = IntervalUnit.WEEK; break;
                case TimeUnit.MONTHS: qiu = IntervalUnit.MONTH; break;
                case TimeUnit.YEARS: qiu = IntervalUnit.YEAR; break;
            }
            tb.withSchedule(CalendarIntervalScheduleBuilder.calendarIntervalSchedule().withInterval(interval, qiu))
        }
        if (count != null) {
            tb.withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(count))
        }
        if (cronString != null) {
            tb.withSchedule(CronScheduleBuilder.cronSchedule(cronString))
        }

        Trigger trigger = tb.build()

        sfi.scheduler.scheduleJob(job, trigger)
    }
}
