/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
    ResourceReference makeDirectory(String name) { return rr.makeDirectory(name) }
    ResourceReference makeFile(String name) { return rr.makeFile(name) }
    boolean delete() { return rr.delete() }

    void destroy() { rr.destroy() }
}
