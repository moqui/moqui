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
