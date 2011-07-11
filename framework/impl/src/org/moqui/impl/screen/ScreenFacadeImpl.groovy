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
package org.moqui.impl.screen

import freemarker.template.Template

import org.moqui.context.ScreenFacade
import org.moqui.context.ScreenRender
import org.moqui.context.Cache

import org.moqui.impl.context.ExecutionContextFactoryImpl

public class ScreenFacadeImpl implements ScreenFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScreenFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache screenLocationCache
    protected final Cache screenTemplateModeCache
    protected final Cache screenTemplateLocationCache

    ScreenFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.screenLocationCache = ecfi.cacheFacade.getCache("screen.location")
        this.screenTemplateModeCache = ecfi.cacheFacade.getCache("screen.template.mode")
        this.screenTemplateLocationCache = ecfi.cacheFacade.getCache("screen.template.location")
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }

    ScreenDefinition getScreenDefinition(String location) {
        ScreenDefinition sd = (ScreenDefinition) screenLocationCache.get(location)
        if (sd) return sd

        return makeScreenDefinition(location)
    }

    protected synchronized ScreenDefinition makeScreenDefinition(String location) {
        ScreenDefinition sd = (ScreenDefinition) screenLocationCache.get(location)
        if (sd) return sd

        Node screenNode = null
        InputStream screenFileIs = null

        try {
            screenFileIs = ecfi.resourceFacade.getLocationStream(location)
            screenNode = new XmlParser().parse(screenFileIs)
        } catch (IOException e) {
            // probably because there is no resource at that location, so do nothing
            throw new IllegalArgumentException("Error finding screen at location ${location}", e)
        } finally {
            if (screenFileIs != null) screenFileIs.close()
        }

        if (screenNode == null) {
            throw new IllegalArgumentException("Cound not find definition for screen at location [${location}]")
        }

        sd = new ScreenDefinition(this, screenNode, location)
        screenLocationCache.put(location, sd)
        return sd
    }

    String getMimeTypeByMode(String renderMode) {
        String mimeType = ecfi.getConfXmlRoot()."screen-facade"[0]
                ."screen-text-output".find({ it.@type == renderMode })?."@mime-type"
        return mimeType
    }

    Template getTemplateByMode(String renderMode) {
        Template template = (Template) screenTemplateModeCache.get(renderMode)
        if (template) return template

        template = makeTemplateByMode(renderMode)
        if (!template) throw new IllegalArgumentException("Could not find screen render template for mode [${renderMode}]")
        return template
    }

    protected synchronized Template makeTemplateByMode(String renderMode) {
        Template template = (Template) screenTemplateModeCache.get(renderMode)
        if (template) return template

        String templateLocation = ecfi.getConfXmlRoot()."screen-facade"[0]
                ."screen-text-output".find({ it.@type == renderMode })?."@macro-template-location"
        if (!templateLocation) throw new IllegalArgumentException("Could not find macro-template-location for render mode (screen-text-output.@type) [${renderMode}]")
        // NOTE: this is a special case where we need something to call #recurse so that all includes can be straight libraries
        String rootTemplate = """<#include "${templateLocation}"/>
            <#recurse widgetsNode>
            """

        Template newTemplate
        try {
            newTemplate = new Template("moqui.automatic.${renderMode}", new StringReader(rootTemplate),
                    ecfi.resourceFacade.ftlTemplateRenderer.getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing Screen Widgets template at [${templateLocation}]", e)
        }

        screenTemplateModeCache.put(renderMode, newTemplate)
        return newTemplate
    }

    Template getTemplateByLocation(String templateLocation) {
        Template template = (Template) screenTemplateLocationCache.get(templateLocation)
        if (template) return template
        return makeTemplateByLocation(templateLocation)
    }

    protected synchronized Template makeTemplateByLocation(String templateLocation) {
        Template template = (Template) screenTemplateLocationCache.get(templateLocation)
        if (template) return template

        // NOTE: this is a special case where we need something to call #recurse so that all includes can be straight libraries
        String rootTemplate = """<#include "${templateLocation}"/>
            <#recurse widgetsNode>
            """


        Template newTemplate
        try {
            // this location needs to look like a filename in the runtime directory, otherwise FTL will look for includes under the directory it looks like instead
            String filename = templateLocation.substring(templateLocation.lastIndexOf("/")+1)
            newTemplate = new Template(filename, new StringReader(rootTemplate),
                    ecfi.resourceFacade.ftlTemplateRenderer.getFtlConfiguration())
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while initializing Screen Widgets template at [${templateLocation}]", e)
        }

        screenTemplateLocationCache.put(templateLocation, newTemplate)
        return newTemplate
    }

    @Override
    ScreenRender makeRender() {
        return new ScreenRenderImpl(this)
    }
}
