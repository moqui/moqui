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

import org.apache.commons.collections.set.ListOrderedSet

import org.moqui.entity.EntityException
import org.moqui.impl.entity.EntityQueryBuilder.EntityConditionParameter

import java.sql.ResultSet

class EntityValueImpl extends EntityValueBase {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityValueImpl.class)

    EntityValueImpl(EntityDefinition ed, EntityFacadeImpl efip) {
        super(ed, efip)
    }

    @Override
    void createExtended(ListOrderedSet fieldList) {
        EntityDefinition ed = getEntityDefinition()

        if (ed.isViewEntity()) {
            throw new EntityException("Create not yet implemented for view-entity")
        } else {
            EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append("INSERT INTO ").append(ed.getFullTableName())

            sql.append(" (")
            boolean isFirstField = true
            StringBuilder values = new StringBuilder()
            for (String fieldName in fieldList) {
                if (isFirstField) {
                    isFirstField = false
                } else {
                    sql.append(", ")
                    values.append(", ")
                }
                sql.append(ed.getColumnName(fieldName, false))
                values.append('?')
            }
            sql.append(") VALUES (").append(values.toString()).append(')')

            try {
                getEntityFacadeImpl().entityDbMeta.checkTableRuntime(ed)

                internalCreate(eqb, fieldList)
            } catch (EntityException e) {
                throw new EntityException("Error in create of [${this.toString()}]", e)
            } finally {
                eqb.closeAll()
            }
        }
    }

    protected void internalCreate(EntityQueryBuilder eqb, ListOrderedSet fieldList) {
        eqb.makeConnection()
        eqb.makePreparedStatement()
        int index = 1
        for (String fieldName in fieldList) {
            eqb.setPreparedStatementValue(index, getValueMap().get(fieldName), getEntityDefinition().getFieldNode(fieldName))
            index++
        }
        eqb.executeUpdate()
        setSyncedWithDb()
        // NOTE: cache clear is the same for create, update, delete; even on create need to clear one cache because it
        // might have a null value for a previous query attempt
        getEntityFacadeImpl().getEntityCache().clearCacheForValue(this, true)
    }

    @Override
    void updateExtended(List<String> pkFieldList, ListOrderedSet nonPkFieldList) {
        EntityDefinition ed = getEntityDefinition()

        if (ed.isViewEntity()) {
            throw new EntityException("Update not yet implemented for view-entity")
        } else {
            EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append("UPDATE ").append(ed.getFullTableName()).append(" SET ")

            boolean isFirstField = true
            for (String fieldName in nonPkFieldList) {
                if (isFirstField) isFirstField = false else sql.append(", ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                        getValueMap().get(fieldName), eqb))
            }
            sql.append(" WHERE ")
            boolean isFirstPk = true
            for (String fieldName in pkFieldList) {
                if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                        getValueMap().get(fieldName), eqb))
            }

            try {
                getEntityFacadeImpl().entityDbMeta.checkTableRuntime(ed)

                internalUpdate(eqb)
            } catch (EntityException e) {
                throw new EntityException("Error in update of [${this.toString()}]", e)
            } finally {
                eqb.closeAll()
            }
        }
    }

    protected void internalUpdate(EntityQueryBuilder eqb) {
        eqb.makeConnection()
        eqb.makePreparedStatement()
        eqb.setPreparedStatementValues()
        if (eqb.executeUpdate() == 0)
            throw new EntityException("Tried to update a value that does not exist [${this.toString()}]. SQL used was [${eqb.sqlTopLevel}], parameters were [${eqb.parameters}]")
        setSyncedWithDb()
        getEntityFacadeImpl().getEntityCache().clearCacheForValue(this, false)
    }

    @Override
    void deleteExtended() {
        EntityDefinition ed = getEntityDefinition()

        if (ed.isViewEntity()) {
            throw new EntityException("Delete not implemented for view-entity")
        } else {
            EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append("DELETE FROM ").append(ed.getFullTableName()).append(" WHERE ")

            boolean isFirstPk = true
            for (String fieldName in ed.getPkFieldNames()) {
                if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                        getValueMap().get(fieldName), eqb))
            }

            try {
                getEntityFacadeImpl().entityDbMeta.checkTableRuntime(ed)

                internalDelete(eqb)
            } catch (EntityException e) {
                throw new EntityException("Error in delete of [${this.toString()}]", e)
            } finally {
                eqb.closeAll()
            }
        }
    }

    protected void internalDelete(EntityQueryBuilder eqb) {
        eqb.makeConnection()
        eqb.makePreparedStatement()
        eqb.setPreparedStatementValues()
        if (eqb.executeUpdate() == 0) logger.info("Tried to delete a value that does not exist [${this.toString()}]")
        getEntityFacadeImpl().getEntityCache().clearCacheForValue(this, false)
    }

    @Override
    boolean refreshExtended() {
        EntityDefinition ed = getEntityDefinition()

        // NOTE: this simple approach may not work for view-entities, but not restricting for now

        List<String> pkFieldList = ed.getPkFieldNames()
        ListOrderedSet nonPkFieldList = ed.getFieldNames(false, true, false)
        // NOTE: even if there are no non-pk fields do a refresh in order to see if the record exists or not

        EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
        StringBuilder sql = eqb.getSqlTopLevel()
        sql.append("SELECT ")
        boolean isFirstField = true
        if (nonPkFieldList) {
            for (String fieldName in nonPkFieldList) {
                if (isFirstField) isFirstField = false else sql.append(", ")
                sql.append(ed.getColumnName(fieldName, false))
            }
        } else {
            sql.append("*")
        }

        sql.append(" FROM ").append(ed.getFullTableName()).append(" WHERE ")

        boolean isFirstPk = true
        for (String fieldName in pkFieldList) {
            if (isFirstPk) isFirstPk = false else sql.append(" AND ")
            sql.append(ed.getColumnName(fieldName, false)).append("=?")
            eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                    this.getValueMap().get(fieldName), eqb))
        }

        boolean retVal = false
        try {
            getEntityFacadeImpl().entityDbMeta.checkTableRuntime(ed)

            retVal = internalRefresh(eqb, nonPkFieldList)
        } catch (EntityException e) {
            throw new EntityException("Error in refresh of [${this.toString()}]", e)
        } finally {
            eqb.closeAll()
        }

        return retVal
    }

    protected boolean internalRefresh(EntityQueryBuilder eqb, ListOrderedSet nonPkFieldList) {
        eqb.makeConnection()
        eqb.makePreparedStatement()
        eqb.setPreparedStatementValues()

        boolean retVal = false
        ResultSet rs = eqb.executeQuery()
        if (rs.next()) {
            int j = 1
            for (String fieldName in nonPkFieldList) {
                EntityQueryBuilder.getResultSetValue(rs, j, getEntityDefinition().getFieldNode(fieldName), this, getEntityFacadeImpl())
                j++
            }
            retVal = true
            setSyncedWithDb()
        } else {
            if (logger.traceEnabled) logger.trace("No record found in refresh for entity [${entityName}] with values [${getValueMap()}]")
        }
        return retVal
    }
}
