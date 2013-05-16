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
package org.moqui.impl.service

import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.context.ExecutionContext

import javax.mail.internet.MimeMessage
import javax.mail.Address
import javax.mail.Multipart
import javax.mail.BodyPart
import javax.mail.Part
import javax.mail.Header

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EmailEcaRule {
    protected final static Logger logger = LoggerFactory.getLogger(EmailEcaRule.class)

    protected Node emecaNode
    protected String location

    protected XmlAction condition = null
    protected XmlAction actions = null

    EmailEcaRule(ExecutionContextFactoryImpl ecfi, Node emecaNode, String location) {
        this.emecaNode = emecaNode
        this.location = location

        // prep condition
        if (emecaNode.condition && emecaNode.condition[0].children()) {
            // the script is effectively the first child of the condition element
            condition = new XmlAction(ecfi, (Node) emecaNode.condition[0].children()[0], location + ".condition")
        }
        // prep actions
        if (emecaNode.actions) {
            actions = new XmlAction(ecfi, (Node) emecaNode.actions[0], location + ".actions")
        }
    }

    // Node getEmecaNode() { return emecaNode }

    void runIfMatches(MimeMessage message, ExecutionContext ec) {

        try {
            ec.context.push()

            Map<String, Object> fields = new HashMap()
            ec.context.put("fields", fields)

            List<String> toList = []
            for (Address addr in message.getRecipients(MimeMessage.RecipientType.TO)) toList.add(addr.toString())
            fields.put("toList", toList)

            List<String> ccList = []
            for (Address addr in message.getRecipients(MimeMessage.RecipientType.CC)) toList.add(addr.toString())
            fields.put("ccList", ccList)

            List<String> bccList = []
            for (Address addr in message.getRecipients(MimeMessage.RecipientType.BCC)) toList.add(addr.toString())
            fields.put("bccList", bccList)

            fields.put("from", message.getFrom()?.getAt(0)?.toString())
            fields.put("subject", message.getSubject())
            fields.put("sentDate", message.getSentDate())
            fields.put("receivedDate", message.getReceivedDate())
            fields.put("bodyPartList", makeBodyPartList(message))

            Map<String, Object> headers = new HashMap()
            ec.context.put("headers", headers)
            for (Header header in message.allHeaders) {
                if (headers.get(header.name)) {
                    Object hi = headers.get(header.name)
                    if (hi instanceof List) { hi.add(header.value) }
                    else { headers.put(header.name, [hi, header.value]) }
                } else {
                    headers.put(header.name, header.value)
                }
            }

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

    protected List<String> makeBodyPartList(Part part) {
        List<String> bodyPartList = []
        Object content = part.getContent()
        if (content instanceof String) {
            bodyPartList.add(content)
        } else if (content instanceof Multipart) {
            int count = ((Multipart) content).getCount()
            for (int i = 0; i < count; i++) {
                BodyPart bp = ((Multipart) content).getBodyPart(i)
                bodyPartList.addAll(makeBodyPartList(bp))
            }
        }
        return bodyPartList
    }
}
