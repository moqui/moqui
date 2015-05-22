/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.service;

import java.util.Map;

public interface ServiceCallback {
    public boolean isEnabled();
    public void receiveEvent(Map<String, Object> context, Map<String, Object> result);
    public void receiveEvent(Map<String, Object> context, Throwable t);
}
