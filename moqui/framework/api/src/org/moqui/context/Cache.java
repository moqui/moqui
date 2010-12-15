/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moqui.context;

import java.util.Set;

/** Interface for a basic cache implementation that supports maximum size, expire time, and soft references.
 */
public interface Cache {
    enum EvictionStrategy { LEAST_RECENTLY_USED, LEAST_FREQUENTLY_USED, LEAST_RECENTLY_ADDED }

    String getName();

    long getExpireTimeIdle();
    void setExpireTimeIdle(long expireTime);
    long getExpireTimeLive();
    void setExpireTimeLive(long expireTime);

    long getMaxElements();
    EvictionStrategy getEvictionStrategy();
    void setMaxElements(long maxSize, EvictionStrategy strategy);

    Object get(String key);
    Object put(String key, Object value);
    Object remove(String key);

    Set<String> keySet();
    boolean hasExpired(String key);
    boolean containsKey(String key);

    boolean isEmpty();
    int size();

    void clear();
    void clearExpired();

    long getHitCount();
    long getMissCountNotFound();
    long getMissCountExpired();
    long getMissCountTotal();
    long getRemoveHitCount();
    long getRemoveMissCount();
    void clearCounters();
}
