/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.Moqui

class ServiceCrudImplicit extends Specification {
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
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    def "create and find Example SVCTST1 with service"() {
        when:
        // do a "store" to create or update
        ec.service.sync().name("store#example.Example").parameters([exampleId:"SVCTST1", exampleName:"Test Name"]).call()

        then:
        EntityValue example = ec.entity.find("example.Example").condition([exampleId:"SVCTST1"]).one()
        example.exampleName == "Test Name"
    }

    def "update Example SVCTST1 with service"() {
        when:
        ec.service.sync().name("update#example.Example").parameters([exampleId:"SVCTST1", exampleName:"Test Name 2"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("example.Example").condition([exampleId:"SVCTST1"]).one()
        exampleCheck.exampleName == "Test Name 2"
    }

    def "store update Example SVCTST1 with service"() {
        when:
        ec.service.sync().name("store#example.Example").parameters([exampleId:"SVCTST1", exampleName:"Test Name 3"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("example.Example").condition([exampleId:"SVCTST1"]).one()
        exampleCheck.exampleName == "Test Name 3"
    }

    def "delete Example SVCTST1 with service"() {
        when:
        ec.service.sync().name("delete#example.Example").parameters([exampleId:"SVCTST1"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("example.Example").condition([exampleId:"SVCTST1"]).one()
        exampleCheck == null
    }

    /* No real point to this, muddies data
    def "store create Example TEST_A with service"() {
        when:
        ec.service.sync().name("store#example.Example").parameters([exampleId:"TEST_A", exampleName:"Test Name A"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("example.Example").condition([exampleId:"TEST_A"]).one()
        exampleCheck.exampleName == "Test Name A"
    }
    */
}
