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

import org.moqui.context.ResourceReference
import org.moqui.impl.screen.ScreenDefinition.ParameterItem
import org.moqui.impl.screen.ScreenDefinition.TransitionItem
import org.moqui.impl.webapp.ScreenResourceNotFoundException
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class ScreenUrlInfo {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenUrlInfo.class)

    ScreenRenderImpl sri

    ScreenDefinition fromSd = null
    List<String> fromPathList = null
    String fromScreenPath = null

    String baseUrl = null
    Map<String, String> pathParameterMap = new HashMap()
    boolean requireEncryption = false
    boolean hasActions = false
    boolean inCurrentScreenPath = false
    boolean disableLink = false
    boolean beginTransaction = false

    /** The full path name list for the URL */
    List<String> fullPathNameList = null

    /** The minimal path name list for the URL, basically without following the defaults */
    List<String> minimalPathNameList = null

    /** The path for a file resource (template or static), relative to the targetScreen.location */
    List<String> fileResourcePathList = null
    /** If the full path led to a file resource that is verified to exist, the URL goes here; the URL for access on the
     * server, the client will get the resource from the url field as normal */
    ResourceReference fileResourceRef = null
    String fileResourceContentType = null

    /** All screens found in the path list */
    List<ScreenDefinition> screenPathDefList = new ArrayList<ScreenDefinition>()
    /** The list of screens to render, starting with the root screen OR the last standalone screen if applicable */
    List<ScreenDefinition> screenRenderDefList = new ArrayList<ScreenDefinition>()
    int renderPathDifference = 0

    /** The last screen found in the path list */
    ScreenDefinition targetScreen = null
    /** If a transition is specified, the target transition within the targetScreen */
    TransitionItem targetTransition = null
    List<String> preTransitionPathNameList = new ArrayList<String>()

    protected ScreenUrlInfo() { }

    ScreenUrlInfo(ScreenRenderImpl sri, String url) {
        this.sri = sri
        this.baseUrl = url
    }

    ScreenUrlInfo(ScreenRenderImpl sri, ScreenDefinition fromScreenDef, List<String> fpnl, String ssp) {
        this.sri = sri

        fromSd = fromScreenDef
        if (fromSd == null) fromSd = sri.getActiveScreenDef()

        fromPathList = fpnl
        if (fromPathList == null)
            fromPathList = sri.getActiveScreenPath()

        fromScreenPath = ssp ?: ""

        initUrl()

        hasActions = (targetTransition && targetTransition.actions)
        // if sri.screenUrlInfo is null it is because this object is not yet set to it, so set this to true as it "is" the current screen path
        inCurrentScreenPath = sri.screenUrlInfo ? sri.isInCurrentScreenPath(minimalPathNameList) : true
        disableLink = targetTransition ? !targetTransition.checkCondition(sri.ec) : false
    }

    String getMinimalPathUrlWithParams() {
        String ps = getParameterString()
        return getMinimalPathUrl() + (ps ? "?" + ps : "")
    }

    String getMinimalPathUrl() {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
        for (String pathName in this.minimalPathNameList) urlBuilder.append('/').append(pathName)
        return urlBuilder.toString()
    }

    String getUrlWithParams() {
        String ps = getParameterString()
        return getUrl() + (ps ? "?" + ps : "")
    }

    String getUrl() {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
        for (String pathName in this.fullPathNameList) urlBuilder.append('/').append(pathName)
        return urlBuilder.toString()
    }

    Map<String, String> getParameterMap() {
        Map<String, String> pm = new HashMap()
        // get default parameters for the target screen
        if (targetScreen) {
            for (ParameterItem pi in targetScreen.getParameterMap().values()) {
                Object value = pi.getValue(sri.ec)
                if (value) pm.put(pi.name, value as String)
            }
        }
        // add all of the parameters specified inline in the screen path or added after
        if (pathParameterMap) pm.putAll(pathParameterMap)
        // logger.info("TOREMOVE Getting parameterMap [${pm}] for targetScreen [${targetScreen.location}]")
        return pm
    }

    ScreenUrlInfo addParameter(Object name, Object value) {
        if (!name || value == null) return this
        pathParameterMap.put(name as String, value as String)
        return this
    }
    ScreenUrlInfo addParameters(Map manualParameters) {
        if (!manualParameters) return this
        for (Map.Entry mpEntry in manualParameters.entrySet()) {
            pathParameterMap.put(mpEntry.key as String, mpEntry.value as String)
        }
        return this
    }

    String getParameterString() {
        StringBuilder ps = new StringBuilder()
        Map<String, String> pm = this.getParameterMap()
        for (Map.Entry<String, String> pme in pm) {
            if (!pme.value) continue
            if (ps.length() > 0) ps.append("&")
            ps.append(pme.key).append("=").append(sri.urlCodec.encode(pme.value))
        }
        return ps.toString()
    }

    String getParameterPathString() {
        StringBuilder ps = new StringBuilder()
        Map<String, String> pm = this.getParameterMap()
        for (Map.Entry<String, String> pme in pm) {
            if (!pme.value) continue
            ps.append("/~")
            ps.append(pme.key).append("=").append(sri.urlCodec.encode(pme.value))
        }
        return ps.toString()
    }

    void initUrl() {
        if (this.fromScreenPath.startsWith("/")) {
            this.fromSd = sri.rootScreenDef
            this.fromPathList = []
        }

        // if there are any ?... parameters parse them off and remove them from the string
        if (this.fromScreenPath.contains("?")) {
            String pathParmString = this.fromScreenPath.substring(this.fromScreenPath.indexOf("?")+1)
            if (pathParmString) {
                List<String> nameValuePairs = pathParmString.replaceAll("&amp;", "&").split("&") as List
                for (String nameValuePair in nameValuePairs) {
                    String[] nameValue = nameValuePair.substring(1).split("=")
                    if (nameValue.length == 2) this.pathParameterMap.put(nameValue[0], nameValue[1])
                }
            }
            this.fromScreenPath = this.fromScreenPath.substring(0, this.fromScreenPath.indexOf("?"))
        }

        List<String> tempPathNameList = []
        tempPathNameList.addAll(this.fromPathList)
        if (this.fromScreenPath) tempPathNameList.addAll(this.fromScreenPath.split("/") as List)
        this.fullPathNameList = cleanupPathNameList(tempPathNameList, this.pathParameterMap)

        // encrypt is the default loop through screens if all are not secure/etc use http setting, otherwise https
        this.requireEncryption = false
        if (sri.rootScreenDef?.webSettingsNode?."@require-encryption" != "false") this.requireEncryption = true
        if (sri.rootScreenDef.screenNode?."@begin-transaction" == "true") this.beginTransaction = true

        // start the render list with the from/base SD
        screenRenderDefList.add(fromSd)

        // loop through path for various things: check validity, see if we can do a transition short-cut and go right to its response url, etc
        ScreenDefinition lastSd = sri.rootScreenDef
        List<String> remainingPathList = new ArrayList<String>(fullPathNameList)
        for (String pathName in this.fullPathNameList) {
            String nextLoc = lastSd.getSubscreensItem(pathName)?.location
            if (!nextLoc) {
                // handle case where last one may be a transition name, and not a subscreen name
                TransitionItem ti = lastSd.getTransitionItem(pathName)
                if (ti) {
                    // if fromScreenPath is a transition, and that transition has no condition,
                    // service/actions or conditional-response then use the default-response.url instead
                    // of the name (if type is screen-path or empty, url-type is url or empty)
                    if (ti.condition == null && ti.actions == null && !ti.conditionalResponseList &&
                            ti.defaultResponse && ti.defaultResponse.type == "url" &&
                            ti.defaultResponse.urlType == "screen-path") {
                        String newSubPath = this.fromScreenPath + "/../" + ti.defaultResponse.url
                        // call this method again, transition will get cleaned out in the cleanupPathNameList()
                        this.fromScreenPath = newSubPath
                        for (ParameterItem pi in ti.defaultResponse.parameterMap.values())
                            this.addParameter(pi.name, pi.getValue(sri.ec))
                        initUrl()
                        return
                    }
                    this.targetTransition = ti
                    // if return above, just break out; a transition means we're at the end
                    break
                }

                // is this a file under the screen?
                ResourceReference existingFileRef = lastSd.getSubContentRef(remainingPathList)
                if (existingFileRef && existingFileRef.supportsExists() && existingFileRef.exists) {
                    fileResourceRef = existingFileRef
                    break
                }

                throw new ScreenResourceNotFoundException(fromSd, fullPathNameList, lastSd, pathName, new Exception("Screen sub-content not found here"))
            }
            ScreenDefinition nextSd = sri.sfi.getScreenDefinition(nextLoc)
            if (nextSd == null) throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${pathName}] in relative screen reference [${fromScreenPath}] in screen [${lastSd.location}]")

            if (nextSd.webSettingsNode?."@require-encryption" != "false") this.requireEncryption = true
            if (nextSd.screenNode?."@begin-transaction" == "true") this.beginTransaction = true

            // if standalone, clear out screenRenderDefList before adding this to it
            if (nextSd.screenNode?."@standalone" == "true" ||
                    (sri.ec.web != null && sri.ec.web.requestParameters.lastStandalone == "true")) {
                renderPathDifference += screenRenderDefList.size()
                screenRenderDefList.clear()
            }
            screenRenderDefList.add(nextSd)

            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // add this to the list of path names to use for transition redirect
            preTransitionPathNameList.add(pathName)

            // made it all the way to here so this was a screen or transition
            remainingPathList.remove(0)
        }

        // save the path so far for minimal URLs
        minimalPathNameList = new ArrayList(fullPathNameList)

        // beyond the last screenPathName, see if there are any screen.default-item values (keep following until none found)
        while (targetTransition == null && fileResourceRef == null && lastSd.screenNode."subscreens" && lastSd.screenNode."subscreens"."@default-item"[0]) {
            String subscreenName = lastSd.screenNode."subscreens"."@default-item"[0]
            String nextLoc = lastSd.getSubscreensItem(subscreenName)?.location
            if (!nextLoc) {
                // handle case where last one may be a transition name, and not a subscreen name
                targetTransition = lastSd.getTransitionItem(subscreenName)
                if (targetTransition) {
                    break
                } else {
                    throw new IllegalArgumentException("Could not find subscreen or transition [${subscreenName}] in screen [${lastSd.location}]")
                }
            }
            ScreenDefinition nextSd = sri.sfi.getScreenDefinition(nextLoc)
            if (nextSd == null) throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is default subscreen [${subscreenName}] in screen [${lastSd.location}]")

            if (nextSd.webSettingsNode?."@require-encryption" != "false") this.requireEncryption = true
            if (nextSd.screenNode?."@begin-transaction" == "true") this.beginTransaction = true

            // if standalone, clear out screenRenderDefList before adding this to it
            if (nextSd.screenNode?."@standalone" == "true" ||
                    (sri.ec.web != null && sri.ec.web.requestParameters.lastStandalone == "true")) {
                renderPathDifference += screenRenderDefList.size()
                screenRenderDefList.clear()
            }
            screenRenderDefList.add(nextSd)

            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // for use in URL writing and such add the subscreenName we found to the main path name list
            fullPathNameList.add(subscreenName)
            // add this to the list of path names to use for transition redirect, just in case a default is a transition
            preTransitionPathNameList.add(subscreenName)
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
                        if (sri.ec.web) {
                            urlBuilder.append(sri.ec.web.request.serverName)
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
                        if (sri.ec.web) {
                            urlBuilder.append(sri.ec.web.request.serverName)
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
            if (!servletContextPath && sri.ec.web)
                servletContextPath = sri.ec.web.servletContext.contextPath
            if (servletContextPath) {
                if (servletContextPath.startsWith("/")) servletContextPath = servletContextPath.substring(1)
                urlBuilder.append(servletContextPath)
            }
        }

        if (urlBuilder.charAt(urlBuilder.length()-1) == '/') urlBuilder.deleteCharAt(urlBuilder.length()-1)

        baseUrl = urlBuilder.toString()
    }

    @Override
    String toString() {
        // return ONLY the url built from the inputs; that is the most basic possible value
        return this.url
    }

    ScreenUrlInfo cloneUrlInfo() {
        ScreenUrlInfo sui = new ScreenUrlInfo()

        sui.sri = this.sri
        sui.fromSd = this.fromSd
        sui.fromPathList = this.fromPathList!=null ? new ArrayList<String>(this.fromPathList) : null
        sui.fromScreenPath = this.fromScreenPath
        sui.baseUrl = this.baseUrl
        sui.pathParameterMap = this.pathParameterMap!=null ? new HashMap(this.pathParameterMap) : null
        sui.requireEncryption = this.requireEncryption
        sui.hasActions = this.hasActions
        sui.inCurrentScreenPath = this.inCurrentScreenPath
        sui.disableLink = this.disableLink
        sui.beginTransaction = this.beginTransaction
        sui.fullPathNameList = this.fullPathNameList!=null ? new ArrayList(this.fullPathNameList) : null
        sui.minimalPathNameList = this.minimalPathNameList!=null ? new ArrayList(this.minimalPathNameList) : null
        sui.fileResourcePathList = this.fileResourcePathList!=null ? new ArrayList(this.fileResourcePathList) : null
        sui.fileResourceRef = this.fileResourceRef
        sui.fileResourceContentType = this.fileResourceContentType
        sui.screenPathDefList = this.screenPathDefList!=null ? new ArrayList(this.screenPathDefList) : null
        sui.screenRenderDefList = this.screenRenderDefList!=null ? new ArrayList(this.screenRenderDefList) : null
        sui.targetScreen = this.targetScreen
        sui.targetTransition = this.targetTransition
        sui.preTransitionPathNameList = this.preTransitionPathNameList!=null ? new ArrayList(this.preTransitionPathNameList) : null

        return sui
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
