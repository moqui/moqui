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

import javax.jcr.Session
import javax.jcr.Property

import org.moqui.context.ExecutionContext
import org.moqui.context.ResourceReference
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ResourceFacadeImpl

class ContentResourceReference implements ResourceReference {

    ExecutionContext ec = null

    URI locationUri
    String repositoryName
    String nodePath

    protected javax.jcr.Node theNode = null

    ContentResourceReference() { }
    
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        this.locationUri = new URI(location)

        String strippedLoc = locationUri.path
        if (strippedLoc.contains("/")) {
            repositoryName = strippedLoc.substring(0, strippedLoc.indexOf("/"))
            // NOTE: the nodePath should include a leading /
            nodePath = strippedLoc.substring(strippedLoc.indexOf("/"))
        } else {
            repositoryName = strippedLoc
            nodePath = "/"
        }

        return this
    }

    ResourceReference init(String repositoryName, javax.jcr.Node node, ExecutionContext ec) {
        this.repositoryName = repositoryName
        this.nodePath = node.path
        this.locationUri = new URI("content", repositoryName, nodePath, null, null)
        this.theNode = node
        return this
    }

    String getLocation() { return locationUri.toString() }

    URI getUri() { return locationUri }
    String getFileName() {
        return nodePath.contains("/") ? nodePath.substring(nodePath.lastIndexOf("/")+1) : nodePath
    }

    InputStream openStream() {
        javax.jcr.Node node = getNode()
        if (node == null) return null
        javax.jcr.Node contentNode = node.getNode("jcr:content")
        if (contentNode == null) throw new IllegalArgumentException("Cannot get stream for content at [${repositoryName}][${nodePath}], has no jcr:content child node")
        Property dataProperty = contentNode.getProperty("jcr:data")
        if (dataProperty == null) throw new IllegalArgumentException("Cannot get stream for content at [${repositoryName}][${nodePath}], has no jcr:content.jcr:data property")
        return dataProperty.binary.stream
    }

    String getText() { return StupidUtilities.getStreamText(openStream()) }

    boolean supportsAll() { true }

    boolean supportsUrl() { false }
    URL getUrl() { return null }

    boolean supportsDirectory() { return true }
    boolean isFile() {
        javax.jcr.Node node = getNode()
        if (node == null) return false
        return node.isNodeType("nt:file")
    }
    boolean isDirectory() {
        javax.jcr.Node node = getNode()
        if (node == null) return false
        return node.isNodeType("nt:folder")
    }
    List<ResourceReference> getDirectoryEntries() {
        List<ResourceReference> dirEntries = new LinkedList()
        javax.jcr.Node node = getNode()
        if (node == null) return dirEntries

        for (javax.jcr.Node childNode in node.getNodes()) {
            dirEntries.add(new ContentResourceReference().init(repositoryName, childNode, ec))
        }
        return dirEntries
    }

    boolean supportsExists() { return true }
    boolean getExists() {
        if (theNode != null) return true
        Session session = ((ResourceFacadeImpl) ec.resource).getContentRepositorySession(repositoryName)
        return session.nodeExists(nodePath)
    }

    void destroy() { }

    @Override
    String toString() { return getLocation() }

    javax.jcr.Node getNode() {
        if (theNode != null) return theNode
        Session session = ((ResourceFacadeImpl) ec.resource).getContentRepositorySession(repositoryName)
        return session.getNode(nodePath)
    }
    /* Some example code for adding a file, shows the various properties and types
        //create the file node - see section 6.7.22.6 of the spec
        Node fileNode = folderNode.addNode (file.getName (), "nt:file");

        //create the mandatory child node - jcr:content
        Node resNode = fileNode.addNode ("jcr:content", "nt:resource");
        resNode.setProperty ("jcr:mimeType", mimeType);
        resNode.setProperty ("jcr:encoding", encoding);
        resNode.setProperty ("jcr:data", valueFactory.createBinary(new FileInputStream (file)));
        Calendar lastModified = Calendar.getInstance ();
        lastModified.setTimeInMillis (file.lastModified ());
        resNode.setProperty ("jcr:lastModified", lastModified);
     */
}
