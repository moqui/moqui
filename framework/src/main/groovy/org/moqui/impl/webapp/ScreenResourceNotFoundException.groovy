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
package org.moqui.impl.webapp;

import org.moqui.impl.screen.ScreenDefinition;

import java.util.List;

public class ScreenResourceNotFoundException extends RuntimeException {
    ScreenDefinition rootSd;
    List<String> fullPathNameList;
    ScreenDefinition lastSd;
    String pathFromLastScreen;
    public ScreenResourceNotFoundException(ScreenDefinition rootSd, List<String> fullPathNameList,
                                           ScreenDefinition lastSd, String pathFromLastScreen, Exception cause) {
        super("Could not find subscreen or transition or file/content [" + pathFromLastScreen + "] under screen [" +
                lastSd.getLocation() + "] while finding url for path " + fullPathNameList + " under root screen [" +
                rootSd.getLocation() + "]", cause)
        this.rootSd = rootSd
        this.fullPathNameList = fullPathNameList
        this.lastSd = lastSd
        this.pathFromLastScreen = pathFromLastScreen
    }
}
