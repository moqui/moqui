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
import freemarker.template.TemplateExceptionHandler
import freemarker.template.TemplateException
import freemarker.core.Environment

import javax.activation.DataSource
import javax.activation.MimetypesFileTypeMap
import javax.jcr.Repository
import javax.jcr.Session
import javax.naming.InitialContext

import javax.xml.transform.stream.StreamSource
import javax.xml.transform.TransformerFactory
import javax.xml.transform.Transformer
import javax.xml.transform.URIResolver
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.Source

import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder
import org.apache.commons.mail.ByteArrayDataSource
import org.apache.jackrabbit.commons.JcrUtils
import org.apache.fop.apps.FOUserAgent
import org.apache.fop.apps.Fop
import org.apache.fop.apps.FopFactory

import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.context.TemplateRenderer
import org.moqui.context.ResourceFacade
import org.moqui.context.ResourceReference
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.StupidUtilities

public class ResourceFacadeImpl implements ResourceFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ResourceFacadeImpl.class)

    protected final MimetypesFileTypeMap mimetypesFileTypeMap = new MimetypesFileTypeMap()

    protected final ExecutionContextFactoryImpl ecfi
    protected final Configuration defaultFtlConfiguration

    protected final Cache scriptGroovyLocationCache
    protected final Cache scriptGroovyExpressionCache
    protected final Cache scriptXmlActionLocationCache
    protected final Cache templateFtlLocationCache
    protected final Cache textLocationCache

    protected final Map<String, Class> resourceReferenceClasses = new HashMap()
    protected final Map<String, TemplateRenderer> templateRenderers = new HashMap()

    protected final Map<String, Repository> contentRepositories = new HashMap()
    protected final Map<String, String> contentRepositoryWorkspaces = new HashMap()

    protected final ThreadLocal<Map<String, Session>> contentSessions = new ThreadLocal<Map<String, Session>>()

    protected final Template xmlActionsTemplate

    protected final FopFactory fopFactory

    ResourceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        defaultFtlConfiguration = makeFtlConfiguration(ecfi)

        this.textLocationCache = ecfi.getCacheFacade().getCache("resource.text.location")
        this.templateFtlLocationCache = ecfi.cacheFacade.getCache("resource.ftl.location")

        this.scriptGroovyLocationCache = ecfi.getCacheFacade().getCache("resource.groovy.location")
        this.scriptGroovyExpressionCache = ecfi.getCacheFacade().getCache("resource.groovy.expression")
        this.scriptXmlActionLocationCache = ecfi.getCacheFacade().getCache("resource.xml-actions.location")

        // Setup resource reference classes
        for (Node rrNode in ecfi.confXmlRoot."resource-facade"[0]."resource-reference") {
            Class rrClass = this.getClass().getClassLoader().loadClass(rrNode."@class")
            resourceReferenceClasses.put(rrNode."@scheme", rrClass)
        }

        // Setup template renderers
        for (Node templateRendererNode in ecfi.confXmlRoot."resource-facade"[0]."template-renderer") {
            TemplateRenderer tr = (TemplateRenderer) this.getClass().getClassLoader().loadClass(templateRendererNode."@class").newInstance()
            templateRenderers.put(templateRendererNode."@extension", tr.init(ecfi))
        }

        // Setup content repositories
        for (Node repositoryNode in ecfi.confXmlRoot."repository-list"[0]."repository") {
            try {
                if (repositoryNode."@type" == "davex" || repositoryNode."@type" == "rmi") {
                    Repository repository = JcrUtils.getRepository((String) repositoryNode."@location")
                    contentRepositories.put(repositoryNode."@name", repository)
                } else if (repositoryNode."@type" == "jndi") {
                    InitialContext ic = new InitialContext()
                    Repository repository = (Repository) ic.lookup((String) repositoryNode."@location")
                    contentRepositories.put(repositoryNode."@name", repository)
                } else if (repositoryNode."@type" == "local") {
                    throw new IllegalArgumentException("The local type content repository is not yet supported, pending research into API support for the concept")
                }
                if (repositoryNode."@workspace") contentRepositoryWorkspaces.put(repositoryNode."@name", repositoryNode."@workspace")
            } catch (Exception e) {
                logger.error("Error getting JCR content repository with name [${repositoryNode."@name"}], is of type [${repositoryNode."@type"}] at location [${repositoryNode."@location"}]: ${e.toString()}")
            }
        }

        // setup XmlActions FTL Template
        this.xmlActionsTemplate = makeXmlActionsTemplate()

        // setup FopFactory
        fopFactory = FopFactory.newInstance()
        // Limit the validation for backwards compatibility
        fopFactory.setStrictValidation(false)
        DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder()
        fopFactory.setUserConfig(cfgBuilder.build(getLocationStream("classpath://fop.xconf")))
        fopFactory.getFontManager().setFontBaseURL(ecfi.runtimePath + "/conf")
    }

    void destroyAllInThread() {
        Map<String, Session> sessionMap = contentSessions.get()
        if (sessionMap) for (Session openSession in sessionMap.values()) openSession.logout()
        contentSessions.remove()
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }
    Map<String, TemplateRenderer> getTemplateRenderers() { return templateRenderers }
    Cache getTemplateFtlLocationCache() { return templateFtlLocationCache }

    Repository getContentRepository(String name) { return contentRepositories.get(name) }

    /** Get the active JCR Session for the context/thread, making sure it is live, and make one if needed. */
    Session getContentRepositorySession(String name) {
        Map<String, Session> sessionMap = contentSessions.get()
        if (sessionMap == null) {
            sessionMap = new HashMap()
            contentSessions.set(sessionMap)
        }
        Session newSession = sessionMap[name]
        if (newSession != null) {
            if (newSession.isLive()) {
                return newSession
            } else {
                sessionMap.remove(name)
                newSession = null
            }
        }

        Repository rep = contentRepositories[name]
        if (!rep) return null
        if (contentRepositoryWorkspaces[name]) {
            newSession = rep.login(contentRepositoryWorkspaces[name])
        } else {
            newSession = rep.login()
        }

        if (newSession != null) sessionMap.put(name, newSession)
        return newSession
    }

    /** @see org.moqui.context.ResourceFacade#getLocationReference(String) */
    ResourceReference getLocationReference(String location) {
        String scheme = "file"
        // how to get the scheme for windows? the Java URI class doesn't like spaces, the if we look for the first ":"
        //    it may be a drive letter instead of a scheme/protocol
        if (location.contains(":")) {
            String prefix = location.substring(0, location.indexOf(":"))
            if (!prefix.contains("/") && prefix.length() > 2) scheme = prefix
        }

        Class rrClass = resourceReferenceClasses.get(scheme)
        if (!rrClass) throw new IllegalArgumentException("Prefix (scheme) not supported for location [${location}]")

        ResourceReference rr = (ResourceReference) rrClass.newInstance()
        return rr.init(location, ecfi.executionContext)
    }

    /** @see org.moqui.context.ResourceFacade#getLocationStream(String) */
    InputStream getLocationStream(String location) {
        ResourceReference rr = getLocationReference(location)
        if (!rr) return null
        return rr.openStream()
    }

    String getLocationText(String location, boolean cache) {
        if (cache && textLocationCache.containsKey(location)) return (String) textLocationCache.get(location)
        String text = StupidUtilities.getStreamText(getLocationStream(location))
        if (cache) textLocationCache.put(location, text)
        return text
    }

    DataSource getLocationDataSource(String location) {
        ResourceReference fileResourceRef = getLocationReference(location)

        String fileName = fileResourceRef.fileName
        // if it contains .ftl or .cwiki remove those to avoid problems with trying to find content types based on them
        if (fileName.contains(".ftl")) fileName = fileName.replace(".ftl", "")
        if (fileName.contains(".cwiki")) fileName = fileName.replace(".cwiki", "")
        String fileContentType = getContentType(fileName)

        boolean isBinary = isBinaryContentType(fileContentType)

        if (isBinary) {
            return new ByteArrayDataSource(fileResourceRef.openStream(), fileContentType)
        } else {
            // not a binary object (hopefully), get the text and pass it over
            TemplateRenderer tr = getTemplateRendererByLocation(fileResourceRef.location)
            if (tr != null) {
                StringWriter sw = new StringWriter()
                tr.render(fileResourceRef.location, sw)
                return new ByteArrayDataSource(sw.toString(), fileContentType)
            } else {
                // no renderer found, just grab the text (cached) and throw it to the writer
                String text = getLocationText(fileResourceRef.location, true)
                return new ByteArrayDataSource(sw.toString(), fileContentType)
            }
        }
    }

    /** @see org.moqui.context.ResourceFacade#renderTemplateInCurrentContext(String, Writer) */
    void renderTemplateInCurrentContext(String location, Writer writer) {
        TemplateRenderer tr = getTemplateRendererByLocation(location)
        if (tr != null) {
            tr.render(location, writer)
        } else {
            // no renderer found, just grab the text and throw it to the writer
            String text = getLocationText(location, true)
            if (text) writer.write(text)
        }
    }

    TemplateRenderer getTemplateRendererByLocation(String location) {
        // match against extension for template renderer, with as many dots that match as possible (most specific match)
        int mostDots = 0
        TemplateRenderer tr = null
        for (Map.Entry<String, TemplateRenderer> trEntry in templateRenderers.entrySet()) {
            String ext = trEntry.getKey()
            if (location.endsWith(ext)) {
                int dots = StupidUtilities.countChars(ext, (char) '.')
                if (dots > mostDots) {
                    mostDots = dots
                    tr = trEntry.getValue()
                }
            }
        }
        return tr
    }

    Template getFtlTemplateByLocation(String location) {
        Template theTemplate = (Template) ecfi.resourceFacade.templateFtlLocationCache.get(location)
        if (!theTemplate) theTemplate = makeTemplate(location)
        if (!theTemplate) throw new IllegalArgumentException("Could not find template at [${location}]")
        return theTemplate
    }
    protected Template makeTemplate(String location) {
        Template theTemplate = (Template) ecfi.resourceFacade.templateFtlLocationCache.get(location)
        if (theTemplate) return theTemplate

        Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(ecfi.resourceFacade.getLocationStream(location))
            newTemplate = new Template(location, templateReader, ecfi.resourceFacade.getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing template at [${location}]", e)
        } finally {
            if (templateReader != null) templateReader.close()
        }

        if (newTemplate) ecfi.resourceFacade.templateFtlLocationCache.put(location, newTemplate)
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
    protected Class loadGroovy(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) {
            String groovyText = getLocationText(location, false)
            gc = new GroovyClassLoader().parseClass(groovyText, location)
            scriptGroovyLocationCache.put(location, gc)
        }
        return gc
    }

    XmlAction getXmlActionByLocation(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) loadXmlAction(location)
        return xa
    }
    protected XmlAction loadXmlAction(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) {
            xa = new XmlAction(ecfi, getLocationText(location, false), location)
            scriptXmlActionLocationCache.put(location, xa)
        }
        return xa
    }
    Template getXmlActionsTemplate() { return xmlActionsTemplate }
    protected Template makeXmlActionsTemplate() {
        String templateLocation = ecfi.confXmlRoot."resource-facade"[0]."@xml-actions-template-location"
        Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(this.getLocationStream(templateLocation))
            newTemplate = new Template(templateLocation, templateReader, getFtlConfiguration())
        } catch (Exception e) {
            logger.error("Error while initializing XMLActions template at [${templateLocation}]", e)
        } finally {
            if (templateReader) templateReader.close()
        }
        return newTemplate
    }


    /** @see org.moqui.context.ResourceFacade#evaluateCondition(String, String) */
    boolean evaluateCondition(String expression, String debugLocation) {
        if (!expression) return false
        try {
            Script script = getGroovyScript(expression)
            Object result = script.run()
            return result as boolean
        } catch (Exception e) {
            throw new IllegalArgumentException("Error in condition [${expression}] from [${debugLocation}]", e)
        }
    }

    /** @see org.moqui.context.ResourceFacade#evaluateContextField(String, String) */
    Object evaluateContextField(String expression, String debugLocation) {
        if (!expression) return null
        try {
            Script script = getGroovyScript(expression)
            Object result = script.run()
            return result
        } catch (Exception e) {
            throw new IllegalArgumentException("Error in field expression [${expression}] from [${debugLocation}]", e)
        }
    }

    /** @see org.moqui.context.ResourceFacade#evaluateStringExpand(String, String) */
    String evaluateStringExpand(String inputString, String debugLocation) {
        if (!inputString) return ""

        // always localize string before expanding
        if (inputString.length() < 256) inputString = ecfi.l10nFacade.getLocalizedMessage(inputString)

        String expression = '"""' + inputString + '"""'
        try {
            Script script = getGroovyScript(expression)
            Object result = script.run()
            return result as String
        } catch (Exception e) {
            throw new IllegalArgumentException("Error in string expression [${expression}] from [${debugLocation}]", e)
        }
    }

    Script getGroovyScript(String expression) {
        Class groovyClass = (Class) this.scriptGroovyExpressionCache.get(expression)
        if (groovyClass == null) {
            groovyClass = new GroovyClassLoader().parseClass(expression)
            this.scriptGroovyExpressionCache.put(expression, groovyClass)
        }
        // NOTE: consider keeping the binding somewhere, like in the ExecutionContext to avoid creating repeatedly
        Script script = InvokerHelper.createScript(groovyClass, new Binding(ecfi.executionContext.context))
        return script
    }

    static String stripLocationPrefix(String location) {
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

    String getContentType(String filename) {
        if (!filename || !filename.contains(".")) return null
        String type = mimetypesFileTypeMap.getContentType(filename)
        // strip any parameters, ie after the ;
        if (type.contains(";")) type = type.substring(0, type.indexOf(";"))
        return type
    }

    boolean isBinaryContentType(String contentType) {
        if (!contentType) return false
        if (contentType.startsWith("text/")) return false
        // aside from text/*, a few notable exceptions:
        if (contentType == "application/javascript") return false
        if (contentType == "application/rtf") return false
        if (contentType == "application/xml") return false
        if (contentType == "application/xml-dtd") return false
        return true
    }

    public Configuration getFtlConfiguration() { return defaultFtlConfiguration }

    protected static Configuration makeFtlConfiguration(ExecutionContextFactoryImpl ecfi) {
        Configuration newConfig = new MoquiConfiguration(ecfi)
        BeansWrapper defaultWrapper = BeansWrapper.getDefaultInstance()
        newConfig.setObjectWrapper(defaultWrapper)
        newConfig.setSharedVariable("Static", defaultWrapper.getStaticModels())

        // not needed, using getTemplate override instead: newConfig.setCacheStorage(new NullCacheStorage())
        // not needed, using getTemplate override instead: newConfig.setTemplateUpdateDelay(1)
        // not needed, using getTemplate override instead: newConfig.setTemplateLoader(new MoquiResourceTemplateLoader(ecfi))
        // not needed, using getTemplate override instead: newConfig.setLocalizedLookup(false)

        newConfig.setTemplateExceptionHandler(new MoquiTemplateExceptionHandler())
        newConfig.setWhitespaceStripping(true)
        return newConfig
    }

    static class MoquiConfiguration extends Configuration {
        ExecutionContextFactoryImpl ecfi
        MoquiConfiguration(ExecutionContextFactoryImpl ecfi) {
            super()
            this.ecfi = ecfi
        }
        @Override
        Template getTemplate(String name, Locale locale, String encoding, boolean parse) {
            //return super.getTemplate(name, locale, encoding, parse)
            // NOTE: doing this because template loading behavior with cache/etc not desired and was having issues
            Template theTemplate
            if (parse) {
                theTemplate = ecfi.resourceFacade.getFtlTemplateByLocation(name)
            } else {
                String text = ecfi.resourceFacade.getLocationText(name, true)
                theTemplate = Template.getPlainTextTemplate(name, text, this)
            }
            // NOTE: this is the same exception the standard FreeMarker code returns
            if (theTemplate == null) throw new FileNotFoundException("Template [${name}] not found.")
            return theTemplate
        }
    }

    /* This is not needed with the getTemplate override
    static class NullCacheStorage implements CacheStorage {
        Object get(Object o) { return null }
        void put(Object o, Object o1) { }
        void remove(Object o) { }
        void clear() { }
    }

    static class MoquiResourceTemplateLoader implements TemplateLoader {
        ExecutionContextFactoryImpl ecfi
        MoquiResourceTemplateLoader(ExecutionContextFactoryImpl ecfi) { this.ecfi = ecfi }

        public Object findTemplateSource(String name) throws IOException {
            String text = ecfi.resourceFacade.getLocationText(name, true)
            if (text) return name
            return null
        }
        public long getLastModified(Object templateSource) {
            ResourceReference rr = ecfi.resourceFacade.getLocationReference((String) templateSource)
            return rr.supportsLastModified() ? rr.getLastModified() : -1
        }
        public Reader getReader(Object templateSource, String encoding) throws IOException {
            String text = ecfi.resourceFacade.getLocationText((String) templateSource, true)
            if (!text) {
                logger.warn("Could not find text at location [${templateSource}] reffered to in an FTL template.")
                text = ""
            }
            return new StringReader(text)
        }
        public void closeTemplateSource(Object templateSource) throws IOException { }
    }
    */

    static class MoquiTemplateExceptionHandler implements TemplateExceptionHandler {
        public void handleTemplateException(TemplateException te, Environment env, java.io.Writer out)
                throws TemplateException {
            try {
                // TODO: encode error, something like: StringUtil.SimpleEncoder simpleEncoder = FreeMarkerWorker.getWrappedObject("simpleEncoder", env);
                // stackTrace = simpleEncoder.encode(stackTrace);
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

    void xslFoTransform(StreamSource xslFoSrc, StreamSource xsltSrc, OutputStream out, String contentType) {
        FOUserAgent foUserAgent = fopFactory.newFOUserAgent()
        Fop fop = fopFactory.newFop(contentType, foUserAgent, out)

        TransformerFactory factory = TransformerFactory.newInstance()
        Transformer transformer = xsltSrc == null ? factory.newTransformer() : factory.newTransformer(xsltSrc)
        transformer.setURIResolver(new LocalResolver(ecfi, transformer.getURIResolver()))
        transformer.transform(xslFoSrc, new SAXResult(fop.getDefaultHandler()))
    }

    static class LocalResolver implements URIResolver {
        protected ExecutionContextFactoryImpl ecfi
        protected URIResolver defaultResolver

        protected LocalResolver() {}

        public LocalResolver(ExecutionContextFactoryImpl ecfi, URIResolver defaultResolver) {
            this.ecfi = ecfi
            this.defaultResolver = defaultResolver
        }

        public Source resolve(String href, String base) {
            InputStream is = ecfi.resourceFacade.getLocationStream(href)
            if (is != null) return new StreamSource(is)
            return defaultResolver.resolve(href, base)
        }
    }
}
