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
package org.moqui.impl.context.runner

import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ScriptRunner
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.StupidUtilities

class GroovyScriptRunner implements ScriptRunner {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GroovyScriptRunner.class)

    protected ExecutionContextFactoryImpl ecfi
    protected Cache scriptGroovyLocationCache

    GroovyScriptRunner() { }

    ScriptRunner init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.scriptGroovyLocationCache = ecfi.getCacheFacade().getCache("resource.groovy.location")
        return this
    }

    Object run(String location, String method, ExecutionContext ec) {
        Script script = InvokerHelper.createScript(getGroovyByLocation(location), new Binding(ec.context))
        Object result
        if (method) {
            result = script.invokeMethod(method, {})
        } else {
            result = script.run()
        }
        return result
    }

    void destroy() { }

    Class getGroovyByLocation(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) gc = loadGroovy(location)
        return gc
    }
    protected Class loadGroovy(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) {
            String groovyText = ecfi.resourceFacade.getLocationText(location, false)
            gc = new GroovyClassLoader().parseClass(groovyText, StupidUtilities.cleanStringForJavaName(location))
            scriptGroovyLocationCache.put(location, gc)
        }
        return gc
    }
}
