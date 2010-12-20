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

import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.WebExecutionContext

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.service.ServiceFacadeImpl

class ExecutionContextFactoryImpl implements ExecutionContextFactory {

    protected final static Logger logger = LoggerFactory.getLogger(ExecutionContextFactoryImpl.class)
    
    protected boolean destroyed = false
    
    protected final String runtimePath

    protected final String confPath
    protected final Node confXmlRoot
    
    protected final Map componentLocationMap = new HashMap()
    // for future use if needed: protected final Map componentDetailMap;

    // ======== Permanent Delegated Facades ========
    protected final CacheFacadeImpl cacheFacade
    protected final EntityFacadeImpl entityFacade
    protected final LoggerFacadeImpl loggerFacade
    protected final ResourceFacadeImpl resourceFacade
    protected final ScreenFacadeImpl screenFacade
    protected final ServiceFacadeImpl serviceFacade
    protected final TransactionFacadeImpl transactionFacade

    /**
     * This constructor gets runtime directory and conf file location from a properties file on the classpath so that
     * it can initialize on its own. This is the constructor to be used by the ServiceLoader in the Moqui.java file.
     */
    ExecutionContextFactoryImpl() {
        // get the runtime directory path
        Properties moquiInitProperties = new Properties();
        moquiInitProperties.load(ClassLoader.getSystemResourceAsStream("MoquiInit.properties"));
        this.runtimePath = moquiInitProperties.getProperty("moqui.runtime");
        if (!this.runtimePath) {
            this.runtimePath = System.getProperty("moqui.runtime");
        }
        if (!this.runtimePath) {
            throw new IllegalArgumentException("No moqui.runtime property found in MoquiInit.properties or in a system property (with: -Dmoqui.runtime=... on the command line).");
        }
        if (this.runtimePath.endsWith('/')) this.runtimePath = this.runtimePath.substring(0, this.runtimePath.length()-1);

        // setup the runtimeFile
        File runtimeFile = new File(this.runtimePath);
        if (!runtimeFile.exists()) {
            throw new IllegalArgumentException("The moqui.runtime path [${this.runtimePath}] was not found.");
        }

        // get the moqui configuration file path
        String confPartialPath = moquiInitProperties.getProperty("moqui.conf");
        if (!confPartialPath) {
            confPartialPath = System.getProperty("moqui.conf");
        }
        if (!confPartialPath) {
            throw new IllegalArgumentException("No moqui.conf property found in MoquiInit.properties or in a system property (with: -Dmoqui.conf=... on the command line).");
        }

        // setup the confFile
        if (confPartialPath.beginsWith('/')) confPartialPath = confPartialPath.substring(1);
        String confFullPath = this.runtimePath + '/' + confPartialPath;
        File confFile = new File(confFullPath);
        if (!confFile.exists()) {
            throw new IllegalArgumentException("The moqui.conf path [${confFullPath}] was not found.");
        }

        this.confPath = confFullPath;

        this.init();
    }

    /** This constructor takes the runtime directory path and conf file path directly. */
    ExecutionContextFactoryImpl(String runtimePath, String confPath) {
        // setup the runtimeFile
        File runtimeFile = new File(runtimePath);
        if (!runtimeFile.exists()) {
            throw new IllegalArgumentException("The moqui.runtime path [${runtimePath}] was not found.");
        }

        // setup the confFile
        if (runtimePath.endsWith('/')) runtimePath = runtimePath.substring(0, runtimePath.length()-1);
        if (confPath.beginsWith('/')) confPath = confPath.substring(1);
        String confFullPath = runtimePath + '/' + confPath;
        File confFile = new File(confFullPath);
        if (!confFile.exists()) {
            throw new IllegalArgumentException("The moqui.conf path [${confFullPath}] was not found.");
        }

        this.runtimePath = runtimePath;
        this.confPath = confFullPath;

        this.init();
    }

    /** Initialize all permanent framework objects, ie those not sensitive to webapp or user context. */
    protected void init() {
        URL defaultConfUrl = ClassLoader.getSystemResource("MoquiDefaultConf.xml")
        this.confXmlRoot = new XmlParser().parse(defaultConfUrl.newInputStream())

        File confFile = new File(this.confPath)
        Node overrideConfXmlRoot = new XmlParser().parse(confFile)

        // merge the active/override conf file into the default one to override any settings (they both have the same root node, go from there)
        mergeConfigNodes(baseNode, overrideNode)

        // this init order is important as some facades will use others
        this.cacheFacade = new CacheFacadeImpl(this)
        this.loggerFacade = new LoggerFacadeImpl(this)
        this.resourceFacade = new ResourceFacadeImpl(this)
        this.transactionFacade = new TransactionFacadeImpl(this)
        this.entityFacade = new EntityFacadeImpl(this)
        this.serviceFacade = new ServiceFacadeImpl(this)
        this.screenFacade = new ScreenFacadeImpl(this)
    }

