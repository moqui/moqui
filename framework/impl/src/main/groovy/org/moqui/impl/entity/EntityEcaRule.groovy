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
package org.moqui.impl.entity

import org.moqui.context.ExecutionContext
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ExecutionContextFactoryImpl

class EntityEcaRule {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityEcaRule.class)

    protected Node eecaNode
    protected String location

    protected XmlAction condition = null
    protected XmlAction actions = null

    EntityEcaRule(ExecutionContextFactoryImpl ecfi, Node eecaNode, String location) {
        this.eecaNode = eecaNode
        this.location = location

        // prep condition
        if (eecaNode.condition && eecaNode.condition[0].children()) {
            // the script is effectively the first child of the condition element
            condition = new XmlAction(ecfi, (Node) eecaNode.condition[0].children()[0], location + ".condition")
        }
        // prep actions
        if (eecaNode.actions) {
            actions = new XmlAction(ecfi, (Node) eecaNode.actions[0], location + ".actions")
        }
    }

    String getEntityName() { return eecaNode."@entity" }
    Node getEecaNode() { return eecaNode }

    void runIfMatches(String entityName, Map fieldValues, String operation, boolean before, ExecutionContext ec) {
        // see if we match this event and should run
        if (entityName != eecaNode."@entity") return
        if (ec.message.errors && eecaNode."@run-on-error" != "true") return
        if (before && eecaNode."@run-before" != "true") return
        if (!before && eecaNode."@run-before" == "true") return

        if (eecaNode.attributes().get("on-${operation}") != "true") return

        try {
            ec.context.push()
            ec.context.putAll(fieldValues)
            ec.context.put("entityValue", fieldValues)

            // run the condition and if passes run the actions
            boolean conditionPassed = true
            if (condition) conditionPassed = condition.checkCondition(ec)
            if (conditionPassed) {
                if (actions) actions.run(ec)
            }
        } finally {
            ec.context.pop()
        }
    }
}
