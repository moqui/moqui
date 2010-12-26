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
            // this should always have been initialized before getting it this way, but if not get a non-web ExecutionContext
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

    /** This should be called when it is known a context won't be used any more, such as at the end of a web request or service execution. */
    public static void destroyActiveExecutionContext() {
        ExecutionContext executionContext = activeExecutionContext.get();
        if (executionContext != null) {
            executionContext.destroy();
            activeExecutionContext.remove();
        }
    }

    /** @see org.moqui.context.ExecutionContextFactory#initComponent(String) */
    public static void initComponent(String baseLocation) throws BaseException {
        activeExecutionContextFactory.initComponent(baseLocation);
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroyComponent(String) */
    public static void destroyComponent(String componentName) throws BaseException {
        activeExecutionContextFactory.destroyComponent(componentName);
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    public static Map<String, String> getComponentBaseLocations() {
        return activeExecutionContextFactory.getComponentBaseLocations();
    }
}
