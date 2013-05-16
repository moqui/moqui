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

import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextFactoryImpl.WebappInfo
import org.moqui.Moqui

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MoquiContextListener implements ServletContextListener {
    protected final static Logger logger = LoggerFactory.getLogger(MoquiContextListener.class)

    protected static String getId(ServletContext sc) {
        String contextPath = sc.getContextPath()
        return contextPath.length() > 1 ? contextPath.substring(1) : "ROOT"
    }

    void contextInitialized(ServletContextEvent servletContextEvent) {
        ServletContext sc = servletContextEvent.servletContext
        String webappId = getId(sc)
        String moquiWebappName = sc.getInitParameter("moqui-name")
        String webappRealPath = sc.getRealPath("/")

        logger.info("Loading Moqui Webapp at [${webappId}], moqui webapp name [${moquiWebappName}], context name [${sc.getServletContextName()}], located at [${webappRealPath}]")

        // before we init the ECF, see if there is a runtime directory in the webappRealPath, and if so set that as the moqui.runtime System property
        if (new File(webappRealPath + "/runtime").exists()) System.setProperty("moqui.runtime", webappRealPath + "/runtime")

        ExecutionContextFactory ecfi = new ExecutionContextFactoryImpl()
        sc.setAttribute("executionContextFactory", ecfi)
        // there should always be one ECF that is active for things like deserialize of EntityValue
        // for a servlet that has a factory separate from the rest of the system DON'T call this (ie to have multiple ECFs on a single system)
        Moqui.dynamicInit(ecfi)

        logger.info("Loaded Moqui Execution Context Factory for webapp [${webappId}]")

        // run after-startup actions
        WebappInfo wi = ecfi.getWebappInfo(moquiWebappName)
        if (wi.afterStartupActions) {
            ExecutionContext ec = ecfi.getExecutionContext()
            wi.afterStartupActions.run(ec)
            ec.destroy()
        }
    }

    void contextDestroyed(ServletContextEvent servletContextEvent) {
        ServletContext sc = servletContextEvent.servletContext
        String webappId = getId(sc)
        String moquiWebappName = sc.getInitParameter("moqui-name")

        logger.info("Destroying Moqui Execution Context Factory for webapp [${webappId}]")
        if (sc.getAttribute("executionContextFactory")) {
            ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) sc.getAttribute("executionContextFactory")

            // run before-shutdown actions
            WebappInfo wi = ecfi.getWebappInfo(moquiWebappName)
            if (wi.beforeShutdownActions) {
                ExecutionContext ec = ecfi.getExecutionContext()
                wi.beforeShutdownActions.run(ec)
                ec.destroy()
            }

            sc.removeAttribute("executionContextFactory")
            ecfi.destroy()
        }
        logger.info("Destroyed Moqui Execution Context Factory for webapp [${webappId}]")
    }
}
