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
package org.moqui.impl.entity.orientdb

import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.entity.EntityFindImpl
import org.moqui.impl.entity.EntityValueImpl

import javax.sql.DataSource

import org.moqui.entity.*
import com.orientechnologies.orient.server.OServer
import com.orientechnologies.orient.server.OServerMain
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class OrientDatasourceFactory implements EntityDatasourceFactory {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OrientDatasourceFactory.class)

    protected EntityFacadeImpl efi
    protected Node datasourceNode
    protected String tenantId

    protected OServer oserver
    protected ODatabaseDocumentPool databaseDocumentPool

    protected String uri
    protected String username
    protected String password

    OrientDatasourceFactory() { }

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

        Node inlineOtherNode = datasourceNode."inline-other"[0]
        uri = tenantDataSource ? tenantDataSource.jdbcUri : (inlineOtherNode."@uri" ?: inlineOtherNode."@jdbc-uri")
        username = tenantDataSource ? tenantDataSource.jdbcUsername : (inlineOtherNode."@username" ?: inlineOtherNode."@jdbc-username")
        password = tenantDataSource ? tenantDataSource.jdbcPassword : (inlineOtherNode."@password" ?: inlineOtherNode."@jdbc-password")

        oserver = OServerMain.create()
        oserver.startup(Thread.currentThread().getContextClassLoader().getResourceAsStream("orientdb-server-config.xml"))
        oserver.activate()

        databaseDocumentPool = new ODatabaseDocumentPool()
        databaseDocumentPool.setup((inlineOtherNode."@pool-minsize" ?: "5") as int, (inlineOtherNode."@pool-maxsize" ?: "50") as int)

        return this
    }

    ODatabaseDocumentPool getDatabaseDocumentPool() { return databaseDocumentPool }

    /** Returns the main database access object for OrientDB.
     * Remember to call close() on it when you're done with it (preferably in a try/finally block)!
     */
    ODatabaseDocumentTx getDatabase() { return databaseDocumentPool.acquire(uri, username, password) }

    @Override
    void destroy() {
        databaseDocumentPool.close()
        oserver.shutdown()
    }

    @Override
    EntityValue makeEntityValue(String entityName) {
        EntityDefinition entityDefinition = efi.getEntityDefinition(entityName)
        if (!entityDefinition) {
            throw new EntityException("Entity not found for name [${entityName}]")
        }
        return new OrientEntityValue(entityDefinition, efi, this)
    }

    @Override
    EntityFind makeEntityFind(String entityName) {
        return new OrientEntityFind(efi, entityName, this)
    }

    @Override
    DataSource getDataSource() { return null }
}
