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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.moqui.context.Cache.EvictionStrategy
import org.moqui.impl.StupidUtilities

public class CacheFacadeImpl implements CacheFacade {
    protected final static Logger logger = LoggerFactory.getLogger(CacheFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi
    
    /** This is the Ehcache CacheManager singleton for use in Moqui.
     * Gets config from the default location, ie the ehcache.xml file from the classpath.
     */
    protected final CacheManager cacheManager = new CacheManager()

    CacheFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
    }

    void destroy() { cacheManager.shutdown() }

    /** @see org.moqui.context.CacheFacade#clearAllCaches() */
    void clearAllCaches() { cacheManager.clearAll() }

    /** @see org.moqui.context.CacheFacade#clearExpiredFromAllCaches() */
    void clearExpiredFromAllCaches() {
        List<String> cacheNames = Arrays.asList(cacheManager.getCacheNames())
        for (String cacheName in cacheNames) {
            Ehcache ehcache = cacheManager.getEhcache(cacheName)
            ehcache.evictExpiredElements()
        }
    }

    /** @see org.moqui.context.CacheFacade#clearCachesByPrefix(String) */
    void clearCachesByPrefix(String prefix) { cacheManager.clearAllStartingWith(prefix) }

    /** @see org.moqui.context.CacheFacade#getCache(String) */
    Cache getCache(String cacheName) {
        Cache theCache
        if (cacheManager.cacheExists(cacheName)) {
            // CacheImpl is a really lightweight object, but we should still consider keeping a local map of references
            theCache = new CacheImpl(cacheManager.getCache(cacheName))
        } else {
            theCache = new CacheImpl(initCache(cacheName))
        }

        return theCache
    }

    boolean cacheExists(String cacheName) { return cacheManager.cacheExists(cacheName) }

    List<Map<String, Object>> getAllCachesInfo(String orderByField) {
        List<Map<String, Object>> ci = new LinkedList()
        for (String cn in cacheManager.getCacheNames()) {
            Cache co = getCache(cn)
            ci.add((Map<String, Object>) [name:co.getName(), expireTimeIdle:co.getExpireTimeIdle(),
                    expireTimeLive:co.getExpireTimeLive(), maxElements:co.getMaxElements(),
                    evictionStrategy:getEvictionStrategyString(co.evictionStrategy), size:co.size(),
                    hitCount:co.getHitCount(), missCountNotFound:co.getMissCountNotFound(),
                    missCountExpired:co.getMissCountExpired(), missCountTotal:co.getMissCountTotal(),
                    removeCount:co.getRemoveCount()])
        }
        if (orderByField) StupidUtilities.orderMapList((List<Map>) ci, [orderByField])
        return ci
    }

    String getEvictionStrategyString(EvictionStrategy es) {
        switch (es) {
            case EvictionStrategy.LEAST_RECENTLY_USED: return "LRU"
            case EvictionStrategy.LEAST_RECENTLY_ADDED: return "LRA"
            case EvictionStrategy.LEAST_FREQUENTLY_USED: return "LFU"
        }
    }


    protected synchronized net.sf.ehcache.Cache initCache(String cacheName) {
        if (cacheManager.cacheExists(cacheName)) return cacheManager.getCache(cacheName)

        // make a cache with the default seetings from ehcache.xml
        cacheManager.addCacheIfAbsent(cacheName)
        net.sf.ehcache.Cache newCache = cacheManager.getCache(cacheName)
        newCache.setSampledStatisticsEnabled(true)

        // set any applicable settings from the moqui conf xml file
        CacheConfiguration newCacheConf = newCache.getCacheConfiguration()
        Node confXmlRoot = this.ecfi.getConfXmlRoot()
        Node cacheElement = (Node) confXmlRoot."cache-list".cache.find({ it."@name" == cacheName })

        boolean eternal = true
        if (cacheElement?."@expire-time-idle") {
            newCacheConf.setTimeToIdleSeconds(Long.valueOf((String) cacheElement."@expire-time-idle"))
            eternal = false
        }
        if (cacheElement?."@expire-time-live") {
            newCacheConf.setTimeToLiveSeconds(Long.valueOf((String) cacheElement."@expire-time-live"))
            eternal = false
        }
        newCacheConf.setEternal(eternal)

        if (cacheElement?."@max-elements") {
            newCacheConf.setMaxElementsInMemory(Integer.valueOf((String) cacheElement."@max-elements"))
        }
        String evictionStrategy = cacheElement?."@eviction-strategy"
        if (evictionStrategy) {
            if ("least-recently-used" == evictionStrategy) {
                newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LRU)
            } else if ("least-frequently-used" == evictionStrategy) {
                newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU)
            } else if ("least-recently-added" == evictionStrategy) {
                newCacheConf.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.FIFO)
            }
        }

        logger.info("Initialized new cache [${cacheName}]")
        return newCache
    }
}
