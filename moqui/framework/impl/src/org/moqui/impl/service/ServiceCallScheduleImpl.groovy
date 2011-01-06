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

class ServiceCallScheduleImpl extends ServiceCallImpl implements ServiceCallSchedule {
    protected String jobName = null
    protected String poolName = null
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
    ServiceCallSchedule jobName(String jn) { jobName = jn; return this }

    @Override
    ServiceCallSchedule poolName(String pn) { poolName = pn; return this }

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
        // TODO poolName: any way to handle the pool concept? or get rid of that? multiple schedulers?
        // TODO maxRetry: any way to set and then track the number of retries?

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

        // do we have to have an identity?: .withIdentity(TODO, "ScheduleTrigger")
        TriggerBuilder tb = TriggerBuilder.newTrigger()
                .withPriority(3)
                .usingJobData(new JobDataMap(context))
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
