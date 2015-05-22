/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
