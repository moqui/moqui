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
import org.apache.commons.mail.HtmlEmail
import org.apache.commons.mail.ByteArrayDataSource

org.moqui.impl.context.ExecutionContextImpl ec

// add the bodyParameters to the context so they are available throughout this script
if (bodyParameters) context.putAll(bodyParameters)

def emailTemplate = ec.entity.makeFind("EmailTemplate").condition("emailTemplateId", emailTemplateId).one()
//def emailTemplateAttachmentList = ec.entity.makeFind("EmailTemplateAttachment").condition("emailTemplateId", emailTemplateId).list()
def emailTemplateAttachmentList = emailTemplate.EmailTemplateAttachment
def emailServer = emailTemplate.EmailServer

HtmlEmail email = new HtmlEmail()
email.setHostName(emailServer.smtpRelayHost)
email.setFrom((String) emailTemplate.fromAddress, (String) emailTemplate.fromName)
String subject = ec.resource.evaluateStringExpand((String) emailTemplate.subject, "")
email.setSubject(subject)

def toList = toAddresses.split(",")
for (def toAddress in toList) email.addTo(toAddress.trim())
if (emailTemplate.ccAddresses) {
    def ccList = emailTemplate.ccAddresses.split(",")
    for (def ccAddress in ccList) email.addCc(ccAddress.trim())
}
if (emailTemplate.bccAddresses) {
    def bccList = emailTemplate.bccAddresses.split(",")
    for (def bccAddress in bccList) email.addBcc(bccAddress.trim())
}

// prepare and set the html message
def bodyRender = ec.screen.makeRender().rootScreen(emailTemplate.bodyScreenLocation)
                                       .webappName(emailTemplate.webappName).renderMode("html")
String bodyHtml = bodyRender.render()
email.setHtmlMsg(bodyHtml)

// set the alternative plain text message
// render screen with renderMode=text for this
def bodyTextRender = ec.screen.makeRender().rootScreen(emailTemplate.bodyScreenLocation).renderMode("text")
String bodyText = bodyTextRender.render()
email.setTextMsg(bodyText)
//email.setTextMsg("Your email client does not support HTML messages")

for (def emailTemplateAttachment in emailTemplateAttachmentList) {
    if (emailTemplateAttachment.screenRenderMode) {
        def attachmentRender = ec.screen.makeRender().rootScreen(emailTemplateAttachment.attachmentLocation)
                                                     .webappName(emailTemplate.webappName)
                                                     .renderMode(emailTemplateAttachment.screenRenderMode)
        String attachmentText = attachmentRender.render()
        if (emailTemplateAttachment.screenRenderMode == "xsl-fo") {
            // TODO: use FOP to change to PDF, then attach that

        } else {
            String mimeType = ec.screen.getMimeTypeByMode(emailTemplateAttachment.screenRenderMode)
            def dataSource = new ByteArrayDataSource(attachmentText, mimeType)
            email.attach(dataSource, (String) emailTemplateAttachment.fileName, "")
        }
    } else {
        // not a screen, get straight data with type depending on extension
        def dataSource = ec.resource.getLocationDataSource(emailTemplateAttachment.attachmentLocation)
        email.attach(dataSource, (String) emailTemplateAttachment.fileName, "")
    }
}

// create an EmailMessage record with info about this sent message
// NOTE: can do anything with: statusId, purposeEnumId, toUserId?
Map cemParms = [sentDate:ec.user.nowTimestamp, subject:subject, body:bodyHtml,
        fromAddress:emailTemplate.fromAddress, toAddresses:toAddresses,
        ccAddresses:emailTemplate.ccAddresses, bccAddresses:emailTemplate.bccAddresses,
        contentType:"text/html", emailTemplateId:emailTemplateId, fromUserId:ec.user.userId]
ec.service.async().name("create", "EmailMessage").parameters(cemParms).call()

// send the email
email.send()
