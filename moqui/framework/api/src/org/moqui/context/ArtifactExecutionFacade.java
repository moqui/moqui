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

    /** Gets a stack/deque/list of objects representing artifacts that have been executed to get to the current artifact.
     * The bottom artifact in the stack will generally be a screen or a service. If a service is run locally
     * this will trace back to the screen or service that called it, and if a service was called remotely it will be
     * the bottom of the stack.
     *
     * @return Actual ArtifactExecutionInfo stack/deque object
     */
    Deque<ArtifactExecutionInfo> getStack();

    List<ArtifactExecutionInfo> getHistory();
}
