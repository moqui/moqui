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

abstract class BaseResourceReference implements ResourceReference {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BaseResourceReference.class)

    ExecutionContext ec = null
    protected Map<String, ResourceReference> subContentRefByPath = null

    BaseResourceReference() { }

    @Override
    abstract ResourceReference init(String location, ExecutionContext ec);

    protected Map<String, ResourceReference> getSubContentRefByPath() {
        if (subContentRefByPath == null) subContentRefByPath = new HashMap<String, ResourceReference>()
        return subContentRefByPath
    }

    @Override
    abstract String getLocation();

    @Override
    abstract URI getUri();
    @Override
    abstract String getFileName();

    @Override
    abstract InputStream openStream();

    @Override
    abstract String getText();

    @Override
    abstract String getContentType();

    @Override
    abstract boolean supportsAll();

    @Override
    abstract boolean supportsUrl();
    @Override
    abstract URL getUrl();

    @Override
    abstract boolean supportsDirectory();
    @Override
    abstract boolean isFile();
    @Override
    abstract boolean isDirectory();
    @Override
    abstract List<ResourceReference> getDirectoryEntries();
    @Override
    ResourceReference findChildResource(String relativePath) {
        ResourceReference childRef = null

        // logger.warn("============= finding child resource of [${toString()}] path [${relativePath}]")

        // check the cache first
        childRef = getSubContentRefByPath().get(relativePath)
        if (childRef != null && childRef.exists) return childRef

        if (supportsAll()) {
            // this finds a file in a directory with the same name as this resource, unless this resource is a directory
            StringBuilder dirLoc = new StringBuilder(getLocation())
            ResourceReference directoryRef = ec.resource.getLocationReference(dirLoc.toString())
            while (!(directoryRef.exists && directoryRef.isDirectory()) && dirLoc.lastIndexOf(".") > 0) {
                // get rid of one suffix at a time (for screens probably .xml but use .* for other files, etc)
                dirLoc.delete(dirLoc.lastIndexOf("."), dirLoc.length())
                directoryRef = ec.resource.getLocationReference(dirLoc.toString())
            }

            // logger.warn("============= finding child resource path [${relativePath}] directoryRef [${directoryRef}]")
            if (directoryRef.exists) {
                StringBuilder fileLoc = new StringBuilder(dirLoc)
                fileLoc.append(relativePath)

                ResourceReference theFile = ec.resource.getLocationReference(fileLoc.toString())
                if (theFile.exists && theFile.isFile()) childRef = theFile

                // logger.warn("============= finding child resource path [${relativePath}] childRef 1 [${childRef}]")
                if (childRef == null) {
                    // try adding known extensions
                    for (String extToTry in ec.resource.templateRenderers.keySet()) {
                        if (childRef != null) break
                        theFile = ec.resource.getLocationReference(fileLoc.toString() + extToTry)
                        if (theFile.exists && theFile.isFile()) childRef = theFile
                    }
                }

                // logger.warn("============= finding child resource path [${relativePath}] childRef 2 [${childRef}]")
                if (childRef == null) {
                    // didn't find it at a literal path, try searching for it in all subdirectories
                    List<String> relativePathNameList = relativePath.split("/")
                    String childFilename = relativePathNameList.get(relativePathNameList.size()-1)
                    relativePathNameList = relativePathNameList.subList(0, relativePathNameList.size()-1)

                    // search remaining relativePathNameList, ie partial directories leading up to filename
                    for (String relativePathName in relativePathNameList) {
                        directoryRef = internalFindChildDir(directoryRef, relativePathName)
                        if (directoryRef == null) break
                    }

                    // recursively walk the directory tree and find the childFilename
                    childRef = internalFindChildFile(directoryRef, childFilename)
                }
                // logger.warn("============= finding child resource path [${relativePath}] childRef 3 [${childRef}]")
            }
        } else {
            ec.message.addError("Not looking for child resource at [${relativePath}] under space root page [${getLocation()}] because exists, isFile, etc are not supported")
        }

        // put it in the cache before returning
        getSubContentRefByPath().put(relativePath, childRef)

        // logger.warn("============= finding child resource of [${toString()}] path [${relativePath}] got [${childRef}]")
        return childRef
    }

    ResourceReference internalFindChildDir(ResourceReference directoryRef, String childDirName) {
        if (directoryRef == null || !directoryRef.exists) return null
        for (ResourceReference childRef in directoryRef.directoryEntries) {
            if (childRef.isDirectory() && (childRef.fileName == childDirName || childRef.fileName.contains(childDirName + '.'))) {
                // matching directory name, use it
                return childRef
            } else if (childRef.isDirectory()) {
                // non-matching directory name, recurse into it
                return internalFindChildDir(childRef, childDirName)
            }
        }
        return null
    }

    ResourceReference internalFindChildFile(ResourceReference directoryRef, String childFilename) {
        if (directoryRef == null || !directoryRef.exists) return null
        for (ResourceReference childRef in directoryRef.directoryEntries) {
            if (childRef.isFile() && (childRef.fileName == childFilename || childRef.fileName.contains(childFilename + '.'))) {
                return childRef
            } else if (childRef.isDirectory()) {
                return internalFindChildFile(childRef, childFilename)
            }
        }
        return null
    }

    @Override
    abstract boolean supportsExists();
    @Override
    abstract boolean getExists();

    abstract boolean supportsLastModified();
    abstract long getLastModified();

    @Override
    void destroy() { }

    @Override
    String toString() { return getLocation() ?: "[no location (${this.class.getName()})]" }
}
