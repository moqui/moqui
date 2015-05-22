/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import groovy.util.Node;
import org.moqui.entity.EntityFacade;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

public interface TransactionInternal {
    TransactionInternal init(ExecutionContextFactory ecf);

    TransactionManager getTransactionManager();
    UserTransaction getUserTransaction();
    DataSource getDataSource(EntityFacade ef, Node datasourceNode, String tenantId);

    void destroy();
}
