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
package org.moqui.impl.context

import org.moqui.context.ScreenRender
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.impl.screen.ScreenDefinition
import org.moqui.context.ExecutionContext

class ScreenRenderImpl implements ScreenRender {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenRenderImpl.class)

    protected final ScreenFacadeImpl sfi

    protected String rootScreenLocation = null
    protected List<String> screenPathNameList = new ArrayList<String>()
    protected String outputType = "html"
    protected String characterEncoding = "UTF-8"
    protected String macroTemplateLocation = null

    protected Appendable appender

    ScreenRenderImpl(ScreenFacadeImpl sfi) {
        this.sfi = sfi
    }

    Appendable getAppender() { return this.appender }

    ExecutionContext getEc() { return sfi.ecfi.getExecutionContext() }

    @Override
    ScreenRender rootScreen(String rootScreenLocation) { this.rootScreenLocation = rootScreenLocation; return this }

    @Override
    ScreenRender screenPath(List<String> screenNameList) { this.screenPathNameList.addAll(screenNameList); return this }

    @Override
    ScreenRender outputType(String outputType) { this.outputType = outputType; return this }

    @Override
    ScreenRender encoding(String characterEncoding) { this.characterEncoding = characterEncoding;  return this }

    @Override
    ScreenRender macroTemplate(String mtl) { this.macroTemplateLocation = mtl; return this }

    @Override
    void render(Appendable appender) {
        this.appender = appender
        internalRender()
    }

    @Override
    String render() {
        this.appender = new StringWriter()
        internalRender()
        return this.appender.toString()
    }

    protected internalRender() {
        // TODO get screen defs for each screen in path to use for subscreens
        List<ScreenDefinition> screenPathDefList = new ArrayList<ScreenDefinition>(screenPathNameList.size())
        ScreenDefinition rootScreenDef = sfi.getScreenDefinition(rootScreenLocation)

        rootScreenDef.getSection().render(this)
    }
}
