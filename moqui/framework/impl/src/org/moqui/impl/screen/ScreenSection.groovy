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

import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ContextStack

class ScreenSection {
    protected Node sectionNode
    protected String location

    protected XmlAction condition = null
    protected XmlAction actions = null
    protected ScreenWidgets widgets = null
    protected ScreenWidgets failWidgets = null

    ScreenSection(ExecutionContextFactoryImpl ecfi, Node sectionNode, String location) {
        this.sectionNode = sectionNode
        this.location = location

        // prep condition
        if (sectionNode.condition && sectionNode.condition[0].children()) {
            // the script is effectively the first child of the condition element
            condition = new XmlAction(ecfi, (Node) sectionNode.condition[0].children()[0], location + ".condition")
        }
        // prep actions
        if (sectionNode.actions) {
            actions = new XmlAction(ecfi, (Node) sectionNode.actions[0], location + ".actions")
        }
        // prep widgets
        if (sectionNode.widgets) {
            widgets = new ScreenWidgets(ecfi, (Node) sectionNode.widgets[0], location + ".widgets")
        }
        // prep fail-widgets
        if (sectionNode."fail-widgets") {
            failWidgets = new ScreenWidgets(ecfi, (Node) sectionNode."fail-widgets"[0], location + ".fail-widgets")
        }
    }

    void render(ScreenRenderImpl sri) {
        ContextStack cs = (ContextStack) sri.ec.context
        if (sectionNode.name() == "section-iterate") {
            // if nothing to iterate over, all done
            if (!cs.get(sectionNode["@list"][0])) return
            // TODO: handle paginate, paginate-size (lower priority...)
            for (Object entry in cs.get(sectionNode["@list"][0] as String)) {
                try {
                    cs.push()

                    cs.put(sectionNode["@entry"][0] as String, entry)
                    if (sectionNode["@key"] && entry instanceof Map.Entry)
                        cs.put(sectionNode["@key"][0] as String, entry.getKey())

                    renderSingle(sri)
                } finally {
                    cs.pop()
                }
            }
        } else {
            // NOTE: don't push/pop context for normal sections, for root section want to be able to share-scope when it
            // is included by another screen so that fields set will be in context of other screen
            renderSingle(sri)
        }
    }

    protected void renderSingle(ScreenRenderImpl sri) {
        boolean conditionPassed = true
        if (condition) conditionPassed = condition.checkCondition(sri.ec)

        if (conditionPassed) {
            if (actions) actions.run(sri.ec)
            if (widgets) widgets.render(sri)
        } else {
            if (failWidgets) failWidgets.render(sri)
        }
    }
}
