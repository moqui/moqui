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

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import javax.servlet.ServletContext

import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.FileCleanerCleanup
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.FileItemFactory
import org.apache.commons.fileupload.FileItem
import org.apache.commons.io.FileCleaningTracker

import org.moqui.context.WebFacade
import org.moqui.impl.context.ExecutionContextFactoryImpl.WebappInfo
import org.moqui.impl.service.ServiceXmlRpcDispatcher
import org.moqui.impl.StupidWebUtilities
import org.moqui.impl.service.ServiceJsonRpcDispatcher

/** This class is a facade to easily get information from and about the web context. */
class WebFacadeImpl implements WebFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebFacadeImpl.class)

    protected ExecutionContextImpl eci
    protected String webappMoquiName
    protected HttpServletRequest request
    protected HttpServletResponse response

    protected Map<String, Object> savedParameters = null
    protected Map<String, Object> multiPartParameters = null

    protected ContextStack parameters = null
    protected Map<String, Object> requestAttributes = null
    protected Map<String, Object> requestParameters = null
    protected Map<String, Object> sessionAttributes = null
    protected Map<String, Object> applicationAttributes = null

    protected Map<String, Object> errorParameters = null

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
            eci.message.messageList.addAll((Collection) session.getAttribute("moqui.message.messages"))
            session.removeAttribute("moqui.message.messages")
        }
        if (session.getAttribute("moqui.message.errors")) {
            eci.message.errorList.addAll((Collection) session.getAttribute("moqui.message.errors"))
            session.removeAttribute("moqui.message.errors")
        }

        // if this is a multi-part request, get the data for it
        if (ServletFileUpload.isMultipartContent(request)) {
            multiPartParameters = new HashMap()
            FileItemFactory factory = makeDiskFileItemFactory(request.session.getServletContext())
            ServletFileUpload upload = new ServletFileUpload(factory)

            List<FileItem> items = upload.parseRequest(request)
            for (FileItem item in items) {
                if (item.isFormField()) {
                    multiPartParameters.put(item.getFieldName(), item.getString())
                } else {
                    // put the FileItem itself in the Map to be used by the application code
                    multiPartParameters.put(item.getFieldName(), item)

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

    ExecutionContextImpl getEci() { eci }
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

    /** @see org.moqui.context.WebFacade#getParameters() */
    Map<String, Object> getParameters() {
        // NOTE: no blocking in these methods because the WebFacadeImpl is created for each thread

        // only create when requested, then keep for additional requests
        if (parameters) return parameters

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

    /** @see org.moqui.context.WebFacade#getRequest() */
    HttpServletRequest getRequest() { return request }

    /** @see org.moqui.context.WebFacade#getRequestAttributes() */
    Map<String, Object> getRequestAttributes() {
        if (requestAttributes) return requestAttributes
        requestAttributes = new StupidWebUtilities.RequestAttributeMap(request)
        return requestAttributes
    }

    /** @see org.moqui.context.WebFacade#getRequestParameters() */
    Map<String, Object> getRequestParameters() {
        if (requestParameters) return requestParameters
        ContextStack cs = new ContextStack()
        if (savedParameters) cs.push(savedParameters)
        if (multiPartParameters) cs.push(multiPartParameters)
        cs.push((Map<String, Object>) request.getParameterMap())
        cs.push(StupidWebUtilities.getPathInfoParameterMap(request.getPathInfo()))
        // NOTE: the CanonicalizeMap cleans up character encodings, and unwraps lists of values with a single entry
        requestParameters = new StupidWebUtilities.CanonicalizeMap(cs)
        return requestParameters
    }

    /** @see org.moqui.context.WebFacade#getResponse() */
    HttpServletResponse getResponse() { return response }

    /** @see org.moqui.context.WebFacade#getSession() */
    HttpSession getSession() { return request.getSession() }

    /** @see org.moqui.context.WebFacade#getSessionAttributes() */
    Map<String, Object> getSessionAttributes() {
        if (sessionAttributes) return sessionAttributes
        sessionAttributes = new StupidWebUtilities.SessionAttributeMap(request.getSession())
        return sessionAttributes
    }

    /** @see org.moqui.context.WebFacade#getServletContext() */
    ServletContext getServletContext() { return request.session.getServletContext() }

    /** @see org.moqui.context.WebFacade#getApplicationAttributes() */
    Map<String, Object> getApplicationAttributes() {
        if (applicationAttributes) return applicationAttributes
        applicationAttributes = new StupidWebUtilities.ServletContextAttributeMap(request.session.getServletContext())
        return applicationAttributes
    }

    /** @see org.moqui.context.WebFacade#getErrorParameters() */
    Map<String, Object> getErrorParameters() { return errorParameters }

    /** @see org.moqui.context.WebFacade#sendJsonResponse(Object) */
    void sendJsonResponse(Object responseObj) {
        JsonBuilder jb = new JsonBuilder()
        jb.call(responseObj)
        String jsonStr = jb.toString()

        if (!jsonStr) return

        response.setContentType("application/x-json")
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

    void handleXmlRpcServiceCall() {
        new ServiceXmlRpcDispatcher(eci).dispatch(request, response)
    }

    void handleJsonRpcServiceCall() {
        new ServiceJsonRpcDispatcher(eci).dispatch(request.inputStream, response)
    }

    void saveScreenLastInfo(String screenPath, Map parameters) {
        session.setAttribute("moqui.screen.last.path", screenPath ?: request.getPathInfo())
        session.setAttribute("moqui.screen.last.parameters", parameters ?: new HashMap(getRequestParameters()))
    }

    String getRemoveScreenLastPath() {
        String path = session.getAttribute("moqui.screen.last.path")
        session.removeAttribute("moqui.screen.last.path")
        return path
    }
    void removeScreenLastParameters(boolean moveToSaved) {
        if (moveToSaved) session.setAttribute("moqui.saved.parameters", session.getAttribute("moqui.screen.last.parameters"))
        session.removeAttribute("moqui.screen.last.parameters")
    }

    void saveMessagesToSession() {
        if (eci.message.messages) session.setAttribute("moqui.message.messages", eci.message.messages)
        if (eci.message.errors) session.setAttribute("moqui.message.errors", eci.message.errors)
    }
    /** Save request parameters and attributes to a Map in the moqui.saved.parameters session attribute */
    void saveRequestParametersToSession() {
        Map parms = new HashMap()
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

        FileCleaningTracker fileCleaningTracker = FileCleanerCleanup.getFileCleaningTracker(context)
        factory.setFileCleaningTracker(fileCleaningTracker)
        return factory
    }
}
