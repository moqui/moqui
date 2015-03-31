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

import groovy.transform.CompileStatic

import java.sql.Timestamp

import org.moqui.context.Cache
import org.moqui.impl.StupidUtilities

import net.sf.ehcache.Ehcache
import net.sf.ehcache.store.MemoryStoreEvictionPolicy
import net.sf.ehcache.Element

@CompileStatic
class CacheImpl implements Cache {

    protected final Ehcache ehcache

    CacheImpl(Ehcache ehcache) { this.ehcache = ehcache }

    Ehcache getInternalCache() { return this.ehcache }

    @Override
    String getName() { return this.ehcache.getName() }

    @Override
    long getExpireTimeIdle() { return this.ehcache.getCacheConfiguration().getTimeToIdleSeconds() }

    @Override
    void setExpireTimeIdle(long expireTime) {
        this.ehcache.getCacheConfiguration().setTimeToIdleSeconds(expireTime)
    }

    @Override
    long getExpireTimeLive() { return this.ehcache.getCacheConfiguration().getTimeToLiveSeconds() }

    @Override
    void setExpireTimeLive(long expireTime) {
        this.ehcache.getCacheConfiguration().setTimeToLiveSeconds(expireTime)
    }

    @Override
    long getMaxElements() { return this.ehcache.getCacheConfiguration().getMaxEntriesLocalHeap() }

    @Override
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

    @Override
    void setMaxElements(long maxSize, Cache.EvictionStrategy strategy) {
        this.ehcache.getCacheConfiguration().setMaxEntriesLocalHeap(maxSize)
        
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    Set<Serializable> keySet() {
        List keyList = this.ehcache.getKeysWithExpiryCheck()
        Set newKeySet = new HashSet()
        newKeySet.addAll(keyList)
        return newKeySet
    }

    List<Map> makeElementInfoList(String orderByField) {
        List<Map> elementInfoList = new LinkedList()
        for (Object key in ehcache.getKeysWithExpiryCheck()) {
            Element e = ehcache.get(key)
            Map<String, Object> im = new HashMap<String, Object>([key:key as String,
                    value:e.getObjectValue() as String, hitCount:e.getHitCount(),
                    creationTime:new Timestamp(e.getCreationTime()), version:e.getVersion()])
            if (e.getLastUpdateTime()) im.lastUpdateTime = new Timestamp(e.getLastUpdateTime())
            if (e.getLastAccessTime()) im.lastAccessTime = new Timestamp(e.getLastAccessTime())
            elementInfoList.add(im)
        }
        if (orderByField) StupidUtilities.orderMapList(elementInfoList, [orderByField])
        return elementInfoList
    }

    @Override
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

    @Override
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

    @Override
    boolean isEmpty() { return (this.ehcache.getSize() > 0) }

    @Override
    int size() { return this.ehcache.getSize() }

    @Override
    void clear() { this.ehcache.removeAll() }

    @Override
    void clearExpired() { this.ehcache.evictExpiredElements() }

    @Override
    long getHitCount() { return this.ehcache.getStatistics().cacheHitCount() }

    @Override
    long getMissCountNotFound() { return this.ehcache.getStatistics().cacheMissNotFoundCount() }

    @Override
    long getMissCountExpired() { return this.ehcache.getStatistics().cacheMissExpiredCount() }

    @Override
    long getMissCountTotal() { return this.ehcache.getStatistics().cacheMissCount() }

    @Override
    long getRemoveCount() { return this.ehcache.getStatistics().cacheRemoveCount() }

    @Override
    void clearCounters() { /* this is no longer supported by ehcache as of version 2.7 */ }
}
