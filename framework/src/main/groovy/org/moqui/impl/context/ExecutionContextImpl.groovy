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

import groovy.transform.CompileStatic
import org.elasticsearch.client.Client
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.StatelessKieSession
import org.moqui.context.*
import org.moqui.entity.EntityFacade
import org.moqui.entity.EntityList
import org.moqui.service.ServiceFacade
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest
import org.apache.camel.CamelContext
import org.moqui.entity.EntityValue

@CompileStatic
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

    ExecutionContextFactoryImpl getEcfi() { return ecfi }

    @Override
    ContextStack getContext() { return context }

    @Override
    Map<String, Object> getContextRoot() { return context.getRootMap() }

    @Override
    String getTenantId() { tenantId ?: "DEFAULT" }
    @Override
    EntityValue getTenant() {
        boolean alreadyDisabled = getArtifactExecution().disableAuthz()
        try {
            return getEntity().find("moqui.tenant.Tenant").condition("tenantId", getTenantId()).useCache(true).one()
        } finally {
            if (!alreadyDisabled) getArtifactExecution().enableAuthz()
        }
    }

    @Override
    WebFacade getWeb() { webFacade }

    @Override
    UserFacade getUser() { if (userFacade != null) return userFacade else return (userFacade = new UserFacadeImpl(this)) }

    @Override
    MessageFacade getMessage() { if (messageFacade != null) return messageFacade else return (messageFacade = new MessageFacadeImpl()) }

    @Override
    ArtifactExecutionFacade getArtifactExecution() {
        if (artifactExecutionFacade != null) return artifactExecutionFacade
        else return (artifactExecutionFacade = new ArtifactExecutionFacadeImpl(this))
    }

    // ==== More Permanent Objects (get from the factory instead of locally) ===

    @Override
    L10nFacade getL10n() { ecfi.getL10nFacade() }

    @Override
    ResourceFacade getResource() { ecfi.getResourceFacade() }

    @Override
    LoggerFacade getLogger() { ecfi.getLoggerFacade() }

    @Override
    CacheFacade getCache() { ecfi.getCacheFacade() }

    @Override
    TransactionFacade getTransaction() { ecfi.getTransactionFacade() }

    @Override
    EntityFacade getEntity() { ecfi.getEntityFacade(getTenantId()) }

    @Override
    ServiceFacade getService() { ecfi.getServiceFacade() }

    @Override
    ScreenFacade getScreen() { ecfi.getScreenFacade() }

    @Override
    NotificationMessage makeNotificationMessage() { return new NotificationMessageImpl(this) }
    @Override
    List<NotificationMessage> getNotificationMessages(String userId, String topic) {
        if (!userId && !topic) return []

        List<NotificationMessage> nmList = []
        boolean alreadyDisabled = getArtifactExecution().disableAuthz()
        try {
            Map parameters = [receivedDate:null]
            if (userId) parameters.userId = userId
            if (topic) parameters.topic = topic
            EntityList nmbuList = entity.find("moqui.security.user.NotificationMessageByUser").condition(parameters).list()
            for (EntityValue nmbu in nmbuList) {
                NotificationMessageImpl nmi = new NotificationMessageImpl(this)
                nmi.populateFromValue(nmbu)
                nmList.add(nmi)
            }
        } finally {
            if (!alreadyDisabled) getArtifactExecution().enableAuthz()
        }
        return nmList
    }
    @Override
    void registerNotificationMessageListener(NotificationMessageListener nml) {
        getEcfi().registerNotificationMessageListener(nml)
    }


    @Override
    CamelContext getCamelContext() { ecfi.getCamelContext() }
    @Override
    Client getElasticSearchClient() { ecfi.getElasticSearchClient() }
    @Override
    KieContainer getKieContainer(String componentName) { ecfi.getKieContainer(componentName) }
    @Override
    KieSession getKieSession(String ksessionName) {
        KieSession session = ecfi.getKieSession(ksessionName)
        session.setGlobal("ec", this)
        return session
    }
    @Override
    StatelessKieSession getStatelessKieSession(String ksessionName) {
        StatelessKieSession session = ecfi.getStatelessKieSession(ksessionName)
        session.setGlobal("ec", this)
        return session
    }


    @Override
    void initWebFacade(String webappMoquiName, HttpServletRequest request, HttpServletResponse response) {
        tenantId = request.session.getAttribute("moqui.tenantId")
        if (!tenantId) {
            boolean alreadyDisabled = getArtifactExecution().disableAuthz()
            try {
                EntityValue tenantHostDefault = getEntity().find("moqui.tenant.TenantHostDefault")
                        .condition("hostName", request.getServerName()).useCache(true).one()
                if (tenantHostDefault) {
                    tenantId = tenantHostDefault.tenantId
                    request.session.setAttribute("moqui.tenantId", tenantId)
                    request.session.setAttribute("moqui.tenantHostName", tenantHostDefault.hostName)
                    if (tenantHostDefault.allowOverride)
                        request.session.setAttribute("moqui.tenantAllowOverride", tenantHostDefault.allowOverride)
                }
            } finally {
                if (!alreadyDisabled) getArtifactExecution().enableAuthz()
            }
        }
        webFacade = new WebFacadeImpl(webappMoquiName, request, response, this)
        ((UserFacadeImpl) getUser()).initFromHttpRequest(request, response)

        // perhaps debatable whether or not this is a good idea, but makes things much easier
        context.putAll(webFacade.requestParameters)

        // this is the beginning of a request, so trigger before-request actions
        webFacade.runBeforeRequestActions()
    }

    void changeTenant(String tenantId) {
        if (webFacade != null && webFacade.session.getAttribute("moqui.tenantAllowOverride") == "N")
            throw new IllegalArgumentException("Tenant override is not allowed for host [${webFacade.session.getAttribute("moqui.tenantHostName")?:"Unknown"}].")
        // logout the current user, won't be valid in other tenant
        if (userFacade != null && !userFacade.getLoggedInAnonymous()) userFacade.logoutUser()
        this.tenantId = tenantId
        if (webFacade != null) webFacade.session.setAttribute("moqui.tenantId", tenantId)
    }

    @Override
    void destroy() {
        // if webFacade exists this is the end of a request, so trigger after-request actions
        if (webFacade) webFacade.runAfterRequestActions()

        // make sure there are no transactions open, if any commit them all now
        ecfi.transactionFacade.destroyAllInThread()
        // clean up resources, like JCR session
        ecfi.resourceFacade.destroyAllInThread()
        // clear out the ECFI's reference to this as well
        ecfi.activeContext.remove()
    }

    @Override
    String toString() { return "ExecutionContext" }
}
