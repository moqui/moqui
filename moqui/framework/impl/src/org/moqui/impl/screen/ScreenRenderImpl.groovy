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
import org.moqui.impl.context.ContextStack
import org.moqui.impl.screen.ScreenDefinition.ResponseItem
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.context.WebFacadeImpl

class ScreenRenderImpl implements ScreenRender {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScreenRenderImpl.class)
    protected final static URLCodec urlCodec = new URLCodec()

    protected final ScreenFacadeImpl sfi

    protected String rootScreenLocation = null
    protected ScreenDefinition rootScreenDef = null

    protected List<String> originalScreenPathNameList = new ArrayList<String>()
    protected ScreenUrlInfo screenUrlInfo = null
    protected int screenPathIndex = -1

    protected String baseLinkUrl = null
    protected String servletContextPath = null
    protected String webappName = null

    protected String renderMode = null
    protected String characterEncoding = null
    /** For HttpServletRequest/Response renders this will be set on the response either as this default or a value
     * determined during render, especially for screen sub-content based on the extension of the filename. */
    protected String outputContentType = null

    protected String macroTemplateLocation = null
    protected Boolean boundaryComments = null

    protected HttpServletRequest request = null
    protected HttpServletResponse response = null
    protected Writer writer = null

    ScreenRenderImpl(ScreenFacadeImpl sfi) {
        this.sfi = sfi
    }

    Writer getWriter() { return this.writer }

    ExecutionContext getEc() { return sfi.ecfi.getExecutionContext() }
    ScreenFacadeImpl getSfi() { return sfi }

    @Override
    ScreenRender rootScreen(String rootScreenLocation) { this.rootScreenLocation = rootScreenLocation; return this }

    @Override
    ScreenRender screenPath(List<String> screenNameList) { this.originalScreenPathNameList.addAll(screenNameList); return this }

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
        if (!webappName) webappName(request.session.servletContext.getInitParameter("moqui-name"))
        if (webappName && !rootScreenLocation) rootScreen(getWebappNode()."@root-screen-location")
        // TODO: should we really use the character encoding of the request, or always go with UTF-8?
        if (!characterEncoding && request.getCharacterEncoding()) encoding(request.getCharacterEncoding())
        if (!originalScreenPathNameList) screenPath(request.getPathInfo().split("/") as List)
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
        if (!renderMode) renderMode = "html"

        rootScreenDef = sfi.getScreenDefinition(rootScreenLocation)
        if (!rootScreenDef) throw new IllegalArgumentException("Could not find screen at location [${rootScreenLocation}]")

        logger.info("Rendering screen [${rootScreenLocation}] with path list [${originalScreenPathNameList}]")

        this.screenUrlInfo = new ScreenUrlInfo(this, rootScreenDef, originalScreenPathNameList, null)
        if (ec.web) {
            // add URL parameters, if there were any in the URL (in path info or after ?)
            this.screenUrlInfo.addParameters(ec.web.requestParameters)
        }

        // check webapp settings for each screen in the path
        if (!checkWebappSettings(rootScreenDef)) return
        for (ScreenDefinition checkSd in screenUrlInfo.screenPathDefList) {
            if (!checkWebappSettings(checkSd)) return
        }

        // if these aren't set in any screen (in the checkWebappSettings method), set them here
        if (!characterEncoding) characterEncoding = "UTF-8"
        if (!outputContentType) outputContentType = "text/html"

        // before we render, set the character encoding (set the content type later, after we see if there is sub-content with a different type)
        if (this.response != null) response.setCharacterEncoding(this.characterEncoding)

        if (screenUrlInfo.targetTransition) {
            // TODO if this transition has actions and request was not secure or any parameters were not in the body return an error, helps prevent XSRF attacks

            // NOTE: always use a transaction for transition run (actions, etc)
            boolean beganTransaction = sfi.ecfi.transactionFacade.begin(null)
            ResponseItem ri = null
            try {
                // if there is a transition run that INSTEAD of the screen to render
                ri = screenUrlInfo.targetTransition.run(this)
            } catch (Throwable t) {
                sfi.ecfi.transactionFacade.rollback(beganTransaction, "Error running transition in [${screenUrlInfo.url}]", t)
                throw t
            } finally {
                if (sfi.ecfi.transactionFacade.isTransactionInPlace()) {
                    sfi.ecfi.transactionFacade.commit(beganTransaction)
                }
            }

            if (ri == null) throw new IllegalArgumentException("No response found for transition [${screenUrlInfo.targetTransition.name}] on screen [${screenUrlInfo.targetScreen.location}]")

            if (ri.saveCurrentScreen && ec.web) {
                StringBuilder screenPath = new StringBuilder()
                for (String pn in screenUrlInfo.fullPathNameList) screenPath.append("/").append(pn)
                ((WebFacadeImpl) ec.web).saveScreenLastInfo(screenPath.toString(), null)
            }

            if (ri.type == "none") return

            String url = ri.url ?: ""
            String urlType = ri.urlType ?: "screen-path"

            // TODO auto-add the parameters of the target screen at run time (before the explicit ones so they can override)
            // TODO add the ri.parameters
            Map<String, String> parameterMap = new HashMap()

            if (ec.web) {
                WebFacadeImpl wfi = (WebFacadeImpl) ec.web
                if (ri.type == "screen-last" || ri.type == "screen-last-noparam") {
                    String savedUrl = wfi.getRemoveScreenLastPath()
                    if (savedUrl) {
                        urlType = "screen-path"
                        url = savedUrl
                    }
                }
                if (ri.type == "screen-last") {
                    wfi.removeScreenLastParameters(true)
                } else if (ri.type == "screen-last-noparam") {
                    wfi.removeScreenLastParameters(false)
                }
            }

            // either send a redirect for the response, if possible, or just render the response now
            if (this.response) {
                if (urlType == "plain") {
                    response.sendRedirect(url)
                } else {
                    // default is screen-path
                    ScreenUrlInfo fullUrl = buildUrl(rootScreenDef, screenUrlInfo.preTransitionPathNameList, url)
                    response.sendRedirect(fullUrl.getUrlWithParams())
                }
            } else {
                List<String> pathElements = url.split("/") as List
                if (url.startsWith("/")) {
                    this.originalScreenPathNameList = pathElements
                } else {
                    this.originalScreenPathNameList = screenUrlInfo.preTransitionPathNameList
                    this.originalScreenPathNameList.addAll(pathElements)
                }
                // reset screenUrlInfo and call this again to start over with the new target
                screenUrlInfo = null
                internalRender()
            }
        } else if (screenUrlInfo.fileResourceRef != null) {
            // use the fileName to determine the content/mime type
            String fileName = screenUrlInfo.fileResourceRef.fileName
            // if it contains .ftl or .cwiki remove those to avoid problems with trying to find content types based on them
            if (fileName.contains(".ftl")) fileName = fileName.replace(".ftl", "")
            if (fileName.contains(".cwiki")) fileName = fileName.replace(".cwiki", "")
            String fileContentType = sfi.ecfi.resourceFacade.getContentType(fileName)

            if (logger.traceEnabled) logger.trace("Content type for screen sub-content filename [${fileName}] is [${fileContentType}], default [${this.outputContentType}], is binary? ${sfi.ecfi.resourceFacade.isBinaryContentType(fileContentType)}")

            // is it binary?
            if (sfi.ecfi.resourceFacade.isBinaryContentType(fileContentType)) {
                if (logger.infoEnabled) logger.info("Streaming binary content from [${screenUrlInfo.fileResourceRef.location}]")
                if (response) {
                    this.outputContentType = fileContentType
                    response.setContentType(this.outputContentType)

                    InputStream is = null
                    OutputStream os = null
                    try {
                        is = screenUrlInfo.fileResourceRef.openStream()
                        os = response.outputStream
                        byte[] buffer = new byte[4096]
                        int len = is.read(buffer)
                        while (len != -1) {
                            os.write(buffer, 0, len)
                            len = is.read(buffer)
                            if (Thread.interrupted()) throw new InterruptedException()
                        }
                        return
                    } finally {
                        if (is != null) is.close()
                    }
                } else {
                    throw new IllegalArgumentException("Tried to get binary content at [${screenUrlInfo.fileResourcePathList}] under screen [${screenUrlInfo.targetScreen.location}], but there is no HTTP response available")
                }
            }

            // not binary, render as text
            if (screenUrlInfo.targetScreen.screenNode."@include-child-content" != "true") {
                if (fileContentType) this.outputContentType = fileContentType
                if (response != null) response.setContentType(this.outputContentType)
                // not a binary object (hopefully), read it and write it to the writer
                sfi.ecfi.resourceFacade.renderTemplateInCurrentContext(screenUrlInfo.fileResourceRef.location, writer)
            } else {
                // render the root screen as normal, and when that is to the targetScreen include the content
                boolean beganTransaction = screenUrlInfo.beginTransaction ? sfi.ecfi.transactionFacade.begin(null) : false
                try {
                    if (response != null) response.setContentType(this.outputContentType)
                    rootScreenDef.getRootSection().render(this)
                } catch (Throwable t) {
                    String errMsg = "Error rendering screen [${getActiveScreenDef().location}]"
                    sfi.ecfi.transactionFacade.rollback(beganTransaction, errMsg, t)
                    throw new RuntimeException(errMsg, t)
                } finally {
                    if (sfi.ecfi.transactionFacade.isTransactionInPlace()) sfi.ecfi.transactionFacade.commit(beganTransaction)
                }
            }
        } else {
            // start rendering at the root section of the root screen
            boolean beganTransaction = screenUrlInfo.beginTransaction ? sfi.ecfi.transactionFacade.begin(null) : false
            try {
                if (response != null) response.setContentType(this.outputContentType)
                rootScreenDef.getRootSection().render(this)
            } catch (Throwable t) {
                String errMsg = "Error rendering screen [${getActiveScreenDef().location}]"
                sfi.ecfi.transactionFacade.rollback(beganTransaction, errMsg, t)
                throw new RuntimeException(errMsg, t)
            } finally {
                if (sfi.ecfi.transactionFacade.isTransactionInPlace()) sfi.ecfi.transactionFacade.commit(beganTransaction)
            }
        }
    }

    boolean checkWebappSettings(ScreenDefinition currentSd) {
        if (!request) return true

        if (currentSd.webSettingsNode?."@allow-web-request" == "false")
            throw new IllegalArgumentException("The screen [${currentSd.location}] cannot be used in a web request (allow-web-request=false).")

        if (currentSd.webSettingsNode?."@mime-type") this.outputContentType = currentSd.webSettingsNode?."@mime-type"
        if (!this.characterEncoding && currentSd.webSettingsNode?."@character-encoding")
            this.characterEncoding = currentSd.webSettingsNode?."@character-encoding"

        // if request not secure and screens requires secure redirect to https
        if (currentSd.webSettingsNode?."@require-encryption" != "false" && getWebappNode()."@https-enabled" != "false" &&
                !request.isSecure()) {
            logger.info("Screen at location [${currentSd.location}], which is part of [${screenUrlInfo.fullPathNameList}] under screen [${screenUrlInfo.fromSd.location}] requires an encrypted/secure connection but the request is not secure, sending redirect to secure.")
            // redirect to the same URL this came to
            response.sendRedirect(screenUrlInfo.getUrlWithParams())
            return false
        }
        // if screen requires auth and there is not active user redirect to login screen, save this request
        if (logger.traceEnabled) logger.trace("Checking screen [${currentSd.location}] for require-authentication, current user is [${ec.user.userId}]")
        if (currentSd.webSettingsNode?."@require-authentication" != "false" && !ec.user.userId) {
            logger.info("Screen at location [${currentSd.location}], which is part of [${screenUrlInfo.fullPathNameList}] under screen [${screenUrlInfo.fromSd.location}] requires authentication but no user is currently logged in.")
            // save the request as a save-last to use after login
            if (ec.web) {
                StringBuilder screenPath = new StringBuilder()
                for (String pn in screenUrlInfo.fullPathNameList) screenPath.append("/").append(pn)
                ((WebFacadeImpl) ec.web).saveScreenLastInfo(screenPath.toString(), null)
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
        if (boundaryComments != null) return boundaryComments
        boundaryComments = sfi.ecfi.confXmlRoot."screen-facade"[0]."@boundary-comments" == "true"
        return boundaryComments
    }

    ScreenDefinition getActiveScreenDef() {
        ScreenDefinition screenDef = rootScreenDef
        if (screenPathIndex >= 0) {
            screenDef = screenUrlInfo.screenPathDefList[screenPathIndex]
        }
        return screenDef
    }

    List<String> getActiveScreenPath() {
        return (screenPathIndex >= 0 ? screenUrlInfo.fullPathNameList[0..screenPathIndex] : [])
    }

    String renderSubscreen() {
        // first see if there is another screen def in the list
        if ((screenPathIndex+1) >= screenUrlInfo.screenPathDefList.size()) {
            if (screenUrlInfo.fileResourceRef) {
                // NOTE: don't set this.outputContentType, when including in a screen the screen determines the type
                sfi.ecfi.resourceFacade.renderTemplateInCurrentContext(screenUrlInfo.fileResourceRef.location, writer)
                return ""
            } else {
                return "Tried to render subscreen in screen [${getActiveScreenDef()?.location}] but there is no subscreens.@default-item, and no more valid subscreen names in the screen path [${screenUrlInfo.fullPathNameList}]"
            }
        }

        screenPathIndex++
        ScreenDefinition screenDef = screenUrlInfo.screenPathDefList[screenPathIndex]
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
            // NOTE: run templates with their own variable space so we can add sri, and avoid getting anything added from within
            ContextStack cs = (ContextStack) ec.context
            cs.push()
            cs.put("sri", this)
            sfi.ecfi.resourceFacade.renderTemplateInCurrentContext(location, writer)
            cs.pop()
            writer.flush()
            // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
            return ""
        } else {
            return sfi.ecfi.resourceFacade.getLocationText(location, true)
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
            case "transition": return new ScreenUrlInfo(this, null, null, url)
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
        if (pathNameList.size() > screenUrlInfo.fullPathNameList.size()) return false
        for (int i = 0; i < pathNameList.size(); i++) {
            if (pathNameList.get(i) != screenUrlInfo.fullPathNameList.get(i)) return false
        }
        return true
    }

    String getCurrentThemeId() {
        // get the screen's theme type; try second level
        String stteId = null
        if (screenUrlInfo.screenPathDefList) stteId = screenUrlInfo.screenPathDefList[0].webSettingsNode?."@screen-theme-type-enum-id"
        // if no setting try first level (root)
        if (!stteId) stteId = rootScreenDef.webSettingsNode?."@screen-theme-type-enum-id"
        // if no setting default to STT_INTERNAL
        if (!stteId) stteId = "STT_INTERNAL"

        // see if there is a user setting for the theme
        String themeId = sfi.ecfi.entityFacade.makeFind("UserScreenTheme")
                .condition([userId:ec.user.userId, screenThemeTypeEnumId:stteId]).useCache(true)
                .one()?.screenThemeId
        // default theme
        if (!themeId) themeId = "DEFAULT"
        return themeId
    }

    List<String> getThemeValues(String resourceTypeEnumId) {
        EntityList strList = sfi.ecfi.entityFacade.makeFind("ScreenThemeResource")
                .condition([screenThemeId:getCurrentThemeId(), resourceTypeEnumId:resourceTypeEnumId])
                .orderBy("sequenceNum").useCache(true).list()
        List<String> values = new LinkedList()
        for (EntityValue str in strList) values.add(str.resourceValue as String)
        return values
    }
}
