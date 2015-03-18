/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by Paul Z. Xu, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.Moqui
import java.sql.Timestamp
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityList

class EntityFindTests extends Specification {
    @Shared
    ExecutionContext ec
    @Shared
    Timestamp timestamp

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        timestamp = ec.user.nowTimestamp
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
        ec.transaction.begin(null)
        ec.entity.makeValue("Example").setAll([exampleId:"EXTST1", exampleTypeEnumId: null,
                description: "", exampleName:"Test Name",
                exampleSize: 100, exampleDate: timestamp]).createOrUpdate()
    }

    def cleanup() {
        ec.entity.makeValue("Example").set("exampleId", "EXTST1").delete()
        ec.artifactExecution.enableAuthz()
        ec.transaction.commit()
    }

    @Unroll
    def "find Example by single condition (#fieldName = #value)"() {
        expect:
        EntityValue example = ec.entity.find("Example").condition(fieldName, value).one()
        example != null
        example.exampleId == "EXTST1"

        where:
        fieldName | value
        "exampleId" | "EXTST1"
        // fails on some DBs without pre-JDBC type conversion: "exampleSize" | "100"
        "exampleSize" | 100
        // fails on some DBs without pre-JDBC type conversion: "exampleDate" | ec.l10n.format(timestamp, "yyyy-MM-dd HH:mm:ss.SSS")
        "exampleDate" | timestamp
    }

    @Unroll
    def "find Example by operator condition (#fieldName #operator #value)"() {
        expect:
        EntityValue example = ec.entity.find("Example").condition(fieldName, operator, value).one()
        example != null
        example.exampleId == "EXTST1"

        where:
        fieldName | operator | value
        "exampleId" | EntityCondition.BETWEEN | ["EXTST0", "EXTST2"]
        "exampleId" | EntityCondition.EQUALS | "EXTST1"
        "exampleId" | EntityCondition.IN | ["EXTST1"]
        "exampleId" | EntityCondition.LIKE | "%XTST%"
    }

    @Unroll
    def "find Example by searchFormInputs (#inputsMap #resultId)"() {
        expect:
        ec.context.putAll(inputsMap)
        EntityValue example = ec.entity.find("Example").searchFormInputs("", "", false).one()
        resultId ? example != null && example.exampleId == resultId : example == null

        where:
        inputsMap | resultId
        [exampleId: "EXTST1", exampleId_op: "equals"] | "EXTST1"
        [exampleId: "%XTST%", exampleId_op: "like"] | "EXTST1"
        [exampleId: "XTST", exampleId_op: "contains"] | "EXTST1"
        [exampleId: "EXTST1", exampleId_op: "equals", exampleTypeEnumId_op: "empty"] | "EXTST1"
        [exampleId: "EXTST1", exampleId_op: "equals", description_op: "empty"] | "EXTST1"
        [exampleId: "EXTST1", exampleId_op: "equals", exampleDate_from: "", exampleDate_thru: ""] | "EXTST1"
        [exampleId: "EXTST1", exampleId_op: "equals", exampleDate_from: timestamp, exampleDate_thru: timestamp] | null
        [exampleId: "EXTST1", exampleId_op: "equals", exampleDate_from: timestamp, exampleDate_thru: timestamp + 1] | "EXTST1"
        [exampleId: "EXTST1", exampleId_op: "equals", exampleName_not: "Y", exampleName_op: "equals", exampleName: ""] | "EXTST1"
        [exampleId: "EXTST1", exampleId_op: "equals", exampleName_not: "Y", exampleName_op: "empty"] | "EXTST1"
    }

    def "auto cache clear for list"() {
        // update the exampleName and make sure we get the new value
        when:
        EntityList exampleList = ec.entity.find("Example").condition("exampleSize", 100).useCache(true).list()
        ec.entity.makeValue("Example").setAll([exampleId:"EXTST1", exampleName:"Test Name 2"]).update()
        exampleList = ec.entity.find("Example").condition("exampleSize", 100).useCache(true).list()

        then:
        exampleList.size() == 1
        exampleList.first.exampleName == "Test Name 2"
    }

    def "auto cache clear for one by primary key"() {
        when:
        EntityValue example = ec.entity.find("Example").condition("exampleId", "EXTST1").useCache(true).one()
        ec.entity.makeValue("Example").setAll([exampleId:"EXTST1", exampleName:"Test Name 3"]).update()
        example = ec.entity.find("Example").condition("exampleId", "EXTST1").useCache(true).one()

        then:
        example.exampleName == "Test Name 3"
    }

    def "auto cache clear for one by non-primary key"() {
        when:
        EntityValue example = ec.entity.find("Example").condition([exampleSize:100, exampleDate:timestamp]).useCache(true).one()
        ec.entity.makeValue("Example").setAll([exampleId:"EXTST1", exampleName:"Test Name 4"]).update()
        example = ec.entity.find("Example").condition([exampleSize:100, exampleDate:timestamp]).useCache(true).one()

        then:
        example.exampleName == "Test Name 4"
    }

    def "auto cache clear for one by non-pk and initially no result"() {
        when:
        EntityValue example1 = ec.entity.find("Example").condition([exampleName:"Test Name 5"]).useCache(true).one()
        ec.entity.makeValue("Example").setAll([exampleId:"EXTST1", exampleName:"Test Name 5"]).update()
        EntityValue example2 = ec.entity.find("Example").condition([exampleName:"Test Name 5"]).useCache(true).one()

        then:
        example1 == null
        example2 != null
        example2.exampleName == "Test Name 5"
    }
}
