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

import org.moqui.context.ResourceReference
import org.moqui.context.ExecutionContext
import org.moqui.impl.StupidUtilities

class UrlResourceReference implements ResourceReference {
    URL locationUrl = null
    ExecutionContext ec = null
    Boolean exists = null
    
    UrlResourceReference() { }
    
    ResourceReference init(String location, ExecutionContext ec) {
        if (location.indexOf(":") < 0) {
            // no prefix, local file: if starts with '/' is absolute, otherwise is relative to runtime path
            if (location.charAt(0) != '/') location = ec.ecfi.runtimePath + '/' + location
            locationUrl = new File(location).toURI().toURL()
        } else {
            this.locationUrl = new URL(location)
        }
        this.ec = ec
        return this
    }

    String getLocation() { return locationUrl.toString() }

    URI getUri() { return locationUrl.toURI() }
    String getFileName() {
        String path = locationUrl.getPath()
        return path.contains("/") ? path.substring(path.lastIndexOf("/")+1) : path
    }

    InputStream openStream() { return locationUrl.openStream() }

    String getText() { return StupidUtilities.getStreamText(openStream()) }

    boolean supportsAll() { locationUrl.protocol == "file" }

    boolean supportsUrl() { return true }
    URL getUrl() { return locationUrl }

    boolean supportsDirectory() { return locationUrl.protocol == "file" }
    boolean isFile() {
        if (locationUrl.protocol == "file") {
            File f = new File(locationUrl.toURI())
            return f.isFile()
        } else {
            throw new IllegalArgumentException("Exists not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }
    boolean isDirectory() {
        if (locationUrl.protocol == "file") {
            File f = new File(locationUrl.toURI())
            return f.isDirectory()
        } else {
            throw new IllegalArgumentException("Exists not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }
    List<ResourceReference> getDirectoryEntries() {
        if (locationUrl.protocol == "file") {
            File f = new File(locationUrl.toURI())
            List<ResourceReference> children = new LinkedList<ResourceReference>()
            for (File dirFile in f.listFiles()) {
                children.add(ec.resource.getLocationReference(dirFile.toURI().toString()))
            }
            return children
        } else {
            throw new IllegalArgumentException("Children not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }

    boolean supportsExists() { return locationUrl.protocol == "file" || exists != null }
    boolean getExists() {
        if (exists != null) return exists

        if (locationUrl.protocol == "file") {
            File f = new File(locationUrl.toURI())
            exists = f.exists()
            return exists
        } else {
            throw new IllegalArgumentException("Exists not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }

    void destroy() { }

    @Override
    String toString() { return getLocation() }
}
