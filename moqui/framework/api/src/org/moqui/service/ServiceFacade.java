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

/**
 * ServiceFacade Interface
 */
public interface ServiceFacade {

    ServiceCallSync sync(String serviceName);

    ServiceCallAsync async(String serviceName);

    ServiceCallSchedule schedule(String serviceName);

    ServiceCallSpecial special(String serviceName);

    /** Register a callback listener on a specific service.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param serviceCallback The callback implementation.
     */
    void registerCallback(String serviceName, ServiceCallback serviceCallback);
}
