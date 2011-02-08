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

import org.moqui.context.TemplateRenderer
import freemarker.template.Template
import freemarker.template.Configuration
import freemarker.ext.beans.BeansWrapper
import freemarker.template.TemplateExceptionHandler
import freemarker.template.TemplateException
import freemarker.core.Environment
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.context.Cache
import org.moqui.context.ExecutionContextFactory
import org.moqui.impl.context.ExecutionContextFactoryImpl

class FtlTemplateRenderer implements TemplateRenderer {
    protected final static Logger logger = LoggerFactory.getLogger(FtlTemplateRenderer.class)

    protected final static Configuration defaultFtlConfiguration = makeFtlConfiguration()
    public static Configuration getFtlConfiguration() { return defaultFtlConfiguration }

    protected ExecutionContextFactoryImpl ecfi

    protected Cache templateFtlLocationCache

    FtlTemplateRenderer() { }

    TemplateRenderer init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.templateFtlLocationCache = ecfi.cacheFacade.getCache("resource.ftl.location")
        return this
    }

    void render(String location, Writer writer) {
        Template theTemplate = (Template) templateFtlLocationCache.get(location)
        if (!theTemplate) theTemplate = makeTemplate(location)
        if (!theTemplate) throw new IllegalArgumentException("Could not find template at [${location}]")
        theTemplate.createProcessingEnvironment(ecfi.executionContext.context, writer).process()
    }

    void destroy() { }


    protected Template makeTemplate(String location) {
        Template theTemplate = (Template) templateFtlLocationCache.get(location)
        if (theTemplate) return theTemplate

        Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(ecfi.resourceFacade.getLocationStream(location))
            newTemplate = new Template(location, templateReader, getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing template at [${location}]", e)
        } finally {
            if (templateReader != null) templateReader.close()
        }

        if (newTemplate) templateFtlLocationCache.put(location, newTemplate)
        return newTemplate
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
