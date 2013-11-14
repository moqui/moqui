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


import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import spock.lang.Shared
import spock.lang.Specification

class EntityNoSqlCrud extends Specification {
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
        ec.artifactExecution.disableAuthz()
        ec.transaction.begin(null)
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
        ec.transaction.commit()
    }

    def "create and find ToolsTestNoSqlEntity TEST1"() {
        when:
        ec.entity.makeValue("moqui.tools.test.ToolsTestNoSqlEntity").setAll([testId:"TEST1", testMedium:"Test Name"]).createOrUpdate()

        then:
        EntityValue testCheck = ec.entity.makeFind("moqui.tools.test.ToolsTestNoSqlEntity").condition("testId", "TEST1").one()
        testCheck.testMedium == "Test Name"
    }

    def "update Example TEST1"() {
        when:
        EntityValue testValue = ec.entity.makeFind("moqui.tools.test.ToolsTestNoSqlEntity").condition("testId", "TEST1").one()
        testValue.testMedium = "Test Name 2"
        testValue.update()

        then:
        EntityValue testCheck = ec.entity.makeFind("moqui.tools.test.ToolsTestNoSqlEntity").condition([testId:"TEST1"]).one()
        testCheck.testMedium == "Test Name 2"
    }

    def "delete Example TEST1"() {
        when:
        ec.entity.makeFind("moqui.tools.test.ToolsTestNoSqlEntity").condition([testId:"TEST1"]).one().delete()

        then:
        EntityValue testCheck = ec.entity.makeFind("moqui.tools.test.ToolsTestNoSqlEntity").condition([testId:"TEST1"]).one()
        testCheck == null
    }
}
