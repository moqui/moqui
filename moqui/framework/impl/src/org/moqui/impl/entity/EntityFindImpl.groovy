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

import org.moqui.entity.EntityFind
import org.moqui.entity.EntityDynamicView
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityException
import org.moqui.impl.entity.EntityConditionFactoryImpl.EntityConditionImplBase
import org.moqui.impl.entity.EntityFindBuilder.EntityConditionParameter

import java.sql.ResultSet
import java.sql.Connection
import java.sql.SQLException
import java.sql.PreparedStatement
import java.sql.ResultSetMetaData
import java.sql.Clob
import java.sql.Blob
import java.sql.Types
import javax.sql.rowset.serial.SerialBlob
import javax.sql.rowset.serial.SerialClob

class EntityFindImpl implements EntityFind {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityFindImpl.class)

    protected final EntityFacadeImpl efi

    protected String entityName
    protected EntityDynamicView dynamicView = null

    protected Map<String, Object> simpleAndMap = null
    protected EntityConditionImplBase whereEntityCondition = null
    protected EntityConditionImplBase havingEntityCondition = null

    /** This is always a TreeSet so that we can get the results in a consistent order */
    protected TreeSet<String> fieldsToSelect = null
    protected List<String> orderByFields = null

    protected boolean useCache = false
    protected boolean forUpdate = false

    protected int resultSetType = ResultSet.TYPE_SCROLL_INSENSITIVE
    protected int resultSetConcurrency = ResultSet.CONCUR_READ_ONLY
    protected Integer fetchSize = null
    protected Integer maxRows = null
    protected boolean distinct = false

    EntityFindImpl(EntityFacadeImpl efi, String entityName) {
        this.efi = efi
        this.entityName = entityName
    }

    /** @see org.moqui.entity.EntityFind#entity(String) */
    EntityFind entity(String entityName) {
        this.entityName = entityName
        return this
    }

    /** @see org.moqui.entity.EntityFind#getEntity() */
    String getEntity() {
        return this.entityName
    }

    /** @see org.moqui.entity.EntityFind#entityDynamicView(EntityDynamicView) */
    EntityFind entityDynamicView(EntityDynamicView dynamicView) {
        this.dynamicView = dynamicView
        return this
    }

    /** @see org.moqui.entity.EntityFind#getEntityDynamicView() */
    String getEntityDynamicView() {
        return this.dynamicView
    }

    // ======================== Conditions (Where and Having) =================

    /** @see org.moqui.entity.EntityFind#condition(String, Object) */
    EntityFind condition(String fieldName, Object value) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap();
        this.simpleAndMap.put(fieldName, value)
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(Map<String,?>) */
    EntityFind condition(Map<String, ?> fields) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap();
        this.simpleAndMap.putAll(fields)
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(EntityCondition) */
    EntityFind condition(EntityCondition condition) {
        if (this.whereEntityCondition) {
            this.whereEntityCondition = this.efi.conditionFactory.makeCondition(
                    this.whereEntityCondition, EntityCondition.JoinOperator.AND, condition)
        } else {
            this.whereEntityCondition = (EntityConditionImplBase) condition
        }
        return this
    }

    /** @see org.moqui.entity.EntityFind#havingCondition(EntityCondition) */
    EntityFind havingCondition(EntityCondition condition) {
        if (this.havingEntityCondition) {
            this.havingEntityCondition = this.efi.conditionFactory.makeCondition(
                    this.havingEntityCondition, EntityCondition.JoinOperator.AND, condition)
        } else {
            this.havingEntityCondition = (EntityConditionImplBase) condition
        }
        return this
    }

    /** @see org.moqui.entity.EntityFind#getWhereEntityCondition() */
    EntityCondition getWhereEntityCondition() {
        if (this.simpleAndMap) {
            EntityCondition simpleAndMapCond = this.efi.conditionFactory.makeCondition(this.simpleAndMap)
            if (this.whereEntityCondition) {
                return this.efi.conditionFactory.makeCondition(simpleAndMapCond, EntityCondition.JoinOperator.AND, this.whereEntityCondition)
            } else {
                return simpleAndMapCond
            }
        } else {
            return this.whereEntityCondition
        }
    }

    /** @see org.moqui.entity.EntityFind#getHavingEntityCondition() */
    EntityCondition getHavingEntityCondition() {
        return this.havingEntityCondition
    }

    // ======================== General/Common Options ========================

    /** @see org.moqui.entity.EntityFind#selectField(String) */
    EntityFind selectField(String fieldToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new TreeSet()
        this.fieldsToSelect.add(fieldToSelect)
        return this
    }

    /** @see org.moqui.entity.EntityFind#selectFields(Collection<String>) */
    EntityFind selectFields(Collection<String> fieldsToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new TreeSet()
        this.fieldsToSelect.addAll(fieldsToSelect)
        return this
    }

    /** @see org.moqui.entity.EntityFind#getSelectFields() */
    Set<String> getSelectFields() {
        return Collections.unmodifiableSet(this.fieldsToSelect)
    }

    /** @see org.moqui.entity.EntityFind#orderBy(String) */
    EntityFind orderBy(String orderByFieldName) {
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        this.orderByFields.add(orderByFieldName)
        return this
    }

    /** @see org.moqui.entity.EntityFind#orderBy(List<String>) */
    EntityFind orderBy(List<String> orderByFieldNames) {
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        this.orderByFields.addAll(orderByFieldNames)
        return this
    }

    /** @see org.moqui.entity.EntityFind#getOrderBy() */
    List<String> getOrderBy() {
        return Collections.unmodifiableList(this.orderByFields)
    }

    /** @see org.moqui.entity.EntityFind#useCache(boolean) */
    EntityFind useCache(boolean useCache) {
        this.useCache = useCache
        return this
    }

    /** @see org.moqui.entity.EntityFind#getUseCache() */
    boolean getUseCache() {
        return this.useCache
    }

    /** @see org.moqui.entity.EntityFind#forUpdate(boolean) */
    EntityFind forUpdate(boolean forUpdate) {
        this.forUpdate = forUpdate
        return this
    }

    /** @see org.moqui.entity.EntityFind#getForUpdate() */
    boolean getForUpdate() {
        return this.forUpdate
    }

    // ======================== Advanced Options ==============================

    /** @see org.moqui.entity.EntityFind#resultSetType(int) */
    EntityFind resultSetType(int resultSetType) {
        this.resultSetType = resultSetType
        return this
    }

    /** @see org.moqui.entity.EntityFind#getResultSetType() */
    int getResultSetType() {
        return this.resultSetType
    }

    /** @see org.moqui.entity.EntityFind#resultSetConcurrency(int) */
    EntityFind resultSetConcurrency(int resultSetConcurrency) {
        this.resultSetConcurrency = resultSetConcurrency
        return this
    }

    /** @see org.moqui.entity.EntityFind#getResultSetConcurrency() */
    int getResultSetConcurrency() {
        return this.resultSetConcurrency
    }

    /** @see org.moqui.entity.EntityFind#fetchSize(int) */
    EntityFind fetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize
        return this
    }

    /** @see org.moqui.entity.EntityFind#getFetchSize() */
    Integer getFetchSize() {
        return this.fetchSize
    }

    /** @see org.moqui.entity.EntityFind#maxRows(int) */
    EntityFind maxRows(Integer maxRows) {
        this.maxRows = maxRows
        return this
    }

    /** @see org.moqui.entity.EntityFind#getMaxRows() */
    Integer getMaxRows() {
        return this.maxRows
    }

    /** @see org.moqui.entity.EntityFind#distinct(boolean) */
    EntityFind distinct(boolean distinct) {
        this.distinct = distinct
        return this
    }

    /** @see org.moqui.entity.EntityFind#getDistinct() */
    boolean getDistinct() {
        return this.distinct
    }

    // ======================== Run Find Methods ==============================

    /** @see org.moqui.entity.EntityFind#one() */
    EntityValue one() throws EntityException {
        // for find one we'll always use the basic result set type and concurrency:
        this.resultSetType(ResultSet.TYPE_FORWARD_ONLY)
        this.resultSetConcurrency(ResultSet.CONCUR_READ_ONLY)

        EntityFindBuilder efb = new EntityFindBuilder(this);

        if (this.dynamicView) {
            throw new IllegalArgumentException("Dynamic View not supported for 'one' find.")
        }
        EntityDefinition entityDefinition = this.efi.getEntityDefinition(this.entityName)

        // we always want fieldsToSelect populated so that we know the order of the results coming back
        if (!this.fieldsToSelect) this.fieldsToSelect = entityDefinition.getFieldNames(false, true) 
        efb.makeSqlSelectFields(this.fieldsToSelect, entityDefinition)
        efb.makeSqlFromClause(entityDefinition, null)

        // where clause only for one/pk query
        efb.startWhereClause()
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        whereCondition.makeSqlWhere(efb)

        // run the SQL now that it is built
        PreparedStatement ps
        ResultSet rs
        try {
            ps = makePreparedStatement(efb)

            // set all of the values from the SQL building in efb
            int paramIndex = 1
            for (EntityConditionParameter entityConditionParam: efb.getParameters()) {
                setPreparedStatementValue(ps, paramIndex, entityConditionParam)
                paramIndex++
            }

            rs = ps.executeQuery()

            if (rs.next()) {
                EntityValueImpl newEntityValue = (EntityValueImpl) this.efi.makeValue(this.getEntity())
                int j = 1
                for (String fieldName in this.fieldsToSelect) {
                    getResultSetValue(rs, j, entityDefinition.getFieldNode(fieldName), newEntityValue)
                    j++
                }
            } else {
                throw new EntityException("Result set was empty for find on entity [${this.entityName}] with condition [${whereCondition.toString()}]");
            }
        } finally {
            ps.close()
            rs.close()
            // TODO: close the connection? or do as part of context open/close along with transaction?
        }

        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#list() */
    EntityList list() throws EntityException {
        EntityListIterator eli = this.iterator()
        return eli.getCompleteList()
    }

    /** @see org.moqui.entity.EntityFind#iterator() */
    EntityListIterator iterator() throws EntityException {
        EntityFindBuilder efb = new EntityFindBuilder(this)

        if (this.dynamicView) {
            // TODO: implement for dynamic views
        } else {
            EntityDefinition entityDefinition = this.efi.getEntityDefinition(this.entityName)
            // TODO from/etc like in one() above
        }

        // where clause
        efb.startWhereClause()
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        whereCondition.makeSqlWhere(efb)

        // group by clause

        // having clause

        // order by clause

        // run the query

        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#count() */
    long count() throws EntityException {
        // TODO: implement this
        return 0;
    }

    // ========= Internal Methods ==========
    protected PreparedStatement makePreparedStatement(EntityFindBuilder efb) {
        String sql = efb.getSqlTopLevel().toString()
        Connection connection = this.efi.getConnection(this.efi.getEntityGroupName(this.entityName))
        PreparedStatement ps
        try {
            ps = connection.prepareStatement(sql, this.resultSetType, this.resultSetConcurrency)
            if (this.maxRows > 0) ps.setMaxRows(this.maxRows)
            if (this.fetchSize > 0) ps.setFetchSize(this.fetchSize)
        } catch (SQLException sqle) {
            throw new EntityException("SQL Exception preparing statement:" + sql, sqle)
        }
        return ps
    }

    protected void getResultSetValue(ResultSet rs, int index, Node fieldNode, EntityValueImpl entityValueImpl) throws EntityException {
        String javaType = getFieldJavaType(fieldNode."@type")

        try {
            int typeValue = getJavaTypeInt(javaType)
            ResultSetMetaData rsmd = rs.getMetaData()
            int colType = rsmd.getColumnType(index)

            if (typeValue <= 4 || typeValue >= 11) {
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
                                throw new EntityException("Error reading long character stream for field [${fieldNode."@name"}] of entity [${this.getEntity()}]", e)
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

                case 11:
                    Object obj = null
                    byte[] originalBytes = rs.getBytes(index)
                    InputStream binaryInput = null;
                    if (originalBytes != null && originalBytes.length > 0) {
                        binaryInput = new ByteArrayInputStream(originalBytes);
                    }
                    if (originalBytes != null && originalBytes.length <= 0) {
                        logger.warn("Got byte array back empty for serialized Object with length [${originalBytes.length}] while getting value [${fieldNode."@name"}] (${index})")
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
            } else {
                switch (typeValue) {
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
                }
            }
        } catch (SQLException sqle) {
            throw new EntityException("SQL Exception while getting value for field: [${fieldNode."@name"}] (${index})", sqle)
        }
    }

    void setPreparedStatementValue(PreparedStatement ps, int index, EntityConditionParameter entityConditionParam) throws EntityException {
        String javaType = getFieldJavaType(entityConditionParam.getFieldNode()."@type")
        if (entityConditionParam.getValue()) {
            if (!ObjectTypeFoo.instanceOf(entityConditionParam.getValue(), javaType)) {
                // this is only an info level message because under normal operation for most JDBC
                // drivers this will be okay, but if not then the JDBC driver will throw an exception
                // and when lower debug levels are on this should help give more info on what happened
                String fieldClassName = entityConditionParam.getValue().getClass().getName()
                if (entityConditionParam.getValue() instanceof byte[]) {
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

        boolean useBinaryTypeForBlob = ("true" == this.efi.getDatabaseNode(this.getEntity())."@use-binary-type-for-blob")


        try {
            int typeValue = getJavaTypeInt(javaType)
            switch (typeValue) {
            case 1:
                if (entityConditionParam.getValue() != null) {
                    ps.setString(index, (String) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.VARCHAR)
                }
                break

            case 2:
                if (entityConditionParam.getValue() != null) {
                    ps.setTimestamp(index, (java.sql.Timestamp) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.TIMESTAMP)
                }
                break

            case 3:
                if (entityConditionParam.getValue() != null) {
                    ps.setTime(index, (java.sql.Time) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.TIME)
                }
                break

            case 4:
                if (entityConditionParam.getValue() != null) {
                    ps.setDate(index, (java.sql.Date) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.DATE)
                }
                break

            case 5:
                if (entityConditionParam.getValue() != null) {
                    ps.setInt(index, (java.lang.Integer) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 6:
                if (entityConditionParam.getValue() != null) {
                    ps.setLong(index, (java.lang.Long) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 7:
                if (entityConditionParam.getValue() != null) {
                    ps.setFloat(index, (java.lang.Float) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 8:
                if (entityConditionParam.getValue() != null) {
                    ps.setDouble(index, (java.lang.Double) entityConditionParam.getValue());
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 9:
                if (entityConditionParam.getValue() != null) {
                    ps.setBigDecimal(index, (java.math.BigDecimal) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.NUMERIC)
                }
                break

            case 10:
                if (entityConditionParam.getValue() != null) {
                    ps.setBoolean(index, (java.lang.Boolean) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.BOOLEAN)
                }
                break

            case 11:
                if (entityConditionParam.getValue() != null) {
                    try {
                        ByteArrayOutputStream os = new ByteArrayOutputStream()
                        ObjectOutputStream oos = new ObjectOutputStream(os)
                        oos.writeObject(entityConditionParam.getValue())
                        oos.close()
                        byte[] buf = os.toByteArray()
                        os.close()
                        
                        ByteArrayInputStream is = new ByteArrayInputStream(buf)
                        ps.setBinaryStream(index, is, buf.length)
                        is.close()
                    } catch (IOException ex) {
                        throw new EntityException("Error setting serialized object for field [${entityConditionParam.getFieldNode()."@name"}]", ex)
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
                if (entityConditionParam.getValue() instanceof byte[]) {
                    ps.setBytes(index, (byte[]) entityConditionParam.getValue())
                } else if (entityConditionParam.getValue() instanceof java.nio.ByteBuffer) {
                    ps.setBytes(((java.nio.ByteBuffer) entityConditionParam.getValue()).array())
                } else {
                    if (entityConditionParam.getValue() != null) {
                        ps.setBlob(index, (java.sql.Blob) entityConditionParam.getValue())
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
                if (entityConditionParam.getValue() != null) {
                    ps.setClob(index, (java.sql.Clob) entityConditionParam.getValue())
                } else {
                    ps.setNull(index, Types.CLOB)
                }
                break

            case 14:
                if (entityConditionParam.getValue() != null) {
                    ps.setTimestamp(index, new java.sql.Timestamp(((java.util.Date) entityConditionParam.getValue()).getTime()))
                } else {
                    ps.setNull(index, Types.TIMESTAMP)
                }
                break

            case 15:
                // TODO: is this the best way to do collections and such?
                if (entityConditionParam.getValue() != null) {
                    ps.setObject(index, entityConditionParam.getValue(), Types.JAVA_OBJECT)
                } else {
                    ps.setNull(index, Types.JAVA_OBJECT)
                }
                break
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error while setting value on field [" + modelField.getName() + "] of entity " + entityName + ": " + e.toString(), e)
        } catch (SQLException sqle) {
            throw new EntityException("SQL Exception while setting value on field [" + modelField.getName() + "] of entity " + entityName + ": " + sqle.toString(), sqle)
        }
    }

    protected static final Map<String, String> fieldTypeMap = [
            "id":"String",
            "id-long":"String",
            "id-vlong":"String",
            "date":"Date",
            "time":"Time",
            "date-time":"Timestamp",
            "number-integer":"Long",
            "number-decimal":"BigDecimal",
            "number-float":"Double",
            "currency-amount":"BigDecimal",
            "currency-precise":"BigDecimal",
            "text-indicator":"String",
            "text-short":"String",
            "text-medium":"String",
            "text-long":"String",
            "text-very-long":"String",
            "binary-very-long":"Blob"]
    protected String getFieldJavaType(String fieldType) {
        Node databaseNode = this.efi.getDatabaseNode(this.getEntity())
        String javaType = databaseNode ? databaseNode."field-type-def".find({ it.@type == fieldType })[0]."@java-type" : null
        if (javaType) {
            return javaType
        } else {
            // get the default field java type
            String val = fieldTypeMap.get(fieldType)
            if (!val) throw new IllegalArgumentException("Field type " + fieldType + " not supported for entity fields")
            return val
        }
    }

    protected static final Map<String, Integer> javaTypeMap = [
            "java.lang.String":1, "String":1,
            "java.sql.Timestamp":2, "Timestamp":2,
            "java.sql.Time":3, "Time":3,
            "java.sql.Date":4, "Date":4,
            "java.lang.Integer":5, "Integer":5,
            "java.lang.Long":6,"Long":6,
            "java.lang.Float":7, "Float":7,
            "java.lang.Double":8, "Double":8,
            "java.math.BigDecimal":9, "BigDecimal":9,
            "java.lang.Boolean":10, "Boolean":10,
            "java.lang.Object":11, "Object":11,
            "java.sql.Blob":12, "Blob":12, "byte[]":12, "java.nio.ByteBuffer":12, "java.nio.HeapByteBuffer":12,
            "java.sql.Clob":13, "Clob":13,
            "java.util.Date":14,
            "java.util.ArrayList":15, "java.util.HashSet":15, "java.util.LinkedHashSet":15, "java.util.LinkedList":15]
    protected int getJavaTypeInt(String javaType) {
        Integer val = javaTypeMap.get(javaType)
        if (!val) throw new IllegalArgumentException("Java type " + javaType + " not supported for entity fields")
        return val
    }
}
