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

import java.util.logging.Level;

/** For trace, error, etc logging to the console, files, etc. */
public interface LoggerFacade {
    /** Log a message and/or Throwable error at the given level.
     *
     * @param level
     * @param message The message text to log. If contains ${} syntax will be expanded from the current context.
     * @param thrown
     */
    void log(Level level, String message, Throwable thrown);

    /** Is the given LoggingLevel enabled? */
    boolean logEnabled(Level level);
}
