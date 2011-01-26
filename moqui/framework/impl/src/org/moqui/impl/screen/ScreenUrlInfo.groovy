package org.moqui.impl.screen

import org.moqui.impl.screen.ScreenDefinition.TransitionItem
import org.moqui.impl.screen.ScreenDefinition.ParameterItem
import org.moqui.context.WebExecutionContext

class ScreenUrlInfo {
    ScreenRenderImpl sri

    ScreenDefinition fromSd = null
    List<String> fromPathList = null
    String subscreenPath = null

    String url = null
    Map<String, String> pathParameterMap = new HashMap()
    boolean requireEncryption = false
    boolean hasActions = false
    boolean inCurrentScreenPath = false
    boolean disableLink = false

    List<String> fullPathNameList = null
    ScreenDefinition targetScreen = null
    TransitionItem targetTransition = null

    ScreenUrlInfo(ScreenRenderImpl sri, String url) {
        this.sri = sri
        this.url = url
    }

    ScreenUrlInfo(ScreenRenderImpl sri, ScreenDefinition fs, List<String> fpnl, String ssp) {
        this.sri = sri

        fromSd = fs
        if (fromSd == null) fromSd = sri.getActiveScreenDef()

        fromPathList = fpnl
        if (fromPathList == null)
            fromPathList = (sri.screenPathIndex >= 0 ? sri.screenPathNameList[0..sri.screenPathIndex] : [])

        subscreenPath = ssp ?: ""

        initUrl()

        hasActions = (targetTransition && targetTransition.actions)

        inCurrentScreenPath = sri.isInCurrentScreenPath(fullPathNameList)

        disableLink = targetTransition ? !targetTransition.checkCondition(sri.ec) : false
    }

    void addParameters(Map<String, Object> parameters) {
        for (Map.Entry<String, Object> p in parameters)
            this.pathParameterMap.put(p.getKey(), p.getValue() as String)
    }

    String getFullUrl() {
        String ps = getParameterString()
        return url + (ps ? "?" + ps : "")
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
