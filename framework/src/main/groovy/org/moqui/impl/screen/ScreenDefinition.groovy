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

import org.codehaus.groovy.runtime.InvokerHelper
import org.moqui.context.ExecutionContext
import org.moqui.context.ScreenFacade
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import groovy.util.slurpersupport.GPathResult
import org.moqui.impl.actions.XmlAction
import org.moqui.context.ResourceReference
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.StupidUtilities
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityCondition
import org.moqui.impl.context.ContextBinding
import org.moqui.service.ServiceFacade
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ScreenDefinition {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenDefinition.class)

    protected final ScreenFacadeImpl sfi
    protected final Node screenNode
    protected final String location

    protected Map<String, ParameterItem> parameterByName = new HashMap()
    protected Map<String, TransitionItem> transitionByName = new HashMap()
    protected Map<String, SubscreensItem> subscreensByName = new HashMap()
    protected List<SubscreensItem> subscreensItemsSorted = null
    protected Set<String> tenantsAllowed = null

    protected XmlAction alwaysActions = null
    protected XmlAction preActions = null

    protected ScreenSection rootSection = null
    protected Map<String, ScreenSection> sectionByName = new HashMap()
    protected Map<String, ScreenForm> formByName = new HashMap()
    protected Map<String, ScreenTree> treeByName = new HashMap()

    protected Map<String, ResourceReference> subContentRefByPath = new HashMap()

    ScreenDefinition(ScreenFacadeImpl sfi, Node screenNode, String location) {
        this.sfi = sfi
        this.screenNode = screenNode
        this.location = location

        long startTime = System.currentTimeMillis()

        // parameter
        for (Node parameterNode in screenNode."parameter")
            parameterByName.put((String) parameterNode."@name", new ParameterItem(parameterNode, location))
        // prep always-actions
        if (screenNode."always-actions")
            alwaysActions = new XmlAction(sfi.ecfi, (Node) screenNode."always-actions"[0], location + ".always_actions")
        // transition
        for (Node transitionNode in screenNode."transition") {
            TransitionItem ti = new TransitionItem(transitionNode, this)
            transitionByName.put(ti.method == "any" ? ti.name : ti.name + "#" + ti.method, ti)
        }
        // subscreens
        populateSubscreens()

        // tenants-allowed
        if (screenNode."@tenants-allowed") {
            tenantsAllowed = new HashSet(Arrays.asList(((String) screenNode."@tenants-allowed").split(",")))
        }

        // prep pre-actions
        if (screenNode."pre-actions")
            preActions = new XmlAction(sfi.ecfi, (Node) screenNode."pre-actions"[0], location + ".pre_actions")

        // get the root section
        rootSection = new ScreenSection(sfi.ecfi, screenNode, location + ".screen")

        if (rootSection && rootSection.widgets) {
            // get all of the other sections by name
            for (Node sectionNode in (Collection<Node>) rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it instanceof Node && (it.name() == "section" || it.name() == "section-iterate") })) {
                sectionByName.put((String) sectionNode["@name"],
                        new ScreenSection(sfi.ecfi, sectionNode, "${location}.${sectionNode.name().replace('-','_')}_${sectionNode["@name"].replace('-','_')}"))
            }
            for (Node sectionNode in (Collection<Node>) rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it instanceof Node && (it.name() == "section-include") })) {
                ScreenDefinition includeScreen = sfi.getEcfi().getScreenFacade().getScreenDefinition((String) sectionNode["@location"])
                ScreenSection includeSection = includeScreen?.getSection((String) sectionNode["@name"])
                if (includeSection == null) throw new IllegalArgumentException("Could not find section [${sectionNode["@name"]} to include at location [${sectionNode["@location"]}]")
                sectionByName.put((String) sectionNode["@name"], includeSection)

                // see if the included section contains any SECTIONS, need to reference those here too!
                for (Node inclRefNode in (Collection<Node>) includeSection.sectionNode.depthFirst()
                        .findAll({ it instanceof Node && (it.name() == "section" || it.name() == "section-iterate") })) {
                    sectionByName.put((String) inclRefNode["@name"], includeScreen.getSection((String) inclRefNode["@name"]))
                }

                // see if the included section contains any FORMS or TREES, need to reference those here too!
                for (Node formNode in (Collection<Node>) includeSection.sectionNode.depthFirst()
                        .findAll({ it instanceof Node && (it.name() == "form-single" || it.name() == "form-list") })) {
                    formByName.put((String) formNode["@name"], includeScreen.getForm((String) formNode["@name"]))
                }
                for (Node treeNode in (Collection<Node>) includeSection.sectionNode.depthFirst()
                        .findAll({ it instanceof Node && (it.name() == "tree") })) {
                    treeByName.put((String) treeNode["@name"], includeScreen.getTree((String) treeNode["@name"]))
                }
            }

            // get all forms by name
            for (Node formNode in (Collection<Node>) rootSection.widgets.widgetsNode.breadthFirst()
                    .findAll({ it instanceof Node && (it.name() == "form-single" || it.name() == "form-list") })) {
                formByName.put((String) formNode["@name"], new ScreenForm(sfi.ecfi, this, formNode, "${location}.${formNode.name().replace('-','_')}_${formNode["@name"].replace('-','_')}"))
            }

            // get all trees by name
            for (Node treeNode in (Collection<Node>) rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it instanceof Node && (it.name() == "tree") })) {
                treeByName.put((String) treeNode["@name"], new ScreenTree(sfi.ecfi, this, treeNode, "${location}.${treeNode.name().replace('-','_')}_${treeNode["@name"].replace('-','_')}"))
            }
        }

        if (logger.isTraceEnabled()) logger.trace("Loaded screen at [${location}] in [${(System.currentTimeMillis()-startTime)/1000}] seconds")
    }

    void populateSubscreens() {
        // start with file/directory structure
        String cleanLocationBase = location.substring(0, location.lastIndexOf("."))
        ResourceReference locationRef = sfi.ecfi.resourceFacade.getLocationReference(location)
        if (logger.traceEnabled) logger.trace("Finding subscreens for screen at [${locationRef}]")
        if (locationRef.supportsAll()) {
            String subscreensDirStr = locationRef.location
            subscreensDirStr = subscreensDirStr.substring(0, subscreensDirStr.lastIndexOf("."))

            ResourceReference subscreensDirRef = sfi.ecfi.resourceFacade.getLocationReference(subscreensDirStr)
            if (subscreensDirRef.exists && subscreensDirRef.isDirectory()) {
                if (logger.traceEnabled) logger.trace("Looking for subscreens in directory [${subscreensDirRef}]")
                for (ResourceReference subscreenRef in subscreensDirRef.directoryEntries) {
                    if (!subscreenRef.isFile() || !subscreenRef.location.endsWith(".xml")) continue
                    InputStream subscreenIs = subscreenRef.openStream()
                    try {
                        GPathResult subscreenRoot = new XmlSlurper().parse(subscreenIs)
                        if (subscreenRoot.name() == "screen") {
                            String ssName = subscreenRef.getFileName()
                            ssName = ssName.substring(0, ssName.lastIndexOf("."))
                            String cleanLocation = cleanLocationBase + "/" + subscreenRef.getFileName()
                            SubscreensItem si = new SubscreensItem(ssName, cleanLocation, subscreenRoot, this)
                            subscreensByName.put(si.name, si)
                            if (logger.traceEnabled) logger.trace("Added file subscreen [${si.name}] at [${si.location}] to screen [${locationRef}]")
                        }
                    } finally {
                        if (subscreenIs != null) subscreenIs.close()
                    }
                }
            }
        } else {
            logger.warn("Not getting subscreens by file/directory structure for screen [${location}] because it is not a location that supports directories")
        }

        // override dir structure with subscreens.subscreens-item elements
        for (Node subscreensItem in screenNode."subscreens"?."subscreens-item") {
            SubscreensItem si = new SubscreensItem(subscreensItem, this)
            subscreensByName.put(si.name, si)
            if (logger.traceEnabled) logger.trace("Added XML defined subscreen [${si.name}] at [${si.location}] to screen [${locationRef}]")
        }

        // override dir structure and subscreens-item elements with moqui.screen.SubscreensItem entity
        EntityFind subscreensItemFind = sfi.ecfi.entityFacade.makeFind("moqui.screen.SubscreensItem")
                .condition([screenLocation:location])
        // NOTE: this filter should NOT be done here, causes subscreen items to be filtered by first user that renders the screen, not by current user!
        // subscreensItemFind.condition("userGroupId", EntityCondition.IN, sfi.ecfi.executionContext.user.userGroupIdSet)
        EntityList subscreensItemList = subscreensItemFind.useCache(true).list()
        for (EntityValue subscreensItem in subscreensItemList) {
            SubscreensItem si = new SubscreensItem(subscreensItem, this)
            subscreensByName.put(si.name, si)
            if (logger.traceEnabled) logger.trace("Added database subscreen [${si.name}] at [${si.location}] to screen [${locationRef}]")
        }
    }

    Node getScreenNode() { return screenNode }
    Node getWebSettingsNode() { return screenNode."web-settings"[0] }
    String getLocation() { return location }
    Set<String> getTenantsAllowed() { return tenantsAllowed }

    String getScreenName() {
        String filename = location.contains("/") ? location.substring(location.lastIndexOf("/")+1) : location
        return filename.contains(".") ? filename.substring(0, filename.indexOf(".")) : filename
    }

    String getDefaultMenuName() {
        if (screenNode."@default-menu-title") return screenNode."@default-menu-title"

        String filename = location.substring(location.lastIndexOf("/")+1, location.length()-4)
        StringBuilder prettyName = new StringBuilder()
        for (String part in filename.split("(?=[A-Z])")) {
            if (prettyName) prettyName.append(" ")
            prettyName.append(part)
        }
        if (prettyName.charAt(0).isLowerCase()) prettyName.setCharAt(0, prettyName.charAt(0).toUpperCase())
        return prettyName.toString()
    }

    Map<String, ParameterItem> getParameterMap() { return parameterByName }

    TransitionItem getTransitionItem(String name, String method) {
        method = method ? method.toLowerCase() : ""
        TransitionItem ti = (TransitionItem) transitionByName.get(name + "#" + method)
        // if no ti, try by name only which will catch transitions with "any" or empty method
        if (ti == null) ti = (TransitionItem) transitionByName.get(name)
        // still none? try each one to see if it matches as a regular expression (first one to match wins)
        if (ti == null) for (TransitionItem curTi in transitionByName.values()) {
            if (method && curTi.method && (curTi.method == "any" || curTi.method == method)) {
                if (name == curTi.name) { ti = curTi; break }
                if (name.matches(curTi.name)) { ti = curTi; break }
            }
            // logger.info("In getTransitionItem() transition with name [${curTi.name}] method [${curTi.method}] did not match name [${name}] method [${method}]")
        }
        return ti
    }

    SubscreensItem getSubscreensItem(String name) { return (SubscreensItem) subscreensByName.get(name) }

    List<SubscreensItem> getSubscreensItemsSorted() {
        if (subscreensItemsSorted != null) return subscreensItemsSorted
        List<SubscreensItem> newList = new ArrayList(subscreensByName.size())
        if (subscreensByName.size() == 0) return newList
        newList.addAll(subscreensByName.values())
        Collections.sort(newList, new SubscreensItemComparator())
        return subscreensItemsSorted = newList
    }

    List<SubscreensItem> getMenuSubscreensItems() {
        List<SubscreensItem> allItems = getSubscreensItemsSorted()
        List<SubscreensItem> filteredList = new ArrayList(allItems.size())

        for (SubscreensItem si in allItems) {
            // check the menu include flag
            if (!si.getMenuInclude()) continue
            // if the subscreens item is limited to a UserGroup make sure user is in that group
            if (si.getUserGroupId() && !(si.getUserGroupId() in sfi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet())) continue
            // made it through the checks? add it in...
            filteredList.add(si)
        }

        return filteredList
    }

    ScreenSection getRootSection() { return rootSection }
    void render(ScreenRenderImpl sri, boolean isTargetScreen) {
        // NOTE: don't require authz if the screen doesn't require auth
        sri.ec.artifactExecution.push(new ArtifactExecutionInfoImpl(location, "AT_XML_SCREEN", "AUTHZA_VIEW"),
                isTargetScreen ? (!screenNode."@require-authentication" || screenNode."@require-authentication" == "true") : false)

        boolean loggedInAnonymous = false
        if (screenNode."@require-authentication" == "anonymous-all") {
            sri.ec.artifactExecution.setAnonymousAuthorizedAll()
            loggedInAnonymous = sri.ec.getUser().loginAnonymousIfNoUser()
        } else if (screenNode."@require-authentication" == "anonymous-view") {
            sri.ec.artifactExecution.setAnonymousAuthorizedView()
            loggedInAnonymous = sri.ec.getUser().loginAnonymousIfNoUser()
        }

        rootSection.render(sri)

        // all done so pop the artifact info; don't bother making sure this is done on errors/etc like in a finally clause because if there is an error this will help us know how we got there
        sri.ec.artifactExecution.pop()
        if (loggedInAnonymous) sri.ec.getUser().logoutAnonymousOnly()
    }

    ScreenSection getSection(String sectionName) {
        ScreenSection ss = sectionByName.get(sectionName)
        if (ss == null) throw new IllegalArgumentException("Could not find form [${sectionName}] in screen: ${getLocation()}")
        return ss
    }
    ScreenForm getForm(String formName) {
        ScreenForm sf = formByName.get(formName)
        if (sf == null) throw new IllegalArgumentException("Could not find form [${formName}] in screen: ${getLocation()}")
        return sf
    }
    ScreenTree getTree(String treeName) {
        ScreenTree st = treeByName.get(treeName)
        if (st == null) throw new IllegalArgumentException("Could not find tree [${treeName}] in screen: ${getLocation()}")
        return st
    }

    ResourceReference getSubContentRef(List<String> pathNameList) {
        StringBuilder pathNameBldr = new StringBuilder()
        // add the path elements that remain
        for (String rp in pathNameList) pathNameBldr.append("/").append(rp)
        String pathName = pathNameBldr.toString()

        ResourceReference contentRef = subContentRefByPath.get(pathName)
        if (contentRef) return contentRef

        ResourceReference lastScreenRef = sfi.ecfi.resourceFacade.getLocationReference(location)
        if (lastScreenRef.supportsAll()) {
            // NOTE: this caches internally so consider getting rid of subContentRefByPath
            contentRef = lastScreenRef.findChildFile(pathName)
        } else {
            logger.warn("Not looking for sub-content [${pathName}] under screen [${location}] because screen location does not support exists, isFile, etc")
        }

        if (contentRef) subContentRefByPath.put(pathName, contentRef)
        return contentRef
    }

    @Override
    String toString() { return location }

    static class ParameterItem {
        protected String name
        protected Class fromFieldGroovy = null
        protected Class valueGroovy = null
        ParameterItem(Node parameterNode, String location) {
            this.name = parameterNode."@name"

            if (parameterNode."@from") fromFieldGroovy = new GroovyClassLoader().parseClass(
                    (String) parameterNode."@from", StupidUtilities.cleanStringForJavaName("${location}.parameter_${name}.from_field"))
            if (parameterNode."@value" != null) valueGroovy = new GroovyClassLoader().parseClass(
                    ('"""' + (String) parameterNode."@value" + '"""'), StupidUtilities.cleanStringForJavaName("${location}.parameter_${name}.value"))
        }
        String getName() { return name }
        Object getValue(ExecutionContext ec) {
            Object value = null
            if (fromFieldGroovy != null) {
                value = InvokerHelper.createScript(fromFieldGroovy, new ContextBinding(ec.context)).run()
            }
            if (valueGroovy != null && !value) {
                value = InvokerHelper.createScript(valueGroovy, new ContextBinding(ec.context)).run()
            }
            if (value == null) value = ec.context.get(name)
            if (value == null && ec.web) value = ec.web.parameters.get(name)
            return value
        }
    }

    static class TransitionItem {
        protected ScreenDefinition parentScreen

        protected String name
        protected String method
        protected String location
        protected XmlAction condition = null
        protected XmlAction actions = null
        protected String singleServiceName = null

        protected Map<String, ParameterItem> parameterByName = new HashMap()
        protected List<String> pathParameterList = null

        protected List<ResponseItem> conditionalResponseList = new ArrayList<ResponseItem>()
        protected ResponseItem defaultResponse = null
        protected ResponseItem errorResponse = null

        protected boolean beginTransaction = true
        protected boolean readOnly = false

        TransitionItem(Node transitionNode, ScreenDefinition parentScreen) {
            this.parentScreen = parentScreen
            name = transitionNode."@name"
            method = transitionNode."@method" ?: "any"
            location = "${parentScreen.location}.transition_${StupidUtilities.cleanStringForJavaName(name)}"
            beginTransaction = transitionNode."@begin-transaction" != "false"
            readOnly = transitionNode."@read-only" == "true"

            // parameter
            for (Node parameterNode in transitionNode."parameter")
                parameterByName.put((String) parameterNode."@name", new ParameterItem(parameterNode, location))
            // path-parameter
            if (transitionNode."path-parameter") {
                pathParameterList = new ArrayList()
                for (Node pathParameterNode in transitionNode."path-parameter") pathParameterList.add(pathParameterNode."@name")
            }

            // condition
            if (transitionNode.condition?.getAt(0)?.children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(parentScreen.sfi.ecfi, (Node) transitionNode.condition[0].children()[0], location + ".condition")
            }
            // service OR actions
            if (transitionNode."service-call") {
                Node callServiceNode = (Node) transitionNode."service-call"[0]
                if (!callServiceNode."@in-map") callServiceNode.attributes().put("in-map", "true")
                if (!callServiceNode."@out-map") callServiceNode.attributes().put("out-map", "context")
                if (!callServiceNode."@multi") callServiceNode.attributes().put("multi", "parameter")
                actions = new XmlAction(parentScreen.sfi.ecfi, callServiceNode, location + ".service_call")
                singleServiceName = callServiceNode."@name"
            } else if (transitionNode.actions) {
                actions = new XmlAction(parentScreen.sfi.ecfi, (Node) transitionNode."actions"[0], location + ".actions")
            }

            // conditional-response*
            for (Node condResponseNode in transitionNode."conditional-response")
                conditionalResponseList.add(new ResponseItem(condResponseNode, this, parentScreen))
            // default-response
            defaultResponse = new ResponseItem((Node) transitionNode."default-response"[0], this, parentScreen)
            // error-response
            if (transitionNode."error-response")
                errorResponse = new ResponseItem((Node) transitionNode."error-response"[0], this, parentScreen)
        }

        String getName() { return name }
        String getMethod() { return method }
        String getSingleServiceName() { return singleServiceName }
        List<String> getPathParameterList() { return pathParameterList }
        Map<String, ParameterItem> getParameterMap() { return parameterByName }
        boolean hasActionsOrSingleService() { return actions != null }
        boolean getBeginTransaction() { return beginTransaction }
        boolean isReadOnly() { return readOnly }

        boolean checkCondition(ExecutionContext ec) { return condition ? condition.checkCondition(ec) : true }

        void setAllParameters(ScreenUrlInfo screenUrlInfo, ExecutionContext ec) {
            // get the path parameters
            if (screenUrlInfo.getExtraPathNameList() && getPathParameterList()) {
                List<String> pathParameterList = getPathParameterList()
                int i = 0
                for (String extraPathName in screenUrlInfo.getExtraPathNameList()) {
                    if (pathParameterList.size() > i) {
                        if (ec.getWeb()) ec.getWeb().addDeclaredPathParameter(pathParameterList.get(i), extraPathName)
                        ec.getContext().put(pathParameterList.get(i), extraPathName)
                        i++
                    } else {
                        break
                    }
                }
            }

            // put parameters in the context
            if (ec.getWeb()) {
                // screen parameters
                for (ParameterItem pi in parentScreen.getParameterMap().values()) {
                    Object value = pi.getValue(ec)
                    if (value != null) ec.getContext().put(pi.getName(), value)
                }
                // transition parameters
                for (ParameterItem pi in parameterByName.values()) {
                    Object value = pi.getValue(ec)
                    if (value != null) ec.getContext().put(pi.getName(), value)
                }
            }
        }

        ResponseItem run(ScreenRenderImpl sri) {
            ExecutionContext ec = sri.getEc()

            // NOTE: if parent screen of transition does not require auth, don't require authz
            // NOTE: use the View authz action to leave it open, ie require minimal authz; restrictions are often more
            //    in the services/etc if/when needed, or specific transitions can have authz settings
            ec.getArtifactExecution().push(new ArtifactExecutionInfoImpl(location,
                    "AT_XML_SCREEN_TRANS", "AUTHZA_VIEW"),
                    (!parentScreen.screenNode."@require-authentication" ||
                     parentScreen.screenNode."@require-authentication" == "true"))

            boolean loggedInAnonymous = false
            if (parentScreen.screenNode."@require-authentication" == "anonymous-all") {
                ec.artifactExecution.setAnonymousAuthorizedAll()
                loggedInAnonymous = ec.getUser().loginAnonymousIfNoUser()
            } else if (parentScreen.screenNode."@require-authentication" == "anonymous-view") {
                ec.artifactExecution.setAnonymousAuthorizedView()
                loggedInAnonymous = ec.getUser().loginAnonymousIfNoUser()
            }

            ScreenUrlInfo screenUrlInfo = sri.getScreenUrlInfo()
            setAllParameters(screenUrlInfo, ec)

            if (!checkCondition(ec)) {
                sri.ec.message.addError("Condition failed for transition [${location}], not running actions or redirecting")
                if (errorResponse) return errorResponse
                return defaultResponse
            }

            // don't push a map on the context, let the transition actions set things that will remain: sri.ec.context.push()
            ec.getContext().put("sri", sri)
            if (actions != null) actions.run(ec)

            ResponseItem ri = null
            // if there is an error-response and there are errors, we have a winner
            if (ec.getMessage().hasError() && errorResponse) ri = errorResponse

            // check all conditional-response, if condition then return that response
            if (ri == null) for (ResponseItem condResp in conditionalResponseList) {
                if (condResp.checkCondition(ec)) ri = condResp
            }
            // no errors, no conditionals, return default
            if (ri == null) ri = defaultResponse

            // don't pop the context until after evaluating conditions so that data set in the actions can be used
            // don't pop the context at all, see note above about push: sri.ec.context.pop()

            // all done so pop the artifact info; don't bother making sure this is done on errors/etc like in a finally
            // clause because if there is an error this will help us know how we got there
            ec.getArtifactExecution().pop()
            if (loggedInAnonymous) ec.getUser().logoutAnonymousOnly()

            return ri
        }
    }

    static class ResponseItem {
        protected TransitionItem transitionItem
        protected ScreenDefinition parentScreen
        protected XmlAction condition = null
        protected Map<String, ParameterItem> parameterMap = new HashMap()

        protected String type
        protected String url
        protected String urlType
        protected Class parameterMapNameGroovy = null
        // deferred for future version: protected boolean saveLastScreen
        protected boolean saveCurrentScreen
        protected boolean saveParameters

        ResponseItem(Node responseNode, TransitionItem ti, ScreenDefinition parentScreen) {
            this.transitionItem = ti
            this.parentScreen = parentScreen
            String location = "${parentScreen.location}.transition_${ti.name}.${responseNode.name().replace("-","_")}"
            if (responseNode."condition" && responseNode."condition"[0].children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(parentScreen.sfi.ecfi, (Node) responseNode."condition"[0].children()[0],
                        location + ".condition")
            }

            type = responseNode."@type" ?: "url"
            url = responseNode."@url"
            urlType = responseNode."@url-type" ?: "screen-path"
            if (responseNode."@parameter-map") parameterMapNameGroovy = new GroovyClassLoader().parseClass(
                    (String) responseNode."@parameter-map", "${location}.parameter_map")
            // deferred for future version: saveLastScreen = responseNode."@save-last-screen" == "true"
            saveCurrentScreen = responseNode."@save-current-screen" == "true"
            saveParameters = responseNode."@save-parameters" == "true"

            for (Node parameterNode in responseNode."parameter")
                parameterMap.put((String) parameterNode."@name", new ParameterItem(parameterNode, location))
        }

        boolean checkCondition(ExecutionContext ec) { return condition ? condition.checkCondition(ec) : true }

        String getType() { return type }
        String getUrl() { return parentScreen.sfi.ecfi.resourceFacade.evaluateStringExpand(url, "") }
        String getUrlType() { return urlType }
        // deferred for future version: boolean getSaveLastScreen() { return saveLastScreen }
        boolean getSaveCurrentScreen() { return saveCurrentScreen }
        boolean getSaveParameters() { return saveParameters }

        Map expandParameters(ScreenUrlInfo screenUrlInfo, ExecutionContext ec) {
            transitionItem.setAllParameters(screenUrlInfo, ec)

            Map ep = new HashMap()
            for (ParameterItem pi in parameterMap.values()) ep.put(pi.getName(), pi.getValue(ec))
            if (parameterMapNameGroovy != null) {
                Object pm = InvokerHelper.createScript(parameterMapNameGroovy, new ContextBinding(ec.getContext())).run()
                if (pm && pm instanceof Map) ep.putAll(pm)
            }
            // logger.warn("========== Expanded response map to url [${url}] to: ${ep}; parameterMap=${parameterMap}; parameterMapNameGroovy=[${parameterMapNameGroovy}]")
            return ep
        }
    }

    static class SubscreensItem {
        protected ScreenDefinition parentScreen
        protected String name
        protected String location
        protected String menuTitle
        protected int menuIndex
        protected boolean menuInclude
        protected Class disableWhenGroovy = null
        protected String userGroupId = null

        SubscreensItem(String name, String location, GPathResult screen, ScreenDefinition parentScreen) {
            this.parentScreen = parentScreen
            this.name = name
            this.location = location
            menuTitle = screen."@default-menu-title" as String ?: getDefaultTitle()
            menuIndex = (screen."@default-menu-index" as String ?: "5") as Integer
            menuInclude = (!screen."@default-menu-include"?.getAt(0) || screen."@default-menu-include"[0] == "true")
        }

        SubscreensItem(Node subscreensItem, ScreenDefinition parentScreen) {
            this.parentScreen = parentScreen
            name = subscreensItem."@name"
            location = subscreensItem."@location"
            menuTitle = subscreensItem."@menu-title" as String ?: getDefaultTitle()
            menuIndex = (subscreensItem."@menu-index" as String ?: "5") as int
            menuInclude = (!subscreensItem."@menu-include"?.getAt(0) || subscreensItem."@menu-include"[0] == "true")

            if (subscreensItem."@disable-when") disableWhenGroovy = new GroovyClassLoader().parseClass(
                    (String) subscreensItem."@disable-when", "${parentScreen.location}.subscreens_item_${name}.disable_when")
        }

        SubscreensItem(EntityValue subscreensItem, ScreenDefinition parentScreen) {
            this.parentScreen = parentScreen
            name = subscreensItem.subscreenName
            location = subscreensItem.subscreenLocation
            menuTitle = subscreensItem.menuTitle ?: getDefaultTitle()
            menuIndex = (subscreensItem.menuIndex ?: 5) as int
            menuInclude = (subscreensItem.menuInclude == "Y")
            userGroupId = subscreensItem.userGroupId
        }

        String getDefaultTitle() {
            ScreenDefinition sd = parentScreen.sfi.getScreenDefinition(location)
            if (sd != null) {
                return sd.getDefaultMenuName()
            } else {
                return location.substring(location.lastIndexOf("/")+1, location.length()-4)
            }
        }

        String getName() { return name }
        String getLocation() { return location }
        String getMenuTitle() { return menuTitle }
        int getMenuIndex() { return menuIndex }
        boolean getMenuInclude() { return menuInclude }
        boolean getDisable(ExecutionContext ec) {
            if (!disableWhenGroovy) return false
            return InvokerHelper.createScript(disableWhenGroovy, new ContextBinding(ec.context)).run() as boolean
        }
        String getUserGroupId() { return userGroupId }
    }

    static class SubscreensItemComparator implements Comparator<SubscreensItem> {
        public SubscreensItemComparator() { }
        @Override
        public int compare(SubscreensItem ssi1, SubscreensItem ssi2) {
            // order by index
            int indexComp = ssi1.menuIndex.compareTo(ssi2.menuIndex)
            if (indexComp != 0) return indexComp
            // if index is the same, order by title
            return ssi1.menuTitle.compareTo(ssi2.menuTitle)
        }
    }
}
