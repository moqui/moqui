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
import org.moqui.context.WebExecutionContext

class ScreenDefinition {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScreenDefinition.class)

    protected static final List<String> defaultExtensions = [".ftl", ".cwiki", ".cwiki.ftl", ".html", ".html.ftl"]

    protected final ScreenFacadeImpl sfi
    protected final Node screenNode
    final String location

    protected Map<String, ParameterItem> parameterByName = new HashMap()

    protected Map<String, TransitionItem> transitionByName = new HashMap()

    protected Map<String, SubscreensItem> subscreensByName = new HashMap()

    protected ScreenSection rootSection = null
    protected Map<String, ScreenSection> sectionByName = new HashMap()
    protected Map<String, ScreenForm> formByName = new HashMap()

    protected Map<String, URL> subContentUrlByPath = new HashMap()

    ScreenDefinition(ScreenFacadeImpl sfi, Node screenNode, String location) {
        this.sfi = sfi
        this.screenNode = screenNode
        this.location = location

        // parameter
        for (Node parameterNode in screenNode."parameter")
            parameterByName.put(parameterNode."@name", new ParameterItem(parameterNode, location))
        // transition
        for (Node transitionNode in screenNode."transition")
            transitionByName.put(transitionNode."@name", new TransitionItem(transitionNode, this))
        // subscreens
        populateSubscreens()

        // get the root section
        rootSection = new ScreenSection(sfi.ecfi, screenNode, location + ".screen")

        if (rootSection && rootSection.widgets) {
            // get all of the other sections by name
            for (Node sectionNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "section" || it.name() == "section-iterate" })) {
                sectionByName.put((String) sectionNode["@name"], new ScreenSection(sfi.ecfi, sectionNode, "${location}.${sectionNode.name().replace('-','_')}_${sectionNode["@name"]}"))
            }

            // get all forms by name
            for (Node formNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "form-single" || it.name() == "form-list" })) {
                formByName.put((String) formNode["@name"], new ScreenForm(sfi.ecfi, formNode, "${location}.${formNode.name().replace('-','_')}_${formNode["@name"]}"))
            }
        }
    }

    void populateSubscreens() {
        // start with file/directory structure
        URL locationUrl = sfi.ecfi.resourceFacade.getLocationUrl(location)
        logger.info("Finding subscreens for screen at [${locationUrl}]")
        if (locationUrl.getProtocol() == "file") {
            String subscreensDirStr = locationUrl.toString()
            subscreensDirStr = subscreensDirStr.substring(subscreensDirStr.indexOf(":")+1, subscreensDirStr.lastIndexOf("."))
            File subscreensDir = new File(subscreensDirStr)
            if (subscreensDir.exists() && subscreensDir.isDirectory()) {
                logger.info("Looking for subscreens in directory [${subscreensDir.toURI()}]")
                for (File subscreenFile in subscreensDir.listFiles()) {
                    if (!subscreenFile.isFile() || !subscreenFile.getName().endsWith(".xml")) continue
                    GPathResult subscreenRoot = new XmlSlurper().parse(subscreenFile)
                    if (subscreenRoot.name() == "screen") {
                        String ssName = subscreenFile.getName()
                        ssName = ssName.substring(0, ssName.lastIndexOf("."))
                        SubscreensItem si = new SubscreensItem(ssName, subscreenFile.toURI().toString(), subscreenRoot)
                        subscreensByName.put(si.name, si)
                        logger.info("Added file subscreen [${si.name}] at [${si.location}] to screen [${locationUrl}]")
                    }
                }
            }
        } else {
            logger.warn("Not getting subscreens by file/directory structure for screen [${location}] because it is not a file location")
        }

        // override dir structure with subscreens.subscreens-item elements
        for (Node subscreensItem in screenNode."subscreens"?."subscreens-item") {
            SubscreensItem si = new SubscreensItem(subscreensItem, this)
            subscreensByName.put(si.name, si)
            logger.info("Added file subscreen [${si.name}] at [${si.location}] to screen [${locationUrl}]")
        }

        // override dir structure and subscreens-item elements with SubscreensItem entity
        EntityList subscreensItemList = sfi.ecfi.entityFacade.makeFind("SubscreensItem")
                .condition([screenLocation:location, userId:"_NA_"]).useCache(true).list()
        for (EntityValue subscreensItem in subscreensItemList) {
            SubscreensItem si = new SubscreensItem(subscreensItem)
            subscreensByName.put(si.name, si)
            logger.info("Added file subscreen [${si.name}] at [${si.location}] to screen [${locationUrl}]")
        }
        // override rest with SubscreensItem entity with userId of current user
        if (sfi.ecfi.executionContext.user.userId) {
            EntityList userSubscreensItemList = sfi.ecfi.entityFacade.makeFind("SubscreensItem")
                    .condition([screenLocation:location, userId:sfi.ecfi.executionContext.user.userId]).useCache(true).list()
            for (EntityValue subscreensItem in userSubscreensItemList) {
                SubscreensItem si = new SubscreensItem(subscreensItem)
                subscreensByName.put(si.name, si)
                logger.info("Added file subscreen [${si.name}] at [${si.location}] to screen [${locationUrl}]")
            }
        }
    }

    Node getScreenNode() { return screenNode }

    Node getWebSettingsNode() { return screenNode."web-settings"[0] }

    Map<String, ParameterItem> getParameterMap() { return parameterByName }

    TransitionItem getTransitionItem(String name) { return (TransitionItem) transitionByName.get(name) }

    SubscreensItem getSubscreensItem(String name) { return (SubscreensItem) subscreensByName.get(name) }

    List<SubscreensItem> getSubscreensItemsSorted() {
        List<SubscreensItem> newList = new ArrayList(subscreensByName.size())
        if (subscreensByName.size() == 0) return newList
        newList.addAll(subscreensByName.values())
        Collections.sort(newList, new SubscreensItemComparator())
        return newList
    }

    ScreenSection getRootSection() { return rootSection }

    ScreenSection getSection(String sectionName) { return (ScreenSection) sectionByName.get(sectionName) }

    ScreenForm getForm(String formName) { return (ScreenForm) formByName.get(formName) }

    URL getSubContentUrl(List<String> pathNameList) {
        StringBuilder pathNameBldr = new StringBuilder()
        // add the path elements that remain
        for (String rp in pathNameList) pathNameBldr.append("/").append(rp)
        String pathName = pathNameBldr.toString()

        URL contentUrl = subContentUrlByPath.get(pathName)
        if (contentUrl) return contentUrl

        URL lastScreenUrl = sfi.ecfi.resourceFacade.getLocationUrl(location)
        if (lastScreenUrl.protocol == "file") {
            StringBuilder fileLoc = new StringBuilder(lastScreenUrl.toString())
            // get rid of the "file:" prefix
            fileLoc.delete(0, 5)
            // get rid of the suffix, probably .xml but use .*
            if (fileLoc.indexOf(".") > 0) fileLoc.delete(fileLoc.indexOf("."), fileLoc.length())
            fileLoc.append(pathName)

            File theFile = new File(fileLoc.toString())
            if (theFile.exists() && theFile.isFile()) contentUrl = theFile.toURI().toURL()

            for (String extToTry in defaultExtensions) {
                if (contentUrl != null) break
                theFile = new File(fileLoc.toString() + extToTry)
                if (theFile.exists() && theFile.isFile()) contentUrl = theFile.toURI().toURL()
            }
        }

        if (contentUrl) subContentUrlByPath.put(pathName, contentUrl)
        return contentUrl
    }

    static class ParameterItem {
        protected String name
        protected Class fromFieldGroovy = null
        protected Class valueGroovy = null
        ParameterItem(Node parameterNode, String location) {
            this.name = parameterNode."@name"

            if (parameterNode."@from-field") fromFieldGroovy = new GroovyClassLoader().parseClass(
                    (String) parameterNode."@from-field", "${location}.parameter_${name}.from_field")
            if (parameterNode."@value") fromFieldGroovy = new GroovyClassLoader().parseClass(
                    ('"""' + (String) parameterNode."@value" + '"""'), "${location}.parameter_${name}.value")
        }
        String getName() { return name }
        Object getValue(ExecutionContext ec) {
            Object value = null
            if (fromFieldGroovy) {
                value = InvokerHelper.createScript(fromFieldGroovy, new Binding(ec.context))
            }
            if (valueGroovy && !value) {
                value = InvokerHelper.createScript(valueGroovy, new Binding(ec.context))
            }
            if (!value) value = ec.context.get(name)
            if (!value && ec instanceof WebExecutionContext) value = ((WebExecutionContext) ec).parameters.get(name)
            return value
        }
    }

    static class TransitionItem {
        protected ScreenDefinition parentScreen

        protected String name
        protected String location
        protected XmlAction condition = null
        protected XmlAction actions = null

        protected List<ResponseItem> conditionalResponseList = new ArrayList<ResponseItem>()
        protected ResponseItem defaultResponse = null
        protected ResponseItem errorResponse = null

        TransitionItem(Node transitionNode, ScreenDefinition parentScreen) {
            this.parentScreen = parentScreen
            name = transitionNode."@name"
            location = "${parentScreen.location}.transition_${name}"
            // condition
            if (transitionNode.condition?.getAt(0)?.children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(parentScreen.sfi.ecfi, (Node) transitionNode.condition[0].children()[0], location + ".condition")
            }
            // call-service OR actions
            if (transitionNode."call-service") {
                Node callServiceNode = (Node) transitionNode."call-service"[0]
                if (!callServiceNode."@in-map") callServiceNode.attributes().put("in-map", "true")
                actions = new XmlAction(parentScreen.sfi.ecfi, callServiceNode, location + ".call_service")
            } else if (transitionNode.actions) {
                actions = new XmlAction(parentScreen.sfi.ecfi, (Node) transitionNode.actions[0], location + ".actions")
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

        boolean checkCondition(ExecutionContext ec) { return condition ? condition.checkCondition(ec) : true }

        ResponseItem run(ScreenRenderImpl sri) {
            // put parameters in the context
            if (sri.ec instanceof WebExecutionContext) {
                WebExecutionContext wec = (WebExecutionContext) sri.ec
                for (ParameterItem pi in parentScreen.parameterMap.values()) {
                    Object value = pi.getValue(wec.parameters)
                    if (value) sri.ec.context.put(pi.getName(), value)
                }
            }

            if (!checkCondition(sri.ec)) {
                sri.ec.message.addError("Condition failed for transition [${location}], not running actions or redirecting")
                if (errorResponse) return errorResponse
                return defaultResponse
            }

            if (actions) actions.run(sri.ec)

            // if there is an error-response and there are errors, we have a winner
            if (errorResponse && sri.ec.message.errors) return errorResponse
            // check all conditional-response, if condition then return that response
            for (ResponseItem condResp in conditionalResponseList) {
                if (condResp.checkCondition(sri.ec)) return condResp
            }
            // no errors, no conditionals, return default
            return defaultResponse
        }
    }

    static class ResponseItem {
        protected XmlAction condition = null
        protected Map<String, ParameterItem> parameterMap = new HashMap()

        protected String type
        protected String url
        protected String urlType
        // deferred for future version: protected boolean saveLastScreen
        protected boolean saveCurrentScreen

        ResponseItem(Node responseNode, TransitionItem ti, ScreenDefinition parentScreen) {
            String location = "${parentScreen.location}.transition[${ti.name}].${responseNode.name()}"
            if (responseNode.condition && responseNode.condition[0].children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(parentScreen.sfi.ecfi, (Node) responseNode.condition[0].children()[0],
                        location + ".condition")
            }

            type = responseNode."@type" ?: "url"
            url = responseNode."@url"
            urlType = responseNode."@url-type" ?: "screen-path"
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
            menuInclude = (!screen."@default-menu-include"[0] || screen."@default-menu-include"[0] == "true")
        }

        SubscreensItem(Node subscreensItem, ScreenDefinition parentScreen) {
            name = subscreensItem."@name"
            location = subscreensItem."@location"
            menuTitle = subscreensItem."@menu-title"
            menuIndex = subscreensItem."@menu-index" as int
            menuInclude = true

            if (subscreensItem."@disable-when") disableWhenGroovy = new GroovyClassLoader().parseClass(
                    (String) subscreensItem."@disable-when", "${parentScreen.location}.subscreens-item[${name}].@disable-when")
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
            return InvokerHelper.createScript(disableWhenGroovy, new Binding(ec.context)) as boolean
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
