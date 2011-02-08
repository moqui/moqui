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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.service.ServiceFacadeImpl

import org.moqui.impl.screen.ScreenFacadeImpl

class ExecutionContextFactoryImpl implements ExecutionContextFactory {
    protected final static Logger logger = LoggerFactory.getLogger(ExecutionContextFactoryImpl.class)
    
    protected boolean destroyed = false
    
    protected final String runtimePath

    protected final String confPath
    final Node confXmlRoot
    
    protected final Map<String, String> componentLocationMap = new HashMap<String, String>()

    protected ThreadLocal<ExecutionContext> activeContext = new ThreadLocal<ExecutionContext>()

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
     * it can initialize on its own. This is the constructor to be used by the ServiceLoader in the Moqui.java file,
     * or by init methods in a servlet or context filter or OSGi component or Spring component or whatever.
     */
    ExecutionContextFactoryImpl() {
        // get the runtime directory path
        Properties moquiInitProperties = new Properties()
        moquiInitProperties.load(this.class.getClassLoader().getResourceAsStream("MoquiInit.properties"))

        // if there is a system property use that, otherwise from the properties file
        this.runtimePath = System.getProperty("moqui.runtime")
        if (!this.runtimePath) {
            this.runtimePath = moquiInitProperties.getProperty("moqui.runtime")
        }
        if (!this.runtimePath) {
            throw new IllegalArgumentException("No moqui.runtime property found in MoquiInit.properties or in a system property (with: -Dmoqui.runtime=... on the command line).")
        }
        if (this.runtimePath.endsWith("/")) this.runtimePath = this.runtimePath.substring(0, this.runtimePath.length()-1)

        // setup the runtimeFile
        File runtimeFile = new File(this.runtimePath)
        if (!runtimeFile.exists()) {
            throw new IllegalArgumentException("The moqui.runtime path [${this.runtimePath}] was not found.")
        } else {
            this.runtimePath = runtimeFile.getCanonicalPath()
        }

        // always set the full moqui.runtime system property for use in various places
        System.setProperty("moqui.runtime", this.runtimePath)

        // get the moqui configuration file path
        String confPartialPath = System.getProperty("moqui.conf")
        if (!confPartialPath) {
            confPartialPath = moquiInitProperties.getProperty("moqui.conf")
        }
        if (!confPartialPath) {
            throw new IllegalArgumentException("No moqui.conf property found in MoquiInit.properties or in a system property (with: -Dmoqui.conf=... on the command line).")
        }

        // setup the confFile
        if (confPartialPath.startsWith("/")) confPartialPath = confPartialPath.substring(1)
        String confFullPath = this.runtimePath + "/" + confPartialPath
        File confFile = new File(confFullPath)
        if (!confFile.exists()) {
            throw new IllegalArgumentException("The moqui.conf path [${confFullPath}] was not found.")
        }

        this.confPath = confFullPath
        this.init()
    }

    /** This constructor takes the runtime directory path and conf file path directly. */
    ExecutionContextFactoryImpl(String runtimePath, String confPath) {
        // setup the runtimeFile
        File runtimeFile = new File(runtimePath)
        if (!runtimeFile.exists()) {
            throw new IllegalArgumentException("The moqui.runtime path [${runtimePath}] was not found.")
        }

        // setup the confFile
        if (runtimePath.endsWith('/')) runtimePath = runtimePath.substring(0, runtimePath.length()-1)
        if (confPath.startsWith('/')) confPath = confPath.substring(1)
        String confFullPath = runtimePath + '/' + confPath
        File confFile = new File(confFullPath)
        if (!confFile.exists()) {
            throw new IllegalArgumentException("The moqui.conf path [${confFullPath}] was not found.")
        }

        this.runtimePath = runtimePath
        this.confPath = confFullPath

        this.init()
    }

