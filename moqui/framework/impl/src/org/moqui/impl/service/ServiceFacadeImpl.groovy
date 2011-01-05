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

    @Override
    ServiceCallSync sync() { return new ServiceCallSyncImpl(this) }

    @Override
    ServiceCallAsync async() { return new ServiceCallAsyncImpl(this) }

    @Override
    ServiceCallSchedule schedule() { return new ServiceCallScheduleImpl(this) }

    @Override
    ServiceCallSpecial special() { return new ServiceCallSpecialImpl(this) }

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
