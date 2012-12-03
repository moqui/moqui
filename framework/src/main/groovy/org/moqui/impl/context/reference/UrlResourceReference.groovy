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

class UrlResourceReference extends BaseResourceReference {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UrlResourceReference.class)

    URL locationUrl = null
    Boolean exists = null
    boolean isFile = false
    File localFile = null

    UrlResourceReference() { }
    
    @Override
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        if (location.startsWith("/") || location.indexOf(":") < 0) {
            // no prefix, local file: if starts with '/' is absolute, otherwise is relative to runtime path
            if (location.charAt(0) != '/') location = ec.ecfi.runtimePath + '/' + location
            locationUrl = new URL("file:" + location)
            isFile = true
        } else {
            locationUrl = new URL(location)
            isFile = (locationUrl?.protocol == "file")
        }
        return this
    }

    File getFile() {
        if (localFile != null) return localFile
        // NOTE: using toExternalForm().substring(5) instead of toURI because URI does not allow spaces in a filename
        localFile = new File(locationUrl.toExternalForm().substring(5))
        return localFile
    }

    @Override
    String getLocation() { return locationUrl?.toString() }

    @Override
    URI getUri() { return locationUrl?.toURI() }
    @Override
    String getFileName() {
        if (!locationUrl) return null
        String path = locationUrl.getPath()
        return path.contains("/") ? path.substring(path.lastIndexOf("/")+1) : path
    }

    @Override
    InputStream openStream() { return locationUrl?.openStream() }

    @Override
    String getText() { return StupidUtilities.getStreamText(openStream()) }

    @Override
    String getContentType() {
        if (!locationUrl) return null
        ec.resource.getContentType(getFileName())
    }

    @Override
    boolean supportsAll() { isFile }

    @Override
    boolean supportsUrl() { return true }
    @Override
    URL getUrl() { return locationUrl }

    @Override
    boolean supportsDirectory() { isFile }
    @Override
    boolean isFile() {
        if (isFile) {
            return getFile().isFile()
        } else {
            throw new IllegalArgumentException("Is file not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }
    @Override
    boolean isDirectory() {
        if (isFile) {
            return getFile().isDirectory()
        } else {
            throw new IllegalArgumentException("Is directory not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }
    @Override
    List<ResourceReference> getDirectoryEntries() {
        if (isFile) {
            File f = getFile()
            List<ResourceReference> children = new LinkedList<ResourceReference>()
            for (File dirFile in f.listFiles()) {
                children.add(new UrlResourceReference().init(dirFile.absolutePath, ec))
            }
            return children
        } else {
            throw new IllegalArgumentException("Children not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }

    @Override
    boolean supportsExists() { return isFile || exists != null }
    @Override
    boolean getExists() {
        if (exists != null) return exists

        if (isFile) {
            exists = getFile().exists()
            return exists
        } else {
            throw new IllegalArgumentException("Exists not supported for resource with protocol [${locationUrl?.protocol}]")
        }
    }

    boolean supportsLastModified() { isFile }
    long getLastModified() {
        if (isFile) {
            return getFile().lastModified()
        } else {
            System.currentTimeMillis()
        }
    }
}
