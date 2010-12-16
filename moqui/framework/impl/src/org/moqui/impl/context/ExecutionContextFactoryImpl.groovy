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
import java.util.Properties
import java.net.URL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import groovy.util.slurpersupport.GPathResult
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.service.ServiceFacadeImpl

public class ExecutionContextFactoryImpl implements ExecutionContextFactory {

    protected final static Logger logger = LoggerFactory.getLogger(ExecutionContextFactoryImpl.class)
    
    protected boolean destroyed = false
    
    protected final String runtimePath

    protected final String confPath
    protected final GPathResult confXmlRoot
    
    protected final GPathResult defaultConfXmlRoot

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
    public ExecutionContextFactoryImpl() {
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
    public ExecutionContextFactoryImpl(String runtimePath, String confPath) {
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
        File confFile = new File(this.confPath)
        this.confXmlRoot = new XmlSlurper().parse(confFile)

        URL defaultConfUrl = ClassLoader.getSystemResource("MoquiDefaultConf.xml")
        this.defaultConfXmlRoot = new XmlSlurper().parse(defaultConfUrl.newInputStream())

        // this init order is important as some facades will use others
        this.cacheFacade = new CacheFacadeImpl(this)
        this.loggerFacade = new LoggerFacadeImpl(this)
        this.resourceFacade = new ResourceFacadeImpl(this)
        this.transactionFacade = new TransactionFacadeImpl(this)
        this.entityFacade = new EntityFacadeImpl(this)
        this.serviceFacade = new ServiceFacadeImpl(this)
        this.screenFacade = new ScreenFacadeImpl(this)
    }

    public GPathResult getConfXmlRoot() {
        return this.confXmlRoot
    }

    public GPathResult getDefaultConfXmlRoot() {
        return this.defaultConfXmlRoot
    }

    public synchronized void destroy() {
        if (!this.destroyed) {
            // this destroy order is important as some use others so must be destroyed first
            this.serviceFacade.destroy()
            this.entityFacade.destroy()
            this.transactionFacade.destroy()
            this.cacheFacade.destroy()

            this.destroyed = true
        }
    }

    public CacheFacadeImpl getCacheFacade() {
        return this.cacheFacade;
    }

    public EntityFacadeImpl getEntityFacade() {
        return this.entityFacade;
    }

    public LoggerFacadeImpl getLoggerFacade() {
        return this.loggerFacade;
    }

    public ResourceFacadeImpl getResourceFacade() {
        return this.resourceFacade;
    }

    public ScreenFacadeImpl getScreenFacade() {
        return this.screenFacade;
    }

    public ServiceFacadeImpl getServiceFacade() {
        return this.serviceFacade;
    }

    public TransactionFacadeImpl getTransactionFacade() {
        return this.transactionFacade;
    }

    // ========== Interface Implementations ==========

    /** @see org.moqui.context.ExecutionContextFactory#getExecutionContext() */
    public ExecutionContext getExecutionContext() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#getWebExecutionContext(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) */
    public WebExecutionContext getWebExecutionContext(HttpServletRequest request, HttpServletResponse response) {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContextFactory#initComponent(String) */
    public void initComponent(String baseLocation) throws BaseException {
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
    public void destroyComponent(String componentName) throws BaseException {
        this.componentLocationMap.remove(componentName);

        // TODO: implement rest of this as needed
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    public Map<String, String> getComponentBaseLocations() {
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
}
