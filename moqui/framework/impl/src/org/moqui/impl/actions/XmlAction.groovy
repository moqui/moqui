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
package org.moqui.impl.actions

import org.moqui.context.ExecutionContext
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.codehaus.groovy.runtime.InvokerHelper

class XmlAction {
    protected final static Logger logger = LoggerFactory.getLogger(XmlAction.class)

    protected final ExecutionContextFactoryImpl ecfi
    protected final Class groovyClass

    XmlAction(ExecutionContextFactoryImpl ecfi, String xmlText, String fileName) {
        this.ecfi = ecfi
        // TODO transform XML to groovy
        String groovyText = null

        // parse groovy
        groovyClass = new GroovyClassLoader().parseClass(groovyText, fileName)
    }

    /** Run the XML actions in the current context of the ExecutionContext */
    Map<String, Object> run(ExecutionContext ec) {
        Script script = InvokerHelper.createScript(groovyClass, new Binding(ec.context))
        Object result = script.run()
        if (result instanceof Map) {
            return (Map<String, Object>) result
        } else if (context.get("result")) {
            return (Map<String, Object>) context.get("result")
        } else {
            return null
        }
    }
}
