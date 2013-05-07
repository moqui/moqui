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
package org.moqui.impl.entity

import org.moqui.entity.EntityException
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ExecutionContextFactoryImpl

import javax.transaction.Status
import javax.transaction.Transaction
import javax.transaction.TransactionManager
import javax.transaction.xa.XAException
import javax.transaction.xa.XAResource
import javax.transaction.xa.Xid

class EntityDataFeed {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityDataFeed.class)

    protected EntityFacadeImpl efi

    EntityDataFeed(EntityFacadeImpl efi) {
        this.efi = efi
    }

    EntityFacadeImpl getEfi() { return efi }

    /* Notes:
    - doing update on entity have entityNames updated, for each fieldNames updated, field values (query as needed based
        on actual conditions if any conditions on fields not present in EntityValue
    - do this based on a committed transaction of changes, not just on a single record... if possible
    - if can't do on transaction commit, keep data for documents to include until transaction committed
    - either way only do this on a successful commit, not just on the create/update calls

    to quickly lookup DataDocuments updated with a corresponding real time (DTFDTP_RT_PUSH) DataFeed need:
    - don't have to constrain by real time DataFeed, will be done in advance for index
    - Map with entityName as key
    - value is List of Map with:
      - dataFeedId
      - List of dataDocumentInfo Maps with
        - dataDocumentId
        - Set of fields for DataDocument and the current entity
        - primaryEntityName
        - relationship path from primary to current entity
        - Map of field conditions for current entity - and for entire document? TODO
    - find with query on DataFeed and DataFeedDocument where DataFeed.dataFeedTypeEnumId=DTFDTP_RT_PUSH
      - iterate through dataDocumentId and call getDataDocumentEntityInfo() for each

    to produce the document with zero or minimal query
    - during transaction save all created or updated records in EntityList updatedList (in EntityFacadeImpl?)
    - EntityValues added to the list only if they are in the

    - once we have dataDocumentIdSet use to lookup all DTFDTP_RT_PUSH DataFeed with a matching DataFeedDocument record
    - look up primary entity value for the current updated value and use its PK fields as a condition to call
        getDataDocuments() so that we get a document for just the updated record(s)

     */

    void dataFeedCheckAndRegister(EntityValue ev) {
        List<Map> entityInfoList = getDataFeedEntityInfoList(ev.getEntityName())
        // TODO see if this should be added to the feed

        DataFeedXaResource dfxr = (DataFeedXaResource) efi.getEcfi().getTransactionFacade().getActiveXaResource("DataFeedXaResource")
        dfxr.addValueToFeed(ev, null) // TODO: populate and pass the dataDocumentIdSet, and/or other things needed
    }

    List<Map> getDataFeedEntityInfoList(String fullEntityName) {
        List<Map> entityInfoList = (List<Map>) dataFeedEntityInfo.get(fullEntityName)
        if (entityInfoList == null) {
            // TODO: rebuild from the DB for this and other entities, ie have to do it for all DataFeeds and
            //     DataDocuments because we can't query it by entityName

        }
        return entityInfoList
    }

    Map<String, Map> getDataDocumentEntityInfo(String dataDocumentId) {
        EntityValue dataDocument = efi.makeFind("moqui.entity.document.DataDocument")
                .condition("dataDocumentId", dataDocumentId).useCache(true).one()
        if (dataDocument == null) throw new EntityException("No DataDocument found with ID [${dataDocumentId}]")
        EntityList dataDocumentFieldList = dataDocument.findRelated("moqui.entity.document.DataDocumentField", null, null, true, false)
        EntityList dataDocumentConditionList = dataDocument.findRelated("moqui.entity.document.DataDocumentCondition", null, null, true, false)

        String primaryEntityName = dataDocument.primaryEntityName
        EntityDefinition primaryEd = efi.getEntityDefinition(primaryEntityName)

        Map<String, Map> entityInfoMap = [:]

        // start with the primary entity
        entityInfoMap.put(primaryEntityName, [primaryEntityName:primaryEntityName, relationshipPath:""])

        // have to go through entire fieldTree instead of entity names directly from fieldPath because may not have hash (#) separator
        Map<String, Object> fieldTree = [:]
        for (EntityValue dataDocumentField in dataDocumentFieldList) {
            String fieldPath = dataDocumentField.fieldPath
            Iterator<String> fieldPathElementIter = fieldPath.split(":").iterator()
            Map currentTree = fieldTree
            Map currentEntityInfo = entityInfoMap.get(primaryEntityName)
            StringBuilder currentRelationshipPath = new StringBuilder()
            EntityDefinition currentEd = primaryEd
            while (fieldPathElementIter.hasNext()) {
                String fieldPathElement = fieldPathElementIter.next()
                if (fieldPathElementIter.hasNext()) {
                    if (currentRelationshipPath.length() > 0) currentRelationshipPath.append(":")
                    currentRelationshipPath.append(fieldPathElement)

                    Map subTree = (Map) currentTree.get(fieldPathElement)
                    if (subTree == null) { subTree = [:]; currentTree.put(fieldPathElement, subTree) }
                    currentTree = subTree

                    // make sure we have an entityInfo Map
                    Node relNode = currentEd.getRelationshipNode(fieldPathElement)
                    if (relNode == null) throw new EntityException("Could not find relationship [${fieldPathElement}] from entity [${currentEd.getFullEntityName()}] as part of DataDocumentField.fieldPath [${fieldPath}]")
                    String relEntityName = relNode."@related-entity-name"
                    if (!entityInfoMap.containsKey(relEntityName)) entityInfoMap.put(relEntityName,
                            [primaryEntityName:primaryEntityName, relationshipPath:currentRelationshipPath.toString()])
                    currentEntityInfo = entityInfoMap.get(relEntityName)
                    currentEd = efi.getEntityDefinition(relEntityName)
                } else {
                    currentTree.put(fieldPathElement, dataDocumentField.fieldNameAlias ?: fieldPathElement)
                    // save the current field name (not the alias)
                    StupidUtilities.addToSetInMap("fields", fieldPathElement, currentEntityInfo)
                    // see if there are any conditions for this alias, if so add the fieldName/value to the entity conditions Map
                    for (EntityValue dataDocumentCondition in dataDocumentConditionList) {
                        if (dataDocumentCondition.fieldNameAlias == dataDocumentField.fieldNameAlias) {
                            Map conditions = (Map) currentEntityInfo.get("conditions")
                            if (conditions == null) {
                                conditions = [:]
                                currentEntityInfo.put("conditions", conditions)
                            }
                            conditions.put(fieldPathElement, dataDocumentCondition.fieldValue)
                        }
                    }
                }
            }
        }
        logger.warn("============ got entityInfoMap: ${entityInfoMap}\n============ for fieldTree: ${fieldTree}")

        return entityInfoMap
    }

    static class DataFeedXaResource implements XAResource {
        protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DataFeedXaResource.class)

        protected ExecutionContextFactoryImpl ecfi
        protected EntityDataFeed edf

        protected Transaction tx = null
        protected Xid xid = null
        protected Integer timeout = null
        protected boolean active = false

        protected EntityList feedValues
        protected Set<String> allDataDocumentIds = new HashSet<String>()

        DataFeedXaResource(EntityDataFeed edf) {
            this.edf = edf
            ecfi = edf.getEfi().getEcfi()
            feedValues = new EntityListImpl(edf.getEfi())
        }

        Xid getXid() { return xid }

        void enlist() {
            TransactionManager tm = ecfi.getTransactionFacade().getTransactionManager()
            if (tm == null && tm.getStatus() != Status.STATUS_ACTIVE) throw new XAException("Cannot enlist: no transaction manager or transaction not active")
            Transaction tx = tm.getTransaction()
            if (tx == null) throw new XAException(XAException.XAER_NOTA)
            this.tx = tx

            ecfi.getTransactionFacade().putAndEnlistActiveXaResource("DataFeedXaResource", this)
        }

        void addValueToFeed(EntityValue ev, Set<String> dataDocumentIdSet) {
            feedValues.add(ev)
            allDataDocumentIds.addAll(dataDocumentIdSet)
        }

        @Override
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
        }

        @Override
        void end(Xid xid, int flag) throws XAException {
            if (!this.active) throw new XAException(XAException.XAER_PROTO)
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
            this.active = false
        }

        @Override
        void forget(Xid xid) throws XAException {
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
            this.xid = null
            if (active) logger.warn("forget() called without end()")
        }

        @Override
        int prepare(Xid xid) throws XAException {
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)
            return XA_OK
        }

        @Override
        Xid[] recover(int flag) throws XAException { return this.xid != null ? [this.xid] : [] }
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
            if (this.active) logger.warn("commit() called without end()")
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

            // send feed in new thread and tx
            feedInThreadAndTx()

            this.xid = null
            this.active = false
        }

        @Override
        void rollback(Xid xid) throws XAException {
            if (this.active) logger.warn("rollback() called without end()")
            if (this.xid == null || !this.xid.equals(xid)) throw new XAException(XAException.XAER_NOTA)

            // do nothing on rollback but maintain state

            this.xid = null
            this.active = false
        }

        void feedInThreadAndTx() {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    boolean beganTransaction = ecfi.transactionFacade.begin(null)
                    try {
                        // TODO assemble data and call DataFeed services
                    } catch (Throwable t) {
                        logger.error("Error running Real-time DataFeed", t)
                        ecfi.transactionFacade.rollback(beganTransaction, "Error running Real-time DataFeed", t)
                    } finally {
                        if (beganTransaction && ecfi.transactionFacade.isTransactionInPlace())
                            ecfi.transactionFacade.commit()
                    }
                }
            };
            thread.start();
        }
    }
}
