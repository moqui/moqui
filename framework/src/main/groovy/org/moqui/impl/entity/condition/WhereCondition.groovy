/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
    boolean populateMap(Map<String, ?> map) { return false }

    void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet) { }

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
