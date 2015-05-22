/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import java.util.Deque;
import java.util.List;

/** For information about artifacts as they are being executed. */
public interface ArtifactExecutionFacade {
    /** Gets information about the current artifact being executed, and about authentication and authorization for
     * that artifact.
     *
     * @return Current (most recent) ArtifactExecutionInfo
     */
    ArtifactExecutionInfo peek();

    ArtifactExecutionInfo pop();
    void push(ArtifactExecutionInfo aei, boolean requiresAuthz);
    void push(String name, String typeEnumId, String actionEnumId, boolean requiresAuthz);

    /** Gets a stack/deque/list of objects representing artifacts that have been executed to get to the current artifact.
     * The bottom artifact in the stack will generally be a screen or a service. If a service is run locally
     * this will trace back to the screen or service that called it, and if a service was called remotely it will be
     * the bottom of the stack.
     *
     * @return Actual ArtifactExecutionInfo stack/deque object
     */
    Deque<ArtifactExecutionInfo> getStack();

    List<ArtifactExecutionInfo> getHistory();
    String printHistory();

    /** Disable authorization checks for the current ExecutionContext only.
     * This should be used when the system automatically does something (possible based on a user action) that the user
     * would not generally have permission to do themselves.
     *
     * @return boolean representing previous state of disable authorization (true if was disabled, false if not). If
     *         this is true, you should not enableAuthz when you are done and instead allow whichever code first did the
     *         disable to enable it.
     */
    boolean disableAuthz();
    /** Enable authorization after a disableAuthz() call. Not that this should be done in a finally block with the code
     * following the disableAuthz() in the corresponding try block. If this is not in a finally block an exception may
     * result in authorizations being disabled for the rest of the scope of the ExecutionContext (a potential security
     * whole).
     */
    void enableAuthz();

    void setAnonymousAuthorizedAll();
    void setAnonymousAuthorizedView();

    /** Disable Entity Facade ECA rules (for this thread/ExecutionContext only, does not affect other things happening
     * in the system).
     * @return boolean following same pattern as disableAuthz(), and should be handled the same way.
     */
    boolean disableEntityEca();
    /** Disable Entity Facade ECA rules (for this thread/ExecutionContext only, does not affect other things happening
     * in the system).
     */
    void enableEntityEca();
}
