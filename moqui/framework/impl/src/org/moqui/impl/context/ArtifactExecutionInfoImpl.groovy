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

import org.moqui.context.ArtifactExecutionInfo

class ArtifactExecutionInfoImpl implements ArtifactExecutionInfo {

    protected String name
    protected String typeEnumId
    protected String actionEnumId
    protected String authorizedUserId
    protected String authorizedAuthzTypeId
    protected boolean isAuthorizationInheritable

    ArtifactExecutionInfoImpl(String name, String typeEnumId, String actionEnumId) {
        this.name = name
        this.typeEnumId = typeEnumId
        this.actionEnumId = actionEnumId
        this.authorizedUserId = authorizedUserId
        this.isAuthorizationInheritable = isAuthorizationInheritable
    }

    /** @see org.moqui.context.ArtifactExecutionInfo#getName() */
    String getName() { return this.name }
    /** @see org.moqui.context.ArtifactExecutionInfo#getTypeEnumId() */
    String getTypeEnumId() { return this.typeEnumId }
    /** @see org.moqui.context.ArtifactExecutionInfo#getActionEnumId() */
    String getActionEnumId() { return this.actionEnumId }

    /** @see org.moqui.context.ArtifactExecutionInfo#getAuthorizedUserId() */
    String getAuthorizedUserId() { return this.authorizedUserId }
    void setAuthorizedUserId(String authorizedUserId) { this.authorizedUserId = authorizedUserId }
    /** @see org.moqui.context.ArtifactExecutionInfo#getAuthorizedAuthzTypeId() */
    String getAuthorizedAuthzTypeId() { return this.authorizedAuthzTypeId }
    void setAuthorizedAuthzTypeId(String authorizedAuthzTypeId) { this.authorizedAuthzTypeId = authorizedAuthzTypeId }

    /** @see org.moqui.context.ArtifactExecutionInfo#isAuthorizationInheritable() */
    boolean isAuthorizationInheritable() { return this.isAuthorizationInheritable }
    void setAuthorizationInheritable(boolean isAuthorizationInheritable) { this.isAuthorizationInheritable = isAuthorizationInheritable}
}
