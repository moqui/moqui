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
package org.moqui.impl.entity.orientdb

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.entity.*
import org.moqui.impl.entity.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OrientEntityFind extends EntityFindBase {
    protected final static Logger logger = LoggerFactory.getLogger(OrientEntityValue.class)

    OrientDatasourceFactory odf

    OrientEntityFind(EntityFacadeImpl efip, String entityName, OrientDatasourceFactory odf) {
        super(efip, entityName)
        this.odf = odf
    }

    @Override
    EntityDynamicView makeEntityDynamicView() {
        throw new UnsupportedOperationException("EntityDynamicView is not yet supported for Orient DB")
    }

    @Override
    EntityValueBase oneExtended(EntityConditionImplBase whereCondition) throws EntityException {
        EntityDefinition ed = this.getEntityDef()

        // NOTE: the native Java query API does not used indexes and such, so use the OSQL approach
        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            EntityFindBuilder efb = new EntityFindBuilder(ed, this)

            // SELECT fields
            // NOTE: for OrientDB don't bother listing fields to select: efb.makeSqlSelectFields(this.fieldsToSelect)

            // FROM Clause
            efb.makeSqlFromClause()

            // WHERE clause only for one/pk query
            // NOTE: do this here after caching because this will always be added on and isn't a part of the original where
            EntityConditionImplBase viewWhere = ed.makeViewWhereCondition()
            if (viewWhere) whereCondition =
                (EntityConditionImplBase) efi.getConditionFactory().makeCondition(whereCondition, EntityCondition.JoinOperator.AND, viewWhere)
            efb.startWhereClause()
            whereCondition.makeSqlWhere(efb)

            // FOR UPDATE doesn't seem to be supported for OrientDB: if (this.forUpdate) efb.makeForUpdate()

            // run the SQL now that it is built
            odf.checkCreateDocumentClass(oddt, ed)

            String sqlString = efb.getSqlTopLevel().toString()
            // logger.warn("TOREMOVE: running OrientDB query: ${sqlString}")

            OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sqlString)

            List<Object> paramValues = new ArrayList<Object>()
            for (EntityQueryBuilder.EntityConditionParameter entityConditionParam in efb.getParameters()) {
                paramValues.add(entityConditionParam.getValue())
            }
            List<ODocument> documentList = oddt.command(query).execute(paramValues.toArray(new Object[paramValues.size]))

            // there should only be one value since we're querying by a set of fields with a unique index (the pk)
            if (!documentList) return null

            ODocument document = documentList[0]
            OrientEntityValue newEntityValue = new OrientEntityValue(ed, efi, odf, document)

            return newEntityValue
        } catch (Exception e) {
            throw new EntityException("Error finding value", e)
        } finally {
            oddt.close()
        }
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
        // LIMIT/OFFSET clause (TODO: supported in ODB?)
        efb.addLimitOffset(this.limit, this.offset)
        // FOR UPDATE (TODO: supported in ODB?)
        if (this.forUpdate) efb.makeForUpdate()

        // run the SQL now that it is built
        EntityListIterator eli
        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            odf.checkCreateDocumentClass(oddt, ed)

            // get SQL and do query with OrientDB API
            String sqlString = efb.getSqlTopLevel().toString()
            // logger.warn("TOREMOVE: running OrientDB query: ${sqlString}")

            OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sqlString.toString())
            List<Object> paramValues = new ArrayList<Object>()
            for (EntityQueryBuilder.EntityConditionParameter entityConditionParam in efb.getParameters()) {
                paramValues.add(entityConditionParam.getValue())
            }
            List<ODocument> documentList = oddt.command(query).execute(paramValues.toArray(new Object[paramValues.size]))
            // logger.warn("TOREMOVE: got OrientDb query results: ${documentList}")

            eli = new OrientEntityListIterator(odf, oddt, documentList, this.getEntityDef(), this.fieldsToSelect, this.efi)
        } catch (EntityException e) {
            oddt.close()
            throw e
        } catch (Throwable t) {
            oddt.close()
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
        efb.getSqlTopLevel().append("COUNT(*) ")
        // efb.makeCountFunction()
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
        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            odf.checkCreateDocumentClass(oddt, ed)

            // get SQL and do query with OrientDB API
            String sqlString = efb.getSqlTopLevel().toString()
            // logger.warn("TOREMOVE: running OrientDB count query: ${sqlString}")

            OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sqlString.toString())
            List<Object> paramValues = new ArrayList<Object>()
            for (EntityQueryBuilder.EntityConditionParameter entityConditionParam in efb.getParameters()) {
                paramValues.add(entityConditionParam.getValue())
            }
            List<ODocument> documentList = oddt.command(query).execute(paramValues.toArray(new Object[paramValues.size]))
            if (!documentList) logger.warn("Got no result for count query: ${sqlString}")
            count = (documentList?.get(0)?.field("count") as Long) ?: 0
        } catch (Exception e) {
            throw new EntityException("Error finding value", e)
        } finally {
            oddt.close()
        }

        return count
    }
}
