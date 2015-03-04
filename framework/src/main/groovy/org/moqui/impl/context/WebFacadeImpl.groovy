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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.moqui.context.ContextStack
import org.moqui.context.ResourceReference
import org.moqui.context.ValidationError
import org.moqui.impl.StupidUtilities

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import javax.servlet.ServletContext

import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.FileItemFactory
import org.apache.commons.fileupload.FileItem
import org.moqui.context.WebFacade
import org.moqui.impl.context.ExecutionContextFactoryImpl.WebappInfo
import org.moqui.impl.service.ServiceJsonRpcDispatcher
import org.moqui.impl.service.ServiceXmlRpcDispatcher
import org.moqui.impl.StupidWebUtilities

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** This class is a facade to easily get information from and about the web context. */
class WebFacadeImpl implements WebFacade {
    protected final static Logger logger = LoggerFactory.getLogger(WebFacadeImpl.class)

    protected static final Map<String, String> webappRootUrlByParms = new HashMap()

    protected ExecutionContextImpl eci
    protected String webappMoquiName
    protected HttpServletRequest request
    protected HttpServletResponse response

    protected Map<String, Object> savedParameters = null
    protected Map<String, Object> multiPartParameters = null
    protected Map<String, Object> jsonParameters = null
    protected Map<String, Object> declaredPathParameters = null

    protected ContextStack parameters = null
    protected Map<String, Object> requestAttributes = null
    protected Map<String, Object> requestParameters = null
    protected Map<String, Object> sessionAttributes = null
    protected Map<String, Object> applicationAttributes = null

    protected Map<String, Object> errorParameters = null

    protected List<String> savedMessages = null
    protected List<String> savedErrors = null
    protected List<ValidationError> savedValidationErrors = null

    WebFacadeImpl(String webappMoquiName, HttpServletRequest request, HttpServletResponse response,
                  ExecutionContextImpl eci) {
        this.eci = eci
        this.webappMoquiName = webappMoquiName
        this.request = request
        this.response = response

        // NOTE: the Visit is not setup here but rather in the MoquiEventListener (for init and destroy)
        request.setAttribute("ec", eci)

        // get any parameters saved to the session from the last request, and clear that session attribute if there
        savedParameters = (Map) request.session.getAttribute("moqui.saved.parameters")
        if (savedParameters != null) request.session.removeAttribute("moqui.saved.parameters")

        errorParameters = (Map) request.session.getAttribute("moqui.error.parameters")
        if (errorParameters != null) request.session.removeAttribute("moqui.error.parameters")

        // get any messages saved to the session, and clear them from the session
        if (session.getAttribute("moqui.message.messages")) {
            savedMessages = (List<String>) session.getAttribute("moqui.message.messages")
            session.removeAttribute("moqui.message.messages")
        }
        if (session.getAttribute("moqui.message.errors")) {
            savedErrors = (List<String>) session.getAttribute("moqui.message.errors")
            session.removeAttribute("moqui.message.errors")
        }
        if (session.getAttribute("moqui.message.validationErrors")) {
            savedValidationErrors = (List<ValidationError>) session.getAttribute("moqui.message.validationErrors")
            session.removeAttribute("moqui.message.validationErrors")
        }

        // if there is a JSON document submitted consider those as parameters too
        String contentType = request.getHeader("Content-Type")
        if (contentType && (contentType.contains("application/json") || contentType.contains("text/json"))) {
            JsonSlurper slurper = new JsonSlurper()
            Object jsonObj = null
            try {
                jsonObj = slurper.parse(new BufferedReader(new InputStreamReader(request.getInputStream(),
                        request.getCharacterEncoding() ?: "UTF-8")))
            } catch (Throwable t) {
                logger.error("Error parsing HTTP request body JSON: ${t.toString()}", t)
                jsonParameters = [_requestBodyJsonParseError:t.getMessage()]
            }
            if (jsonObj instanceof Map) {
                jsonParameters = (Map<String, Object>) jsonObj
            } else if (jsonObj instanceof List) {
                jsonParameters = [_requestBodyJsonList:jsonObj]
            }
            // logger.warn("=========== Got JSON HTTP request body: ${jsonParameters}")
        }

        // if this is a multi-part request, get the data for it
        if (ServletFileUpload.isMultipartContent(request)) {
            multiPartParameters = new HashMap()
            FileItemFactory factory = makeDiskFileItemFactory(request.session.getServletContext())
            ServletFileUpload upload = new ServletFileUpload(factory)

            List<FileItem> items = (List<FileItem>) upload.parseRequest(request)
            List<FileItem> fileUploadList = []
            multiPartParameters.put("_fileUploadList", fileUploadList)

            for (FileItem item in items) {
                if (item.isFormField()) {
                    multiPartParameters.put(item.getFieldName(), item.getString())
                } else {
                    // put the FileItem itself in the Map to be used by the application code
                    multiPartParameters.put(item.getFieldName(), item)
                    fileUploadList.add(item)

                    /* Stuff to do with the FileItem:
                      - get info about the uploaded file
                        String fieldName = item.getFieldName()
                        String fileName = item.getName()
                        String contentType = item.getContentType()
                        boolean isInMemory = item.isInMemory()
                        long sizeInBytes = item.getSize()

                      - get the bytes in memory
                        byte[] data = item.get()

                      - write the data to a File
                        File uploadedFile = new File(...)
                        item.write(uploadedFile)

                      - get the bytes in a stream
                        InputStream uploadedStream = item.getInputStream()
                        ...
                        uploadedStream.close()
                     */
                }
            }
        }
    }

