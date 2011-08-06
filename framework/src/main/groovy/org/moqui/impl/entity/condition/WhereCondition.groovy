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

class WhereCondition extends EntityConditionImplBase {
    protected String sqlWhereClause

    WhereCondition(EntityConditionFactoryImpl ecFactoryImpl, String sqlWhereClause) {
        super(ecFactoryImpl)
        this.sqlWhereClause = sqlWhereClause ? sqlWhereClause : ""
    }

    @Override
    void makeSqlWhere(EntityQueryBuilder eqb) {
        eqb.getSqlTopLevel().append(this.sqlWhereClause)
    }

    @Override
    boolean mapMatches(Map<String, ?> map) {
        // NOTE: always return false unless we eventually implement some sort of SQL parsing, for caching/etc
        // always consider not matching
        logger.warn("The mapMatches for the SQL Where Condition is not supported, text is [${this.sqlWhereClause}]")
        return false
    }

    @Override
    EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

    @Override
    String toString() { return this.sqlWhereClause }

    @Override
    int hashCode() { return (sqlWhereClause ? sqlWhereClause.hashCode() : 0) }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getClass()) return false
        WhereCondition that = (WhereCondition) o
        if (!this.sqlWhereClause.equals(that.sqlWhereClause)) return false
        return true
    }
}
