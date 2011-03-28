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
import org.moqui.impl.StupidWebUtilities
import org.moqui.impl.FtlNodeWrapper
import org.moqui.entity.EntityListIterator
import org.apache.commons.collections.map.ListOrderedMap
import org.moqui.context.TemplateRenderer
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.screen.ScreenDefinition.SubscreensItem
import org.moqui.impl.screen.ScreenDefinition.ParameterItem

class ScreenRenderImpl implements ScreenRender {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScreenRenderImpl.class)
    protected final static URLCodec urlCodec = new URLCodec()

    protected final ScreenFacadeImpl sfi

    protected String rootScreenLocation = null
    protected ScreenDefinition rootScreenDef = null

    protected List<String> originalScreenPathNameList = new ArrayList<String>()
    protected ScreenUrlInfo screenUrlInfo = null
    protected Map<String, ScreenUrlInfo> subscreenUrlInfos = new HashMap()
    protected int screenPathIndex = 0

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
    protected Writer afterFormWriter = null

    ScreenRenderImpl(ScreenFacadeImpl sfi) {
        this.sfi = sfi
    }

    Writer getWriter() { return this.writer }

    ExecutionContext getEc() { return sfi.ecfi.getExecutionContext() }
    ScreenFacadeImpl getSfi() { return sfi }
    ScreenUrlInfo getScreenUrlInfo() { return screenUrlInfo }

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
        Map<String, Object> requestParameters = sfi.ecfi.executionContext.web.requestParameters
        if (!renderMode) {
            if (requestParameters.containsKey("renderMode")) {
                renderMode = requestParameters.get("renderMode")
                String mimeType = sfi.getMimeTypeByMode(renderMode)
                if (mimeType) outputContentType = mimeType
            } else {
                renderMode = "html"
            }
        }
        if (!webappName) webappName(request.session.servletContext.getInitParameter("moqui-name"))
        if (webappName && !rootScreenLocation) rootScreen(getWebappNode()."@root-screen-location")
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

        if (logger.traceEnabled) logger.trace("Rendering screen [${rootScreenLocation}] with path list [${originalScreenPathNameList}]")

        this.screenUrlInfo = new ScreenUrlInfo(this, rootScreenDef, originalScreenPathNameList, null)
        if (ec.web) {
            // clear out the parameters used for special screen URL config
            if (ec.web.requestParameters.lastStandalone) ec.web.requestParameters.lastStandalone = ""

            // add URL parameters, if there were any in the URL (in path info or after ?)
            this.screenUrlInfo.addParameters(ec.web.requestParameters)
        }

        // check webapp settings for each screen in the path
        for (ScreenDefinition checkSd in screenUrlInfo.screenRenderDefList) {
            if (!checkWebappSettings(checkSd)) return
        }

        // if these aren't set in any screen (in the checkWebappSettings method), set them here
        if (!characterEncoding) characterEncoding = "UTF-8"
        if (!outputContentType) outputContentType = "text/html"

        // before we render, set the character encoding (set the content type later, after we see if there is sub-content with a different type)
        if (this.response != null) response.setCharacterEncoding(this.characterEncoding)

        // if there is a transition run that INSTEAD of the screen to render
        if (screenUrlInfo.targetTransition) {
            // if this transition has actions and request was not secure or any parameters were not in the body
            // return an error, helps prevent XSRF attacks
            if (request != null && screenUrlInfo.targetTransition.actions != null) {
                if ((!request.isSecure() && getWebappNode()."@https-enabled" != "false") ||
                        request.getQueryString() ||
                        StupidWebUtilities.getPathInfoParameterMap(request.getPathInfo())) {
                    throw new IllegalArgumentException(
                        """Cannot run screen transition with actions from non-secure request or with URL
                        parameters for security reasons (they are not encrypted and need to be for data
                        protection and source validation). Change the link this came from to be a
                        form with hidden input fields instead.""")
                }
            }

            long transitionStartTime = System.currentTimeMillis()
            // NOTE: always use a transaction for transition run (actions, etc)
            boolean beganTransaction = sfi.ecfi.transactionFacade.begin(null)
            ResponseItem ri = null
            try {
                // for inherited permissions to work, walk the screen list and artifact push them, then pop after
                int screensPushed = 0
                for (ScreenDefinition permSd in screenUrlInfo.screenPathDefList) {
                    screensPushed++
                    // for these authz is not required, as long as something authorizes on the way to the transition, or
                    // the transition itself, it's fine
                    ec.artifactExecution.push(
                            new ArtifactExecutionInfoImpl(permSd.location, "AT_XML_SCREEN", "AUTHZA_VIEW"), false)
                }

                ri = screenUrlInfo.targetTransition.run(this)

                for (int i = screensPushed; i > 0; i--) ec.artifactExecution.pop()
            } catch (Throwable t) {
                sfi.ecfi.transactionFacade.rollback(beganTransaction, "Error running transition in [${screenUrlInfo.url}]", t)
                throw t
            } finally {
                if (sfi.ecfi.transactionFacade.isTransactionInPlace()) {
                    sfi.ecfi.transactionFacade.commit(beganTransaction)
                }

                if (screenUrlInfo.targetScreen.screenNode."@track-artifact-hit" != "false") {
                    sfi.ecfi.countArtifactHit("transition", ri?.type ?: "", screenUrlInfo.url,
                            (ec.web ? ec.web.requestParameters : null), transitionStartTime,
                            System.currentTimeMillis(), null)
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

            // handle screen-last, etc
            if (ec.web) {
                WebFacadeImpl wfi = (WebFacadeImpl) ec.web
                if (ri.type == "screen-last" || ri.type == "screen-last-noparam") {
                    String savedUrl = wfi.getRemoveScreenLastPath()
                    urlType = "screen-path"
                    url = savedUrl ?: "/"
                    // if no saved URL, just go to root/default; avoid getting stuck on Login screen, etc
                }
                if (ri.type == "screen-last") {
                    wfi.removeScreenLastParameters(true)
                } else if (ri.type == "screen-last-noparam") {
                    wfi.removeScreenLastParameters(false)
                }
            }

            // either send a redirect for the response, if possible, or just render the response now
            if (this.response != null) {
                // save messages in session before redirecting so they can be displayed on the next screen
                if (ec.web) ((WebFacadeImpl) ec.web).saveMessagesToSession()

                if (urlType == "plain") {
                    response.sendRedirect(url)
                } else {
                    // default is screen-path
                    ScreenUrlInfo fullUrl = buildUrl(rootScreenDef, screenUrlInfo.preTransitionPathNameList, url)
                    for (ParameterItem pi in ri.parameterMap.values()) fullUrl.addParameter(pi.name, pi.getValue(ec))
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
            long resourceStartTime = System.currentTimeMillis()
            // use the fileName to determine the content/mime type
            String fileName = screenUrlInfo.fileResourceRef.fileName
            // if it contains .ftl or .cwiki remove those to avoid problems with trying to find content types based on them
            if (fileName.contains(".ftl")) fileName = fileName.replace(".ftl", "")
            if (fileName.contains(".cwiki")) fileName = fileName.replace(".cwiki", "")
            String fileContentType = sfi.ecfi.resourceFacade.getContentType(fileName)

            boolean isBinary = sfi.ecfi.resourceFacade.isBinaryContentType(fileContentType)
            // if (logger.traceEnabled) logger.trace("Content type for screen sub-content filename [${fileName}] is [${fileContentType}], default [${this.outputContentType}], is binary? ${isBinary}")

            if (isBinary) {
                if (response) {
                    this.outputContentType = fileContentType
                    response.setContentType(this.outputContentType)
                    // static binary, tell the browser to cache it
                    // NOTE: make this configurable?
                    response.addHeader("Cache-Control", "max-age=3600, must-revalidate, public")

                    InputStream is
                    try {
                        is = screenUrlInfo.fileResourceRef.openStream()
                        OutputStream os = response.outputStream
                        byte[] buffer = new byte[4096]
                        int totalLen = 0
                        int len = is.read(buffer)
                        while (len != -1) {
                            totalLen += len
                            os.write(buffer, 0, len)
                            len = is.read(buffer)
                            if (Thread.interrupted()) throw new InterruptedException()
                        }
                        if (screenUrlInfo.targetScreen.screenNode."@track-artifact-hit" != "false") {
                            sfi.ecfi.countArtifactHit("screen-content", fileContentType, screenUrlInfo.url,
                                    (ec.web ? ec.web.requestParameters : null), resourceStartTime,
                                    System.currentTimeMillis(), totalLen)
                        }
                        if (logger.traceEnabled) logger.trace("Sent binary response of length [${totalLen}] with from file [${screenUrlInfo.fileResourceRef.location}] for request to [${screenUrlInfo.url}]")
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
                if (response != null) {
                    response.setContentType(this.outputContentType)
                    response.setCharacterEncoding(this.characterEncoding)
                }
                // not a binary object (hopefully), read it and write it to the writer
                TemplateRenderer tr = sfi.ecfi.resourceFacade.getTemplateRendererByLocation(screenUrlInfo.fileResourceRef.location)
                if (tr != null) {
                    // if requires a render, don't cache and make it private
                    if (response != null) response.addHeader("Cache-Control", "no-cache, must-revalidate, private")
                    tr.render(screenUrlInfo.fileResourceRef.location, writer)
                } else {
                    // static text, tell the browser to cache it
                    // NOTE: make this configurable?
                    if (response != null) response.addHeader("Cache-Control", "max-age=3600, must-revalidate, public")
                    // no renderer found, just grab the text (cached) and throw it to the writer
                    String text = sfi.ecfi.resourceFacade.getLocationText(screenUrlInfo.fileResourceRef.location, true)
                    if (text) {
                        // NOTE: String.length not correct for byte length
                        String charset = response?.getCharacterEncoding() ?: "UTF-8"
                        int length = text.getBytes(charset).length
                        if (response != null) response.setContentLength(length)

                        if (logger.traceEnabled) logger.trace("Sending text response of length [${length}] with [${charset}] encoding from file [${screenUrlInfo.fileResourceRef.location}] for request to [${screenUrlInfo.url}]")

                        writer.write(text)

                        if (screenUrlInfo.targetScreen.screenNode."@track-artifact-hit" != "false") {
                            sfi.ecfi.countArtifactHit("screen-content", fileContentType, screenUrlInfo.url,
                                    (ec.web ? ec.web.requestParameters : null), resourceStartTime,
                                    System.currentTimeMillis(), length)
                        }
                    } else {
                        logger.warn("Not sending text response from file [${screenUrlInfo.fileResourceRef.location}] for request to [${screenUrlInfo.url}] because no text was found in the file.")
                    }
                }
            } else {
                // render the root screen as normal, and when that is to the targetScreen include the content
                doActualRender()
            }
        } else {
            doActualRender()
        }
    }

    void doActualRender() {
        long screenStartTime = System.currentTimeMillis()
        boolean beganTransaction = screenUrlInfo.beginTransaction ? sfi.ecfi.transactionFacade.begin(null) : false
        try {
            // before we kick-off rendering run all pre-actions
            for (ScreenDefinition sd in screenUrlInfo.screenRenderDefList) {
                if (sd.preActions != null) sd.preActions.run(ec)
            }
            if (response != null) {
                response.setContentType(this.outputContentType)
                response.setCharacterEncoding(this.characterEncoding)
                // if requires a render, don't cache and make it private
                response.addHeader("Cache-Control", "no-cache, must-revalidate, private")
            }

            // for inherited permissions to work, walk the screen list before the screenRenderDefList and artifact push
            // them, then pop after
            int screensPushed = 0
            if (screenUrlInfo.renderPathDifference > 0) {
                for (int i = 0; i < screenUrlInfo.renderPathDifference; i++) {
                    ScreenDefinition permSd = screenUrlInfo.screenPathDefList.get(i)
                    ec.artifactExecution.push(new ArtifactExecutionInfoImpl(permSd.location, "AT_XML_SCREEN", "AUTHZA_VIEW"), false)
                    screensPushed++
                }
            }

            // start rendering at the root section of the root screen
            ScreenDefinition renderStartDef = screenUrlInfo.screenRenderDefList[0]
            // if screenRenderDefList.size == 1 then it is the target screen, otherwise it's not
            renderStartDef.render(this, screenUrlInfo.screenRenderDefList.size() == 1)

            for (int i = screensPushed; i > 0; i--) ec.artifactExecution.pop()
        } catch (Throwable t) {
            String errMsg = "Error rendering screen [${getActiveScreenDef().location}]"
            sfi.ecfi.transactionFacade.rollback(beganTransaction, errMsg, t)
            throw new RuntimeException(errMsg, t)
        } finally {
            if (sfi.ecfi.transactionFacade.isTransactionInPlace()) sfi.ecfi.transactionFacade.commit(beganTransaction)
            if (screenUrlInfo.targetScreen.screenNode."@track-artifact-hit" != "false") {
                sfi.ecfi.countArtifactHit("screen", this.outputContentType, screenUrlInfo.url,
                        (ec.web ? ec.web.requestParameters : null), screenStartTime, System.currentTimeMillis(), null)
            }
        }
    }

    boolean checkWebappSettings(ScreenDefinition currentSd) {
        if (!request) return true

        if (currentSd.webSettingsNode?."@allow-web-request" == "false")
            throw new IllegalArgumentException("The screen [${currentSd.location}] cannot be used in a web request (allow-web-request=false).")

        if (currentSd.webSettingsNode?."@mime-type") this.outputContentType = currentSd.webSettingsNode?."@mime-type"
        if (currentSd.webSettingsNode?."@character-encoding") this.characterEncoding = currentSd.webSettingsNode?."@character-encoding"

        // if request not secure and screens requires secure redirect to https
        if (currentSd.webSettingsNode?."@require-encryption" != "false" && getWebappNode()."@https-enabled" != "false" &&
                !request.isSecure()) {
            logger.info("Screen at location [${currentSd.location}], which is part of [${screenUrlInfo.fullPathNameList}] under screen [${screenUrlInfo.fromSd.location}] requires an encrypted/secure connection but the request is not secure, sending redirect to secure.")
            if (ec.web) {
                // save messages in session before redirecting so they can be displayed on the next screen
                ((WebFacadeImpl) ec.web).saveMessagesToSession()
            }
            // redirect to the same URL this came to
            response.sendRedirect(screenUrlInfo.getUrlWithParams())
            return false
        }

        // if screen requires auth and there is not active user redirect to login screen, save this request
        if (logger.traceEnabled) logger.trace("Checking screen [${currentSd.location}] for require-authentication, current user is [${ec.user.userId}]")
        if (currentSd.screenNode?."@require-authentication" != "false" && !ec.user.userId) {
            logger.info("Screen at location [${currentSd.location}], which is part of [${screenUrlInfo.fullPathNameList}] under screen [${screenUrlInfo.fromSd.location}] requires authentication but no user is currently logged in.")
            // save the request as a save-last to use after login
            if (ec.web) {
                StringBuilder screenPath = new StringBuilder()
                for (String pn in screenUrlInfo.fullPathNameList) screenPath.append("/").append(pn)
                ((WebFacadeImpl) ec.web).saveScreenLastInfo(screenPath.toString(), null)
                // save messages in session before redirecting so they can be displayed on the next screen
                ((WebFacadeImpl) ec.web).saveMessagesToSession()
            }
            // now prepare and send the redirect
            ScreenUrlInfo sui = new ScreenUrlInfo(this, rootScreenDef, [], "Login")
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

    ScreenDefinition getActiveScreenDef() { return screenUrlInfo.screenRenderDefList[screenPathIndex] }

    List<String> getActiveScreenPath() {
        // handle case where root screen is first/zero in list versus a standalone screen
        int fullPathIndex = screenUrlInfo.renderPathDifference + screenPathIndex
        return screenUrlInfo.fullPathNameList[0..fullPathIndex-1]
    }

    String renderSubscreen() {
        // first see if there is another screen def in the list
        if ((screenPathIndex+1) >= screenUrlInfo.screenRenderDefList.size()) {
            if (screenUrlInfo.fileResourceRef) {
                // NOTE: don't set this.outputContentType, when including in a screen the screen determines the type
                sfi.ecfi.resourceFacade.renderTemplateInCurrentContext(screenUrlInfo.fileResourceRef.location, writer)
                return ""
            } else {
                return "Tried to render subscreen in screen [${getActiveScreenDef()?.location}] but there is no subscreens.@default-item, and no more valid subscreen names in the screen path [${screenUrlInfo.fullPathNameList}]"
            }
        }

        screenPathIndex++
        ScreenDefinition screenDef = screenUrlInfo.screenRenderDefList[screenPathIndex]
        try {
            writer.flush()
            screenDef.render(this, (screenUrlInfo.screenRenderDefList.size() - 1) == screenPathIndex)
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

    String startFormListRow(String formName, Object listEntry) {
        ScreenDefinition sd = getActiveScreenDef()
        ScreenForm form = sd.getForm(formName)
        if (!form) throw new IllegalArgumentException("No form with name [${formName}] in screen [${sd.location}]")
        ((ContextStack) ec.context).push()
        form.runFormListRowActions(this, listEntry)
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but nothing it written
        return ""
    }
    String endFormListRow() {
        ((ContextStack) ec.context).pop()
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but nothing it written
        return ""
    }
    String safeCloseList(Object listObject) {
        if (listObject instanceof EntityListIterator) ((EntityListIterator) listObject).close()
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but nothing it written
        return ""
    }
    FtlNodeWrapper getFtlFormNode(String formName) {
        ScreenDefinition sd = getActiveScreenDef()
        ScreenForm form = sd.getForm(formName)
        if (!form) throw new IllegalArgumentException("No form with name [${formName}] in screen [${sd.location}]")
        return form.ftlFormNode
    }

    boolean isFormUpload(String formName) { return getActiveScreenDef().getForm(formName).isUpload() }
    boolean isFormHeaderForm(String formName) { return getActiveScreenDef().getForm(formName).isFormHeaderForm() }

    String getFormFieldValidationClasses(String formName, String fieldName) {
        ScreenForm form = getActiveScreenDef().getForm(formName)
        Node parameterNode = form.getFieldInParameterNode(fieldName)
        if (parameterNode == null) return ""

        Set<String> vcs = new HashSet()
        if (parameterNode."@required" == "true") vcs.add("required")
        if (parameterNode."number-integer") vcs.add("number")
        if (parameterNode."number-decimal") vcs.add("number")
        if (parameterNode."text-email") vcs.add("email")
        if (parameterNode."text-url") vcs.add("url")
        if (parameterNode."text-digits") vcs.add("digits")
        if (parameterNode."credit-card") vcs.add("creditcard")

        StringBuilder sb = new StringBuilder()
        for (String vc in vcs) { if (sb) sb.append(" "); sb.append(vc); }
        return sb.toString()
    }

    String renderIncludeScreen(String location, String shareScopeStr) {
        boolean shareScope = shareScopeStr == "true"

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
        boolean isTemplate = (isTemplateStr != "false")

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

    String appendToAfterFormWriter(String text) {
        if (afterFormWriter == null) afterFormWriter = new StringWriter()
        afterFormWriter.append(text)
        // NOTE: this returns a String so that it can be used in an FTL interpolation, but it always writes to the writer
        return ""
    }
    String getAfterFormWriterText() { return afterFormWriter == null ? "" : afterFormWriter.toString() }

    ScreenUrlInfo buildUrl(String subscreenPath) {
        if (subscreenUrlInfos.containsKey(subscreenPath)) return subscreenUrlInfos.get(subscreenPath)
        ScreenUrlInfo sui = new ScreenUrlInfo(this, null, null, subscreenPath)
        subscreenUrlInfos.put(subscreenPath, sui)
        return sui
    }

    ScreenUrlInfo buildUrl(ScreenDefinition fromSd, List<String> fromPathList, String subscreenPath) {
        ScreenUrlInfo ui = new ScreenUrlInfo(this, fromSd, fromPathList, subscreenPath)
        return ui
    }

    ScreenUrlInfo makeUrlByType(String url, String urlType, FtlNodeWrapper parameterParentNodeWrapper) {
        /* TODO handle urlType=content
            A content location (without the content://). URL will be one that can access that content.
         */
        ScreenUrlInfo sui = null
        switch (urlType) {
            // for transition we want a URL relative to the current screen, so just pass that to buildUrl
            case "transition": sui = new ScreenUrlInfo(this, null, null, url); break;
            case "content": throw new IllegalArgumentException("The url-type of content is not yet supported"); break;
            case "plain":
            default: sui = new ScreenUrlInfo(this, url); break;
        }

        if (sui != null && parameterParentNodeWrapper != null) {
            Node parameterParentNode = parameterParentNodeWrapper.groovyNode
            if (parameterParentNode."@parameter-map") {
                def ctxParameterMap = ec.resource.evaluateContextField((String) parameterParentNode."@parameter-map", "")
                if (ctxParameterMap) sui.addParameters((Map) ctxParameterMap)
            }
            for (Node parameterNode in parameterParentNode."parameter")
                sui.addParameter(parameterNode."@name", makeValue(parameterNode."@from" ?: parameterNode."@name", parameterNode."@value"))
        }

        return sui
    }

    String makeValue(String from, String value) {
        if (value) {
            return ec.resource.evaluateStringExpand(value, getActiveScreenDef().location)
        } else if (from) {
            return ec.resource.evaluateContextField(from, getActiveScreenDef().location) as String
        } else {
            return ""
        }
    }

    String getFieldValue(FtlNodeWrapper fieldNodeWrapper, String defaultValue) {
        Node fieldNode = fieldNodeWrapper.getGroovyNode()
        if (fieldNode."@entry-name") return ec.resource.evaluateContextField(fieldNode."@entry-name", null)
        String fieldName = fieldNode."@name"
        Object value = ec.context.get(fieldName)
        if (!value && ec.context.fieldValues && fieldNode.parent().name() == "form-single") value = ec.context.fieldValues.get(fieldName)
        if (!value && ec.web) value = ec.web.parameters.get(fieldName)

        if (value) return value as String
        return ec.resource.evaluateStringExpand(defaultValue, null)
    }

    String getFieldEntityValue(FtlNodeWrapper widgetNodeWrapper) {
        FtlNodeWrapper fieldNodeWrapper = widgetNodeWrapper.parentNode.parentNode
        Object fieldValue = getFieldValue(fieldNodeWrapper, "")
        if (!fieldValue) return ""
        Node widgetNode = widgetNodeWrapper.getGroovyNode()
        // find the entity value
        EntityValue ev = ec.entity.makeFind(widgetNode."@entity-name")
                .condition(widgetNode."@key-field-name"?:fieldNodeWrapper.groovyNode."@name", fieldValue)
                .useCache(widgetNode."@use-cache"?:"true" == "true").one()
        if (ev == null) return ""
        // push onto the context and then expand the text
        ec.context.push(ev)
        String value = ec.resource.evaluateStringExpand(widgetNode."@text"?:"\${description}", null)
        ec.context.pop()
        return value
    }

    ListOrderedMap getFieldOptions(FtlNodeWrapper widgetNodeWrapper) {
        return ScreenForm.getFieldOptions(widgetNodeWrapper.getGroovyNode(), ec)
    }

    boolean isInCurrentScreenPath(List<String> pathNameList) {
        if (pathNameList.size() > screenUrlInfo.fullPathNameList.size()) return false
        for (int i = 0; i < pathNameList.size(); i++) {
            if (pathNameList.get(i) != screenUrlInfo.fullPathNameList.get(i)) return false
        }
        return true
    }
    boolean isActiveInCurrentMenu() {
        for (SubscreensItem ssi in getActiveScreenDef().subscreensByName.values()) {
            if (!ssi.menuInclude) continue
            logger.info("Checking isActiveInCurrentMenu for ssi [${ssi.name}] under active screen [${getActiveScreenDef().location}]")
            ScreenUrlInfo urlInfo = buildUrl(ssi.name)
            if (urlInfo.inCurrentScreenPath) return true
        }
        return false
    }

    ScreenUrlInfo getCurrentScreenUrl() { return screenUrlInfo }

    String getCurrentThemeId() {
        String stteId = null
        if (screenUrlInfo.screenRenderDefList) {
            // start with the second screen to render (makes it easier to have themes there to allow for different branches with different themes)
            if (screenUrlInfo.screenRenderDefList.size() > 1)
                stteId = screenUrlInfo.screenRenderDefList[1].screenNode?."@screen-theme-type-enum-id"
            // if nothing there, try the first screen to render, the root screen
            if (!stteId) stteId = screenUrlInfo.screenRenderDefList[0].screenNode?."@screen-theme-type-enum-id"
        }
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
