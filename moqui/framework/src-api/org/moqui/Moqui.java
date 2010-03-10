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
package org.moqui;

import org.moqui.context.ExecutionContext;
import org.moqui.context.ExecutionContextFactory;
import org.moqui.context.WebExecutionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * This is a base class that implements a simple way to access the Moqui framework for use in simple deployments like a
 * single webapp or an OSGi component. 
 *
 * In deployments where a static reference to the ExecutionContextFactory is not helpful, or not possible, this does
 * not need to be used and the ExecutionContextFactory instance should be referenced and used from somewhere else.
 */

public class Moqui {
    private static final ServiceLoader<ExecutionContextFactory> executionContextFactoryLoader =
            ServiceLoader.load(ExecutionContextFactory.class);
    private static final ExecutionContextFactory activeExecutionContextFactory;
    static {
        // initialize the activeExecutionContextFactory from configuration using java.util.ServiceLoader
        // the implementation class name should be in: "META-INF/services/org.moqui.context.ExecutionContextFactory"
        activeExecutionContextFactory = executionContextFactoryLoader.iterator().next();
    }
    
    private static final ThreadLocal<ExecutionContext> activeExecutionContext = new ThreadLocal<ExecutionContext>();

    public static ExecutionContext getExecutionContext() {
        // TODO make this more thread safe, preferably in a non-blocking way
        ExecutionContext executionContext = activeExecutionContext.get();
        if (executionContext == null) {
            // this should always have been initialized before it is used, and if not get a non-web ExecutionContext
            executionContext = activeExecutionContextFactory.getExecutionContext();
            activeExecutionContext.set(executionContext);
        }
        return executionContext;
    }

    /** This should be called by a filter or servlet at the beginning of an HTTP request to initialize a web context
     * for the current thread.
     */
    public static WebExecutionContext initWebExecutionContext(HttpServletRequest request, HttpServletResponse response) {
        WebExecutionContext wec = activeExecutionContextFactory.getWebExecutionContext(request, response);
        activeExecutionContext.set(wec);
        return wec;
    }

    /** @see org.moqui.context.ExecutionContextFactory#init() */
    public static void init() throws BaseException {
        activeExecutionContextFactory.init();
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroy() */
    public static void destroy() throws BaseException {
        activeExecutionContextFactory.destroy();
    }

    /** @see org.moqui.context.ExecutionContextFactory#initComponent(String) */
    public static void initComponent(String baseLocation) throws BaseException {
        activeExecutionContextFactory.initComponent(baseLocation);
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroyComponent(String) */
    public static void destroyComponent(String baseLocation) throws BaseException {
        activeExecutionContextFactory.destroyComponent(baseLocation);
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    Map<String, String> getComponentBaseLocations() {
        return activeExecutionContextFactory.getComponentBaseLocations();
    }
}
