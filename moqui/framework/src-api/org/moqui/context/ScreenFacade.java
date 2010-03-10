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

/** For rendering screens for general use (mostly for things other than web pages or web page snippets). */
public interface ScreenFacade {
    /** The output types available when rendering a screen as text. */
    enum ScreenTextOutputType { HTML, XSL_FO, XML }

    /** Render a screen to an appender using the current context. The screen will run in a sub-context so the original
     * context will not be changed.
     *
     * @param screenLocation Location of the XML Screen file to render.
     * @param appender Textual output of the rendering will be appended to this.
     * @param outputType The type of output. Used to select sub-elements of the <code>platform-specific</code> element.
     *                   If macroTemplateLocation is not specified is also used to determine the default macro template.
     * @param macroTemplateLocation Location of an FTL file with macros used to generate output.
     */
    void renderScreenText(String screenLocation, Appendable appender, ScreenTextOutputType outputType, String macroTemplateLocation);
}
