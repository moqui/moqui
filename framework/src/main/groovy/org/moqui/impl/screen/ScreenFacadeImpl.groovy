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
import net.sf.ehcache.Element
import org.moqui.context.ResourceReference
import org.moqui.context.ScreenFacade
import org.moqui.context.ScreenRender
import org.moqui.context.Cache
import org.moqui.impl.context.CacheImpl
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.screen.ScreenDefinition.SubscreensItem
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class ScreenFacadeImpl implements ScreenFacade {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache screenLocationCache
    protected final Map<String, ScreenDefinition> screenLocationPermCache = new HashMap()
    protected final Cache screenTemplateModeCache
    protected final Cache screenTemplateLocationCache
    protected final Cache widgetTemplateLocationCache
    protected final Cache screenFindPathCache

    ScreenFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.screenLocationCache = ecfi.cacheFacade.getCache("screen.location")
        this.screenTemplateModeCache = ecfi.cacheFacade.getCache("screen.template.mode")
        this.screenTemplateLocationCache = ecfi.cacheFacade.getCache("screen.template.location")
        this.widgetTemplateLocationCache = ecfi.cacheFacade.getCache("widget.template.location")
        this.screenFindPathCache = ecfi.cacheFacade.getCache("screen.find.path")
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }

    ScreenDefinition getScreenDefinition(String location) {
        if (!location) return null
        ScreenDefinition sd = (ScreenDefinition) screenLocationCache.get(location)
        if (sd) return sd

        return makeScreenDefinition(location)
    }

    protected synchronized ScreenDefinition makeScreenDefinition(String location) {
        ScreenDefinition sd = (ScreenDefinition) screenLocationCache.get(location)
        if (sd) return sd

        ResourceReference screenRr = ecfi.getResourceFacade().getLocationReference(location)

        ScreenDefinition permSd = (ScreenDefinition) screenLocationPermCache.get(location)
        if (permSd) {
            // check to see if file has been modified, if we know when it was last modified
            if (permSd.sourceLastModified && screenRr.supportsLastModified() &&
                    screenRr.getLastModified() == permSd.sourceLastModified) {
                //logger.warn("========= screen expired but hasn't changed so reusing: ${location}")
                screenLocationCache.put(location, permSd)
                return permSd
            } else {
                screenLocationPermCache.remove(location)
            }
        }

        Node screenNode = null
        InputStream screenFileIs = null

        try {
            screenFileIs = screenRr.openStream()
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
        // logger.warn("========= loaded screen [${location}] supports LM ${screenRr.supportsLastModified()}, LM: ${screenRr.getLastModified()}")
        sd.sourceLastModified = screenRr.supportsLastModified() ? screenRr.getLastModified() : null
        screenLocationCache.put(location, sd)
        if (screenRr.supportsLastModified()) screenLocationPermCache.put(location, sd)
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
        String rootTemplate = """<#include "${templateLocation}"/><#visit widgetsNode>"""

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
        String rootTemplate = """<#include "${templateLocation}"/><#visit widgetsNode>"""


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

    Node getWidgetTemplatesNodeByLocation(String templateLocation) {
        Node templatesNode = (Node) widgetTemplateLocationCache.get(templateLocation)
        if (templatesNode) return templatesNode
        return makeWidgetTemplatesNodeByLocation(templateLocation)
    }

    protected synchronized Node makeWidgetTemplatesNodeByLocation(String templateLocation) {
        Node templatesNode = (Node) widgetTemplateLocationCache.get(templateLocation)
        if (templatesNode) return templatesNode

        templatesNode = new XmlParser().parse(ecfi.resourceFacade.getLocationStream(templateLocation))

        widgetTemplateLocationCache.put(templateLocation, templatesNode)
        return templatesNode
    }

    String getScreenDisplayString(String rootLocation, int levels) {
        StringBuilder sb = new StringBuilder()
        List<String> infoList = getScreenDisplayInfo(rootLocation, levels)
        for (String info in infoList) sb.append(info).append("\n")
        return sb.toString()
    }
    List<String> getScreenDisplayInfo(String rootLocation, int levels) {
        ScreenInfo rootInfo = new ScreenInfo(getScreenDefinition(rootLocation), null, null)
        List<String> infoList = []
        addScreenDisplayInfo(infoList, rootInfo, 0, levels)
        return infoList
    }
    void addScreenDisplayInfo(List<String> infoList, ScreenInfo si, int level, int levels) {
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < level; i++) sb.append("- ")
        sb.append(" ").append(si.name).append(" ")
        sb.append("Subscreens: ").append(si.allSubscreens).append("(").append(si.allSubscreensNonPlaceholder).append("), ")
        sb.append("Transitions: ").append(si.transitions).append(" sub ").append(si.allSubscreensTransitions).append(", ")
        sb.append("Sections: ").append(si.sections).append(" sub ").append(si.allSubscreensSections).append(", ")
        sb.append("Forms: ").append(si.forms).append(" sub ").append(si.allSubscreensForms).append(", ")
        sb.append("Trees: ").append(si.trees).append(" sub ").append(si.allSubscreensTrees).append(" ")
        infoList.add(sb.toString())

        if (level == levels) return
        for (ScreenInfo childSi in si.subscreenInfoByName.values()) {
            addScreenDisplayInfo(infoList, childSi, level+1, levels)
        }
    }

    class ScreenInfo {
        ScreenDefinition sd
        SubscreensItem ssi
        ScreenInfo parentInfo
        Map<String, ScreenInfo> subscreenInfoByName = [:]
        String name

        boolean isNonPlaceholder = false
        int subscreens = 0, allSubscreens = 0, subscreensNonPlaceholder = 0, allSubscreensNonPlaceholder = 0
        int forms = 0, allSubscreensForms = 0
        int trees = 0, allSubscreensTrees = 0
        int sections = 0, allSubscreensSections = 0
        int transitions = 0, allSubscreensTransitions = 0

        ScreenInfo(ScreenDefinition sd, SubscreensItem ssi, ScreenInfo parentInfo) {
            this.sd = sd
            this.ssi = ssi
            this.parentInfo = parentInfo
            this.name = ssi ? ssi.getName() : sd.getScreenName()

            subscreens = sd.subscreensByName.size()

            forms = sd.formByName.size()
            trees = sd.treeByName.size()
            sections = sd.sectionByName.size()
            transitions = sd.transitionByName.size()
            isNonPlaceholder = forms || sections || transitions

            // trickle up totals
            ScreenInfo curParent = parentInfo
            while (curParent != null) {
                curParent.allSubscreens += 1
                if (isNonPlaceholder) curParent.allSubscreensNonPlaceholder += 1
                curParent.allSubscreensForms += forms
                curParent.allSubscreensTrees += trees
                curParent.allSubscreensSections += sections
                curParent.allSubscreensTransitions += transitions
                curParent = curParent.parentInfo
            }

            // get info for all subscreens
            for (Map.Entry<String, SubscreensItem> ssEntry in sd.subscreensByName.entrySet()) {
                SubscreensItem curSsi = ssEntry.getValue()
                ScreenDefinition ssSd = getScreenDefinition(curSsi.getLocation())
                if (ssSd == null) {
                    logger.warn("While getting ScreenInfo screen not found for ${curSsi.getName()} at: ${curSsi.getLocation()}")
                    continue
                }
                subscreenInfoByName.put(ssEntry.getKey(), new ScreenInfo(ssSd, curSsi, this))
            }
        }
    }

    @Override
    ScreenRender makeRender() {
        return new ScreenRenderImpl(this)
    }
}
