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

import org.moqui.context.ScreenRender
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import org.moqui.context.ExecutionContext
import freemarker.template.Template
import org.moqui.impl.context.ContextStack

class ScreenRenderImpl implements ScreenRender {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenRenderImpl.class)

    protected final ScreenFacadeImpl sfi

    protected String rootScreenLocation = null
    protected ScreenDefinition rootScreenDef = null

    protected List<String> screenPathNameList = new ArrayList<String>()
    protected List<ScreenDefinition> screenPathDefList = new ArrayList<ScreenDefinition>()
    protected int screenPathIndex = -1

    protected String renderMode = "html"
    protected String characterEncoding = "UTF-8"
    protected String macroTemplateLocation = null

    protected Writer writer

    ScreenRenderImpl(ScreenFacadeImpl sfi) {
        this.sfi = sfi
    }

    Writer getWriter() { return this.writer }

    ExecutionContext getEc() { return sfi.ecfi.getExecutionContext() }
    ScreenFacadeImpl getSfi() { return sfi }

    @Override
    ScreenRender rootScreen(String rootScreenLocation) { this.rootScreenLocation = rootScreenLocation; return this }

    @Override
    ScreenRender screenPath(List<String> screenNameList) { this.screenPathNameList.addAll(screenNameList); return this }

    @Override
    ScreenRender renderMode(String renderMode) { this.renderMode = renderMode; return this }

    String getRenderMode() { return this.renderMode }

    @Override
    ScreenRender encoding(String characterEncoding) { this.characterEncoding = characterEncoding;  return this }

    @Override
    ScreenRender macroTemplate(String mtl) { this.macroTemplateLocation = mtl; return this }

    @Override
    void render(Writer writer) {
        this.writer = writer
        internalRender()
    }

    @Override
    String render() {
        this.writer = new StringWriter()
        internalRender()
        return this.writer.toString()
    }

    protected internalRender() {
        rootScreenDef = sfi.getScreenDefinition(rootScreenLocation)

        // TODO get screen defs for each screen in path to use for subscreens

        // start rendering at the root section of the root screen
        rootScreenDef.getRootSection().render(this)
    }

    ScreenDefinition getActiveScreenDef() {
        ScreenDefinition screenDef = rootScreenDef
        if (screenPathIndex >= 0) {
            screenDef = screenPathDefList[screenPathIndex]
        }
        return screenDef
    }

    Template getTemplate() {
        if (macroTemplateLocation) {
            return sfi.getTemplateByLocation(macroTemplateLocation)
        } else {
            return sfi.getTemplateByMode(renderMode)
        }
    }

    String renderSection(String sectionName) {
        ScreenDefinition sd = getActiveScreenDef()
        ScreenSection section = sd.getSection(sectionName)
        if (!section) throw new IllegalArgumentException("No section with name [${sectionName}] in screen [${sd.location}]")
        section.render(this)
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }

    String renderForm(String formName) {
        ScreenDefinition sd = getActiveScreenDef()
        ScreenForm form = sd.getForm(formName)
        if (!form) throw new IllegalArgumentException("No form with name [${formName}] in screen [${sd.location}]")
        form.render(this)
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }

    String renderIncludeScreen(String location, String shareScopeStr) {
        boolean shareScope = false
        if (shareScopeStr == "true") shareScope = true

        ContextStack cs = (ContextStack) ec.context
        try {
            if (!shareScope) cs.push()
            sfi.makeRender().rootScreen(location).renderMode(renderMode).encoding(characterEncoding)
                    .macroTemplate(macroTemplateLocation).render(writer)
        } finally {
            if (!shareScope) cs.pop()
        }

        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }

    String renderText(String location, String isTemplateStr) {
        boolean isTemplate = true
        if (isTemplateStr == "false") isTemplate = false

        if (isTemplate) {
            sfi.ecfi.resourceFacade.renderTemplateInCurrentContext(location, writer)
            // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
            return ""
        } else {
            return sfi.ecfi.resourceFacade.getLocationText(location)
        }
    }

    String makeUrl(String url, String urlType) {
        /* TODO handle urlType:
            <xs:enumeration value="transition">
                <xs:annotation><xs:documentation>The name of a transition in the current screen. URL will be build based on the transition definition.</xs:documentation></xs:annotation>
            </xs:enumeration>
            <xs:enumeration value="content">
                <xs:annotation><xs:documentation>A content location (without the content://). URL will be one that can access that content.</xs:documentation></xs:annotation>
            </xs:enumeration>
            <xs:enumeration value="plain">
                <xs:annotation><xs:documentation>A plain URL to be used literally (should start with http:// or https://).</xs:documentation></xs:annotation>
            </xs:enumeration>
         */
        return url
    }

    String makeValue(String fromField, String value) {
        // TODO to groovy interpretation/expansion on fromField and value
        if (value) {
            return value
        } else if (fromField) {
            return ec.context.get(fromField) as String
        } else {
            return ""
        }
    }
}
