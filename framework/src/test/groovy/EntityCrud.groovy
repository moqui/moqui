/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.Moqui

class EntityCrud extends Specification {
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

    def "create and find Example CRDTST1"() {
        when:
        ec.entity.makeValue("example.Example").setAll([exampleId:"CRDTST1", exampleName:"Test Name"]).createOrUpdate()

        then:
        EntityValue example = ec.entity.find("example.Example").condition("exampleId", "CRDTST1").one()
        example.exampleName == "Test Name"
    }

    def "update Example CRDTST1"() {
        when:
        EntityValue example = ec.entity.find("example.Example").condition("exampleId", "CRDTST1").one()
        example.exampleName = "Test Name 2"
        example.update()

        then:
        EntityValue exampleCheck = ec.entity.find("example.Example").condition([exampleId:"CRDTST1"]).one()
        exampleCheck.exampleName == "Test Name 2"
    }

    def "delete Example CRDTST1"() {
        when:
        ec.entity.find("example.Example").condition([exampleId:"CRDTST1"]).one().delete()

        then:
        EntityValue exampleCheck = ec.entity.find("example.Example").condition([exampleId:"CRDTST1"]).one()
        exampleCheck == null
    }
}
