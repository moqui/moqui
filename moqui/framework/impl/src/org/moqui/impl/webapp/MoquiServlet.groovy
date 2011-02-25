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

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.ServletConfig
import javax.servlet.ServletException

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.impl.context.ExecutionContextFactoryImpl

class MoquiServlet extends HttpServlet {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiServlet.class)

    protected String webappId = null
    protected String webappMoquiName = null
    protected WebappDefinition webappDef = null

    public MoquiServlet() {
        super();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config)
        String contextPath = config.getServletContext().getContextPath()
        webappId = contextPath.length() > 1 ? contextPath.substring(1) : "ROOT"
        webappMoquiName = config.getServletContext().getInitParameter("moqui-name")

        logger.info("Loading Moqui Webapp at [${webappId}], moqui webapp name [${webappMoquiName}], context name [${config.getServletContext().getServletContextName()}], located at [${config.getServletContext().getRealPath("/")}]")
        ExecutionContextFactory executionContextFactory = new ExecutionContextFactoryImpl()
        config.getServletContext().setAttribute("executionContextFactory", executionContextFactory)

        // there should always be one ECF that is active for things like deserialize of EntityValue
        // for a servlet that has a factory separate from the rest of the system DON'T call this (ie to have multiple ECFs on a single system)
        Moqui.dynamicInit(executionContextFactory)
        logger.info("Loaded Moqui Execution Context Factory for webapp [${webappId}]")

        webappDef = new WebappDefinition(webappMoquiName, executionContextFactory)
    }

    @Override
    public void destroy() {
        logger.info("Destroying Moqui Execution Context Factory for webapp [${webappId}]")
        if (getServletContext().getAttribute("executionContextFactory")) {
            ExecutionContextFactory executionContextFactory =
                    (ExecutionContextFactory) getServletContext().getAttribute("executionContextFactory")
            executionContextFactory.destroy()
            getServletContext().removeAttribute("executionContextFactory")
        }
        super.destroy();
        logger.info("Destroyed Moqui Execution Context Factory for webapp [${webappId}]")
    }

    /** @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    /** @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ExecutionContextFactory executionContextFactory =
                (ExecutionContextFactory) getServletContext().getAttribute("executionContextFactory")

        String pathInfo = request.getPathInfo()
        long startTime = System.currentTimeMillis()

        if (logger.infoEnabled) logger.info("=-=-=-=-=-= Start request to [${pathInfo}] at time [${startTime}] in session [${request.session.id}] thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")

        ExecutionContext ec = executionContextFactory.getExecutionContext()
        ec.initWebFacade(webappMoquiName, request, response)

        /** NOTE to set render settings manually do something like this, but it is not necessary to set these things
         * for a web page render because if we call render(request, response) it can figure all of this out as defaults
         *
         * ScreenRender render = ec.screen.makeRender().webappName(webappMoquiName).renderMode("html")
         *         .rootScreen(webappDef.webappNode."@root-screen-location").screenPath(pathInfo.split("/") as List)
         */

        try {
            ec.screen.makeRender().render(request, response)
        } catch (ScreenResourceNotFoundException e) {
            logger.warn("Resource Not Found: " + e.message)
            response.sendError(404, e.message)
        }

        // make sure everything is cleaned up
        ec.destroy()

        double runningTime = (System.currentTimeMillis() - startTime) / 1000
        if (logger.infoEnabled) logger.info("=-=-=-=-=-= End request to [${pathInfo}] of type [${response.getContentType()}] in [${runningTime}] seconds in session [${request.session.id}] thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")
    }
}
