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

    int getMaxElements();
    EvictionStrategy getEvictionStrategy();
    void setMaxElements(int maxSize, EvictionStrategy strategy);

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
