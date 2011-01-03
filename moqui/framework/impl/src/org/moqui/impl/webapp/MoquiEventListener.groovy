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

import javax.servlet.http.HttpSessionListener
import javax.servlet.http.HttpSession
import javax.servlet.http.HttpSessionEvent

class MoquiEventListener implements HttpSessionListener {
    void sessionCreated(HttpSessionEvent event) {
        HttpSession session = event.getSession()

        // TODO create and persist Visit
        // put visit in session as "moqui.visit"
    }

    void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession()

        // TODO set thruDate on Visit
    }
}
