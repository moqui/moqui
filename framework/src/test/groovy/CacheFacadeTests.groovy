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
import java.sql.Timestamp
import org.moqui.context.Cache

class CacheFacadeTests extends Specification {
    @Shared
    ExecutionContext ec
    @Shared
    Cache testCache

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        testCache = ec.cache.getCache("CacheFacadeTests")
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "add cache element"() {
        when:
        testCache.put("key1", "value1")
        int hitCountBefore = testCache.getHitCount()

        then:
        testCache.get("key1") == "value1"
        testCache.getHitCount() == hitCountBefore + 1

        cleanup:
        testCache.clear()
    }

    def "overflow cache size limit"() {
        when:
        testCache.setMaxElements(3, Cache.LEAST_RECENTLY_ADDED)
        testCache.put("key1", "value1")
        testCache.put("key2", "value2")
        testCache.put("key3", "value3")
        testCache.put("key4", "value4")
        int hitCountBefore = testCache.getHitCount()
        int removeCountBefore = testCache.getRemoveCount()
        int missCountBefore = testCache.getMissCountTotal()

        then:
        testCache.getEvictionStrategy() == Cache.LEAST_RECENTLY_ADDED
        testCache.getMaxElements() == 3
        testCache.size() == 3
        testCache.getRemoveCount() == removeCountBefore
        testCache.get("key1") == null
        !testCache.containsKey("key1")
        testCache.getMissCountTotal() == missCountBefore + 1
        testCache.get("key2") == "value2"
        testCache.getHitCount() == hitCountBefore + 1

        cleanup:
        testCache.clear()
        // go back to size limit defaults
        testCache.setMaxElements(10000, Cache.LEAST_RECENTLY_USED)
    }

    // TODO: test cache expire time
}
