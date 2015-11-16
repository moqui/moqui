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

class SystemScreenRenderTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(SystemScreenRenderTests.class)

    @Shared
    ExecutionContext ec
    @Shared
    ScreenTest screenTest

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("john.doe", "moqui", null)
        screenTest = ec.screen.makeTest().rootScreen("component://webroot/screen/webroot.xml").baseScreenPath("apps/system")
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

    /* use @Unroll instead:
    def "render all screens with no required parameters"() {
        when:
        long startTime = System.currentTimeMillis()
        Set<String> screensToSkip = new HashSet()
        List<String> screenPaths = screenTest.getNoRequiredParameterPaths(screensToSkip)
        for (String screenPath in screenPaths) {
            // logger.info("Rendering ${screenPath}")
            try {
                ScreenTestRender str = screenTest.render(screenPath, null, null)
                logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms")
            } catch (Throwable t) {
                logger.warn("Error rendering ${screenPath}: ${t.toString()}")
            }
        }

        logger.info("Rendered ${screenPaths.size()} screens in ${System.currentTimeMillis() - startTime}ms")

        then:
        true
    }
    */

    @Unroll
    def "render system screen (#screenPath, #parameters, #containsText)"() {
        expect:
        ScreenTestRender str = screenTest.render(screenPath, parameters, null)
        // logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms")
        !str.errorMessages
        containsText ? str.assertContains(containsText) : true

        where:
        screenPath | parameters | containsText
        "dashboard" | null | null
        "ArtifactHitSummary" | null | null
        "ArtifactHitBins" | null | null
        "AuditLog" | null | null
        "Cache/CacheList" | null | null
        "DataDocument/Search" | null | null
        "DataDocument/Index" | null | null
        "DataDocument/Export" | null | null
        "EntitySync/EntitySyncList" | null | null
        "Localization/Messages" | null | null
        "Localization/EntityFields" | null | null
        "Print/PrintJob/PrintJobList" | null | null
        "Print/Printer/PrinterList" | null | null
        "Resource/ElFinder" | null | null
        "Scheduler/SchedulerDetail" | null | null
        "Scheduler/Jobs" | null | null
        "Scheduler/Triggers" | null | null
        "Scheduler/History" | null | null
        "Security/UserAccount/UserAccountList" | null | null
        "Security/UserGroup/UserGroupList" | null | null
        "Security/ArtifactGroup/ArtifactGroupList" | null | null
        "SystemMessage/Message/SystemMessageList" | null | null
        "SystemMessage/Remote/MessageRemoteList" | null | null
        "SystemMessage/Type/MessageTypeList" | null | null
        "Visit/VisitList" | null | null
    }
}
