/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.service;

import java.util.Map;
import java.io.Serializable;

/**
 * Service Result Receiver Interface
 */
public interface ServiceResultReceiver extends Serializable {
    /**
     * Receive the result of an asynchronous service call
     * @param result Map of name, value pairs composing the result
     */
    void receiveResult(Map<String, Object> result);

    /**
     * Receive an exception (Throwable) from an asynchronous service cell
     * @param t The Throwable which was received
     */
    void receiveThrowable(Throwable t);
}
