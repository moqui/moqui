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

import org.moqui.context.Cache;
import org.moqui.context.Cache.EvictionStrategy

import net.sf.ehcache.Ehcache
import net.sf.ehcache.store.MemoryStoreEvictionPolicy
import net.sf.ehcache.Element;

public class CacheImpl implements Cache {

    protected final Ehcache ehcache

    public CacheImpl(Ehcache ehcache) {
        this.ehcache = ehcache
    }

    /** @see org.moqui.context.Cache#getName() */
    public String getName() { return this.ehcache.getName() }

    /** @see org.moqui.context.Cache#getExpireTimeIdle() */
    public long getExpireTimeIdle() { return this.ehcache.getCacheConfiguration().getTimeToIdleSeconds() }

    /** @see org.moqui.context.Cache#setExpireTimeIdle(long) */
    public void setExpireTimeIdle(long expireTime) {
        this.ehcache.getCacheConfiguration().setTimeToIdleSeconds(expireTime)
    }

    /** @see org.moqui.context.Cache#getExpireTimeLive() */
    public long getExpireTimeLive() { return this.ehcache.getCacheConfiguration().getTimeToLiveSeconds() }

    /** @see org.moqui.context.Cache#setExpireTimeLive(long) */
    public void setExpireTimeLive(long expireTime) {
        this.ehcache.getCacheConfiguration().setTimeToLiveSeconds(expireTime)
    }

    /** @see org.moqui.context.Cache#getMaxElements() */
    public int getMaxElements() { return this.ehcache.getCacheConfiguration().getMaxElementsInMemory() }

    /** @see org.moqui.context.Cache#getEvictionStrategy() */
    public EvictionStrategy getEvictionStrategy() {
        MemoryStoreEvictionPolicy policy = this.ehcache.getCacheConfiguration().getMemoryStoreEvictionPolicy()
        if (MemoryStoreEvictionPolicy.LRU.equals(policy)) {
            return EvictionStrategy.LEAST_RECENTLY_USED
        } else if (MemoryStoreEvictionPolicy.LFU.equals(policy)) {
            return EvictionStrategy.LEAST_FREQUENTLY_USED
        } else if (MemoryStoreEvictionPolicy.FIFO.equals(policy)) {
            return EvictionStrategy.LEAST_RECENTLY_ADDED
        } else {
            return null
        }
    }

    /** @see org.moqui.context.Cache#setMaxElements(int, EvictionStrategy) */
    public void setMaxElements(int maxSize, EvictionStrategy strategy) {
        this.ehcache.getCacheConfiguration().setMaxElementsInMemory(maxSize)
        
        switch (strategy) {
        case EvictionStrategy.LEAST_RECENTLY_USED:
            this.ehcache.getCacheConfiguration().setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LRU)
            break

        case EvictionStrategy.LEAST_FREQUENTLY_USED:
            this.ehcache.getCacheConfiguration().setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU)
            break

        case EvictionStrategy.LEAST_RECENTLY_ADDED:
            this.ehcache.getCacheConfiguration().setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.FIFO)
            break
        }
    }

    /** @see org.moqui.context.Cache#get(Serializable) */
    public Object get(Serializable key) {
        Element element = this.ehcache.get(key)
        if (!element) return null
        if (element.isExpired()) {
            this.ehcache.removeElement(element)
            return null
        } else {
            return element.getObjectValue()
        }
    }

    /** @see org.moqui.context.Cache#put(Serializable, Object) */
    public Object put(Serializable key, Object value) {
        // use quiet get to not update stats as this isn't an explicit user get
        Element originalElement = this.ehcache.getQuiet(key)
        this.ehcache.put(new Element(key, value))
        if (originalElement) {
            return originalElement.getValue()
        } else {
            return null
        }
    }

    /** @see org.moqui.context.Cache#remove(Serializable) */
    public Object remove(Serializable key) {
        // use quiet get to not update stats as this isn't an explicit user get
        Element originalElement = this.ehcache.getQuiet(key)
        this.ehcache.removeElement(originalElement)
        if (originalElement) {
            return originalElement.getObjectValue()
        } else {
            return null
        }
    }

    /** @see org.moqui.context.Cache#keySet() */
    public Set<Serializable> keySet() {
        List keyList = this.ehcache.getKeysWithExpiryCheck()
        Set newKeySet = new HashSet()
        newKeySet.addAll(keyList)
        return newKeySet
    }

    /** @see org.moqui.context.Cache#hasExpired(String) */
    public boolean hasExpired(Serializable key) {
        // use quiet get to not update stats as this isn't an explicit user get
        Element originalElement = this.ehcache.getQuiet(key)
        if (originalElement) {
            // TODO: if we find an expired element in this case should we remove it?
            return originalElement.isExpired()
        } else {
            return false
        }
    }

    /** @see org.moqui.context.Cache#containsKey(String) */
    public boolean containsKey(Serializable key) { return this.ehcache.isKeyInCache(key) }

    /** @see org.moqui.context.Cache#isEmpty() */
    public boolean isEmpty() { return (this.ehcache.getSize() > 0) }

    /** @see org.moqui.context.Cache#size() */
    public int size() { return this.ehcache.getSize() }

    /** @see org.moqui.context.Cache#clear() */
    public void clear() { this.ehcache.removeAll() }

    /** @see org.moqui.context.Cache#clearExpired() */
    public void clearExpired() { this.ehcache.evictExpiredElements() }

    /** @see org.moqui.context.Cache#getHitCount() */
    public long getHitCount() { return this.ehcache.getStatistics().getCacheHits() }

    /** @see org.moqui.context.Cache#getMissCountNotFound() */
    public long getMissCountNotFound() {
        return this.ehcache.getSampledCacheStatistics().getCacheMissNotFoundMostRecentSample()
    }

    /** @see org.moqui.context.Cache#getMissCountExpired() */
    public long getMissCountExpired() {
        return this.ehcache.getSampledCacheStatistics().getCacheMissExpiredMostRecentSample()
    }

    /** @see org.moqui.context.Cache#getMissCountTotal() */
    public long getMissCountTotal() { return this.ehcache.getStatistics().getCacheMisses() }

    /** @see org.moqui.context.Cache#getRemoveCount() */
    public long getRemoveCount() {
        return this.ehcache.getSampledCacheStatistics().getCacheElementRemovedMostRecentSample()
    }

    /** @see org.moqui.context.Cache#clearCounters() */
    public void clearCounters() { this.ehcache.clearStatistics() }
}
