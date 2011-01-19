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

import java.util.List;

public interface ScreenRender {
    /** Location of the root XML Screen file to render.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender rootScreen(String screenLocation);

    /** A list of screen names used to determine which screens to use when rendering subscreens.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender screenPath(List<String> screenNameList);

    /** The type of output. Used to select sub-elements of the <code>platform-specific</code> element and the default
     * macro template (if one is not specified for this render).
     *
     * If macroTemplateLocation is not specified is also used to determine the default macro template
     * based on configuration. Can be anything. Default supported values include: text, html, xsl-fo,
     * xml, and csv.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender outputType(String outputType);

    /** The MIME character encoding for the text produced. Defaults to <code>UTF-8</code>. Must be a valid charset in
     * the java.nio.charset.Charset class.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender encoding(String characterEncoding);

    /** Location of an FTL file with macros used to generate output. If not specified macro file from the screen
     * configuration will be used depending on the outputType.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender macroTemplate(String macroTemplateLocation);

    /** Render a screen to an appender using the current context. The screen will run in a sub-context so the original
     * context will not be changed.
     */
    void render(Appendable appender);

    /** Render a screen and return the output as a String. Context semantics are the same as other render methods. */
    String render();
}
