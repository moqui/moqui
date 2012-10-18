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
    EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

    @Override
    String toString() {
        return this.makeCondition().toString()
    }

    protected EntityConditionImplBase makeCondition() {
        return (EntityConditionImplBase) ecFactoryImpl.makeCondition(
            ecFactoryImpl.makeCondition(
                ecFactoryImpl.makeCondition(thruFieldName, EntityCondition.ComparisonOperator.EQUALS, null),
                EntityCondition.JoinOperator.OR,
                ecFactoryImpl.makeCondition(thruFieldName, EntityCondition.ComparisonOperator.GREATER_THAN, compareStamp)
            ),
            EntityCondition.JoinOperator.AND,
            ecFactoryImpl.makeCondition(
                ecFactoryImpl.makeCondition(fromFieldName, EntityCondition.ComparisonOperator.EQUALS, null),
                EntityCondition.JoinOperator.OR,
                ecFactoryImpl.makeCondition(fromFieldName, EntityCondition.ComparisonOperator.LESS_THAN_EQUAL_TO, compareStamp)
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