    // ExecutionContextImpl getEci() { eci }
    void runFirstHitInVisitActions() {
        WebappInfo wi = eci.ecfi.getWebappInfo(webappMoquiName)
        if (wi.firstHitInVisitActions) wi.firstHitInVisitActions.run(eci)
    }
    void runBeforeRequestActions() {
        WebappInfo wi = eci.ecfi.getWebappInfo(webappMoquiName)
        if (wi.beforeRequestActions) wi.beforeRequestActions.run(eci)
    }
    void runAfterRequestActions() {
        WebappInfo wi = eci.ecfi.getWebappInfo(webappMoquiName)
        if (wi.afterRequestActions) wi.afterRequestActions.run(eci)
    }
    void runAfterLoginActions() {
        WebappInfo wi = eci.ecfi.getWebappInfo(webappMoquiName)
        if (wi.afterLoginActions) wi.afterLoginActions.run(eci)
    }
    void runBeforeLogoutActions() {
        WebappInfo wi = eci.ecfi.getWebappInfo(webappMoquiName)
        if (wi.beforeLogoutActions) wi.beforeLogoutActions.run(eci)
    }

    String getRequestUrl() {
        StringBuilder requestUrl = new StringBuilder()
        requestUrl.append(request.getScheme())
        requestUrl.append("://" + request.getServerName())
        if (request.getServerPort() != 80 && request.getServerPort() != 443) requestUrl.append(":" + request.getServerPort())
        requestUrl.append(request.getRequestURI())
        if (request.getQueryString()) requestUrl.append("?" + request.getQueryString())
        return requestUrl.toString()
    }

    void addDeclaredPathParameter(String name, String value) {
        if (declaredPathParameters == null) declaredPathParameters = new HashMap()
        declaredPathParameters.put(name, value)
    }

    List<String> getSavedMessages() { return savedMessages }
    List<String> getSavedErrors() { return savedErrors }
    List<ValidationError> getSavedValidationErrors() { return savedValidationErrors }

    @Override
    Map<String, Object> getParameters() {
        // NOTE: no blocking in these methods because the WebFacadeImpl is created for each thread

        // only create when requested, then keep for additional requests
        if (parameters != null) return parameters

        // Uses the approach of creating a series of this objects wrapping the other non-Map attributes/etc instead of
        // copying everything from the various places into a single combined Map; this should be much faster to create
        // and only slightly slower when running.
        ContextStack cs = new ContextStack()
        cs.push(getRequestParameters())
        cs.push(getApplicationAttributes())
        cs.push(getSessionAttributes())
        cs.push(getRequestAttributes())
        parameters = cs
        return parameters
    }

