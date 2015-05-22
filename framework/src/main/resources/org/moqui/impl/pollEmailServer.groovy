/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.moqui.impl.context.ExecutionContextImpl

final static Logger logger = LoggerFactory.getLogger("org.moqui.impl.pollEmailServer")

ExecutionContextImpl ec = context.ec

def emailServer = ec.entity.find("moqui.basic.email.EmailServer").condition("emailServerId", emailServerId).one()

def sessionProperties = new Properties()
sessionProperties.put("mail.store.protocol", emailServer.storeProtocol)
sessionProperties.put("mail.host", emailServer.storeHost)
sessionProperties.put("mail.port", emailServer.storePort)
sessionProperties.put("mail.user", emailServer.mailUsername)
sessionProperties.put("mail.pass", emailServer.mailPassword)

Session session = Session.getInstance(sessionProperties)
Store store = session.getStore()

//def urlName = new URLName(emailServer.storeProtocol, emailServer.storeHost, emailServer.storePort as int,
//        "", emailServer.mailUsername, emailServer.mailPassword)

if (!store.isConnected()) store.connect();

// open the INBOX folder
Folder folder = store.getDefaultFolder();
if (!folder.exists()) { ec.message.addError("No default (root) folder available"); return }
folder = folder.getFolder("INBOX");
if (!folder.exists()) { ec.message.addError("No INBOX folder available"); return }

// get message count
folder.open(Folder.READ_WRITE)
int totalMessages = folder.getMessageCount()
// close and return if no messages
if (totalMessages == 0) { folder.close(false); return }

// get unseen messages
Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
FetchProfile profile = new FetchProfile()
profile.add(FetchProfile.Item.ENVELOPE)
profile.add(FetchProfile.Item.FLAGS)
profile.add("X-Mailer")
folder.fetch(messages, profile)

for (Message message in messages) {
    if (message.isSet(Flags.Flag.SEEN)) continue

    // NOTE: should we check size? long messageSize = message.getSize()
    if (message instanceof MimeMessage) {
        ec.service.runEmecaRules(message)
        message.setFlag(Flags.Flag.SEEN, true)

        // delete the message if setup to do so
        if (emailServer.storeDelete == "Y") message.setFlag(Flags.Flag.DELETED, true)
    } else {
        logger.warn("Doing nothing with non-MimeMessage message: ${message}")
    }
}

// expunge and close the folder
folder.close(true)
