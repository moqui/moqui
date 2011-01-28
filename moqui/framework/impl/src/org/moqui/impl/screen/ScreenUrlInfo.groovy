package org.moqui.impl.screen

import org.moqui.impl.screen.ScreenDefinition.TransitionItem
import org.moqui.impl.screen.ScreenDefinition.ParameterItem
import org.moqui.context.WebExecutionContext

class ScreenUrlInfo {
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

    /** The full path name list for the URL */
    List<String> fullPathNameList = null

    /** The minimal path name list for the URL, basically without following the defaults */
    List<String> minimalPathNameList = null

    /** The path for a file resource (template or static), relative to the targetScreen.location */
    List<String> fileResourcePathList = null
    /** If the full path led to a file resource that is verified to exist, the URL goes here; the URL for access on the
     * server, the client will get the resource from the url field as normal */
    URL fileResourceUrl = null

    /** All screens found in the path list */
    List<ScreenDefinition> screenPathDefList = new ArrayList<ScreenDefinition>()
    /** The last screen found in the path list */
    ScreenDefinition targetScreen = null
    /** If a transition is specified, the target transition within the targetScreen */
    TransitionItem targetTransition = null
    List<String> preTransitionPathNameList = new ArrayList<String>()

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

    void addParameters(Map<String, Object> parameters) {
        for (Map.Entry<String, Object> p in parameters)
            this.pathParameterMap.put(p.getKey(), p.getValue() as String)
    }

    String getMinimalPathUrl() {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
        for (String pathName in this.minimalPathNameList) urlBuilder.append('/').append(pathName)
        return urlBuilder.toString()
    }

    String getFullUrl() {
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
                Object value = pi.getValue(sri.ec.context)
                if (value) pm.put(pi.name, value as String)
            }
        }
        // add all of the parameters specified inline in the screen path or added after
        if (pathParameterMap) pm.putAll(pathParameterMap)
        return pm
    }

    String getParameterString() {
        StringBuilder ps = new StringBuilder()
        Map<String, String> pm = this.getParameterMap()
        boolean isFirst = true
        for (Map.Entry<String, String> pme in pm) {
            if (isFirst) isFirst = false else ps.append("&")
            ps.append(pme.key).append("=").append(sri.urlCodec.encode(pme.value))
        }
        return ps.toString()
    }

    String getParameterPathString() {
        StringBuilder ps = new StringBuilder()
        Map<String, String> pm = this.getParameterMap()
        for (Map.Entry<String, String> pme in pm) {
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
        if (sri.rootScreenDef.getWebSettingsNode()?."require-encryption" != "false") {
            this.requireEncryption = true
        }

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
                    // call-service/actions or conditional-response then use the default-response.url instead
                    // of the name (if type is screen-path or empty, url-type is url or empty)
                    if (ti.condition == null && ti.actions == null && !ti.conditionalResponseList &&
                            ti.defaultResponse && ti.defaultResponse.type == "url" &&
                            ti.defaultResponse.urlType == "screen-path") {
                        String newSubPath = this.fromScreenPath + "/../" + ti.defaultResponse.url
                        // call this method again, transition will get cleaned out in the cleanupPathNameList()
                        this.fromScreenPath = newSubPath
                        initUrl()
                        return
                    }
                    this.targetTransition = ti
                    // if return above, just break out; a transition means we're at the end
                    break
                }

                // is this a file under the screen?
                URL lastScreenUrl = sri.sfi.ecfi.resourceFacade.getLocationUrl(lastSd.location)
                if (lastScreenUrl.protocol == "file") {
                    StringBuilder fileLoc = new StringBuilder(lastScreenUrl.toString())
                    // get rid of the "file:" prefix
                    fileLoc.delete(0, 5)
                    // get rid of the suffix, probably .xml but use .*
                    if (fileLoc.indexOf(".") > 0) fileLoc.delete(fileLoc.indexOf("."), fileLoc.length())
                    // add the path elements that remain
                    for (String rp in remainingPathList) fileLoc.append("/").append(rp)

                    File theFile = new File(fileLoc.toString())
                    if (theFile.exists() && theFile.isFile()) {
                        fileResourceUrl = theFile.toURI().toURL()
                        break
                    }
                }

                throw new IllegalArgumentException("Could not find subscreen or transition or file/content [${pathName}] in screen [${lastSd.location}] while finding url for path [${fullPathNameList}] based on [${fromPathList}]:[${fromScreenPath}] relative to screen [${fromSd.location}]")
            }
            ScreenDefinition nextSd = sri.sfi.getScreenDefinition(nextLoc)
            if (nextSd) {
                if (nextSd.getWebSettingsNode()?."require-encryption" != "false") this.requireEncryption = true
                screenPathDefList.add(nextSd)
                lastSd = nextSd
                // add this to the list of path names to use for transition redirect
                preTransitionPathNameList.add(pathName)
            } else {
                throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${pathName}] in relative screen reference [${fromScreenPath}] in screen [${lastSd.location}]")
            }
            // made it all the way to here so this was a screen or transition
            remainingPathList.remove(0)
        }

        // save the path so far for minimal URLs
        minimalPathNameList = new ArrayList(fullPathNameList)

        // beyond the last screenPathName, see if there are any screen.default-item values (keep following until none found)
        while (!targetTransition && !fileResourceUrl && lastSd.screenNode."subscreens" && lastSd.screenNode."subscreens"."@default-item"[0]) {
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
            if (!nextSd) throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is default subscreen [${subscreenName}] in screen [${lastSd.location}]")
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

        if (urlBuilder.charAt(urlBuilder.length()-1) == '/') urlBuilder.deleteCharAt(urlBuilder.length()-1)

        baseUrl = urlBuilder.toString()
    }

    @Override
    String toString() {
        // return ONLY the url built from the inputs; that is the most basic possible value
        return this.url
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
