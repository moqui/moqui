package org.moqui.impl.entity.condition

import org.moqui.impl.entity.EntityConditionFactoryImpl
import org.moqui.impl.entity.EntityQueryBuilder
import org.moqui.entity.EntityCondition

class MapCondition extends EntityConditionImplBase {
    protected Class internalClass = null
    protected Map<String, ?> fieldMap
    protected EntityCondition.ComparisonOperator comparisonOperator
    protected EntityCondition.JoinOperator joinOperator
    protected boolean ignoreCase = false

    MapCondition(EntityConditionFactoryImpl ecFactoryImpl,
            Map<String, ?> fieldMap, EntityCondition.ComparisonOperator comparisonOperator,
            EntityCondition.JoinOperator joinOperator) {
        super(ecFactoryImpl)
        this.fieldMap = fieldMap ? fieldMap : new HashMap()
        this.comparisonOperator = comparisonOperator ? comparisonOperator : EntityCondition.EQUALS
        this.joinOperator = joinOperator ? joinOperator : EntityCondition.JoinOperator.AND
    }

    Class getLocalClass() { if (this.internalClass == null) this.internalClass = this.getClass(); return this.internalClass }

    @Override
    void makeSqlWhere(EntityQueryBuilder eqb) {
        this.makeCondition().makeSqlWhere(eqb)
    }

    @Override
    boolean mapMatches(Map<String, ?> map) {
        return this.makeCondition().mapMatches(map)
    }

    void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet) {
        for (Map.Entry<String, Object> entry in fieldMap) fieldAliasSet.add(entry.key)
    }

    @Override
    EntityCondition ignoreCase() { this.ignoreCase = true; return this }

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
        List conditionList = new LinkedList()
        for (Map.Entry<String, ?> fieldEntry in this.fieldMap.entrySet()) {
            EntityConditionImplBase newCondition = (EntityConditionImplBase) this.ecFactoryImpl.makeCondition(fieldEntry.getKey(),
                    this.comparisonOperator, fieldEntry.getValue())
            if (this.ignoreCase) newCondition.ignoreCase()
            conditionList.add(newCondition)
        }
        return (EntityConditionImplBase) this.ecFactoryImpl.makeCondition(conditionList, this.joinOperator)
    }

    @Override
    int hashCode() {
        return (fieldMap ? fieldMap.hashCode() : 0) + comparisonOperator.hashCode() + joinOperator.hashCode() +
                ignoreCase.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getLocalClass()) return false
        MapCondition that = (MapCondition) o
        // NOTE: for Java Enums the != is WAY faster than the .equals
        if (this.comparisonOperator != that.comparisonOperator) return false
        if (this.joinOperator != that.joinOperator) return false
        if (this.ignoreCase != that.ignoreCase) return false
        if (!this.fieldMap.equals(that.fieldMap)) return false
        return true
    }
}
