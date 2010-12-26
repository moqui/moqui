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

    protected ExecutionContextImpl eci

    protected List<ArtifactExecutionInfo> artifactExecutionInfoStack = new LinkedList<ArtifactExecutionInfo>()

    ArtifactExecutionFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#getCurrentArtifactExecutionInfo() */
    public ArtifactExecutionInfo getCurrentArtifactExecutionInfo() {
        return this.artifactExecutionInfoStack ? this.artifactExecutionInfoStack[0] : null
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#getArtifactExecutionInfoStack() */
    public List<ArtifactExecutionInfo> getArtifactExecutionInfoStack() {
        return this.artifactExecutionInfoStack
    }

    void pushArtifactExecutionInfo(ArtifactExecutionInfo artifactExecutionInfo) {
        this.artifactExecutionInfoStack.add(0, artifactExecutionInfo)
    }

    ArtifactExecutionInfo popArtifactExecutionInfo() {
        return this.artifactExecutionInfoStack ? this.artifactExecutionInfoStack.remove(0) : null
    }

    void pushUserId(String userId) {
        // TODO: what to do with this? leave for now, possibly eliminate later
    }
    String popUserId() {
        // TODO: what to do with this? leave for now, possibly eliminate later
        return null
    }

    void pushSessionId(String sessionId) {
        // TODO: what to do with this? leave for now, possibly eliminate later
    }
    String popSessionId() {
        // TODO: what to do with this? leave for now, possibly eliminate later
        return null
    }
}
