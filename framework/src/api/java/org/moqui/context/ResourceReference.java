/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

public interface ResourceReference {
    ResourceReference init(String location, ExecutionContextFactory ecf);

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

    /** Get the entries of a directory */
    List<ResourceReference> getDirectoryEntries();
    /** Get the parent directory, null if it is the root (no parent). */
    ResourceReference getParent();

    /** Find the directory with a name that matches the current filename (minus the extension) */
    ResourceReference findMatchingDirectory();
    /** Get a reference to the child of this directory or this file in the matching directory */
    ResourceReference getChild(String name);
    /** Get a list of references to all files in this directory or for a file in the matching directory */
    List<ResourceReference> getChildren();
    /** Find a file by path (can be single name) in the matching directory and child matching directories */
    ResourceReference findChildFile(String relativePath);
    /** Find a directory by path (can be single name) in the matching directory and child matching directories */
    ResourceReference findChildDirectory(String relativePath);

    boolean supportsExists();
    boolean getExists();

    boolean supportsLastModified();
    long getLastModified();

    boolean supportsSize();
    long getSize();

    boolean supportsWrite();
    void putText(String text);
    void putStream(InputStream stream);
    void move(String newLocation);
    ResourceReference makeDirectory(String name);
    ResourceReference makeFile(String name);
    boolean delete();

    void destroy();
}
