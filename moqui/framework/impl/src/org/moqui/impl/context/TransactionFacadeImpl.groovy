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

import org.moqui.context.TransactionFacade
import javax.transaction.Transaction
import javax.transaction.xa.XAResource
import javax.transaction.Synchronization

class TransactionFacadeImpl implements TransactionFacade {

    protected final ExecutionContextFactoryImpl ecfi;

    public TransactionFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;

        // TODO: init tx mgr, etc
    }

    public void destroy() {
        // TODO: destroy tx mgr, etc
    }

    /** @see org.moqui.context.TransactionFacade#getStatus() */
    int getStatus() {
        // TODO: implement this
        return 0;
    }

    /** @see org.moqui.context.TransactionFacade#getStatusString() */
    String getStatusString() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.TransactionFacade#isTransactionInPlace() */
    boolean isTransactionInPlace() {
        // TODO: implement this
        return false;
    }

    /** @see org.moqui.context.TransactionFacade#begin(Integer) */
    boolean begin(Integer timeout) {
        // TODO: implement this
        return false;
    }

    /** @see org.moqui.context.TransactionFacade#commit(boolean) */
    void commit(boolean beganTransaction) {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#rollback(boolean, String, Throwable) */
    void rollback(boolean beganTransaction, String causeMessage, Throwable causeThrowable) {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#commit() */
    void commit() {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#rollback() */
    void rollback() {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#setRollbackOnly(String, Throwable) */
    void setRollbackOnly(String causeMessage, Throwable causeThrowable) {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#suspend() */
    Transaction suspend() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.TransactionFacade#resume(Transaction) */
    void resume(Transaction parentTx) {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#enlistResource(XAResource) */
    void enlistResource(XAResource resource) {
        // TODO: implement this
    }

    /** @see org.moqui.context.TransactionFacade#registerSynchronization(Synchronization) */
    void registerSynchronization(Synchronization sync) {
        // TODO: implement this
    }
}
