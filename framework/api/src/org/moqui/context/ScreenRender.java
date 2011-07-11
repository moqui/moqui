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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.util.List;

public interface ScreenRender {
    /** Location of the root XML Screen file to render.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender rootScreen(String screenLocation);

    /** Determine location of the root XML Screen file to render based on a host name.
     *
     * @param host The host name, usually from ServletRequest.getServerName()
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender rootScreenFromHost(String host);

    /** A list of screen names used to determine which screens to use when rendering subscreens.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender screenPath(List<String> screenNameList);

    /** The mode to render for (type of output). Used to select sub-elements of the <code>render-mode</code>
     * element and the default macro template (if one is not specified for this render).
     *
     * If macroTemplateLocation is not specified is also used to determine the default macro template
     * based on configuration.
     *
     * @param outputType Can be anything. Default supported values include: text, html, xsl-fo, xml, and csv.
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender renderMode(String outputType);

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

    /** If specified will be used as the base URL for links. If not specified the base URL will come from configuration
     * on the webapp-list.webapp element and the servletContextPath.
     *
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender baseLinkUrl(String baseLinkUrl);

    /** If baseLinkUrl is not specified then this is used along with the webapp-list.webapp configuration to create
     * a base URL. If this is not specified and the active ExecutionContext has a WebFacade active then it will get
     * it from that (meaning with a WebFacade this is not necessary to get a correct result).
     *
     * @param scp The servletContext.contextPath
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender servletContextPath(String scp);

    /** The webapp name to use to look up webapp (webapp-list.webapp.@name) settings for URL building, request actions
     * running, etc.
     *
     * @param wan The webapp name
     * @return Reference to this ScreenRender for convenience
     */
    ScreenRender webappName(String wan);

    /** Render a screen to a response using the current context. The screen will run in a sub-context so the original
     * context will not be changed. The request will be used to check web settings such as secure connection, etc.
     */
    void render(HttpServletRequest request, HttpServletResponse response);

    /** Render a screen to a writer using the current context. The screen will run in a sub-context so the original
     * context will not be changed.
     */
    void render(Writer writer);

    /** Render a screen and return the output as a String. Context semantics are the same as other render methods. */
    String render();
}
