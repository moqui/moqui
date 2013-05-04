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

import org.moqui.context.ExecutionContext
import org.moqui.context.UserFacade
import org.moqui.context.MessageFacade
import org.moqui.context.L10nFacade
import org.moqui.context.ResourceFacade
import org.moqui.context.LoggerFacade
import org.moqui.context.CacheFacade
import org.moqui.context.TransactionFacade
import org.moqui.entity.EntityFacade
import org.moqui.service.ServiceFacade
import org.moqui.context.ScreenFacade
import org.moqui.context.ArtifactExecutionFacade
import org.moqui.context.WebFacade
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest
import org.apache.camel.CamelContext
import org.moqui.entity.EntityValue

class ExecutionContextImpl implements ExecutionContext {

    protected ExecutionContextFactoryImpl ecfi

    protected ContextStack context = new ContextStack()
    protected String tenantId = null

    protected WebFacadeImpl webFacade = null
    protected UserFacadeImpl userFacade = null
    protected MessageFacadeImpl messageFacade = null
    protected ArtifactExecutionFacadeImpl artifactExecutionFacade = null

    ExecutionContextImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        // NOTE: no WebFacade init here, wait for call in to do that
        // NOTE: don't init userFacade, messageFacade, artifactExecutionFacade here, lazy init when first used instead
        // put reference to this in the context root
        contextRoot.put("ec", this)
    }

    ExecutionContextFactoryImpl getEcfi() { ecfi }

    /** @see org.moqui.context.ExecutionContext#getContext() */
    Map<String, Object> getContext() { (Map<String, Object>) this.context }

    /** @see org.moqui.context.ExecutionContext#getContextRoot() */
    Map<String, Object> getContextRoot() { this.context.getRootMap() }

    /** @see org.moqui.context.ExecutionContext#getTenantId() */
    String getTenantId() { this.tenantId ?: "DEFAULT" }

    /** @see org.moqui.context.ExecutionContext#getWeb() */
    WebFacade getWeb() { this.webFacade }

    /** @see org.moqui.context.ExecutionContext#getUser() */
    UserFacade getUser() { if (userFacade) return userFacade else return (userFacade = new UserFacadeImpl(this)) }

    /** @see org.moqui.context.ExecutionContext#getMessage() */
    MessageFacade getMessage() { if (messageFacade) return messageFacade else return (messageFacade = new MessageFacadeImpl()) }

    /** @see org.moqui.context.ExecutionContext#getArtifactExecution() */
    ArtifactExecutionFacade getArtifactExecution() {
        if (artifactExecutionFacade) return artifactExecutionFacade else return (artifactExecutionFacade = new ArtifactExecutionFacadeImpl(this))
    }

    // ==== More Permanent Objects (get from the factory instead of locally) ===

    /** @see org.moqui.context.ExecutionContext#getL10n() */
    L10nFacade getL10n() { this.ecfi.getL10nFacade() }

    /** @see org.moqui.context.ExecutionContext#getResource() */
    ResourceFacade getResource() { this.ecfi.getResourceFacade() }

    /** @see org.moqui.context.ExecutionContext#getLogger() */
    LoggerFacade getLogger() { this.ecfi.getLoggerFacade() }

    /** @see org.moqui.context.ExecutionContext#getCache() */
    CacheFacade getCache() { this.ecfi.getCacheFacade() }

    /** @see org.moqui.context.ExecutionContext#getTransaction() */
    TransactionFacade getTransaction() { this.ecfi.getTransactionFacade() }

    /** @see org.moqui.context.ExecutionContext#getEntity() */
    EntityFacade getEntity() { this.ecfi.getEntityFacade(getTenantId()) }

    /** @see org.moqui.context.ExecutionContext#getService() */
    ServiceFacade getService() { this.ecfi.getServiceFacade() }

    /** @see org.moqui.context.ExecutionContext#getScreen() */
    ScreenFacade getScreen() { this.ecfi.getScreenFacade() }

    CamelContext getCamelContext() { return this.ecfi.getCamelContext() }

    /** @see org.moqui.context.ExecutionContext#initWebFacade(String, HttpServletRequest, HttpServletResponse) */
    void initWebFacade(String webappMoquiName, HttpServletRequest request, HttpServletResponse response) {
        this.tenantId = request.session.getAttribute("moqui.tenantId")
        if (!this.tenantId) {
            boolean alreadyDisabled = getArtifactExecution().disableAuthz()
            try {
                EntityValue tenantHostDefault = getEntity().makeFind("moqui.tenant.TenantHostDefault")
                        .condition("hostName", request.getServerName()).useCache(true).one()
                if (tenantHostDefault) {
                    this.tenantId = tenantHostDefault.tenantId
                    request.session.setAttribute("moqui.tenantId", this.tenantId)
                    request.session.setAttribute("moqui.tenantHostName", tenantHostDefault.hostName)
                    if (tenantHostDefault.allowOverride)
                        request.session.setAttribute("moqui.tenantAllowOverride", tenantHostDefault.allowOverride)
                }
            } finally {
                if (!alreadyDisabled) getArtifactExecution().enableAuthz()
            }
        }
        this.webFacade = new WebFacadeImpl(webappMoquiName, request, response, this)
        this.getUser().initFromHttpRequest(request, response)

        // perhaps debatable whether or not this is a good idea, but makes things much easier
        this.context.putAll(this.webFacade.requestParameters)

        // this is the beginning of a request, so trigger before-request actions
        this.webFacade.runBeforeRequestActions()
    }

    void changeTenant(String tenantId) {
        if (webFacade != null && webFacade.session.getAttribute("moqui.tenantAllowOverride") == "N")
            throw new IllegalArgumentException("Tenant override is not allowed for host [${webFacade.session.getAttribute("moqui.tenantHostName")?:"Unknown"}].")
        this.tenantId = tenantId
        if (webFacade != null) webFacade.session.setAttribute("moqui.tenantId", tenantId)
    }

    /** @see org.moqui.context.ExecutionContext#destroy() */
    void destroy() {
        // if webFacade exists this is the end of a request, so trigger after-request actions
        if (this.webFacade) this.webFacade.runAfterRequestActions()

        // make sure there are no transactions open, if any commit them all now
        this.ecfi.transactionFacade.destroyAllInThread()
        // clean up resources, like JCR session
        this.ecfi.resourceFacade.destroyAllInThread()
        // clear out the ECFI's reference to this as well
        this.ecfi.activeContext.remove()
    }

    @Override
    String toString() { return "ExecutionContext" }
}