    @Override
    HttpServletRequest getRequest() { return request }
    @Override
    Map<String, Object> getRequestAttributes() {
        if (requestAttributes != null) return requestAttributes
        requestAttributes = new StupidWebUtilities.RequestAttributeMap(request)
        return requestAttributes
    }
    @Override
    Map<String, Object> getRequestParameters() {
        if (requestParameters != null) return requestParameters

        ContextStack cs = new ContextStack()
        if (savedParameters) cs.push(savedParameters)
        if (multiPartParameters) cs.push(multiPartParameters)
        if (jsonParameters) cs.push(jsonParameters)
        if (declaredPathParameters) cs.push(declaredPathParameters)
        cs.push((Map<String, Object>) request.getParameterMap())
        cs.push(StupidWebUtilities.getPathInfoParameterMap(request.getPathInfo()))

        // NOTE: the CanonicalizeMap cleans up character encodings, and unwraps lists of values with a single entry
        requestParameters = new StupidWebUtilities.CanonicalizeMap(cs)
        return requestParameters
    }

    @Override
    HttpServletResponse getResponse() { return response }

    @Override
    HttpSession getSession() { return request.getSession(true) }
    @Override
    Map<String, Object> getSessionAttributes() {
        if (sessionAttributes) return sessionAttributes
        sessionAttributes = new StupidWebUtilities.SessionAttributeMap(getSession())
        return sessionAttributes
    }

    @Override
    ServletContext getServletContext() { return getSession().getServletContext() }
    @Override
    Map<String, Object> getApplicationAttributes() {
        if (applicationAttributes) return applicationAttributes
        applicationAttributes = new StupidWebUtilities.ServletContextAttributeMap(getSession().getServletContext())
        return applicationAttributes
    }
    @Override
    String getWebappRootUrl(boolean requireFullUrl, Boolean useEncryption) {
        return getWebappRootUrl(this.webappMoquiName, null, requireFullUrl, useEncryption, eci)
    }

    static String getWebappRootUrl(String webappName, String servletContextPath, boolean requireFullUrl, Boolean useEncryption, ExecutionContextImpl eci) {
        WebFacade webFacade = eci.getWeb()
        boolean requireEncryption = useEncryption == null && webFacade != null ? webFacade.getRequest().isSecure() : useEncryption
        boolean needFullUrl = requireFullUrl ||
                (requireEncryption && webFacade != null && !webFacade.getRequest().isSecure()) ||
                (!requireEncryption && webFacade != null && webFacade.getRequest().isSecure())

        String cacheKey = webappName + servletContextPath + needFullUrl + requireEncryption
        String cachedRootUrl = webappRootUrlByParms.get(cacheKey)
        if (cachedRootUrl != null) return cachedRootUrl

        Node webappNode = (Node) eci.ecfi.confXmlRoot."webapp-list"[0]."webapp".find({ it.@name == webappName })
        StringBuilder urlBuilder = new StringBuilder()
        // build base from conf
        if (needFullUrl && webappNode) {
            if (requireEncryption && webappNode."@https-enabled" != "false") {
                urlBuilder.append("https://")
                if (webappNode."@https-host") {
                    urlBuilder.append(webappNode."@https-host")
                } else {
                    if (webFacade != null) {
                        String hostName = null
                        try { hostName = new URL(webFacade.getRequest().getRequestURL().toString()).getHost() }
                        catch (Exception e) { /* ignore it, default to getServerName() result */ }
                        if (!hostName) hostName = webFacade.getRequest().getServerName()
                        urlBuilder.append(hostName)
                    } else {
                        // uh-oh, no web context, default to localhost
                        urlBuilder.append("localhost")
                    }
                }
                String httpsPort = webappNode."@https-port"
                // try the local port; this won't work when switching from http to https, conf required for that
                if (!httpsPort && webFacade && webFacade.request.isSecure()) httpsPort = webFacade.request.getLocalPort() as String
                if (httpsPort && httpsPort != "443") urlBuilder.append(":").append(httpsPort)
            } else {
                urlBuilder.append("http://")
                if (webappNode."@http-host") {
                    urlBuilder.append(webappNode."@http-host")
                } else {
                    if (webFacade) {
                        String hostName = null
                        try { hostName = new URL(webFacade.getRequest().getRequestURL().toString()).getHost() }
                        catch (Exception e) { /* ignore it, default to getServerName() result */ }
                        if (!hostName) hostName = webFacade.getRequest().getServerName()
                        urlBuilder.append(hostName)
                    } else {
                        // uh-oh, no web context, default to localhost
                        urlBuilder.append("localhost")
                    }
                }
                String httpPort = webappNode."@http-port"
                // try the local port; this won't work when switching from https to http, conf required for that
                if (!httpPort && webFacade && !webFacade.getRequest().isSecure()) httpPort = webFacade.getRequest().getLocalPort() as String
                if (httpPort && httpPort != "80") urlBuilder.append(":").append(httpPort)
            }
            urlBuilder.append("/")
        } else {
            // can't get these settings, hopefully a URL from the root will do
            urlBuilder.append("/")
        }

        // add servletContext.contextPath
        if (!servletContextPath && webFacade)
            servletContextPath = webFacade.getServletContext().getContextPath()
        if (servletContextPath) {
            if (servletContextPath.startsWith("/")) servletContextPath = servletContextPath.substring(1)
            urlBuilder.append(servletContextPath)
        }

        // make sure we don't have a trailing slash
        if (urlBuilder.charAt(urlBuilder.length()-1) == '/') urlBuilder.deleteCharAt(urlBuilder.length()-1)

        String urlValue = urlBuilder.toString()
        webappRootUrlByParms.put(cacheKey, urlValue)
        return urlValue
    }


