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

public interface ServiceCall {
    public enum TimeUnit { SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

    /** Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To explicitly separate
     * the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}" (this is useful for calling the
     * implicit entity CrUD services where verb is create, update, or delete and noun is the name of the entity).
     */
    ServiceCall serviceName(String serviceName);

    ServiceCall serviceName(String verb, String noun);

    ServiceCall serviceName(String path, String verb, String noun);

    String getServiceName();

    /** Map of name, value pairs that make up the context (in parameters) passed to the service. */
    ServiceCall context(Map<String, Object> context);

    /** Single name, value pairs to put in the context (in parameters) passed to the service. */
    ServiceCall context(String name, Object value);

    /** Map of name, value pairs that make up the context (in parameters) passed to the service. */
    Map<String, Object> getContext();
}
