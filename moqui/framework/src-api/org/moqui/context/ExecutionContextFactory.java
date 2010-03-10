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
package org.moqui.context;

import org.moqui.BaseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Interface for the object that will be used to get an ExecutionContext object and manage framework life cycle.
 */

public interface ExecutionContextFactory {
    /** Initialize a simple ExecutionContext for use in a non-webapp context, like a remote service call or similar. */
    ExecutionContext getExecutionContext();

    /** Initialize a WebExecutionContext for a single request. */
    WebExecutionContext getWebExecutionContext(HttpServletRequest request, HttpServletResponse response);

    /**
     * Initialize/Start the Moqui Framework. This should be called by the container, ie in a webapp or an OSGi component.
     *
     * In a webapp the HttpServlet implementation should call this in the init() method, and in the web.xml servlet
     * entry there should be a load-on-startup set to 1.
     */
    void init() throws BaseException;

    /**
     * Destroy/Stop the Moqui Framework (does nothing if start has not been called).
     *
     * In a webapp the HttpServlet implementation should call this in the destroy() method.
     */
    void destroy() throws BaseException;

    /**
     * Register a component with the framework.
     *
     * @param baseLocation A file system directory or a content repository location (the component base location).
     */
    void initComponent(String baseLocation) throws BaseException;

    /**
     * Destroy a component that has been initialized.
     *
     * All initialized components will be destroyed when the framework is destroyed, but this can be used to destroy
     * a component while the framework is still running in order to re-initilize or simple disable it.
     *
     * @param baseLocation A component base location.
     */
    void destroyComponent(String baseLocation) throws BaseException;

    /** Get a Map where each key is a component name and each value is the component's base location. */
    Map<String, String> getComponentBaseLocations();
}
