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

public class CacheFacadeImpl implements CacheFacade {

    protected final ExecutionContextFactoryImpl ecfi;
    
    /** This is the Ehcache CacheManager singleton for use in Moqui.
     * Gets config from the default location, ie the ehcache.xml file from the classpath.
     */
    protected final CacheManager cacheManager = new CacheManager();

    public CacheFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

    public void destroy() {
        this.cacheManager.shutdown();
    }

    // ========== Interface Method Implementations ==========

    /** @see org.moqui.context.CacheFacade#clearAllCaches() */
    public void clearAllCaches() {
        // TODO: implement this
    }

    /** @see org.moqui.context.CacheFacade#clearExpiredFromAllCaches() */
    public void clearExpiredFromAllCaches() {
        // TODO: implement this
    }

    /** @see org.moqui.context.CacheFacade#clearCachesByPrefix(String) */
    public void clearCachesByPrefix(String prefix) {
        // TODO: implement this
    }

    /** @see org.moqui.context.CacheFacade#getCache(String) */
    public Cache getCache(String cacheName) {
        if (this.cacheManager.cacheExists(cacheName)) {
            // CacheImpl is a really lightweight object, but we should still consider keeping a local map of references
            return new CacheImpl(cacheManager.getCache(cacheName));
        } else {
            // TODO: if not, create a new cache using settings from the moqui conf file (if there are settings that match the cacheName)

        }



        return null;
    }
}
