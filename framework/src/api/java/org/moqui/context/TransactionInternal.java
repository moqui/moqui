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
