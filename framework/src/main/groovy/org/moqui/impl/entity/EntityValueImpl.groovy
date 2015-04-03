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

import groovy.transform.CompileStatic
import org.apache.commons.collections.set.ListOrderedSet

import org.moqui.entity.EntityException
import org.moqui.entity.EntityValue
import org.moqui.impl.entity.EntityQueryBuilder.EntityConditionParameter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.ResultSet

@CompileStatic
class EntityValueImpl extends EntityValueBase {
    protected final static Logger logger = LoggerFactory.getLogger(EntityValueImpl.class)

    EntityValueImpl(EntityDefinition ed, EntityFacadeImpl efip) { super(ed, efip) }

    @Override
    public EntityValue cloneValue() {
        EntityValueImpl newObj = new EntityValueImpl(getEntityDefinition(), getEntityFacadeImpl())
        newObj.getValueMap().putAll(getValueMap())
        if (getDbValueMap()) newObj.setDbValueMap(new HashMap<String, Object>(getDbValueMap()))
        // don't set mutable (default to mutable even if original was not) or modified (start out not modified)
        return newObj
    }

    @Override
    EntityValue cloneDbValue(boolean getOld) {
        EntityValueImpl newObj = new EntityValueImpl(getEntityDefinition(), getEntityFacadeImpl())
        newObj.getValueMap().putAll(getValueMap())
        for (String fieldName in getEntityDefinition().getAllFieldNames())
            newObj.put(fieldName, getOld ? getOldDbValue(fieldName) : getOriginalDbValue(fieldName))
        newObj.setSyncedWithDb()
        return newObj
    }

    @Override
    void createExtended(ArrayList<String> fieldList, Connection con) {
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

            int size = fieldList.size()
            for (int i = 0; i < size; i++) {
                String fieldName = fieldList.get(i)
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

                if (con != null) eqb.useConnection(con) else eqb.makeConnection()
                eqb.makePreparedStatement()
                for (int i = 0; i < size; i++) {
                    String fieldName = fieldList.get(i)
                    eqb.setPreparedStatementValue(i+1, getValueMap().get(fieldName), getEntityDefinition().getFieldNode(fieldName))
                }
                eqb.executeUpdate()
                setSyncedWithDb()
            } catch (EntityException e) {
                throw new EntityException("Error in create of [${this.toString()}]", e)
            } finally {
                eqb.closeAll()
            }
        }
    }

    @Override
    void updateExtended(ArrayList<String>  pkFieldList, ArrayList<String> nonPkFieldList, Connection con) {
        EntityDefinition ed = getEntityDefinition()

        if (ed.isViewEntity()) {
            throw new EntityException("Update not yet implemented for view-entity")
        } else {
            EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append("UPDATE ").append(ed.getFullTableName()).append(" SET ")

            boolean isFirstField = true
            int size = nonPkFieldList.size()
            for (int i = 0; i < size; i++) {
                String fieldName = nonPkFieldList.get(i)
                if (isFirstField) isFirstField = false else sql.append(", ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                        getValueMap().get(fieldName), eqb))
            }
            sql.append(" WHERE ")
            boolean isFirstPk = true
            int sizePk = pkFieldList.size()
            for (int i = 0; i < sizePk; i++) {
                String fieldName = pkFieldList.get(i)
                if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                        getValueMap().get(fieldName), eqb))
            }

            try {
                getEntityFacadeImpl().entityDbMeta.checkTableRuntime(ed)

                if (con != null) eqb.useConnection(con) else eqb.makeConnection()
                eqb.makePreparedStatement()
                eqb.setPreparedStatementValues()
                if (eqb.executeUpdate() == 0)
                    throw new EntityException("Tried to update a value that does not exist [${this.toString()}]. SQL used was [${eqb.sqlTopLevel}], parameters were [${eqb.parameters}]")
                setSyncedWithDb()
            } catch (EntityException e) {
                throw new EntityException("Error in update of [${this.toString()}]", e)
            } finally {
                eqb.closeAll()
            }
        }
    }

    @Override
    void deleteExtended(Connection con) {
        EntityDefinition ed = getEntityDefinition()

        if (ed.isViewEntity()) {
            throw new EntityException("Delete not implemented for view-entity")
        } else {
            EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append("DELETE FROM ").append(ed.getFullTableName()).append(" WHERE ")

            boolean isFirstPk = true
            ArrayList<String> pkFieldList = ed.getPkFieldNames()
            int sizePk = pkFieldList.size()
            for (int i = 0; i < sizePk; i++) {
                String fieldName = pkFieldList.get(i)
                if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                        getValueMap().get(fieldName), eqb))
            }

            try {
                getEntityFacadeImpl().entityDbMeta.checkTableRuntime(ed)

                if (con != null) eqb.useConnection(con) else eqb.makeConnection()
                eqb.makePreparedStatement()
                eqb.setPreparedStatementValues()
                if (eqb.executeUpdate() == 0) logger.info("Tried to delete a value that does not exist [${this.toString()}]")
            } catch (EntityException e) {
                throw new EntityException("Error in delete of [${this.toString()}]", e)
            } finally {
                eqb.closeAll()
            }
        }
    }

    @Override
    boolean refreshExtended() {
        EntityDefinition ed = getEntityDefinition()

        // table doesn't exist, just return null
        if (!getEntityFacadeImpl().getEntityDbMeta().tableExists(ed)) return null

        // NOTE: this simple approach may not work for view-entities, but not restricting for now

        List<String> pkFieldList = ed.getPkFieldNames()
        List<String> nonPkFieldList = ed.getNonPkFieldNames()
        // NOTE: even if there are no non-pk fields do a refresh in order to see if the record exists or not

        EntityQueryBuilder eqb = new EntityQueryBuilder(ed, getEntityFacadeImpl())
        StringBuilder sql = eqb.getSqlTopLevel()
        sql.append("SELECT ")
        boolean isFirstField = true
        if (nonPkFieldList) {
            int size = nonPkFieldList.size()
            for (int i = 0; i < size; i++) {
                String fieldName = nonPkFieldList.get(i)
                if (isFirstField) isFirstField = false else sql.append(", ")
                sql.append(ed.getColumnName(fieldName, false))
            }
        } else {
            sql.append("*")
        }

        sql.append(" FROM ").append(ed.getFullTableName()).append(" WHERE ")

        boolean isFirstPk = true
        int sizePk = pkFieldList.size()
        for (int i = 0; i < sizePk; i++) {
            String fieldName = pkFieldList.get(i)
            if (isFirstPk) isFirstPk = false else sql.append(" AND ")
            sql.append(ed.getColumnName(fieldName, false)).append("=?")
            eqb.getParameters().add(new EntityConditionParameter(ed.getFieldNode(fieldName),
                    this.getValueMap().get(fieldName), eqb))
        }

        boolean retVal = false
        try {
            // don't check create, above tableExists check is done:
            // efi.getEntityDbMeta().checkTableRuntime(ed)
            // if this is a view-entity and any table in it exists check/create all or will fail with optional members, etc
            if (ed.isViewEntity()) getEntityFacadeImpl().getEntityDbMeta().checkTableRuntime(ed)

            eqb.makeConnection()
            eqb.makePreparedStatement()
            eqb.setPreparedStatementValues()

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
                if (logger.traceEnabled) logger.trace("No record found in refresh for entity [${getEntityName()}] with values [${getValueMap()}]")
            }
        } catch (EntityException e) {
            throw new EntityException("Error in refresh of [${this.toString()}]", e)
        } finally {
            eqb.closeAll()
        }

        return retVal
    }
}
