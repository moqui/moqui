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
package org.moqui.service;

import java.util.Map;
import org.quartz.Scheduler;

/** ServiceFacade Interface */
public interface ServiceFacade {

    /** Get a service caller to call a service synchronously. */
    ServiceCallSync sync();

    /** Get a service caller to call a service asynchronously. */
    ServiceCallAsync async();

    /** Get a service caller to schedule a service. */
    ServiceCallSchedule schedule();

    /** Get a service caller for special service calls such as on commit and on rollback of current transaction. */
    ServiceCallSpecial special();

    /** Call a JSON remote service. For Moqui services the location will be something like "http://hostname/rpc/json". */
    Map<String, Object> callJsonRpc(String location, String method, Map<String, Object> parameters);


    /** Register a callback listener on a specific service.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param serviceCallback The callback implementation.
     */
    void registerCallback(String serviceName, ServiceCallback serviceCallback);

    Scheduler getScheduler();
}
