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

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.codec.net.URLCodec

import org.moqui.context.ExecutionContext
import org.moqui.context.ScreenRender
import org.moqui.context.WebExecutionContext
import org.moqui.impl.context.ContextStack
import org.moqui.impl.context.WebExecutionContextImpl
import org.moqui.impl.screen.ScreenDefinition.ResponseItem
import org.moqui.impl.screen.ScreenDefinition.TransitionItem

class ScreenRenderImpl implements ScreenRender {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScreenRenderImpl.class)

    protected final ScreenFacadeImpl sfi

    protected String rootScreenLocation = null
    protected ScreenDefinition rootScreenDef = null

    protected List<String> screenPathNameList = new ArrayList<String>()
    protected List<ScreenDefinition> screenPathDefList = new ArrayList<ScreenDefinition>()
    protected int screenPathIndex = -1
    /** If set this represents the transition after the last screen in the path */
    protected TransitionItem transitionItem = null

    protected String baseLinkUrl = null
    protected String servletContextPath = null
    protected String webappName = null

    protected String renderMode = null
    protected String characterEncoding = null
    protected String macroTemplateLocation = null

    protected HttpServletRequest request = null
    protected HttpServletResponse response = null
    protected Writer writer = null

    protected URLCodec urlCodec = new URLCodec()

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
    ScreenRender baseLinkUrl(String blu) { this.baseLinkUrl = blu; return this }

    @Override
    ScreenRender servletContextPath(String scp) { this.servletContextPath = scp; return this }

    @Override
    ScreenRender webappName(String wan) { this.webappName = wan; return this }

    @Override
    void render(HttpServletRequest request, HttpServletResponse response) {
        if (this.writer) throw new IllegalStateException("This screen render has already been used")
        this.writer = response.getWriter()
        this.request = request
        this.response = response
        // we know this is a web request, set defaults if missing
        if (!renderMode) renderMode = "html"
        if (!webappName) webappName(request.getServletContext().getInitParameter("moqui-name"))
        if (webappName && !rootScreenLocation) rootScreen(getWebappNode()."@root-screen-location")
        if (!characterEncoding && request.getCharacterEncoding()) encoding(request.getCharacterEncoding())
        if (!screenPathNameList) screenPath(request.getPathInfo().split("/") as List)
        // now render
        internalRender()
    }

    @Override
    void render(Writer writer) {
        if (this.writer) throw new IllegalStateException("This screen render has already been used")
        this.writer = writer
        internalRender()
    }

    @Override
    String render() {
        if (this.writer) throw new IllegalStateException("This screen render has already been used")
        this.writer = new StringWriter()
        internalRender()
        return this.writer.toString()
    }

    protected void internalRender() {
        // make sure we have at least these defaults
        if (!characterEncoding) characterEncoding = "UTF-8"
        if (!renderMode) renderMode = "html"

        rootScreenDef = sfi.getScreenDefinition(rootScreenLocation)
        if (!rootScreenDef) throw new IllegalArgumentException("Could not find screen at location [${rootScreenLocation}]")

        // clean up the screenPathNameList, remove null/empty entries, etc, etc
        screenPathNameList = ScreenUrlInfo.cleanupPathNameList(screenPathNameList, null)

        logger.info("Rendering screen [${rootScreenLocation}] with path list [${screenPathNameList}]")

        // get screen defs for each screen in path to use for subscreens
        List<String> preTransitionPathNameList = new ArrayList<String>()
        ScreenDefinition lastSd = rootScreenDef
        for (String pathName in screenPathNameList) {
            String nextLoc = lastSd.getSubscreensItem(pathName)?.location
            if (!nextLoc) {
                // handle case where last one may be a transition name, and not a subscreen name
                transitionItem = lastSd.getTransitionItem(pathName)
                if (transitionItem) {
                    break
                } else {
                    throw new IllegalArgumentException("Could not find subscreen or transition [${pathName}] in screen [${lastSd.location}]")
                }
            }
            ScreenDefinition nextSd = sfi.getScreenDefinition(nextLoc)
            if (!nextSd) throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${pathName}] in screen [${lastSd.location}]")
            if (!checkWebappSettings(nextSd, pathName, lastSd)) return
            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // add this to the list of path names to use for transition redirect
            preTransitionPathNameList.add(pathName)
        }

        // beyond the last screenPathName, see if there are any screen.default-item values (keep following until none found)
        while (!transitionItem && lastSd.screenNode."subscreens" && lastSd.screenNode."subscreens"."@default-item"[0]) {
            String subscreenName = lastSd.screenNode."subscreens"."@default-item"[0]
            String nextLoc = lastSd.getSubscreensItem(subscreenName)?.location
            if (!nextLoc) {
                // handle case where last one may be a transition name, and not a subscreen name
                transitionItem = lastSd.getTransitionItem(pathName)
                if (transitionItem) {
                    break
                } else {
                    throw new IllegalArgumentException("Could not find subscreen or transition [${pathName}] in screen [${lastSd.location}]")
                }
            }
            ScreenDefinition nextSd = sfi.getScreenDefinition(nextLoc)
            if (!nextSd) throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${subscreenName}] in screen [${lastSd.location}]")
            if (!checkWebappSettings(nextSd, subscreenName, lastSd)) return
            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // for use in URL writing and such add the subscreenName we found to the main path name list
            screenPathNameList.add(subscreenName)
        }

        if (transitionItem) {
            // TODO if this transition has actions and any parameters were not in the body return an error, helps prevent XSRF attacks

            // if there is a transition run that INSTEAD of the screen to render
            ResponseItem ri = transitionItem.run(this)

            if (ri.saveCurrentScreen && ec instanceof WebExecutionContextImpl) {
                StringBuilder screenPath = new StringBuilder()
                for (String pn in screenPathNameList) screenPath.append("/").append(pn)
                ((WebExecutionContextImpl) ec).saveScreenLastInfo(screenPath.toString(), null)
            }

            if (ri.type == "none") return

            String url = ri.url ?: ""
            String urlType = ri.urlType ?: "screen-path"

            // TODO auto-add the parameters of the target screen at run time (before the explicit ones so they can override)
            // TODO add the ri.parameters
            Map<String, String> parameterMap = new HashMap()

            if (ec instanceof WebExecutionContextImpl) {
                WebExecutionContextImpl weci = (WebExecutionContextImpl) ec
                if (ri.type == "screen-last" || ri.type == "screen-last-noparam") {
                    String savedUrl = weci.getRemoveScreenLastPath()
                    if (savedUrl) {
                        urlType = "screen-path"
                        url = savedUrl
                    }
                }
                if (ri.type == "screen-last") {
                    weci.removeScreenLastParameters(true)
                } else if (ri.type == "screen-last-noparam") {
                    weci.removeScreenLastParameters(false)
                }
            }

            // either send a redirect for the response, if possible, or just render the response now
            if (this.response) {
                if (urlType == "plain") {
                    response.sendRedirect(url)
                } else {
                    // default is screen-path
                    String fullUrl = buildUrl(lastSd, preTransitionPathNameList, url)
                    response.sendRedirect(fullUrl)
                }
            } else {
                List<String> pathElements = url.split("/") as List
                if (url.startsWith("/")) {
                    this.screenPathNameList = pathElements
                } else {
                    this.screenPathNameList = preTransitionPathNameList
                    this.screenPathNameList.addAll(pathElements)
                }
                screenPathDefList = new ArrayList<ScreenDefinition>()
                transitionItem = null
                internalRender()
            }
        } else {
            // start rendering at the root section of the root screen
            try {
                rootScreenDef.getRootSection().render(this)
            } catch (Throwable t) {
                throw new RuntimeException("Error rendering screen [${getActiveScreenDef().location}]", t)
            }
        }
    }

    boolean checkWebappSettings(ScreenDefinition nextSd, String pathName, ScreenDefinition lastSd) {
        if (!request) return true
        // if request not secure and screens requires secure redirect to https
        if (nextSd.webSettingsNode?."@require-encryption" != "false" && getWebappNode()."@https-enabled" != "false" &&
                !request.isSecure()) {
            logger.info("Screen at location [${nextSd.location}], which is subscreen [${pathName}] in screen [${lastSd.location}] requires an encrypted/secure connection but the request is not secure.")
            // redirect to the same URL this came to
            ScreenUrlInfo sui = new ScreenUrlInfo(this, rootScreenDef, screenPathNameList, null)
            // add URL parameters, if there were any in the URL (in path info or after ?)
            sui.addParameters(((WebExecutionContext) ec).requestParameters)
            response.sendRedirect(sui.getFullUrl())
            return false
        }
        // if screen requires auth and there is not active user redirect to login screen, save this request
        // TODO: remove the "false && " below to enable authentication checking again; commented for now until data loading, etc is implemented, otherwise cannot really test
        if (false && nextSd.webSettingsNode?."@require-authentication" != "false" && !ec.user.userId) {
            logger.info("Screen at location [${nextSd.location}], which is subscreen [${pathName}] in screen [${lastSd.location}] requires authentication but no user is currently logged in.")
            // save the request as a save-last to use after login
            if (ec instanceof WebExecutionContextImpl) {
                StringBuilder screenPath = new StringBuilder()
                for (String pn in screenPathNameList) screenPath.append("/").append(pn)
                ((WebExecutionContextImpl) ec).saveScreenLastInfo(screenPath.toString(), null)
            }
            // now prepare and send the redirect
            ScreenUrlInfo sui = new ScreenUrlInfo(this, rootScreenDef, null, "Login")
            response.sendRedirect(sui.url)
            return false
        }

        return true
    }

    Node getWebappNode() {
        if (!webappName) return null
        return (Node) sfi.ecfi.confXmlRoot["webapp-list"][0]["webapp"].find({ it.@name == webappName })
    }

    boolean doBoundaryComments() {
        return sfi.ecfi.confXmlRoot."screen-facade"[0]."@boundary-comments" == "true"
    }

    ScreenDefinition getActiveScreenDef() {
        ScreenDefinition screenDef = rootScreenDef
        if (screenPathIndex >= 0) {
            screenDef = screenPathDefList[screenPathIndex]
        }
        return screenDef
    }

    String renderSubscreen() {
        // first see if there is another screen def in the list
        if ((screenPathIndex+1) >= screenPathDefList.size())
            return "Tried to render subscreen in screen [${getActiveScreenDef()?.location}] but there is no subscreens.@default-item, and no more subscreen names in the screen path"

        screenPathIndex++
        ScreenDefinition screenDef = screenPathDefList[screenPathIndex]
        try {
            writer.flush()
            screenDef.getRootSection().render(this)
            writer.flush()
        } catch (Throwable t) {
            logger.error("Error rendering screen [${screenDef.location}]", t)
            return "Error rendering screen [${screenDef.location}]: ${t.toString()}"
        } finally {
            screenPathIndex--
        }
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
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
        writer.flush()
        section.render(this)
        writer.flush()
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }

    String renderFormSingle(String formName) {
        ScreenDefinition sd = getActiveScreenDef()
        ScreenForm form = sd.getForm(formName)
        if (!form) throw new IllegalArgumentException("No form with name [${formName}] in screen [${sd.location}]")
        writer.flush()
        form.renderSingle(this)
        writer.flush()
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }

    String renderFormList(String formName) {
        ScreenDefinition sd = getActiveScreenDef()
        ScreenForm form = sd.getForm(formName)
        if (!form) throw new IllegalArgumentException("No form with name [${formName}] in screen [${sd.location}]")
        writer.flush()
        form.renderListRow(this)
        writer.flush()
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }

    String renderIncludeScreen(String location, String shareScopeStr) {
        boolean shareScope = false
        if (shareScopeStr == "true") shareScope = true

        ContextStack cs = (ContextStack) ec.context
        try {
            if (!shareScope) cs.push()
            writer.flush()
            sfi.makeRender().rootScreen(location).renderMode(renderMode).encoding(characterEncoding)
                    .macroTemplate(macroTemplateLocation).render(writer)
            writer.flush()
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
            writer.flush()
            sfi.ecfi.resourceFacade.renderTemplateInCurrentContext(location, writer)
            writer.flush()
            // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
            return ""
        } else {
            return sfi.ecfi.resourceFacade.getLocationText(location)
        }
    }

    ScreenUrlInfo buildUrl(String subscreenPath) {
        ScreenUrlInfo ui = new ScreenUrlInfo(this, null, null, subscreenPath)
        return ui
    }

    ScreenUrlInfo buildUrl(ScreenDefinition fromSd, List<String> fromPathList, String subscreenPath) {
        ScreenUrlInfo ui = new ScreenUrlInfo(this, fromSd, fromPathList, subscreenPath)
        return ui
    }

    ScreenUrlInfo makeUrlByType(String url, String urlType) {
        /* TODO handle urlType=content
            A content location (without the content://). URL will be one that can access that content.
         */
        switch (urlType) {
            // for transition we want a URL relative to the current screen, so just pass that to buildUrl
            case "transition": return buildUrl(url)
            case "content": throw new IllegalArgumentException("The url-type of content is not yet supported")
            case "plain":
            default: return new ScreenUrlInfo(this, url)
        }
    }

    String makeValue(String fromField, String value) {
        if (value) {
            return ec.resource.evaluateStringExpand(value, getActiveScreenDef().location)
        } else if (fromField) {
            return ec.resource.evaluateContextField(fromField, getActiveScreenDef().location) as String
        } else {
            return ""
        }
    }

    boolean isInCurrentScreenPath(List<String> pathNameList) {
        if (pathNameList.size() > this.screenPathNameList.size()) return false
        for (int i=0; i<pathNameList.size(); i++) {
            if (pathNameList.get(i) != this.screenPathNameList.get(i)) return false
        }
        return true
    }
}
