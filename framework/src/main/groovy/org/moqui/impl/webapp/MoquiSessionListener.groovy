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
        // NOTE: this method now does nothing because we only want to create the Visit on the first request, and in
        //     order to not create the Visit under certain conditions we need the HttpServletRequest object.
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


        try {
            ecfi.executionContext.artifactExecution.disableAuthz()

            // set thruDate on Visit
            ecfi.serviceFacade.sync().name("update", "moqui.server.Visit")
                    .parameters((Map<String, Object>) [visitId:visitId, thruDate:new Timestamp(System.currentTimeMillis())])
                    .call()
        } finally {
            ecfi.executionContext.artifactExecution.enableAuthz()
        }
    }
}
