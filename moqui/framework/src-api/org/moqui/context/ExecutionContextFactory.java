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
     * @param componentName A component name.
     */
    void destroyComponent(String componentName) throws BaseException;

    /** Get a Map where each key is a component name and each value is the component's base location. */
    Map<String, String> getComponentBaseLocations();
}
