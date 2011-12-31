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
import javax.activation.DataSource
import org.moqui.BaseException

org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("org.moqui.impl.sendEmailTemplate")

try {
// NOTE: uncomment for autocomplete, comment to avoid causing runtime problems
// org.moqui.impl.context.ExecutionContextImpl ec

// logger.info("sendEmailTemplate with emailTemplateId [${emailTemplateId}], bodyParameters [${bodyParameters}]")

// add the bodyParameters to the context so they are available throughout this script
if (bodyParameters) context.putAll(bodyParameters)

def emailTemplate = ec.entity.makeFind("moqui.basic.email.EmailTemplate").condition("emailTemplateId", emailTemplateId).one()
//def emailTemplateAttachmentList = ec.entity.makeFind("moqui.basic.email.EmailTemplateAttachment").condition("emailTemplateId", emailTemplateId).list()
def emailTemplateAttachmentList = emailTemplate."moqui.basic.email.EmailTemplateAttachment"
def emailServer = emailTemplate."moqui.basic.email.EmailServer"

// check a couple of required fields
if (!emailTemplate) ec.message.addError("No EmailTemplate record found for ID [${emailTemplateId}]")
if (!emailServer) ec.message.addError("No EmailServer record found for EmailTemplate [${emailTemplateId}]")
if (emailServer && !emailServer.smtpHost)
    ec.message.addError("SMTP Host is empty for EmailServer [${emailServer.emailServerId}]")
if (emailTemplate && !emailTemplate.fromAddress)
    ec.message.addError("From address is empty for EmailTemplate [${emailTemplateId}]")
if (ec.message.errors) return

HtmlEmail email = new HtmlEmail()
email.setHostName(emailServer.smtpHost)
if (emailServer.smtpPort) email.setSmtpPort((emailServer.smtpPort ?: "25") as int)
if (emailServer.mailUsername) email.setAuthentication(emailServer.mailUsername, emailServer.mailPassword)
if (emailServer.smtpStartTls) email.setTLS(emailServer.smtpStartTls == "Y")
if (emailServer.smtpSsl) email.setSSL(emailServer.smtpSsl == "Y")

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
            DataSource dataSource = new ByteArrayDataSource(attachmentText, mimeType)
            email.attach(dataSource, (String) emailTemplateAttachment.fileName, "")
        }
    } else {
        // not a screen, get straight data with type depending on extension
        DataSource dataSource = ec.resource.getLocationDataSource(emailTemplateAttachment.attachmentLocation)
        email.attach(dataSource, (String) emailTemplateAttachment.fileName, "")
    }
}

// create an moqui.basic.email.EmailMessage record with info about this sent message
// NOTE: can do anything with: statusId, purposeEnumId, toUserId?
if (createEmailMessage) {
    Map cemParms = [sentDate:ec.user.nowTimestamp, subject:subject, body:bodyHtml,
            fromAddress:emailTemplate.fromAddress, toAddresses:toAddresses,
            ccAddresses:emailTemplate.ccAddresses, bccAddresses:emailTemplate.bccAddresses,
            contentType:"text/html", emailTemplateId:emailTemplateId, fromUserId:ec.user.userId]
    ec.artifactExecution.disableAuthz()
    ec.service.sync().name("create", "moqui.basic.email.EmailMessage").parameters(cemParms).call()
    ec.artifactExecution.enableAuthz()
}

logger.info("Sending [${email}] email from template [${emailTemplateId}] with bodyHtml [${bodyHtml}] bodyText [${bodyText}]")

// send the email
email.send()
} catch (Throwable t) {
    logger.info("Error in groovy", t)
    throw new BaseException("Error in sendEmailTemplate", t)
}
