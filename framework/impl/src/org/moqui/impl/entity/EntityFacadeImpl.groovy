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
import com.atomikos.jdbc.nonxa.AtomikosNonXADataSourceBean
import com.atomikos.jdbc.AbstractDataSourceBean

import groovy.util.slurpersupport.GPathResult

import java.sql.Connection
import javax.sql.DataSource
import javax.sql.XADataSource
import javax.naming.InitialContext
import javax.naming.Context
import javax.naming.NamingException

import org.moqui.context.Cache
import org.moqui.context.ResourceReference
import org.moqui.context.TransactionException
import org.moqui.context.TransactionFacade

import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityConditionFactory
import org.moqui.entity.EntityDataLoader
import org.moqui.entity.EntityException
import org.moqui.entity.EntityFacade
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityList

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.StupidUtilities

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import org.w3c.dom.Element
import org.apache.commons.collections.set.ListOrderedSet
import org.moqui.entity.EntityDataWriter

class EntityFacadeImpl implements EntityFacade {
    protected final static Logger logger = LoggerFactory.getLogger(EntityFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi
    protected final String tenantId

    protected final EntityConditionFactoryImpl entityConditionFactory

    protected final Map<String, DataSource> dataSourceByGroupMap = new HashMap()

    /** Cache with entity name as the key and an EntityDefinition as the value; clear this cache to reload entity def */
    protected final Cache entityDefinitionCache
    /** Cache with entity name as the key and List of file location Strings as the value, Map<String, List<String>> */
    protected final Cache entityLocationCache

    protected final Map<String, List<EntityEcaRule>> eecaRulesByEntityName = new HashMap()

    protected EntityDbMeta dbMeta = null

    EntityFacadeImpl(ExecutionContextFactoryImpl ecfi, String tenantId) {
        this.ecfi = ecfi
        this.tenantId = tenantId ?: "DEFAULT"
        this.entityConditionFactory = new EntityConditionFactoryImpl(this)

        // init connection pool (DataSource) for each group
        this.initAllDatasources()

        // init entity meta-data
        this.entityDefinitionCache = ecfi.getCacheFacade().getCache("entity.definition")
        this.entityLocationCache = ecfi.getCacheFacade().getCache("entity.location")
        this.loadAllEntityLocations()

        // EECA rule tables
        loadEecaRulesAll()
    }

    protected void initAllDatasources() {
        EntityValue tenant = null
        EntityFacadeImpl defaultEfi = null
        if (this.tenantId != "DEFAULT") {
            defaultEfi = ecfi.getEntityFacade("DEFAULT")
            tenant = defaultEfi.makeFind("Tenant").condition("tenantId", this.tenantId).one()
        }

        for(Node datasource in this.ecfi.getConfXmlRoot()."entity-facade"[0]."datasource") {
            EntityValue tenantDataSource = null
            EntityList tenantDataSourceXaPropList = null
            if (tenant != null) {
                tenantDataSource = defaultEfi.makeFind("TenantDataSource").condition("tenantId", this.tenantId)
                        .condition("entityGroupName", datasource."@group-name").one()
                tenantDataSourceXaPropList = defaultEfi.makeFind("TenantDataSourceXaProp")
                        .condition("tenantId", this.tenantId).condition("entityGroupName", datasource."@group-name")
                        .list()
            }

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

                    String jndiName = tenantDataSource ? tenantDataSource.jndiName : datasource."jndi-jdbc"[0]."@jndi-name"
                    XADataSource ds = (XADataSource) ic.lookup(jndiName)
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

                // special thing for embedded derby, just set an system property; for derby.log, etc
                if (datasource."@database-conf-name" == "derby") {
                    System.setProperty("derby.system.home", System.getProperty("moqui.runtime") + "/db/derby")
                    logger.info("Set property derby.system.home to [${System.getProperty("derby.system.home")}]")
                }

                AbstractDataSourceBean ads
                if (xaProperties) {
                    AtomikosDataSourceBean ds = new AtomikosDataSourceBean()
                    ds.setUniqueResourceName(this.tenantId + datasource."@group-name")
                    String xsDsClass = inlineJdbc."@xa-ds-class" ? inlineJdbc."@xa-ds-class" : database."@default-xa-ds-class"
                    ds.setXaDataSourceClassName(xsDsClass)

                    Properties p = new Properties()
                    if (tenantDataSourceXaPropList) {
                        for (EntityValue tenantDataSourceXaProp in tenantDataSourceXaPropList) {
                            String propValue = tenantDataSourceXaProp.propValue
                            // NOTE: consider changing this to expand for all system properties using groovy or something
                            if (propValue.contains("\${moqui.runtime}")) propValue = propValue.replace("\${moqui.runtime}", System.getProperty("moqui.runtime"))
                            p.setProperty((String) tenantDataSourceXaProp.propName, propValue)
                        }
                    } else {
                        for (Map.Entry<String, String> entry in xaProperties.attributes().entrySet()) {
                            // the Derby "databaseName" property has a ${moqui.runtime} which is a System property, others may have it too
                            String propValue = entry.getValue()
                            // NOTE: consider changing this to expand for all system properties using groovy or something
                            if (propValue.contains("\${moqui.runtime}")) propValue = propValue.replace("\${moqui.runtime}", System.getProperty("moqui.runtime"))
                            p.setProperty(entry.getKey(), propValue)
                        }
                    }
                    ds.setXaProperties(p)

                    ads = ds
                } else {
                    AtomikosNonXADataSourceBean ds = new AtomikosNonXADataSourceBean()
                    ds.setUniqueResourceName(datasource."@group-name")
                    String driver = inlineJdbc."@jdbc-driver" ? inlineJdbc."@jdbc-driver" : database."@default-jdbc-driver"
                    ds.setDriverClassName(driver)
                    ds.setUrl(tenantDataSource ? tenantDataSource.jdbcUri : inlineJdbc."@jdbc-uri")
                    ds.setUser(tenantDataSource ? tenantDataSource.jdbcUsername : inlineJdbc."@jdbc-username")
                    ds.setPassword(tenantDataSource ? tenantDataSource.jdbcPassword : inlineJdbc."@jdbc-password")

                    ads = ds
                }

                String txIsolationLevel = inlineJdbc."@isolation-level" ? inlineJdbc."@isolation-level" : database."@default-isolation-level"
                if (txIsolationLevel && getTxIsolationFromString(txIsolationLevel) != -1) {
                    ads.setDefaultIsolationLevel(getTxIsolationFromString(txIsolationLevel))
                }

                // no need for this, just sets min and max sizes: ads.setPoolSize
                if (inlineJdbc."@pool-minsize") ads.setMinPoolSize(inlineJdbc."@pool-minsize" as int)
                if (inlineJdbc."@pool-maxsize") ads.setMaxPoolSize(inlineJdbc."@pool-maxsize" as int)

                if (inlineJdbc."@pool-time-idle") ads.setMaxIdleTime(inlineJdbc."@pool-time-idle" as int)
                if (inlineJdbc."@pool-time-reap") ads.setReapTimeout(inlineJdbc."@pool-time-reap" as int)
                if (inlineJdbc."@pool-time-maint") ads.setMaintenanceInterval(inlineJdbc."@pool-time-maint" as int)
                if (inlineJdbc."@pool-time-wait") ads.setBorrowConnectionTimeout(inlineJdbc."@pool-time-wait" as int)

                if (inlineJdbc."@pool-test-query") {
                    ads.setTestQuery(inlineJdbc."@pool-test-query")
                } else if (database."@default-test-query") {
                    ads.setTestQuery(database."@default-test-query")
                }

                this.dataSourceByGroupMap.put(datasource."@group-name", ads)
            } else {
                throw new EntityException("Found datasource with no jdbc sub-element (in datasource with group-name [${datasource."@group-name"}])")
            }
        }
    }