    @Override
    Map<String, Object> getErrorParameters() { return errorParameters }

    @Override
    void sendJsonResponse(Object responseObj) {
        String jsonStr
        if (responseObj instanceof String) {
            jsonStr = (String) responseObj
        } else {
            if (eci.message.messages) {
                if (responseObj == null) {
                    responseObj = [messages:eci.message.getMessagesString()]
                } else if (responseObj instanceof Map && !responseObj.containsKey("messages")) {
                    Map responseMap = new HashMap()
                    responseMap.putAll((Map) responseObj)
                    responseMap.put("messages", eci.message.getMessagesString())
                    responseObj = responseMap
                }
            }

            if (eci.getMessage().hasError()) {
                JsonBuilder jb = new JsonBuilder()
                // if the responseObj is a Map add all of it's data
                if (responseObj instanceof Map) {
                    // only add an errors if it is not a jsonrpc response (JSON RPC has it's own error handling)
                    if (!responseObj.containsKey("jsonrpc")) {
                        Map responseMap = new HashMap()
                        responseMap.putAll(responseObj)
                        responseMap.put("errors", eci.message.errorsString)
                        responseObj = responseMap
                    }
                    jb.call(responseObj)
                } else if (responseObj != null) {
                    jb.call(responseObj)
                }

                jsonStr = jb.toString()
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            } else if (responseObj != null) {
                JsonBuilder jb = new JsonBuilder()
                jb.call(responseObj)
                jsonStr = jb.toString()
                response.setStatus(HttpServletResponse.SC_OK)
            } else {
                jsonStr = ""
                response.setStatus(HttpServletResponse.SC_OK)
            }
        }

        if (!jsonStr) return

        response.setContentType("application/json")
        // NOTE: String.length not correct for byte length
        String charset = response.getCharacterEncoding() ?: "UTF-8"
        int length = jsonStr.getBytes(charset).length
        response.setContentLength(length)

        if (logger.infoEnabled) logger.info("Sending JSON response of length [${length}] with [${charset}] encoding")

        try {
            response.writer.write(jsonStr)
            response.writer.flush()
        } catch (IOException e) {
            logger.error("Error sending JSON string response", e)
        }
    }

    @Override
    void sendTextResponse(String text) {
        String responseText
        if (eci.getMessage().hasError()) {
            responseText = eci.message.errorsString
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        } else {
            responseText = text
            response.setStatus(HttpServletResponse.SC_OK)
        }

        response.setContentType("text/plain")
        // NOTE: String.length not correct for byte length
        String charset = response.getCharacterEncoding() ?: "UTF-8"
        int length = responseText ? responseText.getBytes(charset).length : 0
        response.setContentLength(length)

        try {
            if (responseText) response.writer.write(responseText)
            response.writer.flush()
        } catch (IOException e) {
            logger.error("Error sending text response", e)
        }
    }

