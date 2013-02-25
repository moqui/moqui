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
package org.moqui.impl.entity

import java.sql.ResultSet
import java.sql.Connection
import java.sql.SQLException

import org.moqui.entity.EntityDynamicView

import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityValue

import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityException

import org.moqui.impl.entity.condition.EntityConditionImplBase

class EntityFindImpl extends EntityFindBase {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityFindImpl.class)

    EntityFindImpl(EntityFacadeImpl efi, String entityName) {
        super(efi, entityName)
    }

    @Override
    EntityDynamicView makeEntityDynamicView() {
        if (this.dynamicView) return this.dynamicView
        this.dynamicView = new EntityDynamicViewImpl(this)
        return this.dynamicView
    }

    // ======================== Run Find Methods ==============================

    @Override
    EntityValue oneExtended(EntityConditionImplBase whereCondition) throws EntityException {
        EntityDefinition ed = this.getEntityDef()

        EntityFindBuilder efb = new EntityFindBuilder(ed, this)

        // SELECT fields
        efb.makeSqlSelectFields(this.fieldsToSelect)
        // FROM Clause
        efb.makeSqlFromClause()

        // WHERE clause only for one/pk query
        if (whereCondition) {
            efb.startWhereClause()
            whereCondition.makeSqlWhere(efb)
        }
        // GROUP BY clause
        efb.makeGroupByClause(this.fieldsToSelect)

        if (this.forUpdate) efb.makeForUpdate()

        // run the SQL now that it is built
        EntityValue newEntityValue = null
        try {
            efi.getEntityDbMeta().checkTableRuntime(ed)

            efb.makeConnection()
            efb.makePreparedStatement()
            efb.setPreparedStatementValues()

            String condSql = whereCondition.toString()
            ResultSet rs = efb.executeQuery()
            if (rs.next()) {
                newEntityValue = new EntityValueImpl(this.entityDef, this.efi)
                int j = 1
                for (String fieldName in this.fieldsToSelect) {
                    EntityQueryBuilder.getResultSetValue(rs, j, this.entityDef.getFieldNode(fieldName), newEntityValue, this.efi)
                    j++
                }
            } else {
                logger.trace("Result set was empty for find on entity [${this.entityName}] with condition [${condSql}]")
            }
            if (rs.next()) {
                logger.trace("Found more than one result for condition [${condSql}] on entity [${this.entityDef.getEntityName()}]")
            }
        } catch (SQLException e) {
            throw new EntityException("Error finding value", e)
        } finally {
            efb.closeAll()
        }

        return newEntityValue
    }

    @Override
    EntityListIterator iteratorExtended(EntityConditionImplBase whereCondition, EntityConditionImplBase havingCondition,
                                        List<String> orderByExpanded) throws EntityException {
        EntityDefinition ed = this.getEntityDef()
        EntityFindBuilder efb = new EntityFindBuilder(ed, this)
        if (this.getDistinct()) efb.makeDistinct()

        // select fields
        efb.makeSqlSelectFields(this.fieldsToSelect)
        // FROM Clause
        efb.makeSqlFromClause()

        // WHERE clause
        if (whereCondition) {
            efb.startWhereClause()
            whereCondition.makeSqlWhere(efb)
        }
        // GROUP BY clause
        efb.makeGroupByClause(this.fieldsToSelect)
        // HAVING clause
        if (havingCondition) {
            efb.startHavingClause()
            havingCondition.makeSqlWhere(efb)
        }

        // ORDER BY clause
        efb.makeOrderByClause(orderByExpanded)
        // LIMIT/OFFSET clause
        efb.addLimitOffset(this.limit, this.offset)
        // FOR UPDATE
        if (this.forUpdate) efb.makeForUpdate()

        // run the SQL now that it is built
        EntityListIterator eli
        try {
            efi.getEntityDbMeta().checkTableRuntime(ed)

            Connection con = efb.makeConnection()
            efb.makePreparedStatement()
            efb.setPreparedStatementValues()

            ResultSet rs = efb.executeQuery()
            eli = new EntityListIteratorImpl(con, rs, this.getEntityDef(), this.fieldsToSelect, this.efi)
            // ResultSet will be closed in the EntityListIterator
            efb.releaseAll()
        } catch (EntityException e) {
            efb.closeAll()
            throw e
        } catch (Throwable t) {
            efb.closeAll()
            throw new EntityException("Error in find", t)
        }

        return eli
    }

    @Override
    long countExtended(EntityConditionImplBase whereCondition, EntityConditionImplBase havingCondition)
            throws EntityException {
        EntityDefinition ed = this.getEntityDef()
        EntityFindBuilder efb = new EntityFindBuilder(ed, this)

        // count function instead of select fields
        efb.makeCountFunction()
        // FROM Clause
        efb.makeSqlFromClause()

        // WHERE clause
        if (whereCondition) {
            efb.startWhereClause()
            whereCondition.makeSqlWhere(efb)
        }
        // GROUP BY clause
        efb.makeGroupByClause(this.fieldsToSelect)
        // HAVING clause
        if (havingCondition) {
            efb.startHavingClause()
            havingCondition.makeSqlWhere(efb)
        }

        efb.closeCountFunctionIfGroupBy()

        // run the SQL now that it is built
        long count = 0
        try {
            efi.getEntityDbMeta().checkTableRuntime(ed)
            count = internalCount(efb)
        } catch (SQLException e) {
            throw new EntityException("Error finding count", e)
        } finally {
            efb.closeAll()
        }

        return count
    }

    protected long internalCount(EntityFindBuilder efb) {
        long count = 0
        efb.makeConnection()
        efb.makePreparedStatement()
        efb.setPreparedStatementValues()

        ResultSet rs = efb.executeQuery()
        if (rs.next()) count = rs.getLong(1)
        return count
    }
}
