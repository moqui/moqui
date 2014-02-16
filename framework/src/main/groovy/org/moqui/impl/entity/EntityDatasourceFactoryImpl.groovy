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

import org.moqui.context.TransactionInternal
import org.moqui.entity.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.naming.Context
import javax.naming.InitialContext
import javax.naming.NamingException
import javax.sql.DataSource

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

        if (datasourceNode."jndi-jdbc") {
            EntityValue tenant = null
            EntityFacadeImpl defaultEfi = null
            if (this.tenantId != "DEFAULT") {
                defaultEfi = efi.ecfi.getEntityFacade("DEFAULT")
                tenant = defaultEfi.makeFind("moqui.tenant.Tenant").condition("tenantId", this.tenantId).one()
            }

            EntityValue tenantDataSource = null
            if (tenant != null) {
                tenantDataSource = defaultEfi.makeFind("moqui.tenant.TenantDataSource").condition("tenantId", this.tenantId)
                        .condition("entityGroupName", datasourceNode."@group-name").one()
            }

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
            // special thing for embedded derby, just set an system property; for derby.log, etc
            if (datasourceNode."@database-conf-name" == "derby") {
                System.setProperty("derby.system.home", System.getProperty("moqui.runtime") + "/db/derby")
                logger.info("Set property derby.system.home to [${System.getProperty("derby.system.home")}]")
            }

            TransactionInternal ti = efi.getEcfi().getTransactionFacade().getTransactionInternal()
            this.dataSource = ti.getDataSource(efi, datasourceNode, tenantId)
        } else {
            throw new EntityException("Found datasource with no jdbc sub-element (in datasource with group-name [${datasourceNode."@group-name"}])")
        }

        return this
    }

    @Override
    void destroy() {
        // NOTE: TransactionInternal DataSource will be destroyed when the TransactionFacade is destroyed
    }

    @Override
    void checkAndAddTable(String entityName) { efi.getEntityDbMeta().checkTableStartup(efi.getEntityDefinition(entityName)) }

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
