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


import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import javax.servlet.ServletContext

import org.moqui.context.WebFacade
import org.moqui.impl.StupidWebUtilities
import org.slf4j.LoggerFactory
import org.slf4j.Logger

/** This class is a delegator for the ExecutionContextImpl class so that it can easily be used to extend an existing
 * ExecutionContext.
 */
class WebFacadeImpl implements WebFacade {
    protected final static Logger logger = LoggerFactory.getLogger(WebFacadeImpl.class)

    protected ExecutionContextImpl eci
    protected String webappMoquiName
    protected HttpServletRequest request
    protected HttpServletResponse response

    protected Map<String, Object> savedParameters = null

    protected Map<String, Object> parameters = null
    protected Map<String, Object> requestAttributes = null
    protected Map<String, Object> requestParameters = null
    protected Map<String, Object> sessionAttributes = null
    protected Map<String, Object> applicationAttributes = null

    WebFacadeImpl(String webappMoquiName, HttpServletRequest request, HttpServletResponse response,
                  ExecutionContextImpl eci) {
        this.eci = eci
        this.webappMoquiName = webappMoquiName
        this.request = request
        this.response = response

        // NOTE: the Visit is not setup here but rather in the MoquiEventListener (for init and destroy)
        request.setAttribute("ec", eci)
        this.eci.userFacade.initFromHttpRequest(request)

        // get any parameters saved to the session from the last request, and clear that session attribute if there
        savedParameters = (Map) request.session.getAttribute("moqui.saved.parameters")
        if (savedParameters != null) request.session.removeAttribute("moqui.saved.parameters")

        // get any messages saved to the session, and clear them from the session
        if (session.getAttribute("moqui.message.messages")) {
            eci.message.messageList.addAll((Collection) session.getAttribute("moqui.message.messages"))
            session.removeAttribute("moqui.message.messages")
        }
        if (session.getAttribute("moqui.message.errors")) {
            eci.message.errorList.addAll((Collection) session.getAttribute("moqui.message.errors"))
            session.removeAttribute("moqui.message.errors")
        }
    }

    ExecutionContextImpl getEci() { eci }

    /** @see org.moqui.context.WebFacade#getParameters() */
    Map<String, Object> getParameters() {
        // NOTE: no blocking in these methods because the ExecutionContext is created for each thread

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

    void saveScreenLastInfo(String screenPath, Map parameters) {
        session.setAttribute("moqui.screen.last.path", screenPath ?: request.getPathInfo())
        session.setAttribute("moqui.screen.last.parameters", parameters ?: getRequestParameters())
    }

    String getRemoveScreenLastPath() {
        String path = session.getAttribute("moqui.screen.last.path")
        session.removeAttribute("moqui.screen.last.path")
        return path
    }
    void removeScreenLastParameters(boolean moveToSaved) {
        if (moveToSaved)
            session.setAttribute("moqui.saved.parameters", session.getAttribute("moqui.screen.last.parameters"))
        session.removeAttribute("moqui.screen.last.parameters")
    }

    void saveMessagesToSession() {
        if (eci.message.messages) session.setAttribute("moqui.message.messages", eci.message.messages)
        if (eci.message.errors) session.setAttribute("moqui.message.errors", eci.message.errors)
    }
}
