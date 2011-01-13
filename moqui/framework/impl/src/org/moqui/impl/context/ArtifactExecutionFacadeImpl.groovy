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
    protected Deque<ArtifactExecutionInfo> artifactExecutionInfoStack = new LinkedList<ArtifactExecutionInfo>()

    ArtifactExecutionFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#peek() */
    public ArtifactExecutionInfo peek() { return this.artifactExecutionInfoStack.peek() }

    /** @see org.moqui.context.ArtifactExecutionFacade#pop() */
    public ArtifactExecutionInfo pop() { return this.artifactExecutionInfoStack.pop() }

    /** @see org.moqui.context.ArtifactExecutionFacade#push(ArtifactExecutionInfo) */
    public void push(ArtifactExecutionInfo aei) { this.artifactExecutionInfoStack.push(aei) }

    /** @see org.moqui.context.ArtifactExecutionFacade#getStack() */
    public Deque<ArtifactExecutionInfo> getStack() { return this.artifactExecutionInfoStack }
}
