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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.elasticsearch.client.Client;
import org.kie.api.runtime.KieContainer;
import org.moqui.entity.EntityFacade;
import org.moqui.entity.EntityValue;
import org.moqui.service.ServiceFacade;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Interface definition for object used throughout the Moqui Framework to manage contextual execution information and
 * tool interfaces. One instance of this object will exist for each thread running code and will be applicable for that
 * thread only.
 */
public interface ExecutionContext {
    /** Returns a Map that represents the current local variable space (context) in whatever is being run. */
    ContextStack getContext();

    /** Returns a Map that represents the global/root variable space (context), ie the bottom of the context stack. */
    Map<String, Object> getContextRoot();

    /** Get current Tenant ID. A single application may be run in multiple virtual instances, one for each Tenant, and
     * each will have its own set of databases (except for the tenant database which is shared among all Tenants).
     */
    String getTenantId();
    EntityValue getTenant();

    /** If running through a web (HTTP servlet) request offers access to the various web objects/information.
     * If not running in a web context will return null.
     */
    WebFacade getWeb();

    /** For information about the user and user preferences (including locale, time zone, currency, etc). */
    UserFacade getUser();

    /** For user messages including general feedback, errors, and field-specific validation errors. */
    MessageFacade getMessage();

    /** For localization (l10n) functionality, like localizing messages. */
    L10nFacade getL10n();

    /** For information about artifacts as they are being executed. */
    ArtifactExecutionFacade getArtifactExecution();

    /** For accessing resources by location string (http://, jar://, component://, content://, classpath://, etc). */
    ResourceFacade getResource();

    /** For trace, error, etc logging to the console, files, etc. */
    LoggerFacade getLogger();

    /** For managing and accessing caches. */
    CacheFacade getCache();

    /** For transaction operations use this facade instead of the JTA UserTransaction and TransactionManager. See javadoc comments there for examples of code usage. */
    TransactionFacade getTransaction();

    /** For interactions with a relational database. */
    EntityFacade getEntity();

    /** For calling services (local or remote, sync or async or scheduled). */
    ServiceFacade getService();

    /** For rendering screens for general use (mostly for things other than web pages or web page snippets). */
    ScreenFacade getScreen();

    NotificationMessage makeNotificationMessage();
    List<NotificationMessage> getNotificationMessages(String userId, String topic);
    void registerNotificationMessageListener(NotificationMessageListener nml);

    /** Apache Camel is used for integration message routing. To interact directly with Camel get the context here. */
    CamelContext getCamelContext();

    /** ElasticSearch Client is used for indexing and searching documents */
    Client getElasticSearchClient();

    /** Get a KIE Container for Drools, jBPM, OptaPlanner, etc */
    KieContainer getKieContainer(String componentName);

    /** This should be called by a filter or servlet at the beginning of an HTTP request to initialize a web facade
     * for the current thread.
     */
    void initWebFacade(String webappMoquiName, HttpServletRequest request, HttpServletResponse response);

    void changeTenant(String tenantId);

    /** This should be called when the ExecutionContext won't be used any more. Implementations should make sure
     * any active transactions, database connections, etc are closed.
     */
    void destroy();
}
