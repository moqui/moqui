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

import java.sql.Timestamp
import java.util.jar.JarFile

import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.L10nFacade
import org.moqui.context.ResourceReference
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.screen.ScreenFacadeImpl
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.StupidClassLoader
import org.moqui.impl.StupidUtilities

import org.apache.shiro.authc.credential.CredentialsMatcher
import org.apache.shiro.authc.credential.HashedCredentialsMatcher
import org.apache.shiro.crypto.hash.SimpleHash
import org.apache.shiro.config.IniSecurityManagerFactory
import org.apache.shiro.SecurityUtils
import org.apache.commons.collections.map.ListOrderedMap

import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.moqui.impl.service.camel.MoquiServiceComponent
import org.moqui.impl.service.camel.MoquiServiceConsumer

import org.elasticsearch.node.NodeBuilder
import org.elasticsearch.client.Client

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExecutionContextFactoryImpl implements ExecutionContextFactory {
    protected final static Logger logger = LoggerFactory.getLogger(ExecutionContextFactoryImpl.class)
    
    protected boolean destroyed = false
    
    protected final String runtimePath

    protected final String confPath
    protected final Node confXmlRoot

    protected StupidClassLoader cachedClassLoader

    protected final ListOrderedMap componentLocationMap = new ListOrderedMap()

    protected ThreadLocal<ExecutionContextImpl> activeContext = new ThreadLocal<ExecutionContextImpl>()

    protected Map<String, EntityFacadeImpl> entityFacadeByTenantMap = new HashMap<String, EntityFacadeImpl>()

    protected Map<String, Map<String, Object>> artifactHitBinByType = new HashMap()

    protected Map<String, WebappInfo> webappInfoMap = new HashMap()

    /** The SecurityManager for Apache Shiro */
    protected org.apache.shiro.mgt.SecurityManager internalSecurityManager

    /** The central object of the Camel API: CamelContext */
    protected final CamelContext camelContext
    protected MoquiServiceComponent moquiServiceComponent
    protected Map<String, MoquiServiceConsumer> camelConsumerByUriMap = new HashMap<String, MoquiServiceConsumer>()

    /* ElasticSearch fields */
    org.elasticsearch.node.Node elasticSearchNode
    Client elasticSearchClient

    // ======== Permanent Delegated Facades ========
    protected final CacheFacadeImpl cacheFacade
    protected final LoggerFacadeImpl loggerFacade
    protected final ResourceFacadeImpl resourceFacade
    protected final ScreenFacadeImpl screenFacade
    protected final ServiceFacadeImpl serviceFacade
    protected final TransactionFacadeImpl transactionFacade
    protected final L10nFacadeImpl l10nFacade

    // Some direct-cached values for better performance
    protected String skipStatsCond
    protected Integer hitBinLengthMillis
    protected Map<String, Boolean> artifactPersistHitByType = new HashMap<String, Boolean>()
    protected Map<String, Boolean> artifactPersistBinByType = new HashMap<String, Boolean>()

    /**
     * This constructor gets runtime directory and conf file location from a properties file on the classpath so that
     * it can initialize on its own. This is the constructor to be used by the ServiceLoader in the Moqui.java file,
     * or by init methods in a servlet or context filter or OSGi component or Spring component or whatever.
     */
    ExecutionContextFactoryImpl() {
        // get the MoquiInit.properties file
        Properties moquiInitProperties = new Properties()
        URL initProps = this.class.getClassLoader().getResource("MoquiInit.properties")
        if (initProps != null) { InputStream is = initProps.openStream(); moquiInitProperties.load(is); is.close(); }

        // if there is a system property use that, otherwise from the properties file
        this.runtimePath = System.getProperty("moqui.runtime")
        if (!this.runtimePath) this.runtimePath = moquiInitProperties.getProperty("moqui.runtime")
        if (!this.runtimePath)
            throw new IllegalArgumentException("No moqui.runtime property found in MoquiInit.properties or in a system property (with: -Dmoqui.runtime=... on the command line).")

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
        if (!confPartialPath) confPartialPath = moquiInitProperties.getProperty("moqui.conf")
        if (!confPartialPath)
            throw new IllegalArgumentException("No moqui.conf property found in MoquiInit.properties or in a system property (with: -Dmoqui.conf=... on the command line).")

        // setup the confFile
        if (confPartialPath.startsWith("/")) confPartialPath = confPartialPath.substring(1)
        String confFullPath = this.runtimePath + "/" + confPartialPath
        File confFile = new File(confFullPath)
        if (confFile.exists()) {
            this.confPath = confFullPath
        } else {
            this.confPath = null
            logger.warn("The moqui.conf path [${confFullPath}] was not found.")
        }

        confXmlRoot = this.initConfig()
        skipStatsCond = confXmlRoot."server-stats"[0]."@stats-skip-condition"
        hitBinLengthMillis = (confXmlRoot."server-stats"[0]."@bin-length-seconds" as Integer)*1000 ?: 900000

        // must load components before ClassLoader since ClassLoader currently adds lib and classes directories at init time
        initComponents()
        // init ClassLoader early so that classpath:// resources and framework interface impls will work
        initClassLoader()

        // setup the CamelContext, but don't init yet
        camelContext = new DefaultCamelContext()

        // this init order is important as some facades will use others
        this.cacheFacade = new CacheFacadeImpl(this)
        logger.info("Moqui CacheFacadeImpl Initialized")
        this.loggerFacade = new LoggerFacadeImpl(this)
        logger.info("Moqui LoggerFacadeImpl Initialized")
        this.resourceFacade = new ResourceFacadeImpl(this)
        logger.info("Moqui ResourceFacadeImpl Initialized")

        this.transactionFacade = new TransactionFacadeImpl(this)
        logger.info("Moqui TransactionFacadeImpl Initialized")
        // always init the EntityFacade for tenantId DEFAULT
        this.entityFacadeByTenantMap.put("DEFAULT", new EntityFacadeImpl(this, "DEFAULT"))
        logger.info("Moqui EntityFacadeImpl for DEFAULT Tenant Initialized")
        this.serviceFacade = new ServiceFacadeImpl(this)
        logger.info("Moqui ServiceFacadeImpl Initialized")
        this.screenFacade = new ScreenFacadeImpl(this)
        logger.info("Moqui ScreenFacadeImpl Initialized")
        this.l10nFacade = new L10nFacadeImpl(this)
        logger.info("Moqui L10nFacadeImpl Initialized")

        // init ElasticSearch after facades, before Camel
        initElasticSearch()

        // everything else ready to go, init Camel
        this.initCamel()

        // now that everything is started up, if configured check all entity tables
        this.entityFacade.checkInitDatasourceTables()

        logger.info("Moqui ExecutionContextFactory Initialization Complete")
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

        this.confXmlRoot = this.initConfig()

        // must load components before ClassLoader since ClassLoader currently adds lib and classes directories at init time
        initComponents()
        // init ClassLoader early so that classpath:// resources and framework interface impls will work
        initClassLoader()

        // setup the CamelContext, but don't init yet
        camelContext = new DefaultCamelContext()

        // this init order is important as some facades will use others
        this.cacheFacade = new CacheFacadeImpl(this)
        logger.info("Moqui CacheFacadeImpl Initialized")
        this.loggerFacade = new LoggerFacadeImpl(this)
        logger.info("Moqui LoggerFacadeImpl Initialized")
        this.resourceFacade = new ResourceFacadeImpl(this)
        logger.info("Moqui ResourceFacadeImpl Initialized")

        this.transactionFacade = new TransactionFacadeImpl(this)
        logger.info("Moqui TransactionFacadeImpl Initialized")
        // always init the EntityFacade for tenantId DEFAULT
        this.entityFacadeByTenantMap.put("DEFAULT", new EntityFacadeImpl(this, "DEFAULT"))
        logger.info("Moqui EntityFacadeImpl for DEFAULT Tenant Initialized")
        this.serviceFacade = new ServiceFacadeImpl(this)
        logger.info("Moqui ServiceFacadeImpl Initialized")
        this.screenFacade = new ScreenFacadeImpl(this)
        logger.info("Moqui ScreenFacadeImpl Initialized")
        this.l10nFacade = new L10nFacadeImpl(this)
        logger.info("Moqui L10nFacadeImpl Initialized")

        // init ElasticSearch after facades, before Camel
        initElasticSearch()

        // everything else ready to go, init Camel
        this.initCamel()

        // now that everything is started up, if configured check all entity tables
        this.entityFacade.checkInitDatasourceTables()

        logger.info("Moqui ExecutionContextFactory Initialization Complete")
    }

    /** Initialize all permanent framework objects, ie those not sensitive to webapp or user context. */
    protected Node initConfig() {
        logger.info("Initializing Moqui ExecutionContextFactoryImpl\n - runtime directory [${this.runtimePath}]\n - config file [${this.confPath}]\n - moqui.runtime property [${System.getProperty("moqui.runtime")}]")

        URL defaultConfUrl = this.class.getClassLoader().getResource("MoquiDefaultConf.xml")
        if (!defaultConfUrl) throw new IllegalArgumentException("Could not find MoquiDefaultConf.xml file on the classpath")
        Node newConfigXmlRoot = new XmlParser().parse(defaultConfUrl.newInputStream())

        if (this.confPath) {
            File confFile = new File(this.confPath)
            Node overrideConfXmlRoot = new XmlParser().parse(confFile)

            // merge the active/override conf file into the default one to override any settings (they both have the same root node, go from there)
            mergeConfigNodes(newConfigXmlRoot, overrideConfXmlRoot)
        }

        return newConfigXmlRoot
    }

    protected void initComponents() {
        // the default directory for components
        initComponentsRuntimeDir("component")
        // a little special treatment for mantle components
        initComponentsRuntimeDir("mantle")

        // init components referred to in component-list.component elements in the conf file
        if (confXmlRoot."component-list"?.getAt(0)?."component") {
            for (Node componentNode in confXmlRoot."component-list"[0]."component") {
                this.initComponent((String) componentNode."@name", (String) componentNode."@location")
            }
        }
    }

    protected void initComponentsRuntimeDir(String dirName) {
        // init all components in the runtime/component directory
        File componentDir = new File(this.runtimePath + "/" + dirName)
        // if directory doesn't exist skip it, runtime doesn't always have an component directory
        if (componentDir.exists() && componentDir.isDirectory()) {
            // get all files in the directory
            TreeMap<String, File> componentDirEntries = new TreeMap<String, File>()
            for (File componentSubDir in componentDir.listFiles()) {
                // if it's a directory and doesn't start with a "." then add it as a component dir
                if (!componentSubDir.isDirectory() || componentSubDir.getName().startsWith(".")) continue
                componentDirEntries.put(componentSubDir.getName(), componentSubDir)
            }
            for (Map.Entry<String, File> componentDirEntry in componentDirEntries) {
                this.initComponent(null, componentDirEntry.getValue().toURI().toURL().toString())
            }
        }
    }

    protected void initClassLoader() {
        // now setup the CachedClassLoader, this should init in the main thread so we can set it properly
        ClassLoader pcl = (Thread.currentThread().getContextClassLoader() ?: this.class.classLoader) ?: System.classLoader
        cachedClassLoader = new StupidClassLoader(pcl)
        Thread.currentThread().setContextClassLoader(cachedClassLoader)
        // add runtime/classes jar files to the class loader
        File runtimeClassesFile = new File(runtimePath + "/classes")
        if (runtimeClassesFile.exists()) {
            cachedClassLoader.addClassesDirectory(runtimeClassesFile)
        }
        // add runtime/lib jar files to the class loader
        File runtimeLibFile = new File(runtimePath + "/lib")
        if (runtimeLibFile.exists()) {
            for (File jarFile: runtimeLibFile.listFiles()) {
                if (jarFile.getName().endsWith(".jar")) {
                    cachedClassLoader.addJarFile(new JarFile(jarFile))
                    logger.info("Added JAR from runtime/lib: ${jarFile.getName()}")
                }
            }
        }
    }

    /** this is called by the ResourceFacadeImpl constructor right after the ResourceReference classes are loaded but before ScriptRunners and TemplateRenderers */
    protected void initComponentLibAndClasses(ResourceFacadeImpl rfi) {
        // add <component>/classes and <component>/lib jar files to the class loader now that component locations loaded
        for (Map.Entry componentEntry in componentBaseLocations) {
            ResourceReference classesRr = rfi.getLocationReference(componentEntry.value + "/classes")
            if (classesRr.supportsExists() && classesRr.exists && classesRr.supportsDirectory() && classesRr.isDirectory()) {
                cachedClassLoader.addClassesDirectory(new File(classesRr.getUri()))
            }

            ResourceReference libRr = rfi.getLocationReference(componentEntry.value + "/lib")
            if (libRr.supportsExists() && libRr.exists && libRr.supportsDirectory() && libRr.isDirectory()) {
                for (ResourceReference jarRr: libRr.getDirectoryEntries()) {
                    if (jarRr.fileName.endsWith(".jar")) {
                        try {
                            cachedClassLoader.addJarFile(new JarFile(new File(jarRr.uri)))
                            logger.info("Added JAR from [${componentEntry.key}] component: ${jarRr.uri}")
                        } catch (Exception e) {
                            logger.info("Could not load JAR from [${componentEntry.key}] component: ${jarRr.location}: ${e.toString()}")
                        }
                    }
                }
            }
        }
    }

    protected void initElasticSearch() {
        // set the ElasticSearch home directory
        System.setProperty("es.path.home", runtimePath + "/elasticsearch")
        elasticSearchNode = NodeBuilder.nodeBuilder().node()
        elasticSearchClient = elasticSearchNode.client()
    }

    protected void initCamel() {
        moquiServiceComponent = new MoquiServiceComponent(this)
        camelContext.addComponent("moquiservice", moquiServiceComponent)
        camelContext.start()
    }

    synchronized void destroy() {
        if (!this.destroyed) {
            // first stop Camel to prevent more calls coming in
            camelContext.stop()

            // stop ElasticSearch
            elasticSearchNode.close()

            // persist any remaining bins in artifactHitBinByType
            Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis())
            List<Map<String, Object>> ahbList = new ArrayList<Map<String, Object>>(artifactHitBinByType.values())
            artifactHitBinByType.clear()
            for (Map<String, Object> ahb in ahbList) {
                ahb.binEndDateTime = currentTimestamp
                executionContext.service.sync().name("create", "moqui.server.ArtifactHitBin").parameters(ahb).call()
            }

            // this destroy order is important as some use others so must be destroyed first
            if (this.serviceFacade) { this.serviceFacade.destroy() }
            if (this.entityFacade) { this.entityFacade.destroy() }
            if (this.transactionFacade) { this.transactionFacade.destroy() }
            if (this.cacheFacade) { this.cacheFacade.destroy() }

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
    Node getConfXmlRoot() { return confXmlRoot }

    org.apache.shiro.mgt.SecurityManager getSecurityManager() {
        if (internalSecurityManager != null) return internalSecurityManager

        // init Apache Shiro; NOTE: init must be done here so that ecfi will be fully initialized and in the static context
        org.apache.shiro.util.Factory<org.apache.shiro.mgt.SecurityManager> factory =
                new IniSecurityManagerFactory("classpath:shiro.ini")
        internalSecurityManager = factory.getInstance()
        // NOTE: setting this statically just in case something uses it, but for Moqui we'll be getting the SecurityManager from the ecfi
        SecurityUtils.setSecurityManager(internalSecurityManager)

        return internalSecurityManager
    }
    CredentialsMatcher getCredentialsMatcher(String hashType) {
        HashedCredentialsMatcher hcm = new HashedCredentialsMatcher()
        if (hashType) {
            hcm.setHashAlgorithmName(hashType)
        } else {
            hcm.setHashAlgorithmName(getPasswordHashType())
        }
        return hcm
    }
    static String getRandomSalt() { return StupidUtilities.getRandomString(8) }
    String getPasswordHashType() {
        Node passwordNode = confXmlRoot."user-facade"[0]."password"[0]
        return passwordNode."@encrypt-hash-type" ?: "SHA-256"
    }
    String getSimpleHash(String source, String salt) { return new SimpleHash(passwordHashType, source, salt).toString() }

    // ========== Getters ==========

    CacheFacadeImpl getCacheFacade() { return this.cacheFacade }

    EntityFacadeImpl getEntityFacade() { return getEntityFacade(executionContext.tenantId) }
    EntityFacadeImpl getEntityFacade(String tenantId) {
        EntityFacadeImpl efi = this.entityFacadeByTenantMap.get(tenantId)
        if (efi == null) efi = initEntityFacade(tenantId)
        return efi
    }
    synchronized EntityFacadeImpl initEntityFacade(String tenantId) {
        EntityFacadeImpl efi = this.entityFacadeByTenantMap.get(tenantId)
        if (efi != null) return efi

        efi = new EntityFacadeImpl(this, tenantId)
        this.entityFacadeByTenantMap.put(tenantId, efi)
        logger.info("Moqui EntityFacadeImpl for Tenant [${tenantId}] Initialized")
        return efi
    }

    LoggerFacadeImpl getLoggerFacade() { return loggerFacade }

    ResourceFacadeImpl getResourceFacade() { return resourceFacade }

    ScreenFacadeImpl getScreenFacade() { return screenFacade }

    ServiceFacadeImpl getServiceFacade() { return serviceFacade }

    TransactionFacadeImpl getTransactionFacade() { return transactionFacade }

    L10nFacade getL10nFacade() { return l10nFacade }

    CamelContext getCamelContext() { return camelContext }
    MoquiServiceComponent getMoquiServiceComponent() { return moquiServiceComponent }
    void registerCamelConsumer(String uri, MoquiServiceConsumer consumer) { camelConsumerByUriMap.put(uri, consumer) }
    MoquiServiceConsumer getCamelConsumer(String uri) { return camelConsumerByUriMap.get(uri) }

    Client getElasticSearchClient() { return elasticSearchClient }

    // ========== Interface Implementations ==========

    @Override
    ExecutionContext getExecutionContext() {
        ExecutionContextImpl ec = this.activeContext.get()
        if (ec) {
            return ec
        } else {
            if (logger.traceEnabled) logger.trace("Creating new ExecutionContext in thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")
            if (!(Thread.currentThread().getContextClassLoader() instanceof StupidClassLoader))
                Thread.currentThread().setContextClassLoader(cachedClassLoader)
            ec = new ExecutionContextImpl(this)
            this.activeContext.set(ec)
            return ec
        }
    }

    ExecutionContextImpl getEci() { return (ExecutionContextImpl) this.executionContext }

    void destroyActiveExecutionContext() {
        ExecutionContext ec = this.activeContext.get()
        if (ec) {
            ec.destroy()
            this.activeContext.remove()
        }
    }

    @Override
    void initComponent(String componentName, String baseLocation) throws BaseException {
        // NOTE: how to get component name? for now use last directory name
        if (baseLocation.endsWith('/')) baseLocation = baseLocation.substring(0, baseLocation.length()-1)
        int lastSlashIndex = baseLocation.lastIndexOf('/')
        if (lastSlashIndex < 0) {
            // if this happens the component directory is directly under the runtime directory, so prefix loc with that
            baseLocation = runtimePath + '/' + baseLocation
        }
        if (!componentName) componentName = baseLocation.substring(lastSlashIndex+1)

        if (componentLocationMap.containsKey(componentName))
            logger.warn("Overriding component [${componentName}] at [${componentLocationMap.get(componentName)}] with location [${baseLocation}] because another component of the same name was initialized.")
        // put at the beginning of the Map/List, components registered later override those registered earlier
        componentLocationMap.put(0, componentName, baseLocation)
        logger.info("Added component [${componentName}] at [${baseLocation}]")
    }

    @Override
    void destroyComponent(String componentName) throws BaseException { componentLocationMap.remove(componentName) }

    @Override
    Map<String, String> getComponentBaseLocations() {
        return Collections.unmodifiableMap(componentLocationMap)
    }

    // ========== Server Stat Tracking ==========
    boolean getSkipStats() {
        // NOTE: the results of this condition eval can't be cached because the expression can use any data in the ec
        return (skipStatsCond && getEci().getResource().evaluateCondition(skipStatsCond, null))
    }

    protected boolean artifactPersistHit(String artifactType, String artifactSubType) {
        String cacheKey = artifactType + "#" + artifactSubType
        Boolean ph = artifactPersistHitByType.get(cacheKey)
        if (ph == null) {
            Node artifactStats = getArtifactStatsNode(artifactType, artifactSubType)
            ph = artifactStats."@persist-hit" == "true"
            artifactPersistHitByType.put(cacheKey, ph)
        }
        return ph
    }
    protected boolean artifactPersistBin(String artifactType, String artifactSubType) {
        String cacheKey = artifactType + "#" + artifactSubType
        Boolean ph = artifactPersistBinByType.get(cacheKey)
        if (ph == null) {
            Node artifactStats = getArtifactStatsNode(artifactType, artifactSubType)
            ph = artifactStats."@persist-bin" == "true"
            artifactPersistBinByType.put(cacheKey, ph)
        }
        return ph
    }

    protected Node getArtifactStatsNode(String artifactType, String artifactSubType) {
        // find artifact-stats node by type AND sub-type, if not found find by just the type
        Node artifactStats = (Node) confXmlRoot."server-stats"[0]."artifact-stats".find({ it.@type == artifactType && it."@sub-type" == artifactSubType })
        if (artifactStats == null) artifactStats = (Node) confXmlRoot."server-stats"[0]."artifact-stats".find({ it.@type == artifactType })
        return artifactStats
    }

    void countArtifactHit(String artifactType, String artifactSubType, String artifactName, Map parameters,
                          long startTime, long endTime, Long outputSize) {
        // don't count the ones this calls
        if (artifactType == "service" && artifactName.contains("moqui.server.ArtifactHit")) return
        if (artifactType == "entity" && artifactName == "moqui.server.ArtifactHit") return

        ExecutionContextImpl eci = this.getEci()
        long runningTimeMillis = endTime - startTime

        // NOTE: never save hits for entity artifact hits, way too heavy and also avoids self-reference (could also be done by checking for ArtifactHit/etc of course)
        if (!"entity".equals(artifactType) && artifactPersistHit(artifactType, artifactSubType) && !getSkipStats()) {
            Map<String, Object> ahp = (Map<String, Object>) [visitId:eci.user.visitId, userId:eci.user.userId,
                artifactType:artifactType, artifactSubType:artifactSubType, artifactName:artifactName,
                startDateTime:new Timestamp(startTime), runningTimeMillis:runningTimeMillis]

            if (parameters) {
                StringBuilder ps = new StringBuilder()
                for (Map.Entry pme in parameters) {
                    if (!pme.value) continue
                    if (ps.length() > 0) ps.append(",")
                    ps.append(pme.key).append("=").append(pme.value)
                }
                if (ps.length() > 255) ps.delete(255, ps.length())
                ahp.parameterString = ps.toString()
            }
            if (outputSize != null) ahp.outputSize = outputSize
            if (eci.getMessage().hasError()) {
                ahp.wasError = "Y"
                StringBuilder errorMessage = new StringBuilder()
                for (String curErr in eci.message.errors) errorMessage.append(curErr).append(";")
                if (errorMessage.length() > 255) errorMessage.delete(255, errorMessage.length())
                ahp.errorMessage = errorMessage
            } else {
                ahp.wasError = "N"
            }
            if (eci.web != null) {
                String fullUrl = eci.web.requestUrl
                fullUrl = (fullUrl.length() > 255) ? fullUrl.substring(0, 255) : fullUrl.toString()
                ahp.requestUrl = fullUrl
                ahp.referrerUrl = eci.web.request.getHeader("Referrer") ?: ""
            }
            try {
                InetAddress address = InetAddress.getLocalHost()
                if (address) {
                    ahp.serverIpAddress = address.getHostAddress()
                    ahp.serverHostName = address.getHostName()
                }
            } catch (UnknownHostException e) {
                logger.warn("Could not get localhost address", new BaseException("Could not get localhost address", e))
            }

            // call async, let the server do it whenever
            eci.service.async().name("create", "moqui.server.ArtifactHit").parameters(ahp).call()
        }
        if (artifactPersistBin(artifactType, artifactSubType) && !getSkipStats()) {
            Map<String, Object> ahb = artifactHitBinByType.get(artifactType + "." + artifactSubType + ":" + artifactName)
            if (ahb == null) ahb = makeArtifactHitBinMap(artifactType, artifactSubType, artifactName, startTime)

            // has the current bin expired since the last hit record?
            long binStartTime = ((Timestamp) ahb.get("binStartDateTime")).time
            if (startTime > (binStartTime + hitBinLengthMillis)) {
                if (logger.infoEnabled) logger.info("Advancing ArtifactHitBin [${artifactType}.${artifactSubType}:${artifactName}] current hit start [${new Timestamp(startTime)}], bin start [${ahb.get("binStartDateTime")}] bin length ${hitBinLengthMillis/1000} seconds")
                ahb = advanceArtifactHitBin(artifactType, artifactSubType, artifactName, startTime, hitBinLengthMillis)
            } else {
                if (logger.traceEnabled) logger.trace("Adding to ArtifactHitBin [${artifactType}.${artifactSubType}:${artifactName}] current hit start [${new Timestamp(startTime)}], bin start [${ahb.get("binStartDateTime")}] bin length ${hitBinLengthMillis/1000} seconds")
            }

            ahb.hitCount += 1
            ahb.totalTimeMillis += runningTimeMillis
            if (runningTimeMillis < ahb.minTimeMillis) ahb.minTimeMillis = runningTimeMillis
            if (runningTimeMillis > ahb.maxTimeMillis) ahb.maxTimeMillis = runningTimeMillis
        }
    }
    protected synchronized Map<String, Object> advanceArtifactHitBin(String artifactType, String artifactSubType,
                                                     String artifactName, long startTime, int hitBinLengthMillis) {
        Map<String, Object> ahb = artifactHitBinByType.get(artifactType + "." + artifactSubType + ":" + artifactName)
        if (ahb == null) return makeArtifactHitBinMap(artifactType, artifactSubType, artifactName, startTime)

        long binStartTime = ((Timestamp) ahb.get("binStartDateTime")).time

        // check the time again and return just in case something got in while waiting with the same type
        if (startTime < (binStartTime + hitBinLengthMillis)) return ahb

        // otherwise, persist the old (async so this is fast) and create a new one
        ahb.binEndDateTime = new Timestamp(binStartTime + hitBinLengthMillis)
        executionContext.service.async().name("create", "moqui.server.ArtifactHitBin").parameters(ahb).call()

        return makeArtifactHitBinMap(artifactType, artifactSubType, artifactName, startTime)
    }
    protected Map<String, Object> makeArtifactHitBinMap(String artifactType, String artifactSubType,
                                                        String artifactName, long startTime) {
        Map<String, Object> ahb = (Map<String, Object>) [artifactType:artifactType, artifactSubType:artifactSubType,
                artifactName:artifactName, binStartDateTime:new Timestamp(startTime), binEndDateTime:null,
                hitCount:0, totalTimeMillis:0, minTimeMillis:Long.MAX_VALUE, maxTimeMillis:0]
        try {
            InetAddress address = InetAddress.getLocalHost()
            if (address) {
                ahb.serverIpAddress = address.getHostAddress()
                ahb.serverHostName = address.getHostName()
            }
        } catch (UnknownHostException e) {
            logger.warn("Could not get localhost address", new BaseException("Could not get localhost address", e))
        }
        artifactHitBinByType.put(artifactType + "." + artifactSubType + ":" + artifactName, ahb)
        return ahb
    }

    // ========== Configuration File Merging Methods ==========

    protected void mergeConfigNodes(Node baseNode, Node overrideNode) {
        if (overrideNode."cache-list") {
            mergeNodeWithChildKey((Node) baseNode."cache-list"[0], (Node) overrideNode."cache-list"[0], "cache", "name")
        }
        
        if (overrideNode."server-stats") {
            // the artifact-stats nodes have 2 keys: type, sub-type; can't use the normal method
            Node ssNode = baseNode."server-stats"[0]
            Node overrideSsNode = overrideNode."server-stats"[0]
            // override attributes for this node
            ssNode.attributes().putAll(overrideSsNode.attributes())
            for (Node childOverrideNode in overrideSsNode["artifact-stats"]) {
                String type = childOverrideNode.attribute("type")
                String subType = childOverrideNode.attribute("sub-type")
                Node childBaseNode = (Node) ssNode["artifact-stats"]?.find({ it."@type" == type &&
                        (it."@sub-type" == subType || (!it."@sub-type" && !subType)) })
                if (childBaseNode) {
                    // merge the node attributes
                    childBaseNode.attributes().putAll(childOverrideNode.attributes())
                } else {
                    // no matching child base node, so add a new one
                    ssNode.append(childOverrideNode)
                }
            }
        }

        if (overrideNode."webapp-list") {
            mergeNodeWithChildKey((Node) baseNode."webapp-list"[0], (Node) overrideNode."webapp-list"[0], "webapp", "name")
        }

        if (overrideNode."artifact-execution-facade") {
            mergeNodeWithChildKey((Node) baseNode."artifact-execution-facade"[0],
                    (Node) overrideNode."artifact-execution-facade"[0], "artifact-execution", "type")
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
            mergeNodeWithChildKey((Node) baseNode."resource-facade"[0], (Node) overrideNode."resource-facade"[0],
                    "resource-reference", "scheme")
            mergeNodeWithChildKey((Node) baseNode."resource-facade"[0], (Node) overrideNode."resource-facade"[0],
                    "template-renderer", "extension")
            mergeNodeWithChildKey((Node) baseNode."resource-facade"[0], (Node) overrideNode."resource-facade"[0],
                    "script-runner", "extension")
        }

        if (overrideNode."screen-facade") {
            mergeNodeWithChildKey((Node) baseNode."screen-facade"[0], (Node) overrideNode."screen-facade"[0],
                    "screen-text-output", "type")
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
            mergeNodeWithChildKey((Node) baseNode."database-list"[0], (Node) overrideNode."database-list"[0], "dictionary-type", "type")
            mergeNodeWithChildKey((Node) baseNode."database-list"[0], (Node) overrideNode."database-list"[0], "database", "name")
        }

        if (overrideNode."repository-list") {
            mergeNodeWithChildKey((Node) baseNode."repository-list"[0], (Node) overrideNode."repository-list"[0], "repository", "name")
        }

        if (overrideNode."component-list") {
            if (!baseNode."component-list") baseNode.appendNode("component-list")
            mergeNodeWithChildKey((Node) baseNode."component-list"[0], (Node) overrideNode."component-list"[0], "component", "name")
        }
    }

    protected static void mergeSingleChild(Node baseNode, Node overrideNode, String childNodeName) {
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
            Node childBaseNode = (Node) baseNode[childNodesName]?.find({ it.attribute(keyAttributeName) == keyValue })

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
                        if (childBaseNode."jndi-jdbc") childBaseNode.remove((Node) childBaseNode."jndi-jdbc"[0])
                        if (childBaseNode."inline-jdbc") childBaseNode.remove((Node) childBaseNode."inline-jdbc"[0])

                        if (childOverrideNode."inline-jdbc") {
                            childBaseNode.append((Node) childOverrideNode."inline-jdbc"[0])
                        } else if (childOverrideNode."jndi-jdbc") {
                            childBaseNode.append((Node) childOverrideNode."jndi-jdbc"[0])
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
        mergeNodeWithChildKey(baseNode, overrideNode, "root-screen", "host")
        // handle webapp -> first-hit-in-visit[1], after-request[1], before-request[1], after-login[1], before-logout[1], root-screen[1]
        mergeWebappActions(baseNode, overrideNode, "first-hit-in-visit")
        mergeWebappActions(baseNode, overrideNode, "after-request")
        mergeWebappActions(baseNode, overrideNode, "before-request")
        mergeWebappActions(baseNode, overrideNode, "after-login")
        mergeWebappActions(baseNode, overrideNode, "before-logout")
        mergeWebappActions(baseNode, overrideNode, "after-startup")
        mergeWebappActions(baseNode, overrideNode, "before-shutdown")
    }

    protected static void mergeWebappActions(Node baseWebappNode, Node overrideWebappNode, String childNodeName) {
        List<Node> overrideActionNodes = overrideWebappNode.get(childNodeName)?.getAt(0)?."actions"?.getAt(0)?.children()
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

    Node getWebappNode(String webappName) { return (Node) confXmlRoot."webapp-list"[0]."webapp".find({ it."@name" == webappName }) }

    WebappInfo getWebappInfo(String webappName) {
        if (webappInfoMap.containsKey(webappName)) return webappInfoMap.get(webappName)
        return makeWebappInfo(webappName)
    }
    protected synchronized WebappInfo makeWebappInfo(String webappName) {
        WebappInfo wi = new WebappInfo(webappName, this)
        webappInfoMap.put(webappName, wi)
        return wi
    }

    static class WebappInfo {
        String webappName
        XmlAction firstHitInVisitActions = null
        XmlAction beforeRequestActions = null
        XmlAction afterRequestActions = null
        XmlAction afterLoginActions = null
        XmlAction beforeLogoutActions = null
        XmlAction afterStartupActions = null
        XmlAction beforeShutdownActions = null

        WebappInfo(String webappName, ExecutionContextFactoryImpl ecfi) {
            this.webappName = webappName
            // prep actions
            Node webappNode = ecfi.getWebappNode(webappName)
            if (webappNode."first-hit-in-visit")
                this.firstHitInVisitActions = new XmlAction(ecfi, (Node) webappNode."first-hit-in-visit"[0]."actions"[0],
                        "webapp_${webappName}.first_hit_in_visit.actions")

            if (webappNode."before-request")
                this.beforeRequestActions = new XmlAction(ecfi, (Node) webappNode."before-request"[0]."actions"[0],
                        "webapp_${webappName}.before_request.actions")
            if (webappNode."after-request")
                this.afterRequestActions = new XmlAction(ecfi, (Node) webappNode."after-request"[0]."actions"[0],
                        "webapp_${webappName}.after_request.actions")

            if (webappNode."after-login")
                this.afterLoginActions = new XmlAction(ecfi, (Node) webappNode."after-login"[0]."actions"[0],
                        "webapp_${webappName}.after_login.actions")
            if (webappNode."before-logout")
                this.beforeLogoutActions = new XmlAction(ecfi, (Node) webappNode."before-logout"[0]."actions"[0],
                        "webapp_${webappName}.before_logout.actions")

            if (webappNode."after-startup")
                this.afterStartupActions = new XmlAction(ecfi, (Node) webappNode."after-startup"[0]."actions"[0],
                        "webapp_${webappName}.after_startup.actions")
            if (webappNode."before-shutdown")
                this.beforeShutdownActions = new XmlAction(ecfi, (Node) webappNode."before-shutdown"[0]."actions"[0],
                        "webapp_${webappName}.before_shutdown.actions")
        }
    }

    @Override
    String toString() { return "ExecutionContextFactory" }
}
