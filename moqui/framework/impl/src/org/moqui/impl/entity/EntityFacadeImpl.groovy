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

import java.sql.Connection
import javax.sql.XADataSource
import javax.naming.InitialContext
import javax.naming.Context
import javax.naming.NamingException

import org.moqui.entity.EntityFacade
import org.moqui.entity.EntityConditionFactory
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityList
import org.moqui.impl.context.ExecutionContextFactoryImpl

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import org.w3c.dom.Document
import org.w3c.dom.Element

class EntityFacadeImpl implements EntityFacade {
    protected final static Logger logger = LoggerFactory.getLogger(EntityFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final EntityConditionFactoryImpl entityConditionFactory

    protected final Map<String, XADataSource> dataSourceByGroupMap

    EntityFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.entityConditionFactory = new EntityConditionFactoryImpl(this)

        // init connection pool (DataSource) for each group
        this.initAllDatasources()
        // TODO: init entity definitions
        // TODO: EECA rule tables
    }

    protected void initAllDatasources() {
        for(Node datasource in this.ecfi.getConfXmlRoot()."entity-facade"[0]."datasource") {
            if (datasource."jndi-jdbc") {
                Node serverJndi = this.ecfi.getConfXmlRoot()."entity-facade"[0]."server-jndi"[0]
                try {
                    InitialContext ic;
                    if (serverJndi) {
                        Hashtable<String, Object> h = new Hashtable<String, Object>()
                        h.put(Context.INITIAL_CONTEXT_FACTORY, serverJndi."@initial-context-factory")
                        h.put(Context.PROVIDER_URL, serverJndi."@context-provider-url")
                        if (serverJndi."@url-pkg-prefixes") h.put(Context.URL_PKG_PREFIXES, serverJndi."@url-pkg-prefixes")
                        if (serverJndi."@security-principal") h.put(Context.SECURITY_PRINCIPAL, serverJndi."@security-principal")
                        if (serverJndi."@security-credentials") h.put(Context.SECURITY_CREDENTIALS, serverJndi."@security-credentials")
                        ic = new InitialContext(h)
                    } else {
                        ic = new InitialContext()
                    }

                    XADataSource ds = (XADataSource) ic.lookup((String) datasource."jndi-jdbc"[0]."@jndi-name")
                    if (ds) {
                        this.dataSourceByGroupMap.put(datasource."@group-name", ds)
                    } else {
                        logger.error("Could not find XADataSource with name [${datasource."jndi-jdbc"[0]."@jndi-name"}] in JNDI server [${serverJndi ? serverJndi."@context-provider-url" : "default"}] for datasource with group-name [${datasource."@group-name"}].")
                    }

                } catch (NamingException ne) {
                    logger.error("Error finding XADataSource with name [${datasource."jndi-jdbc"[0]."@jndi-name"}] in JNDI server [${serverJndi ? serverJndi."@context-provider-url" : "default"}] for datasource with group-name [${datasource."@group-name"}].")
                }
            } else if (datasource."inline-jdbc") {
                throw new IllegalArgumentException("The inline-jdbc datasource is not yet supported (found in datasource with group-name [${datasource."@group-name"}])")
            } else {
                throw new IllegalArgumentException("Found datasource with no jdbc sub-element (in datasource with group-name [${datasource."@group-name"}])")
            }
        }
    }

    void destroy() {
        for(Node datasource in this.ecfi.getConfXmlRoot()."entity-facade"[0]."datasource") {
            //nothing to do for jndi-jdbc: if (datasource."jndi-jdbc") {
            if (datasource."inline-jdbc") {
                // TODO: destroy anything related to the internal impl
            }
        }
    }

    EntityDefinition getEntityDefinition(String entityName) {
        // TODO: implement this
        return null
    }

    Node getDatabaseNode(String entityName) {
        def confXmlRoot = this.ecfi.getConfXmlRoot()
        def databaseConfName = getDataBaseConfName(entityName)
        return (Node) confXmlRoot."database-list".database.find({ it.@name == databaseConfName })[0]
    }
    String getDataBaseConfName(String entityName) {
        String groupName = this.getEntityGroupName(entityName)
        def confXmlRoot = this.ecfi.getConfXmlRoot()
        def datasourceNode = confXmlRoot."entity-facade".datasource.find({ it."@group-name" == groupName })
        return datasourceNode."@database-conf-name"
    }

