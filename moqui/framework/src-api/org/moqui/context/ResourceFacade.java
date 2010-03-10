/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moqui.context;

import java.io.InputStream;
import java.net.URL;

/** For accessing resources by location string (http://, jar://, component://, content://, classpath://, etc). */
public interface ResourceFacade {
    /** Get a URL representing the Moqui location string passed.
     *
     * @param location A URL-style location string. In addition to the standard URL protocols (http, https, ftp, jar,
     * and file) can also have the special Moqui protocols of "component://" for a resource location relative to a
     * component base location, "content://" for a resource in the content repository, and "classpath://" to get a
     * resource from the Java classpath.
     */
    URL getLocationUrl(String location);

    /** Open an InputStream to read the contents of a file/document at a location.
     *
     * @param location A URL-style location string that also support the Moqui-specific component and content protocols.
     */
    InputStream getLocationStream(String location);
}
