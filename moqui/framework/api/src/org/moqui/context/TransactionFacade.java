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

import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

/** Use this interface to do transaction demarcation and related operations.
 * This should be used instead of using the JTA UserTransaction and TransactionManager interfaces.
 *
 * When you do transaction demarcation yourself use something like:
 *
 * <pre>
 * boolean beganTransaction = transactionFacade.begin(timeout);
 * try {
 *     ...
 * } catch (Throwable t) {
 *     transactionFacade.rollback(beganTransaction, "...", t);
 * } finally {
 *     if (transactionFacade.isTransactionInPlace()) {
 *         transactionFacade.commit(beganTransaction);
 *     }
 * }
 * </pre>
 *
 * This code will use a transaction if one is already in place (including setRollbackOnly instead of rollbackon
 * error), or begin a new one if not.
 *
 * When you want to suspend the current transaction and create a new one use something like: 
 *
 * <pre>
 * Transaction parentTransaction = null;
 * try {
 *     if (TransactionUtil.isTransactionInPlace()) {
 *         parentTransaction = TransactionUtil.suspend();
 *     }
 *
 *     boolean beganTransaction = transactionFacade.begin(timeout);
 *     try {
 *         ...
 *     } catch (Throwable t) {
 *         transactionFacade.rollback(beganTransaction, "...", t);
 *     } finally {
 *         if (transactionFacade.isTransactionInPlace()) {
 *             transactionFacade.commit(beganTransaction);
 *         }
 *     }
 * } catch (TransactionException e) {
 *     ...
 * } finally {
 *     if (parentTransaction != null) {
 *         transactionFacade.resume(parentTransaction);
 *     }
 * }
 * </pre>
 */
public interface TransactionFacade {

    /** Gets the status of the transaction in the current thread IF
     * transactions are available, otherwise returns STATUS_NO_TRANSACTION */
    int getStatus() throws TransactionException;

    String getStatusString() throws TransactionException;

    boolean isTransactionInPlace() throws TransactionException;

    /** Begins a transaction in the current thread IF transactions are available; only
     * tries if the current transaction status is ACTIVE, if not active it returns false.
     * If and on only if it begins a transaction it will return true. In other words, if
     * a transaction is already in place it will return false and do nothing.
     */
    boolean begin(Integer timeout) throws TransactionException;

    /** Commits the transaction in the current thread IF transactions are available
     *  AND if beganTransaction is true
     */
    void commit(boolean beganTransaction) throws TransactionException;

    /** Rolls back transaction in the current thread IF transactions are available
     *  AND if beganTransaction is true; if beganTransaction is not true,
     *  setRollbackOnly is called to insure that the transaction will be rolled back
     */
    void rollback(boolean beganTransaction, String causeMessage, Throwable causeThrowable) throws TransactionException;

    /** Commits the transaction in the current thread IF transactions are available */
    void commit() throws TransactionException;

    /** Rolls back transaction in the current thread IF transactions are available */
    void rollback() throws TransactionException;

    /** Makes a rollback the only possible outcome of the transaction in the current thread IF transactions are available */
    void setRollbackOnly(String causeMessage, Throwable causeThrowable) throws TransactionException;

    Transaction suspend() throws TransactionException;

    void resume(Transaction parentTx) throws TransactionException;

    void enlistResource(XAResource resource) throws TransactionException;

    void registerSynchronization(Synchronization sync) throws TransactionException;
}
