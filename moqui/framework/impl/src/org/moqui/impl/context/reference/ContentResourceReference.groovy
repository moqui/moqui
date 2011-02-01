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
package org.moqui.impl.context.reference

import org.moqui.context.ExecutionContext
import org.moqui.context.ResourceReference
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ExecutionContextImpl

class ContentResourceReference implements ResourceReference {
    ExecutionContext ec = null

    ContentResourceReference() { }
    
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        return this
    }

    String getLocation() { return null /* TODO */ }

    URI getUri() { return null /* TODO */ }
    String getFileName() {
        String path = getUri().getPath()
        return path.contains("/") ? path.substring(path.lastIndexOf("/")+1) : path
    }

    InputStream openStream() { return null /* TODO */ }

    String getText() { return StupidUtilities.getStreamText(openStream()) }

    boolean supportsAll() { true }

    boolean supportsUrl() { false }
    URL getUrl() { return null }

    boolean supportsDirectory() { return true }
    boolean isFile() {
        /* TODO */
        false
    }
    boolean isDirectory() {
        /* TODO */
        false
    }
    List<ResourceReference> getDirectoryEntries() {
        /* TODO */
        null
    }

    boolean supportsExists() { return true }
    boolean getExists() {
        /* TODO */
        false
    }

    void destroy() { }

    @Override
    String toString() { return getLocation() }
}
