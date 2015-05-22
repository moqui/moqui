/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.Moqui
import org.moqui.example.ExampleCompiled

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExampleServiceTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(ExampleServiceTests.class)

    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() {
        ec.user.loginUser("john.doe", "moqui", null)
        // we still have to disableAuthz even though a user is logged in because this user does not have permission to
        //     call this service directly (normally is called through a screen with inherited permission)
        ec.artifactExecution.disableAuthz()
        ec.transaction.begin(null)
    }

    def cleanup() {
        ec.transaction.commit()
        ec.artifactExecution.enableAuthz()
        ec.user.logoutUser()
    }

    def "create example with ExampleServices"() {
        when:
        Map createResult = ec.service.sync().name("org.moqui.example.ExampleServices.createExample")
                .parameters([exampleTypeEnumId:"EXT_MADE_UP", statusId:"EXST_IN_DESIGN", exampleName:"Service Test Example"]).call()
        EntityValue exampleCreated = ec.entity.find("Example").condition("exampleId", createResult.exampleId).one()

        then:
        exampleCreated != null
        exampleCreated.exampleTypeEnumId == "EXT_MADE_UP"
        exampleCreated.exampleName == "Service Test Example"

        cleanup:
        exampleCreated.delete()
    }

    def "call ExampleCompiled echo method"() {
        expect:
        "Test String" == ExampleCompiled.echo("Test String")
    }

    def "send and consume ExampleMessage"() {
        when:
        // use the direct/local "remote" because no web server is running for local RPC call
        Map result = ec.service.sync().name("org.moqui.example.ExampleServices.send#ExampleMessage")
                .parameters([exampleId:'TEST2', systemMessageRemoteId:'Example1Direct']).call()
        // message is sent async so wait 0.5 second, should be more than plenty for Quartz to pick it up, etc
        sleep(500)
        EntityValue sentMessage = ec.entity.find("moqui.service.message.SystemMessage").condition("systemMessageId", result.systemMessageId).one()
        EntityValue receivedMessage = ec.entity.find("moqui.service.message.SystemMessage").condition("systemMessageId", sentMessage.remoteMessageId).one()

        then:
        result.systemMessageId
        sentMessage.statusId == 'SmsgSent'
        receivedMessage.statusId == 'SmsgConsumed'
    }
}
