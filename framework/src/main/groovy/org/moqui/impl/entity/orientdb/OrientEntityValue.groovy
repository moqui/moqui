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
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.sql.OCommandSQL

import org.apache.commons.collections.set.ListOrderedSet

import org.moqui.entity.EntityException
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.entity.EntityValueBase

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

class OrientEntityValue extends EntityValueBase {
    protected final static Logger logger = LoggerFactory.getLogger(OrientEntityValue.class)

    OrientDatasourceFactory odf
    ORID recordId = null

    OrientEntityValue(EntityDefinition ed, EntityFacadeImpl efip, OrientDatasourceFactory odf) {
        super(ed, efip)
        this.odf = odf
    }

    OrientEntityValue(EntityDefinition ed, EntityFacadeImpl efip, OrientDatasourceFactory odf, ODocument document) {
        super(ed, efip)
        this.odf = odf
        for (String fieldName in ed.getAllFieldNames()) getValueMap().put(fieldName, document.field(ed.getColumnName(fieldName, false)))
        this.recordId = document.getIdentity()
    }

    @Override
    void createExtended(ListOrderedSet fieldList, Connection con) {
        EntityDefinition ed = getEntityDefinition()
        if (ed.isViewEntity()) throw new EntityException("Create not yet implemented for view-entity")

        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            odf.checkCreateDocumentClass(oddt, ed)

            ODocument od = oddt.newInstance(ed.getTableName())
            for (Map.Entry<String, Object> valueEntry in getValueMap()) {
                od.field(ed.getColumnName(valueEntry.getKey(), false), valueEntry.getValue())
            }
            od.save()
            recordId = od.getIdentity()
        } finally {
            oddt.close()
        }
    }

    @Override
    void updateExtended(List<String> pkFieldList, ListOrderedSet nonPkFieldList, Connection con) {
        EntityDefinition ed = getEntityDefinition()
        if (ed.isViewEntity()) throw new EntityException("Update not yet implemented for view-entity")

        // NOTE: according to OrientDB documentation the native Java query API does not use indexes and such, so use the OSQL approach
        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            odf.checkCreateDocumentClass(oddt, ed)

            // logger.warn("========== updating ${ed.getFullEntityName()} recordId=${recordId} pk=${this.getPrimaryKeys()}")

            StringBuilder sql = new StringBuilder()
            List<Object> paramValues = new ArrayList<Object>()
            sql.append("UPDATE ")
            if (recordId == null) sql.append(ed.getTableName())
            else sql.append("#").append(recordId.getClusterId()).append(":").append(recordId.getClusterPosition())
            sql.append(" SET ")

            boolean isFirstField = true
            for (String fieldName in nonPkFieldList) {
                if (isFirstField) isFirstField = false else sql.append(", ")
                sql.append(ed.getColumnName(fieldName, false)).append("=?")
                paramValues.add(getValueMap().get(fieldName))
            }
            if (recordId == null) {
                sql.append(" WHERE ")
                boolean isFirstPk = true
                for (String fieldName in pkFieldList) {
                    if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                    sql.append(ed.getColumnName(fieldName, false)).append("=?")
                    paramValues.add(getValueMap().get(fieldName))
                }
            }

            int recordsChanged = oddt.command(new OCommandSQL(sql.toString())).execute(paramValues.toArray(new Object[paramValues.size]))
            if (recordsChanged == 0) throw new IllegalArgumentException("Cannot update entity [${ed.getFullEntityName()}] value with pk [${this.getPrimaryKeys()}], document not found")

            /* an interesting alternative, in basic tests is about the same speed...
            StringBuilder sql = new StringBuilder()
            List<Object> paramValues = new ArrayList<Object>()
            sql.append("SELECT FROM ").append(ed.getTableName()).append(" WHERE ")

            boolean isFirstPk = true
            for (String fieldName in pkFieldList) {
                if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                sql.append(ed.getColumnName(fieldName, false)).append(" = ?")
                paramValues.add(getValueMap().get(fieldName))
            }

            // logger.warn("=========== orient update sql=${sql.toString()}; paramValues=${paramValues}")
            OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sql.toString())
            List<ODocument> documentList = oddt.command(query).execute(paramValues.toArray(new Object[paramValues.size]))

            // there should only be one value since we're querying by a set of fields with a unique index (the pk)
            if (!documentList) throw new IllegalArgumentException("Cannot update entity [${ed.getEntityName()}] value with pk [${this.getPrimaryKeys()}], document not found")

            ODocument document = documentList.first()
            for (String fieldName in nonPkFieldList) document.field(fieldName, getValueMap().get(fieldName))
            document.save()
            */
        } finally {
            oddt.close()
        }
    }

    @Override
    void deleteExtended(Connection con) {
        EntityDefinition ed = getEntityDefinition()
        if (ed.isViewEntity()) throw new EntityException("Delete not yet implemented for view-entity")

        // NOTE: according to OrientDB documentation the native Java query API does not use indexes and such, so use the OSQL approach
        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            odf.checkCreateDocumentClass(oddt, ed)

            StringBuilder sql = new StringBuilder()
            List<Object> paramValues = new ArrayList<Object>()
            sql.append("DELETE FROM ")
            if (recordId == null) {
                sql.append(ed.getTableName()).append(" WHERE ")
                boolean isFirstPk = true
                for (String fieldName in ed.getPkFieldNames()) {
                    if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                    sql.append(ed.getColumnName(fieldName, false)).append(" = ?")
                    paramValues.add(getValueMap().get(fieldName))
                }
            } else {
                sql.append("#").append(recordId.getClusterId()).append(":").append(recordId.getClusterPosition())
            }

            int recordsChanged = oddt.command(new OCommandSQL(sql.toString())).execute(paramValues.toArray(new Object[paramValues.size]))
            if (recordsChanged == 0) throw new IllegalArgumentException("Cannot delete entity [${ed.getFullEntityName()}] value with pk [${this.getPrimaryKeys()}], document not found")

            /* alternate approach with query then delete():
            OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sql.toString())
            List<ODocument> documentList = oddt.command(query).execute(paramValues.toArray(new Object[paramValues.size()]))
            // there should only be one value since we're querying by a set of fields with a unique index (the pk)
            if (!documentList) throw new IllegalArgumentException("Cannot delete entity [${ed.getEntityName()}] value with pk [${this.getPrimaryKeys()}], document not found")
            ODocument document = documentList.first()
            document.delete()
            */
        } finally {
            oddt.close()
        }
    }

    @Override
    boolean refreshExtended() {
        EntityDefinition ed = getEntityDefinition()

        // NOTE: according to OrientDB documentation the native Java query API does not use indexes and such, so use the OSQL approach
        ODatabaseDocumentTx oddt = odf.getDatabase()
        try {
            odf.checkCreateDocumentClass(oddt, ed)

            StringBuilder sql = new StringBuilder()
            List<Object> paramValues = new ArrayList<Object>()
            sql.append("SELECT FROM ")
            if (recordId == null) {
                sql.append(ed.getTableName()).append(" WHERE ")
                boolean isFirstPk = true
                for (String fieldName in ed.getPkFieldNames()) {
                    if (isFirstPk) isFirstPk = false else sql.append(" AND ")
                    sql.append(ed.getColumnName(fieldName, false)).append(" = ?")
                    paramValues.add(getValueMap().get(fieldName))
                }
            } else {
                sql.append("#").append(recordId.getClusterId()).append(":").append(recordId.getClusterPosition())
            }

            OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(sql.toString())
            List<ODocument> documentList = oddt.command(query).execute(paramValues.toArray(new Object[paramValues.size]))

            // there should only be one value since we're querying by a set of fields with a unique index (the pk)
            if (!documentList) {
                logger.info("In refresh document not found for entity [${ed.getFullEntityName()}] with pk [${this.getPrimaryKeys()}]")
                return false
            }

            ODocument document = documentList.first()
            for (String fieldName in ed.getFieldNames(false, true, false))
                getValueMap().put(fieldName, document.field(fieldName))

            return true
        } finally {
            oddt.close()
        }
    }
}
