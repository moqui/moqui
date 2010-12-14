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
package org.moqui.impl.context

import org.moqui.context.ArtifactExecutionFacade
import org.moqui.context.ArtifactExecutionInfo

public class ArtifactExecutionFacadeImpl implements ArtifactExecutionFacade {

    /** @see org.moqui.context.ArtifactExecutionFacade#getCurrentArtifactExecutionInfo() */
    public ArtifactExecutionInfo getCurrentArtifactExecutionInfo() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#getArtifactExecutionInfoStack() */
    public List<ArtifactExecutionInfo> getArtifactExecutionInfoStack() {
        // TODO: implement this
        return null;
    }

    void pushUserId(String userId) {

    }
    String popUserId() {

    }

    void pushSessionId(String sessionId) {
        
    }
    String popSessionId() {

    }

    void pushArtifactExecutionInfo(ArtifactExecutionInfo artifactExecutionInfo) {

    }
    ArtifactExecutionInfo popArtifactExecutionInfo() {

    }
}
