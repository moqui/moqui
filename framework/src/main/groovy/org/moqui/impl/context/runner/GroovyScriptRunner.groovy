/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.runner

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ScriptRunner
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ContextBinding

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GroovyScriptRunner implements ScriptRunner {
    protected final static Logger logger = LoggerFactory.getLogger(GroovyScriptRunner.class)

    protected ExecutionContextFactoryImpl ecfi
    protected Cache scriptGroovyLocationCache

    GroovyScriptRunner() { }

    ScriptRunner init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.scriptGroovyLocationCache = ecfi.getCacheFacade().getCache("resource.groovy.location")
        return this
    }

    @CompileStatic
    Object run(String location, String method, ExecutionContext ec) {
        Script script = InvokerHelper.createScript(getGroovyByLocation(location), new ContextBinding(ec.context))
        Object result
        if (method) {
            result = script.invokeMethod(method, {})
        } else {
            result = script.run()
        }
        return result
    }

    void destroy() { }

    @CompileStatic
    Class getGroovyByLocation(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) gc = loadGroovy(location)
        return gc
    }
    @CompileStatic
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
