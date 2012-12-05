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

class WrapperResourceReference extends BaseResourceReference  {
    ResourceReference rr = null

    WrapperResourceReference() { }
    
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        return this
    }

    ResourceReference init(ResourceReference rr, ExecutionContext ec) {
        this.rr = rr
        this.ec = ec
        return this
    }

    String getLocation() { return rr.getLocation() }
    URI getUri() { return rr.getUri() }
    String getFileName() { rr.getFileName() }

    InputStream openStream() { return rr.openStream() }
    String getText() { return rr.getText() }
    String getContentType() { return rr.getContentType() }

    boolean supportsAll() { return rr.supportsAll() }

    boolean supportsUrl() { return rr.supportsUrl() }
    URL getUrl() { return rr.getUrl() }

    boolean supportsDirectory() { return rr.supportsDirectory() }
    boolean isFile() { return rr.isFile() }
    boolean isDirectory() { return rr.isDirectory() }
    List<ResourceReference> getDirectoryEntries() { return rr.getDirectoryEntries() }

    boolean supportsExists() { return rr.supportsExists() }
    boolean getExists() { return rr.getExists()}

    boolean supportsLastModified() { return rr.supportsLastModified() }
    long getLastModified() { return rr.getLastModified() }

    boolean supportsWrite() { return rr.supportsWrite() }
    void putText(String text) { rr.putText(text) }
    OutputStream openOutputStream() { return rr.openOutputStream() }

    void destroy() { rr.destroy() }
}
