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

import static org.moqui.context.Cache.EvictionStrategy.*

import java.sql.Timestamp

import org.moqui.context.Cache
import org.moqui.impl.StupidUtilities

import net.sf.ehcache.Ehcache
import net.sf.ehcache.store.MemoryStoreEvictionPolicy
import net.sf.ehcache.Element

class CacheImpl implements Cache {

    protected final Ehcache ehcache

    CacheImpl(Ehcache ehcache) { this.ehcache = ehcache }

    Ehcache getInternalCache() { return this.ehcache }

    /** @see org.moqui.context.Cache#getName() */
    String getName() { return this.ehcache.getName() }

    /** @see org.moqui.context.Cache#getExpireTimeIdle() */
    long getExpireTimeIdle() { return this.ehcache.getCacheConfiguration().getTimeToIdleSeconds() }

    /** @see org.moqui.context.Cache#setExpireTimeIdle(long) */
    void setExpireTimeIdle(long expireTime) {
        this.ehcache.getCacheConfiguration().setTimeToIdleSeconds(expireTime)
    }

    /** @see org.moqui.context.Cache#getExpireTimeLive() */
    long getExpireTimeLive() { return this.ehcache.getCacheConfiguration().getTimeToLiveSeconds() }

    /** @see org.moqui.context.Cache#setExpireTimeLive(long) */
    void setExpireTimeLive(long expireTime) {
        this.ehcache.getCacheConfiguration().setTimeToLiveSeconds(expireTime)
    }

    /** @see org.moqui.context.Cache#getMaxElements() */
    int getMaxElements() { return this.ehcache.getCacheConfiguration().getMaxElementsInMemory() }

    /** @see org.moqui.context.Cache#getEvictionStrategy() */
    Cache.EvictionStrategy getEvictionStrategy() {
        MemoryStoreEvictionPolicy policy = this.ehcache.getCacheConfiguration().getMemoryStoreEvictionPolicy()
        if (MemoryStoreEvictionPolicy.LRU.equals(policy)) {
            return LEAST_RECENTLY_USED
        } else if (MemoryStoreEvictionPolicy.LFU.equals(policy)) {
            return LEAST_FREQUENTLY_USED
        } else if (MemoryStoreEvictionPolicy.FIFO.equals(policy)) {
            return LEAST_RECENTLY_ADDED
        } else {
            return null
        }
    }

    /** @see org.moqui.context.Cache#setMaxElements(int, EvictionStrategy) */
    void setMaxElements(int maxSize, Cache.EvictionStrategy strategy) {
        this.ehcache.getCacheConfiguration().setMaxElementsInMemory(maxSize)
        
        switch (strategy) {
        case LEAST_RECENTLY_USED:
            this.ehcache.getCacheConfiguration().setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LRU)
            break

        case LEAST_FREQUENTLY_USED:
            this.ehcache.getCacheConfiguration().setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU)
            break

