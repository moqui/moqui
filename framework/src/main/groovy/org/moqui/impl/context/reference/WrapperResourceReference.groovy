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

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ResourceReference

class WrapperResourceReference extends BaseResourceReference  {
    ResourceReference rr = null

    WrapperResourceReference() { }
    
    ResourceReference init(String location, ExecutionContextFactory ecf) {
        this.ecf = ecf
        return this
    }

    ResourceReference init(ResourceReference rr, ExecutionContextFactory ecf) {
        this.rr = rr
        this.ecf = ecf
        return this
    }

    String getLocation() { return rr.getLocation() }

    InputStream openStream() { return rr.openStream() }
    String getText() { return rr.getText() }

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

    boolean supportsSize() { return rr.supportsSize() }
    long getSize() { return rr.getSize() }

    boolean supportsWrite() { return rr.supportsWrite() }
    void putText(String text) { rr.putText(text) }
    void putStream(InputStream stream) { rr.putStream(stream) }
    void move(String newLocation) { rr.move(newLocation) }

    void destroy() { rr.destroy() }
}
