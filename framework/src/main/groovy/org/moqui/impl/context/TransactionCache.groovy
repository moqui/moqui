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

import org.apache.commons.collections.map.ListOrderedMap
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.entity.EntityList
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.entity.EntityFindBase
import org.moqui.impl.entity.EntityValueBase
import org.moqui.impl.entity.EntityValueImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.transaction.Status
import javax.transaction.Transaction
import javax.transaction.TransactionManager
import javax.transaction.xa.XAException
import javax.transaction.xa.XAResource
import javax.transaction.xa.Xid

/** This is a per-transaction cache that basically pretends to be the database for the scope of the transaction.
 * Test your code well when using this as it doesn't support everything.
 *
 * Some known limitations:
 * - find list and iterate don't cache results (but do filter and add to results aside from limitations below)
 * - EntityListIterator.getPartialList() and iterating through results with next/previous does not add created values
 * - find with DB limit will return wrong number of values if deleted values were in the results
 * - find count doesn't add for created values, subtract for deleted values, and for updates if old matched and new doesn't subtract and vice-versa
 * - view-entities won't work, they don't incorporate results from TX Cache
 * - if a value is created or update, then a record with FK is created, then the value is updated again commit writes may fail with FK violation (see update() method for other notes)
 */
class TransactionCache implements XAResource {
    protected final static Logger logger = LoggerFactory.getLogger(TransactionCache.class)
    public enum WriteMode { CREATE, UPDATE, DELETE }

    protected ExecutionContextFactoryImpl ecfi

    protected Transaction tx = null
    protected Xid xid = null
    protected Integer timeout = null
    protected boolean active = false
    protected boolean suspended = false

    protected Map<Map, EntityValueBase> readCache = [:]
    protected ListOrderedMap writeInfoList = new ListOrderedMap()

    TransactionCache(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
    }

    TransactionCache enlist() {
        // logger.warn("========= Enlisting new TransactionCache")
        TransactionManager tm = ecfi.getTransactionFacade().getTransactionManager()
        if (tm == null || tm.getStatus() != Status.STATUS_ACTIVE) throw new XAException("Cannot enlist: no transaction manager or transaction not active")
        Transaction tx = tm.getTransaction()
        if (tx == null) throw new XAException(XAException.XAER_NOTA)
        this.tx = tx

        // logger.warn("================= putting and enlisting new TransactionCache")
        ecfi.getTransactionFacade().putAndEnlistActiveXaResource("TransactionCache", this)

        return this
    }

    static Map makeKey(EntityValueBase evb) {
        if (evb == null) return null
        Map key = evb.getPrimaryKeys()
        if (!key) return null
        key.put("_entityName", evb.getEntityName())
        return key
    }
    static Map makeKey(EntityFindBase efb) {
        // NOTE: this should never come in null (EntityFindBase.one() => oneGet() => this is only call path)
        if (efb == null) return null
        Map key = efb.getSimpleMapPrimaryKeys()
        if (!key) return null
        key.put("_entityName", efb.getEntityDef().getFullEntityName())
        return key
    }

