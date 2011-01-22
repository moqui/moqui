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
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import groovy.util.slurpersupport.GPathResult

class ScreenDefinition {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenDefinition.class)

    protected final ScreenFacadeImpl sfi
    protected final Node screenNode
    protected final String location

    protected Map subscreensByName = new HashMap()

    protected ScreenSection rootSection = null
    protected Map<String, ScreenSection> sectionByName = new HashMap()
    protected Map<String, ScreenForm> formByName = new HashMap()

    ScreenDefinition(ScreenFacadeImpl sfi, Node screenNode, String location) {
        this.sfi = sfi
        this.screenNode = screenNode
        this.location = location

        // TODO web-settings
        // TODO parameter
        // TODO transition
        // subscreens
        populateSubscreens()

        // get the root section
        rootSection = new ScreenSection(sfi.ecfi, screenNode, location + ".screen")

        if (rootSection && rootSection.widgets) {
            // get all of the other sections by name
            for (Node sectionNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "section" || it.name() == "section-iterate" })) {
                sectionByName.put((String) sectionNode["@name"], new ScreenSection(sfi.ecfi, sectionNode, "${location}.${sectionNode.name()}[${sectionNode["@name"]}]"))
            }

            // get all forms by name
            for (Node formNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "form-single" || it.name() == "form-list" })) {
                formByName.put((String) formNode["@name"], new ScreenForm(sfi.ecfi, formNode, "${location}.${formNode.name()}[${formNode["@name"]}]"))
            }
        }
    }

    void populateSubscreens() {
        // start with file/directory structure
        URL locationUrl = sfi.ecfi.resourceFacade.getLocationUrl(location)
        if (locationUrl.getProtocol() == "file") {
            String filename = locationUrl.getFile()
            String subscreensDirStr = locationUrl.getPath() + filename.substring(0, filename.lastIndexOf("."))
            File subscreensDir = new File(subscreensDirStr)
            if (subscreensDir.exists() && subscreensDir.isDirectory()) {
                for (File subscreenFile in subscreensDir.listFiles()) {
                    if (!subscreenFile.isFile() || !subscreenFile.getName().endsWith(".xml")) continue
                    GPathResult subscreenRoot = new XmlSlurper().parse(subscreenFile)
                    if (subscreenRoot.name() == "screen" && subscreenRoot."@default-menu-include" != "false") {
                        String ssName = subscreenFile.getName()
                        ssName = ssName.substring(0, ssName.lastIndexOf("."))
                        subscreensByName.put(ssName, new SubscreensItem(ssName, subscreenFile.toURI().toString(), subscreenRoot))
                    }
                }
            }
        } else {
            logger.warn("Not getting subscreens by file/directory structure for screen [${location}] because it is not a file location")
        }

        // override dir structure with subscreens.subscreens-item elements
        for (Node subscreensItem in screenNode."subscreens"?."subscreens-item") {
            subscreensByName.put(subscreensItem."@name", new SubscreensItem(subscreensItem, this))
        }

        // override dir structure and subscreens-item elements with SubscreensItem entity
        EntityList subscreensItemList = sfi.ecfi.entityFacade.makeFind("SubscreensItem")
                .condition([screenLocation:location, userId:"_NA_"]).useCache(true).list()
        for (EntityValue subscreensItem in subscreensItemList) {
            subscreensByName.put(subscreensItem.subscreenName, new SubscreensItem(subscreensItem))
        }
        // override rest with SubscreensItem entity with userId of current user
        if (sfi.ecfi.executionContext.user.userId) {
            EntityList userSubscreensItemList = sfi.ecfi.entityFacade.makeFind("SubscreensItem")
                    .condition([screenLocation:location, userId:sfi.ecfi.executionContext.user.userId]).useCache(true).list()
            for (EntityValue subscreensItem in userSubscreensItemList) {
                subscreensByName.put(subscreensItem.subscreenName, new SubscreensItem(subscreensItem))
            }
        }
    }

    Node getScreenNode() { return screenNode }

    SubscreensItem getSubscreensItem(String subscreenName) { return (SubscreensItem) subscreensByName.get(subscreenName) }

    ScreenSection getRootSection() { return rootSection }

    ScreenSection getSection(String sectionName) { return (ScreenSection) sectionByName.get(sectionName) }

    ScreenForm getForm(String formName) { return (ScreenForm) formByName.get(formName) }

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
            menuTitle = screen."@default-menu-title"
            menuIndex = (screen."@default-menu-index" as Integer) ?: 1
            menuInclude = true
        }

        SubscreensItem(Node subscreensItem, ScreenDefinition parentScreen) {
            name = subscreensItem."@name"
            location = subscreensItem."@location"
            menuTitle = subscreensItem."@menu-title"
            menuIndex = subscreensItem."@menu-index" as int
            menuInclude = true

            if (subscreensItem."@disable-when") disableWhenGroovy = new GroovyClassLoader().parseClass(
                    (String) subscreensItem."@disable-when", "${parentScreen.location}.subscreens-item[${name}].disable-when")
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
}
