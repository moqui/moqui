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

import freemarker.ext.beans.BeansWrapper
import freemarker.template.Configuration
import freemarker.template.Template

import org.moqui.context.ScreenFacade
import org.moqui.context.ScreenRender
import org.moqui.context.Cache

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import org.moqui.impl.context.ExecutionContextFactoryImpl

public class ScreenFacadeImpl implements ScreenFacade {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache screenLocationCache
    protected final Cache screenOutputTemplateCache

    ScreenFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.screenLocationCache = ecfi.cacheFacade.getCache("screen.location")
        this.screenOutputTemplateCache = ecfi.cacheFacade.getCache("screen.output.template")
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

    Template getTemplateForOutputType(String outputType) {
        Template template = (Template) screenOutputTemplateCache.get(outputType)
        if (template) return template

        return makeTemplate(outputType)
    }

    protected synchronized Template makeTemplate(String outputType) {
        Template template = (Template) screenOutputTemplateCache.get(outputType)
        if (template) return template

        String templateLocation = ecfi.getConfXmlRoot()."screen-facade"[0]
                ."screen-text-output".find({ it.@type == outputType })."@macro-template-location"

        Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(ecfi.resourceFacade.getLocationStream(templateLocation))
            newTemplate = new Template(templateLocation, templateReader, makeConfiguration())
        } catch (Exception e) {
            logger.error("Error while initializing Screen Widgets template at [${templateLocation}]", e)
        } finally {
            if (templateReader) templateReader.close()
        }

        if (newTemplate) screenOutputTemplateCache.put(outputType, newTemplate)
        return newTemplate
    }
    protected static Configuration makeConfiguration() {
        BeansWrapper defaultWrapper = BeansWrapper.getDefaultInstance()
        Configuration newConfig = new Configuration()
        newConfig.setObjectWrapper(defaultWrapper)
        newConfig.setSharedVariable("Static", defaultWrapper.getStaticModels())
        return newConfig
    }

    @Override
    ScreenRender makeRender() {
        return new ScreenRenderImpl(this)
    }
}
