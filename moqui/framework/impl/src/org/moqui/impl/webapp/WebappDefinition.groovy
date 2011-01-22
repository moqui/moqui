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

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class WebappDefinition {
    protected final static Logger logger = LoggerFactory.getLogger(WebappDefinition.class)

    protected final ExecutionContextFactoryImpl ecfi
    protected final String webappName
    protected final Node webappNode

    WebappDefinition(String webappName, ExecutionContextFactoryImpl ecfi) {
        this.webappName = webappName
        this.ecfi = ecfi

        webappNode = (Node) ecfi.confXmlRoot["webapp-list"][0]["webapp"].find({ it.@name == webappName })
    }

    String getWebappName() { return webappName }
    Node getWebappNode() { return webappNode }
}
