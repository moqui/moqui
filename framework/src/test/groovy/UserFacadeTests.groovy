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

class UserFacadeTests extends Specification {
    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "login user john.doe"() {
        expect:
        ec.user.loginUser("john.doe", "moqui", null)
    }

    def "check userId username currencyUomId locale userAccount.userFullName"() {
        expect:
        ec.user.userId == "EX_JOHN_DOE"
        ec.user.username == "john.doe"
        ec.user.currencyUomId == "USD"
        ec.user.locale.toString() == "en_us"
        ec.user.userAccount.userFullName == "John Doe"
    }

    def "check userGroupIdSet and isInGroup for ALL_USERS and ADMIN"() {
        expect:
        ec.user.userGroupIdSet.contains("ALL_USERS")
        ec.user.isInGroup("ALL_USERS")
        ec.user.userGroupIdSet.contains("ADMIN")
        ec.user.isInGroup("ADMIN")
    }

    def "not in web context so no visit"() {
        expect:
        ec.user.visitId == null
    }

    def "set and get Preference"() {
        expect:
        ec.user.setPreference("testPref1", "prefValue1")
        ec.user.getPreference("testPref1") == "prefValue1"
    }

    def "logout user"() {
        expect:
        ec.user.logoutUser()
    }
}