    /** Initialize all permanent framework objects, ie those not sensitive to webapp or user context. */
    protected void init() {
        logger.info("Initializing Moqui ExecutionContextFactoryImpl\n - runtime directory [${this.runtimePath}]\n - config file [${this.confPath}]\n - moqui.runtime property [${System.getProperty("moqui.runtime")}]")

        URL defaultConfUrl = this.class.getClassLoader().getResource("MoquiDefaultConf.xml")
        if (!defaultConfUrl) throw new IllegalArgumentException("Could not find MoquiDefaultConf.xml file on the classpath")
        this.confXmlRoot = new XmlParser().parse(defaultConfUrl.newInputStream())

        File confFile = new File(this.confPath)
        Node overrideConfXmlRoot = new XmlParser().parse(confFile)

        // merge the active/override conf file into the default one to override any settings (they both have the same root node, go from there)
        mergeConfigNodes(this.confXmlRoot, overrideConfXmlRoot)

        // init all components in the runtime/component directory
        File componentDir = new File(this.runtimePath + "/component")
        // if directory doesn't exist skip it, component doesn't have an entity directory
        if (componentDir.exists() && componentDir.isDirectory()) {
            // get all files in the directory
            for (File componentSubDir in componentDir.listFiles()) {
                // if it's a directory and doesn't start with a "." then add it as a component dir
                if (componentSubDir.isDirectory() && !componentSubDir.getName().startsWith(".")) {
                    this.initComponent(componentSubDir.toURI().toURL().toString())
                }
            }
        }

        // this init order is important as some facades will use others
        this.cacheFacade = new CacheFacadeImpl(this)
        logger.info("Moqui CacheFacadeImpl Initialized")
        this.loggerFacade = new LoggerFacadeImpl(this)
        logger.info("Moqui LoggerFacadeImpl Initialized")
        this.resourceFacade = new ResourceFacadeImpl(this)
        logger.info("Moqui ResourceFacadeImpl Initialized")
        this.transactionFacade = new TransactionFacadeImpl(this)
        logger.info("Moqui TransactionFacadeImpl Initialized")
        this.entityFacade = new EntityFacadeImpl(this)
        logger.info("Moqui EntityFacadeImpl Initialized")
        this.serviceFacade = new ServiceFacadeImpl(this)
        logger.info("Moqui ServiceFacadeImpl Initialized")
        this.screenFacade = new ScreenFacadeImpl(this)
        logger.info("Moqui ScreenFacadeImpl Initialized")

        logger.info("Moqui ExecutionContextFactoryImpl Initialization Complete")
    }

