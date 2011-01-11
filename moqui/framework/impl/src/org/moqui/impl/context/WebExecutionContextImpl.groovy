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


import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import javax.servlet.ServletContext

import org.moqui.context.WebExecutionContext
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
import org.moqui.impl.StupidWebUtilities

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
        this.eci.userFacade.initFromHttpRequest(request)
    }

    // ========== Web EC Methods

    /** @see org.moqui.context.WebExecutionContext#getParameters() */
    Map<String, Object> getParameters() {
        // Uses the approach of creating a series of this objects wrapping the other non-Map attributes/etc instead of
        // copying everything from the various places into a single combined Map; this should be much faster to create
        // and only slightly slower when running.
        ContextStack cs = new ContextStack()
        cs.push(getRequestParameters())
        cs.push(getApplicationAttributes())
        cs.push(getSessionAttributes())
        cs.push(getRequestAttributes())
        return cs
    }

    /** @see org.moqui.context.WebExecutionContext#getRequest() */
    HttpServletRequest getRequest() { return request }

    /** @see org.moqui.context.WebExecutionContext#getRequestAttributes() */
    Map<String, Object> getRequestAttributes() {
        return new StupidWebUtilities.RequestAttributeMap(request)
    }

    /** @see org.moqui.context.WebExecutionContext#getRequestParameters() */
    Map<String, Object> getRequestParameters() {
        ContextStack cs = new ContextStack()
        cs.push((Map<String, Object>) request.getParameterMap())
        cs.push(StupidWebUtilities.getPathInfoParameterMap(request.getPathInfo()))
        return new StupidWebUtilities.CanonicalizeMap(cs)
    }

    /** @see org.moqui.context.WebExecutionContext#getResponse() */
    HttpServletResponse getResponse() { return response }

    /** @see org.moqui.context.WebExecutionContext#getSession() */
    HttpSession getSession() { return request.getSession() }

    /** @see org.moqui.context.WebExecutionContext#getSessionAttributes() */
    Map<String, Object> getSessionAttributes() {
        return new StupidWebUtilities.SessionAttributeMap(request.getSession())
    }

    /** @see org.moqui.context.WebExecutionContext#getServletContext() */
    ServletContext getServletContext() { return request.getServletContext() }

    /** @see org.moqui.context.WebExecutionContext#getApplicationAttributes() */
    Map<String, Object> getApplicationAttributes() {
        return new StupidWebUtilities.ServletContextAttributeMap(request.getServletContext())
    }

    /** @see org.moqui.context.ExecutionContext#getContext() */
    Map<String, Object> getContext() { return eci.context }

    /** @see org.moqui.context.ExecutionContext#getContextRoot() */
    Map<String, Object> getContextRoot() { return eci.context.getRootMap() }

    /** @see org.moqui.context.ExecutionContext#getTenantId() */
    String getTenantId() { return eci.tenantId }

    /** @see org.moqui.context.ExecutionContext#getUser() */
    UserFacade getUser() { return eci.userFacade }

    /** @see org.moqui.context.ExecutionContext#getMessage() */
    MessageFacade getMessage() { return eci.messageFacade }

    /** @see org.moqui.context.ExecutionContext#getL10n() */
    L10nFacade getL10n() { return eci.l10nFacade }

    /** @see org.moqui.context.ExecutionContext#getArtifactExecution() */
    ArtifactExecutionFacade getArtifactExecution() { return eci.artifactExecutionFacade }

    // ==== More Permanent Objects (get from the factory) ===

    /** @see org.moqui.context.ExecutionContext#getResource() */
    ResourceFacade getResource() { return ecfi.getResourceFacade() }

    /** @see org.moqui.context.ExecutionContext#getLogger() */
    LoggerFacade getLogger() { return ecfi.getLoggerFacade() }

    /** @see org.moqui.context.ExecutionContext#getCache() */
    CacheFacade getCache() { return ecfi.getCacheFacade() }

    /** @see org.moqui.context.ExecutionContext#getTransaction() */
    TransactionFacade getTransaction() { return ecfi.getTransactionFacade() }

    /** @see org.moqui.context.ExecutionContext#getEntity() */
    EntityFacade getEntity() { return ecfi.getEntityFacade() }

    /** @see org.moqui.context.ExecutionContext#getService() */
    ServiceFacade getService() { return ecfi.getServiceFacade() }

    /** @see org.moqui.context.ExecutionContext#getScreen() */
    ScreenFacade getScreen() { return ecfi.getScreenFacade() }

    /** @see org.moqui.context.ExecutionContext#destroy() */
    void destroy() {
        eci.destroy()
    }
}
