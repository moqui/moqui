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

import java.sql.Timestamp
import javax.servlet.http.HttpSessionListener
import javax.servlet.http.HttpSession
import javax.servlet.http.HttpSessionEvent

import org.moqui.impl.context.ExecutionContextFactoryImpl

class MoquiSessionListener implements HttpSessionListener {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiSessionListener.class)

    void sessionCreated(HttpSessionEvent event) {
        HttpSession session = event.getSession()
        ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) session.getServletContext().getAttribute("executionContextFactory")
        if (!ecfi) {
            logger.warn("Not creating visit for session [${session.id}], no executionContextFactory in ServletContext")
            return
        }

        if (ecfi.confXmlRoot."server-stats"[0]."@visit-enabled" == "false") return

        // create and persist Visit
        String contextPath = session.getServletContext().getContextPath()
        String webappId = contextPath.length() > 1 ? contextPath.substring(1) : "ROOT"
        Map parameters = [sessionId:session.id, webappName:webappId, fromDate:new Timestamp(session.getCreationTime())]
        InetAddress address = InetAddress.getLocalHost();
        if (address) {
            parameters.serverIpAddress = address.getHostAddress()
            parameters.serverHostName = address.getHostName()
        }
        Map result = ecfi.serviceFacade.sync().name("create", "Visit").parameters((Map<String, Object>) parameters).call()

        // put visitId in session as "moqui.visitId"
        session.setAttribute("moqui.visitId", result.visitId)
    }

    void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession()
        ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) session.getServletContext().getAttribute("executionContextFactory")
        if (!ecfi) {
            logger.warn("Not updating (closing) visit for session [${session.id}], no executionContextFactory in ServletContext")
            return
        }

        if (ecfi.confXmlRoot."server-stats"[0]."@visit-enabled" == "false") return

        String visitId = session.getAttribute("moqui.visitId")
        if (!visitId) {
            logger.warn("Not updating (closing) visit for session [${session.id}], no moqui.visitId attribute found")
            return
        }
        // set thruDate on Visit
        ecfi.serviceFacade.sync().name("update", "Visit")
                .parameters((Map<String, Object>) [visitId:visitId, thruDate:new Timestamp(System.currentTimeMillis())])
                .call()
    }
}
