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

import org.moqui.context.Cache

public class CacheImpl<V> implements Cache<V> {

    /** @see org.moqui.context.Cache#getName() */
    public String getName() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.Cache#getExpireTimeIdle() */
    public long getExpireTimeIdle() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#setExpireTimeIdle(long) */
    public void setExpireTimeIdle(long expireTime) {
        // TODO: implement this
    }

    /** @see org.moqui.context.Cache#getExpireTimeLive() */
    public long getExpireTimeLive() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#setExpireTimeLive(long) */
    public void setExpireTimeLive(long expireTime) {
        // TODO: implement this
    }

    /** @see org.moqui.context.Cache#getMaxElements() */
    public long getMaxElements() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#getEvictionStrategy() */
    public EvictionStrategy getEvictionStrategy() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.Cache#setMaxElements(long, EvictionStrategy) */
    public void setMaxElements(long maxSize, EvictionStrategy strategy) {
        // TODO: implement this
    }

    /** @see org.moqui.context.Cache#get(String) */
    public V get(String key) {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.Cache#put(String, V) */
    public V put(String key, V value) {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.Cache#remove(String) */
    public Object remove(String key) {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.Cache#keySet() */
    public Set<String> keySet() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.Cache#hasExpired(String) */
    public boolean hasExpired(String key) {
        // TODO: implement this
        return false;
    }

    /** @see org.moqui.context.Cache#containsKey(String) */
    public boolean containsKey(String key) {
        // TODO: implement this
        return false;
    }

    /** @see org.moqui.context.Cache#isEmpty() */
    public boolean isEmpty() {
        // TODO: implement this
        return false;
    }

    /** @see org.moqui.context.Cache#size() */
    public int size() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#clear() */
    public void clear() {
        // TODO: implement this
    }

    /** @see org.moqui.context.Cache#clearExpired() */
    public void clearExpired() {
        // TODO: implement this
    }

    /** @see org.moqui.context.Cache#getHitCount() */
    public long getHitCount() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#getMissCountNotFound() */
    public long getMissCountNotFound() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#getMissCountExpired() */
    public long getMissCountExpired() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#getMissCountTotal() */
    public long getMissCountTotal() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#getRemoveHitCount() */
    public long getRemoveHitCount() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#getRemoveMissCount() */
    public long getRemoveMissCount() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.Cache#clearCounters() */
    public void clearCounters() {
        // TODO: implement this
    }
}
