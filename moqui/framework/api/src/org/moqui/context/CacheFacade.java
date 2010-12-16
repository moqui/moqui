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

/** A facade used for managing and accessing Cache instances. */
public interface CacheFacade {
    void clearAllCaches();
    void clearExpiredFromAllCaches();
    void clearCachesByPrefix(String prefix);

    /** Get the named Cache, creating one based on configuration and defaults if none exists. */
    Cache getCache(String cacheName);
}
