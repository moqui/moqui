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

public interface ServiceCallAsync extends ServiceCall {
    /** Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To explicitly separate
     * the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}" (this is useful for calling the
     * implicit entity CrUD services where verb is create, update, or delete and noun is the name of the entity).
     */
    ServiceCallAsync name(String serviceName);

    ServiceCallAsync name(String verb, String noun);

    ServiceCallAsync name(String path, String verb, String noun);

    /** Map of name, value pairs that make up the context (in parameters) passed to the service. */
    ServiceCallAsync context(Map<String, Object> context);

    /** Single name, value pairs to put in the context (in parameters) passed to the service. */
    ServiceCallAsync context(String name, Object value);


    /** If true the service call will be persisted and then run. If false it will be run from memory only.
     * Defaults to false.
     * @return Reference to this for convenience.
     */
    ServiceCallAsync persist(boolean persist);

    /* * Specify the transaction isolation desired for this service call.
     * For possible values see JavaDoc for javax.sql.Connection.
     * If not specified defaults to configured value for service, or container.
     *
     * @return Reference to this for convenience.
     */
    /* not supported by Atomikos/etc right now, consider for later: ServiceCallAsync transactionIsolation(int transactionIsolation); */

    /** Object implementing ServiceResultReceiver interface which will receive the result when service is complete.
     * @return Reference to this for convenience.
     */
    ServiceCallAsync resultReceiver(ServiceResultReceiver resultReceiver);

    /** Maximum number of times to retry running this service.
     * @return Reference to this for convenience.
     */
    ServiceCallAsync maxRetry(int maxRetry);

    /** Call the service asynchronously. */
    void call() throws ServiceException;

    /** Call the service asynchronously, and get a result waiter object back so you can wait for the service to
     * complete and get the result. This is useful for running a number of service simultaneously and then getting
     * all of the results back which will reduce the total running time from the sum of the time to run each service
     * to just the time the longest service takes to run.
     */
    ServiceResultWaiter callWaiter() throws ServiceException;
}
