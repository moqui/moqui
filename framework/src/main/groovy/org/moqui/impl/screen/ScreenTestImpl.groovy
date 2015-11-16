/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.impl.screen

import groovy.transform.CompileStatic
import org.moqui.context.ContextStack
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.screen.ScreenRender
import org.moqui.screen.ScreenTest
import org.moqui.screen.ScreenTest.ScreenTestRender
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class ScreenTestImpl implements ScreenTest {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenTestImpl.class)

    protected final ExecutionContextFactoryImpl ecfi
    protected final ScreenFacadeImpl sfi

    protected String rootScreenLocation = null
    protected String baseScreenPath = null
    protected String outputType = null
    protected String characterEncoding = null
    protected String macroTemplateLocation = null
    protected String baseLinkUrl = null
    protected String servletContextPath = null
    protected String webappName = null

    final Map<String, Object> sessionAttributes = [:]

    ScreenTestImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        sfi = ecfi.screenFacade
    }

    @Override
    ScreenTest rootScreen(String screenLocation) { rootScreenLocation = screenLocation; return this }
    @Override
    ScreenTest baseScreenPath(String screenPath) {
        baseScreenPath = screenPath
        if (baseScreenPath.endsWith("/")) baseScreenPath = baseScreenPath.substring(0, baseScreenPath.length() - 1)
        return this
    }
    @Override
    ScreenTest renderMode(String outputType) { this.outputType = outputType; return this }
    @Override
    ScreenTest encoding(String characterEncoding) { this.characterEncoding = characterEncoding; return this }
    @Override
    ScreenTest macroTemplate(String macroTemplateLocation) { this.macroTemplateLocation = macroTemplateLocation; return this }
    @Override
    ScreenTest baseLinkUrl(String baseLinkUrl) { this.baseLinkUrl = baseLinkUrl; return this }
    @Override
    ScreenTest servletContextPath(String scp) { this.servletContextPath = scp; return this }
    @Override
    ScreenTest webappName(String wan) { webappName = wan; return this }

    @Override
    List<String> getNoRequiredParameterPaths(Set<String> screensToSkip) {
        if (!rootScreenLocation) throw new IllegalArgumentException("No rootScreenLocation specified")
        ScreenDefinition rootScreenDef = sfi.getScreenDefinition(rootScreenLocation)
        ScreenDefinition baseScreenDef = rootScreenDef
        if (baseScreenPath) {
            ArrayList<String> baseScreenList = ScreenUrlInfo.parseSubScreenPath(rootScreenDef, rootScreenDef, [], baseScreenPath, null, sfi)
            for (String screenName in baseScreenList) {
                String subLocation = baseScreenDef.getSubscreensItem(screenName).location
                baseScreenDef = sfi.getScreenDefinition(subLocation)
            }
        }

        List<String> noReqParmLocations = baseScreenDef.nestedNoReqParmLocations("", screensToSkip)
        // logger.info("======= rootScreenLocation=${rootScreenLocation}\nbaseScreenPath=${baseScreenPath}\nbaseScreenDef: ${baseScreenDef.location}\nnoReqParmLocations: ${noReqParmLocations}")
        return noReqParmLocations
    }

    @Override
    ScreenTestRender render(String screenPath, Map<String, Object> parameters, String requestMethod) {
        if (!rootScreenLocation) throw new IllegalArgumentException("No rootScreenLocation specified")
        return new ScreenTestRenderImpl(this, screenPath, parameters, requestMethod).render()
    }

    @CompileStatic
    static class ScreenTestRenderImpl implements ScreenTestRender {
        protected final ScreenTestImpl sti
        String screenPath = null
        Map<String, Object> parameters = null
        String requestMethod = null

        protected ScreenRender screenRender = null
        protected String outputString = null
        protected long renderTime = 0

        protected List<String> errorMessages = []

        ScreenTestRenderImpl(ScreenTestImpl sti, String screenPath, Map<String, Object> parameters, String requestMethod) {
            this.sti = sti
            this.screenPath = screenPath
            this.parameters = parameters
            this.requestMethod = requestMethod
        }

        ScreenTestRender render() {
            ExecutionContextImpl eci = sti.ecfi.getEci()
            long startTime = System.currentTimeMillis()
            // push the context
            ContextStack cs = eci.getContext()
            cs.push()
            // create the WebFacadeStub
            WebFacadeStub wfs = new WebFacadeStub(parameters, sti.sessionAttributes, requestMethod)
            // set stub on eci, will also put parameters in the context
            eci.setWebFacade(wfs)
            // make the ScreenRender
            screenRender = sti.sfi.makeRender()
            // pass through various settings
            if (sti.rootScreenLocation) screenRender.rootScreen(sti.rootScreenLocation)
            if (sti.outputType) screenRender.renderMode(sti.outputType)
            if (sti.characterEncoding) screenRender.encoding(sti.characterEncoding)
            if (sti.macroTemplateLocation) screenRender.macroTemplate(sti.macroTemplateLocation)
            if (sti.baseLinkUrl) screenRender.baseLinkUrl(sti.baseLinkUrl)
            if (sti.servletContextPath) screenRender.servletContextPath(sti.servletContextPath)
            screenRender.webappName(sti.webappName ?: 'webroot')

            // TODO: handle requestMethod, will need ScreenRenderImpl and ScreenUrlInfo changes

            // set the screenPath
            if (screenPath.startsWith("/")) screenPath = screenPath.substring(1)
            if (sti.baseScreenPath) screenPath = sti.baseScreenPath + "/" + screenPath
            screenRender.screenPath(screenPath.split("/") as List)

            // do the render
            try {
                outputString = screenRender.render()
            } catch (Throwable t) {
                String errMsg = "Exception in render of ${screenPath}: ${t.toString()}"
                logger.warn(errMsg, t)
                errorMessages.add(errMsg)
            }
            // calc renderTime
            renderTime = System.currentTimeMillis() - startTime

            // pop the context stack, get rid of var space
            cs.pop()

            if (eci.message.hasError()) {
                errorMessages.addAll(eci.message.getErrors())
                eci.message.clearErrors()
                StringBuilder sb = new StringBuilder("Error messages from ${screenPath}: ")
                for (String errorMessage in errorMessages) sb.append("\n").append(errorMessage)
                logger.warn(sb.toString())
            }

            return this
        }

        @Override
        ScreenRender getScreenRender() { return screenRender }
        @Override
        String getOutput() { return outputString }
        @Override
        long getRenderTime() { return renderTime }
        @Override
        List<String> getErrorMessages() { return errorMessages }

        @Override
        boolean assertContains(String text) {
            if (!outputString) return false
            return outputString.contains(text)
        }
        @Override
        boolean assertNotContains(String text) {
            if (!outputString) return true
            return !outputString.contains(text)
        }
        @Override
        boolean assertRegex(String regex) {
            if (!outputString) return false
            return outputString.matches(regex)
        }
    }
}