    @Override
    void sendResourceResponse(String location) { sendResourceResponse(location, false) }
    void sendResourceResponse(String location, boolean inline) {
        ResourceReference rr = eci.resource.getLocationReference(location)
        if (rr == null) throw new IllegalArgumentException("Resource not found at: ${location}")
        response.setContentType(rr.contentType)
        if (inline) response.addHeader("Content-Disposition", "inline")
        else response.addHeader("Content-Disposition", "attachment; filename=\"${rr.getFileName()}\"")
        InputStream is = rr.openStream()
        try {
            OutputStream os = response.outputStream
            try {
                int totalLen = StupidUtilities.copyStream(is, os)
                logger.info("Streamed ${totalLen} bytes from contentLocation ${location}")
            } finally {
                os.close()
            }
        } finally {
            is.close()
        }
    }

    void handleXmlRpcServiceCall() { new ServiceXmlRpcDispatcher(eci).dispatch(request, response) }

    void handleJsonRpcServiceCall() { new ServiceJsonRpcDispatcher(eci).dispatch(request, response) }

    void saveScreenLastInfo(String screenPath, Map parameters) {
        session.setAttribute("moqui.screen.last.path", screenPath ?: request.getPathInfo())
        session.setAttribute("moqui.screen.last.parameters", parameters ?: new HashMap(getRequestParameters()))
    }

    String getRemoveScreenLastPath() {
        String path = session.getAttribute("moqui.screen.last.path")
        session.removeAttribute("moqui.screen.last.path")
        return path
    }
    Map getSavedParameters() { return (Map) session.getAttribute("moqui.saved.parameters") }
    void removeScreenLastParameters(boolean moveToSaved) {
        if (moveToSaved) session.setAttribute("moqui.saved.parameters", session.getAttribute("moqui.screen.last.parameters"))
        session.removeAttribute("moqui.screen.last.parameters")
    }

    void saveMessagesToSession() {
        if (eci.message.messages) session.setAttribute("moqui.message.messages", eci.message.messages)
        if (eci.message.errors) session.setAttribute("moqui.message.errors", eci.message.errors)
        if (eci.message.validationErrors) session.setAttribute("moqui.message.validationErrors", eci.message.validationErrors)
    }

    /** Save passed parameters Map to a Map in the moqui.saved.parameters session attribute */
    void saveParametersToSession(Map parameters) {
        Map parms = new HashMap()
        Map currentSavedParameters = (Map) request.session.getAttribute("moqui.saved.parameters")
        if (currentSavedParameters) parms.putAll(currentSavedParameters)
        if (parameters) parms.putAll(parameters)
        session.setAttribute("moqui.saved.parameters", parms)
    }
    /** Save request parameters and attributes to a Map in the moqui.saved.parameters session attribute */
    void saveRequestParametersToSession() {
        Map parms = new HashMap()
        Map currentSavedParameters = (Map) request.session.getAttribute("moqui.saved.parameters")
        if (currentSavedParameters) parms.putAll(currentSavedParameters)
        if (requestParameters) parms.putAll(requestParameters)
        if (requestAttributes) parms.putAll(requestAttributes)
        session.setAttribute("moqui.saved.parameters", parms)
    }

    /** Save request parameters and attributes to a Map in the moqui.error.parameters session attribute */
    void saveErrorParametersToSession() {
        Map parms = new HashMap()
        if (requestParameters) parms.putAll(requestParameters)
        if (requestAttributes) parms.putAll(requestAttributes)
        session.setAttribute("moqui.error.parameters", parms)
    }

    static DiskFileItemFactory makeDiskFileItemFactory(ServletContext context) {
        // NOTE: consider keeping this factory somewhere to be more efficient, if it even makes a difference...
        File repository = new File(System.getProperty("moqui.runtime") + "/tmp")
        if (!repository.exists()) repository.mkdir()

        DiskFileItemFactory factory = new DiskFileItemFactory(DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD, repository)

        // TODO: this was causing files to get deleted before the upload was streamed... need to figure out something else
        //FileCleaningTracker fileCleaningTracker = FileCleanerCleanup.getFileCleaningTracker(context)
        //factory.setFileCleaningTracker(fileCleaningTracker)
        return factory
    }
}
