/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import java.io.Serializable;
import java.util.Set;

/** Interface for a basic cache implementation that supports maximum size, expire time, and soft references.
 */
public interface Cache {
    public final static EvictionStrategy LEAST_RECENTLY_USED = EvictionStrategy.LEAST_RECENTLY_USED;
    public final static EvictionStrategy LEAST_FREQUENTLY_USED = EvictionStrategy.LEAST_FREQUENTLY_USED;
    public final static EvictionStrategy LEAST_RECENTLY_ADDED = EvictionStrategy.LEAST_RECENTLY_ADDED;

    enum EvictionStrategy { LEAST_RECENTLY_USED, LEAST_FREQUENTLY_USED, LEAST_RECENTLY_ADDED }

    String getName();

    long getExpireTimeIdle();
    void setExpireTimeIdle(long expireTime);
    long getExpireTimeLive();
    void setExpireTimeLive(long expireTime);

    long getMaxElements();
    EvictionStrategy getEvictionStrategy();
    void setMaxElements(long maxSize, EvictionStrategy strategy);

    Object get(Serializable key);
    Object put(Serializable key, Object value);
    Object remove(Serializable key);

    Set<Serializable> keySet();
    boolean hasExpired(Serializable key);
    boolean containsKey(Serializable key);

    boolean isEmpty();
    int size();

    void clear();
    void clearExpired();

    long getHitCount();
    long getMissCountNotFound();
    long getMissCountExpired();
    long getMissCountTotal();
    long getRemoveCount();
    void clearCounters();
}
