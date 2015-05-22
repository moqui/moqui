/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
