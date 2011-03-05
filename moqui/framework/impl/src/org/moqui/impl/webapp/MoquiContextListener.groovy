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
package org.moqui.impl.webapp

import javax.servlet.http.HttpSessionListener
import javax.servlet.http.HttpSession
import javax.servlet.http.HttpSessionEvent

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.sql.Timestamp
import javax.servlet.ServletContextListener
import javax.servlet.ServletContextEvent
import org.moqui.context.ExecutionContextFactory
import org.moqui.Moqui
import javax.servlet.ServletContext
import org.moqui.impl.StupidUtilities
import org.moqui.impl.StupidUtilities.CachedClassLoader

class MoquiContextListener implements ServletContextListener {
    protected final static Logger logger = LoggerFactory.getLogger(MoquiContextListener.class)

    protected CachedClassLoader ccl = null

    protected String getId(ServletContext sc) {
        String contextPath = sc.getContextPath()
        return contextPath.length() > 1 ? contextPath.substring(1) : "ROOT"
    }

    void contextInitialized(ServletContextEvent servletContextEvent) {
        // first setup the CachedClassLoader, this should init in the main thread so we can set it properly
        ccl = new StupidUtilities.CachedClassLoader(new URL[0], Thread.currentThread().getContextClassLoader())
        Thread.currentThread().setContextClassLoader(ccl)

        ServletContext sc = servletContextEvent.servletContext
        String webappId = getId(sc)
        String moquiWebappName = sc.getInitParameter("moqui-name")

        logger.info("Loading Moqui Webapp at [${webappId}], moqui webapp name [${moquiWebappName}], context name [${sc.getServletContextName()}], located at [${sc.getRealPath("/")}]")
        ExecutionContextFactory executionContextFactory = new ExecutionContextFactoryImpl()
        sc.setAttribute("executionContextFactory", executionContextFactory)

        // there should always be one ECF that is active for things like deserialize of EntityValue
        // for a servlet that has a factory separate from the rest of the system DON'T call this (ie to have multiple ECFs on a single system)
        Moqui.dynamicInit(executionContextFactory)
        logger.info("Loaded Moqui Execution Context Factory for webapp [${webappId}]")

        //webappDef = new WebappDefinition(moquiWebappName, executionContextFactory)
    }

    void contextDestroyed(ServletContextEvent servletContextEvent) {
        ServletContext sc = servletContextEvent.servletContext
        String webappId = getId(sc)

        logger.info("Destroying Moqui Execution Context Factory for webapp [${webappId}]")
        if (sc.getAttribute("executionContextFactory")) {
            ExecutionContextFactory executionContextFactory =
                    (ExecutionContextFactory) sc.getAttribute("executionContextFactory")
            sc.removeAttribute("executionContextFactory")
            executionContextFactory.destroy()
        }
        logger.info("Destroyed Moqui Execution Context Factory for webapp [${webappId}]")
        logger.info("CachedClassLoader on destroy: classCache size [${ccl.classCache.size()}] resourceCache size [${ccl.resourceCache.size()}]")
    }
}
