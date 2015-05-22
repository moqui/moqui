/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

/** For rendering screens for general use (mostly for things other than web pages or web page snippets). */
public interface ScreenFacade {

    /** Make a ScreenRender object to render a screen. */
    ScreenRender makeRender();
}
