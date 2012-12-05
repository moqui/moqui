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

    ResourceReference childOfResource = null

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
        // no path to child? that means this resource
        if (!relativePath) return this

        if (!supportsAll()) {
            ec.message.addError("Not looking for child resource at [${relativePath}] under space root page [${getLocation()}] because exists, isFile, etc are not supported")
            return null
        }

        ResourceReference childRef = null
        // logger.warn("============= finding child resource of [${toString()}] path [${relativePath}]")

        // check the cache first
        childRef = getSubContentRefByPath().get(relativePath)
        if (childRef != null && childRef.exists) return childRef

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
            if (fileLoc.charAt(fileLoc.length()-1) == '/') fileLoc.deleteCharAt(fileLoc.length()-1)
            if (relativePath.charAt(0) != '/') fileLoc.append('/')
            fileLoc.append(relativePath)

            ResourceReference theFile = ec.resource.getLocationReference(fileLoc.toString())
            if (theFile.exists && theFile.isFile()) childRef = theFile

            // logger.warn("============= finding child resource path [${relativePath}] childRef 1 [${childRef}]")
            /* this approach is no longer needed; the more flexible approach below will handle this and more:
            if (childRef == null) {
                // try adding known extensions
                for (String extToTry in ec.resource.templateRenderers.keySet()) {
                    if (childRef != null) break
                    theFile = ec.resource.getLocationReference(fileLoc.toString() + extToTry)
                    if (theFile.exists && theFile.isFile()) childRef = theFile
                    // logger.warn("============= finding child resource path [${relativePath}] fileLoc [${fileLoc}] extToTry [${extToTry}] childRef [${theFile}]")
                }
            }
            */

            // logger.warn("============= finding child resource path [${relativePath}] childRef 2 [${childRef}]")
            if (childRef == null) {
                // didn't find it at a literal path, try searching for it in all subdirectories
                List<String> relativePathNameList = relativePath.split("/")
                String childFilename = relativePathNameList.get(relativePathNameList.size()-1)
                relativePathNameList = relativePathNameList.subList(0, relativePathNameList.size()-1)

                ResourceReference childDirectoryRef = directoryRef

                // search remaining relativePathNameList, ie partial directories leading up to filename
                for (String relativePathName in relativePathNameList) {
                    childDirectoryRef = internalFindChildDir(childDirectoryRef, relativePathName)
                    if (childDirectoryRef == null) break
                }

                // recursively walk the directory tree and find the childFilename
                childRef = internalFindChildFile(childDirectoryRef, childFilename)
                // logger.warn("============= finding child resource path [${relativePath}] directoryRef [${directoryRef}] childFilename [${childFilename}] childRef [${childRef}]")
            }
            // logger.warn("============= finding child resource path [${relativePath}] childRef 3 [${childRef}]")

            if (childRef != null && childRef instanceof BaseResourceReference) {
                ((BaseResourceReference) childRef).childOfResource = directoryRef
            }
        }

        if (childRef == null) {
            // still nothing? treat the path to the file as a literal and return it (exists will be false)
            if (directoryRef.exists) {
                childRef = ec.resource.getLocationReference(directoryRef.getLocation() + '/' + relativePath)
                if (childRef instanceof BaseResourceReference) {
                    ((BaseResourceReference) childRef).childOfResource = directoryRef
                }
            } else {
                String newDirectoryLoc = getLocation()
                // pop off the extension, everything past the first dot after the last slash
                int lastSlashLoc = newDirectoryLoc.lastIndexOf("/")
                if (newDirectoryLoc.contains(".")) newDirectoryLoc = newDirectoryLoc.substring(0, newDirectoryLoc.indexOf(".", lastSlashLoc))
                childRef = ec.resource.getLocationReference(newDirectoryLoc + '/' + relativePath)
            }
        } else {
            // put it in the cache before returning, but don't cache the literal reference
            getSubContentRefByPath().put(relativePath, childRef)
        }

        // logger.warn("============= finding child resource of [${toString()}] path [${relativePath}] got [${childRef}]")
        return childRef
    }

    ResourceReference internalFindChildDir(ResourceReference directoryRef, String childDirName) {
        if (directoryRef == null || !directoryRef.exists) return null
        // no child dir name, means this/current dir
        if (!childDirName) return directoryRef

        // try a direct sub-directory, if it is there it's more efficient than a brute-force search
        StringBuilder dirLocation = new StringBuilder(directoryRef.getLocation())
        if (dirLocation.charAt(dirLocation.length()-1) == '/') dirLocation.deleteCharAt(dirLocation.length()-1)
        if (childDirName.charAt(0) != '/') dirLocation.append('/')
        dirLocation.append(childDirName)
        ResourceReference directRef = ec.resource.getLocationReference(dirLocation.toString())
        if (directRef != null && directRef.exists) return directRef

        // if no direct reference is found, try the more flexible search
        for (ResourceReference childRef in directoryRef.directoryEntries) {
            if (childRef.isDirectory() && (childRef.fileName == childDirName || childRef.fileName.contains(childDirName + '.'))) {
                // matching directory name, use it
                return childRef
            } else if (childRef.isDirectory()) {
                // non-matching directory name, recurse into it
                ResourceReference subRef = internalFindChildDir(childRef, childDirName)
                if (subRef != null) return subRef
            }
        }
        return null
    }

    ResourceReference internalFindChildFile(ResourceReference directoryRef, String childFilename) {
        if (directoryRef == null || !directoryRef.exists) return null
        List<ResourceReference> childEntries = directoryRef.directoryEntries
        // look through all files first, ie do a breadth-first search
        for (ResourceReference childRef in childEntries) {
            if (childRef.isFile() && (childRef.fileName == childFilename || childRef.fileName.contains(childFilename + '.'))) {
                return childRef
            }
        }
        for (ResourceReference childRef in childEntries) {
            if (childRef.isDirectory()) {
                ResourceReference subRef = internalFindChildFile(childRef, childFilename)
                if (subRef != null) return subRef
            }
        }
        return null
    }

    String getActualChildPath() {
        if (childOfResource == null) return null
        String parentLocation = childOfResource.getLocation()
        String childLocation = getLocation()
        // this should be true, but just in case:
        if (childLocation.startsWith(parentLocation)) {
            return childLocation.substring(parentLocation.length())
        }
        // if not, what to do?
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
