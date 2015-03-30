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

    protected final String name
    protected final String typeEnumId
    protected final String actionEnumId
    protected String actionDetail = ""
    protected Map<String, Object> parameters = null
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

    ArtifactExecutionInfoImpl setActionDetail(String detail) { this.actionDetail = detail; return this }
    ArtifactExecutionInfoImpl setParameters(Map<String, Object> parameters) { this.parameters = parameters; return this }

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
        this.authorizedUserId = userId
        this.authorizedAuthzTypeId = (String) aacv.get('authzTypeEnumId')
        this.authorizedActionEnumId = (String) aacv.get('authzActionEnumId')
        this.authorizationInheritable = aacv.get('inheritAuthz') == "Y"
    }

    void copyAuthorizedInfo(ArtifactExecutionInfoImpl aeii) {
        this.authorizedUserId = aeii.authorizedUserId
        this.authorizedAuthzTypeId = aeii.authorizedAuthzTypeId
        this.authorizedActionEnumId = aeii.authorizedActionEnumId
        this.authorizationInheritable = aeii.authorizationInheritable
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
        writer.append(StupidUtilities.paddedString(actionDetail, 5, true)).append(' ')
        writer.append(name).append('\n')

        if (children) for (ArtifactExecutionInfoImpl aeii in childList) aeii.print(writer, level + 1, true)
    }

    String getKeyString() { return name + ":" + typeEnumId + ":" + actionEnumId + ":" + actionDetail }

    static List<Map> hotSpotByTime(List<ArtifactExecutionInfoImpl> aeiiList, boolean ownTime, String orderBy) {
        Map<String, Map> timeByArtifact = [:]
        for (ArtifactExecutionInfoImpl aeii in aeiiList) aeii.addToMapByTime(timeByArtifact, ownTime)
        List<Map> hotSpotList = []
        hotSpotList.addAll(timeByArtifact.values())

        // in some cases we get REALLY long times before the system is warmed, knock those out
        for (Map val in hotSpotList) {
            int knockOutCount = 0
            List<Long> newTimes = []
            def timeAvg = val.timeAvg
            for (Long time in val.times) {
                // this ain't no standard deviation, but consider 3 times average to be abnormal
                if (time > (timeAvg * 3)) {
                    knockOutCount++
                } else {
                    newTimes.add(time)
                }
            }
            if (knockOutCount > 0) {
                // calc new average, add knockOutCount times to fill in gaps, calc new time total
                long newTotal = 0
                long newMax = 0
                for (long time in newTimes) { newTotal += time; if (time > newMax) newMax = time }
                BigDecimal newAvg = ((newTotal / newTimes.size()) as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)
                long newTimeAvg = newAvg.setScale(0, BigDecimal.ROUND_HALF_UP)
                newTotal += newTimeAvg * knockOutCount
                val.time = newTotal
                val.timeMax = newMax
                val.timeAvg = newAvg
            }
        }

        StupidUtilities.orderMapList(hotSpotList, [orderBy ?: '-time'])
        return hotSpotList
    }
    void addToMapByTime(Map<String, Map> timeByArtifact, boolean ownTime) {
        String key = getKeyString()
        Map val = timeByArtifact.get(key)
        long curTime = ownTime ? getThisRunningTime() : getRunningTime()
        if (val == null) {
            timeByArtifact.put(key, [times:[curTime], time:curTime, timeMin:curTime, timeMax:curTime, timeAvg:curTime,
                    count:1, name:name, actionDetail:actionDetail,
                    type:ArtifactExecutionFacadeImpl.artifactTypeDescriptionMap.get(typeEnumId),
                    action:ArtifactExecutionFacadeImpl.artifactActionDescriptionMap.get(actionEnumId)])
        } else {
            val = timeByArtifact[key]
            val.count = val.count + 1
            val.times.add(curTime)
            val.time = val.time + curTime
            val.timeMin = val.timeMin > curTime ? curTime : val.timeMin
            val.timeMax = val.timeMax > curTime ? val.timeMax : curTime
            val.timeAvg = ((val.time / val.count) as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)
        }
        for (ArtifactExecutionInfoImpl aeii in childList) aeii.addToMapByTime(timeByArtifact, ownTime)
    }
    static void printHotSpotList(Writer writer, List<Map> infoList) {
        // "[${time}:${timeMin}:${timeAvg}:${timeMax}][${count}] ${type} ${action} ${actionDetail} ${name}"
        for (Map info in infoList) {
            writer.append('[').append(StupidUtilities.paddedString(info.time as String, 5, false)).append(':')
            writer.append(StupidUtilities.paddedString(info.timeMin as String, 3, false)).append(':')
            writer.append(StupidUtilities.paddedString(info.timeAvg as String, 5, false)).append(':')
            writer.append(StupidUtilities.paddedString(info.timeMax as String, 3, false)).append(']')
            writer.append('[').append(StupidUtilities.paddedString(info.count as String, 3, false)).append('] ')
            writer.append(StupidUtilities.paddedString((String) info.type, 10, true)).append(' ')
            writer.append(StupidUtilities.paddedString((String) info.action, 7, true)).append(' ')
            writer.append(StupidUtilities.paddedString((String) info.actionDetail, 5, true)).append(' ')
            writer.append((String) info.name).append('\n')
        }
    }


    static List<Map> consolidateArtifactInfo(List<ArtifactExecutionInfoImpl> aeiiList) {
        List<Map> topLevelList = []
        Map<String, Map> flatMap = [:]
        for (ArtifactExecutionInfoImpl aeii in aeiiList) aeii.consolidateArtifactInfo(topLevelList, flatMap, null)
        return topLevelList
    }
    void consolidateArtifactInfo(List<Map> topLevelList, Map<String, Map> flatMap, Map parentArtifactMap) {
        String key = getKeyString()
        Map artifactMap = flatMap.get(key)
        if (artifactMap == null) {
            artifactMap = [time:getRunningTime(), thisTime:getThisRunningTime(), childrenTime:getChildrenRunningTime(),
                    count:1, name:name, actionDetail:actionDetail, childInfoList:[], key:key,
                    type:ArtifactExecutionFacadeImpl.artifactTypeDescriptionMap.get(typeEnumId),
                    action:ArtifactExecutionFacadeImpl.artifactActionDescriptionMap.get(actionEnumId)]
            flatMap.put(key, artifactMap)
            if (parentArtifactMap != null) {
                parentArtifactMap.childInfoList.add(artifactMap)
            } else {
                topLevelList.add(artifactMap)
            }
        } else {
            artifactMap.count = artifactMap.count + 1
            artifactMap.time = artifactMap.time + getRunningTime()
            artifactMap.thisTime = artifactMap.thisTime + getThisRunningTime()
            artifactMap.childrenTime = artifactMap.childrenTime + getChildrenRunningTime()
            if (parentArtifactMap != null) {
                // is the current artifact in the current parent's child list? if not add it (a given artifact may be under multiple parents, normal)
                boolean foundMap = false
                for (Map candidate in parentArtifactMap.childInfoList) if (candidate.key == key) { foundMap = true; break }
                if (!foundMap) parentArtifactMap.childInfoList.add(artifactMap)
            }
        }

        for (ArtifactExecutionInfoImpl aeii in childList) aeii.consolidateArtifactInfo(topLevelList, flatMap, artifactMap)
    }
    static String printArtifactInfoList(List<Map> infoList) {
        StringWriter sw = new StringWriter()
        printArtifactInfoList(sw, infoList, 0)
        return sw.toString()
    }
    static void printArtifactInfoList(Writer writer, List<Map> infoList, int level) {
        // "[${time}:${thisTime}:${childrenTime}][${count}] ${type} ${action} ${actionDetail} ${name}"
        for (Map info in infoList) {
            for (int i = 0; i < level; i++) writer.append('|').append(' ')
            writer.append('[').append(StupidUtilities.paddedString(info.time as String, 5, false)).append(':')
            writer.append(StupidUtilities.paddedString(info.thisTime as String, 3, false)).append(':')
            writer.append(StupidUtilities.paddedString(info.childrenTime as String, 3, false)).append(']')
            writer.append('[').append(StupidUtilities.paddedString(info.count as String, 3, false)).append('] ')
            writer.append(StupidUtilities.paddedString((String) info.type, 10, true)).append(' ')
            writer.append(StupidUtilities.paddedString((String) info.action, 7, true)).append(' ')
            writer.append(StupidUtilities.paddedString((String) info.actionDetail, 5, true)).append(' ')
            writer.append((String) info.name).append('\n')
            // if we get past level 25 just give up, probably a loop in the tree
            if (level < 25) {
                printArtifactInfoList(writer, (List<Map>) info.childInfoList, level + 1)
            } else {
                for (int i = 0; i < level; i++) writer.append('|').append(' ')
                writer.append("Reached depth limit, not printing children (may be a cycle in the 'tree')\n")
            }
        }
    }

    @Override
    String toString() {
        return "[name:'${name}',type:'${typeEnumId}',action:'${actionEnumId}',user:'${authorizedUserId}',authz:'${authorizedAuthzTypeId}',authAction:'${authorizedActionEnumId}',inheritable:${authorizationInheritable},runningTime:${getRunningTime()}]"
    }
}
