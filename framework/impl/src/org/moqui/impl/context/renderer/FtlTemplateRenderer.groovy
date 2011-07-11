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
package org.moqui.impl.context.renderer

import freemarker.core.Environment
import freemarker.ext.beans.BeansWrapper
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import freemarker.template.TemplateException

import org.moqui.context.Cache
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.TemplateRenderer
import org.moqui.impl.context.ExecutionContextFactoryImpl

class FtlTemplateRenderer implements TemplateRenderer {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FtlTemplateRenderer.class)

    protected ExecutionContextFactoryImpl ecfi

    protected Configuration defaultFtlConfiguration
    protected Cache templateFtlLocationCache

    FtlTemplateRenderer() { }

    TemplateRenderer init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.defaultFtlConfiguration = makeFtlConfiguration(ecfi)
        this.templateFtlLocationCache = ecfi.cacheFacade.getCache("resource.ftl.location")
        return this
    }

    void render(String location, Writer writer) {
        Template theTemplate = getFtlTemplateByLocation(location)
        theTemplate.createProcessingEnvironment(ecfi.executionContext.context, writer).process()
    }

    String stripTemplateExtension(String fileName) {
        return fileName.contains(".ftl") ? fileName.replace(".ftl", "") : fileName
    }

    void destroy() { }

    Cache getTemplateFtlLocationCache() { return templateFtlLocationCache }

    freemarker.template.Template getFtlTemplateByLocation(String location) {
        freemarker.template.Template theTemplate = (freemarker.template.Template) templateFtlLocationCache.get(location)
        if (!theTemplate) theTemplate = makeTemplate(location)
        if (!theTemplate) throw new IllegalArgumentException("Could not find template at [${location}]")
        return theTemplate
    }
    protected freemarker.template.Template makeTemplate(String location) {
        freemarker.template.Template theTemplate = (freemarker.template.Template) templateFtlLocationCache.get(location)
        if (theTemplate) return theTemplate

        freemarker.template.Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(ecfi.resourceFacade.getLocationStream(location))
            newTemplate = new freemarker.template.Template(location, templateReader, getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing template at [${location}]", e)
        } finally {
            if (templateReader != null) templateReader.close()
        }

        if (newTemplate) templateFtlLocationCache.put(location, newTemplate)
        return newTemplate
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
        freemarker.template.Template getTemplate(String name, Locale locale, String encoding, boolean parse) {
            //return super.getTemplate(name, locale, encoding, parse)
            // NOTE: doing this because template loading behavior with cache/etc not desired and was having issues
            freemarker.template.Template theTemplate
            if (parse) {
                theTemplate = ecfi.resourceFacade.ftlTemplateRenderer.getFtlTemplateByLocation(name)
            } else {
                String text = ecfi.resourceFacade.getLocationText(name, true)
                theTemplate = freemarker.template.Template.getPlainTextTemplate(name, text, this)
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
}
