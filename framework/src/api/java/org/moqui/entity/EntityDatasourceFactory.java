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
package org.moqui.entity;

import groovy.util.Node;
import javax.sql.DataSource;

public interface EntityDatasourceFactory {
    public EntityDatasourceFactory init(EntityFacade ef, Node datasourceNode, String tenantId);
    public void destroy();
    public void checkAndAddTable(String entityName);
    public EntityValue makeEntityValue(String entityName);
    public EntityFind makeEntityFind(String entityName);

    /** Return the JDBC DataSource, if applicable. Return null if no JDBC DataSource exists for this Entity Datasource. */
    public DataSource getDataSource();
}
