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

import com.atomikos.jdbc.AbstractDataSourceBean
import com.atomikos.jdbc.AtomikosDataSourceBean
import com.atomikos.jdbc.nonxa.AtomikosNonXADataSourceBean

import javax.naming.Context
import javax.naming.InitialContext
import javax.naming.NamingException
import javax.sql.DataSource

import org.moqui.entity.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EntityDatasourceFactoryImpl implements EntityDatasourceFactory {
    protected final static Logger logger = LoggerFactory.getLogger(EntityDatasourceFactoryImpl.class)

    protected EntityFacadeImpl efi
    protected Node datasourceNode
    protected String tenantId

    protected DataSource dataSource

    EntityDatasourceFactoryImpl() { }

    @Override
    EntityDatasourceFactory init(EntityFacade ef, Node datasourceNode, String tenantId) {
        // local fields
        this.efi = (EntityFacadeImpl) ef
        this.datasourceNode = datasourceNode
        this.tenantId = tenantId

        // init the DataSource
        EntityValue tenant = null
        EntityFacadeImpl defaultEfi = null
        if (this.tenantId != "DEFAULT") {
            defaultEfi = efi.ecfi.getEntityFacade("DEFAULT")
            tenant = defaultEfi.makeFind("moqui.tenant.Tenant").condition("tenantId", this.tenantId).one()
        }

        EntityValue tenantDataSource = null
        EntityList tenantDataSourceXaPropList = null
        if (tenant != null) {
            tenantDataSource = defaultEfi.makeFind("moqui.tenant.TenantDataSource").condition("tenantId", this.tenantId)
                    .condition("entityGroupName", datasourceNode."@group-name").one()
            tenantDataSourceXaPropList = defaultEfi.makeFind("moqui.tenant.TenantDataSourceXaProp")
                    .condition("tenantId", this.tenantId).condition("entityGroupName", datasourceNode."@group-name")
                    .list()
        }

        if (datasourceNode."jndi-jdbc") {
            Node serverJndi = efi.ecfi.getConfXmlRoot()."entity-facade"[0]."server-jndi"[0]
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

                String jndiName = tenantDataSource ? tenantDataSource.jndiName : datasourceNode."jndi-jdbc"[0]."@jndi-name"
                this.dataSource = (DataSource) ic.lookup(jndiName)
                if (this.dataSource == null) {
                    logger.error("Could not find DataSource with name [${datasourceNode."jndi-jdbc"[0]."@jndi-name"}] in JNDI server [${serverJndi ? serverJndi."@context-provider-url" : "default"}] for datasource with group-name [${datasourceNode."@group-name"}].")
                }
            } catch (NamingException ne) {
                logger.error("Error finding DataSource with name [${datasourceNode."jndi-jdbc"[0]."@jndi-name"}] in JNDI server [${serverJndi ? serverJndi."@context-provider-url" : "default"}] for datasource with group-name [${datasourceNode."@group-name"}].", ne)
            }
        } else if (datasourceNode."inline-jdbc") {
            Node inlineJdbc = datasourceNode."inline-jdbc"[0]
            Node xaProperties = inlineJdbc."xa-properties"[0]
            Node database = efi.getDatabaseNode((String) datasourceNode."@group-name")

            // special thing for embedded derby, just set an system property; for derby.log, etc
            if (datasourceNode."@database-conf-name" == "derby") {
                System.setProperty("derby.system.home", System.getProperty("moqui.runtime") + "/db/derby")
                logger.info("Set property derby.system.home to [${System.getProperty("derby.system.home")}]")
            }

            AbstractDataSourceBean ads
            if (xaProperties) {
                AtomikosDataSourceBean ds = new AtomikosDataSourceBean()
                ds.setUniqueResourceName(this.tenantId + datasourceNode."@group-name")
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
                ds.setUniqueResourceName(this.tenantId + datasourceNode."@group-name")
                String driver = inlineJdbc."@jdbc-driver" ? inlineJdbc."@jdbc-driver" : database."@default-jdbc-driver"
                ds.setDriverClassName(driver)
                ds.setUrl(tenantDataSource ? (String) tenantDataSource.jdbcUri : inlineJdbc."@jdbc-uri")
                ds.setUser(tenantDataSource ? (String) tenantDataSource.jdbcUsername : inlineJdbc."@jdbc-username")
                ds.setPassword(tenantDataSource ? (String) tenantDataSource.jdbcPassword : inlineJdbc."@jdbc-password")

                ads = ds
            }

            String txIsolationLevel = inlineJdbc."@isolation-level" ? inlineJdbc."@isolation-level" : database."@default-isolation-level"
            if (txIsolationLevel && efi.getTxIsolationFromString(txIsolationLevel) != -1) {
                ads.setDefaultIsolationLevel(efi.getTxIsolationFromString(txIsolationLevel))
            }

            // no need for this, just sets min and max sizes: ads.setPoolSize
            if (inlineJdbc."@pool-minsize") ads.setMinPoolSize(inlineJdbc."@pool-minsize" as int)
            if (inlineJdbc."@pool-maxsize") ads.setMaxPoolSize(inlineJdbc."@pool-maxsize" as int)

            if (inlineJdbc."@pool-time-idle") ads.setMaxIdleTime(inlineJdbc."@pool-time-idle" as int)
            if (inlineJdbc."@pool-time-reap") ads.setReapTimeout(inlineJdbc."@pool-time-reap" as int)
            if (inlineJdbc."@pool-time-maint") ads.setMaintenanceInterval(inlineJdbc."@pool-time-maint" as int)
            if (inlineJdbc."@pool-time-wait") ads.setBorrowConnectionTimeout(inlineJdbc."@pool-time-wait" as int)

            if (inlineJdbc."@pool-test-query") {
                ads.setTestQuery((String) inlineJdbc."@pool-test-query")
            } else if (database."@default-test-query") {
                ads.setTestQuery((String) database."@default-test-query")
            }

            this.dataSource = ads
        } else {
            throw new EntityException("Found datasource with no jdbc sub-element (in datasource with group-name [${datasourceNode."@group-name"}])")
        }

        return this
    }

    @Override
    void destroy() {
        // destroy anything related to the internal impl, ie Atomikos
        if (dataSource instanceof AtomikosDataSourceBean) {
            ((AtomikosDataSourceBean) dataSource).close()
        }
    }

    @Override
    EntityValue makeEntityValue(String entityName) {
        EntityDefinition entityDefinition = efi.getEntityDefinition(entityName)
        if (!entityDefinition) {
            throw new EntityException("Entity not found for name [${entityName}]")
        }
        return new EntityValueImpl(entityDefinition, efi)
    }

    @Override
    EntityFind makeEntityFind(String entityName) {
        return new EntityFindImpl(efi, entityName)
    }

    @Override
    DataSource getDataSource() { return dataSource }
}
