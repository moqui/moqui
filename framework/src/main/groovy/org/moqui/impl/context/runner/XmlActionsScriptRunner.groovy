/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.runner

import freemarker.template.Template

import org.moqui.context.Cache
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ScriptRunner
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.actions.XmlAction
import org.moqui.context.ExecutionContext

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class XmlActionsScriptRunner implements ScriptRunner {
    protected final static Logger logger = LoggerFactory.getLogger(XmlActionsScriptRunner.class)

    protected ExecutionContextFactoryImpl ecfi
    protected Cache scriptXmlActionLocationCache
    protected Template xmlActionsTemplate = null

    XmlActionsScriptRunner() { }

    ScriptRunner init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.scriptXmlActionLocationCache = ecfi.getCacheFacade().getCache("resource.xml-actions.location")
        return this
    }

    Object run(String location, String method, ExecutionContext ec) {
        XmlAction xa = getXmlActionByLocation(location)
        return xa.run(ec)
    }

    void destroy() { }

    XmlAction getXmlActionByLocation(String location) {
        XmlAction xa = (XmlAction) scriptXmlActionLocationCache.get(location)
        if (xa == null) xa = loadXmlAction(location)
        return xa
    }
    protected synchronized XmlAction loadXmlAction(String location) {
        XmlAction xa = (XmlAction) scriptXmlActionLocationCache.get(location)
        if (xa == null) {
            xa = new XmlAction(ecfi, ecfi.resourceFacade.getLocationText(location, false), location)
            scriptXmlActionLocationCache.put(location, xa)
        }
        return xa
    }

    Template getXmlActionsTemplate() {
        if (xmlActionsTemplate == null) makeXmlActionsTemplate()
        return xmlActionsTemplate
    }
    protected synchronized void makeXmlActionsTemplate() {
        if (xmlActionsTemplate != null) return

        String templateLocation = ecfi.confXmlRoot."resource-facade"[0]."@xml-actions-template-location"
        Template newTemplate = null
        Reader templateReader = null
        try {
            templateReader = new InputStreamReader(ecfi.resourceFacade.getLocationStream(templateLocation))
            newTemplate = new Template(templateLocation, templateReader,
                    ecfi.resourceFacade.ftlTemplateRenderer.getFtlConfiguration())
        } catch (Exception e) {
            logger.error("Error while initializing XMLActions template at [${templateLocation}]", e)
        } finally {
            if (templateReader) templateReader.close()
        }
        xmlActionsTemplate = newTemplate
    }
}
