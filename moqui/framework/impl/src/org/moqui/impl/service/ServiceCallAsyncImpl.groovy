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

import org.moqui.service.ServiceCallAsync
import org.moqui.service.ServiceResultReceiver
import org.moqui.service.ServiceResultWaiter
import org.quartz.impl.matchers.NameMatcher
import org.quartz.TriggerBuilder
import org.quartz.Trigger
import org.quartz.JobDetail
import org.quartz.JobDataMap
import org.quartz.JobBuilder

class ServiceCallAsyncImpl extends ServiceCallImpl implements ServiceCallAsync {
    protected boolean persist = false
    /* not supported by Atomikos/etc right now, consider for later: protected int transactionIsolation = -1 */
    protected ServiceResultReceiver resultReceiver = null
    protected int maxRetry = 1

    ServiceCallAsyncImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallAsync persist(boolean p) { this.persist = p; return this }

    /* not supported by Atomikos/etc right now, consider for later:
    @Override
    ServiceCallAsync transactionIsolation(int ti) { this.transactionIsolation = ti; return this }
    */

    @Override
    ServiceCallAsync resultReceiver(ServiceResultReceiver rr) { this.resultReceiver = rr; return this }

    @Override
    ServiceCallAsync maxRetry(int mr) { this.maxRetry = mr; return this }

    @Override
    void call() {
        // TODO: how to handle persist on a per-job bases? seems like the volatile Job concept matched this, but that is deprecated in 2.0
        // TODO: how to handle maxRetry

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
            sfi.scheduler.getListenerManager().addJobListener(sqjl, NameMatcher.matchNameEquals(uniqueJobName))
        }

        sfi.scheduler.scheduleJob(job, nowTrigger)
    }

    @Override
    ServiceResultWaiter callWaiter() {
        ServiceResultWaiter resultWaiter = new ServiceResultWaiter()
        this.resultReceiver(resultWaiter)
        this.call()
        return resultWaiter
    }
}
