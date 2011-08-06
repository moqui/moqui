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
import org.moqui.impl.entity.EntityQueryBuilder
import org.moqui.impl.entity.EntityConditionFactoryImpl

class BasicJoinCondition extends EntityConditionImplBase {
    protected EntityConditionImplBase lhs
    protected EntityCondition.JoinOperator operator
    protected EntityConditionImplBase rhs

    BasicJoinCondition(EntityConditionFactoryImpl ecFactoryImpl,
            EntityConditionImplBase lhs, EntityCondition.JoinOperator operator, EntityConditionImplBase rhs) {
        super(ecFactoryImpl)
        this.lhs = lhs
        this.operator = operator ? operator : EntityCondition.JoinOperator.AND
        this.rhs = rhs
    }

    @Override
    void makeSqlWhere(EntityQueryBuilder eqb) {
        StringBuilder sql = eqb.getSqlTopLevel()
        sql.append('(')
        this.lhs.makeSqlWhere(eqb)
        sql.append(' ').append(EntityConditionFactoryImpl.getJoinOperatorString(this.operator)).append(' ')
        this.rhs.makeSqlWhere(eqb)
        sql.append(')')
    }

    @Override
    boolean mapMatches(Map<String, ?> map) {
        boolean lhsMatches = this.lhs.mapMatches(map)

        // handle cases where we don't need to evaluate rhs
        if (lhsMatches && operator == EntityCondition.JoinOperator.OR) return true
        if (!lhsMatches && operator == EntityCondition.JoinOperator.AND) return false

        // handle opposite cases since we know cases above aren't true (ie if OR then lhs=false, if AND then lhs=true
        // if rhs then result is true whether AND or OR
        // if !rhs then result is false whether AND or OR
        return this.rhs.mapMatches(map)
    }

    @Override
    EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

    @Override
    String toString() {
        // general SQL where clause style text with values included
        return "(" + lhs.toString() + " " + EntityConditionFactoryImpl.getJoinOperatorString(this.operator) + " " + rhs.toString() + ")"
    }

    @Override
    int hashCode() {
        return (lhs ? lhs.hashCode() : 0) + operator.hashCode() + (rhs ? rhs.hashCode() : 0)
    }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getClass()) return false
        BasicJoinCondition that = (BasicJoinCondition) o
        if (!this.lhs.equals(that.lhs)) return false
        // NOTE: for Java Enums the != is WAY faster than the .equals
        if (this.operator != that.operator) return false
        if (!this.rhs.equals(that.rhs)) return false
        return true
    }
}
