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

import org.moqui.BaseException;
import org.moqui.context.ExecutionContext;
import org.moqui.context.ExecutionContextFactory;
import org.moqui.context.WebExecutionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Properties;
import java.net.URL;

public class ExecutionContextFactoryImpl implements ExecutionContextFactory {

    protected final File runtimeFile;
    protected final File confFile;

    protected final Map componentLocationMap = new HashMap();
    // for future use if needed: protected final Map componentDetailMap;

    // ======== Permanent Delegated Facades ========
    protected final CacheFacadeImpl cacheFacade;
    //protected final EntityFacadeImpl entityFacade;
    protected final LoggerFacadeImpl loggerFacade;
    protected final ResourceFacadeImpl resourceFacade;
    protected final ScreenFacadeImpl screenFacade;
    //protected final ServiceFacadeImpl serviceFacade;
    protected final TransactionFacadeImpl transactionFacade;

    /**
     * This constructor gets runtime directory and conf file location from a properties file on the classpath so that
     * it can initialize on its own. This is the constructor to be used by the ServiceLoader in the Moqui.java file.
     */
    public ExecutionContextFactoryImpl() {
        // get the runtime directory path
        Properties moquiInitProperties = new Properties();
        moquiInitProperties.load(ClassLoader.getSystemResourceAsStream("MoquiInit.properties"));
        String runtimePath = moquiInitProperties.getProperty("moqui.runtime");
        if (!runtimePath) {
            runtimePath = System.getProperty("moqui.runtime");
        }
        if (!runtimePath) {
            throw new IllegalArgumentException("No moqui.runtime property found in MoquiInit.properties or in a system property (with: -Dmoqui.runtime=... on the command line).");
        }

        // setup the runtimeFile
        runtimeFile = new File(runtimePath);
        if (!runtimeFile.exists()) {
            throw new IllegalArgumentException("The moqui.runtime path [${runtimePath}] was not found.");
        }

        // get the moqui configuration file path
        String confPath = moquiInitProperties.getProperty("moqui.conf");
        if (!confPath) {
            confPath = System.getProperty("moqui.conf");
        }
        if (!confPath) {
            throw new IllegalArgumentException("No moqui.conf property found in MoquiInit.properties or in a system property (with: -Dmoqui.conf=... on the command line).");
        }

        // setup the confFile
        if (runtimePath.endsWith('/')) runtimePath = runtimePath.substring(0, runtimePath.length()-1);
        if (confPath.beginsWith('/')) confPath = confPath.substring(1); 
        String confFullPath = runtimePath + '/' + confPath;
        confFile = new File(confFullPath);
        if (!confFile.exists()) {
            throw new IllegalArgumentException("The moqui.conf path [${confFullPath}] was not found.");
        }

        this.init();
    }

    /** This constructor takes the runtime directory path and conf file path directly. */
    public ExecutionContextFactoryImpl(String runtimePath, String confPath) {
        // setup the runtimeFile
        runtimeFile = new File(runtimePath);
        if (!runtimeFile.exists()) {
            throw new IllegalArgumentException("The moqui.runtime path [${runtimePath}] was not found.");
        }

        // setup the confFile
        if (runtimePath.endsWith('/')) runtimePath = runtimePath.substring(0, runtimePath.length()-1);
        if (confPath.beginsWith('/')) confPath = confPath.substring(1);
        String confFullPath = runtimePath + '/' + confPath;
        confFile = new File(confFullPath);
        if (!confFile.exists()) {
            throw new IllegalArgumentException("The moqui.conf path [${confFullPath}] was not found.");
        }

        this.init();
    }

    /** Initialize all permanent framework objects, ie those not sensitive to webapp or user context. */
    protected void init() {
        this.cacheFacade = new CacheFacadeImpl(this);
        this.loggerFacade = new LoggerFacadeImpl(this);
        this.resourceFacade = new ResourceFacadeImpl(this);
        this.screenFacade = new ScreenFacadeImpl(this);
        this.transactionFacade = new TransactionFacadeImpl(this);
    }

    public CacheFacadeImpl getCacheFacade() {
        return this.cacheFacade;
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

        // TODO: implement rest of this
    }

    /** @see org.moqui.context.ExecutionContextFactory#destroyComponent(String) */
    public void destroyComponent(String componentName) throws BaseException {
        this.componentLocationMap.remove(componentName);
        // TODO: implement rest of this
    }

    /** @see org.moqui.context.ExecutionContextFactory#getComponentBaseLocations() */
    public Map<String, String> getComponentBaseLocations() {
        return Collections.unmodifiableMap(this.componentLocationMap);
    }
}
