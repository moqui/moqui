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
package org.moqui.impl.service.runner

import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ContextStack
import org.moqui.impl.actions.XmlAction

public class InlineServiceRunner implements ServiceRunner {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InlineServiceRunner.class)

    protected ServiceFacadeImpl sfi = null

    InlineServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        ExecutionContext ec = sfi.ecfi.getExecutionContext()
        ContextStack cs = (ContextStack) ec.context
        try {
            cs.push(parameters)
            // push again to get a new Map that will protect the parameters Map passed in
            cs.push()
            // ec is already in place, in the contextRoot, so no need to put here
            // context is handled by the ContextStack itself, always there
            ec.context.put("result", new HashMap())

            XmlAction xa = sd.getXmlAction()
            Object result = xa.run(ec)

            if (result instanceof Map) {
                return (Map<String, Object>) result
            } else if (ec.context.get("result")) {
                return (Map<String, Object>) ec.context.get("result")
            } else {
                return null
            }
        } catch (Throwable t) {
            logger.error("Error running inline XML Actions in service [${sd.serviceName}]: ", t)
            throw t
        } finally {
            // in the push we pushed two Maps to protect the parameters Map, so pop twice
            cs.pop()
            cs.pop()
        }
    }

    public void destroy() { }
}
