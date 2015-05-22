/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.renderer

import freemarker.template.Template
import org.markdown4j.Markdown4jProcessor
import org.moqui.context.Cache
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.TemplateRenderer
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FtlMarkdownTemplateRenderer implements TemplateRenderer {
    protected final static Logger logger = LoggerFactory.getLogger(FtlMarkdownTemplateRenderer.class)

    protected ExecutionContextFactoryImpl ecfi

    protected Cache templateFtlLocationCache

    FtlMarkdownTemplateRenderer() { }

    TemplateRenderer init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.templateFtlLocationCache = ecfi.cacheFacade.getCache("resource.ftl.location")
        return this
    }

    void render(String location, Writer writer) {
        Template theTemplate = (Template) templateFtlLocationCache.get(location)
        if (!theTemplate) theTemplate = makeTemplate(location)
        if (!theTemplate) throw new IllegalArgumentException("Could not find template at ${location}")
        theTemplate.createProcessingEnvironment(ecfi.executionContext.context, writer).process()
    }

    protected Template makeTemplate(String location) {
        Template theTemplate = (Template) templateFtlLocationCache.get(location)
        if (theTemplate) return theTemplate

        Template newTemplate
        try {
            Markdown4jProcessor markdown4jProcessor = new Markdown4jProcessor()
            //ScreenRenderImpl sri = (ScreenRenderImpl) ecfi.getExecutionContext().getContext().get("sri")
            // how to set base URL? if (sri != null) builder.setBase(sri.getBaseLinkUri())

            String mdText = markdown4jProcessor.process(ecfi.resourceFacade.getLocationText(location, false))

            // logger.warn("======== .md.ftl post-markdown text: ${mdText}")

            Reader templateReader = new StringReader(mdText)
            newTemplate = new Template(location, templateReader, ecfi.resourceFacade.ftlTemplateRenderer.getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing template at [${location}]", e)
        }

        if (newTemplate) templateFtlLocationCache.put(location, newTemplate)
        return newTemplate
    }

    String stripTemplateExtension(String fileName) {
        String stripped = fileName.contains(".md") ? fileName.replace(".md", "") : fileName
        stripped = stripped.contains(".markdown") ? stripped.replace(".markdown", "") : stripped
        return stripped.contains(".ftl") ? stripped.replace(".ftl", "") : stripped
    }

    void destroy() { }
}
