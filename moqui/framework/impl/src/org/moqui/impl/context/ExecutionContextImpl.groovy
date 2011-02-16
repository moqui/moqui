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

class ExecutionContextImpl implements ExecutionContext {

    protected ExecutionContextFactoryImpl ecfi

    protected ContextStack context = new ContextStack()
    protected String tenantId = null

    protected WebFacadeImpl webFacade = null
    protected UserFacadeImpl userFacade
    protected MessageFacadeImpl messageFacade
    protected ArtifactExecutionFacadeImpl artifactExecutionFacade

    ExecutionContextImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        // NOTE: no WebFacade init here, wait for call in to do that
        this.userFacade = new UserFacadeImpl(this)
        this.messageFacade = new MessageFacadeImpl()
        this.artifactExecutionFacade = new ArtifactExecutionFacadeImpl(this)

        // put reference to this in the context root
        contextRoot.put("ec", this)
    }

    ExecutionContextFactoryImpl getEcfi() { ecfi }

    /** @see org.moqui.context.ExecutionContext#getContext() */
    Map<String, Object> getContext() { this.context }

    /** @see org.moqui.context.ExecutionContext#getContextRoot() */
    Map<String, Object> getContextRoot() { this.context.getRootMap() }

    /** @see org.moqui.context.ExecutionContext#getTenantId() */
    String getTenantId() { this.tenantId }

    /** @see org.moqui.context.ExecutionContext#getWeb() */
    WebFacade getWeb() { this.webFacade }

    /** @see org.moqui.context.ExecutionContext#getUser() */
    UserFacade getUser() { this.userFacade }

    /** @see org.moqui.context.ExecutionContext#getMessage() */
    MessageFacade getMessage() { this.messageFacade }

    /** @see org.moqui.context.ExecutionContext#getArtifactExecution() */
    ArtifactExecutionFacade getArtifactExecution() { this.artifactExecutionFacade }

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
    EntityFacade getEntity() { this.ecfi.getEntityFacade() }

    /** @see org.moqui.context.ExecutionContext#getService() */
    ServiceFacade getService() { this.ecfi.getServiceFacade() }

    /** @see org.moqui.context.ExecutionContext#getScreen() */
    ScreenFacade getScreen() { this.ecfi.getScreenFacade() }

    /** @see org.moqui.context.ExecutionContext#initWebFacade(String, HttpServletRequest, HttpServletResponse) */
    void initWebFacade(String webappMoquiName, HttpServletRequest request, HttpServletResponse response) {
        this.webFacade = new WebFacadeImpl(webappMoquiName, request, response, this)
    }

    /** @see org.moqui.context.ExecutionContext#destroy() */
    void destroy() {
        // TODO?: make sure there are no db connections open, if so close them
        // make sure there are no transactions open, if any commit them all now
        this.ecfi.transactionFacade.destroyAllInThread()
        // clean up resources, like JCR session
        this.ecfi.resourceFacade.destroyAllInThread()
        // clear out the ECFI's reference to this as well
        this.ecfi.activeContext.remove()
    }
}
