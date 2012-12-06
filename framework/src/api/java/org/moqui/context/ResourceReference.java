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
package org.moqui.context;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

public interface ResourceReference {
    ResourceReference init(String location, ExecutionContext ec);

    String getLocation();

    URI getUri();

    /** One part of the URI not easy to get from the URI object, basically the last part of the path. */
    String getFileName();

    InputStream openStream();
    String getText();

    /** The content (MIME) type for this content, if known or can be determined. */
    String getContentType();

    boolean supportsAll();

    boolean supportsUrl();
    URL getUrl();

    boolean supportsDirectory();
    boolean isFile();
    boolean isDirectory();
    List<ResourceReference> getDirectoryEntries();
    ResourceReference findChildResource(String relativePath);

    boolean supportsExists();
    boolean getExists();

    boolean supportsLastModified();
    long getLastModified();

    boolean supportsWrite();
    void putText(String text);
    void putStream(InputStream stream);

    void destroy();
}
