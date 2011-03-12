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
import org.moqui.entity.EntityList
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityCondition.ComparisonOperator
import org.moqui.entity.EntityValue

public class ArtifactExecutionFacadeImpl implements ArtifactExecutionFacade {

    protected ExecutionContextImpl eci
    protected Deque<ArtifactExecutionInfoImpl> artifactExecutionInfoStack = new LinkedList<ArtifactExecutionInfoImpl>()

    ArtifactExecutionFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#peek() */
    public ArtifactExecutionInfo peek() { return this.artifactExecutionInfoStack.peek() }

    /** @see org.moqui.context.ArtifactExecutionFacade#pop() */
    public ArtifactExecutionInfo pop() { return this.artifactExecutionInfoStack.pop() }

    /** @see org.moqui.context.ArtifactExecutionFacade#push(ArtifactExecutionInfo) */
    public void push(ArtifactExecutionInfo aei) {
        ArtifactExecutionInfoImpl aeii = (ArtifactExecutionInfoImpl) aei

        // do permission check for this new aei that current user is trying to access
        String userId = eci.user.userId
        ArtifactExecutionInfoImpl lastAeii = artifactExecutionInfoStack.peek()

        EntityFind aacvFind = eci.entity.makeFind("ArtifactAuthzCheckView")
                .condition([userId:userId, artifactName:aeii.name, artifactTypeEnumId:aeii.typeEnumId])
        if (aeii.actionEnumId) {
            aacvFind.condition("authzActionEnumId", ComparisonOperator.IN, [aeii.actionEnumId, "AUTHZA_ALL"])
        } else {
            aacvFind.condition("authzActionEnumId", "AUTHZA_ALL")
        }
        EntityList aacvList = aacvFind.useCache(true).list()

        if (aacvList.size() > 0) {
            boolean foundDeny = false
            for (EntityValue aacv in aacvList) {
                if (aacv.authzTypeEnumId == "AUTHZT_ALLOW") {
                    // TODO
                } else if (aacv.authzTypeEnumId == "AUTHZT_DENY") {
                    // TODO
                } else if (aacv.authzTypeEnumId == "AUTHZT_ALWAYS") {
                    // if always allow don't need to check for deny or anything
                    aeii.setAuthorizedUserId(userId)
                    aeii.setAuthorizedAuthzTypeId((String) aacv.authzTypeEnumId)
                    aeii.setAuthorizationInheritable(aacv.inheritAuthz == "Y")
                    this.artifactExecutionInfoStack.push(aeii)
                    return
                }
            }
        } else {
            // no perms found for this, only allow if the current AEI has inheritable auth and same user
            if (lastAeii && lastAeii.authorizationInheritable && lastAeii.authorizedUserId == userId) {
                aeii.setAuthorizedUserId(userId)
                aeii.setAuthorizedAuthzTypeId(lastAeii.authorizedAuthzTypeId)
                aeii.setAuthorizationInheritable(true)
                this.artifactExecutionInfoStack.push(aeii)
                return
            }
        }

        // if we got here no authz found, blow up
        throw new IllegalAccessException("User [${userId}] is not authorized for [${aeii.name}]. [type:${aeii.typeEnumId},action:${aeii.actionEnumId}]")
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#getStack() */
    public Deque<ArtifactExecutionInfo> getStack() { return this.artifactExecutionInfoStack }
}
