/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
