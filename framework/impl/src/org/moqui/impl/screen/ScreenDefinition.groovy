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
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import groovy.util.slurpersupport.GPathResult
import org.moqui.impl.actions.XmlAction
import org.moqui.context.ResourceReference
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.StupidUtilities

class ScreenDefinition {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScreenDefinition.class)

    protected final ScreenFacadeImpl sfi
    protected final Node screenNode
    protected final String location

    protected Map<String, ParameterItem> parameterByName = new HashMap()
    protected Map<String, TransitionItem> transitionByName = new HashMap()
    protected Map<String, SubscreensItem> subscreensByName = new HashMap()

    protected XmlAction preActions = null

    protected ScreenSection rootSection = null
    protected Map<String, ScreenSection> sectionByName = new HashMap()
    protected Map<String, ScreenForm> formByName = new HashMap()

    protected Map<String, ResourceReference> subContentRefByPath = new HashMap()

    ScreenDefinition(ScreenFacadeImpl sfi, Node screenNode, String location) {
        this.sfi = sfi
        this.screenNode = screenNode
        this.location = location

        long startTime = System.currentTimeMillis()

        // parameter
        for (Node parameterNode in screenNode."parameter")
            parameterByName.put(parameterNode."@name", new ParameterItem(parameterNode, location))
        // transition
        for (Node transitionNode in screenNode."transition") {
            TransitionItem ti = new TransitionItem(transitionNode, this)
            transitionByName.put(ti.method == "any" ? ti.name : ti.name + "#" + ti.method, ti)
        }
        // subscreens
        populateSubscreens()
        // prep pre-actions
        if (screenNode."pre-actions") {
            preActions = new XmlAction(sfi.ecfi, (Node) screenNode."pre-actions"[0], location + ".pre_actions")
        }

        // get the root section
        rootSection = new ScreenSection(sfi.ecfi, screenNode, location + ".screen")

        if (rootSection && rootSection.widgets) {
            // get all of the other sections by name
            for (Node sectionNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "section" || it.name() == "section-iterate" })) {
                sectionByName.put((String) sectionNode["@name"], new ScreenSection(sfi.ecfi, sectionNode, "${location}.${sectionNode.name().replace('-','_')}_${sectionNode["@name"].replace('-','_')}"))
            }

            // get all forms by name
            for (Node formNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "form-single" || it.name() == "form-list" })) {
                formByName.put((String) formNode["@name"], new ScreenForm(sfi.ecfi, this, formNode, "${location}.${formNode.name().replace('-','_')}_${formNode["@name"].replace('-','_')}"))
            }
        }

        if (logger.infoEnabled) logger.info("Loaded screen at [${location}] in [${(System.currentTimeMillis()-startTime)/1000}] seconds")
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
                            SubscreensItem si = new SubscreensItem(ssName, cleanLocation, subscreenRoot)
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

        // override dir structure and subscreens-item elements with SubscreensItem entity
        EntityList subscreensItemList = sfi.ecfi.entityFacade.makeFind("SubscreensItem")
                .condition([screenLocation:location, userId:"_NA_"]).useCache(true).list()
        for (EntityValue subscreensItem in subscreensItemList) {
            SubscreensItem si = new SubscreensItem(subscreensItem)
            subscreensByName.put(si.name, si)
            if (logger.traceEnabled) logger.trace("Added database subscreen [${si.name}] at [${si.location}] to screen [${locationRef}]")
        }
        // override rest with SubscreensItem entity with userId of current user
        if (sfi.ecfi.executionContext.user.userId) {
            EntityList userSubscreensItemList = sfi.ecfi.entityFacade.makeFind("SubscreensItem")
                    .condition([screenLocation:location, userId:sfi.ecfi.executionContext.user.userId]).useCache(true).list()
            for (EntityValue subscreensItem in userSubscreensItemList) {
                SubscreensItem si = new SubscreensItem(subscreensItem)
                subscreensByName.put(si.name, si)
                if (logger.traceEnabled) logger.trace("Added user-specific database subscreen [${si.name}] at [${si.location}] to screen [${locationRef}]")
            }
        }
    }

    Node getScreenNode() { return screenNode }

    Node getWebSettingsNode() { return screenNode."web-settings"[0] }

    String getLocation() { return location }

    String getDefaultMenuName() {
        return screenNode."@default-menu-title" ?: location.substring(location.lastIndexOf("/")+1, location.length()-4)
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
        List<SubscreensItem> newList = new ArrayList(subscreensByName.size())
        if (subscreensByName.size() == 0) return newList
        newList.addAll(subscreensByName.values())
        Collections.sort(newList, new SubscreensItemComparator())
        return newList
    }

    ScreenSection getRootSection() { return rootSection }
    void render(ScreenRenderImpl sri, boolean isTargetScreen) {
        // NOTE: don't require authz if the screen doesn't require auth
        sri.ec.artifactExecution.push(new ArtifactExecutionInfoImpl(location, "AT_XML_SCREEN", "AUTHZA_VIEW"),
                isTargetScreen ? screenNode."@require-authentication" != "false" : false)
        rootSection.render(sri)
        // all done so pop the artifact info; don't bother making sure this is done on errors/etc like in a finally clause because if there is an error this will help us know how we got there
        sri.ec.artifactExecution.pop()
    }

    ScreenSection getSection(String sectionName) { return (ScreenSection) sectionByName.get(sectionName) }

    ScreenForm getForm(String formName) { return (ScreenForm) formByName.get(formName) }

    ResourceReference getSubContentRef(List<String> pathNameList) {
        StringBuilder pathNameBldr = new StringBuilder()
        // add the path elements that remain
        for (String rp in pathNameList) pathNameBldr.append("/").append(rp)
        String pathName = pathNameBldr.toString()

        ResourceReference contentRef = subContentRefByPath.get(pathName)
        if (contentRef) return contentRef

        ResourceReference lastScreenRef = sfi.ecfi.resourceFacade.getLocationReference(location)
        if (lastScreenRef.supportsAll()) {
            StringBuilder fileLoc = new StringBuilder(lastScreenRef.location)
            // get rid of the prefix, before the ":"
            // we probably shouldn't do this, causes problems on Windows and shouldn't be needed: fileLoc.delete(0, fileLoc.indexOf(":")+1)
            // get rid of the suffix, probably .xml but use .*
            if (fileLoc.lastIndexOf(".") > 0) fileLoc.delete(fileLoc.lastIndexOf("."), fileLoc.length())
            fileLoc.append(pathName)

            ResourceReference theFile = sfi.ecfi.resourceFacade.getLocationReference(fileLoc.toString())
            if (theFile.exists && theFile.isFile()) contentRef = theFile

            for (String extToTry in sfi.ecfi.resourceFacade.templateRenderers.keySet()) {
                if (contentRef != null) break
                theFile = sfi.ecfi.resourceFacade.getLocationReference(fileLoc.toString() + extToTry)
                if (theFile.exists && theFile.isFile()) contentRef = theFile
            }
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
                    (String) parameterNode."@from", "${location}.parameter_${name}.from_field")
            if (parameterNode."@value") fromFieldGroovy = new GroovyClassLoader().parseClass(
                    ('"""' + (String) parameterNode."@value" + '"""'), "${location}.parameter_${name}.value")
        }
        String getName() { return name }
        Object getValue(ExecutionContext ec) {
            Object value = null
            if (fromFieldGroovy) {
                value = InvokerHelper.createScript(fromFieldGroovy, new Binding(ec.context)).run()
            }
            if (valueGroovy && !value) {
                value = InvokerHelper.createScript(valueGroovy, new Binding(ec.context)).run()
            }
            if (!value) value = ec.context.get(name)
            if (!value && ec.web) value = ec.web.parameters.get(name)
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

        protected List<ResponseItem> conditionalResponseList = new ArrayList<ResponseItem>()
        protected ResponseItem defaultResponse = null
        protected ResponseItem errorResponse = null

        TransitionItem(Node transitionNode, ScreenDefinition parentScreen) {
            this.parentScreen = parentScreen
            name = transitionNode."@name"
            method = transitionNode."@method" ?: "any"
            location = "${parentScreen.location}.transition_${StupidUtilities.cleanStringForJavaName(name)}"
            // condition
            if (transitionNode.condition?.getAt(0)?.children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(parentScreen.sfi.ecfi, (Node) transitionNode.condition[0].children()[0], location + ".condition")
            }
            // service OR actions
            if (transitionNode."service-call") {
                Node callServiceNode = (Node) transitionNode."service-call"[0]
                if (!callServiceNode."@in-map") callServiceNode.attributes().put("in-map", "true")
                if (!callServiceNode."@out-map") callServiceNode.attributes().put("out-map", "ec.web.requestAttributes")
                actions = new XmlAction(parentScreen.sfi.ecfi, callServiceNode, location + ".service_call")
            } else if (transitionNode.actions) {
                actions = new XmlAction(parentScreen.sfi.ecfi, (Node) transitionNode."actions"[0], location + ".actions")
            }

            // conditional-response*
            for (Node condResponseNode in transitionNode."conditional-response")
                conditionalResponseList.add(new ResponseItem(condResponseNode, this, parentScreen))
            // default-response
            defaultResponse = new ResponseItem(transitionNode."default-response"[0], this, parentScreen)
            // error-response
            if (transitionNode."error-response")
                errorResponse = new ResponseItem(transitionNode."error-response"[0], this, parentScreen)
        }

        String getName() { return name }
        String getMethod() { return method }

        boolean checkCondition(ExecutionContext ec) { return condition ? condition.checkCondition(ec) : true }

        ResponseItem run(ScreenRenderImpl sri) {
            // NOTE: if parent screen of transition does not require auth, don't require authz
            // NOTE: use the View authz action to leave it open, ie require minimal authz; restrictions are often more
            //    in the services/etc if/when needed, or specific transitions can have authz settings
            sri.ec.artifactExecution.push(new ArtifactExecutionInfoImpl(location,
                    "AT_XML_SCREEN_TRANS", "AUTHZA_VIEW"),
                    parentScreen.screenNode."@require-authentication" != "false")

            // put parameters in the context
            if (sri.ec.web) {
                for (ParameterItem pi in parentScreen.parameterMap.values()) {
                    Object value = pi.getValue(sri.ec)
                    if (value) sri.ec.context.put(pi.getName(), value)
                }
            }

            if (!checkCondition(sri.ec)) {
                sri.ec.message.addError("Condition failed for transition [${location}], not running actions or redirecting")
                if (errorResponse) return errorResponse
                return defaultResponse
            }

            sri.ec.context.push()
            sri.ec.context.put("sri", sri)
            if (actions) actions.run(sri.ec)

            ResponseItem ri = null
            // if there is an error-response and there are errors, we have a winner
            if (sri.ec.message.errors && errorResponse) ri = errorResponse

            // check all conditional-response, if condition then return that response
            if (ri == null) for (ResponseItem condResp in conditionalResponseList) {
                if (condResp.checkCondition(sri.ec)) ri = condResp
            }
            // no errors, no conditionals, return default
            if (ri == null) ri = defaultResponse

            // don't pop the context until after evaluating conditions so that data set in the actions can be used
            sri.ec.context.pop()

            // all done so pop the artifact info; don't bother making sure this is done on errors/etc like in a finally
            // clause because if there is an error this will help us know how we got there
            sri.ec.artifactExecution.pop()

            return ri
        }
    }

    static class ResponseItem {
        protected XmlAction condition = null
        protected Map<String, ParameterItem> parameterMap = new HashMap()

        protected String type
        protected String url
        protected String urlType
        protected Class parameterMapNameGroovy = null
        // deferred for future version: protected boolean saveLastScreen
        protected boolean saveCurrentScreen

        ResponseItem(Node responseNode, TransitionItem ti, ScreenDefinition parentScreen) {
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

            for (Node parameterNode in responseNode."parameter")
                parameterMap.put(parameterNode."@name", new ParameterItem(parameterNode, location))
        }

        boolean checkCondition(ExecutionContext ec) { return condition ? condition.checkCondition(ec) : true }

        String getType() { return type }
        String getUrl() { return url }
        String getUrlType() { return urlType }
        boolean getSaveLastScreen() { return saveLastScreen }
        boolean getSaveCurrentScreen() { return saveCurrentScreen }

        Map expandParameters(ExecutionContext ec) {
            Map ep = new HashMap()
            for (ParameterItem pi in parameterMap.values()) ep.put(pi.name, pi.getValue(ec))
            if (parameterMapNameGroovy != null) {
                Object pm = InvokerHelper.createScript(parameterMapNameGroovy, new Binding(ec.context)).run()
                if (pm && pm instanceof Map) ep.putAll(pm)
            }
            // logger.info("Expanded response map to url [${url}] to: ${ep}; parameterMapNameGroovy=[${parameterMapNameGroovy}]")
            return ep
        }
    }

    static class SubscreensItem {
        protected String name
        protected String location
        protected String menuTitle
        protected int menuIndex
        protected boolean menuInclude
        protected Class disableWhenGroovy = null

        SubscreensItem(String name, String location, GPathResult screen) {
            this.name = name
            this.location = location
            menuTitle = screen."@default-menu-title"[0]
            menuIndex = (screen."@default-menu-index"[0] as String ?: "5") as Integer
            menuInclude = (!screen."@default-menu-include"?.getAt(0) || screen."@default-menu-include"[0] == "true")
        }

        SubscreensItem(Node subscreensItem, ScreenDefinition parentScreen) {
            name = subscreensItem."@name"
            location = subscreensItem."@location"
            menuTitle = subscreensItem."@menu-title"
            menuIndex = (subscreensItem."@menu-index" ?: "5") as int
            menuInclude = (!subscreensItem."@menu-include"?.getAt(0) || subscreensItem."@menu-include"[0] == "true")

            if (subscreensItem."@disable-when") disableWhenGroovy = new GroovyClassLoader().parseClass(
                    (String) subscreensItem."@disable-when", "${parentScreen.location}.subscreens_item[${name}].disable_when")
        }

        SubscreensItem(EntityValue subscreensItem) {
            name = subscreensItem.subscreenName
            location = subscreensItem.subscreenLocation
            menuTitle = subscreensItem.menuTitle
            menuIndex = subscreensItem.menuIndex as int
            menuInclude = (subscreensItem.menuInclude == "Y")
        }

        String getName() { return name }
        String getLocation() { return location }
        String getMenuTitle() { return menuTitle }
        int getMenuIndex() { return menuIndex }
        boolean getMenuInclude() { return menuInclude }
        boolean getDisable(ExecutionContext ec) {
            if (!disableWhenGroovy) return false
            return InvokerHelper.createScript(disableWhenGroovy, new Binding(ec.context)).run() as boolean
        }
    }

    static class SubscreensItemComparator implements Comparator<SubscreensItem> {
        public MapOrderByComparator() { }
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
