/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moqui.service;

import java.util.Map;

/**
 * ServiceFacade Interface
 */
public interface ServiceFacade {
    /**
     * Run the service synchronously with a specified timeout and return the result.
     * @param serviceName Name of the service to run. To explicitly separate the service verb and noun put a hash mark (#) between them.
     * @param context Map of name, value pairs composing the context.
     * @param persist True for store and run; False for run from memory.
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @return Map of name, value pairs composing the result.
     * @throws ServiceException
     */
    public Map<String, Object> sync(String serviceName, Map<String, ?> context, boolean persist, boolean requireNewTransaction) throws ServiceException;

    /**
     * Run the service asynchronously, passing an instance of GenericRequester that will receive the result.
     * @param serviceName Name of the service to run. To explicitly separate the service verb and noun put a hash mark (#) between them.
     * @param context Map of name, value pairs composing the context.
     * @param requester Object implementing GenericRequester interface which will receive the result.
     * @param persist True for store/run; False for run.
     * @param requireNewTransaction if true we will suspend and create a new transaction so we are sure to start.
     * @throws ServiceException
     */
    public void async(String serviceName, Map<String, ?> context, ServiceRequester requester, boolean persist, boolean requireNewTransaction) throws ServiceException;

    /**
     * Run the service asynchronously.
     * @param serviceName Name of the service to run. To explicitly separate the service verb and noun put a hash mark (#) between them.
     * @param context Map of name, value pairs composing the context.
     * @param persist True for store/run; False for run.
     * @return A new GenericRequester object.
     * @throws ServiceException
     */
    public ServiceResultWaiter async(String serviceName, Map<String, ?> context, boolean persist, boolean requireNewTransaction) throws ServiceException;

    /**
     * Schedule a service to run asynchronously at a specific start time.
     * @param jobName Name of the job
     * @param poolName Name of the service pool to send to.
     * @param serviceName Name of the service to run. To explicitly separate the service verb and noun put a hash mark (#) between them.
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
     * @param serviceName Name of the service to link callback to. To explicitly separate the service verb and noun put a hash mark (#) between them.
     * @param serviceCallback The callback implementation.
     */
    public void registerCallback(String serviceName, ServiceCallback serviceCallback);

    /**
     * Adds a rollback service to the current TX using the ServiceXaWrapper
     * @param serviceName Name of the service to run. To explicitly separate the service verb and noun put a hash mark (#) between them.
     * @param context
     * @param persist
     * @throws ServiceException
     */
    public void addRollbackService(String serviceName, Map<String, ?> context, boolean persist) throws ServiceException;

    /**
     * Adds a commit service to the current TX using the ServiceXaWrapper
     * @param serviceName Name of the service to run. To explicitly separate the service verb and noun put a hash mark (#) between them.
     * @param context
     * @param persist
     * @throws ServiceException
     */
    public void addCommitService(String serviceName, Map<String, ?> context, boolean persist) throws ServiceException;
}
