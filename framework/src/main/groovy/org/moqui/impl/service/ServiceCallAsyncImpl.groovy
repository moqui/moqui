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
import org.moqui.impl.context.ExecutionContextImpl

class ServiceCallAsyncImpl extends ServiceCallImpl implements ServiceCallAsync {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceCallAsyncImpl.class)

    protected boolean persist = false
    /* not supported by Atomikos/etc right now, consider for later: protected int transactionIsolation = -1 */
    protected ServiceResultReceiver resultReceiver = null
    protected int maxRetry = 1

    ServiceCallAsyncImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallAsync name(String serviceName) { this.setServiceName(serviceName); return this }

    @Override
    ServiceCallAsync name(String v, String n) { path = null; verb = v; noun = n; return this }

    @Override
    ServiceCallAsync name(String p, String v, String n) { path = p; verb = v; noun = n; return this }

    @Override
    ServiceCallAsync parameters(Map<String, ?> map) { parameters.putAll(map); return this }

    @Override
    ServiceCallAsync parameter(String name, Object value) { parameters.put(name, value); return this }

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
        if (logger.traceEnabled) logger.trace("Setting up call to async service [${serviceName}] with parameters [${parameters}]")

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

        // NOTE: is this the best way to get a unique job name? (needed to register a listener below)
        String uniqueJobName = UUID.randomUUID()
        // NOTE: don't store durably, ie tell it to get rid of it after it is run
        JobBuilder jobBuilder = JobBuilder.newJob(ServiceQuartzJob.class)
                .withIdentity(uniqueJobName, serviceName)
                .usingJobData(new JobDataMap(parameters))
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
