/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service.runner

import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner
import org.moqui.context.ExecutionContext
import org.moqui.context.ContextStack

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class ScriptServiceRunner implements ServiceRunner {
    protected final static Logger logger = LoggerFactory.getLogger(ScriptServiceRunner.class)

    protected ServiceFacadeImpl sfi = null

    ScriptServiceRunner() { }

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        ExecutionContext ec = sfi.ecfi.getExecutionContext()
        ContextStack cs = (ContextStack) ec.context
        try {
            // push the entire context to isolate the context for the service call
            cs.pushContext()
            // we have an empty context so add the ec
            cs.put("ec", ec)
            // now add the parameters to this service call
            cs.push(parameters)
            // push again to get a new Map that will protect the parameters Map passed in
            cs.push()
            // add a convenience Map to explicitly put results in
            Map<String, Object> autoResult = new HashMap()
            ec.context.put("result", autoResult)

            Object result = ec.resource.runScriptInCurrentContext((String) sd.serviceNode."@location", (String) sd.serviceNode."@method")

            if (result instanceof Map) {
                return (Map<String, Object>) result
            } else {
                // if there are fields in ec.context that match out-parameters but that aren't in the result, set them
                for (String outParameterName in sd.getOutParameterNames()) {
                    if (!autoResult.containsKey(outParameterName) && cs.get(outParameterName) != null)
                        autoResult.put(outParameterName, cs.get(outParameterName))
                }
                return autoResult
            }
        } finally {
            // pop the entire context to get back to where we were before isolating the context with pushContext
            cs.popContext()
        }
    }

    public void destroy() { }
}
