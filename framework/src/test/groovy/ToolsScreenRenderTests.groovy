/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.screen.ScreenTest
import org.moqui.screen.ScreenTest.ScreenTestRender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ToolsScreenRenderTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(ToolsScreenRenderTests.class)

    @Shared
    ExecutionContext ec
    @Shared
    ScreenTest screenTest

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("john.doe", "moqui", null)
        screenTest = ec.screen.makeTest().rootScreen("component://webroot/screen/webroot.xml").baseScreenPath("apps/tools")
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

    @Unroll
    def "render tools screen (#screenPath, #parameters, #containsText)"() {
        expect:
        ScreenTestRender str = screenTest.render(screenPath, parameters, null)
        // logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms")
        !str.errorMessages
        containsText ? str.assertContains(containsText) : true

        where:
        screenPath | parameters | containsText
        "dashboard" | null | null
        "AutoScreen/MainEntityList" | null | null
        // don't run, takes too long: "ArtifactStats" | null | null
        "DataView/FindDbView" | null | null
        "Entity/DataEdit/EntityList" | null | null
        "Entity/DataExport" | null | null
        "Entity/DataImport" | null | null
        "Entity/SqlRunner" | null | null
        "Entity/SpeedTest" | [baseCalls:'10'] | null // run with very few baseCalls so it doesn't take too long
        "Service/ServiceReference" | null | null
        "Service/ServiceRun" | null | null
    }
}
