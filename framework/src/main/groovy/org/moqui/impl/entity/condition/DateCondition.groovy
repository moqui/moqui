/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.entity.condition

import org.moqui.entity.EntityCondition
import java.sql.Timestamp
import org.moqui.impl.entity.EntityConditionFactoryImpl
import org.moqui.impl.entity.EntityQueryBuilder

class DateCondition extends EntityConditionImplBase {
    protected String fromFieldName
    protected String thruFieldName
    protected Timestamp compareStamp

    DateCondition(EntityConditionFactoryImpl ecFactoryImpl,
            String fromFieldName, String thruFieldName, Timestamp compareStamp) {
        super(ecFactoryImpl)
        this.fromFieldName = fromFieldName ?: "fromDate"
        this.thruFieldName = thruFieldName ?: "thruDate"
        this.compareStamp = compareStamp
    }

    @Override
    void makeSqlWhere(EntityQueryBuilder eqb) {
        this.makeCondition().makeSqlWhere(eqb)
    }

    void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet) {
        fieldAliasSet.add(fromFieldName)
        fieldAliasSet.add(thruFieldName)
    }

    @Override
    boolean mapMatches(Map<String, ?> map) {
        return this.makeCondition().mapMatches(map)
    }

    @Override
    boolean populateMap(Map<String, ?> map) { return false }

    @Override
    EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

    @Override
    String toString() {
        return this.makeCondition().toString()
    }

    protected EntityConditionImplBase makeCondition() {
        return (EntityConditionImplBase) ecFactoryImpl.makeCondition(
            ecFactoryImpl.makeCondition(
                ecFactoryImpl.makeCondition(thruFieldName, EntityCondition.EQUALS, null),
                EntityCondition.JoinOperator.OR,
                ecFactoryImpl.makeCondition(thruFieldName, EntityCondition.GREATER_THAN, compareStamp)
            ),
            EntityCondition.JoinOperator.AND,
            ecFactoryImpl.makeCondition(
                ecFactoryImpl.makeCondition(fromFieldName, EntityCondition.EQUALS, null),
                EntityCondition.JoinOperator.OR,
                ecFactoryImpl.makeCondition(fromFieldName, EntityCondition.LESS_THAN_EQUAL_TO, compareStamp)
            )
        )
    }

    @Override
    int hashCode() {
        return (compareStamp ? compareStamp.hashCode() : 0) + fromFieldName.hashCode() + thruFieldName.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getClass()) return false
        DateCondition that = (DateCondition) o
        if (this.compareStamp == null && that.compareStamp != null) return false
        if (this.compareStamp != null) {
            if (that.compareStamp == null) {
                return false
            } else {
                if (!this.compareStamp.equals(that.compareStamp)) return false
            }
        }
        if (!this.fromFieldName.equals(that.fromFieldName)) return false
        if (!this.thruFieldName.equals(that.thruFieldName)) return false
        return true
    }
}
