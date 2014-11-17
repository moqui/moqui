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

    def "create and find Example TEST1 with service"() {
        when:
        // do a "store" to create or update
        ec.service.sync().name("store#moqui.example.Example").parameters([exampleId:"TEST1", exampleName:"Test Name"]).call()

        then:
        EntityValue example = ec.entity.find("moqui.example.Example").condition([exampleId:"TEST1"]).one()
        example.exampleName == "Test Name"
    }

    def "update Example TEST1 with service"() {
        when:
        ec.service.sync().name("update#moqui.example.Example").parameters([exampleId:"TEST1", exampleName:"Test Name 2"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("moqui.example.Example").condition([exampleId:"TEST1"]).one()
        exampleCheck.exampleName == "Test Name 2"
    }

    def "store update Example TEST1 with service"() {
        when:
        ec.service.sync().name("store#moqui.example.Example").parameters([exampleId:"TEST1", exampleName:"Test Name 3"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("moqui.example.Example").condition([exampleId:"TEST1"]).one()
        exampleCheck.exampleName == "Test Name 3"
    }

    def "delete Example TEST1 with service"() {
        when:
        ec.service.sync().name("delete#moqui.example.Example").parameters([exampleId:"TEST1"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("moqui.example.Example").condition([exampleId:"TEST1"]).one()
        exampleCheck == null
    }

    def "store create Example TEST_A with service"() {
        when:
        ec.service.sync().name("store#moqui.example.Example").parameters([exampleId:"TEST_A", exampleName:"Test Name A"]).call()

        then:
        EntityValue exampleCheck = ec.entity.find("moqui.example.Example").condition([exampleId:"TEST_A"]).one()
        exampleCheck.exampleName == "Test Name A"
    }
}
