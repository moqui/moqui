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

import java.util.List;

/** For information about artifacts as they are being executed. */
public interface ArtifactExecutionFacade {
    /** Gets information about the current artifact being executed, and about authentication and authorization for
     * that artifact.
     */
    ArtifactExecutionInfo getCurrentArtifactExecutionInfo();

    /** Gets a list of objects representing artifacts that have been executed to get to the current artifact (last in
     * the list). The first artifact in the list will generally be a screen or a service. If a service is run locally
     * this will trace back to the screen or service that called it, and if a service was called remotely it will be
     * the top of the stack (first in the list).
     */
    List<ArtifactExecutionInfo> getArtifactExecutionInfoStack();
}

/* removing these from the interface because they will be used internally by the framework only, keeping here for future reference
    void pushUserId(String userId);
    String popUserId();

    void pushSessionId(String sessionId);
    String popSessionId();

    void pushArtifactExecutionInfo(ArtifactExecutionInfo artifactExecutionInfo);
    ArtifactExecutionInfo popArtifactExecutionInfo();
 */
