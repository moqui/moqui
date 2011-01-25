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

import org.moqui.context.ExecutionContext
import freemarker.template.Template
import org.moqui.impl.context.ContextStack
import org.moqui.impl.screen.ScreenDefinition.TransitionItem
import javax.servlet.http.HttpServletResponse
import org.moqui.context.WebExecutionContext
import org.moqui.impl.screen.ScreenDefinition.ResponseItem
import org.moqui.impl.screen.ScreenDefinition.ParameterItem

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

    protected String renderMode = "html"
    protected String characterEncoding = "UTF-8"
    protected String macroTemplateLocation = null

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
    void render(HttpServletResponse response) {
        if (this.writer) throw new IllegalStateException("This screen render has already been used")
        this.writer = response.getWriter()
        this.response = response
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
        rootScreenDef = sfi.getScreenDefinition(rootScreenLocation)
        if (!rootScreenDef) throw new IllegalArgumentException("Could not find screen at location [${rootScreenLocation}]")

        // clean up the screenPathNameList, remove null/empty entries, etc, etc
        screenPathNameList = cleanupPathNameList(screenPathNameList, null)

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
            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // add this to the list of path names to use for transition redirect
            preTransitionPathNameList.add(pathName)
        }

        // beyond the last screenPathName, see if there are any screen.default-item values (keep following until none found)
        while (!transitionItem && lastSd.screenNode."subscreens" && lastSd.screenNode."subscreens"."@default-item"[0]) {
            String subscreenName = lastSd.screenNode."subscreens"."@default-item"[0]
            String nextLoc = lastSd.getSubscreensItem(subscreenName)?.location
            if (!nextLoc) throw new IllegalArgumentException("Could not find subscreen [${subscreenName}] in screen [${lastSd.location}]")
            ScreenDefinition nextSd = sfi.getScreenDefinition(nextLoc)
            if (!nextSd) throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${subscreenName}] in screen [${lastSd.location}]")
            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // for use in URL writing and such add the subscreenName we found to the main path name list
            screenPathNameList.add(subscreenName)
        }

        if (transitionItem) {
            // TODO if this transition has actions and any parameters were not in the body return an error, helps prevent XSRF attacks

            // if there is a transition run that INSTEAD of the screen to render
            ResponseItem ri = transitionItem.run(this)

            if (ri.type == "none") return

            String url = ri.url ?: ""
            String urlType = ri.urlType ?: "screen-path"

            // TODO auto-add the parameters of the target screen at run time (before the explicit ones so they can override)
            // TODO add the ri.parameters
            Map<String, String> parameterMap = new HashMap()

            if (ri.type == "screen-last") {
                /* TODO
                Will use the screen from the last request unless there is a saved from some previous
                request (using the save-last-screen attribute).
                If no last screen is found the value in the url will be used.
                 */
                throw new IllegalArgumentException("The response type screen-last is not yet supported")
            } else if (ri.type == "screen-last-noparam") {
                throw new IllegalArgumentException("The response type screen-last-noparam is not yet supported")
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
                // TODO if request not secure and screens requires secure redirect to https
                // TODO if screen requires auth and there is not active user redirect to login screen, save this request
                rootScreenDef.getRootSection().render(this)
            } catch (Throwable t) {
                throw new RuntimeException("Error rendering screen [${getActiveScreenDef().location}]", t)
            }
        }
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
            // TODO if request not secure and screens requires secure redirect to https
            // TODO if screen requires auth and there is not active user redirect to login screen, save this request
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
        /* TODO handle urlType:
            <xs:enumeration value="content">
                <xs:annotation><xs:documentation>A content location (without the content://). URL will be one that can access that content.</xs:documentation></xs:annotation>
            </xs:enumeration>
         */
        switch (urlType) {
            // for transition we want a URL relative to the current screen, so just pass that to buildUrl
            case "transition": return buildUrl(url)
            case "content": throw new IllegalArgumentException("The url-type of content is not yet supported")
            case "plain":
            default: return url
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

    static class ScreenUrlInfo {
        ScreenRenderImpl sri

        ScreenDefinition fromSd
        List<String> fromPathList
        String subscreenPath

        String url
        Map<String, String> pathParameterMap = new HashMap()
        boolean requireEncryption
        boolean hasActions
        boolean inCurrentScreenPath

        List<String> fullPathNameList
        ScreenDefinition targetScreen
        TransitionItem targetTransition

        ScreenUrlInfo(ScreenRenderImpl sri, ScreenDefinition fs, List<String> fpnl, String ssp) {
            this.sri = sri

            fromSd = fs
            if (fromSd == null) fromSd = sri.getActiveScreenDef()

            fromPathList = fpnl
            if (fromPathList == null)
                fromPathList = (sri.screenPathIndex >= 0 ? sri.screenPathNameList[0..sri.screenPathIndex] : [])

            subscreenPath = ssp

            initUrl()

            hasActions = (targetTransition && targetTransition.actions)

            inCurrentScreenPath = sri.isInCurrentScreenPath(fullPathNameList)
        }

        Map<String, String> getParameterMap() {
            Map<String, String> pm = new HashMap()
            // get default parameters for the target screen
            if (targetScreen) {
                for (ParameterItem pi in targetScreen.getParameterMap().values()) {
                    Object value = pi.getValue(sri.ec.context)
                    if (value) pm.put(pi.name, value as String)
                }
            }
            // add all of the parameters specified inline in the screen path
            if (pathParameterMap) pm.putAll(pathParameterMap)
            return pm
        }

        String getParameterString() {
            StringBuilder ps = new StringBuilder()
            Map<String, String> pm = this.getParameterMap()
            boolean isFirst = true
            for (Map.Entry<String, String> pme in pm) {
                if (isFirst) isFirst = false else ps.append("&")
                // TODO: should we do any URL encoding here?
                ps.append(pme.key).append("=").append(pme.value)
            }
            return ps.toString()
        }

        void initUrl() {
            if (this.subscreenPath.startsWith("/")) {
                this.fromSd = sri.rootScreenDef
                this.fromPathList = []
            }

            // if there are any ?... parameters parse them off and remove them from the string
            if (this.subscreenPath.contains("?")) {
                String pathParmString = this.subscreenPath.substring(this.subscreenPath.indexOf("?")+1)
                if (pathParmString) {
                    List<String> nameValuePairs = pathParmString.replaceAll("&amp;", "&").split("&") as List
                    for (String nameValuePair in nameValuePairs) {
                        String[] nameValue = nameValuePair.substring(1).split("=")
                        if (nameValue.length == 2) this.pathParameterMap.put(nameValue[0], nameValue[1])
                    }
                }
                this.subscreenPath = this.subscreenPath.substring(0, this.subscreenPath.indexOf("?"))
            }

            List<String> tempPathNameList = []
            tempPathNameList.addAll(this.fromPathList)
            if (this.subscreenPath) tempPathNameList.addAll(this.subscreenPath.split("/") as List)
            this.fullPathNameList = cleanupPathNameList(tempPathNameList, this.pathParameterMap)

            // encrypt is the default loop through screens if all are not secure/etc use http setting, otherwise https
            this.requireEncryption = false
            if (sri.rootScreenDef.getWebSettingsNode()?."require-encryption" != "false") {
                this.requireEncryption = true
            }
            // also loop through path to check validity, and see if we can do a transition short-cut and go right to its response url
            ScreenDefinition lastSd = sri.rootScreenDef
            for (String pathName in this.fullPathNameList) {
                String nextLoc = lastSd.getSubscreensItem(pathName)?.location
                if (!nextLoc) {
                    // handle case where last one may be a transition name, and not a subscreen name
                    TransitionItem ti = lastSd.getTransitionItem(pathName)
                    if (ti) {
                        // if subscreenPath is a transition, and that transition has no condition,
                        // call-service/actions or conditional-response then use the default-response.url instead
                        // of the name (if type is screen-path or empty, url-type is url or empty)
                        if (ti.condition == null && ti.actions == null && !ti.conditionalResponseList &&
                                ti.defaultResponse && ti.defaultResponse.type == "url" &&
                                ti.defaultResponse.urlType == "screen-path") {
                            String newSubPath = this.subscreenPath + "/../" + ti.defaultResponse.url
                            // call this method again, transition will get cleaned out in the cleanupPathNameList()
                            this.subscreenPath = newSubPath
                            initUrl()
                            return
                        } else {
                            this.targetTransition = ti
                        }
                        // if nothing happened there, just break out a transition means we're at the end
                        break
                    } else {
                        throw new IllegalArgumentException("Could not find subscreen or transition [${pathName}] in relative screen reference [${subscreenPath}] in screen [${lastSd.location}]")
                    }
                }
                ScreenDefinition nextSd = sri.sfi.getScreenDefinition(nextLoc)
                if (nextSd) {
                    if (nextSd.getWebSettingsNode()?."require-encryption" != "false") {
                        this.requireEncryption = true
                    }
                    lastSd = nextSd
                } else {
                    throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${pathName}] in relative screen reference [${subscreenPath}] in screen [${lastSd.location}]")
                }
            }

            this.targetScreen = lastSd

            StringBuilder urlBuilder = new StringBuilder()
            if (sri.baseLinkUrl != null) {
                urlBuilder.append(sri.baseLinkUrl)
            } else {
                // build base from conf
                Node webappNode = sri.getWebappNode()
                if (webappNode) {
                    if (this.requireEncryption && webappNode."@https-enabled" != "false") {
                        urlBuilder.append("https://")
                        if (webappNode."@https-host") {
                            urlBuilder.append(webappNode."@https-host")
                        } else {
                            if (sri.getEc() instanceof WebExecutionContext) {
                                urlBuilder.append(((WebExecutionContext) sri.getEc()).getRequest().getServerName())
                            } else {
                                // uh-oh, no web context, default to localhost
                                urlBuilder.append("localhost")
                            }
                        }
                        if (webappNode."@https-port") urlBuilder.append(":").append(webappNode."@https-port")
                    } else {
                        urlBuilder.append("http://")
                        if (webappNode."@http-host") {
                            urlBuilder.append(webappNode."@http-host")
                        } else {
                            if (sri.getEc() instanceof WebExecutionContext) {
                                urlBuilder.append(((WebExecutionContext) sri.getEc()).getRequest().getServerName())
                            } else {
                                // uh-oh, no web context, default to localhost
                                urlBuilder.append("localhost")
                            }
                        }
                        if (webappNode."@http-port") urlBuilder.append(":").append(webappNode."@http-port")
                    }
                    urlBuilder.append("/")
                } else {
                    // can't get these settings, hopefully a URL from the root will do
                    urlBuilder.append("/")
                }

                // add servletContext.contextPath
                String servletContextPath = sri.servletContextPath
                if (!servletContextPath && sri.getEc() instanceof WebExecutionContext)
                    servletContextPath = ((WebExecutionContext) sri.getEc()).getServletContext().getContextPath()
                if (servletContextPath) {
                    if (servletContextPath.startsWith("/")) servletContextPath = servletContextPath.substring(1)
                    urlBuilder.append(servletContextPath)
                }
            }

            if (urlBuilder.charAt(urlBuilder.length()-1) != '/') urlBuilder.append('/')
            for (String pathName in this.fullPathNameList) urlBuilder.append(pathName).append('/')
            if (urlBuilder.charAt(urlBuilder.length()-1) == '/') urlBuilder.deleteCharAt(urlBuilder.length()-1)

            this.url = urlBuilder.toString()
        }

        @Override
        String toString() {
            // return ONLY the url built from the inputs; that is the most basic possible value
            return this.url
        }
    }

    static List<String> cleanupPathNameList(List<String> inputPathNameList, Map inlineParameters) {
        // filter the list: remove empty, remove ".", remove ".." and previous
        List<String> cleanList = new ArrayList<String>(inputPathNameList.size())
        for (String pathName in inputPathNameList) {
            if (!pathName) continue
            if (pathName == ".") continue
            // .. means go up a level, ie drop the last in the list
            if (pathName == "..") { if (cleanList.size()) cleanList.remove(cleanList.size()-1); continue }
            // if it has a tilde it is a parameter, so skip it but remember it
            if (pathName.startsWith("~")) {
                if (inlineParameters != null) {
                    String[] nameValue = pathName.substring(1).split("=")
                    if (nameValue.length == 2) inlineParameters.put(nameValue[0], nameValue[1])
                }
                continue
            }
            cleanList.add(pathName)
        }
        return cleanList
    }
}
