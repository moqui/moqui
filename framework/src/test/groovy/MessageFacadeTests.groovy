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
