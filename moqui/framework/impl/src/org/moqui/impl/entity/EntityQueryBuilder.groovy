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

import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import javax.sql.rowset.serial.SerialClob
import javax.sql.rowset.serial.SerialBlob

import org.moqui.entity.EntityException
import org.moqui.impl.StupidUtilities

class EntityQueryBuilder {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityQueryBuilder.class)

    protected EntityFacadeImpl efi
    protected EntityDefinition mainEntityDefinition
    protected StringBuilder sqlTopLevel = new StringBuilder()
    protected List<EntityConditionParameter> parameters = new ArrayList()

    protected PreparedStatement ps
    protected ResultSet rs
    protected Connection connection

    EntityQueryBuilder(EntityDefinition entityDefinition, EntityFacadeImpl efi) {
        this.mainEntityDefinition = entityDefinition
        this.efi = efi
    }

    /** @return StringBuilder meant to be appended to */
    StringBuilder getSqlTopLevel() {
        return this.sqlTopLevel
    }

    /** returns List of EntityConditionParameter meant to be added to */
    List<EntityConditionParameter> getParameters() {
        return this.parameters
    }

    Connection makeConnection() {
        this.connection = this.efi.getConnection(this.efi.getEntityGroupName(this.mainEntityDefinition.getEntityName()))
        return this.connection
    }

    protected void handleSqlException(Exception e, String sql) {
        throw new EntityException("SQL Exception with statement:" + sql, e)
    }

    PreparedStatement makePreparedStatement() {
        if (!this.connection) throw new IllegalStateException("Cannot make PreparedStatement, no Connection in place")
        String sql = this.getSqlTopLevel().toString()
        try {
            this.ps = connection.prepareStatement(sql)
        } catch (SQLException sqle) {
            handleSqlException(sqle, sql)
        }
        return this.ps
    }

    ResultSet executeQuery() throws EntityException {
        if (!this.ps) throw new IllegalStateException("Cannot Execute Query, no PreparedStatement in place")
        try {
            this.rs = this.ps.executeQuery()
            return this.rs
        } catch (SQLException sqle) {
            throw new EntityException("Error in query for:" + this.sqlTopLevel, sqle)
        }
    }

    public int executeUpdate() throws EntityException {
        if (!this.ps) throw new IllegalStateException("Cannot Execute Update, no PreparedStatement in place")
        try {
            return ps.executeUpdate()
        } catch (SQLException sqle) {
            throw new EntityException("Error in update for:" + this.sqlTopLevel, sqle)
        }
    }

    /** NOTE: this should be called in a finally clause to make sure things are closed */
    void closeAll() {
        if (this.ps != null) {
            this.ps.close()
            this.ps = null
        }
        if (this.rs != null) {
            this.rs.close()
            this.rs = null
        }
        if (this.connection != null) {
            this.connection.close()
            this.connection = null
        }
    }

    /** Only close the PreparedStatement, leave the ResultSet and Connection open, but null references to them
     * NOTE: this should be called in a finally clause to make sure things are closed
     */
    void releaseAll() {
        this.ps = null
        this.rs = null
        this.connection = null
    }

    String sanitizeColumnName(String colName) {
        return colName.replace('.', '_').replace('(','_').replace(')','_')
    }

    void getResultSetValue(int index, Node fieldNode, EntityValueImpl entityValueImpl) throws EntityException {
        getResultSetValue(this.rs, index, fieldNode, entityValueImpl, this.efi)
    }

    void setPreparedStatementValue(int index, Object value, Node fieldNode) throws EntityException {
        setPreparedStatementValue(this.ps, index, value, fieldNode, this.mainEntityDefinition.getEntityName(), this.efi)
    }

    void setPreparedStatementValues() {
        // set all of the values from the SQL building in efb
        int paramIndex = 1
        for (EntityConditionParameter entityConditionParam: getParameters()) {
            entityConditionParam.setPreparedStatementValue(paramIndex)
            paramIndex++
        }
    }

    static class EntityConditionParameter {
        protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityConditionParameter.class)

        protected Node fieldNode
        protected Object value
        protected EntityQueryBuilder eqb

        EntityConditionParameter(Node fieldNode, Object value, EntityQueryBuilder eqb) {
            this.fieldNode = fieldNode
            this.value = value
            this.eqb = eqb
        }

        Node getFieldNode() { return this.fieldNode }

        Object getValue() { return this.value }

        void setPreparedStatementValue(int index) throws EntityException {
            setPreparedStatementValue(this.eqb.ps, index, this.value, this.fieldNode,
                    this.eqb.mainEntityDefinition.getEntityName(), this.eqb.efi)
        }

        @Override
        String toString() { return fieldNode."@name" + ":" + value }
    }

    static void getResultSetValue(ResultSet rs, int index, Node fieldNode, EntityValueImpl entityValueImpl,
                                            EntityFacadeImpl efi) throws EntityException {
        String javaType = efi.getFieldJavaType(fieldNode."@type", entityValueImpl.getEntityName())

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
                        try {
                            int charsRead
                            while ((charsRead = valueReader.read(inCharBuffer, 0, 4096)) > 0) {
                                strBuf.append(inCharBuffer, 0, charsRead)
                            }
                            valueReader.close()
                        } catch (IOException e) {
                            throw new EntityException("Error reading long character stream for field [${fieldNode."@name"}] of entity [${entityValueImpl.getEntityName()}]", e)
                        }
                        entityValueImpl.getValueMap().put(fieldNode."@name", strBuf.toString())
                    } else {
                        entityValueImpl.getValueMap().put(fieldNode."@name", null)
                    }
                } else {
                    String value = rs.getString(index)
                    entityValueImpl.getValueMap().put(fieldNode."@name", value)
                }
                break

            case 2:
                entityValueImpl.getValueMap().put(fieldNode."@name", rs.getTimestamp(index))
                break

            case 3:
                entityValueImpl.getValueMap().put(fieldNode."@name", rs.getTime(index))
                break

            case 4:
                entityValueImpl.getValueMap().put(fieldNode."@name", rs.getDate(index))
                break

            case 5:
                int intValue = rs.getInt(index)
                if (rs.wasNull()) {
                    entityValueImpl.getValueMap().put(fieldNode."@name", null)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", Integer.valueOf(intValue))
                }
                break

            case 6:
                long longValue = rs.getLong(index)
                if (rs.wasNull()) {
                    entityValueImpl.getValueMap().put(fieldNode."@name", null)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", Long.valueOf(longValue))
                }
                break

            case 7:
                float floatValue = rs.getFloat(index)
                if (rs.wasNull()) {
                    entityValueImpl.getValueMap().put(fieldNode."@name", null)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", Float.valueOf(floatValue))
                }
                break

            case 8:
                double doubleValue = rs.getDouble(index)
                if (rs.wasNull()) {
                    entityValueImpl.getValueMap().put(fieldNode."@name", null)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", Double.valueOf(doubleValue))
                }
                break

            case 9:
                BigDecimal bigDecimalValue = rs.getBigDecimal(index)
                if (rs.wasNull()) {
                    entityValueImpl.getValueMap().put(fieldNode."@name", null)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", bigDecimalValue)
                }
                break

            case 10:
                boolean booleanValue = rs.getBoolean(index)
                if (rs.wasNull()) {
                    entityValueImpl.getValueMap().put(fieldNode."@name", null)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", Boolean.valueOf(booleanValue))
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
                    entityValueImpl.getValueMap().put(fieldNode."@name", obj)
                } else {
                    entityValueImpl.getValueMap().put(fieldNode."@name", originalBytes)
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
                        entityValueImpl.getValueMap().put(fieldNode."@name", blobObject)
                    } else {
                        if (originalObject instanceof Blob) {
                            // NOTE using SerialBlob here instead of the Blob from the database to make sure we can pass it around, serialize it, etc
                            entityValueImpl.getValueMap().put(fieldNode."@name", new SerialBlob((Blob) originalObject))
                        } else {
                            entityValueImpl.getValueMap().put(fieldNode."@name", originalObject)
                        }
                    }
                }

                break
            case 13:
                entityValueImpl.getValueMap().put(fieldNode."@name", new SerialClob(rs.getClob(index)))
                break
            case 14:
            case 15:
                entityValueImpl.getValueMap().put(fieldNode."@name", rs.getObject(index))
                break
            }
        } catch (SQLException sqle) {
            throw new EntityException("SQL Exception while getting value for field: [${fieldNode."@name"}] (${index})", sqle)
        }
    }

    static void setPreparedStatementValue(PreparedStatement ps, int index, Object value, Node fieldNode,
                                          String entityName, EntityFacadeImpl efi) throws EntityException {
        String javaType = efi.getFieldJavaType(fieldNode."@type", entityName)
        if (value) {
            if (!StupidUtilities.isInstanceOf(value, javaType)) {
                // this is only an info level message because under normal operation for most JDBC
                // drivers this will be okay, but if not then the JDBC driver will throw an exception
                // and when lower debug levels are on this should help give more info on what happened
                String fieldClassName = value.getClass().getName()
                if (value instanceof byte[]) {
                    fieldClassName = "byte[]"
                }

                if (logger.traceEnabled) logger.trace("Type of field " + entityName + "." + fieldNode."@name" +
                        " is " + fieldClassName + ", was expecting " + javaType + " this may " +
                        "indicate an error in the configuration or in the class, and may result " +
                        "in an SQL-Java data conversion error. Will use the real field type: " +
                        fieldClassName + ", not the definition.")
                javaType = fieldClassName
            }
        }

        boolean useBinaryTypeForBlob = ("true" == efi.getDatabaseNode(efi.getEntityGroupName(entityName))."@use-binary-type-for-blob")

        try {
            int typeValue = EntityFacadeImpl.getJavaTypeInt(javaType)
            switch (typeValue) {
            case 1:
                if (value != null) {
                    ps.setString(index, (String) value)
                } else {
                    ps.setNull(index, Types.VARCHAR)
                }
                break

            case 2:
                if (value != null) {
                    ps.setTimestamp(index, (java.sql.Timestamp) value)
                } else {
                    ps.setNull(index, Types.TIMESTAMP)
                }
                break

            case 3:
                if (value != null) {
                    ps.setTime(index, (java.sql.Time) value)
                } else {
                    ps.setNull(index, Types.TIME)
                }
                break

            case 4:
                if (value != null) {
                    ps.setDate(index, (java.sql.Date) value)
                } else {
                    ps.setNull(index, Types.DATE)
                }
                break

            case 5:
                if (value != null) {
                    ps.setInt(index, (java.lang.Integer) value)
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 6:
                if (value != null) {
                    ps.setLong(index, (java.lang.Long) value)
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 7:
                if (value != null) {
                    ps.setFloat(index, (java.lang.Float) value)
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 8:
                if (value != null) {
                    ps.setDouble(index, (java.lang.Double) value);
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 9:
                if (value != null) {
                    ps.setBigDecimal(index, (java.math.BigDecimal) value)
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 10:
                if (value != null) {
                    ps.setBoolean(index, (java.lang.Boolean) value)
                } else {
                    ps.setNull(index, Types.BOOLEAN)
                }
                break

            case 11:
                if (value != null) {
                    try {
                        ByteArrayOutputStream os = new ByteArrayOutputStream()
                        ObjectOutputStream oos = new ObjectOutputStream(os)
                        oos.writeObject(value)
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
                if (value instanceof byte[]) {
                    ps.setBytes(index, (byte[]) value)
                } else if (value instanceof java.nio.ByteBuffer) {
                    ps.setBytes(index, ((java.nio.ByteBuffer) value).array())
                } else {
                    if (value != null) {
                        ps.setBlob(index, (java.sql.Blob) value)
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
                if (value != null) {
                    ps.setClob(index, (java.sql.Clob) value)
                } else {
                    ps.setNull(index, Types.CLOB)
                }
                break

            case 14:
                if (value != null) {
                    ps.setTimestamp(index, new java.sql.Timestamp(((java.util.Date) value).getTime()))
                } else {
                    ps.setNull(index, Types.TIMESTAMP)
                }
                break

            case 15:
                // TODO: is this the best way to do collections and such?
                if (value != null) {
                    ps.setObject(index, value, Types.JAVA_OBJECT)
                } else {
                    ps.setNull(index, Types.JAVA_OBJECT)
                }
                break
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error while setting value on field [" + fieldNode."@name" + "] of entity " + entityName + ": " + e.toString(), e)
        } catch (SQLException sqle) {
            throw new EntityException("SQL Exception while setting value on field [" + fieldNode."@name" + "] of entity " + entityName + ": " + sqle.toString(), sqle)
        }
    }
}
