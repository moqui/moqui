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
        groovy.text.Template theTemplate = ecfi.resourceFacade.getGStringTemplateByLocation(location)
        Writable writable = theTemplate.make(ecfi.executionContext.context)
        writable.writeTo(writer)
    }

    String stripTemplateExtension(String fileName) {
        return fileName.contains(".gstring") ? fileName.replace(".gstring", "") : fileName
    }

    void destroy() { }

    groovy.text.Template getGStringTemplateByLocation(String location) {
        groovy.text.Template theTemplate =
                (groovy.text.Template) ecfi.resourceFacade.templateGStringLocationCache.get(location)
        if (!theTemplate) theTemplate = makeGStringTemplate(location)
        if (!theTemplate) throw new IllegalArgumentException("Could not find template at [${location}]")
        return theTemplate
    }
    protected groovy.text.Template makeGStringTemplate(String location) {
        groovy.text.Template theTemplate =
                (groovy.text.Template) ecfi.resourceFacade.templateGStringLocationCache.get(location)
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

        if (newTemplate) ecfi.resourceFacade.templateGStringLocationCache.put(location, newTemplate)
        return newTemplate
    }

}
