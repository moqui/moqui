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

    protected String location
    protected String name
    protected String authorizedUserId
    protected boolean isAuthorizationInheritable

    ArtifactExecutionInfoImpl(String location, String name, String authorizedUserId, boolean isAuthorizationInheritable) {
        this.location = location
        this.name = name
        this.authorizedUserId = authorizedUserId
        this.isAuthorizationInheritable = isAuthorizationInheritable
    }


    /** @see org.moqui.context.ArtifactExecutionInfo#getLocation() */
    String getLocation() { return this.location }

    /** @see org.moqui.context.ArtifactExecutionInfo#getLocationURL() */
    URL getLocationURL() { return new URL(this.location) }

    /** @see org.moqui.context.ArtifactExecutionInfo#getName() */
    String getName() { return this.name }

    /** @see org.moqui.context.ArtifactExecutionInfo#getAuthorizedUserId() */
    String getAuthorizedUserId() { return this.authorizedUserId }

    /** @see org.moqui.context.ArtifactExecutionInfo#isAuthorizationInheritable() */
    boolean isAuthorizationInheritable() { return this.isAuthorizationInheritable }
}
