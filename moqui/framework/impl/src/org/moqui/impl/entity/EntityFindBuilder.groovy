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

import java.sql.PreparedStatement
import org.moqui.entity.EntityException
import java.sql.Types
import java.sql.SQLException
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Clob
import java.sql.Blob
import javax.sql.rowset.serial.SerialBlob
import javax.sql.rowset.serial.SerialClob
import org.moqui.impl.StupidUtilities

class EntityFindBuilder {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityFindBuilder.class)

    protected EntityFindImpl entityFindImpl
    protected EntityDefinition mainEntityDefinition

    protected StringBuilder sqlTopLevel
    protected List<EntityConditionParameter> parameters

    EntityFindBuilder(EntityDefinition entityDefinition, EntityFindImpl entityFindImpl) {
        this.entityFindImpl = entityFindImpl
        this.mainEntityDefinition = entityDefinition

        // this is always going to start with "SELECT ", so just set it here
        this.sqlTopLevel = new StringBuilder("SELECT ")
        this.parameters = new ArrayList()
    }

    /** returns StringBuilder meant to be appended to */
    StringBuilder getSqlTopLevel() {
        return this.sqlTopLevel
    }

    /** returns List of EntityConditionParameter meant to be added to */
    List<EntityConditionParameter> getParameters() {
        return this.parameters
    }

    void makeDistinct() {
        this.sqlTopLevel.append("DISTINCT ")

    }

    void makeSqlSelectFields(Set<String> fieldsToSelect) {
        if (fieldsToSelect.size() > 0) {
            boolean isFirst = true
            for (String fieldName in fieldsToSelect) {
                if (isFirst) isFirst = false else this.sqlTopLevel.append(", ")
                this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(fieldName, true))
            }
        } else {
            this.sqlTopLevel.append("*")
        }
    }

    void makeSqlFromClause() {
        makeSqlFromClause(this.mainEntityDefinition, this.sqlTopLevel)
    }
    void makeSqlFromClause(EntityDefinition localEntityDefinition, StringBuilder localBuilder) {
        localBuilder.append(" FROM ")

        Node entityNode = localEntityDefinition.entityNode

        if (localEntityDefinition.isViewEntity()) {
            Node databaseNode = this.entityFindImpl.efi.getDatabaseNode(localEntityDefinition.entityName)
            String joinStyle = databaseNode."@join-style"

            if ("ansi" != joinStyle && "ansi-no-parenthesis" != joinStyle) {
                throw new IllegalArgumentException("The join-style " + joinStyle + " is not supported")
            }

            boolean useParenthesis = ("ansi" == joinStyle)

            // keep a set of all aliases in the join so far and if the left entity alias isn't there yet, and this
            // isn't the first one, throw an exception
            Set<String> joinedAliasSet = new TreeSet<String>()

            // on initial pass only add opening parenthesis since easier than going back and inserting them, then insert the rest
            StringBuilder restOfStatement = new StringBuilder()
            boolean isFirst = true
            for (Node viewLink in entityNode."view-link") {
                if (useParenthesis) localBuilder.append('(')

                String linkEntityName = entityNode."member-entity".find({ it."@entity-alias" == viewLink."@entity-alias" })."@entity-name"
                EntityDefinition linkEntityDefinition = this.entityFindImpl.efi.getEntityDefinition(linkEntityName)
                String relatedLinkEntityName = entityNode."member-entity".find({ it."@entity-alias" == viewLink."@related-entity-alias" })."@entity-name"
                EntityDefinition relatedLinkEntityDefinition = this.entityFindImpl.efi.getEntityDefinition(relatedLinkEntityName)

                if (isFirst) {
                    // first link, add link entity for this one only, for others add related link entity
                    makeSqlViewTableName(linkEntityDefinition, restOfStatement)
                    restOfStatement.append(" ")
                    restOfStatement.append(viewLink."@entity-alias")

                    joinedAliasSet.add(viewLink."@entity-alias")
                } else {
                    // make sure the left entity alias is already in the join...
                    if (!joinedAliasSet.contains(viewLink."@entity-alias")) {
                        throw new IllegalArgumentException("Tried to link the " + viewLink."@entity-alias" +
                                " alias to the " + viewLink."@related-entity-alias" + " alias of the " +
                                localEntityDefinition.getEntityName() + " view-entity, but it is not the first view-link and has not been included in a previous view-link. In other words, the left/main alias isn't connected to the rest of the member-entities yet.")
                    }
                }
                // now put the rel (right) entity alias into the set that is in the join
                joinedAliasSet.add(viewLink."@related-entity-alias")

                if (viewLink."@related-optional" == "true") {
                    restOfStatement.append(" LEFT OUTER JOIN ")
                } else {
                    restOfStatement.append(" INNER JOIN ")
                }

                makeSqlViewTableName(relatedLinkEntityDefinition, restOfStatement)
                restOfStatement.append(" ")
                restOfStatement.append(viewLink."@related-entity-alias")
                restOfStatement.append(" ON ")

                if (!viewLink."key-map") {
                    throw new IllegalArgumentException("No view-link/join key-maps found for the " +
                            viewLink."@entity-alias" + " and the " + viewLink."@related-entity-alias" + 
                            " member-entities of the " + localEntityDefinition.getEntityName() + " view-entity.")
                }

                boolean isFirstKeyMap = true
                for (Node keyMap in viewLink."key-map") {
                    if (isFirstKeyMap) isFirstKeyMap = false else restOfStatement.append(" AND ")

                    restOfStatement.append(viewLink."@entity-alias")
                    restOfStatement.append(".")
                    restOfStatement.append(sanitizeColumnName(linkEntityDefinition.getColumnName(keyMap."@field-name", false)))

                    restOfStatement.append(" = ")

                    restOfStatement.append(viewLink."@related-entity-alias")
                    restOfStatement.append(".")
                    restOfStatement.append(sanitizeColumnName(relatedLinkEntityDefinition.getColumnName(keyMap."@related-field-name", false)))
                }

                if (viewLink."entity-condition") {
                    // TODO: add any additional manual conditions for the view-link here
                }

                if (useParenthesis) restOfStatement.append(')')
                isFirst = false
            }

            localBuilder.append(restOfStatement.toString())

            // handle member-entities not referenced in any view-link element
            boolean fromEmpty = restOfStatement.length() == 0
            for (Node memberEntity in entityNode."member-entity") {
                EntityDefinition fromEntityDefinition = this.entityFindImpl.efi.getEntityDefinition(memberEntity."@entity-name")
                if (!joinedAliasSet.contains(memberEntity."@entity-alias")) {
                    if (fromEmpty) fromEmpty = false else localBuilder.append(", ")
                    makeSqlViewTableName(fromEntityDefinition, localBuilder)
                    localBuilder.append(" ")
                    localBuilder.append(memberEntity."@entity-alias")
                }
            }
        } else {
            localBuilder.append(localEntityDefinition.getTableName())
        }
    }

    void makeSqlViewTableName(StringBuilder localBuilder) {
        makeSqlViewTableName(this.mainEntityDefinition, localBuilder)
    }
    void makeSqlViewTableName(EntityDefinition localEntityDefinition, StringBuilder localBuilder) {
        if (localEntityDefinition.isViewEntity()) {
            localBuilder.append("(SELECT ")

            boolean isFirst = true
            for (Node aliasNode in localEntityDefinition.entityNode.alias) {
                if (isFirst) isFirst = false else localBuilder.append(", ")
                localBuilder.append(localEntityDefinition.getColumnName(aliasNode."@name", true))
                // TODO: are the next two lines really needed? have removed AS stuff elsewhere since it is not commonly used and not needed
                localBuilder.append(" AS ")
                localBuilder.append(sanitizeColumnName(localEntityDefinition.getColumnName(aliasNode."@name", false)))
            }

            makeSqlFromClause(localEntityDefinition, localBuilder)

            def groupByAliases = localEntityDefinition.entityNode.alias.find({ it."@group-by" == "true" })
            if (groupByAliases) {
                localBuilder.append(" GROUP BY ")
                boolean isFirstGroupBy = true
                for (Node groupByAlias in groupByAliases) {
                    if (isFirstGroupBy) isFirstGroupBy = false else localBuilder.append(", ")
                    localBuilder.append(localEntityDefinition.getColumnName(groupByAlias."@name", true));
                }
            }

            localBuilder.append(")");
        } else {
            localBuilder.append(localEntityDefinition.getTableName())
        }
    }

    void startWhereClause() {
        this.sqlTopLevel.append(" WHERE ")
    }

    void makeGroupByClause() {
        if (this.mainEntityDefinition.isViewEntity()) {
            List groupByAliasNodes = (List) this.mainEntityDefinition.getEntityNode().alias.find({ it."@group-by" == "true" })
            if (groupByAliasNodes) {
                this.sqlTopLevel.append(" GROUP BY ")

                boolean isFirstGroupBy = true
                for (Node aliasNode in groupByAliasNodes) {
                    if (isFirstGroupBy) isFirstGroupBy = false else this.sqlTopLevel.append(", ")
                    this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(aliasNode."@name", false))
                }
            }
        }
    }

    void startHavingClause() {
        this.sqlTopLevel.append(" HAVING ")
    }

    void makeOrderByClause(List orderByFieldList) {

        if (orderByFieldList) {
            this.sqlTopLevel.append(" ORDER BY ")
        }
        boolean isFirst = true
        for (String fieldName in orderByFieldList) {
            if (isFirst) isFirst = false else this.sqlTopLevel.append(", ")

            // Parse the fieldName (can have other stuff in it, need to tear down to just the field name)
            Boolean nullsFirstLast = null
            boolean descending = false
            Boolean caseUpperLower = null

            fieldName = fieldName.trim()

            if (fieldName.toUpperCase().endsWith("NULLS FIRST")) {
                nullsFirstLast = true
                fieldName = fieldName.substring(0, fieldName.length() - "NULLS FIRST".length()).trim()
            }
            if (fieldName.toUpperCase().endsWith("NULLS LAST")) {
                nullsFirstLast = false
                fieldName = fieldName.substring(0, fieldName.length() - "NULLS LAST".length()).trim()
            }

            int startIndex = 0
            int endIndex = fieldName.length()
            if (fieldName.endsWith(" DESC")) {
                descending = true
                endIndex -= 5
            } else if (fieldName.endsWith(" ASC")) {
                descending = false
                endIndex -= 4
            } else if (fieldName.startsWith("-")) {
                descending = true
                startIndex++
            } else if (fieldName.startsWith("+")) {
                descending = false
                startIndex++
            }

            if (fieldName.endsWith(")")) {
                String upperText = fieldName.toUpperCase()
                endIndex--
                if (upperText.startsWith("UPPER(")) {
                    caseUpperLower = true
                    startIndex += 6
                } else if (upperText.startsWith("LOWER(")) {
                    caseUpperLower = false
                    startIndex += 6
                }
            }

            fieldName = fieldName.substring(startIndex, endIndex)

            // not that it's all torn down, build it back up using the column name
            if (caseUpperLower != null) this.sqlTopLevel.append(caseUpperLower ? "UPPER(" : "LOWER(")
            this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(fieldName, false))
            if (caseUpperLower != null) this.sqlTopLevel.append(")")

            this.sqlTopLevel.append(descending ? " DESC" : " ASC")

            if (nullsFirstLast != null) this.sqlTopLevel.append(nullsFirstLast ? " NULLS FIRST" : " NULLS LAST")
        }
    }

    String sanitizeColumnName(String colName) {
        return colName.replace('.', '_').replace('(','_').replace(')','_')
    }

    protected void getResultSetValue(ResultSet rs, int index, Node fieldNode, EntityValueImpl entityValueImpl) throws EntityException {
        String javaType = this.entityFindImpl.efi.getFieldJavaType(fieldNode."@type", entityValueImpl.getEntityName())

        try {
            int typeValue = EntityFacadeImpl.getJavaTypeInt(javaType)
            ResultSetMetaData rsmd = rs.getMetaData()
            int colType = rsmd.getColumnType(index)

            switch (typeValue) {
            case 1:
                if (java.sql.Types.CLOB == colType) {
                    // if the String is empty, try to get a text input stream, this is required for some databases
                    // for larger fields, like CLOBs
                    Clob valueClob = rs.getClob(index)
                    Reader valueReader = null
                    if (valueClob != null) {
                        valueReader = valueClob.getCharacterStream()
                    }

                    if (valueReader != null) {
                        // read up to 4096 at a time
                        char[] inCharBuffer = new char[4096]
                        StringBuilder strBuf = new StringBuilder()
                        int charsRead = 0
                        try {
                            while ((charsRead = valueReader.read(inCharBuffer, 0, 4096)) > 0) {
                                strBuf.append(inCharBuffer, 0, charsRead)
                            }
                            valueReader.close()
                        } catch (IOException e) {
                            throw new EntityException("Error reading long character stream for field [${fieldNode."@name"}] of entity [${entityValueImpl.getEntityName()}]", e)
                        }
                        entityValueImpl.valueMap.put(fieldNode."@name", strBuf.toString())
                    } else {
                        entityValueImpl.valueMap.put(fieldNode."@name", null)
                    }
                } else {
                    String value = rs.getString(index)
                    entityValueImpl.valueMap.put(fieldNode."@name", value)
                }
                break

            case 2:
                entityValueImpl.valueMap.put(fieldNode."@name", rs.getTimestamp(index))
                break

            case 3:
                entityValueImpl.valueMap.put(fieldNode."@name", rs.getTime(index))
                break

            case 4:
                entityValueImpl.valueMap.put(fieldNode."@name", rs.getDate(index))
                break

            case 5:
                int intValue = rs.getInt(index)
                if (rs.wasNull()) {
                    entityValueImpl.valueMap.put(fieldNode."@name", null)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", Integer.valueOf(intValue))
                }
                break

            case 6:
                long longValue = rs.getLong(index)
                if (rs.wasNull()) {
                    entityValueImpl.valueMap.put(fieldNode."@name", null)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", Long.valueOf(longValue))
                }
                break

            case 7:
                float floatValue = rs.getFloat(index)
                if (rs.wasNull()) {
                    entityValueImpl.valueMap.put(fieldNode."@name", null)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", Float.valueOf(floatValue))
                }
                break

            case 8:
                double doubleValue = rs.getDouble(index)
                if (rs.wasNull()) {
                    entityValueImpl.valueMap.put(fieldNode."@name", null)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", Double.valueOf(doubleValue))
                }
                break

            case 9:
                BigDecimal bigDecimalValue = rs.getBigDecimal(index)
                if (rs.wasNull()) {
                    entityValueImpl.valueMap.put(fieldNode."@name", null)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", bigDecimalValue)
                }
                break

            case 10:
                boolean booleanValue = rs.getBoolean(index)
                if (rs.wasNull()) {
                    entityValueImpl.valueMap.put(fieldNode."@name", null)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", Boolean.valueOf(booleanValue))
                }
                break

            case 11:
                Object obj = null
                byte[] originalBytes = rs.getBytes(index)
                InputStream binaryInput = null;
                if (originalBytes != null && originalBytes.length > 0) {
                    binaryInput = new ByteArrayInputStream(originalBytes);
                }
                if (originalBytes != null && originalBytes.length <= 0) {
                    logger.warn("Got byte array back empty for serialized Object with length [${originalBytes.length}] for field [${fieldNode."@name"}] (${index})")
                }

                if (binaryInput != null) {
                    ObjectInputStream inStream = null
                    try {
                        inStream = new ObjectInputStream(binaryInput)
                        obj = inStream.readObject()
                    } catch (IOException ex) {
                        if (logger.traceEnabled) logger.trace("Unable to read BLOB from input stream for field [${fieldNode."@name"}] (${index}): ${ex.toString()}")
                    } catch (ClassNotFoundException ex) {
                        if (logger.traceEnabled) logger.trace("Class not found: Unable to cast BLOB data to an Java object for field [${fieldNode."@name"}] (${index}); most likely because it is a straight byte[], so just using the raw bytes: ${ex.toString()}")
                    } finally {
                        if (inStream != null) {
                            try {
                                inStream.close()
                            } catch (IOException e) {
                                throw new EntityException("Unable to close binary input stream for field [${fieldNode."@name"}] (${index}): ${e.toString()}", e)
                            }
                        }
                    }
                }

                if (obj != null) {
                    entityValueImpl.valueMap.put(fieldNode."@name", obj)
                } else {
                    entityValueImpl.valueMap.put(fieldNode."@name", originalBytes)
                }
                break
            case 12:
                Object originalObject
                byte[] fieldBytes
                try {
                    Blob theBlob = rs.getBlob(index)
                    fieldBytes = theBlob != null ? theBlob.getBytes(1, (int) theBlob.length()) : null
                    originalObject = theBlob
                } catch (SQLException e) {
                    // for backward compatibility if getBlob didn't work try getBytes
                    fieldBytes = rs.getBytes(index)
                    originalObject = fieldBytes
                }

                if (originalObject != null) {
                    // for backward compatibility, check to see if there is a serialized object and if so return that
                    Object blobObject = deserializeField(fieldBytes, index, fieldNode."@name")
                    if (blobObject != null) {
                        entityValueImpl.valueMap.put(fieldNode."@name", blobObject)
                    } else {
                        if (originalObject instanceof Blob) {
                            // NOTE using SerialBlob here instead of the Blob from the database to make sure we can pass it around, serialize it, etc
                            entityValueImpl.valueMap.put(fieldNode."@name", new SerialBlob((Blob) originalObject))
                        } else {
                            entityValueImpl.valueMap.put(fieldNode."@name", originalObject)
                        }
                    }
                }

                break
            case 13:
                entityValueImpl.valueMap.put(fieldNode."@name", new SerialClob(rs.getClob(index)))
                break
            case 14:
            case 15:
                entityValueImpl.valueMap.put(fieldNode."@name", rs.getObject(index))
                break
            }
        } catch (SQLException sqle) {
            throw new EntityException("SQL Exception while getting value for field: [${fieldNode."@name"}] (${index})", sqle)
        }
    }

    static class EntityConditionParameter {
        protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityConditionParameter.class)

        protected Node fieldNode
        protected Object value
        protected EntityFindBuilder efb

        EntityConditionParameter(Node fieldNode, Object value, EntityFindBuilder efb) {
            this.fieldNode = fieldNode
            this.value = value
            this.efb = efb
        }

        Node getFieldNode() { return this.fieldNode }

        Object getValue() { return this.value }

        void setPreparedStatementValue(PreparedStatement ps, int index) throws EntityException {
            String javaType = this.efb.entityFindImpl.efi.getFieldJavaType(this.fieldNode."@type", this.efb.entityFindImpl.getEntity())
            if (this.value) {
                if (!StupidUtilities.isInstanceOf(this.value, javaType)) {
                    // this is only an info level message because under normal operation for most JDBC
                    // drivers this will be okay, but if not then the JDBC driver will throw an exception
                    // and when lower debug levels are on this should help give more info on what happened
                    String fieldClassName = this.value.getClass().getName()
                    if (this.value instanceof byte[]) {
                        fieldClassName = "byte[]"
                    }

                    if (logger.traceEnabled) logger.trace("Type of field " + entityName + "." + modelField.getName() +
                            " is " + fieldClassName + ", was expecting " + javaType + " this may " +
                            "indicate an error in the configuration or in the class, and may result " +
                            "in an SQL-Java data conversion error. Will use the real field type: " +
                            fieldClassName + ", not the definition.")
                    javaType = fieldClassName
                }
            }

            boolean useBinaryTypeForBlob = ("true" == this.efb.entityFindImpl.efi.getDatabaseNode(this.efb.mainEntityDefinition.getEntityName())."@use-binary-type-for-blob")


            try {
                int typeValue = EntityFacadeImpl.getJavaTypeInt(javaType)
                switch (typeValue) {
                case 1:
                    if (this.value != null) {
                        ps.setString(index, (String) this.value)
                    } else {
                        ps.setNull(index, Types.VARCHAR)
                    }
                    break

                case 2:
                    if (this.value != null) {
                        ps.setTimestamp(index, (java.sql.Timestamp) this.value)
                    } else {
                        ps.setNull(index, Types.TIMESTAMP)
                    }
                    break

                case 3:
                    if (this.value != null) {
                        ps.setTime(index, (java.sql.Time) this.value)
                    } else {
                        ps.setNull(index, Types.TIME)
                    }
                    break

                case 4:
                    if (this.value != null) {
                        ps.setDate(index, (java.sql.Date) this.value)
                    } else {
                        ps.setNull(index, Types.DATE)
                    }
                    break

                case 5:
                    if (this.value != null) {
                        ps.setInt(index, (java.lang.Integer) this.value)
                    } else {
                        ps.setNull(index, Types.NUMERIC)
                    }
                    break

                case 6:
                    if (this.value != null) {
                        ps.setLong(index, (java.lang.Long) this.value)
                    } else {
                        ps.setNull(index, Types.NUMERIC)
                    }
                    break

                case 7:
                    if (this.value != null) {
                        ps.setFloat(index, (java.lang.Float) this.value)
                    } else {
                        ps.setNull(index, Types.NUMERIC)
                    }
                    break

                case 8:
                    if (this.value != null) {
                        ps.setDouble(index, (java.lang.Double) this.value);
                    } else {
                        ps.setNull(index, Types.NUMERIC)
                    }
                    break

                case 9:
                    if (this.value != null) {
                        ps.setBigDecimal(index, (java.math.BigDecimal) this.value)
                    } else {
                        ps.setNull(index, Types.NUMERIC)
                    }
                    break

                case 10:
                    if (this.value != null) {
                        ps.setBoolean(index, (java.lang.Boolean) this.value)
                    } else {
                        ps.setNull(index, Types.BOOLEAN)
                    }
                    break

                case 11:
                    if (this.value != null) {
                        try {
                            ByteArrayOutputStream os = new ByteArrayOutputStream()
                            ObjectOutputStream oos = new ObjectOutputStream(os)
                            oos.writeObject(this.value)
                            oos.close()
                            byte[] buf = os.toByteArray()
                            os.close()

                            ByteArrayInputStream is = new ByteArrayInputStream(buf)
                            ps.setBinaryStream(index, is, buf.length)
                            is.close()
                        } catch (IOException ex) {
                            throw new EntityException("Error setting serialized object for field [${this.fieldNode."@name"}]", ex)
                        }
                    } else {
                        if (useBinaryTypeForBlob) {
                            ps.setNull(index, Types.BINARY)
                        } else {
                            ps.setNull(index, Types.BLOB)
                        }
                    }
                    break

                case 12:
                    if (this.value instanceof byte[]) {
                        ps.setBytes(index, (byte[]) this.value)
                    } else if (this.value instanceof java.nio.ByteBuffer) {
                        ps.setBytes(index, ((java.nio.ByteBuffer) this.value).array())
                    } else {
                        if (this.value != null) {
                            ps.setBlob(index, (java.sql.Blob) this.value)
                        } else {
                            if (useBinaryTypeForBlob) {
                                ps.setNull(index, Types.BINARY)
                            } else {
                                ps.setNull(index, Types.BLOB)
                            }
                        }
                    }
                    break

                case 13:
                    if (this.value != null) {
                        ps.setClob(index, (java.sql.Clob) this.value)
                    } else {
                        ps.setNull(index, Types.CLOB)
                    }
                    break

                case 14:
                    if (this.value != null) {
                        ps.setTimestamp(index, new java.sql.Timestamp(((java.util.Date) this.value).getTime()))
                    } else {
                        ps.setNull(index, Types.TIMESTAMP)
                    }
                    break

                case 15:
                    // TODO: is this the best way to do collections and such?
                    if (this.value != null) {
                        ps.setObject(index, this.value, Types.JAVA_OBJECT)
                    } else {
                        ps.setNull(index, Types.JAVA_OBJECT)
                    }
                    break
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Error while setting value on field [" + this.fieldNode."@name" + "] of entity " + this.efb.mainEntityDefinition.getEntityName() + ": " + e.toString(), e)
            } catch (SQLException sqle) {
                throw new EntityException("SQL Exception while setting value on field [" + this.fieldNode."@name" + "] of entity " + this.efb.mainEntityDefinition.getEntityName() + ": " + sqle.toString(), sqle)
            }
        }
    }
}
