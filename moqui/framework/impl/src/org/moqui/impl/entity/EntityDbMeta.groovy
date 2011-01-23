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

import java.sql.SQLException
import java.sql.Connection
import java.sql.Statement
import org.moqui.entity.EntityException

class EntityDbMeta {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityDefinition.class)

    protected EntityFacadeImpl efi
    EntityDbMeta(EntityFacadeImpl efi) {
        this.efi = efi
    }

    void createTable(EntityDefinition ed) {
        if (!ed) throw new IllegalArgumentException("No EntityDefinition specified, cannot create table")
        if (ed.isViewEntity()) throw new IllegalArgumentException("Cannot create table for a view entity")

        String groupName = efi.getEntityGroupName(ed.entityName)
        Node databaseNode = efi.getDatabaseNode(groupName)

        Connection con = efi.getConnection(groupName)
        Statement stmt = null

        StringBuilder sql = new StringBuilder("CREATE TABLE ")
        sql.append(ed.getTableName())
        sql.append(" (")

        for (String fieldName in ed.getFieldNames(true, true)) {
            Node fieldNode = ed.getFieldNode(fieldName)
            Node fieldTypeDef = (Node) databaseNode."field-type-def".find({ it."@type" == fieldNode."@type" })
            if (!fieldTypeDef) throw new IllegalArgumentException("In entity field [${ed.entityName}.${fieldName}] could not find conf for type [${fieldNode."@type"}]")

            String javaType = efi.getFieldJavaType(fieldNode."@type", ed.entityName)

            sql.append(ed.getColumnName(fieldName, false))
            sql.append(" ")
            sql.append(fieldTypeDef."@sql-type")

            if ("String" == javaType || "java.lang.String" == javaType) {
                if (databaseNode."@character-set") {
                    sql.append(" CHARACTER SET ")
                    sql.append(databaseNode."@character-set")
                }
                if (databaseNode."@collate") {
                    sql.append(" COLLATE ");
                    sql.append(databaseNode."@collate");
                }
            }

            if (fieldNode."@is-pk" == "true") {
                if (databaseNode."@always-use-constraint-keyword" == "true") {
                    sql.append(" CONSTRAINT NOT NULL")
                } else {
                    sql.append(" NOT NULL")
                }
            }
            sql.append(", ")
        }

        if (databaseNode."@use-pk-constraint-names" != "false") {
            String pkName = "PK_" + ed.getTableName()

            int constraintNameClipLength = databaseNode."@constraint-name-clip-length" as int
            if (pkName.length() > constraintNameClipLength) pkName = pkName.substring(0, constraintNameClipLength)

            sql.append("CONSTRAINT ")
            sql.append(pkName)
        }
        sql.append(" PRIMARY KEY (")
        boolean isFirstPk = true
        for (String pkName in ed.getFieldNames(true, false)) {
            if (isFirstPk) isFirstPk = false else sql.append(", ")
            sql.append(ed.getColumnName(pkName, false))
        }
        sql.append(")")

        sql.append(")")

        // some MySQL-specific inconveniences...
        if (databaseNode."@table-engine") {
            sql.append(" ENGINE ")
            sql.append(databaseNode."@table-engine")
        }
        if (databaseNode."@character-set") {
            sql.append(" CHARACTER SET ")
            sql.append(databaseNode."@character-set")
        }
        if (databaseNode."@collate") {
            sql.append(" COLLATE ")
            sql.append(databaseNode."@collate")
        }

        if (logger.traceEnabled) logger.trace("Create Table with SQL: " + sql.toString())
        try {
            stmt = con.createStatement()
            stmt.executeUpdate(sql.toString())
        } catch (SQLException e) {
            throw new EntityException("SQL Exception while executing the following SQL [${sql.toString()}]", e)
        } finally {
            if (stmt) stmt.close()
            if (con) con.close()
        }
    }
}
