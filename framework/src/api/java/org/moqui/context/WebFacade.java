/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/** Web Facade for access to HTTP Servlet objects and information. */
public interface WebFacade {
    String getRequestUrl();
    Map<String, Object> getParameters();

    HttpServletRequest getRequest();
    Map<String, Object> getRequestAttributes();
    Map<String, Object> getRequestParameters();

    HttpServletResponse getResponse();

    HttpSession getSession();
    Map<String, Object> getSessionAttributes();

    ServletContext getServletContext();
    Map<String, Object> getApplicationAttributes();
    String getWebappRootUrl(boolean requireFullUrl, Boolean useEncryption);

    Map<String, Object> getErrorParameters();

    void sendJsonResponse(Object responseObj);
    void sendTextResponse(String text);
    void sendResourceResponse(String location);
    void handleXmlRpcServiceCall();
    void handleJsonRpcServiceCall();
    void handleEntityRestCall(List<String> extraPathNameList);
}
