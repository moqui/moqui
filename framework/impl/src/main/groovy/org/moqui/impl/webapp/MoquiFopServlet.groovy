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

import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.moqui.context.ExecutionContext
import org.moqui.context.ScreenRender
import org.moqui.impl.context.ExecutionContextFactoryImpl

import javax.xml.transform.stream.StreamSource
import javax.xml.transform.TransformerFactory
import javax.xml.transform.Transformer
import javax.xml.transform.URIResolver
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.Source

import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder

import org.apache.fop.apps.FOUserAgent
import org.apache.fop.apps.Fop
import org.apache.fop.apps.FopFactory

class MoquiFopServlet extends HttpServlet {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiFopServlet.class)

    protected FopFactory internalFopFactory = null

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
        } catch (ScreenResourceNotFoundException e) {
            logger.warn("Resource Not Found: ${e.message}")
            response.sendError(404, e.message)
        }

        // logger.info("XSL-FO content:\n${xslFoText}")

        String contentType = ec.web.requestParameters."contentType" ?: "application/pdf"
        response.setContentType(contentType)

        try {
            xslFoTransform(new StreamSource(new StringReader(xslFoText)), null,
                    response.getOutputStream(), contentType)
        } catch (Exception e) {
            logger.error("Error transforming XSL-FO content:\n${xslFoText}", e)
        }

        // make sure everything is cleaned up
        ec.destroy()

        if (logger.infoEnabled) logger.info("Finished FOP request to [${pathInfo}] of content type [${response.getContentType()}] in [${(System.currentTimeMillis()-startTime)/1000}] seconds in session [${request.session.id}] thread [${Thread.currentThread().id}:${Thread.currentThread().name}]")
    }

    FopFactory getFopFactory() {
        if (internalFopFactory != null) return internalFopFactory

        ExecutionContextFactoryImpl ecfi =
                (ExecutionContextFactoryImpl) getServletContext().getAttribute("executionContextFactory")
        // setup FopFactory
        internalFopFactory = FopFactory.newInstance()
        // Limit the validation for backwards compatibility
        internalFopFactory.setStrictValidation(false)
        DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder()
        internalFopFactory.setUserConfig(cfgBuilder.build(ecfi.resourceFacade.getLocationStream("classpath://fop.xconf")))
        internalFopFactory.getFontManager().setFontBaseURL(ecfi.runtimePath + "/conf")

        return internalFopFactory
    }

    void xslFoTransform(StreamSource xslFoSrc, StreamSource xsltSrc, OutputStream out, String contentType) {
        ExecutionContextFactoryImpl ecfi =
                (ExecutionContextFactoryImpl) getServletContext().getAttribute("executionContextFactory")

        FopFactory ff = getFopFactory()
        FOUserAgent foUserAgent = ff.newFOUserAgent()
        Fop fop = ff.newFop(contentType, foUserAgent, out)

        TransformerFactory factory = TransformerFactory.newInstance()
        Transformer transformer = xsltSrc == null ? factory.newTransformer() : factory.newTransformer(xsltSrc)
        transformer.setURIResolver(new LocalResolver(ecfi, transformer.getURIResolver()))
        transformer.transform(xslFoSrc, new SAXResult(fop.getDefaultHandler()))
    }

    static class LocalResolver implements URIResolver {
        protected ExecutionContextFactoryImpl ecfi
        protected URIResolver defaultResolver

        protected LocalResolver() {}

        public LocalResolver(ExecutionContextFactoryImpl ecfi, URIResolver defaultResolver) {
            this.ecfi = ecfi
            this.defaultResolver = defaultResolver
        }

        public Source resolve(String href, String base) {
            InputStream is = ecfi.resourceFacade.getLocationStream(href)
            if (is != null) return new StreamSource(is)
            return defaultResolver.resolve(href, base)
        }
    }
}
