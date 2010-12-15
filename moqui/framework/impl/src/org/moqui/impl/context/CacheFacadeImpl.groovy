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

public class CacheFacadeImpl implements CacheFacade {

    ExecutionContextFactoryImpl ecfi;

    public CacheFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

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
        // TODO: implement this
        return null;
    }
}
