/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
        EntityValue testCheck = ec.entity.find("moqui.tools.test.ToolsTestNoSqlEntity").condition("testId", "TEST1").one()
        testCheck.testMedium == "Test Name"
    }

    def "update Example TEST1"() {
        when:
        EntityValue testValue = ec.entity.find("moqui.tools.test.ToolsTestNoSqlEntity").condition("testId", "TEST1").one()
        testValue.testMedium = "Test Name 2"
        testValue.update()

        then:
        EntityValue testCheck = ec.entity.find("moqui.tools.test.ToolsTestNoSqlEntity").condition([testId:"TEST1"]).one()
        testCheck.testMedium == "Test Name 2"
    }

    def "delete Example TEST1"() {
        when:
        ec.entity.find("moqui.tools.test.ToolsTestNoSqlEntity").condition([testId:"TEST1"]).one().delete()

        then:
        EntityValue testCheck = ec.entity.find("moqui.tools.test.ToolsTestNoSqlEntity").condition([testId:"TEST1"]).one()
        testCheck == null
    }
}
