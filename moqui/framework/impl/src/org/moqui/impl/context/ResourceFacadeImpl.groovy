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
package org.moqui.impl.context

import freemarker.template.Template
import freemarker.template.Configuration
import freemarker.ext.beans.BeansWrapper

import java.nio.charset.Charset

import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.context.ResourceFacade
import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.impl.actions.XmlAction

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import freemarker.template.TemplateException
import freemarker.core.Environment
import freemarker.template.TemplateExceptionHandler

public class ResourceFacadeImpl implements ResourceFacade {
    protected final static Logger logger = LoggerFactory.getLogger(ResourceFacadeImpl.class)

    protected final static Configuration defaultFtlConfiguration = makeFtlConfiguration()
    public static Configuration getFtlConfiguration() { return defaultFtlConfiguration }

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache templateFtlLocationCache
    protected final Cache scriptGroovyLocationCache
    protected final Cache scriptXmlActionLocationCache

    protected GroovyShell localGroovyShell = null

    ResourceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.templateFtlLocationCache = ecfi.getCacheFacade().getCache("template.ftl.location")
        this.scriptGroovyLocationCache = ecfi.getCacheFacade().getCache("script.groovy.location")
        this.scriptXmlActionLocationCache = ecfi.getCacheFacade().getCache("script.xml-actions.location")
    }

    /** @see org.moqui.context.ResourceFacade#getLocationUrl(String) */
    URL getLocationUrl(String location) {
        String strippedLocation = stripLocationPrefix(location)

        if (location.startsWith("component://")) {
            // turn this into another URL using the component location
            StringBuffer baseLocation = new StringBuffer(strippedLocation)

            // componentName is everything before the first slash
            String componentName;
            int firstSlash = baseLocation.indexOf("/")
            if (firstSlash > 0) {
                componentName = baseLocation.substring(0, firstSlash)
                // got the componentName, now remove it from the baseLocation
                baseLocation.delete(0, firstSlash + 1)
            } else {
                componentName = baseLocation
                baseLocation.delete(0, baseLocation.length())
            }

            baseLocation.insert(0, '/')
            baseLocation.insert(0, this.ecfi.getComponentBaseLocations().get(componentName))
            location = baseLocation.toString()
        }

        URL locationUrl
        if (location.startsWith("classpath://")) {
            // first try the ClassLoader that loaded this class
            locationUrl = this.getClass().getClassLoader().getResource(strippedLocation)
            // no luck? try the system ClassLoader
            if (!locationUrl) locationUrl = ClassLoader.getSystemResource(strippedLocation)
        } else if (location.startsWith("https://") || location.startsWith("http://") ||
                   location.startsWith("ftp://") || location.startsWith("file:") ||
                   location.startsWith("jar:")) {
            locationUrl = new URL(location)
        } else if (location.indexOf(":/") < 0) {
            // no prefix, local file: if starts with '/' is absolute, otherwise is relative to runtime path
            if (location.charAt(0) != '/') location = this.ecfi.runtimePath + '/' + location
            locationUrl = new File(location).toURI().toURL()
        } else {
            throw new IllegalArgumentException("Prefix (scheme) not supported for location [${location}")
        }

        return locationUrl
    }

    /** @see org.moqui.context.ResourceFacade#getLocationStream(String) */
    InputStream getLocationStream(String location) {
        URL lu = getLocationUrl(location)
        if (!lu) return null
        return lu.newInputStream()
    }

    String getLocationText(String location) {
        InputStream is = null
        Reader r = null
        try {
            is = getLocationStream(location)
            if (!is) return null

            r = new InputStreamReader(new BufferedInputStream(is), Charset.forName("UTF-8"))

            StringBuilder sb = new StringBuilder()
            char[] buf = new char[4096]
            int i
            while ((i = r.read(buf, 0, 4096)) > 0) {
                sb.append(buf, 0, i)
            }
            return sb.toString()
        } finally {
            // closing r should close is, if not add that here
            try { if (r) r.close() } catch (IOException e) { logger.warn("Error in close after reading text", e) }
        }
    }

    protected static String stripLocationPrefix(String location) {
        if (!location) return ""

        // first remove colon (:) and everything before it
        StringBuilder strippedLocation = new StringBuilder(location)
        int colonIndex = strippedLocation.indexOf(":")
        if (colonIndex == 0) {
            strippedLocation.deleteCharAt(0)
        } else if (colonIndex > 0) {
            strippedLocation.delete(0, colonIndex+1)
        }

        // delete all leading forward slashes
        while (strippedLocation.charAt(0) == '/') strippedLocation.deleteCharAt(0)

        return strippedLocation.toString()
    }

    /** @see org.moqui.context.ResourceFacade#renderTemplateInCurrentContext(String, Writer) */
    void renderTemplateInCurrentContext(String location, Writer writer) {
        Template theTemplate = (Template) templateFtlLocationCache.get(location)
        if (!theTemplate) theTemplate = makeTemplate(location)

        if (!theTemplate) throw new IllegalArgumentException("Could not find template at ${location}")

        Map root = ecfi.executionContext.context
        theTemplate.createProcessingEnvironment(root, writer).process()
    }

    protected synchronized Template makeTemplate(String location) {
        Template theTemplate = (Template) templateFtlLocationCache.get(location)
        if (theTemplate) return theTemplate

        Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(getLocationStream(location))
            newTemplate = new Template(location, templateReader, getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing template at [${location}]", e)
        } finally {
            if (templateReader) templateReader.close()
        }

        if (newTemplate) templateFtlLocationCache.put(location, newTemplate)
        return newTemplate
    }

    /** @see org.moqui.context.ResourceFacade#runScriptInCurrentContext(String, String) */
    Object runScriptInCurrentContext(String location, String method) {
        ExecutionContext ec = ecfi.executionContext
        if (location.endsWith(".groovy")) {
            Script script = InvokerHelper.createScript(getGroovyByLocation(location), new Binding(ec.context))
            Object result
            if (method) {
                result = script.invokeMethod(method, {})
            } else {
                result = script.run()
            }
            return result
        } else if (location.endsWith(".xml")) {
            XmlAction xa = getXmlActionByLocation(location)
            return xa.run(ec)
        } else {
            throw new IllegalArgumentException("Cannot run script [${location}], unknown extension.")
        }
    }

    Class getGroovyByLocation(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) gc = loadGroovy(location)
        return gc
    }
    protected synchronized Class loadGroovy(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) {
            gc = new GroovyClassLoader().parseClass(getLocationText(location), location)
            scriptGroovyLocationCache.put(location, gc)
        }
        return gc
    }

    XmlAction getXmlActionByLocation(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) loadXmlAction(location)
        return xa
    }
    protected synchronized XmlAction loadXmlAction(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) {
            xa = new XmlAction(ecfi, getLocationText(location), location)
            scriptXmlActionLocationCache.put(location, xa)
        }
        return xa
    }

    boolean evaluateCondition(String expression, String debugLocation) {
        return getGroovyShell().evaluate(expression, debugLocation) as boolean
    }

    Object evaluateContextField(String expression, String debugLocation) {
        return getGroovyShell().evaluate(expression, debugLocation)
    }

    String evaluateStringExpand(String inputString, String debugLocation) {
        return getGroovyShell().evaluate('"""' + inputString + '"""', debugLocation) as String
    }
    protected GroovyShell getGroovyShell() {
        // consider not caching this; does Binding eval at runtime or when built? if at runtime can just create one
        // for the context and it will update with the context, if not then would have to be created every time an
        // expression/script is run
        if (localGroovyShell) return localGroovyShell
        localGroovyShell = new GroovyShell(new Binding(ecfi.executionContext.context))
        return localGroovyShell
    }

    protected static Configuration makeFtlConfiguration() {
        BeansWrapper defaultWrapper = BeansWrapper.getDefaultInstance()
        Configuration newConfig = new Configuration()
        newConfig.setObjectWrapper(defaultWrapper)
        newConfig.setSharedVariable("Static", defaultWrapper.getStaticModels())
        newConfig.setTemplateExceptionHandler(new MoquiTemplateExceptionHandler())
        return newConfig
    }

    static class MoquiTemplateExceptionHandler implements TemplateExceptionHandler {
        public void handleTemplateException(TemplateException te, Environment env, java.io.Writer out)
                throws TemplateException {
            try {
                if (te.cause) {
                    logger.error("Error in FTL render", te.cause)
                    out.write("[Error: ${te.cause.message}]")
                } else {
                    logger.error("Error in FTL render", te)
                    out.write("[Template Error: ${te.message}]")
                }
            } catch (IOException e) {
                throw new TemplateException("Failed to print error message. Cause: " + e, env)
            }
        }
    }
}
