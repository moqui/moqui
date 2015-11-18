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
        long totalTime = System.currentTimeMillis() - screenTest.startTime
        logger.info("Rendered ${screenTest.renderCount} screens (${screenTest.errorCount} errors) in ${ec.l10n.format(totalTime/1000, "0.000")}s, output ${ec.l10n.format(screenTest.renderTotalChars/1000, "#,##0")}k chars")

        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    @Unroll
    def "render system screen #screenPath (#containsText1, #containsText2)"() {
        expect:
        ScreenTestRender str = screenTest.render(screenPath, null, null)
        // logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms")
        !str.errorMessages
        containsText1 ? str.assertContains(containsText1) : true
        containsText2 ? str.assertContains(containsText2) : true

        where:
        screenPath | containsText1 | containsText2
        "dashboard" | "" | ""
        "ArtifactHitSummary" | "" | ""
        "ArtifactHitBins" | "" | ""
        "AuditLog" | "" | ""
        "Cache/CacheList" | "" | ""
        "DataDocument/Search" | "" | ""
        "DataDocument/Index" | "" | ""
        "DataDocument/Export" | "" | ""
        "EntitySync/EntitySyncList" | "" | ""
        "Localization/Messages" | "" | ""
        "Localization/EntityFields" | "" | ""
        "Print/PrintJob/PrintJobList" | "" | ""
        "Print/Printer/PrinterList" | "" | ""
        "Resource/ElFinder" | "" | ""
        "Scheduler/SchedulerDetail" | "" | ""
        "Scheduler/Jobs" | "" | ""
        "Scheduler/Triggers" | "" | ""
        "Scheduler/History" | "" | ""
        "Security/UserAccount/UserAccountList" | "" | ""
        "Security/UserGroup/UserGroupList" | "" | ""
        "Security/ArtifactGroup/ArtifactGroupList" | "" | ""
        "SystemMessage/Message/SystemMessageList" | "" | ""
        "SystemMessage/Remote/MessageRemoteList" | "" | ""
        "SystemMessage/Type/MessageTypeList" | "" | ""
        "Visit/VisitList" | "" | ""
    }

    /* use @Unroll approach instead:
    def "render all screens with no required parameters"() {
        when:
        Set<String> screensToSkip = new HashSet()
        List<String> screenPaths = screenTest.getNoRequiredParameterPaths(screensToSkip)
        for (String screenPath in screenPaths) {
            ScreenTestRender str = screenTest.render(screenPath, null, null)
            logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms")
        }

        then:
        screenTest.errorCount == 0
    }
    */
}
