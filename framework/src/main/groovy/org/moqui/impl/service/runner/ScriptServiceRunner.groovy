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
import org.codehaus.groovy.runtime.InvokerHelper
import org.moqui.impl.service.ServiceRunner
import org.moqui.impl.actions.XmlAction
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.context.ContextStack

public class ScriptServiceRunner implements ServiceRunner {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScriptServiceRunner.class)

    protected ServiceFacadeImpl sfi = null

    ScriptServiceRunner() { }

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
            Map<String, Object> autoResult = new HashMap()
            ec.context.put("result", autoResult)

            Object result = ec.resource.runScriptInCurrentContext(sd.serviceNode."@location", sd.serviceNode."@method")

            if (result instanceof Map) {
                return (Map<String, Object>) result
            } else {
                // if there are fields in ec.context that match out-parameters but that aren't in the result, set them
                for (String outParameterName in sd.getOutParameterNames()) {
                    if (!autoResult.containsKey(outParameterName) && cs.get(outParameterName))
                        autoResult.put(outParameterName, cs.get(outParameterName))
                }
                return autoResult
            }
        } finally {
            // in the push we pushed two Maps to protect the parameters Map, so pop twice
            cs.pop()
            cs.pop()
        }
    }

    public void destroy() { }
}
