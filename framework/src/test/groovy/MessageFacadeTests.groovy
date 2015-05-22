/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.Moqui
import org.moqui.entity.EntityValue
import java.sql.Timestamp

class MessageFacadeTests extends Specification {
    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "add non-error message"() {
        when:
        String testMessage = "This is a test message"
        ec.message.addMessage(testMessage)

        then:
        ec.message.messages.contains(testMessage)
        ec.message.messagesString.contains(testMessage)
        !ec.message.hasError()

        cleanup:
        ec.message.messages.clear()
    }

    def "add error message"() {
        when:
        String testMessage = "This is a test error message"
        ec.message.addError(testMessage)

        then:
        ec.message.errors.contains(testMessage)
        ec.message.errorsString.contains(testMessage)
        ec.message.hasError()

        cleanup:
        ec.message.errors.clear()
    }

    def "add validation error"() {
        when:
        String errorMessage = "This is a test validation error"
        ec.message.addValidationError("form", "field", "service", errorMessage, new Exception("validation error location"))

        then:
        ec.message.validationErrors[0].message == errorMessage
        ec.message.validationErrors[0].form == "form"
        ec.message.validationErrors[0].field == "field"
        ec.message.validationErrors[0].serviceName == "service"
        ec.message.errorsString.contains(errorMessage)
        ec.message.hasError()

        cleanup:
        ec.message.validationErrors.clear()
    }
}