        case LEAST_RECENTLY_ADDED:
            this.ehcache.getCacheConfiguration().setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.FIFO)
            break
        }
    }

    /** @see org.moqui.context.Cache#get(Serializable) */
    Object get(Serializable key) {
        if (key == null) return null
        Element element = this.ehcache.get(key)
        if (element == null) return null
        if (element.isExpired()) {
            this.ehcache.removeElement(element)
            return null
        } else {
            return element.getObjectValue()
        }
    }

    Element getElement(Serializable key) {
        if (key == null) return null
        return this.ehcache.get(key)
    }

    /** @see org.moqui.context.Cache#put(Serializable, Object) */
    Object put(Serializable key, Object value) {
        // use quiet get to not update stats as this isn't an explicit user get
        Element originalElement = this.ehcache.getQuiet(key)
        this.ehcache.put(new Element(key, value))
        if (originalElement) {
            return originalElement.getObjectValue()
        } else {
            return null
        }
    }

    /** @see org.moqui.context.Cache#remove(Serializable) */
    Object remove(Serializable key) {
        // use quiet get to not update stats as this isn't an explicit user get
        Element originalElement = this.ehcache.getQuiet(key)
        if (originalElement) {
            this.ehcache.removeElement(originalElement)
            return originalElement.getObjectValue()
        } else {
            return null
        }
    }

    void removeElement(Element el) { this.ehcache.removeElement(el) }

    /** @see org.moqui.context.Cache#keySet() */
    Set<Serializable> keySet() {
        List keyList = this.ehcache.getKeysWithExpiryCheck()
        Set newKeySet = new HashSet()
        newKeySet.addAll(keyList)
        return newKeySet
    }

    List<Map> makeElementInfoList(String orderByField) {
        if (size() > 500) return [[key:"Not displaying cache elements because cache size [${size()}] is greater than 500."]]
        List<Map> elementInfoList = new LinkedList()
        for (Serializable key in ehcache.getKeysWithExpiryCheck()) {
            Element e = ehcache.get(key)
            Map im = [key:key as String, value:e.getObjectValue() as String, hitCount:e.getHitCount(),
                    creationTime:new Timestamp(e.getCreationTime()), version:e.getVersion()]
            if (e.getLastUpdateTime()) im.lastUpdateTime = new Timestamp(e.getLastUpdateTime())
            if (e.getLastAccessTime()) im.lastAccessTime = new Timestamp(e.getLastAccessTime())
            elementInfoList.add(im)
        }
        if (orderByField) StupidUtilities.orderMapList(elementInfoList, [orderByField])
        return elementInfoList
    }

    /** @see org.moqui.context.Cache#hasExpired(String) */
    boolean hasExpired(Serializable key) {
        // use quiet get to not update stats as this isn't an explicit user get
        Element originalElement = this.ehcache.getQuiet(key)
        if (originalElement) {
            // if we find an expired element in this case should we remove it? yes, if caller wants to get details
            // about the element should use the ehcache API
            if (originalElement.isExpired()) {
                this.ehcache.removeElement(originalElement)
                return true
            } else {
                return false
            }
        } else {
            return false
        }
    }

    /** @see org.moqui.context.Cache#containsKey(String) */
    boolean containsKey(Serializable key) {
        if (this.ehcache.isKeyInCache(key)) {
            Element element = this.ehcache.get(key)
            if (element == null) return false
            // if the element is expired, consider it at not existing
            if (element.isExpired()) {
                this.ehcache.removeElement(element)
                return false
            } else {
                return true
            }
        } else {
            return false
        }
    }

    /** @see org.moqui.context.Cache#isEmpty() */
    boolean isEmpty() { return (this.ehcache.getSize() > 0) }

    /** @see org.moqui.context.Cache#size() */
    int size() { return this.ehcache.getSize() }

    /** @see org.moqui.context.Cache#clear() */
    void clear() { this.ehcache.removeAll() }

    /** @see org.moqui.context.Cache#clearExpired() */
    void clearExpired() { this.ehcache.evictExpiredElements() }

    /** @see org.moqui.context.Cache#getHitCount() */
    long getHitCount() { return this.ehcache.getStatistics().getCacheHits() }

    /** @see org.moqui.context.Cache#getMissCountNotFound() */
    long getMissCountNotFound() { return this.ehcache.getSampledCacheStatistics().getCacheMissNotFoundMostRecentSample() }

    /** @see org.moqui.context.Cache#getMissCountExpired() */
    long getMissCountExpired() { return this.ehcache.getSampledCacheStatistics().getCacheMissExpiredMostRecentSample() }

    /** @see org.moqui.context.Cache#getMissCountTotal() */
    long getMissCountTotal() { return this.ehcache.getStatistics().getCacheMisses() }

    /** @see org.moqui.context.Cache#getRemoveCount() */
    long getRemoveCount() { return this.ehcache.getSampledCacheStatistics().getCacheElementRemovedMostRecentSample() }

    /** @see org.moqui.context.Cache#clearCounters() */
    void clearCounters() { this.ehcache.clearStatistics() }
}
