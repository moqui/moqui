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
import org.moqui.context.ScreenFacade
import org.moqui.service.ServiceFacade
import org.moqui.entity.EntityFacade
import org.moqui.context.TransactionFacade
import org.moqui.context.CacheFacade
import org.moqui.context.LoggerFacade
import org.moqui.context.ResourceFacade
import org.moqui.context.ArtifactExecutionFacade
import org.moqui.context.L10nFacade
import org.moqui.context.MessageFacade
import org.moqui.context.UserFacade

/** This class is a delegator for the ExecutionContextImpl class so that it can easily be used to extend an existing
 * ExecutionContext.
 */
class WebExecutionContextImpl implements WebExecutionContext {

    protected ExecutionContextFactoryImpl ecfi
    protected ExecutionContextImpl eci
    protected String webappMoquiName
    protected HttpServletRequest request
    protected HttpServletResponse response

    WebExecutionContextImpl(String webappMoquiName, HttpServletRequest request, HttpServletResponse response, ExecutionContextImpl eci) {
        this.eci = eci
        this.ecfi = eci.ecfi
        this.webappMoquiName = webappMoquiName
        this.request = request
        this.response = response

        // NOTE: the Visit is not setup here but rather in the MoquiEventListener (for init and destroy)
        request.setAttribute("executionContext", this)
        this.eci.userFacade.initFromHttpSession(request.getSession())
    }

    // ========== Web EC Methods

    /** @see org.moqui.context.WebExecutionContext#getParameters() */
    Map<String, Object> getParameters() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.WebExecutionContext#getRequest() */
    HttpServletRequest getRequest() { return this.request }

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
    HttpServletResponse getResponse() { return this.response }

    /** @see org.moqui.context.WebExecutionContext#getSession() */
    HttpSession getSession() { return this.request.getSession() }

    /** @see org.moqui.context.WebExecutionContext#getSessionAttributes() */
    Map<String, ?> getSessionAttributes() {
        // HttpSessionHashModel
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.WebExecutionContext#getServletContext() */
    ServletContext getServletContext() { return this.request.getServletContext() }

    /** @see org.moqui.context.WebExecutionContext#getApplicationAttributes() */
    Map<String, ?> getApplicationAttributes() {
        // ServletContextHashModel
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getContext() */
    Map<String, Object> getContext() { return this.eci.context }

    /** @see org.moqui.context.ExecutionContext#getContextRoot() */
    Map<String, Object> getContextRoot() { return this.eci.context.getRootMap() }

    /** @see org.moqui.context.ExecutionContext#getTenantId() */
    String getTenantId() { return this.eci.tenantId }

    /** @see org.moqui.context.ExecutionContext#getUser() */
    UserFacade getUser() { return this.eci.userFacade }

    /** @see org.moqui.context.ExecutionContext#getMessage() */
    MessageFacade getMessage() { return this.eci.messageFacade }

    /** @see org.moqui.context.ExecutionContext#getL10n() */
    L10nFacade getL10n() { return this.eci.l10nFacade }

    /** @see org.moqui.context.ExecutionContext#getArtifactExecution() */
    ArtifactExecutionFacade getArtifactExecution() { return this.eci.artifactExecutionFacade }

    // ==== More Permanent Objects (get from the factory) ===

    /** @see org.moqui.context.ExecutionContext#getResource() */
    ResourceFacade getResource() { return this.ecfi.getResourceFacade() }

    /** @see org.moqui.context.ExecutionContext#getLogger() */
    LoggerFacade getLogger() { return this.ecfi.getLoggerFacade() }

    /** @see org.moqui.context.ExecutionContext#getCache() */
    CacheFacade getCache() { return this.ecfi.getCacheFacade() }

    /** @see org.moqui.context.ExecutionContext#getTransaction() */
    TransactionFacade getTransaction() { return this.ecfi.getTransactionFacade() }

    /** @see org.moqui.context.ExecutionContext#getEntity() */
    EntityFacade getEntity() { return this.ecfi.getEntityFacade() }

    /** @see org.moqui.context.ExecutionContext#getService() */
    ServiceFacade getService() { return this.ecfi.getServiceFacade() }

    /** @see org.moqui.context.ExecutionContext#getScreen() */
    ScreenFacade getScreen() { return this.ecfi.getScreenFacade() }

    /** @see org.moqui.context.ExecutionContext#destroy() */
    void destroy() {
        this.eci.destroy()
    }
}
