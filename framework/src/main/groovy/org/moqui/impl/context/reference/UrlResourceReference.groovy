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
import org.moqui.BaseException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class UrlResourceReference extends BaseResourceReference {
    protected final static Logger logger = LoggerFactory.getLogger(UrlResourceReference.class)

    URL locationUrl = null
    Boolean exists = null
    boolean isFileProtocol = false
    File localFile = null

    UrlResourceReference() { }
    
    @Override
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        if (!location) throw new BaseException("Cannot create URL Resource Reference with empty location")
        if (location.startsWith("/") || location.indexOf(":") < 0) {
            // no prefix, local file: if starts with '/' is absolute, otherwise is relative to runtime path
            if (location.charAt(0) != '/') location = ec.ecfi.runtimePath + '/' + location
            locationUrl = new URL("file:" + location)
            isFileProtocol = true
        } else {
            try {
                locationUrl = new URL(location)
            } catch (MalformedURLException e) {
                logger.trace("Ignoring MalformedURLException for location, trying a local file: ${e.toString()}")
                // special case for Windows, try going through a file:
                locationUrl = new URL("file:/" + location)
            }
            isFileProtocol = (locationUrl?.protocol == "file")
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
    InputStream openStream() { return locationUrl?.openStream() }

    @Override
    String getText() { return StupidUtilities.getStreamText(openStream()) }

    @Override
    boolean supportsAll() { isFileProtocol }

    @Override
    boolean supportsUrl() { return true }
    @Override
    URL getUrl() { return locationUrl }

    @Override
    boolean supportsDirectory() { isFileProtocol }
    @Override
    boolean isFile() {
        if (isFileProtocol) {
            return getFile().isFile()
        } else {
            throw new IllegalArgumentException("Is file not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }
    @Override
    boolean isDirectory() {
        if (isFileProtocol) {
            return getFile().isDirectory()
        } else {
            throw new IllegalArgumentException("Is directory not supported for resource with protocol [${locationUrl.protocol}]")
        }
    }
    @Override
    List<ResourceReference> getDirectoryEntries() {
        if (isFileProtocol) {
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
    boolean supportsExists() { return isFileProtocol || exists != null }
    @Override
    boolean getExists() {
        // only count exists if true
        if (exists) return true

        if (isFileProtocol) {
            exists = getFile().exists()
            return exists
        } else {
            throw new IllegalArgumentException("Exists not supported for resource with protocol [${locationUrl?.protocol}]")
        }
    }

    boolean supportsLastModified() { isFileProtocol }
    long getLastModified() {
        if (isFileProtocol) {
            return getFile().lastModified()
        } else {
            System.currentTimeMillis()
        }
    }

    boolean supportsWrite() { isFileProtocol }
    void putText(String text) {
        // first make sure the directory exists that this is in
        if (!getFile().parentFile.exists()) getFile().parentFile.mkdirs()
        // now write the text to the file and close it
        FileWriter fw = new FileWriter(getFile())
        fw.write(text)
        fw.close()
        this.exists = null
    }
    void putStream(InputStream stream) {
        // first make sure the directory exists that this is in
        if (!getFile().parentFile.exists()) getFile().parentFile.mkdirs()
        OutputStream os = new FileOutputStream(getFile())
        StupidUtilities.copyStream(stream, os)
        stream.close()
        os.close()
        this.exists = null
    }

    void move(String newLocation) {
        if (!newLocation) throw new IllegalArgumentException("No location specified, not moving resource at ${getLocation()}")
        ResourceReference newRr = ec.resource.getLocationReference(newLocation)

        if (newRr.getUrl().getProtocol() != "file")
            throw new IllegalArgumentException("Location [${newLocation}] is not a file location, not moving resource at ${getLocation()}")
        if (!isFileProtocol)
            throw new IllegalArgumentException("Move not supported for resource [${getLocation()}] with protocol [${locationUrl?.protocol}]")

        String path = newRr.getUrl().toExternalForm().substring(5)
        getFile().renameTo(new File(path))
    }
}
