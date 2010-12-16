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

            // start with the default conf file, get settings form there
            Map settings = [:]
            def defaultConfXmlRoot = this.ecfi.getDefaultConfXmlRoot();
            def defaultCacheElement = defaultConfXmlRoot."cache-list".cache.find { it['@name'] == cacheName }
            if (defaultCacheElement) {
                settings.expireTimeIdle = defaultCacheElement."@expire-time-idle"
                settings.expireTimeLive = defaultCacheElement."@expire-time-live"
                settings.maxElements = defaultCacheElement."@max-elements"
                settings.evictionStrategy = defaultCacheElement."@eviction-strategy"
            }

            // then get settings from the active conf file to override defaults
            def confXmlRoot = this.ecfi.getConfXmlRoot()
            def cacheElement = confXmlRoot."cache-list".cache.find { it['@name'] == cacheName }
            if (cacheElement) {
                settings.expireTimeIdle = cacheElement."@expire-time-idle"
                settings.expireTimeLive = cacheElement."@expire-time-live"
                settings.maxElements = cacheElement."@max-elements"
                settings.evictionStrategy = cacheElement."@eviction-strategy"
            }

            if (settings.expireTimeIdle) {
                newCacheConf.setTimeToIdleSeconds(Long.valueOf(settings.expireTimeIdle))
                newCacheConf.setEternal(false)
            }
            if (settings.expireTimeLive) {
                newCacheConf.setTimeToLiveSeconds(Long.valueOf(settings.expireTimeLive))
                newCacheConf.setEternal(false)
            }
            if (settings.maxElements) {
                newCacheConf.setMaxElementsInMemory(Integer.valueOf(settings.maxElements))
            }
            if (settings.evictionStrategy) {
                if ("least-recently-used" == settings.evictionStrategy) {
                    newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LRU)
                } else if ("least-frequently-used" == settings.evictionStrategy) {
                    newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU)
                } else if ("least-recently-added" == settings.evictionStrategy) {
                    newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.FIFO)
                }
            }

            theCache = new CacheImpl(newCache)
        }

        return theCache
    }
}
