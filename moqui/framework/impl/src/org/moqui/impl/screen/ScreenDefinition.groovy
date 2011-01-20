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

import org.slf4j.LoggerFactory
import org.slf4j.Logger

class ScreenDefinition {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenDefinition.class)

    protected final ScreenFacadeImpl sfi
    protected final Node screenNode
    protected final String location
    protected ScreenSection rootSection = null
    protected Map<String, ScreenSection> sectionByName = null

    ScreenDefinition(ScreenFacadeImpl sfi, Node screenNode, String location) {
        this.sfi = sfi
        this.screenNode = screenNode
        this.location = location

        // TODO web-settings
        // TODO parameter
        // TODO transition
        // TODO subscreens

        // get the root section
        if (screenNode.section) rootSection = new ScreenSection(sfi.ecfi, screenNode.section[0], location + ".rootSection")

        // get all of the other sections by name
        if (rootSection && rootSection.widgets) {
            for (Node sectionNode in rootSection.widgets.widgetsNode.depthFirst()
                    .findAll({ it.name() == "section" || it.name() == "section-iterate" })) {
                sectionByName.put((String) sectionNode["@name"], new ScreenSection(sfi.ecfi, sectionNode, "${location}.section[${}]"))
            }
        }
    }

    Node getScreenNode() { return screenNode }

    ScreenSection getRootSection() { return rootSection }

    ScreenSection getSection(String sectionName) { return (ScreenSection) sectionByName.get(sectionName) }
}
