/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
        ec.user.locale.toString() == "en_US"
        ec.user.timeZone.ID == "US/Central"
        ec.user.currencyUomId == "USD"
        ec.user.userAccount.userFullName == "John Doe"
    }

    def "set and get Locale UK"() {
        when:
        ec.user.setLocale(Locale.UK)
        then:
        ec.user.getLocale() == Locale.UK
        ec.user.getLocale().toString() == "en_GB"
    }
    def "set and get Locale US"() {
        when:
        // set back to en_us
        ec.user.setLocale(Locale.US)
        then:
        ec.user.locale.toString() == "en_US"
    }

    def "set and get TimeZone US/Pacific"() {
        when:
        ec.user.setTimeZone(TimeZone.getTimeZone("US/Pacific"))
        then:
        ec.user.getTimeZone() == TimeZone.getTimeZone("US/Pacific")
        ec.user.getTimeZone().getID() == "US/Pacific"
        ec.user.getTimeZone().getRawOffset() == -28800000
    }

    def "set and get TimeZone US/Central"() {
        when:
        // set TimeZone back to default US/Central
        ec.user.setTimeZone(TimeZone.getTimeZone("US/Central"))
        then:
        ec.user.getTimeZone().getID() == "US/Central"
    }

    def "set and get currencyUomId GBP"() {
        when:
        ec.user.setCurrencyUomId("GBP")
        then:
        ec.user.getCurrencyUomId() == "GBP"
    }

    def "set and get currencyUomId USD"() {
        when:
        // reset to the default USD
        ec.user.setCurrencyUomId("USD")
        then:
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
        when:
        ec.user.setPreference("testPref1", "prefValue1")
        then:
        ec.user.getPreference("testPref1") == "prefValue1"
    }

    def "logout user"() {
        expect:
        ec.user.logoutUser()
    }
}
