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

class ContentResourceReference extends BaseResourceReference {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ContentResourceReference.class)

    URI locationUri
    String repositoryName
    String nodePath

    protected javax.jcr.Node theNode = null

    ContentResourceReference() { }
    
    @Override
    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec

        locationUri = new URI(location)
        repositoryName = locationUri.host
        nodePath = locationUri.path

        return this
    }

    ResourceReference init(String repositoryName, javax.jcr.Node node, ExecutionContext ec) {
        this.repositoryName = repositoryName
        this.nodePath = node.path
        this.locationUri = new URI("content", repositoryName, nodePath, null, null)
        this.theNode = node
        return this
    }

    @Override
    String getLocation() { return locationUri.toString() }

    @Override
    URI getUri() { return locationUri }
    @Override
    String getFileName() {
        return nodePath.contains("/") ? nodePath.substring(nodePath.lastIndexOf("/")+1) : nodePath
    }

    @Override
    InputStream openStream() {
        javax.jcr.Node node = getNode()
        if (node == null) return null
        javax.jcr.Node contentNode = node.getNode("jcr:content")
        if (contentNode == null) throw new IllegalArgumentException("Cannot get stream for content at [${repositoryName}][${nodePath}], has no jcr:content child node")
        Property dataProperty = contentNode.getProperty("jcr:data")
        if (dataProperty == null) throw new IllegalArgumentException("Cannot get stream for content at [${repositoryName}][${nodePath}], has no jcr:content.jcr:data property")
        return dataProperty.binary.stream
    }

    @Override
    String getText() { return StupidUtilities.getStreamText(openStream()) }

    @Override
    String getContentType() { ec.resource.getContentType(getFileName()) }

    @Override
    boolean supportsAll() { true }

    @Override
    boolean supportsUrl() { false }
    @Override
    URL getUrl() { return null }

    @Override
    boolean supportsDirectory() { return true }
    @Override
    boolean isFile() {
        javax.jcr.Node node = getNode()
        if (node == null) return false
        return node.isNodeType("nt:file")
    }
    @Override
    boolean isDirectory() {
        javax.jcr.Node node = getNode()
        if (node == null) return false
        return node.isNodeType("nt:folder")
    }
    @Override
    List<ResourceReference> getDirectoryEntries() {
        List<ResourceReference> dirEntries = new LinkedList()
        javax.jcr.Node node = getNode()
        if (node == null) return dirEntries

        for (javax.jcr.Node childNode in node.getNodes()) {
            dirEntries.add(new ContentResourceReference().init(repositoryName, childNode, ec))
        }
        return dirEntries
    }
    // TODO: consider overriding findChildResource() to let the JCR impl do the query
    // ResourceReference findChildResource(String relativePath)

    @Override
    boolean supportsExists() { return true }
    @Override
    boolean getExists() {
        if (theNode != null) return true
        Session session = ((ResourceFacadeImpl) ec.resource).getContentRepositorySession(repositoryName)
        return session.nodeExists(nodePath)
    }

    boolean supportsLastModified() { return false }
    long getLastModified() {
        // TODO: more research to see if we can get a last modified time
        System.currentTimeMillis()
    }

    javax.jcr.Node getNode() {
        if (theNode != null) return theNode
        Session session = ((ResourceFacadeImpl) ec.resource).getContentRepositorySession(repositoryName)
        return session.getNode(nodePath)
    }
}
