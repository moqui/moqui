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
package org.moqui.impl.context

import org.moqui.context.CacheFacade
import org.moqui.context.Cache
import net.sf.ehcache.CacheManager
import net.sf.ehcache.Ehcache
import net.sf.ehcache.config.CacheConfiguration
import net.sf.ehcache.store.MemoryStoreEvictionPolicy

public class CacheFacadeImpl implements CacheFacade {

    protected final ExecutionContextFactoryImpl ecfi
    
    /** This is the Ehcache CacheManager singleton for use in Moqui.
     * Gets config from the default location, ie the ehcache.xml file from the classpath.
     */
    protected final CacheManager cacheManager = new CacheManager()

    public CacheFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
    }

    public void destroy() {
        this.cacheManager.shutdown()
    }

    /** @see org.moqui.context.CacheFacade#clearAllCaches() */
    public void clearAllCaches() {
        this.cacheManager.clearAll()
    }

    /** @see org.moqui.context.CacheFacade#clearExpiredFromAllCaches() */
    public void clearExpiredFromAllCaches() {
        List<String> cacheNames = Arrays.asList(this.cacheManager.getCacheNames())
        for (String cacheName in cacheNames) {
            Ehcache ehcache = this.cacheManager.getEhcache(cacheName)
            ehcache.evictExpiredElements()
        }
    }

    /** @see org.moqui.context.CacheFacade#clearCachesByPrefix(String) */
    public void clearCachesByPrefix(String prefix) {
        this.cacheManager.clearAllStartingWith(prefix)
    }

    /** @see org.moqui.context.CacheFacade#getCache(String) */
    public Cache getCache(String cacheName) {
        Cache theCache = null
        if (this.cacheManager.cacheExists(cacheName)) {
            // CacheImpl is a really lightweight object, but we should still consider keeping a local map of references
            theCache = new CacheImpl(cacheManager.getCache(cacheName))
        } else {
            // make a cache with the default seetings from ehcache.xml
            this.cacheManager.addCacheIfAbsent(cacheName)
            net.sf.ehcache.Cache newCache = this.cacheManager.getCache(cacheName)
            newCache.setSampledStatisticsEnabled(true)

            // set any applicable settings from the moqui conf xml file
            CacheConfiguration newCacheConf = newCache.getCacheConfiguration()
            def confXmlRoot = this.ecfi.getConfXmlRoot()
            // TODO: this find(Closure) may not work since we're now using XmlParser instead of XmlSlurper, so watch for that
            // if that doesn't work, see examples at: http://groovy.codehaus.org/Reading+XML+using+Groovy's+XmlParser
            def cacheElement = confXmlRoot."cache-list".cache.find({ it."@name" == cacheName })
            if (cacheElement) {
                settings.expireTimeIdle = cacheElement."@expire-time-idle"
                settings.expireTimeLive = cacheElement."@expire-time-live"
                settings.maxElements = cacheElement."@max-elements"
                settings.evictionStrategy = cacheElement."@eviction-strategy"
            }

            if (cacheElement."@expire-time-idle") {
                newCacheConf.setTimeToIdleSeconds(Long.valueOf(cacheElement."@expire-time-idle"))
                newCacheConf.setEternal(false)
            }
            if (cacheElement."@expire-time-live") {
                newCacheConf.setTimeToLiveSeconds(Long.valueOf(cacheElement."@expire-time-live"))
                newCacheConf.setEternal(false)
            }
            if (cacheElement."@max-elements") {
                newCacheConf.setMaxElementsInMemory(Integer.valueOf(cacheElement."@max-elements"))
            }
            String evictionStrategy = cacheElement."@eviction-strategy"
            if (evictionStrategy) {
                if ("least-recently-used" == evictionStrategy) {
                    newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LRU)
                } else if ("least-frequently-used" == evictionStrategy) {
                    newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU)
                } else if ("least-recently-added" == evictionStrategy) {
                    newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.FIFO)
                }
            }

            theCache = new CacheImpl(newCache)
        }

        return theCache
    }
}
