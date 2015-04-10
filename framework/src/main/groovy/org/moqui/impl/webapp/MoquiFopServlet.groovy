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

import org.moqui.context.ArtifactTarpitException

import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.moqui.context.ExecutionContext
import org.moqui.context.ScreenRender
import org.moqui.context.ArtifactAuthorizationException
import org.moqui.impl.context.ExecutionContextFactoryImpl

import javax.xml.transform.stream.StreamSource

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MoquiFopServlet extends HttpServlet {
    protected final static Logger logger = LoggerFactory.getLogger(MoquiFopServlet.class)

    MoquiFopServlet() {
        super()
    }

    @Override
    void doPost(HttpServletRequest request, HttpServletResponse response) { doScreenRequest(request, response) }

    @Override
    void doGet(HttpServletRequest request, HttpServletResponse response) { doScreenRequest(request, response) }

    void doScreenRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ExecutionContextFactoryImpl ecfi =
                (ExecutionContextFactoryImpl) getServletContext().getAttribute("executionContextFactory")
        String moquiWebappName = getServletContext().getInitParameter("moqui-name")

        String pathInfo = request.getPathInfo()
        long startTime = System.currentTimeMillis()

        if (logger.traceEnabled) logger.trace("Start request to [${pathInfo}] at time [${startTime}] in session [${request.session.id}] thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")

        ExecutionContext ec = ecfi.getExecutionContext()
        ec.initWebFacade(moquiWebappName, request, response)

        String xslFoText = null
        try {
            ScreenRender sr = ec.screen.makeRender().webappName(moquiWebappName).renderMode("xsl-fo")
                    .rootScreenFromHost(request.getServerName()).screenPath(pathInfo.split("/") as List)
            xslFoText = sr.render()

            // logger.warn("======== XSL-FO content:\n${xslFoText}")
            if (logger.traceEnabled) logger.trace("FOP XSL-FO content:\n${xslFoText}")

            String contentType = ec.web.requestParameters."contentType" ?: "application/pdf"
            response.setContentType(contentType)

            ec.resource.xslFoTransform(new StreamSource(new StringReader(xslFoText)), null,
                    response.getOutputStream(), contentType)
        } catch (ArtifactAuthorizationException e) {
            // SC_UNAUTHORIZED 401 used when authc/login fails, use SC_FORBIDDEN 403 for authz failures
            // See ScreenRenderImpl.checkWebappSettings for authc and SC_UNAUTHORIZED handling
            logger.warn((String) "Web Access Forbidden (no authz): " + e.message)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.message)
        } catch (ArtifactTarpitException e) {
            logger.warn((String) "Web Too Many Requests (tarpit): " + e.message)
            if (e.getRetryAfterSeconds()) response.addIntHeader("Retry-After", e.getRetryAfterSeconds())
            // NOTE: there is no constant on HttpServletResponse for 429; see RFC 6585 for details
            response.sendError(429, e.message)
        } catch (ScreenResourceNotFoundException e) {
            logger.warn((String) "Web Resource Not Found: " + e.message)
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.message)
        } catch (Throwable t) {
            logger.error("Error transforming XSL-FO content:\n${xslFoText}", t)
            if (ec.message.hasError()) {
                String errorsString = ec.message.errorsString
                logger.error(errorsString, t)
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorsString)
            } else {
                throw t
            }
        } finally {
            // make sure everything is cleaned up
            ec.destroy()
        }

        if (logger.infoEnabled) logger.info("Finished FOP request to [${pathInfo}] of content type [${response.getContentType()}] in [${(System.currentTimeMillis()-startTime)/1000}] seconds in session [${request.session.id}] thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")
    }
}
