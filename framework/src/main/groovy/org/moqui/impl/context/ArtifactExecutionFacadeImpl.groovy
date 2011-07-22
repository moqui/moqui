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
import org.moqui.entity.EntityCondition.ComparisonOperator
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.impl.entity.EntityDefinition
import org.moqui.context.ArtifactAuthorizationException
import java.sql.Timestamp

public class ArtifactExecutionFacadeImpl implements ArtifactExecutionFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ArtifactExecutionFacadeImpl.class)

    // NOTE: these need to be in a Map instead of the DB because Enumeration records may not yet be loaded
    protected final static Map<String, String> artifactTypeDescriptionMap = [AT_XML_SCREEN:"XML Screen",
            AT_XML_SCREEN_TRANS:"XML Screen Transition", AT_SERVICE:"Service", AT_ENTITY:"Entity"]
    protected final static Map<String, String> artifactActionDescriptionMap = [AUTHZA_VIEW:"View",
            AUTHZA_CREATE:"Create", AUTHZA_UPDATE:"Update", AUTHZA_DELETE:"Delete", AUTHZA_ALL:"All"]

    protected ExecutionContextImpl eci
    protected Deque<ArtifactExecutionInfoImpl> artifactExecutionInfoStack = new LinkedList<ArtifactExecutionInfoImpl>()
    protected List<ArtifactExecutionInfoImpl> artifactExecutionInfoHistory = new LinkedList<ArtifactExecutionInfoImpl>()

    protected boolean disableAuthz = false

    ArtifactExecutionFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#peek() */
    ArtifactExecutionInfo peek() { return this.artifactExecutionInfoStack.peekFirst() }

    /** @see org.moqui.context.ArtifactExecutionFacade#pop() */
    ArtifactExecutionInfo pop() {
        if (this.artifactExecutionInfoStack.size() > 0) {
            return this.artifactExecutionInfoStack.removeFirst()
        } else {
            logger.warn("Tried to pop from an empty ArtifactExecutionInfo stack", new Exception("Bad pop location"))
            return null
        }
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#push(ArtifactExecutionInfo, boolean) */
    void push(ArtifactExecutionInfo aei, boolean requiresAuthz) {
        ArtifactExecutionInfoImpl aeii = (ArtifactExecutionInfoImpl) aei
        // do permission check for this new aei that current user is trying to access
        String userId = eci.user.username

        ArtifactExecutionInfoImpl lastAeii = artifactExecutionInfoStack.peekFirst()

        // always do this regardless of the authz checks, etc; keep a history of artifacts run
        artifactExecutionInfoHistory.add(aeii)

        // never do this for entities when disableAuthz, as we might use any below and would cause infinite recursion
        if (this.disableAuthz && aeii.typeEnumId == "AT_ENTITY") {
            if (lastAeii != null && lastAeii.authorizationInheritable) aeii.copyAuthorizedInfo(lastAeii)
            this.artifactExecutionInfoStack.addFirst(aeii)
            return
        }

        // see if there is a UserAccount for the username, and if so get its userId as a more permanent identifier
        boolean alreadyDisabled = disableAuthz()
        try {
            EntityValue ua = eci.entity.makeFind("UserAccount").condition("username", userId).useCache(true).one()
            if (ua) userId = ua.userId
        } finally {
            if (!alreadyDisabled) enableAuthz()
        }

        // if last was an always allow, then don't bother checking for deny/etc
        if (lastAeii != null && lastAeii.authorizationInheritable && lastAeii.authorizedUserId == userId &&
                lastAeii.authorizedAuthzTypeId == "AUTHZT_ALWAYS" &&
                (lastAeii.authorizedActionEnumId == "AUTHZA_ALL" || lastAeii.authorizedActionEnumId == aeii.actionEnumId)) {
            aeii.copyAuthorizedInfo(lastAeii)
            this.artifactExecutionInfoStack.addFirst(aeii)
            return
        }

        EntityList aacvList
        EntityValue denyAacv = null

        // don't check authz for these queries, would cause infinite recursion
        alreadyDisabled = disableAuthz()
        try {
            // first get the groups the user is in (cached), always add the "ALL_USERS" group to it
            Set userGroupIdSet = new HashSet()
            userGroupIdSet.add("ALL_USERS")
            for (EntityValue userGroupMember in eci.entity.makeFind("UserGroupMember").condition("userId", userId).list().filterByDate(null, null, null))
                userGroupIdSet.add(userGroupMember.userGroupId)

            // check authorizations for those groups (separately cached for more cache hits)
            EntityFind aacvFind = eci.entity.makeFind("ArtifactAuthzCheckView")
                    .condition("artifactTypeEnumId", aeii.typeEnumId)
                    .condition(eci.entity.conditionFactory.makeCondition(
                        eci.entity.conditionFactory.makeCondition("artifactName", ComparisonOperator.EQUALS, aeii.name),
                        JoinOperator.OR,
                        eci.entity.conditionFactory.makeCondition("nameIsPattern", ComparisonOperator.EQUALS, "Y")))
            if (userGroupIdSet.size() == 1) aacvFind.condition("userGroupId", userGroupIdSet.iterator().next())
            else aacvFind.condition("userGroupId", ComparisonOperator.IN, userGroupIdSet)
            if (aeii.actionEnumId) aacvFind.condition("authzActionEnumId", ComparisonOperator.IN, [aeii.actionEnumId, "AUTHZA_ALL"])
            else aacvFind.condition("authzActionEnumId", "AUTHZA_ALL")

            aacvList = aacvFind.useCache(true).list()

            if (aacvList.size() > 0) {
                for (EntityValue aacv in aacvList) {
                    if (aacv.nameIsPattern == "Y" && !aeii.name.matches((String) aacv.artifactName)) continue
                    // check the record-level permission
                    if (aacv.viewEntityName) {
                        EntityValue artifactAuthzRecord = eci.entity.makeFind("ArtifactAuthzRecord")
                                .condition("artifactAuthzId", aacv.artifactAuthzId).useCache(true).one()
                        EntityDefinition ed = eci.entity.getEntityDefinition((String) aacv.viewEntityName)
                        EntityFind ef = eci.entity.makeFind((String) aacv.viewEntityName)
                        if (artifactAuthzRecord.userIdField) {
                            ef.condition((String) artifactAuthzRecord.userIdField, userId)
                        } else if (ed.isField("userId")) {
                            ef.condition("userId", userId)
                        }
                        if (artifactAuthzRecord.filterByDate == "Y") {
                            ef.conditionDate((String) artifactAuthzRecord.filterByDateFromField,
                                    (String) artifactAuthzRecord.filterByDateThruField, eci.user.nowTimestamp)
                        }
                        EntityList condList = eci.entity.makeFind("ArtifactAuthzRecordCond")
                                .condition("artifactAuthzId", aacv.artifactAuthzId).useCache(true).list()
                        for (EntityValue cond in condList) {
                            String expCondValue = eci.resource.evaluateStringExpand((String) cond.condValue,
                                    "ArtifactAuthzRecordCond.${cond.artifactAuthzId}.${cond.artifactAuthzCondSeqId}")
                            if (expCondValue) {
                                ef.condition((String) cond.fieldName,
                                        eci.entity.conditionFactory.comparisonOperatorFromEnumId((String) cond.operatorEnumId),
                                        expCondValue)
                            }
                        }

                        // anything found? if not it fails this condition, so skip the authz
                        if (ef.useCache(true).count() == 0) continue
                    }

                    String authzTypeEnumId = aacv.authzTypeEnumId
                    if (aacv.authzServiceName) {
                        Map result = eci.service.sync().name((String) aacv.authzServiceName).parameters((Map<String, Object>)
                                [userId:userId, authzActionEnumId:aeii.actionEnumId,
                                artifactTypeEnumId:aeii.typeEnumId, artifactName:aeii.name]).call()
                        if (result?.authzTypeEnumId) authzTypeEnumId = result.authzTypeEnumId
                    }

                    if (authzTypeEnumId == "AUTHZT_DENY") {
                        // we already know last was not always allow (checked above), so keep going in loop just in case we
                        // find an always allow in the query
                        denyAacv = aacv
                    } else if (authzTypeEnumId == "AUTHZT_ALWAYS") {
                        aeii.copyAacvInfo(aacv, userId)
                        this.artifactExecutionInfoStack.addFirst(aeii)
                        return
                    } else if (authzTypeEnumId == "AUTHZT_ALLOW" && denyAacv == null) {
                        // see if there are any denies in AEIs on lower on the stack
                        boolean ancestorDeny = false
                        for (ArtifactExecutionInfoImpl ancestorAeii in artifactExecutionInfoStack)
                            if (ancestorAeii.authorizedAuthzTypeId == "AUTHZT_DENY") ancestorDeny = true

                        if (!ancestorDeny) {
                            aeii.copyAacvInfo(aacv, userId)
                            this.artifactExecutionInfoStack.addFirst(aeii)
                            return
                        }
                    }
                }
            }

            if (denyAacv != null) {
                // record that this was an explicit deny (for push or exception in case something catches and handles it)
                aeii.copyAacvInfo(denyAacv, userId)

                if (!requiresAuthz || this.disableAuthz) {
                    // if no authz required, just push it even though it was a failure
                    this.artifactExecutionInfoStack.addFirst(aeii)
                    return
                } else {
                    StringBuilder warning = new StringBuilder()
                    warning.append("User [${userId}] is not authorized for ${aeii.typeEnumId} [${aeii.name}] because of a deny record [type:${aeii.typeEnumId},action:${aeii.actionEnumId}], here is the current artifact stack:")
                    for (def warnAei in this.stack) warning.append("\n").append(warnAei)
                    logger.warn(warning.toString())

                    eci.service.sync().name("create", "ArtifactAuthzFailure").parameters((Map<String, Object>)
                            [artifactName:aeii.name, artifactTypeEnumId:aeii.typeEnumId,
                            authzActionEnumId:aeii.actionEnumId, userId:userId,
                            failureDate:new Timestamp(System.currentTimeMillis()), isDeny:"Y"]).call()

                    throw new ArtifactAuthorizationException("User [${userId}] is not authorized for ${artifactActionDescriptionMap.get(aeii.actionEnumId)} on ${artifactTypeDescriptionMap.get(aeii.typeEnumId)?:aeii.typeEnumId} [${aeii.name}]")
                }
            } else {
                // no perms found for this, only allow if the current AEI has inheritable auth and same user, and (ALL action or same action)
                if (lastAeii != null && lastAeii.authorizationInheritable && lastAeii.authorizedUserId == userId &&
                        (lastAeii.authorizedActionEnumId == "AUTHZA_ALL" || lastAeii.authorizedActionEnumId == aeii.actionEnumId)) {
                    aeii.copyAuthorizedInfo(lastAeii)
                    this.artifactExecutionInfoStack.addFirst(aeii)
                    return
                }
            }

            if (!requiresAuthz || this.disableAuthz) {
                // if no authz required, just push it even though it was a failure
                if (lastAeii != null && lastAeii.authorizationInheritable) aeii.copyAuthorizedInfo(lastAeii)
                this.artifactExecutionInfoStack.addFirst(aeii)
            } else {
                // if we got here no authz found, blow up
                StringBuilder warning = new StringBuilder()
                warning.append("User [${userId}] is not authorized for ${aeii.typeEnumId} [${aeii.name}] because of no allow record [type:${aeii.typeEnumId},action:${aeii.actionEnumId}]\nlastAeii=[${lastAeii}]\nHere is the artifact stack:")
                for (def warnAei in this.stack) warning.append("\n").append(warnAei)
                logger.warn(warning.toString(), new Exception("Authz failure location"))

                // NOTE: this is called sync because failures should be rare and not as performance sensitive, and
                //  because this is still in a disableAuthz block (if async a service would have to be written for that)
                eci.service.sync().name("create", "ArtifactAuthzFailure").parameters((Map<String, Object>)
                        [artifactName:aeii.name, artifactTypeEnumId:aeii.typeEnumId,
                        authzActionEnumId:aeii.actionEnumId, userId:userId,
                        failureDate:new Timestamp(System.currentTimeMillis()), isDeny:"N"]).call()

                throw new ArtifactAuthorizationException("User [${userId}] is not authorized for ${artifactActionDescriptionMap.get(aeii.actionEnumId)} on ${artifactTypeDescriptionMap.get(aeii.typeEnumId)?:aeii.typeEnumId} [${aeii.name}]")
            }
        } finally {
            if (!alreadyDisabled) enableAuthz()
        }
    }

    /** @see org.moqui.context.ArtifactExecutionFacade#getStack() */
    Deque<ArtifactExecutionInfo> getStack() { return this.artifactExecutionInfoStack }

    /** @see org.moqui.context.ArtifactExecutionFacade#getHistory() */
    List<ArtifactExecutionInfo> getHistory() { return this.artifactExecutionInfoHistory }

    boolean disableAuthz() { boolean alreadyDisabled = this.disableAuthz; this.disableAuthz = true; return alreadyDisabled }
    void enableAuthz() { this.disableAuthz = false }
}
