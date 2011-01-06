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

public class ScriptServiceRunner implements ServiceRunner {
    protected ServiceFacadeImpl sfi = null

    ScriptServiceRunner() { }

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> context) {
        String location = sd.location
        if (location.endsWith(".groovy")) {
            Map<String, Object> vars = new HashMap()
            if (context != null) {
                vars.putAll(context)
                vars.put("context", context)
                vars.put("ec", sfi.ecfi.getExecutionContext())
                vars.put("result", new HashMap())
            }
            Script script = InvokerHelper.createScript(sfi.ecfi.resourceFacade.getGroovyByLocation(sd.location), new Binding(vars))
            Object result
            if (sd.serviceNode."@method") {
                result = script.invokeMethod(sd.serviceNode."@method", {})
            } else {
                result = script.run()
            }
            if (result instanceof Map) {
                return (Map<String, Object>) result
            } else if (vars.get("result")) {
                return (Map<String, Object>) vars.get("result")
            } else {
                return null
            }
        } else if (location.endsWith(".xml")) {
            // TODO implement this once XmlAction stuff is in place
            throw new IllegalArgumentException("Cannot run script [${location}], XML Actions not yet implemented.")
        } else {
            throw new IllegalArgumentException("Cannot run script [${location}], unknown extension.")
        }
    }

    public void destroy() { }
}