    // ========== Interface Implementations ==========

    /** @see org.moqui.entity.EntityFacade#getConditionFactory() */
    EntityConditionFactory getConditionFactory() {
        return this.entityConditionFactory
    }

    /** @see org.moqui.entity.EntityFacade#makeValue(Element) */
    EntityValue makeValue(String entityName) {
        EntityDefinition entityDefinition = this.getEntityDefinition(entityName)
        if (!entityDefinition) {
            throw new IllegalArgumentException("Entity not found for name [${entityName}]")
        }
        return new EntityValueImpl(entityDefinition, this)
    }

    /** @see org.moqui.entity.EntityFacade#updateByCondition(String, Map, EntityCondition) */
    int updateByCondition(String entityName, Map<String, ?> fieldsToSet, EntityCondition condition) {
        // TODO: implement this
        return 0
    }

    /** @see org.moqui.entity.EntityFacade#deleteByCondition(String, EntityCondition) */
    int deleteByCondition(String entityName, EntityCondition condition) {
        // TODO: implement this
        return 0
    }

    /** @see org.moqui.entity.EntityFacade#find(String) */
    EntityFind find(String entityName) {
        return new EntityFindImpl(this, entityName)
    }

    /** @see org.moqui.entity.EntityFacade#sequencedIdPrimary(String, long) */
    String sequencedIdPrimary(String seqName, Long staggerMax) {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFacade#sequencedIdSecondary(EntityValue, String, int, int) */
    void sequencedIdSecondary(EntityValue value, String seqFieldName, Integer numericPadding, Integer incrementBy) {
        // TODO: implement this
    }

    /** @see org.moqui.entity.EntityFacade#getEntityGroupName(String) */
    String getEntityGroupName(String entityName) {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFacade#getConnection(String) */
    Connection getConnection(String groupName) {
        XADataSource xads = this.dataSourceByGroupMap.get(groupName)
        if (!xads) throw new IllegalArgumentException("DataSource not initialized for group-name [${groupName}]")
        return this.ecfi.transactionFacade.enlistConnection(xads.getXAConnection())
    }

    /** @see org.moqui.entity.EntityFacade#readXmlDocument(URL) */
    EntityList readXmlDocument(URL url) {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFacade#readXmlDocument(Document) */
    EntityList readXmlDocument(Document document) {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFacade#makeValue(Element) */
    EntityValue makeValue(Element element) {
        // TODO: implement this
        return null
    }

    protected static final Map<String, String> fieldTypeMap = [
            "id":"java.lang.String",
            "id-long":"java.lang.String",
            "id-vlong":"java.lang.String",
            "date":"java.sql.Date",
            "time":"java.sql.Time",
            "date-time":"java.sql.Timestamp",
            "number-integer":"java.lang.Long",
            "number-decimal":"java.math.BigDecimal",
            "number-float":"java.lang.Double",
            "currency-amount":"java.math.BigDecimal",
            "currency-precise":"java.math.BigDecimal",
            "text-indicator":"java.lang.String",
            "text-short":"java.lang.String",
            "text-medium":"java.lang.String",
            "text-long":"java.lang.String",
            "text-very-long":"java.lang.String",
            "binary-very-long":"java.sql.Blob"]
    protected String getFieldJavaType(String fieldType, String entityName) {
        Node databaseNode = this.getDatabaseNode(entityName)
        String javaType = databaseNode ? databaseNode."field-type-def".find({ it.@type == fieldType })[0]."@java-type" : null
        if (javaType) {
            return javaType
        } else {
            // get the default field java type
            String defaultJavaType = fieldTypeMap[fieldType]
            if (!defaultJavaType) throw new IllegalArgumentException("Field type " + fieldType + " not supported for entity fields")
            return defaultJavaType
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
    public static int getJavaTypeInt(String javaType) {
        Integer typeInt = javaTypeMap[javaType]
        if (!typeInt) throw new IllegalArgumentException("Java type " + javaType + " not supported for entity fields")
        return typeInt
    }
}
