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
import org.moqui.service.ServiceRequester
import org.moqui.service.ServiceResultWaiter
import org.moqui.service.ServiceCallback
import org.moqui.impl.context.ExecutionContextFactoryImpl

class ServiceFacadeImpl implements ServiceFacade {

    protected final ExecutionContextFactoryImpl ecfi;

    protected static final Map<String, List<ServiceCallback>> callbackRegistry = new HashMap()

    public ServiceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;

        // TODO: init quartz scheduler, etc
    }

    public void destroy() {
        // TODO: destroy quartz scheduler, etc
    }

    /** @see org.moqui.service.ServiceFacade#callSync(String, Map<String,?>, boolean, int)  */
    public Map<String, Object> callSync(String serviceName, Map<String, ?> context, boolean requireNewTransaction, Integer transactionIsolation) {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.service.ServiceFacade#callAsync(String, Map, ServiceRequester, boolean, int) */
    public void callAsync(String serviceName, Map<String, ?> context, ServiceRequester requester, boolean persist, Integer transactionIsolation) {
        // TODO: implement this
    }

    /** @see org.moqui.service.ServiceFacade#callAsync(String, Map, boolean, int) */
    public ServiceResultWaiter callAsync(String serviceName, Map<String, ?> context, boolean persist, Integer transactionIsolation) {
        ServiceResultWaiter requester = new ServiceResultWaiter()
        this.callAsync(serviceName, context, requester, persist, transactionIsolation)
        return requester
    }

    /** @see org.moqui.service.ServiceFacade#schedule(String, String, String, Map<String,?>, long, int, int, int, long, int) */
    public void schedule(String jobName, String poolName, String serviceName, Map<String, ?> context, Long startTime, Integer frequency, Integer interval, Integer count, Long endTime, Integer maxRetry) {
        // TODO: implement this
    }

    /** @see org.moqui.service.ServiceFacade#registerCallback(String, ServiceCallback) */
    public synchronized void registerCallback(String serviceName, ServiceCallback serviceCallback) {
        List<ServiceCallback> callbackList = callbackRegistry.get(serviceName)
        if (callbackList == null) {
            callbackList = new LinkedList()
            callbackRegistry.put(serviceName, callbackList)
        }
        callbackList.add(serviceCallback);
    }

    /** @see org.moqui.service.ServiceFacade#addRollbackService(String, Map<String,?>, boolean) */
    public void addRollbackService(String serviceName, Map<String, ?> context, boolean persist) {
        // TODO: implement this
    }

    /** @see org.moqui.service.ServiceFacade#addCommitService(String, Map<String,?>, boolean) */
    public void addCommitService(String serviceName, Map<String, ?> context, boolean persist) {
        // TODO: implement this
    }
}
