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

import groovy.transform.CompileStatic
import org.apache.commons.codec.net.URLCodec
import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.context.ResourceReference
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.context.WebFacadeImpl
import org.moqui.impl.screen.ScreenDefinition.ParameterItem
import org.moqui.impl.screen.ScreenDefinition.TransitionItem
import org.moqui.impl.webapp.ScreenResourceNotFoundException
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.context.ArtifactExecutionFacadeImpl
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class ScreenUrlInfo {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenUrlInfo.class)
    protected final static URLCodec urlCodec = new URLCodec()

    // removed to make this independent of SRI: ScreenRenderImpl sri
    ExecutionContext ec
    ScreenFacadeImpl sfi
    ScreenDefinition rootSd
    List<String> currentPathNameList = null

    ScreenDefinition fromSd = null
    List<String> fromPathList = null
    String fromScreenPath = null

    String baseUrl = null
    Map<String, String> pathParameterMap = new HashMap()
    boolean requireEncryption = false
    boolean hasActions = false
    // removed as part of sri removal, now using currentPathNameList: boolean inCurrentScreenPath = false
    boolean disableLink = false
    boolean alwaysUseFullPath = false
    boolean beginTransaction = false

    /** The full path name list for the URL, including extraPathNameList */
    List<String> fullPathNameList = null

    /** The minimal path name list for the URL, basically without following the defaults */
    List<String> minimalPathNameList = null

    /** Everything in the path after the screen or transition, may be used to pass additional info */
    List<String> extraPathNameList = null

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
    boolean lastStandalone = false

    /** The last screen found in the path list */
    ScreenDefinition targetScreen = null
    /** If a transition is specified, the target transition within the targetScreen */
    TransitionItem targetTransition = null
    boolean expandAliasTransition = true
    String targetTransitionActualName = null
    List<String> preTransitionPathNameList = new ArrayList<String>()

    protected ScreenUrlInfo() { }

    ScreenUrlInfo(ScreenRenderImpl sri, String url) {
        this.ec = sri.getEc()
        this.sfi = sri.getSfi()
        this.rootSd = sri.getRootScreenDef()
        this.baseUrl = url
    }

    ScreenUrlInfo(ExecutionContext ec, ScreenDefinition rootSd, String baseUrl, List<String> currentPathNameList,
                  ScreenDefinition fromScreenDef, List<String> fpnl, String ssp, Boolean expandAliasTransition, Boolean lastStandalone) {
        this.ec = ec
        this.sfi = (ScreenFacadeImpl) ec.getScreen()
        this.rootSd = rootSd
        this.baseUrl = baseUrl
        this.currentPathNameList = currentPathNameList
        fromSd = fromScreenDef
        fromPathList = fpnl

        internalInit(ssp, expandAliasTransition, lastStandalone)
    }

    ScreenUrlInfo(ScreenRenderImpl sri, ScreenDefinition fromScreenDef, List<String> fpnl, String ssp,
                  Boolean expandAliasTransition, Boolean lastStandalone) {
        this.ec = sri.getEc()
        this.sfi = sri.getSfi()
        this.rootSd = sri.getRootScreenDef()
        this.currentPathNameList = sri.screenUrlInfo ? new ArrayList(sri.screenUrlInfo.fullPathNameList) : null

        fromSd = fromScreenDef
        fromPathList = fpnl
        if (fromSd == null) fromSd = sri.getActiveScreenDef()
        if (fromPathList == null) fromPathList = sri.getActiveScreenPath()

        internalInit(ssp, expandAliasTransition, lastStandalone)

        if (sri.baseLinkUrl) {
            baseUrl = sri.baseLinkUrl
            if (baseUrl && baseUrl.charAt(baseUrl.length()-1) == '/') baseUrl.substring(0, baseUrl.length()-1)
        } else {
            if (!sri.webappName) throw new BaseException("No webappName specified, cannot get base URL for screen location ${sri.rootScreenLocation}")
            baseUrl = WebFacadeImpl.getWebappRootUrl(sri.webappName, sri.servletContextPath, true,
                    this.requireEncryption, (ExecutionContextImpl) ec)
        }

        // if sri.screenUrlInfo is null it is because this object is not yet set to it, so set this to true as it "is" the current screen path
        // inCurrentScreenPath = sri.screenUrlInfo ? sri.isInCurrentScreenPath(minimalPathNameList) : true
    }

    protected void internalInit(String ssp, Boolean expandAliasTransition, Boolean lastStandalone) {
        this.expandAliasTransition = expandAliasTransition != null ? expandAliasTransition : true
        this.lastStandalone = lastStandalone != null ? lastStandalone : false
        fromScreenPath = ssp ?: ""

        initUrl()

        hasActions = (targetTransition && targetTransition.actions)
        disableLink = (targetTransition && !targetTransition.checkCondition(ec)) || !isPermitted()
    }
    boolean getInCurrentScreenPath() {
        // if currentPathNameList (was from sri.screenUrlInfo) is null it is because this object is not yet set to it, so set this to true as it "is" the current screen path
        if (currentPathNameList == null) return true
        if (minimalPathNameList.size() > currentPathNameList.size()) return false
        for (int i = 0; i < minimalPathNameList.size(); i++) {
            if (minimalPathNameList.get(i) != currentPathNameList.get(i)) return false
        }
        return true
    }

    boolean isPermitted() {
        ArtifactExecutionFacadeImpl aefi = (ArtifactExecutionFacadeImpl) ec.getArtifactExecution()
        String username = ec.getUser().getUsername()

        // if a user is permitted to view a certain location once in a render/ec they can safely be always allowed to, so cache it
        // add the username to the key just in case user changes during an EC instance
        String permittedCacheKey = username + fullPathNameList.toString()
        Boolean cachedPermitted = aefi.screenPermittedCache.get(permittedCacheKey)
        if (cachedPermitted != null) return cachedPermitted

        Deque<ArtifactExecutionInfoImpl> artifactExecutionInfoStack = new LinkedList<ArtifactExecutionInfoImpl>()

        int index = 1
        for (ScreenDefinition screenDef in screenPathDefList) {
            ArtifactExecutionInfoImpl aeii = new ArtifactExecutionInfoImpl(screenDef.getLocation(), "AT_XML_SCREEN", "AUTHZA_VIEW")

            ArtifactExecutionInfoImpl lastAeii = artifactExecutionInfoStack.peekFirst()

            // logger.warn("TOREMOVE checking screen for user ${username} - ${aeii}")

            boolean isLast = (index == screenPathDefList.size())
            Node screenNode = screenDef.getScreenNode()

            // if screen is limited to certain tenants, and current tenant is not in the Set, it is not permitted
            if (screenDef.getTenantsAllowed() && !screenDef.getTenantsAllowed().contains(ec.getTenantId())) return false

            String requireAuthentication = (String) screenNode.attributes().get('require-authentication')
            if (!aefi.isPermitted(username, aeii, lastAeii,
                    isLast ? (!requireAuthentication || requireAuthentication == "true") : false,
                    false, ec.getUser().getNowTimestamp())) {
                // logger.warn("TOREMOVE user ${username} is NOT allowed to view screen at path ${this.fullPathNameList} because of screen at ${screenDef.location}")
                aefi.screenPermittedCache.put(permittedCacheKey, false)
                return false
            }

            artifactExecutionInfoStack.addFirst(aeii)
            index++
        }

        // logger.warn("TOREMOVE user ${username} IS allowed to view screen at path ${this.fullPathNameList}")
        aefi.screenPermittedCache.put(permittedCacheKey, true)
        return true
    }

    String getMinimalPathUrlWithParams() {
        String ps = getParameterString()
        return getMinimalPathUrl() + (ps ? "?" + ps : "")
    }

    String getMinimalPathUrl() {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
        if (alwaysUseFullPath) {
            // really get the full path instead of minimal
            for (String pathName in this.fullPathNameList) urlBuilder.append('/').append(pathName)
        } else {
            for (String pathName in this.minimalPathNameList) urlBuilder.append('/').append(pathName)
        }
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

    String getScreenPathUrl() {
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
        for (String pathName in this.preTransitionPathNameList) urlBuilder.append('/').append(pathName)
        return urlBuilder.toString()
    }

    List<String> getPreTransitionPathNameList() { return preTransitionPathNameList }
    List<String> getExtraPathNameList() { return extraPathNameList }

    Map<String, String> getParameterMap() {
        Map<String, String> pm = new HashMap()
        // get default parameters for the target screen
        if (targetScreen != null) {
            for (ParameterItem pi in targetScreen.getParameterMap().values()) {
                Object value = pi.getValue(ec)
                if (value) pm.put(pi.name, StupidUtilities.toPlainString(value))
            }
        }
        if (targetTransition != null && targetTransition.getParameterMap()) {
            for (ParameterItem pi in targetTransition.getParameterMap().values()) {
                Object value = pi.getValue(ec)
                if (value) pm.put(pi.name, StupidUtilities.toPlainString(value))
            }
        }
        if (targetTransition != null && targetTransition.getSingleServiceName()) {
            String targetServiceName = targetTransition.getSingleServiceName()
            ServiceDefinition sd = ((ServiceFacadeImpl) ec.getService()).getServiceDefinition(targetServiceName)
            if (sd != null) {
                for (String pn in sd.getInParameterNames()) {
                    Object value = ec.getContext().get(pn)
                    if (!value && ec.getWeb() != null) value = ec.getWeb().getParameters().get(pn)
                    if (value) pm.put(pn, StupidUtilities.toPlainString(value))
                }
            } else if (targetServiceName.contains("#")) {
                // service name but no service def, see if it is an entity op and if so try the pk fields
                String verb = targetServiceName.substring(0, targetServiceName.indexOf("#"))
                if (verb == "create" || verb == "update" || verb == "delete" || verb == "store") {
                    String en = targetServiceName.substring(targetServiceName.indexOf("#") + 1)
                    EntityDefinition ed = ((EntityFacadeImpl) ec.getEntity()).getEntityDefinition(en)
                    if (ed != null) {
                        for (String fn in ed.getPkFieldNames()) {
                            Object value = ec.getContext().get(fn)
                            if (!value && ec.getWeb() != null) value = ec.getWeb().getParameters().get(fn)
                            if (value) pm.put(fn, StupidUtilities.toPlainString(value))
                        }
                    }
                }
            }
        }
        // add all of the parameters specified inline in the screen path or added after
        if (pathParameterMap) pm.putAll(pathParameterMap)
        // logger.info("TOREMOVE Getting parameterMap [${pm}] for targetScreen [${targetScreen.location}]")
        return pm
    }

    String getParameterString() {
        StringBuilder ps = new StringBuilder()
        Map<String, String> pm = this.getParameterMap()
        for (Map.Entry<String, String> pme in pm.entrySet()) {
            if (!pme.value) continue
            if (ps.length() > 0) ps.append("&")
            ps.append(pme.key).append("=").append(urlCodec.encode(pme.value))
        }
        return ps.toString()
    }
    String getParameterPathString() {
        StringBuilder ps = new StringBuilder()
        Map<String, String> pm = this.getParameterMap()
        for (Map.Entry<String, String> pme in pm.entrySet()) {
            if (!pme.getValue()) continue
            ps.append("/~")
            ps.append(pme.getKey()).append("=").append(urlCodec.encode(pme.getValue()))
        }
        return ps.toString()
    }

    ScreenUrlInfo addParameter(Object name, Object value) {
        if (!name || value == null) return this
        pathParameterMap.put(name as String, StupidUtilities.toPlainString(value))
        return this
    }
    ScreenUrlInfo addParameters(Map manualParameters) {
        if (!manualParameters) return this
        for (Map.Entry mpEntry in manualParameters.entrySet()) {
            pathParameterMap.put(mpEntry.getKey() as String, StupidUtilities.toPlainString(mpEntry.getValue()))
        }
        return this
    }
    Map getPathParameterMap() { return pathParameterMap }

    ScreenUrlInfo passThroughSpecialParameters() {
        copySpecialParameters(ec.context, pathParameterMap)
        return this
    }
    static void copySpecialParameters(Map fromMap, Map toMap) {
        if (!fromMap || !toMap) return
        for (String fieldName in fromMap.keySet()) {
            if (fieldName.startsWith("formDisplayOnly")) toMap.put(fieldName, (String) fromMap.get(fieldName))
        }
        if (fromMap.containsKey("pageNoLimit")) toMap.put("pageNoLimit", (String) fromMap.get("pageNoLimit"))
        if (fromMap.containsKey("lastStandalone")) toMap.put("lastStandalone", (String) fromMap.get("lastStandalone"))
        if (fromMap.containsKey("renderMode")) toMap.put("renderMode", (String) fromMap.get("renderMode"))
    }

    void initUrl() {
        // if there are any ?... parameters parse them off and remove them from the string
        if (this.fromScreenPath.contains("?")) {
            String pathParmString = this.fromScreenPath.substring(this.fromScreenPath.indexOf("?")+1)
            if (pathParmString) {
                List<String> nameValuePairs = pathParmString.replaceAll("&amp;", "&").split("&") as List
                for (String nameValuePair in nameValuePairs) {
                    String[] nameValue = nameValuePair.substring(0).split("=")
                    if (nameValue.length == 2) this.pathParameterMap.put(nameValue[0], nameValue[1])
                }
            }
            this.fromScreenPath = this.fromScreenPath.substring(0, this.fromScreenPath.indexOf("?"))
        }

        // support string expansion if there is a "${"
        if (fromScreenPath.contains('${')) fromScreenPath = ec.getResource().evaluateStringExpand(fromScreenPath, "")

        if (fromScreenPath.startsWith("//")) {
            // find the screen by name
            fromSd = rootSd
            fromPathList = []

            String trimmedFromPath = fromScreenPath.substring(2)
            List<String> originalPathNameList = trimmedFromPath.split("/") as List
            originalPathNameList = cleanupPathNameList(originalPathNameList, pathParameterMap)

            if (sfi.screenFindPathCache.containsKey(fromScreenPath)) {
                List<String> cachedPathList = (List<String>) sfi.screenFindPathCache.get(fromScreenPath)
                if (cachedPathList) {
                    fromPathList = cachedPathList
                    fullPathNameList = cachedPathList
                } else {
                    throw new ScreenResourceNotFoundException(fromSd, originalPathNameList, fromSd, fromScreenPath, null,
                            new Exception("Could not find screen, transition or content matching path"))
                }
            } else {
                List<String> expandedPathNameList = rootSd.findSubscreenPath(originalPathNameList, ec.web ? ec.web.request.method : "")
                sfi.screenFindPathCache.put(fromScreenPath, expandedPathNameList)
                if (expandedPathNameList) {
                    fromPathList = expandedPathNameList
                    fullPathNameList = expandedPathNameList
                } else {
                    throw new ScreenResourceNotFoundException(fromSd, originalPathNameList, fromSd, fromScreenPath, null,
                            new Exception("Could not find screen, transition or content matching path"))
                }
            }
        } else {
            if (this.fromScreenPath.startsWith("/")) {
                this.fromSd = rootSd
                this.fromPathList = []
            }

            List<String> tempPathNameList = []
            tempPathNameList.addAll(fromPathList)
            if (fromScreenPath) tempPathNameList.addAll(fromScreenPath.split("/") as List)
            fullPathNameList = cleanupPathNameList(tempPathNameList, pathParameterMap)
        }

        // encrypt is the default loop through screens if all are not secure/etc use http setting, otherwise https
        requireEncryption = false
        if (rootSd?.webSettingsNode?.attributes()?.get('require-encryption') != "false") requireEncryption = true
        if (rootSd?.screenNode?.attributes()?.get('begin-transaction') == "true") beginTransaction = true

        // start the render list with the from/base SD
        screenRenderDefList.add(fromSd)
        screenPathDefList.add(fromSd)

        // loop through path for various things: check validity, see if we can do a transition short-cut and go right
        //     to its response url, etc
        ScreenDefinition lastSd = rootSd
        extraPathNameList = new ArrayList<String>(fullPathNameList)
        for (String pathName in fullPathNameList) {
            String nextLoc = lastSd.getSubscreensItem(pathName)?.location

            if (!nextLoc) {
                // handle case where last one may be a transition name, and not a subscreen name
                TransitionItem ti = lastSd.getTransitionItem(pathName, ec.web ? ec.web.request.method : "")
                if (ti) {
                    // extra path elements always allowed after transitions for parameters, but we don't want the transition name on it
                    extraPathNameList.remove(0)

                    // Screen Transition as a URL Alias:
                    // if fromScreenPath is a transition, and that transition has no condition,
                    // service/actions or conditional-response then use the default-response.url instead
                    // of the name (if type is screen-path or empty, url-type is url or empty)
                    if (ti.condition == null && !ti.hasActionsOrSingleService() && !ti.conditionalResponseList &&
                            ti.defaultResponse && ti.defaultResponse.type == "url" &&
                            ti.defaultResponse.urlType == "screen-path" && ec.web != null && expandAliasTransition) {

                        Map transitionParameters = ti.defaultResponse.expandParameters(this, ec)

                        // create a ScreenUrlInfo, then copy its info into this
                        ScreenUrlInfo aliasUrlInfo = new ScreenUrlInfo(ec, rootSd, baseUrl, currentPathNameList, fromSd,
                                preTransitionPathNameList, ti.defaultResponse.url, false,
                                (this.lastStandalone || transitionParameters.lastStandalone == "true"))

                        // add transition parameters
                        aliasUrlInfo.addParameters(transitionParameters)

                        // for alias transitions rendered in-request put the parameters in the context
                        ec.getContext().putAll(transitionParameters)

                        aliasUrlInfo.copyUrlInfoInto(this)
                        return
                    }

                    this.targetTransition = ti
                    this.targetTransitionActualName = pathName

                    // if no return above, just break out; a transition means we're at the end
                    break
                }

                // is this a file under the screen?
                ResourceReference existingFileRef = lastSd.getSubContentRef(extraPathNameList)
                if (existingFileRef && existingFileRef.supportsExists() && existingFileRef.exists) {
                    // exclude screen files, don't want to treat them as resources and let them be downloaded
                    if (!sfi.isScreen(existingFileRef.getLocation())) {
                        fileResourceRef = existingFileRef
                        break
                    }
                }

                if (lastSd.screenNode.attributes().get('allow-extra-path') == "true") {
                    // call it good
                    break
                }

                throw new ScreenResourceNotFoundException(fromSd, fullPathNameList, lastSd, extraPathNameList?.last(), null,
                        new Exception("Screen sub-content not found here"))
            }

            ScreenDefinition nextSd = sfi.getScreenDefinition(nextLoc)
            if (nextSd == null) {
                throw new ScreenResourceNotFoundException(fromSd, fullPathNameList, lastSd, pathName, nextLoc,
                        new Exception("Screen subscreen or transition not found here"))
                // throw new IllegalArgumentException("Could not find screen at location [${nextLoc}], which is subscreen [${pathName}] in relative screen reference [${fromScreenPath}] in screen [${lastSd.location}]")
            }

            if (nextSd.webSettingsNode?.attributes()?.get('require-encryption') != "false") this.requireEncryption = true
            if (nextSd.screenNode?.attributes()?.get('begin-transaction') == "true") this.beginTransaction = true
            if (nextSd.getSubscreensNode()?.attributes()?.get('always-use-full-path') == "true") alwaysUseFullPath = true

            // if standalone, clear out screenRenderDefList before adding this to it
            if (nextSd.screenNode?.attributes()?.get('standalone') == "true" || this.lastStandalone) {
                renderPathDifference += screenRenderDefList.size()
                screenRenderDefList.clear()
            }
            screenRenderDefList.add(nextSd)

            screenPathDefList.add(nextSd)
            lastSd = nextSd
            // add this to the list of path names to use for transition redirect
            preTransitionPathNameList.add(pathName)

            // made it all the way to here so this was a screen
            extraPathNameList.remove(0)
        }

        // save the path so far for minimal URLs
        minimalPathNameList = new ArrayList(fullPathNameList)

        // beyond the last screenPathName, see if there are any screen.default-item values (keep following until none found)
        while (targetTransition == null && fileResourceRef == null && (String) lastSd.getSubscreensNode()?.attributes()?.get('default-item')) {
            String subscreenName = (String) lastSd.getSubscreensNode()?.attributes()?.get('default-item')
            if (lastSd.getSubscreensNode()?.attributes()?.get('always-use-full-path') == "true") alwaysUseFullPath = true
            // logger.warn("TOREMOVE lastSd ${minimalPathNameList} subscreens: ${lastSd.screenNode?.subscreens}, alwaysUseFullPath=${alwaysUseFullPath}, from ${lastSd.screenNode."subscreens"?."@always-use-full-path"?.getAt(0)}, subscreenName=${subscreenName}")

            // if any conditional-default.@condition eval to true, use that conditional-default.@item instead
            NodeList condDefaultList = (NodeList) lastSd.getSubscreensNode()?.get("conditional-default")
            if (condDefaultList) for (Object conditionalDefaultObj in condDefaultList) {
                Node conditionalDefaultNode = (Node) conditionalDefaultObj
                String condStr = (String) conditionalDefaultNode.attributes().get('condition')
                if (!condStr) continue
                if (ec.resource.evaluateCondition(condStr, null)) {
                    subscreenName = conditionalDefaultNode.attributes().get('item')
                    break
                }
            }

            String nextLoc = lastSd.getSubscreensItem(subscreenName)?.location
            if (!nextLoc) {
                // handle case where last one may be a transition name, and not a subscreen name
                targetTransition = lastSd.getTransitionItem(subscreenName, ec.web ? ec.web.request.method : "")
                if (targetTransition) {
                    targetTransitionActualName = subscreenName
                    fullPathNameList.add(subscreenName)
                    break
                }

                // is this a file under the screen?
                ResourceReference existingFileRef = lastSd.getSubContentRef([subscreenName])
                if (existingFileRef && existingFileRef.supportsExists() && existingFileRef.exists) {
                    fileResourceRef = existingFileRef
                    fullPathNameList.add(subscreenName)
                    break
                }

                throw new ScreenResourceNotFoundException(fromSd, fullPathNameList, lastSd, subscreenName, null,
                        new Exception("Screen subscreen or transition not found here"))
                // throw new BaseException("Could not find subscreen or transition [${subscreenName}] in screen [${lastSd.location}]")
            }
            ScreenDefinition nextSd = sfi.getScreenDefinition(nextLoc)
            if (nextSd == null) {
                throw new ScreenResourceNotFoundException(fromSd, fullPathNameList, lastSd, subscreenName, nextLoc,
                        new Exception("Screen subscreen or transition not found here"))
                // throw new BaseException("Could not find screen at location [${nextLoc}], which is default subscreen [${subscreenName}] in screen [${lastSd.location}]")
            }

            if (nextSd.webSettingsNode?.attributes()?.get('require-encryption') != "false") this.requireEncryption = true
            if (nextSd.screenNode?.attributes()?.get('begin-transaction') == "true") this.beginTransaction = true

            // if standalone, clear out screenRenderDefList before adding this to it
            if (nextSd.screenNode?.attributes()?.get('standalone') == "true" || this.lastStandalone) {
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
    }

    @Override
    String toString() {
        // return ONLY the url built from the inputs; that is the most basic possible value
        return this.url
    }

    ScreenUrlInfo cloneUrlInfo() {
        ScreenUrlInfo sui = new ScreenUrlInfo()
        this.copyUrlInfoInto(sui)
        return sui
    }

    void copyUrlInfoInto(ScreenUrlInfo sui) {
        sui.ec = this.ec
        sui.sfi = this.sfi
        sui.rootSd = this.rootSd
        sui.currentPathNameList = this.currentPathNameList
        sui.fromSd = this.fromSd
        sui.fromPathList = this.fromPathList!=null ? new ArrayList<String>(this.fromPathList) : null
        sui.fromScreenPath = this.fromScreenPath
        sui.baseUrl = this.baseUrl
        sui.pathParameterMap = this.pathParameterMap!=null ? new HashMap(this.pathParameterMap) : null
        sui.requireEncryption = this.requireEncryption
        sui.hasActions = this.hasActions
        sui.disableLink = this.disableLink
        sui.beginTransaction = this.beginTransaction
        sui.fullPathNameList = this.fullPathNameList!=null ? new ArrayList(this.fullPathNameList) : null
        sui.minimalPathNameList = this.minimalPathNameList!=null ? new ArrayList(this.minimalPathNameList) : null
        sui.fileResourcePathList = this.fileResourcePathList!=null ? new ArrayList(this.fileResourcePathList) : null
        sui.fileResourceRef = this.fileResourceRef
        sui.fileResourceContentType = this.fileResourceContentType
        sui.screenPathDefList = this.screenPathDefList!=null ? new ArrayList(this.screenPathDefList) : null
        sui.screenRenderDefList = this.screenRenderDefList!=null ? new ArrayList(this.screenRenderDefList) : null
        sui.renderPathDifference = this.renderPathDifference
        sui.lastStandalone = this.lastStandalone
        sui.targetScreen = this.targetScreen
        sui.targetTransition = this.targetTransition
        sui.expandAliasTransition = this.expandAliasTransition
        sui.targetTransitionActualName = this.targetTransitionActualName
        sui.preTransitionPathNameList = this.preTransitionPathNameList!=null ? new ArrayList(this.preTransitionPathNameList) : null
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
