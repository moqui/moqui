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
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Timestamp
import javax.transaction.Transaction

import org.apache.commons.collections.set.ListOrderedSet
import org.moqui.context.Cache
import org.moqui.entity.EntityException

class EntityDbMeta {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityDefinition.class)

    protected Cache entityTablesChecked

    protected EntityFacadeImpl efi
    EntityDbMeta(EntityFacadeImpl efi) {
        this.efi = efi
        entityTablesChecked = efi.ecfi.cacheFacade.getCache("entity.tables.checked")
    }

    void checkTableRuntime(EntityDefinition ed) {
        boolean addMissingRuntime = !ed.isViewEntity() &&
                efi.getDatasourceNode(efi.getEntityGroupName(ed.entityName))?."@add-missing-runtime" != "false"
        if (!addMissingRuntime) return

        // if it's in this table we've already checked it
        if (entityTablesChecked.containsKey(ed.entityName)) return

        long startTime = System.currentTimeMillis()
        Transaction parentTransaction = null
        try {
            if (efi.ecfi.transactionFacade.isTransactionInPlace()) {
                parentTransaction = efi.ecfi.transactionFacade.suspend()
            }
            // transaction out of the way, check/create
            if (!tableExists(ed)) {
                createTable(ed)
                // create explicit and foreign key auto indexes
                createIndexes(ed)
                // create foreign keys to all other tables that exist
                createForeignKeys(ed, false)
            } else {
                // table exists, see if it is missing any columns
                ListOrderedSet mcs = getMissingColumns(ed)
                if (mcs) for (String fieldName in mcs) {
                    addColumn(ed, fieldName)
                }
                // create foreign keys after checking each to see if it already exists
                createForeignKeys(ed, true)
            }
            entityTablesChecked.put(ed.entityName, new Timestamp(System.currentTimeMillis()))
        } finally {
            if (parentTransaction != null) efi.ecfi.transactionFacade.resume(parentTransaction)
        }

        logger.info("Checked table for entity [${ed.entityName}] in ${(System.currentTimeMillis()-startTime)/1000} seconds")
    }

    boolean tableExists(EntityDefinition ed) {
        String groupName = efi.getEntityGroupName(ed.entityName)
        Connection con = null
        ResultSet tableSet = null
        try {
            con = efi.getConnection(groupName)
            DatabaseMetaData dbData = con.getMetaData()

            String[] types = ["TABLE", "VIEW", "ALIAS", "SYNONYM"]
            tableSet = dbData.getTables(null, ed.getSchemaName(), ed.getTableName(), types)
            if (tableSet.next()) {
                return true
            } else {
                logger.info("Table for entity [${ed.entityName}] does NOT exist")
                return false
            }
        } catch (Exception e) {
            throw new EntityException("Exception checking to see if table [${ed.getTableName()}] exists", e)
        } finally {
            if (tableSet != null) tableSet.close()
            if (con != null) con.close()
        }
    }

    void createTable(EntityDefinition ed) {
        if (ed == null) throw new IllegalArgumentException("No EntityDefinition specified, cannot create table")
        if (ed.isViewEntity()) throw new IllegalArgumentException("Cannot create table for a view entity")

        String groupName = efi.getEntityGroupName(ed.entityName)
        Node databaseNode = efi.getDatabaseNode(groupName)

        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(ed.getFullTableName()).append(" (")

        for (String fieldName in ed.getFieldNames(true, true)) {
            Node fieldNode = ed.getFieldNode(fieldName)
            String sqlType = efi.getFieldSqlType(fieldNode."@type", ed.entityName)
            String javaType = efi.getFieldJavaType(fieldNode."@type", ed.entityName)

            sql.append(ed.getColumnName(fieldName, false)).append(" ").append(sqlType)

            if ("String" == javaType || "java.lang.String" == javaType) {
                if (databaseNode."@character-set") sql.append(" CHARACTER SET ").append(databaseNode."@character-set")
                if (databaseNode."@collate") sql.append(" COLLATE ").append(databaseNode."@collate")
            }

            if (fieldNode."@is-pk" == "true") {
                if (databaseNode."@always-use-constraint-keyword" == "true") sql.append(" CONSTRAINT")
                sql.append(" NOT NULL")
            }
            sql.append(", ")
        }

        if (databaseNode."@use-pk-constraint-names" != "false") {
            String pkName = "PK_" + ed.getTableName()
            int constraintNameClipLength = (databaseNode."@constraint-name-clip-length"?:"30") as int
            if (pkName.length() > constraintNameClipLength) pkName = pkName.substring(0, constraintNameClipLength)
            sql.append("CONSTRAINT ").append(pkName)
        }
        sql.append(" PRIMARY KEY (")
        boolean isFirstPk = true
        for (String pkName in ed.getFieldNames(true, false)) {
            if (isFirstPk) isFirstPk = false else sql.append(", ")
            sql.append(ed.getColumnName(pkName, false))
        }
        sql.append("))")

        // some MySQL-specific inconveniences...
        if (databaseNode."@table-engine") sql.append(" ENGINE ").append(databaseNode."@table-engine")
        if (databaseNode."@character-set") sql.append(" CHARACTER SET ").append(databaseNode."@character-set")
        if (databaseNode."@collate") sql.append(" COLLATE ").append(databaseNode."@collate")

        if (logger.traceEnabled) logger.trace("Create Table with SQL: " + sql.toString())

        runSqlUpdate(sql, groupName)
        if (logger.infoEnabled) logger.info("Created table [${ed.tableName}] for entity [${ed.entityName}]")
    }

    ListOrderedSet getMissingColumns(EntityDefinition ed) {
        String groupName = efi.getEntityGroupName(ed.entityName)
        Connection con = null
        ResultSet colSet = null
        try {
            con = efi.getConnection(groupName)
            DatabaseMetaData dbData = con.getMetaData()

            ListOrderedSet fnSet = ed.getFieldNames(true, true)
            int fieldCount = fnSet.size()
            colSet = dbData.getColumns(null, ed.getSchemaName(), ed.getTableName(), "%")
            while (colSet.next()) {
                String colName = colSet.getString("COLUMN_NAME")
                String fieldName = null
                for (String fn in fnSet) if (ed.getColumnName(fn, false) == colName) { fieldName = fn; break }
                if (fieldName) fnSet.remove(fieldName)
            }

            if (fnSet.size() == fieldCount) {
                logger.warn("Could not find any columns to match fields for entity [${ed.entityName}]")
                return null
            }
            return fnSet
        } catch (Exception e) {
            logger.error("Exception checking for missing columns in table [${ed.getTableName()}]", e)
            return null
        } finally {
            if (colSet != null) colSet.close()
            if (con != null) con.close()
        }
    }

    void addColumn(EntityDefinition ed, String fieldName) {
        if (ed == null) throw new IllegalArgumentException("No EntityDefinition specified, cannot add column")
        if (ed.isViewEntity()) throw new IllegalArgumentException("Cannot add column for a view entity")

        String groupName = efi.getEntityGroupName(ed.entityName)
        Node databaseNode = efi.getDatabaseNode(groupName)

        Node fieldNode = ed.getFieldNode(fieldName)
        String sqlType = efi.getFieldSqlType(fieldNode."@type", ed.entityName)
        String javaType = efi.getFieldJavaType(fieldNode."@type", ed.entityName)

        StringBuilder sql = new StringBuilder("ALTER TABLE ").append(ed.getFullTableName())
        // NOTE: if any databases need "ADD COLUMN" instead of just "ADD", change this to try both or based on config
        sql.append(" ADD ").append(ed.getColumnName(fieldName, false)).append(" ").append(sqlType)

        if ("String" == javaType || "java.lang.String" == javaType) {
            if (databaseNode."@character-set") sql.append(" CHARACTER SET ").append(databaseNode."@character-set")
            if (databaseNode."@collate") sql.append(" COLLATE ").append(databaseNode."@collate")
        }

        runSqlUpdate(sql, groupName)
        if (logger.infoEnabled) logger.info("Added column [${ed.getColumnName(fieldName, false)}] to table [${ed.tableName}] for field [${fieldName}] of entity [${ed.entityName}]")
    }

    void createIndexes(EntityDefinition ed) {
        if (ed == null) throw new IllegalArgumentException("No EntityDefinition specified, cannot create indexes")
        if (ed.isViewEntity()) throw new IllegalArgumentException("Cannot create indexes for a view entity")

        String groupName = efi.getEntityGroupName(ed.entityName)
        Node databaseNode = efi.getDatabaseNode(groupName)

        if (databaseNode."@use-indexes" == "false") return

        int constraintNameClipLength = (databaseNode."@constraint-name-clip-length"?:"30") as int

        // first do index elements
        for (Node indexNode in ed.entityNode."index") {
            StringBuilder sql = new StringBuilder("CREATE ")
            if (databaseNode."@use-indexes-unique" != "false" && indexNode."@unique" == "true") sql.append("UNIQUE ")
            sql.append("INDEX ").append(indexNode."@name")
            sql.append(" ON ").append(ed.getFullTableName())

            sql.append(" (")
            boolean isFirst = true
            for (Node indexFieldNode in indexNode."index-field") {
                if (isFirst) isFirst = false else sql.append(", ")
                sql.append(ed.getColumnName(indexFieldNode."@name", false))
            }
            sql.append(")")

            runSqlUpdate(sql, groupName)
        }

        // do fk auto indexes
        if (databaseNode."@use-foreign-key-indexes" == "false") return
        for (Node relNode in ed.entityNode."relationship") {
            if (relNode."@type" != "one") continue

            StringBuilder indexName = new StringBuilder()
            if (relNode."@fk-name") indexName.append(relNode."@fk-name")
            if (!indexName) indexName.append(ed.entityName).append(relNode."@title"?:"").append(relNode."@related-entity-name")
            shrinkName(indexName, constraintNameClipLength-3)
            indexName.insert(0, "IDX")

            StringBuilder sql = new StringBuilder("CREATE INDEX ")
            sql.append(indexName.toString()).append(" ON ").append(ed.getFullTableName())

            sql.append(" (")
            Map keyMap = ed.getRelationshipExpandedKeyMap(relNode)
            boolean isFirst = true
            for (String fieldName in keyMap.keySet()) {
                if (isFirst) isFirst = false else sql.append(", ")
                sql.append(ed.getColumnName(fieldName, false))
            }
            sql.append(")")

            runSqlUpdate(sql, groupName)
        }
    }

    Boolean foreignKeyExists(EntityDefinition ed, EntityDefinition relEd, Node relNode) {
        String groupName = efi.getEntityGroupName(ed.entityName)
        Connection con = null
        ResultSet ikSet = null
        try {
            con = efi.getConnection(groupName)
            DatabaseMetaData dbData = con.getMetaData()

            // don't rely on constraint name, look at related table name, keys

            // get set of fields on main entity to match against (more unique than fields on related entity)
            Map keyMap = ed.getRelationshipExpandedKeyMap(relNode)
            Set<String> fieldNames = new HashSet(keyMap.keySet())

            ikSet = dbData.getImportedKeys(null, ed.getSchemaName(), ed.getTableName())
            while (ikSet.next()) {
                String pkTable = ikSet.getString("PKTABLE_NAME")
                if (pkTable != relEd.tableName) continue
                String fkCol = ikSet.getString("FKCOLUMN_NAME")
                String foundField = null
                for (String fn in fieldNames) if (ed.getColumnName(fn, false) == fkCol) foundField = fn
                if (foundField) fieldNames.remove(foundField)
            }

            // if we found all of the key-map field-names then fieldNames will be empty, and we have a full fk
            return (fieldNames.size() == 0)
        } catch (Exception e) {
            logger.error("Exception checking to see if foreign key exists for table [${ed.getTableName()}]", e)
            return null
        } finally {
            if (ikSet != null) ikSet.close()
            if (con != null) con.close()
        }
    }

    void createForeignKeys(EntityDefinition ed, boolean checkFkExists) {
        if (ed == null) throw new IllegalArgumentException("No EntityDefinition specified, cannot create foreign keys")
        if (ed.isViewEntity()) throw new IllegalArgumentException("Cannot create foreign keys for a view entity")

        // TODO: in order to get all FKs in place by the time they are used we will probably need to check all incoming
        // TODO: FKs as well as outgoing because of entity use order, tables not rechecked after first hit, etc

        String groupName = efi.getEntityGroupName(ed.entityName)
        Node databaseNode = efi.getDatabaseNode(groupName)

        if (databaseNode."@use-foreign-keys" == "false") return

        int constraintNameClipLength = (databaseNode."@constraint-name-clip-length"?:"30") as int

        for (Node relNode in ed.entityNode."relationship") {
            if (relNode."@type" != "one") continue

            EntityDefinition relEd = efi.getEntityDefinition(relNode."@related-entity-name")
            if (relEd == null) throw new IllegalArgumentException("Entity [${relNode."@related-entity-name"}] does not exist, was in relationship.@related-entity-name of entity [${ed.entityName}]")
            if (!tableExists(relEd)) {
                logger.warn("Not creating foreign key from entity [${ed.entityName}] to related entity [${relEd.entityName}] because related entity does not yet have a table for it")
                continue
            }
            if (checkFkExists) {
                Boolean fkExists = foreignKeyExists(ed, relEd, relNode)
                if (fkExists != null && !fkExists) {
                    logger.info("Not creating foreign key from entity [${ed.entityName}] to related entity [${relEd.entityName}] with title [${relNode."@title"}] because it already exists (matched by key mappings)")
                    continue
                }
                // if we get a null back there was an error, and we'll try to create the FK, which may result in another error
            }

            StringBuilder constraintName = new StringBuilder()
            if (relNode."@fk-name") constraintName.append(relNode."@fk-name")
            if (!constraintName) constraintName.append(ed.entityName).append(relNode."@title"?:"").append(relNode."@related-entity-name")
            shrinkName(constraintName, constraintNameClipLength)

            Map keyMap = ed.getRelationshipExpandedKeyMap(relNode)
            List<String> keyMapKeys = new ArrayList(keyMap.keySet())
            StringBuilder sql = new StringBuilder("ALTER TABLE ").append(ed.getFullTableName()).append(" ADD ")
            if (databaseNode."@fk-style" == "name_fk") {
                sql.append(" FOREIGN KEY ").append(constraintName.toString()).append(" (")
                boolean isFirst = true
                for (String fieldName in keyMapKeys) {
                    if (isFirst) isFirst = false else sql.append(", ")
                    sql.append(ed.getColumnName(fieldName, false))
                }
                sql.append(")")
            } else {
                sql.append("CONSTRAINT ").append(constraintName.toString()).append(" FOREIGN KEY (")
                boolean isFirst = true
                for (String fieldName in keyMapKeys) {
                    if (isFirst) isFirst = false else sql.append(", ")
                    sql.append(ed.getColumnName(fieldName, false))
                }
                sql.append(")");
            }
            sql.append(" REFERENCES ").append(relEd.getFullTableName()).append(" (")
            boolean isFirst = true
            for (String keyName in keyMapKeys) {
                if (isFirst) isFirst = false else sql.append(", ")
                sql.append(relEd.getColumnName((String) keyMap.get(keyName), false))
            }
            sql.append(")")
            if (databaseNode."@use-fk-initially-deferred") {
                sql.append(" INITIALLY DEFERRED")
            }

            runSqlUpdate(sql, groupName)
        }
    }

    void shrinkName(StringBuilder name, int maxLength) {
        if (name.length() > maxLength) {
            // remove all vowels
            for (int i = name.length()-1; i >= 0 && name.length() > maxLength; i--) {
                if ("AEIOUaeiou".contains(name.charAt(i) as String)) name.deleteCharAt(i)
            }
            // clip
            if (name.length() > maxLength) {
                name.delete(maxLength-1, name.length())
            }
        }
    }

    void runSqlUpdate(StringBuilder sql, String groupName) {
        Connection con = null
        Statement stmt = null
        try {
            con = efi.getConnection(groupName)
            stmt = con.createStatement()
            stmt.executeUpdate(sql.toString())
        } catch (SQLException e) {
            logger.error("SQL Exception while executing the following SQL [${sql.toString()}]: ${e.toString()}")
        } finally {
            if (stmt != null) stmt.close()
            if (con != null) con.close()
        }
    }
}