    /** Returns true if create handled, false if not; if false caller should handle the operation */
    boolean create(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return false

        // if create info already exists blow up
        EntityWriteInfo currentEwi = (EntityWriteInfo) writeInfoList.get(key)
        if (readCache.containsKey(key) || (currentEwi != null && currentEwi.writeMode != WriteMode.DELETE))
            throw new EntityException("Tried to create a value that already exists, entity [${evb.getEntityName()}], PK ${evb.getPrimaryKeys()}")

        if (currentEwi != null && currentEwi.writeMode == WriteMode.DELETE) {
            // if deleted remove delete and add an update
            writeInfoList.remove(key)
            writeInfoList.put(key, new EntityWriteInfo(evb, WriteMode.UPDATE))
        } else {
            // add to createCache
            writeInfoList.put(key, new EntityWriteInfo(evb, WriteMode.CREATE))
        }

        // add to readCache after so we don't think it already exists
        readCache.put(key, evb)
        return true
    }
    boolean update(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return false
        // add to readCache
        readCache.put(key, evb)

        // if is in as create or update that value (but stay in current write mode), if delete blow up, otherwise add as update
        EntityWriteInfo currentEwi = (EntityWriteInfo) writeInfoList.get(key)
        if (currentEwi != null) {
            if (currentEwi.writeMode == WriteMode.CREATE || currentEwi.writeMode == WriteMode.UPDATE) {
                // TODO: if new value sets a field with an FK to a field created already in this TX create another
                //  update record (how to do with key strategy!?! maybe add a dummy Map entry...) to avoid FK issues
                currentEwi.evb.setFields(evb, true, null, false)
            } else {
                throw new EntityException("Tried to update a value that has been deleted, entity [${evb.getEntityName()}], PK ${evb.getPrimaryKeys()}")
            }
        } else {
            writeInfoList.put(key, new EntityWriteInfo(evb, WriteMode.UPDATE))
        }
        return true
    }
    boolean delete(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return false

        // remove from readCache if needed
        readCache.remove(key)

        // if in current create remove from write list, if update change to delete, otherwise add as delete
        EntityWriteInfo currentEwi = (EntityWriteInfo) writeInfoList.get(key)
        if (currentEwi != null) {
            if (currentEwi.writeMode == WriteMode.CREATE) {
                writeInfoList.remove(key)
            } else if (currentEwi.writeMode == WriteMode.UPDATE) {
                currentEwi.writeMode = WriteMode.DELETE
            } else {
                // already deleted, throw an exception
                throw new EntityException("Tried to delete a value that already exists, entity [${evb.getEntityName()}], PK ${evb.getPrimaryKeys()}")
            }
        } else {
            writeInfoList.put(key, new EntityWriteInfo(evb, WriteMode.DELETE))
        }
        return true
    }
    boolean refresh(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return false
        EntityValueBase curEvb = readCache.get(key)
        if (curEvb != null) {
            evb.setFields(curEvb, true, null, false)
            return true
        } else {
            return false
        }
    }

    boolean isTxCreate(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return false
        EntityWriteInfo currentEwi = (EntityWriteInfo) writeInfoList.get(key)
        if (currentEwi == null) return false
        return currentEwi.writeMode == WriteMode.CREATE
    }
    EntityValueBase oneGet(EntityFindBase efb) {
        // NOTE: do nothing here on forUpdate, handled by caller
        Map key = makeKey(efb)
        if (key == null) return null

        // if this has been deleted return a DeletedEntityValue instance so caller knows it was deleted and doesn't look in the DB for another record
        EntityWriteInfo currentEwi = (EntityWriteInfo) writeInfoList.get(key)
        if (currentEwi != null && currentEwi.writeMode == WriteMode.DELETE) return new DeletedEntityValue(efb.getEntityDef(), ecfi.getEntityFacade())

        return readCache.get(key)
    }
    void onePut(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return
        readCache.put(key, evb)
    }

    EntityList list(EntityFindBase efb) {
        // future maybe: cache list returned
        return null
    }
    // NOTE: no need to filter EntityList or EntityListIterator, they do it internally by calling this method
    WriteMode checkUpdateValue(EntityValueBase evb) {
        Map key = makeKey(evb)
        if (key == null) return null
        EntityWriteInfo currentEwi = (EntityWriteInfo) writeInfoList.get(key)
        if (currentEwi == null) {
            // add to readCache for future reference
            onePut(evb)
            return null
        }
        if (currentEwi.writeMode == WriteMode.UPDATE) {
            evb.setFields(currentEwi.evb, true, null, false)
            // add to readCache
            onePut(evb)
        } else if (currentEwi.writeMode == WriteMode.CREATE) {
            throw new EntityException("Found value from database that matches a value created in the write-through transaction cache, throwing error now instead of waiting to fail on commit")
        }
        return currentEwi.writeMode
    }
    List<EntityValueBase> getCreatedValueList(String entityName, EntityCondition ec) {
        List<EntityValueBase> valueList = []
        for (EntityWriteInfo ewi in writeInfoList.valueList()) {
            // if (entityName.contains("FOO")) logger.warn("======= Checking ${ewi.evb.getEntityName()}:${ewi.pkMap}:${ewi.writeMode}")
            if (ewi.evb.getEntityName() != entityName) continue
            if (ewi.writeMode == WriteMode.CREATE && ec.mapMatches(ewi.evb)) valueList.add(ewi.evb)
        }
        return valueList
    }


