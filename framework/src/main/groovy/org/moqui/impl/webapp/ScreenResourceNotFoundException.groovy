/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.webapp;

import org.moqui.impl.screen.ScreenDefinition;

public class ScreenResourceNotFoundException extends RuntimeException {
    ScreenDefinition rootSd;
    List<String> fullPathNameList;
    ScreenDefinition lastSd;
    String pathFromLastScreen;
    public ScreenResourceNotFoundException(ScreenDefinition rootSd, List<String> fullPathNameList,
                                           ScreenDefinition lastSd, String pathFromLastScreen, String resourceLocation,
                                           Exception cause) {
        super("Could not find subscreen or transition or file/content [" + pathFromLastScreen +
                (resourceLocation ? ":" + resourceLocation : "") + "] under screen [" +
                lastSd.getLocation() + "] while finding url for path " + fullPathNameList + " under from screen [" +
                rootSd.getLocation() + "]", cause)
        this.rootSd = rootSd
        this.fullPathNameList = fullPathNameList
        this.lastSd = lastSd
        this.pathFromLastScreen = pathFromLastScreen
    }
}
