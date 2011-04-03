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
import java.io.Writer;
import java.net.URL;

/** For accessing resources by location string (http://, jar://, component://, content://, classpath://, etc). */
public interface ResourceFacade {
    /** Get a ResourceReference representing the Moqui location string passed.
     *
     * @param location A URL-style location string. In addition to the standard URL protocols (http, https, ftp, jar,
     * and file) can also have the special Moqui protocols of "component://" for a resource location relative to a
     * component base location, "content://" for a resource in the content repository, and "classpath://" to get a
     * resource from the Java classpath.
     */
    ResourceReference getLocationReference(String location);

    /** Open an InputStream to read the contents of a file/document at a location.
     *
     * @param location A URL-style location string that also support the Moqui-specific component and content protocols.
     */
    InputStream getLocationStream(String location);

    /** Get the text at the given location, optionally from the cache (resource.text.location). */
    String getLocationText(String location, boolean cache);

    /** Render a template at the given location using the current context and write the output to the given writer. */
    void renderTemplateInCurrentContext(String location, Writer writer);

    /** Run a script at the given location (optionally with the given method, like in a groovy class) using the current
     * context for its variable space.
     *
     * @return The value returned by the script, if any.
     */
    Object runScriptInCurrentContext(String location, String method);

    /** Evaluate a Groovy expression as a condition.
     *
     * @return boolean representing the result of evaluating the expression
     */
    boolean evaluateCondition(String expression, String debugLocation);
    /** Evaluate a Groovy expression as a context field, or more generally as an expression that evaluates to an Object
     * reference.
     *
     * @return Object reference representing result of evaluating the expression
     */
    Object evaluateContextField(String expression, String debugLocation);
    /** Evaluate a Groovy expression as a GString to be expanded/interpolated into a simple String.
     *
     * NOTE: the inputString is always run through the L10nFacade.getLocalizedMessage() method before evaluating the
     * expression in order to implicitly internationalize string expansion.
     *
     * @return String representing localized and expanded inputString
     */
    String evaluateStringExpand(String inputString, String debugLocation);
}
