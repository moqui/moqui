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
package org.moqui.impl.context

import org.moqui.context.WebExecutionContext

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import javax.servlet.ServletContext

import freemarker.ext.servlet.HttpRequestHashModel
import freemarker.ext.servlet.HttpSessionHashModel
import freemarker.ext.servlet.ServletContextHashModel

class WebExecutionContextImpl extends ExecutionContextImpl implements WebExecutionContext {

    protected HttpServletRequest request
    protected HttpServletResponse response

    WebExecutionContextImpl(HttpServletRequest request, HttpServletResponse response, ExecutionContextFactoryImpl ecfi) {
        super(ecfi)
        this.request = request
        this.response = response

        // NOTE: the Visit is not setup here but rather in the MoquiEventListener (for init and destroy)

        request.setAttribute("executionContext", this)
        this.userFacade.initFromHttpSession(request.getSession())
    }

    /** @see org.moqui.context.WebExecutionContext#getParameters() */
    Map<String, Object> getParameters() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.WebExecutionContext#getRequest() */
    HttpServletRequest getRequest() {
        return this.request
    }

    /** @see org.moqui.context.WebExecutionContext#getRequestAttributes() */
    Map<String, ?> getRequestAttributes() {
        // HttpRequestHashModel
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.WebExecutionContext#getRequestParameters() */
    Map<String, String> getRequestParameters() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.WebExecutionContext#getResponse() */
    HttpServletResponse getResponse() {
        return this.response
    }

    /** @see org.moqui.context.WebExecutionContext#getSession() */
    HttpSession getSession() {
        return this.request.getSession()
    }

    /** @see org.moqui.context.WebExecutionContext#getSessionAttributes() */
    Map<String, ?> getSessionAttributes() {
        // HttpSessionHashModel
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.WebExecutionContext#getServletContext() */
    ServletContext getServletContext() {
        return this.request.getServletContext()
    }

    /** @see org.moqui.context.WebExecutionContext#getApplicationAttributes() */
    Map<String, ?> getApplicationAttributes() {
        // ServletContextHashModel
        return null;  // TODO: implement this
    }
}
