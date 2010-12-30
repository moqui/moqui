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

import com.atomikos.jdbc.AtomikosDataSourceBean

import java.sql.Connection
import javax.sql.DataSource
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
import com.atomikos.jdbc.nonxa.AtomikosNonXADataSourceBean
import com.atomikos.jdbc.AbstractDataSourceBean
import org.moqui.impl.StupidUtilities
import org.moqui.context.Cache
import groovy.util.slurpersupport.GPathResult

class EntityFacadeImpl implements EntityFacade {
    protected final static Logger logger = LoggerFactory.getLogger(EntityFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final EntityConditionFactoryImpl entityConditionFactory

    protected final Map<String, DataSource> dataSourceByGroupMap = new HashMap()

    /** Cache with entity name as the key and an EntityDefinition as the value; clear this cache to reload entity def */
    protected final Cache entityDefinitionCache
    /** Cache with entity name as the key and List of file location Strings as the value, Map<String, List<String>> */
    protected final Cache entityLocationCache

    EntityFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.entityConditionFactory = new EntityConditionFactoryImpl(this)

        // init connection pool (DataSource) for each group
        this.initAllDatasources()

        // init entity meta-data
        this.entityDefinitionCache = ecfi.getCacheFacade().getCache("entity.definition")
        this.entityLocationCache = ecfi.getCacheFacade().getCache("entity.location")
        this.loadAllEntityLocations()

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
                        this.dataSourceByGroupMap.put(datasource."@group-name", (DataSource) ds)
                    } else {
                        logger.error("Could not find XADataSource with name [${datasource."jndi-jdbc"[0]."@jndi-name"}] in JNDI server [${serverJndi ? serverJndi."@context-provider-url" : "default"}] for datasource with group-name [${datasource."@group-name"}].")
                    }
                } catch (NamingException ne) {
                    logger.error("Error finding XADataSource with name [${datasource."jndi-jdbc"[0]."@jndi-name"}] in JNDI server [${serverJndi ? serverJndi."@context-provider-url" : "default"}] for datasource with group-name [${datasource."@group-name"}].")
                }
            } else if (datasource."inline-jdbc") {
                Node inlineJdbc = datasource."inline-jdbc"[0]
                Node xaProperties = inlineJdbc."xa-properties"[0]
                Node database = this.getDatabaseNode(datasource."@group-name")

                AbstractDataSourceBean ads
                if (xaProperties) {
                    AtomikosDataSourceBean ds = new AtomikosDataSourceBean()
                    ds.setUniqueResourceName(datasource."@group-name")
                    String xsDsClass = inlineJdbc."@xa-ds-class" ? inlineJdbc."@xa-ds-class" : database."@default-xa-ds-class"
                    ds.setXaDataSourceClassName(xsDsClass)

                    Properties p = new Properties()
                    for (Map.Entry<String, String> entry in xaProperties.attributes().entrySet()) {
                        // TODO: the Derby "databaseName" property has a ${moqui.runtime} which is a System property, do we need to expand those here?
                        p.setProperty(entry.getKey(), entry.getValue())
                    }
                    ds.setXaProperties(p)

                    ads = ds
                } else {
                    AtomikosNonXADataSourceBean ds = new AtomikosNonXADataSourceBean()
                    ds.setUniqueResourceName(datasource."@group-name")
                    String driver = inlineJdbc."@jdbc-driver" ? inlineJdbc."@jdbc-driver" : database."@default-jdbc-driver"
                    ds.setDriverClassName(driver)
                    ds.setUrl(inlineJdbc."@jdbc-uri")
                    ds.setUser(inlineJdbc."@jdbc-username")
                    ds.setPassword(inlineJdbc."@jdbc-password")

                    ads = ds
                }

                String txIsolationLevel = inlineJdbc."@isolation-level" ? inlineJdbc."@isolation-level" : database."@default-isolation-level"
                if (txIsolationLevel && StupidUtilities.getTxIsolationFromString(txIsolationLevel) != -1) {
                    ads.setDefaultIsolationLevel(StupidUtilities.getTxIsolationFromString(txIsolationLevel))
                }

                // no need for this, just sets min and max sizes: ads.setPoolSize
                if (inlineJdbc."@pool-minsize") ads.setMinPoolSize(inlineJdbc."@pool-minsize" as int)
                if (inlineJdbc."@pool-maxsize") ads.setMaxPoolSize(inlineJdbc."@pool-maxsize" as int)

                if (inlineJdbc."@pool-time-idle") ads.setMaxIdleTime(inlineJdbc."@pool-time-idle" as int)
                if (inlineJdbc."@pool-time-reap") ads.setReapTimeout(inlineJdbc."@pool-time-reap" as int)
                if (inlineJdbc."@pool-time-maint") ads.setMaintenanceInterval(inlineJdbc."@pool-time-maint" as int)
                if (inlineJdbc."@pool-time-wait") ads.setBorrowConnectionTimeout(inlineJdbc."@pool-time-wait" as int)

                if (inlineJdbc."@pool-test-query") ads.setTestQuery(inlineJdbc."@pool-test-query")

                this.dataSourceByGroupMap.put(datasource."@group-name", ads)
            } else {
                throw new IllegalArgumentException("Found datasource with no jdbc sub-element (in datasource with group-name [${datasource."@group-name"}])")
            }
        }
    }

    protected void loadAllEntityLocations() {
        // loop through all of the entity-facade.load-entity nodes, check each for "<entities>" root element
        for (Node loadEntity in this.ecfi.getConfXmlRoot()."entity-facade"[0]."load-entity") {
            this.loadEntityFileLocations(loadEntity."@location")
        }

        // loop through components look for XML files in the entity directory, check each for "<entities>" root element
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            URL entityDirUrl = this.ecfi.resourceFacade.getLocationUrl(location + "/entity")
            if ("file" == entityDirUrl.getProtocol()) {
                File entityDir = new File(entityDirUrl.toURI())
                // if directory doesn't exist skip it, component doesn't have an entity directory
                if (!entityDir.exists() || !entityDir.isDirectory()) continue
                // get all files in the directory
                for (File entityFile in entityDir.listFiles()) {
                    if (!entityFile.isFile() || !entityFile.getName().endsWith(".xml")) continue
                    this.loadEntityFileLocations(entityFile.toURI().toString())
                }
            } else {
                throw new IllegalArgumentException("Cannot load entity file in location [${location}] because protocol [${entityDirUrl.getProtocol()}] is not yet supported.")
            }
        }
    }

    protected void loadEntityFileLocations(String location) {
        // NOTE: using XmlSlurper here instead of XmlParser because it should be faster since it won't parse through the whole file right away
        InputStream entityStream = this.ecfi.resourceFacade.getLocationStream(location)
        GPathResult entityRoot = new XmlSlurper().parse(entityStream)
        entityStream.close()
        if (entityRoot.name() == "entities") {
            // loop through all entity, view-entity, and extend-entity and add file location to List for any entity named
            int numEntities = 0
            for (GPathResult entity in entityRoot.children()) {
                List theList = (List) this.entityLocationCache.get((String) entity."@entity-name")
                if (!theList) {
                    theList = new ArrayList()
                    this.entityLocationCache.put((String) entity."@entity-name", theList)
                }
                theList.add(location)
                numEntities++
            }
            logger.info("Found [${numEntities}] entity definitions in [${location}]")
        }
    }

    protected synchronized EntityDefinition loadEntityDefinition(String entityName) {
        EntityDefinition ed = (EntityDefinition) this.entityDefinitionCache.get(entityName)
        if (ed) return ed

        List<String> entityLocationList = (List<String>) this.entityLocationCache.get(entityName)
        if (!entityLocationList) {
            this.loadAllEntityLocations()
            entityLocationList = (List<String>) this.entityLocationCache.get(entityName)
            // no locations found for this entity, entity probably doesn't exist
            if (!entityLocationList) {
                throw new IllegalArgumentException("No definition found for entity-name [${entityName}]")
            }
        }

        // get entity, view-entity and extend-entity Nodes for entity from each location
        Node entityNode = null
        List<Node> extendEntityNodes = new ArrayList<Node>()
        for (String location in entityLocationList) {
            InputStream entityStream = this.ecfi.resourceFacade.getLocationStream(location)
            Node entityRoot = new XmlParser().parse(entityStream)
            entityStream.close()
            for (Node childNode in entityRoot.children().findAll({ it."@entity-name" == entityName })) {
                if (childNode.name() == "extend-entity") {
                    extendEntityNodes.add(childNode)
                } else {
                    if (entityNode != null) logger.warn("Entity [${entityName}] was found again, so overriding")
                    entityNode = childNode
                }
            }
        }

        if (!entityNode) throw new IllegalArgumentException("No definition found for entity-name [${entityName}]")

        // merge the extend-entity nodes
        for (Node extendEntity in extendEntityNodes) {
            // merge attributes
            entityNode.attributes().putAll(extendEntity.attributes())
            // merge field nodes
            for (Node childOverrideNode in extendEntity."field") {
                String keyValue = childOverrideNode."@name"
                Node childBaseNode = (Node) entityNode."field".find({ it."@name" == keyValue })
                if (childBaseNode) childBaseNode.attributes().putAll(childOverrideNode.attributes())
                else entityNode.append(childOverrideNode)
            }
            // add relationship, key-map (copy over, will get child nodes too
            for (Node copyNode in extendEntity."relationship") entityNode.append(copyNode)
            // add index, index-field
            for (Node copyNode in extendEntity."index") entityNode.append(copyNode)
        }

        // create the new EntityDefinition
        ed = new EntityDefinition(this, entityNode)

        return ed
    }

    void destroy() {
        Set<String> groupNames = this.dataSourceByGroupMap.keySet()
        for (String groupName in groupNames) {
            DataSource ds = this.dataSourceByGroupMap.get(groupName)
            // destroy anything related to the internal impl, ie Atomikos
            if (ds instanceof AtomikosDataSourceBean) {
                this.dataSourceByGroupMap.put(groupName, null)
                ((AtomikosDataSourceBean) ds).close()
            }
        }
    }

    EntityDefinition getEntityDefinition(String entityName) {
        EntityDefinition ed = (EntityDefinition) this.entityDefinitionCache.get(entityName)
        if (ed) return ed
        return loadEntityDefinition(entityName)
    }

    Node getDatabaseNode(String groupName) {
        Node confXmlRoot = this.ecfi.getConfXmlRoot()
        String databaseConfName = getDataBaseConfName(groupName)
        return (Node) confXmlRoot."database-list".database.find({ it.@name == databaseConfName })
    }
    String getDataBaseConfName(String groupName) {
        //String groupName = this.getEntityGroupName(entityName)
        Node confXmlRoot = this.ecfi.getConfXmlRoot()
        Node datasourceNode = (Node) confXmlRoot."entity-facade".datasource.find({ it."@group-name" == groupName })
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
        DataSource ds = this.dataSourceByGroupMap.get(groupName)
        if (!ds) throw new IllegalArgumentException("DataSource not initialized for group-name [${groupName}]")
        if (ds instanceof XADataSource) {
            return this.ecfi.transactionFacade.enlistConnection(((XADataSource) ds).getXAConnection())
        } else {
            return ds.getConnection()
        }
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
        Node databaseNode = this.getDatabaseNode(this.getEntityGroupName(entityName))
        String javaType = databaseNode ? databaseNode."field-type-def".find({ it.@type == fieldType })."@java-type" : null
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
