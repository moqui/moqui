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
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities

class ArtifactExecutionInfoImpl implements ArtifactExecutionInfo {

    protected String name
    protected String typeEnumId
    protected String actionEnumId
    protected String authorizedUserId = null
    protected String authorizedAuthzTypeId = null
    protected String authorizedActionEnumId = null
    protected boolean authorizationInheritable = false
    //protected Exception createdLocation = null
    protected ArtifactExecutionInfoImpl parentAeii = null
    protected long startTime
    protected Long endTime = null
    protected List<ArtifactExecutionInfoImpl> childList = []
    protected Long childrenRunningTime = null

    ArtifactExecutionInfoImpl(String name, String typeEnumId, String actionEnumId) {
        this.name = name
        this.typeEnumId = typeEnumId
        this.actionEnumId = actionEnumId ?: "AUTHZA_ALL"
        //createdLocation = new Exception("Create AEII location for ${name}, type ${typeEnumId}, action ${actionEnumId}")
        this.startTime = System.currentTimeMillis()
    }

    @Override
    String getName() { return this.name }
    @Override
    String getTypeEnumId() { return this.typeEnumId }
    @Override
    String getActionEnumId() { return this.actionEnumId }

    @Override
    String getAuthorizedUserId() { return this.authorizedUserId }
    void setAuthorizedUserId(String authorizedUserId) { this.authorizedUserId = authorizedUserId }
    @Override
    String getAuthorizedAuthzTypeId() { return this.authorizedAuthzTypeId }
    void setAuthorizedAuthzTypeId(String authorizedAuthzTypeId) { this.authorizedAuthzTypeId = authorizedAuthzTypeId }
    @Override
    String getAuthorizedActionEnumId() { return this.authorizedActionEnumId }
    void setAuthorizedActionEnumId(String authorizedActionEnumId) { this.authorizedActionEnumId = authorizedActionEnumId }

    @Override
    boolean isAuthorizationInheritable() { return this.authorizationInheritable }
    void setAuthorizationInheritable(boolean isAuthorizationInheritable) { this.authorizationInheritable = isAuthorizationInheritable}

    void copyAacvInfo(EntityValue aacv, String userId) {
        setAuthorizedUserId((String) userId)
        setAuthorizedAuthzTypeId((String) aacv.authzTypeEnumId)
        setAuthorizedActionEnumId((String) aacv.authzActionEnumId)
        setAuthorizationInheritable(aacv.inheritAuthz == "Y")
    }

    void copyAuthorizedInfo(ArtifactExecutionInfoImpl aeii) {
        setAuthorizedUserId(aeii.authorizedUserId)
        setAuthorizedAuthzTypeId(aeii.authorizedAuthzTypeId)
        setAuthorizedActionEnumId(aeii.authorizedActionEnumId)
        setAuthorizationInheritable(aeii.authorizationInheritable)
    }

    void setEndTime() { this.endTime = System.currentTimeMillis() }
    @Override
    long getRunningTime() { return endTime != null ? endTime - startTime : 0 }
    void calcChildTime(boolean recurse) {
        childrenRunningTime = 0
        for (ArtifactExecutionInfoImpl aeii in childList) {
            childrenRunningTime += aeii.getRunningTime()
            if (recurse) aeii.calcChildTime(true)
        }
    }
    @Override
    long getThisRunningTime() { return getRunningTime() - getChildrenRunningTime() }
    @Override
    long getChildrenRunningTime() {
        if (childrenRunningTime == null) calcChildTime(false)
        return childrenRunningTime ?: 0
    }

    void setParent(ArtifactExecutionInfoImpl parentAeii) { this.parentAeii = parentAeii }
    @Override
    ArtifactExecutionInfo getParent() { return parentAeii }
    @Override
    BigDecimal getPercentOfParentTime() { parentAeii && endTime ?
        (((getRunningTime() / parentAeii.getRunningTime()) * 100) as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP) : 0 }


    void addChild(ArtifactExecutionInfoImpl aeii) { childList.add(aeii) }
    List<ArtifactExecutionInfo> getChildList() { return childList }

    void print(Writer writer, int level, boolean children) {
        for (int i = 0; i < (level * 2); i++) writer.append(' ')
        writer.append('[').append(parentAeii ? StupidUtilities.paddedString(getPercentOfParentTime() as String, 5, false) : '     ').append('%]')
        writer.append('[').append(StupidUtilities.paddedString(getRunningTime() as String, 5, false)).append(']')
        writer.append('[').append(StupidUtilities.paddedString(getThisRunningTime() as String, 3, false)).append(']')
        writer.append('[').append(childList ? StupidUtilities.paddedString(getChildrenRunningTime() as String, 3, false) : '   ').append('] ')
        writer.append(StupidUtilities.paddedString(ArtifactExecutionFacadeImpl.artifactTypeDescriptionMap.get(typeEnumId), 10, true)).append(' ')
        writer.append(StupidUtilities.paddedString(ArtifactExecutionFacadeImpl.artifactActionDescriptionMap.get(actionEnumId), 7, true)).append(' ')
        writer.append(name).append('\n')

        if (children) for (ArtifactExecutionInfoImpl aeii in childList) aeii.print(writer, level + 1, true)
    }

    @Override
    String toString() {
        return "[name:'${name}',type:'${typeEnumId}',action:'${actionEnumId}',user:'${authorizedUserId}',authz:'${authorizedAuthzTypeId}',authAction:'${authorizedActionEnumId}',inheritable:${authorizationInheritable},runningTime:${getRunningTime()}]"
    }
}
