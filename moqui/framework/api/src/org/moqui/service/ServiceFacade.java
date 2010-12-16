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
    /**
     * Run the service synchronously with a specified timeout and return the result.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param context Map of name, value pairs composing the context.
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @param transactionIsolation If null defaults to configured value for service, or container. For possible values
     *   see JavaDoc for javax.sql.Connection.
     * @return Map of name, value pairs composing the result.
     * @throws ServiceException
     */
    public Map<String, Object> callSync(String serviceName, Map<String, ?> context, boolean requireNewTransaction, Integer transactionIsolation) throws ServiceException;

    /**
     * Run the service asynchronously, passing an instance of GenericRequester that will receive the result.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param context Map of name, value pairs composing the context.
     * @param requester Object implementing GenericRequester interface which will receive the result.
     * @param persist True for store/run; False for run.
     * @param transactionIsolation If null defaults to configured value for service, or container. For possible values
     *   see JavaDoc for javax.sql.Connection.
     * @throws ServiceException
     */
    public void callAsync(String serviceName, Map<String, ?> context, ServiceRequester requester, boolean persist, Integer transactionIsolation) throws ServiceException;

    /**
     * Run the service asynchronously.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param context Map of name, value pairs composing the context.
     * @param persist True for store/run; False for run.
     * @param transactionIsolation If null defaults to configured value for service, or container. For possible values
     *   see JavaDoc for javax.sql.Connection.
     * @return A new GenericRequester object.
     * @throws ServiceException
     */
    public ServiceResultWaiter callAsync(String serviceName, Map<String, ?> context, boolean persist, Integer transactionIsolation) throws ServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param jobName Name of the job
     * @param poolName Name of the service pool to send to.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param context The name/value pairs composing the context.
     * @param startTime The time to run this service.
     * @param frequency The frequency of the recurrence (RecurrenceRule.DAILY, etc).
     * @param interval The interval of the frequency recurrence.
     * @param count The number of times to repeat.
     * @param endTime The time in milliseconds the service should expire
     * @param maxRetry The number of times we should retry on failure
     * @throws ServiceException
     */
    public void schedule(String jobName, String poolName, String serviceName, Map<String, ?> context, Long startTime, Integer frequency, Integer interval, Integer count, Long endTime, Integer maxRetry) throws ServiceException;

    /**
     * Register a callback listener on a specific service.
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param serviceCallback The callback implementation.
     */
    public void registerCallback(String serviceName, ServiceCallback serviceCallback);

    /**
     * Adds a rollback service to the current TX using the ServiceXaWrapper
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param context
     * @param persist
     * @throws ServiceException
     */
    public void addRollbackService(String serviceName, Map<String, ?> context, boolean persist) throws ServiceException;

    /**
     * Adds a commit service to the current TX using the ServiceXaWrapper
     * @param serviceName Name of the service to run. The combined service name, like: "${path}.${verb}${noun}". To
     *   explicitly separate the verb and noun put a hash (#) between them, like: "${path}.${verb}#${noun}".
     * @param context
     * @param persist
     * @throws ServiceException
     */
    public void addCommitService(String serviceName, Map<String, ?> context, boolean persist) throws ServiceException;
}
