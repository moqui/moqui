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
import org.moqui.entity.EntityValue
import org.moqui.Moqui
import org.moqui.example.ExampleCompiled

class ExampleServiceTests extends Specification {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExampleServiceTests.class)

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
}
