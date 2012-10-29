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

    def "create new Example"() {
        setup:
        ec.artifactExecution.disableAuthz()

        when:
        ec.entity.makeValue("Example").setAll([exampleId:"TEST1", exampleName:"Test Name"]).createOrUpdate()

        then:
        EntityValue example = ec.entity.makeFind("Example").condition([exampleId:"TEST1"]).one()
        example.exampleName == "Test Name"

        cleanup:
        ec.artifactExecution.enableAuthz()
    }
}
