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
package org.moqui.impl.entity.condition

import groovy.transform.CompileStatic
import org.moqui.impl.entity.EntityConditionFactoryImpl
import org.moqui.impl.entity.EntityQueryBuilder
import org.moqui.entity.EntityCondition

@CompileStatic
class MapCondition extends EntityConditionImplBase {
    protected volatile Class internalClass = null
    protected Map<String, Object> fieldMap
    protected EntityCondition.ComparisonOperator comparisonOperator
    protected EntityCondition.JoinOperator joinOperator
    protected Boolean ignoreCase = false
    protected EntityConditionImplBase internalCond = null
    protected int curHashCode

    MapCondition(EntityConditionFactoryImpl ecFactoryImpl,
            Map<String, Object> fieldMap, EntityCondition.ComparisonOperator comparisonOperator,
            EntityCondition.JoinOperator joinOperator) {
        super(ecFactoryImpl)
        this.fieldMap = fieldMap ? fieldMap : new HashMap<String, Object>()
        this.comparisonOperator = comparisonOperator ?: EQUALS
        this.joinOperator = joinOperator ?: AND
        curHashCode = createHashCode()
    }

    Class getLocalClass() { if (this.internalClass == null) this.internalClass = this.getClass(); return this.internalClass }

    @Override
    void makeSqlWhere(EntityQueryBuilder eqb) {
        this.makeCondition().makeSqlWhere(eqb)
    }

    @Override
    boolean mapMatches(Map<String, ?> map) {
        // do this directly instead of going through condition, faster
        // return this.makeCondition().mapMatches(map)

        for (Map.Entry<String, ?> fieldEntry in this.fieldMap.entrySet()) {
            boolean conditionMatches = EntityConditionFactoryImpl.compareByOperator(map.get(fieldEntry.getKey()),
                    comparisonOperator, fieldEntry.getValue())
            if (conditionMatches && joinOperator == OR) return true
            if (!conditionMatches && joinOperator == AND) return false
        }

        // if we got here it means that it's an OR with no true, or an AND with no false
        return (joinOperator == AND)
    }

    @Override
    boolean populateMap(Map<String, ?> map) {
        if (joinOperator != AND || comparisonOperator != EQUALS || ignoreCase) return false
        map.putAll(fieldMap)
        curHashCode = createHashCode()
        return true
    }

    void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet) {
        for (Map.Entry<String, Object> entry in fieldMap.entrySet()) fieldAliasSet.add(entry.key)
    }

    @Override
    EntityCondition ignoreCase() { this.ignoreCase = true; curHashCode = createHashCode(); return this }

    @Override
    String toString() {
        return this.makeCondition().toString()
        /* might want to do something like this at some point, but above is probably better for now
        StringBuilder sb = new StringBuilder()
        for (Map.Entry fieldEntry in this.fieldMap.entrySet()) {
            if (sb.length() > 0) {
                sb.append(' ')
                sb.append(EntityConditionFactoryImpl.getJoinOperatorString(this.joinOperator))
                sb.append(' ')
            }
            sb.append(fieldEntry.getKey())
            sb.append(' ')
            sb.append(EntityConditionFactoryImpl.getComparisonOperatorString(this.comparisonOperator))
            sb.append(' ')
            sb.append(fieldEntry.getValue())
        }
        return sb.toString()
        */
    }

    protected EntityConditionImplBase makeCondition() {
        if (internalCond != null) return internalCond

        List conditionList = new LinkedList()
        for (Map.Entry<String, ?> fieldEntry in this.fieldMap.entrySet()) {
            EntityConditionImplBase newCondition = (EntityConditionImplBase) this.ecFactoryImpl.makeCondition(fieldEntry.getKey(),
                    this.comparisonOperator, fieldEntry.getValue())
            if (this.ignoreCase) newCondition.ignoreCase()
            conditionList.add(newCondition)
        }

        internalCond = (EntityConditionImplBase) this.ecFactoryImpl.makeCondition(conditionList, this.joinOperator)
        return internalCond
    }

    @Override
    int hashCode() { return curHashCode }
    protected int createHashCode() {
        return (fieldMap ? fieldMap.hashCode() : 0) + comparisonOperator.hashCode() + joinOperator.hashCode() +
                ignoreCase.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (o == null || !(o instanceof MapCondition)) return false
        MapCondition that = (MapCondition) o
        // NOTE: for Java Enums the != is WAY faster than the .equals
        // NOTE2: Groovy overrides != making it slower...
        if (!comparisonOperator.equals(that.comparisonOperator)) return false
        if (!joinOperator.equals(that.joinOperator)) return false
        if (ignoreCase.booleanValue() != that.ignoreCase.booleanValue()) return false
        if (!fieldMap.equals(that.fieldMap)) return false
        return true
    }
}
