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

import org.apache.camel.CamelContext;
import org.elasticsearch.client.Client;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.moqui.BaseException;
import org.moqui.entity.EntityFacade;
import org.moqui.service.ServiceFacade;

import java.util.Map;

/**
 * Interface for the object that will be used to get an ExecutionContext object and manage framework life cycle.
 */
public interface ExecutionContextFactory {
    /** Initialize a simple ExecutionContext for use in a non-webapp context, like a remote service call or similar. */
    ExecutionContext getExecutionContext();

    /** Destroy the active Execution Context. When another is requested in this thread a new one will be created. */
    void destroyActiveExecutionContext();

    /** Destroy this Execution Context Factory. */
    void destroy();

    /**
     * Register a component with the framework.
     *
     * @param componentName Optional name for the component. If not specified the last directory in the location path
     *     will be used as the name.
     * @param baseLocation A file system directory or a content repository location (the component base location).
     */
    void initComponent(String componentName, String baseLocation) throws BaseException;

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

    /** For localization (l10n) functionality, like localizing messages. */
    L10nFacade getL10n();

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

    /** Apache Camel is used for integration message routing. To interact directly with Camel get the context here. */
    CamelContext getCamelContext();

    /** ElasticSearch Client is used for indexing and searching documents */
    Client getElasticSearchClient();

    /** Get a KIE Container for Drools, jBPM, OptaPlanner, etc from the KIE Module in the given component. */
    KieContainer getKieContainer(String componentName);
    /** Get a KIE Session by name from the last component KIE Module loaded with the given session name. */
    KieSession getKieSession(String ksessionName);
    /** Get a KIE Stateless Session by name from the last component KIE Module loaded with the given session name. */
    StatelessKieSession getStatelessKieSession(String ksessionName);
}
