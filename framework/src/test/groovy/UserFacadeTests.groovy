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

    def "check userId username currencyUomId locale userAccount.userFullName defaults"() {
        expect:
        ec.user.userId == "EX_JOHN_DOE"
        ec.user.username == "john.doe"
        ec.user.locale.toString() == "en_us"
        ec.user.timeZone.ID == "US/Central"
        ec.user.currencyUomId == "USD"
        ec.user.userAccount.userFullName == "John Doe"
    }

    def "set and get Locale"() {
        expect:
        ec.user.setLocale(Locale.UK)
        // doesn't work because of Java API (results in "en_gb" == "en_GB) ec.user.getLocale() == Locale.UK
        ec.user.getLocale().toString() == "en_gb"
        // set back to en_us
        ec.user.setLocale(Locale.US)
        ec.user.locale.toString() == "en_us"
    }

    def "set and get TimeZone"() {
        expect:
        ec.user.setTimeZone(TimeZone.getTimeZone("US/Pacific"))
        ec.user.getTimeZone() == TimeZone.getTimeZone("US/Pacific")
        ec.user.getTimeZone().getID() == "US/Pacific"
        ec.user.getTimeZone().getRawOffset() == -28800000
        // set TimeZone back to default US/Central
        ec.user.setTimeZone(TimeZone.getTimeZone("US/Central"))
        ec.user.getTimeZone().getID() == "US/Central"
    }

    def "set and get currencyUomId"() {
        expect:
        ec.user.setCurrencyUomId("GBP")
        ec.user.getCurrencyUomId() == "GBP"
        // reset to the default USD
        ec.user.setCurrencyUomId("USD")
        ec.user.getCurrencyUomId() == "USD"
    }

    def "check userGroupIdSet and isInGroup for ALL_USERS and ADMIN"() {
        expect:
        ec.user.userGroupIdSet.contains("ALL_USERS")
        ec.user.isInGroup("ALL_USERS")
        ec.user.userGroupIdSet.contains("ADMIN")
        ec.user.isInGroup("ADMIN")
    }

    def "check default admin group permission ExamplePerm"() {
        expect:
        ec.user.hasPermission("ExamplePerm")
        !ec.user.hasPermission("BogusPerm")
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
