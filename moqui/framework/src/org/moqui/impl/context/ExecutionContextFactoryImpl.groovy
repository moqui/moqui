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
package org.moqui.impl.context;

import org.moqui.BaseException;
import org.moqui.context.ExecutionContext;
import org.moqui.context.ExecutionContextFactory;
import org.moqui.context.WebExecutionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public class ExecutionContextFactoryImpl implements ExecutionContextFactory {
    /** @see org.moqui.context.ExecutionContextFactory#getExecutionContext() */
    public ExecutionContext getExecutionContext() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#getWebExecutionContext(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) */
    public WebExecutionContext getWebExecutionContext(HttpServletRequest request, HttpServletResponse response) {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#init() */
    public void init() throws BaseException {
        // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroy() */
    public void destroy() throws BaseException {
        // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#initComponent(String) */
    public void initComponent(String baseLocation) throws BaseException {
        // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroyComponent(String) */
    public void destroyComponent(String baseLocation) throws BaseException {
        // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    public Map<String, String> getComponentBaseLocations() {
        return null;  // TODO: implement this
    }
}
