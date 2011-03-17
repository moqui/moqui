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
package org.moqui.impl.service

import javax.transaction.xa.XAResource
import javax.transaction.Transaction
import javax.transaction.xa.Xid
import javax.transaction.xa.XAException
import javax.transaction.TransactionManager
import javax.transaction.Status

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.service.ServiceCallSpecial

class ServiceCallSpecialImpl extends ServiceCallImpl implements ServiceCallSpecial {

    ServiceCallSpecialImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallSpecial name(String serviceName) { this.setServiceName(serviceName); return this }

    @Override
    ServiceCallSpecial name(String v, String n) { path = null; verb = v; noun = n; return this }

    @Override
    ServiceCallSpecial name(String p, String v, String n) { path = p; verb = v; noun = n; return this }

    @Override
    ServiceCallSpecial parameters(Map<String, Object> map) { parameters.putAll(map); return this }

    @Override
    ServiceCallSpecial parameter(String name, Object value) { parameters.put(name, value); return this }

    @Override
    void registerOnCommit() {
        ServiceXaResource sxr = new ServiceXaResource(this, sfi.ecfi, true)
        sxr.enlist()
    }

    @Override
    void registerOnRollback() {
        ServiceXaResource sxr = new ServiceXaResource(this, sfi.ecfi, false)
        sxr.enlist()
    }

    static class ServiceXaResource extends Thread implements XAResource {
        protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceXaResource.class)

        protected ExecutionContextFactoryImpl ecfi
        protected String serviceName
        protected Map<String, Object> parameters
        protected boolean runOnCommit

        protected Transaction tx = null
        protected Xid xid = null
        protected Integer timeout = null

        protected boolean active = false

        ServiceXaResource(ServiceCallSpecialImpl scsi, ExecutionContextFactoryImpl ecfi, boolean runOnCommit) {
            this.ecfi = ecfi
            this.serviceName = scsi.serviceName
            this.parameters = new HashMap(scsi.parameters)
            this.runOnCommit = runOnCommit
        }

        void enlist() {
            TransactionManager tm = ecfi.transactionFacade.getTransactionManager()
            if (tm == null && tm.getStatus() != Status.STATUS_ACTIVE) throw new XAException("Cannot enlist: no transaction manager or transaction not active")

            Transaction tx = tm.getTransaction();
            if (tx == null) throw new XAException(XAException.XAER_NOTA)

            this.tx = tx
            tx.enlistResource(this)
        }

        /** @see javax.transaction.xa.XAResource#start(javax.transaction.xa.Xid xid, int flag) */
        void start(Xid xid, int flag) throws XAException {
            if (this.active) {
                if (this.xid != null && this.xid.equals(xid)) {
                    throw new XAException(XAException.XAER_DUPID);
                } else {
                    throw new XAException(XAException.XAER_PROTO);
                }
            }
            if (this.xid != null && !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

            this.active = true
            this.xid = xid

            // start a thread with this object to do something on timeout
            this.setName("ServiceXaResourceThread")
            this.setDaemon(true)
            this.start()
        }

        /** @see javax.transaction.xa.XAResource#end(javax.transaction.xa.Xid xid, int flag) */
        void end(Xid xid, int flag) throws XAException {
            if (!this.active) throw new XAException(XAException.XAER_PROTO)
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
            this.active = false
        }

        /** @see javax.transaction.xa.XAResource#forget(javax.transaction.xa.Xid xid) */
        void forget(Xid xid) throws XAException {
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
            this.xid = null
            if (active) logger.warn("forget() called without end()")
        }

        /** @see javax.transaction.xa.XAResource#prepare(javax.transaction.xa.Xid xid) */
        int prepare(Xid xid) throws XAException {
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
            return XA_OK
        }

        /** @see javax.transaction.xa.XAResource#recover(int flag) */
        Xid[] recover(int flag) throws XAException { return this.xid != null ? [this.xid] : [] }
        /** @see javax.transaction.xa.XAResource#isSameRM(javax.transaction.xa.XAResource xaResource) */
        boolean isSameRM(XAResource xaResource) throws XAException { return xaResource == this }
        /** @see javax.transaction.xa.XAResource#getTransactionTimeout() */
        int getTransactionTimeout() throws XAException { return this.timeout == null ? 0 : this.timeout }
        /** @see javax.transaction.xa.XAResource#setTransactionTimeout(int seconds) */
        boolean setTransactionTimeout(int seconds) throws XAException {
            this.timeout = (seconds == 0 ? null : seconds)
            return true
        }

        /** @see javax.transaction.xa.XAResource#commit(javax.transaction.xa.Xid xid, boolean onePhase) */
        void commit(Xid xid, boolean onePhase) throws XAException {
            if (this.active) logger.warn("commit() called without end()")
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

            if (runOnCommit) ecfi.serviceFacade.async().name(this.serviceName).parameters(this.parameters).call()

            this.xid = null
            this.active = false
        }

        /** @see javax.transaction.xa.XAResource#rollback(javax.transaction.xa.Xid xid) */
        void rollback(Xid xid) throws XAException {
            if (this.active) logger.warn("rollback() called without end()")
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

            if (!runOnCommit) ecfi.serviceFacade.async().name(this.serviceName).parameters(this.parameters).call()

            this.xid = null
            this.active = false
        }

        @Override
        void run() {
            try {
                if (timeout != null) {
                    // sleep until the transaction times out
                    sleep(timeout.intValue() * 1000)

                    if (active) {
                        String statusString = ecfi.transactionFacade.getStatusString()
                        logger.warn("Transaction timeout [${timeout}] status [${statusString}] xid [${this.xid}], service [${serviceName}] did NOT run")

                        // TODO: what to do, if anything, when the we timeout and the service hasn't been run?
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Service Call Special Interrupted", e)
            } catch (Throwable t) {
                logger.warn("Service Call Special Error", t)
            }
        }
    }
}