    synchronized void destroy() {
        if (!this.destroyed) {
            // this destroy order is important as some use others so must be destroyed first
            if (this.serviceFacade) { this.serviceFacade.destroy(); this.serviceFacade = null }
            if (this.entityFacade) { this.entityFacade.destroy(); this.entityFacade = null }
            if (this.transactionFacade) { this.transactionFacade.destroy(); this.transactionFacade = null }
            if (this.cacheFacade) { this.cacheFacade.destroy(); this.cacheFacade = null }

            activeContext.remove()

            this.destroyed = true
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!this.destroyed) {
                this.destroy()
                logger.warn("ExecutionContextFactoryImpl not destroyed, caught in finalize.")
            }
        } catch (Exception e) {
            logger.warn("Error in destroy, called in finalize of ExecutionContextFactoryImpl", e)
        }
        super.finalize()
    }

    String getRuntimePath() { return runtimePath }

    Node getConfXmlRoot() {
        return this.confXmlRoot
    }

    // ========== Getters ==========

    CacheFacadeImpl getCacheFacade() {
        return this.cacheFacade
    }

    EntityFacadeImpl getEntityFacade() {
        return this.entityFacade
    }

    LoggerFacadeImpl getLoggerFacade() {
        return this.loggerFacade
    }

    ResourceFacadeImpl getResourceFacade() {
        return this.resourceFacade
    }

    ScreenFacadeImpl getScreenFacade() {
        return this.screenFacade
    }

    ServiceFacadeImpl getServiceFacade() {
        return this.serviceFacade
    }

    TransactionFacadeImpl getTransactionFacade() {
        return this.transactionFacade
    }

    // ========== Interface Implementations ==========

    /** @see org.moqui.context.ExecutionContextFactory#getExecutionContext() */
    ExecutionContext getExecutionContext() {
        ExecutionContext ec = this.activeContext.get()
        if (ec) {
            return ec
        } else {
            if (logger.infoEnabled) logger.info("Creating new ExecutionContext in thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")
            ec = new ExecutionContextImpl(this)
            this.activeContext.set(ec)
            return ec
        }
    }

    void destroyActiveExecutionContext() {
        ExecutionContext ec = this.activeContext.get()
        if (ec) {
            ec.destroy()
            this.activeContext.remove()
        }
    }

    /** @see org.moqui.context.ExecutionContextFactory#initComponent(String) */
    void initComponent(String baseLocation) throws BaseException {
        // NOTE: how to get component name? for now use last directory name
        if (baseLocation.endsWith('/')) baseLocation = baseLocation.substring(0, baseLocation.length()-1)
        int lastSlashIndex = baseLocation.lastIndexOf('/')
        String componentName
        if (lastSlashIndex < 0) {
            // if this happens the component directory is directly under the runtime directory, so prefix loc with that
            componentName = baseLocation
            baseLocation = this.runtimePath + '/' + baseLocation
        } else {
            componentName = baseLocation.substring(lastSlashIndex+1)
        }

        this.componentLocationMap.put(componentName, baseLocation)
        logger.info("Added component [${componentName}] at [${baseLocation}]")
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroyComponent(String) */
    void destroyComponent(String componentName) throws BaseException {
        this.componentLocationMap.remove(componentName)
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    Map<String, String> getComponentBaseLocations() {
        return Collections.unmodifiableMap(this.componentLocationMap)
    }

    protected void mergeConfigNodes(Node baseNode, Node overrideNode) {
        if (overrideNode."cache-list") {
            mergeNodeWithChildKey(baseNode."cache-list"[0], overrideNode."cache-list"[0], "cache", "name")
        }
        
        if (overrideNode."server-stats") {
            mergeNodeWithChildKey(baseNode."server-stats"[0], overrideNode."server-stats"[0], "artifact-stats", "type")
        }

        if (overrideNode."webapp-list") {
            mergeNodeWithChildKey(baseNode."webapp-list"[0], overrideNode."webapp-list"[0], "webapp", "name")
        }

        if (overrideNode."user-facade") {
            Node ufBaseNode = baseNode."user-facade"[0]
            Node ufOverrideNode = overrideNode."user-facade"[0]
            mergeSingleChild(ufBaseNode, ufOverrideNode, "password")
            mergeSingleChild(ufBaseNode, ufOverrideNode, "login")
        }

        if (overrideNode."transaction-facade") {
            Node tfBaseNode = baseNode."transaction-facade"[0]
            Node tfOverrideNode = overrideNode."transaction-facade"[0]
            mergeSingleChild(tfBaseNode, tfOverrideNode, "server-jndi")
            mergeSingleChild(tfBaseNode, tfOverrideNode, "transaction-factory")
        }

        if (overrideNode."resource-facade") {
            mergeNodeWithChildKey(baseNode."resource-facade"[0], overrideNode."resource-facade"[0], "template-renderer", "extension")
        }

        if (overrideNode."screen-facade") {
            mergeNodeWithChildKey(baseNode."screen-facade"[0], overrideNode."screen-facade"[0], "screen-text-output", "type")
        }

        if (overrideNode."service-facade") {
            Node sfBaseNode = baseNode."service-facade"[0]
            Node sfOverrideNode = overrideNode."service-facade"[0]
            mergeNodeWithChildKey(sfBaseNode, sfOverrideNode, "service-location", "name")
            mergeNodeWithChildKey(sfBaseNode, sfOverrideNode, "service-type", "name")
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
            mergeSingleChild(efBaseNode, efOverrideNode, "server-jndi")
            // for load-entity and load-data just copy over override nodes
            for (Node copyNode in efOverrideNode."load-entity") efBaseNode.append(copyNode)
            for (Node copyNode in efOverrideNode."load-data") efBaseNode.append(copyNode)
        }

        if (overrideNode."database-list") {
            mergeNodeWithChildKey(baseNode."database-list"[0], overrideNode."database-list"[0], "dictionary-type", "type")
            mergeNodeWithChildKey(baseNode."database-list"[0], overrideNode."database-list"[0], "database", "name")
        }

        if (overrideNode."repository-list") {
            mergeNodeWithChildKey(baseNode."repository-list"[0], overrideNode."repository-list"[0], "repository", "name")
        }
    }

    protected void mergeSingleChild(Node baseNode, Node overrideNode, String childNodeName) {
        Node childOverrideNode = (Node) overrideNode[childNodeName][0]
        if (childOverrideNode) {
            Node childBaseNode = (Node) baseNode[childNodeName][0]
            if (childBaseNode) {
                childBaseNode.attributes().putAll(childOverrideNode.attributes())
            } else {
                baseNode.append(childOverrideNode)
            }
        }
    }

    protected void mergeNodeWithChildKey(Node baseNode, Node overrideNode, String childNodesName, String keyAttributeName) {
        // override attributes for this node
        baseNode.attributes().putAll(overrideNode.attributes())

        for (Node childOverrideNode in overrideNode[childNodesName]) {
            String keyValue = childOverrideNode.attribute(keyAttributeName)
            Node childBaseNode = (Node) baseNode[childNodesName].find({ it.attribute(keyAttributeName) == keyValue })

            if (childBaseNode) {
                // merge the node attributes
                childBaseNode.attributes().putAll(childOverrideNode.attributes())

                // merge child nodes for specific nodes
                if ("webapp" == childNodesName) {
                    mergeWebappChildNodes(childBaseNode, childOverrideNode)
                } else if ("database" == childNodesName) {
                    // handle database -> database-type@type
                    mergeNodeWithChildKey(childBaseNode, childOverrideNode, "database-type", "type")
                } else if ("datasource" == childNodesName) {
                    // handle the jndi-jdbc and inline-jdbc nodes: if either exist in override have it totally remove both from base, then copy over
                    if (childOverrideNode."jndi-jdbc" || childOverrideNode."inline-jdbc") {
                        if (childBaseNode."jndi-jdbc") childBaseNode.remove(childBaseNode."jndi-jdbc"[0])
                        if (childBaseNode."inline-jdbc") childBaseNode.remove(childBaseNode."inline-jdbc"[0])

                        if (childOverrideNode."inline-jdbc") {
                            childBaseNode.append(childOverrideNode."inline-jdbc"[0])
                        } else if (childOverrideNode."jndi-jdbc") {
                            childBaseNode.append(childOverrideNode."jndi-jdbc"[0])
                        }
                    }
                }
            } else {
                // no matching child base node, so add a new one
                baseNode.append(childOverrideNode)
            }
        }
    }

    protected void mergeWebappChildNodes(Node baseNode, Node overrideNode) {
        // handle webapp -> first-hit-in-visit[1], after-request[1], before-request[1], after-login[1], before-logout[1], root-screen[1]
        mergeWebappActions(baseNode, overrideNode, "first-hit-in-visit")
        mergeWebappActions(baseNode, overrideNode, "after-request")
        mergeWebappActions(baseNode, overrideNode, "before-request")
        mergeWebappActions(baseNode, overrideNode, "after-login")
        mergeWebappActions(baseNode, overrideNode, "before-logout")
    }

    protected void mergeWebappActions(Node baseWebappNode, Node overrideWebappNode, String childNodeName) {
        List overrideActionNodes = overrideWebappNode[childNodeName]?.getAt(0)?.actions?.getAt(0)?.children()
        if (overrideActionNodes) {
            Node childNode = (Node) baseWebappNode[childNodeName][0]
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
