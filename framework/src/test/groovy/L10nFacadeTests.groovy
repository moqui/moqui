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
import org.moqui.entity.EntityValue

class L10nFacadeTests extends Specification {
    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    @Unroll
    def "get Localized Message (#original - #locale)"() {
        expect:
        ec.user.setLocale(new Locale(locale))
        localized == ec.l10n.getLocalizedMessage(original)

        cleanup:
        ec.user.setLocale(Locale.US)

        where:
        original | locale | localized
        "Create" | "en" | "Create"
        "Create" | "es" | "Crear"
        "Create" | "fr" | "Cr\u00E9er"
        "Create" | "zh" | "\u65B0\u5EFA" // for XML: &#26032;&#24314;
        "Not Localized" | "en" | "Not Localized"
        "Not Localized" | "es" | "Not Localized"
        "Not Localized" | "zh" | "Not Localized"
    }

    @Unroll
    def "LocalizedEntityField with Enumeration.description (#enumId - #locale)"() {
        setup:
        ec.artifactExecution.disableAuthz()

        expect:
        ec.user.setLocale(new Locale(locale))
        EntityValue enumValue = ec.entity.makeFind("Enumeration").condition("enumId", enumId).one()
        localized == enumValue.get("description")

        cleanup:
        ec.artifactExecution.enableAuthz()
        ec.user.setLocale(Locale.US)

        where:
        enumId | locale | localized
        "GEOT_CITY" | "en" | "City"
        "GEOT_CITY" | "es" | "Ciudad"
        "GEOT_CITY" | "zh" | "\u5E02" // for XML: &#24066;
        "GEOT_STATE" | "en" | "State"
        "GEOT_STATE" | "es" | "Estado"
        "GEOT_COUNTRY" | "es" | "Pa\u00EDs"
    }

    // TODO test localized message with variable expansion (ensure translate then expand)

    // TODO test formatCurrency
    // TODO test formatValue

    // TODO test parseTime
    // TODO test parseDate
    // TODO test parseTimestamp
    // TODO test parseDateTime
    // TODO test parseNumber
}