    @Override
    void start(Xid xid, int flag) throws XAException {
        // logger.warn("========== TransactionCache.start(${xid}, ${flag})")
        if (this.active) {
            if (this.xid != null && this.xid.equals(xid)) {
                throw new XAException(XAException.XAER_DUPID);
            } else {
                throw new XAException(XAException.XAER_PROTO);
            }
        }
        if (this.xid != null && !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

        // logger.warn("================= starting TransactionCache with xid=${xid}; suspended=${suspended}")
        this.active = true
        this.suspended = false
        this.xid = xid
    }

    @Override
    void end(Xid xid, int flag) throws XAException {
        // logger.warn("========== TransactionCache.end(${xid}, ${flag})")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
        if (flag == TMSUSPEND) {
            if (!this.active) throw new XAException(XAException.XAER_PROTO)
            this.suspended = true
            // logger.warn("================= suspending TransactionCache with xid=${xid}")
        }
        if (flag == TMSUCCESS || flag == TMFAIL) {
            // allow a success/fail end if TX is suspended without a resume flagged start first
            if (!this.active && !this.suspended) throw new XAException(XAException.XAER_PROTO)
        }
        this.active = false
    }

    @Override
    void forget(Xid xid) throws XAException {
        // logger.warn("========== TransactionCache.forget(${xid})")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
        this.xid = null
        if (active) logger.warn("forget() called without end()")
    }

    @Override
    int prepare(Xid xid) throws XAException {
        // logger.warn("========== TransactionCache.prepare(${xid}); this.xid=${this.xid}")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
        return XA_OK
    }

    @Override
    Xid[] recover(int flag) throws XAException {
        // logger.warn("========== TransactionCache.recover(${flag}); this.xid=${this.xid}")
        return this.xid != null ? [this.xid] : []
    }
    @Override
    boolean isSameRM(XAResource xaResource) throws XAException { return xaResource == this }
    @Override
    int getTransactionTimeout() throws XAException { return this.timeout == null ? 0 : this.timeout }
    @Override
    boolean setTransactionTimeout(int seconds) throws XAException {
        this.timeout = (seconds == 0 ? null : seconds)
        return true
    }

    @Override
    void commit(Xid xid, boolean onePhase) throws XAException {
        // logger.warn("========== TransactionCache.commit(${xid}, ${onePhase}); this.xid=${this.xid}; this.active=${this.active}")
        if (this.active) logger.warn("commit() called without end()")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

        // TODO: is this the best event to do this on? need to be able to throw an exception and mark tx for rollback

        long startTime = System.currentTimeMillis()
        int createCount = 0
        int updateCount = 0
        int deleteCount = 0
        for (EntityWriteInfo ewi in writeInfoList.valueList()) {
            if (ewi.writeMode == WriteMode.CREATE) {
                ewi.evb.basicCreate()
                createCount++
            } else if (ewi.writeMode == WriteMode.UPDATE) {
                ewi.evb.basicUpdate()
                updateCount++
            } else {
                ewi.evb.basicDelete()
                deleteCount++
            }
        }
        if (logger.infoEnabled) logger.info("Wrote from TransactionCache in ${System.currentTimeMillis() - startTime}ms: ${createCount} creates, ${updateCount} updates, ${deleteCount} deletes, ${readCache.size()} read entries")

        this.xid = null
        this.active = false
    }

    @Override
    void rollback(Xid xid) throws XAException {
        // logger.warn("========== TransactionCache.rollback(${xid}); this.xid=${this.xid}; this.active=${this.active}")
        if (this.active) logger.warn("rollback() called without end()")
        if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

        // do nothing on rollback but maintain state

        this.xid = null
        this.active = false
    }

    static class EntityWriteInfo {
        WriteMode writeMode
        EntityValueBase evb
        Map pkMap
        EntityWriteInfo(EntityValueBase evb, WriteMode writeMode) {
            this.evb = evb
            this.writeMode = writeMode
            this.pkMap = evb.getPrimaryKeys()
        }
    }

    static class DeletedEntityValue extends EntityValueImpl {
        DeletedEntityValue(EntityDefinition ed, EntityFacadeImpl efip) { super(ed, efip) }
    }
}
