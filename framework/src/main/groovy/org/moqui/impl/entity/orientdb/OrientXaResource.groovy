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

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.moqui.context.TransactionException
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.transaction.Status
import javax.transaction.Transaction
import javax.transaction.TransactionManager
import javax.transaction.xa.XAException
import javax.transaction.xa.XAResource
import javax.transaction.xa.Xid

class OrientXaResource implements XAResource {
    protected final static Logger logger = LoggerFactory.getLogger(OrientXaResource.class)

    protected ExecutionContextFactoryImpl ecfi
    protected OrientDatasourceFactory odf
    protected ODatabaseDocumentTx database

    protected Transaction tx = null
    protected Xid xid = null
    protected Integer timeout = null
    protected boolean active = false
    protected boolean suspended = false


    OrientXaResource(ExecutionContextFactoryImpl ecfi, OrientDatasourceFactory odf) {
        this.ecfi = ecfi
        this.odf = odf
    }

    OrientXaResource enlistOrGet() {
        // logger.warn("========= Enlisting new OrientXaResource")
        TransactionManager tm = ecfi.getTransactionFacade().getTransactionManager()
        if (tm == null || tm.getStatus() != Status.STATUS_ACTIVE) throw new XAException("Cannot enlist: no transaction manager or transaction not active")
        Transaction tx = tm.getTransaction()
        if (tx == null) throw new XAException(XAException.XAER_NOTA)
        this.tx = tx

        OrientXaResource existingOxr = (OrientXaResource) ecfi.getTransactionFacade().getActiveXaResource("OrientXaResource")
        if (existingOxr != null) {
            logger.warn("Tried to enlist OrientXaResource in current transaction but one is already in place, not enlisting", new TransactionException("OrientXaResource already in place"))
            return existingOxr
        }
        // logger.warn("================= putting and enlisting new OrientXaResource")
        ecfi.getTransactionFacade().putAndEnlistActiveXaResource("OrientXaResource", this)

        this.database = odf.getDatabase()
        this.database.begin()

        return this
    }

    ODatabaseDocumentTx getDatabase() { return database }

    @Override
    void start(Xid xid, int flag) throws XAException {
        // logger.warn("========== OrientXaResource.start(${xid}, ${flag})")
        if (this.active) {
            if (this.xid != null && this.xid.equals(xid)) {
                throw new XAException(XAException.XAER_DUPID);
            } else {
                throw new XAException(XAException.XAER_PROTO);
            }
        }
        if (this.xid != null && !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

        // logger.warn("================= starting OrientXaResource with xid=${xid}; suspended=${suspended}")
        this.active = true
        this.suspended = false
        this.xid = xid
    }

    @Override
    void end(Xid xid, int flag) throws XAException {
        // logger.warn("========== OrientXaResource.end(${xid}, ${flag})")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
        if (flag == TMSUSPEND) {
            if (!this.active) throw new XAException(XAException.XAER_PROTO)
            this.suspended = true
            // logger.warn("================= suspending OrientXaResource with xid=${xid}")
        }
        if (flag == TMSUCCESS || flag == TMFAIL) {
            // allow a success/fail end if TX is suspended without a resume flagged start first
            if (!this.active && !this.suspended) throw new XAException(XAException.XAER_PROTO)
        }

        this.active = false
    }

    @Override
    void forget(Xid xid) throws XAException {
        // logger.warn("========== OrientXaResource.forget(${xid})")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
        this.xid = null
        if (active) logger.warn("forget() called without end()")
    }

    @Override
    int prepare(Xid xid) throws XAException {
        // logger.warn("========== OrientXaResource.prepare(${xid}); this.xid=${this.xid}")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
        return XA_OK
    }

    @Override
    Xid[] recover(int flag) throws XAException {
        // logger.warn("========== OrientXaResource.recover(${flag}); this.xid=${this.xid}")
        return this.xid != null ? [this.xid] : []
    }
    @Override
    boolean isSameRM(XAResource xaResource) throws XAException {
        return xaResource instanceof OrientXaResource && ((OrientXaResource) xaResource).xid == this.xid
    }
    @Override
    int getTransactionTimeout() throws XAException { return this.timeout == null ? 0 : this.timeout }
    @Override
    boolean setTransactionTimeout(int seconds) throws XAException {
        this.timeout = (seconds == 0 ? null : seconds)
        return true
    }

    @Override
    void commit(Xid xid, boolean onePhase) throws XAException {
        // logger.warn("========== OrientXaResource.commit(${xid}, ${onePhase}); this.xid=${this.xid}; this.active=${this.active}")
        if (this.active) logger.warn("commit() called without end()")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

        try {
            database.commit()
        } catch (Exception e) {
            logger.error("Error in OrientDB commit: ${e.toString()}", e)
            throw new XAException("Error in OrientDB commit: ${e.toString()}")
        } finally {
            database.close()
        }

        this.xid = null
        this.active = false
    }

    @Override
    void rollback(Xid xid) throws XAException {
        // logger.warn("========== OrientXaResource.rollback(${xid}); this.xid=${this.xid}; this.active=${this.active}")
        if (this.active) logger.warn("rollback() called without end()")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

        try {
            database.rollback()
        } catch (Exception e) {
            logger.error("Error in OrientDB rollback: ${e.toString()}", e)
            throw new XAException("Error in OrientDB rollback: ${e.toString()}")
        } finally {
            database.close()
        }

        this.xid = null
        this.active = false
    }
}
