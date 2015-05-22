/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.renderer

import groovy.text.GStringTemplateEngine

import org.moqui.context.Cache
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.TemplateRenderer
import org.moqui.impl.context.ExecutionContextFactoryImpl

class GStringTemplateRenderer implements TemplateRenderer {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GStringTemplateRenderer.class)

    protected ExecutionContextFactoryImpl ecfi
    protected Cache templateGStringLocationCache

    GStringTemplateRenderer() { }

    TemplateRenderer init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.templateGStringLocationCache = ecfi.cacheFacade.getCache("resource.gstring.location")
        return this
    }

    void render(String location, Writer writer) {
        groovy.text.Template theTemplate = getGStringTemplateByLocation(location)
        Writable writable = theTemplate.make(ecfi.executionContext.context)
        writable.writeTo(writer)
    }

    String stripTemplateExtension(String fileName) {
        return fileName.contains(".gstring") ? fileName.replace(".gstring", "") : fileName
    }

    void destroy() { }

    groovy.text.Template getGStringTemplateByLocation(String location) {
        groovy.text.Template theTemplate =
                (groovy.text.Template) templateGStringLocationCache.get(location)
        if (!theTemplate) theTemplate = makeGStringTemplate(location)
        if (!theTemplate) throw new IllegalArgumentException("Could not find template at [${location}]")
        return theTemplate
    }
    protected groovy.text.Template makeGStringTemplate(String location) {
        groovy.text.Template theTemplate =
                (groovy.text.Template) templateGStringLocationCache.get(location)
        if (theTemplate) return theTemplate

        groovy.text.Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(ecfi.resourceFacade.getLocationStream(location))
            GStringTemplateEngine gste = new GStringTemplateEngine()
            newTemplate = gste.createTemplate(templateReader)
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing template at [${location}]", e)
        } finally {
            if (templateReader != null) templateReader.close()
        }

        if (newTemplate) templateGStringLocationCache.put(location, newTemplate)
        return newTemplate
    }

}
