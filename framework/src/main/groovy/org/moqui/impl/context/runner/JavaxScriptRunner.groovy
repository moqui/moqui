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

import org.moqui.BaseException
import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ScriptRunner
import org.moqui.impl.context.ExecutionContextFactoryImpl
import javax.script.Bindings
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.SimpleBindings
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JavaxScriptRunner implements ScriptRunner {
    protected final static Logger logger = LoggerFactory.getLogger(JavaxScriptRunner.class)

    protected ScriptEngineManager mgr = new ScriptEngineManager();

    protected ExecutionContextFactoryImpl ecfi
    protected Cache scriptLocationCache
    protected String engineName

    JavaxScriptRunner() { this.engineName = "groovy" }
    JavaxScriptRunner(String engineName) { this.engineName = engineName }

    ScriptRunner init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.scriptLocationCache = ecfi.getCacheFacade().getCache("resource.${engineName}.location")
        return this
    }

    Object run(String location, String method, ExecutionContext ec) {
        // this doesn't support methods, so if passed warn about that
        if (method) logger.warn("Tried to invoke script at [${location}] with method [${method}] through javax.script (JSR-223) runner which does NOT support methods, so it is being ignored.", new BaseException("Script Run Location"))

        ScriptEngine engine = mgr.getEngineByName(engineName)
        return bindAndRun(location, ec, engine, scriptLocationCache)
    }

    void destroy() { }

    static Object bindAndRun(String location, ExecutionContext ec, ScriptEngine engine, Cache scriptLocationCache) {
        Bindings bindings = new SimpleBindings()
        for (Map.Entry ce in ec.getContext()) bindings.put((String) ce.getKey(), ce.getValue())

        Object result
        if (engine instanceof Compilable) {
            // cache the CompiledScript
            CompiledScript script = (CompiledScript) scriptLocationCache.get(location)
            if (script == null) {
                script = ((Compilable) engine).compile(ec.getResource().getLocationText(location, false))
                scriptLocationCache.put(location, script)
            }
            result = script.eval(bindings)
        } else {
            // cache the script text
            String scriptText = (String) scriptLocationCache.get(location)
            if (scriptText == null) {
                scriptText = ec.getResource().getLocationText(location, false)
                scriptLocationCache.put(location, scriptText)
            }
            result = engine.eval(scriptText, bindings)
        }

        return result
    }
}