    static int getTxIsolationFromString(String isolationLevel) {
        if (!isolationLevel) return -1
        if ("Serializable".equals(isolationLevel)) {
            return Connection.TRANSACTION_SERIALIZABLE
        } else if ("RepeatableRead".equals(isolationLevel)) {
            return Connection.TRANSACTION_REPEATABLE_READ
        } else if ("ReadUncommitted".equals(isolationLevel)) {
            return Connection.TRANSACTION_READ_UNCOMMITTED
        } else if ("ReadCommitted".equals(isolationLevel)) {
            return Connection.TRANSACTION_READ_COMMITTED
        } else if ("None".equals(isolationLevel)) {
            return Connection.TRANSACTION_NONE
        } else {
            return -1
        }
    }

    protected synchronized void loadAllEntityLocations() {
        // loop through all of the entity-facade.load-entity nodes, check each for "<entities>" root element
        for (Node loadEntity in this.ecfi.getConfXmlRoot()."entity-facade"[0]."load-entity") {
            this.loadEntityFileLocations(this.ecfi.resourceFacade.getLocationReference(loadEntity."@location"))
        }

        // loop through components look for XML files in the entity directory, check each for "<entities>" root element
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            ResourceReference entityDirRr = this.ecfi.resourceFacade.getLocationReference(location + "/entity")
            if (entityDirRr.supportsAll()) {
                // if directory doesn't exist skip it, component doesn't have an entity directory
                if (!entityDirRr.exists || !entityDirRr.isDirectory()) continue
                // get all files in the directory
                for (ResourceReference entityRr in entityDirRr.directoryEntries) {
                    if (!entityRr.isFile() || !entityRr.location.endsWith(".xml")) continue
                    this.loadEntityFileLocations(entityRr)
                }
            } else {
                // just warn here, no exception because any non-file component location would blow everything up
                logger.warn("Cannot load entity directory in component location [${location}] because protocol [${entityDirRr.uri.scheme}] is not supported.")
            }
        }
    }

    protected synchronized void loadEntityFileLocations(ResourceReference entityRr) {
        // NOTE: using XmlSlurper here instead of XmlParser because it should be faster since it won't parse through the whole file right away
        InputStream entityStream = entityRr.openStream()
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
                if (!theList.contains(entityRr.location)) theList.add(entityRr.location)
                numEntities++
            }
            logger.info("Found [${numEntities}] entity definitions in [${entityRr.location}]")
        }
    }

    protected EntityDefinition loadEntityDefinition(String entityName) {
        EntityDefinition ed = (EntityDefinition) entityDefinitionCache.get(entityName)
        if (ed) return ed

        List<String> entityLocationList = (List<String>) entityLocationCache.get(entityName)
        if (!entityLocationList) {
            if (logger.warnEnabled) logger.warn("No location cache found for entity-name [${entityName}], reloading ALL entity file locations known.")
            this.loadAllEntityLocations()
            entityLocationList = (List<String>) entityLocationCache.get(entityName)
            // no locations found for this entity, entity probably doesn't exist
            if (!entityLocationList) {
                throw new EntityException("No definition found for entity-name [${entityName}]")
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

        if (!entityNode) throw new EntityException("No definition found for entity-name [${entityName}]")

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
        // cache it
        entityDefinitionCache.put(entityName, ed)
        // send it on its way
        return ed
    }

    /** This method is called only when the tools need all automatic reverse-many relationships.
     *
     * During normal operation reverse-many relationships are only needed when explicitly referenced, and these are
     * handled in the EntityDefinition.getRelationshipNode() method one at a time (only those used are loaded).
     */
    synchronized void createAllAutoReverseManyRelationships() {
        int relationshipsCreated = 0
        Set<String> entityNameSet = getAllEntityNames()
        for (String entityName in entityNameSet) {
            EntityDefinition ed = getEntityDefinition(entityName)
            ListOrderedSet pkSet = ed.getFieldNames(true, false)
            for (Node relNode in ed.entityNode."relationship") {
                if (relNode."@type" == "many") continue

                EntityDefinition reverseEd
                try {
                    reverseEd = getEntityDefinition(relNode."@related-entity-name")
                } catch (EntityException e) {
                    logger.warn("Error getting definition for entity [${relNode."@related-entity-name"}] referred to in a relationship of entity [${entityName}]: ${e.toString()}")
                    continue
                }
                ListOrderedSet reversePkSet = reverseEd.getFieldNames(true, false)
                String relType = reversePkSet.equals(pkSet) ? "one-nofk" : "many"

                // does a many relationship coming back already exist?
                Node reverseRelNode = (Node) reverseEd.entityNode."relationship".find(
                        { it."@related-entity-name" == ed.entityName && it."@type" == relType })
                if (reverseRelNode != null) {
                    // make sure has is-auto-reverse="true"
                    reverseRelNode.attributes().put("is-one-reverse", "true")
                    continue
                }

                // track the fact that the related entity has others pointing back to it
                if (!ed.isViewEntity()) reverseEd.entityNode.attributes().put("has-dependents", "true")

                // create a new reverse-many relationship
                Map keyMap = ed.getRelationshipExpandedKeyMap(relNode)

                Node newRelNode = reverseEd.entityNode.appendNode("relationship",
                        ["related-entity-name":ed.entityName, "type":relType, "is-one-reverse":"true"])
                for (Map.Entry keyEntry in keyMap) {
                    // add a key-map with the reverse fields
                    newRelNode.appendNode("key-map", ["field-name":keyEntry.value, "related-field-name":keyEntry.key])
                }
                relationshipsCreated++
            }
        }

        if (logger.infoEnabled && relationshipsCreated > 0) logger.info("Created ${relationshipsCreated} automatic reverse-many relationships")
    }

    void loadEecaRulesAll() {
        if (eecaRulesByEntityName.size() > 0) eecaRulesByEntityName.clear()

        // search for the service def XML file in the components
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            ResourceReference entityDirRr = this.ecfi.resourceFacade.getLocationReference(location + "/entity")
            if (entityDirRr.supportsAll()) {
                // if for some weird reason this isn't a directory, skip it
                if (!entityDirRr.isDirectory()) continue
                for (ResourceReference rr in entityDirRr.directoryEntries) {
                    if (!rr.fileName.endsWith(".eecas.xml")) continue
                    loadEecaRulesFile(rr)
                }
            } else {
                logger.warn("Can't load EECA rules from component at [${entityDirRr.location}] because it doesn't support exists/directory/etc")
            }
        }
    }
    void loadEecaRulesFile(ResourceReference rr) {
        InputStream is = null
        try {
            is = rr.openStream()
            Node serviceRoot = new XmlParser().parse(is)
            int numLoaded = 0
            for (Node secaNode in serviceRoot."eeca") {
                EntityEcaRule ser = new EntityEcaRule(ecfi, secaNode, rr.location)
                String entityName = ser.entityName
                // remove the hash if there is one to more consistently match the service name
                if (entityName.contains("#")) entityName = entityName.replace("#", "")
                List<EntityEcaRule> lst = eecaRulesByEntityName.get(entityName)
                if (!lst) {
                    lst = new LinkedList()
                    eecaRulesByEntityName.put(entityName, lst)
                }
                lst.add(ser)
                numLoaded++
            }
            if (logger.infoEnabled) logger.info("Loaded [${numLoaded}] Entity ECA rules from [${rr.location}]")
        } catch (IOException e) {
            // probably because there is no resource at that location, so do nothing
            if (logger.traceEnabled) logger.trace("Error loading EECA rules from [${rr.location}]", e)
        } finally {
            if (is != null) is.close()
        }
    }

    void runEecaRules(String entityName, Map fieldValues, String operation, boolean before) {
        List<EntityEcaRule> lst = eecaRulesByEntityName.get(entityName)
        for (EntityEcaRule eer in lst) {
            eer.runIfMatches(entityName, fieldValues, operation, before, ecfi.executionContext)
        }
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

    /** This uses the data from the loadAllEntityLocations() method, so that must be called first (it is called in the
     * constructor, and the cache must not have been cleared since. */
    Set<String> getAllEntityNames() {
        return new TreeSet(entityLocationCache.keySet())
    }

    EntityDefinition getEntityDefinition(String entityName) {
        EntityDefinition ed = (EntityDefinition) this.entityDefinitionCache.get(entityName)
        if (ed) return ed
        return loadEntityDefinition(entityName)
    }

    List<Map<String, Object>> getAllEntitiesInfo(String orderByField, boolean masterEntitiesOnly) {
        if (masterEntitiesOnly) createAllAutoReverseManyRelationships()

        List<Map<String, Object>> eil = new LinkedList()
        for (String en in getAllEntityNames()) {
            EntityDefinition ed = null
            try { ed = getEntityDefinition(en) } catch (EntityException e) { logger.warn("Problem finding entity definition", e) }
            if (ed == null) continue

            if (masterEntitiesOnly) {
                if (!(ed.entityNode."@has-dependents" == "true") || en.endsWith("Type") ||
                        en == "Enumeration" || en == "StatusItem") continue
                ListOrderedSet pks = ed.getFieldNames(true, false)
                if (pks.size() > 1) continue
            }

            eil.add((Map<String, Object>) [entityName:ed.entityName, "package":ed.entityNode."@package-name",
                    isView:(ed.isViewEntity() ? "true" : "false")])
        }

        if (orderByField) StupidUtilities.orderMapList((List<Map>) eil, [orderByField])
        return eil
    }

    Cache getCacheOne(String entityName) { return ecfi.getCacheFacade().getCache("entity.${tenantId}.one.${entityName}") }
    Cache getCacheList(String entityName) { return ecfi.getCacheFacade().getCache("entity.${tenantId}.list.${entityName}") }
    Cache getCacheListRa(String entityName) { return ecfi.getCacheFacade().getCache("entity.${tenantId}.list_ra.${entityName}") }
    Cache getCacheCount(String entityName) { return ecfi.getCacheFacade().getCache("entity.${tenantId}.count.${entityName}") }

    void clearCacheForValue(EntityValueImpl evi) {
        if (evi.getEntityDefinition().getEntityNode()."@use-cache" == "never") return
        String entityName = evi.getEntityName()
        EntityCondition pkCondition = conditionFactory.makeCondition(evi.getPrimaryKeys())

        // clear one cache
        if (ecfi.cacheFacade.cacheExists("entity.${tenantId}.one.${entityName}")) {
            Cache entityOneCache = getCacheOne(entityName)
            if (entityOneCache.containsKey(pkCondition)) entityOneCache.remove(pkCondition)
        }

        // clear list cache, use reverse-associative Map (also a Cache)
        if (ecfi.cacheFacade.cacheExists("entity.${tenantId}.list.${entityName}")) {
            Cache listRaCache = getCacheListRa(entityName)
            if (listRaCache.containsKey(pkCondition)) {
                List raKeyList = (List) listRaCache.get(pkCondition)
                Cache entityListCache = getCacheList(entityName)
                for (EntityCondition ec in raKeyList) {
                    // check it one last time before removing, may have been cleared by something else
                    if (entityListCache.containsKey(ec)) entityListCache.remove(ec)
                }
                // we've cleared all entries that this was referring to, so clean it out too
                listRaCache.remove(pkCondition)
            }
        }

        // clear count cache (no RA because we only have a count to work with, just match by condition)
        if (ecfi.cacheFacade.cacheExists("entity.${tenantId}.count.${entityName}")) {
            Cache entityCountCache = getCacheCount(entityName)
            for (EntityCondition ec in entityCountCache.keySet()) {
                if (ec.mapMatches(evi)) entityCountCache.remove(ec)
            }
        }
    }
    void registerCacheListRa(String entityName, EntityCondition ec, EntityList el) {
        Cache listRaCache = getCacheListRa(entityName)
        for (EntityValue ev in el) {
            EntityCondition pkCondition = conditionFactory.makeCondition(ev.getPrimaryKeys())
            List raKeyList = (List) listRaCache.get(pkCondition)
            if (!raKeyList) {
                raKeyList = new ArrayList()
                listRaCache.put(pkCondition, raKeyList)
            }
            raKeyList.add(ec)
        }
    }

    Node getDatabaseNode(String groupName) {
        String databaseConfName = getDatabaseConfName(groupName)
        return (Node) ecfi.confXmlRoot."database-list"[0].database.find({ it."@name" == databaseConfName })
    }
    String getDatabaseConfName(String groupName) {
        Node datasourceNode = (Node) ecfi.confXmlRoot."entity-facade"[0].datasource.find({ it."@group-name" == groupName })
        return datasourceNode."@database-conf-name"
    }

    Node getDatasourceNode(String groupName) {
        return (Node) ecfi.confXmlRoot."entity-facade"[0].datasource.find({ it."@group-name" == groupName })
    }

    EntityDbMeta getEntityDbMeta() {
        return dbMeta ? dbMeta : (dbMeta = new EntityDbMeta(this))
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
            throw new EntityException("Entity not found for name [${entityName}]")
        }
        return new EntityValueImpl(entityDefinition, this)
    }

    /** @see org.moqui.entity.EntityFacade#makeFind(String) */
    EntityFind makeFind(String entityName) { return new EntityFindImpl(this, entityName) }

    /** @see org.moqui.entity.EntityFacade#sequencedIdPrimary(String, long) */
    synchronized String sequencedIdPrimary(String seqName, Long staggerMax) {
        // TODO: find some way to get this running non-synchronized for performance reasons (right now if not
        // TODO:     synchronized the forUpdate won't help if the record doesn't exist yet, causing errors in high
        // TODO:     traffic creates; is it creates only?)
        // NOTE: simple approach with forUpdate, not using the update/select "ethernet" approach used in OFBiz; consider
        // that in the future if there are issues with this approach
        // TODO: add support for bins of IDs for performance, ie to avoid going to the db for each one
        Long seqNum
        TransactionFacade tf = this.ecfi.getTransactionFacade()
        boolean suspendedTransaction = false
        try {
            if (tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = tf.begin(null)
            try {
                EntityValue svi = makeFind("SequenceValueItem").condition("seqName", seqName)
                        .useCache(false).forUpdate(true).one()
                if (svi == null) {
                    svi = makeValue("SequenceValueItem")
                    svi.set("seqName", seqName)
                    // a new tradition: start sequenced values at one hundred thousand instead of ten thousand
                    seqNum = 100000
                    svi.set("seqNum", seqNum)
                    svi.create()
                } else {
                    seqNum = svi.getLong("seqNum")
                    if (staggerMax) {
                        long stagger = Math.round(Math.random() * staggerMax)
                        if (stagger == 0) stagger = 1
                        seqNum += stagger
                    } else {
                        seqNum += 1
                    }
                    svi.set("seqNum", seqNum)
                    svi.update()
                }
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error getting primary sequenced ID", t)
            } finally {
                if (tf.isTransactionInPlace()) tf.commit(beganTransaction)
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            if (suspendedTransaction) tf.resume()
        }

        String prefix = this.ecfi.getConfXmlRoot()."entity-facade"[0]."@sequenced-id-prefix"
        return (prefix?:"") + seqNum.toString()
    }

    /** @see org.moqui.entity.EntityFacade#getEntityGroupName(String) */
    String getEntityGroupName(String entityName) {
        EntityDefinition ed = this.getEntityDefinition(entityName)
        if (!ed) return null
        if (ed.entityNode."@group-name") return ed.entityNode."@group-name"
        return this.ecfi.getConfXmlRoot()."entity-facade"[0]."@default-group-name"
    }

    /** @see org.moqui.entity.EntityFacade#getConnection(String) */
    Connection getConnection(String groupName) {
        DataSource ds = this.dataSourceByGroupMap.get(groupName)
        if (!ds) throw new EntityException("DataSource not initialized for group-name [${groupName}]")
        if (ds instanceof XADataSource) {
            return this.ecfi.transactionFacade.enlistConnection(((XADataSource) ds).getXAConnection())
        } else {
            return ds.getConnection()
        }
    }

    /** @see org.moqui.entity.EntityFacade#makeDataLoader() */
    EntityDataLoader makeDataLoader() { return new EntityDataLoaderImpl(this) }

    /** @see org.moqui.entity.EntityFacade#makeDataWriter() */
    EntityDataWriter makeDataWriter() { return new EntityDataWriterImpl(this) }

    /** @see org.moqui.entity.EntityFacade#makeValue(Element) */
    EntityValue makeValue(Element element) {
        if (!element) return null

        String entityName = element.getTagName()
        if (entityName.indexOf('-') > 0) entityName = entityName.substring(entityName.indexOf('-') + 1)
        if (entityName.indexOf(':') > 0) entityName = entityName.substring(entityName.indexOf(':') + 1)

        EntityValue newValue = makeValue(entityName)
        EntityDefinition ed = newValue.getEntityDefinition()

        for (String fieldName in ed.getFieldNames(true, true)) {
            String attrValue = element.getAttribute(fieldName)
            if (attrValue) {
                newValue.setString(fieldName, attrValue)
            } else {
                org.w3c.dom.NodeList seList = element.getElementsByTagName(fieldName)
                Element subElement = seList.getLength() > 0 ? (Element) seList.item(0) : null
                if (subElement) newValue.setString(fieldName, StupidUtilities.elementValue(subElement))
            }
        }

        return newValue
    }

    protected static final Map<String, String> fieldTypeMap = [
            "id":"java.lang.String",
            "id-long":"java.lang.String",
            "id-very-long":"java.lang.String",
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
    String getFieldJavaType(String fieldType, String entityName) {
        Node databaseNode = this.getDatabaseNode(this.getEntityGroupName(entityName))
        String javaType = databaseNode ? databaseNode."database-type".find({ it.@type == fieldType })?."@java-type" : null
        if (javaType) {
            return javaType
        } else {
            Node databaseListNode = this.ecfi.confXmlRoot."database-list"[0]
            javaType = databaseListNode ? databaseListNode."dictionary-type".find({ it.@type == fieldType })?."@java-type" : null
            if (javaType) {
                return javaType
            } else {
                // get the default field java type
                String defaultJavaType = fieldTypeMap[fieldType]
                if (!defaultJavaType) throw new EntityException("Could not find Java type for field type [${fieldType}] on entity [${entityName}]")
                return defaultJavaType
            }
        }
    }
    protected String getFieldSqlType(String fieldType, String entityName) {
        Node databaseNode = this.getDatabaseNode(this.getEntityGroupName(entityName))
        String sqlType = databaseNode ? databaseNode."database-type".find({ it.@type == fieldType })?."@sql-type" : null
        if (sqlType) {
            return sqlType
        } else {
            Node databaseListNode = this.ecfi.confXmlRoot."database-list"[0]
            sqlType = databaseListNode ? databaseListNode."dictionary-type".find({ it.@type == fieldType })?."@default-sql-type" : null
            if (sqlType) {
                return sqlType
            } else {
                throw new EntityException("Could not find SQL type for field type [${fieldType}] on entity [${entityName}]")
            }
        }
    }

    protected static final Map<String, Integer> javaTypeMap = [
            "java.lang.String":1, "String":1, "org.codehaus.groovy.runtime.GStringImpl":1,
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
        if (!typeInt) throw new EntityException("Java type " + javaType + " not supported for entity fields")
        return typeInt
    }
}
