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
package org.moqui.impl.context

import com.atomikos.icatch.config.UserTransactionService
import com.atomikos.icatch.config.UserTransactionServiceImp
import com.atomikos.icatch.jta.UserTransactionManager

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.TransactionInternal

import javax.transaction.TransactionManager
import javax.transaction.UserTransaction

class TransactionInternalAtomikos implements TransactionInternal {

    protected UserTransactionService atomikosUts = null
    protected UserTransaction ut
    protected TransactionManager tm

    @Override
    TransactionInternal init(ExecutionContextFactory ecf) {
        atomikosUts = new UserTransactionServiceImp()
        atomikosUts.init()

        UserTransactionManager utm = new UserTransactionManager()
        this.ut = utm
        this.tm = utm

        return this
    }

    @Override
    TransactionManager getTransactionManager() { return tm }

    @Override
    UserTransaction getUserTransaction() { return ut }

    @Override
    void destroy() {
        atomikosUts.shutdown(false)
    }
}