    Node getConfXmlRoot() {
        return this.confXmlRoot
    }

    synchronized void destroy() {
        if (!this.destroyed) {
            // this destroy order is important as some use others so must be destroyed first
            this.serviceFacade.destroy()
            this.entityFacade.destroy()
            this.transactionFacade.destroy()
            this.cacheFacade.destroy()

            this.destroyed = true
        }
    }

    // ========== Getters ==========

    CacheFacadeImpl getCacheFacade() {
        return this.cacheFacade;
    }

    EntityFacadeImpl getEntityFacade() {
        return this.entityFacade;
    }

    LoggerFacadeImpl getLoggerFacade() {
        return this.loggerFacade;
    }

    ResourceFacadeImpl getResourceFacade() {
        return this.resourceFacade;
    }

    ScreenFacadeImpl getScreenFacade() {
        return this.screenFacade;
    }

    ServiceFacadeImpl getServiceFacade() {
        return this.serviceFacade;
    }

    TransactionFacadeImpl getTransactionFacade() {
        return this.transactionFacade;
    }

    // ========== Interface Implementations ==========

    /** @see org.moqui.context.ExecutionContextFactory#getExecutionContext() */
    ExecutionContext getExecutionContext() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#getWebExecutionContext(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) */
    WebExecutionContext getWebExecutionContext(HttpServletRequest request, HttpServletResponse response) {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#initComponent(String) */
    void initComponent(String baseLocation) throws BaseException {
        // TODO: how to get component name? for now use last directory name
        if (baseLocation.endsWith('/')) baseLocation = baseLocation.substring(0, baseLocation.length()-1);
        int lastSlashIndex = baseLocation.lastIndexOf('/');
        String componentName;
        if (lastSlashIndex < 0) {
            // if this happens the component directory is directly under the runtime directory, so prefix loc with that
            componentName = baseLocation;
            baseLocation = this.runtimeFile.getAbsolutePath() + '/' + baseLocation;
        } else {
            componentName = baseLocation.substring(lastSlashIndex+1);
        }

        this.componentLocationMap.put(componentName, baseLocation);

        // TODO: implement rest of this as needed
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroyComponent(String) */
    void destroyComponent(String componentName) throws BaseException {
        this.componentLocationMap.remove(componentName);

        // TODO: implement rest of this as needed
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    Map<String, String> getComponentBaseLocations() {
        return Collections.unmodifiableMap(this.componentLocationMap);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!this.destroyed) {
                this.destroy();
                this.loggerFacade.log(null, "\n===========================================================\n ExecutionContextFactoryImpl not destroyed, caught in finalize\n===========================================================\n", null);
            }
        } catch (Exception e) {
            this.loggerFacade.log(null, "Error in destroy, called in finalize of ExecutionContextFactoryImpl", e);
        }
        super.finalize();
    }

    void mergeConfigNodes(Node baseNode, Node overrideNode) {
        if (overrideNode."cache-list"[0]) {
            mergeNodeWithChildKey(baseNode."cache-list"[0], overrideNode."cache-list"[0], "cache", "name")
        }
        
        if (overrideNode."server-stats") {
            mergeNodeWithChildKey(baseNode."server-stats"[0], overrideNode."server-stats"[0], "artifact-stats", "type")
        }

        if (overrideNode."webapp-list") {
            mergeNodeWithChildKey(baseNode."webapp-list"[0], overrideNode."webapp-list"[0], "webapp", "type")
        }

        if (overrideNode."screen-facade") {
            mergeNodeWithChildKey(baseNode."screen-facade"[0], overrideNode."screen-facade"[0], "screen-text-output", "type")
        }

        if (overrideNode."service-facade") {
            Node sfBaseNode = baseNode."service-facade"[0]
            Node sfOverrideNode = overrideNode."service-facade"[0]
            mergeNodeWithChildKey(sfBaseNode, sfOverrideNode, "service-location", "name")
            mergeNodeWithChildKey(sfBaseNode, sfOverrideNode, "startup-service", "name")

            // handle thread-pool
            Node tpOverrideNode = sfOverrideNode."thread-pool"[0]
            if (tpOverrideNode) {
                Node tpBaseNode = sfBaseNode."thread-pool"[0]
                if (tpBaseNode) {
                    mergeNodeWithChildKey(tpBaseNode, tpOverrideNode, "run-from-pool", "name")
                } else {
                    sfBaseNode.append(tpOverrideNode)
                }
            }

            // handle jms-service, just copy all over
            for (Node jsOverrideNode in sfOverrideNode."jms-service") {
                sfBaseNode.append(jsOverrideNode)
            }
        }

        if (overrideNode."entity-facade") {
            Node efBaseNode = baseNode."entity-facade"[0]
            Node efOverrideNode = overrideNode."entity-facade"[0]
            mergeNodeWithChildKey(efBaseNode, efOverrideNode, "datasource", "group-name")

            // handle app-server-jndi, transaction-factory
            Node tfOverrideNode = efOverrideNode."transaction-factory"[0]
            if (tfOverrideNode) {
                Node tfBaseNode = efBaseNode."transaction-factory"[0]
                if (tfBaseNode) {
                    tfBaseNode.attributes().putAll(tfOverrideNode.attributes())
                } else {
                    efBaseNode.append(tfOverrideNode)
                }
            }
            Node asjOverrideNode = efOverrideNode."app-server-jndi"[0]
            if (asjOverrideNode) {
                Node asjBaseNode = efBaseNode."app-server-jndi"[0]
                if (asjBaseNode) {
                    asjBaseNode.attributes().putAll(asjOverrideNode.attributes())
                } else {
                    efBaseNode.append(asjOverrideNode)
                }
            }
        }

        if (overrideNode."database-list") {
            mergeNodeWithChildKey(baseNode."database-list"[0], overrideNode."database-list"[0], "database", "name")
        }

        if (overrideNode."repository-list") {
            mergeNodeWithChildKey(baseNode."repository-list"[0], overrideNode."repository-list"[0], "repository", "name")
        }
    }

    void mergeNodeWithChildKey(Node baseNode, Node overrideNode, String childNodesName, String keyAttributeName) {
        // override attributes for this node
        baseNode.attributes().putAll(overrideNode.attributes())

        for (Node childOverrideNode in overrideNode[childNodesName]) {
            String keyValue = childOverrideNode.attribute(keyAttributeName)
            Node childBaseNode = baseNode[childNodesName].find({ it.attribute(keyAttributeName) == keyValue })

            if (childBaseNode) {
                // merge the node attributes
                childBaseNode.attributes().putAll(childOverrideNode.attributes())

                // merge child nodes for specific nodes
                if ("webapp" == childNodesName) {
                    mergeWebappChildNodes(childBaseNode, childOverrideNode)
                } else if ("database" == childNodesName) {
                    // handle database -> field-type-def@type
                    mergeNodeWithChildKey(childBaseNode, childOverrideNode, "field-type-def", "type")
                }
            } else {
                // no matching child base node, so add a new one
                baseNode.append(childOverrideNode.clone())
            }


        }
    }

    void mergeWebappChildNodes(Node baseNode, Node overrideNode) {
        // handle webapp -> first-hit-in-visit[1], after-request[1], before-request[1], after-login[1], before-logout[1], root-screen[1]
        mergeWebappActions(baseNode, overrideNode, "first-hit-in-visit")
        mergeWebappActions(baseNode, overrideNode, "after-request")
        mergeWebappActions(baseNode, overrideNode, "before-request")
        mergeWebappActions(baseNode, overrideNode, "after-login")
        mergeWebappActions(baseNode, overrideNode, "before-logout")

        Node childOverrideNode = overrideNode."root-screen"[0]
        if (childOverrideNode) {
            Node childBaseNode = baseNode."root-screen"[0]
            if (childBaseNode) {
                childBaseNode.attributes().putAll(childOverrideNode.attributes())
            } else {
                baseNode.append((Node) childOverrideNode.clone())
            }
        }
    }

    void mergeWebappActions(Node baseWebappNode, Node overrideWebappNode, String childNodeName) {
        List overrideActionNodes = overrideWebappNode[childNodeName].actions.children()
        if (overrideActionNodes) {
            Node childNode = baseWebappNode[childNodeName][0]
            if (!childNode) {
                childNode = baseWebappNode.appendNode(childNodeName)
            }
            Node actionsNode = childNode.actions[0]
            if (!actionsNode) {
                actionsNode = childNode.appendNode("actions")
            }

            for (Node overrideActionNode in overrideActionNodes) {
                actionsNode.append(overrideActionNode)
            }
        }
    }
}
