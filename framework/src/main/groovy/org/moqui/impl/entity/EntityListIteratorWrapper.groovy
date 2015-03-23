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

import org.apache.commons.collections.set.ListOrderedSet
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityList
import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityValue
import org.moqui.impl.context.TransactionCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EntityListIteratorWrapper implements EntityListIterator {
    protected final static Logger logger = LoggerFactory.getLogger(EntityListIteratorWrapper.class)

    protected EntityFacadeImpl efi
    protected final TransactionCache txCache

    protected List<EntityValue> valueList
    // start out before first
    protected int internalIndex = -1

    protected EntityDefinition entityDefinition
    protected ListOrderedSet fieldsSelected
    protected EntityCondition queryCondition = null
    protected List<String> orderByFields = null

    /** This is needed to determine if the ResultSet is empty as cheaply as possible. */
    protected boolean haveMadeValue = false

    protected boolean closed = false

    EntityListIteratorWrapper(List<EntityValue> valueList, EntityDefinition entityDefinition,
                              ListOrderedSet fieldsSelected, EntityFacadeImpl efi) {
        this.efi = efi
        this.valueList = valueList
        this.entityDefinition = entityDefinition
        this.fieldsSelected = fieldsSelected
        this.txCache = (TransactionCache) efi.getEcfi().getTransactionFacade().getActiveSynchronization("TransactionCache")
    }

    void setQueryCondition(EntityCondition ec) { this.queryCondition = ec }
    void setOrderByFields(List<String> obf) { this.orderByFields = obf }

    @Override
    void close() {
        if (this.closed) {
            logger.warn("EntityListIterator for entity [${this.entityDefinition.getEntityName()}] is already closed, not closing again")
        } else {
            this.closed = true
        }
    }

    @Override
    void afterLast() { this.internalIndex = valueList.size() }

    @Override
    void beforeFirst() { internalIndex = -1 }

    @Override
    boolean last() {
        internalIndex = (valueList.size() - 1)
        return true
    }

    @Override
    boolean first() {
        internalIndex = 0
        return true
    }

    @Override
    EntityValue currentEntityValue() {
        this.haveMadeValue = true
        return valueList.get(internalIndex)
    }

    @Override
    int currentIndex() { return internalIndex }

    @Override
    boolean absolute(int rowNum) {
        internalIndex = rowNum
        return !(internalIndex < 0 || internalIndex >= valueList.size())
    }

    @Override
    boolean relative(int rows) {
        internalIndex += rows
        return !(internalIndex < 0 || internalIndex >= valueList.size())
    }

    @Override
    boolean hasNext() { return internalIndex < (valueList.size() - 1) }

    @Override
    boolean hasPrevious() { return internalIndex > 0 }

    @Override
    EntityValue next() {
        internalIndex++
        if (internalIndex >= valueList.size()) return null
        return currentEntityValue()
    }

    @Override
    int nextIndex() { return internalIndex + 1 }

    @Override
    EntityValue previous() {
        internalIndex--
        if (internalIndex < 0) return null
        return currentEntityValue()
    }

    @Override
    int previousIndex() { return internalIndex - 1 }

    @Override
    void setFetchSize(int rows) { /* do nothing, just ignore */ }

    @Override
    EntityList getCompleteList(boolean closeAfter) {
        try {
            EntityList list = new EntityListImpl(efi)
            EntityValue value
            while ((value = this.next()) != null) {
                list.add(value)
            }

            if (txCache != null && queryCondition != null) {
                // add all created values (updated and deleted values will be handled by the next() method
                List<EntityValueBase> cvList = txCache.getCreatedValueList(entityDefinition.getFullEntityName(), queryCondition)
                list.addAll(cvList)
                // update the order if we know the order by field list
                if (orderByFields != null && cvList) list.orderByFields(orderByFields)
            }

            return list
        } finally {
            if (closeAfter) close()
        }
    }

    @Override
    EntityList getPartialList(int offset, int limit, boolean closeAfter) {
        try {
            EntityList list = new EntityListImpl(this.efi)
            if (limit == 0) return list

            // jump to start index, or just get the first result
            if (!this.absolute(offset)) {
                // not that many results, get empty list
                return list
            }

            // get the first as the current one
            list.add(this.currentEntityValue())

            int numberSoFar = 1
            EntityValue nextValue = null
            while (limit > numberSoFar && (nextValue = this.next()) != null) {
                list.add(nextValue)
                numberSoFar++
            }
            return list
        } finally {
            if (closeAfter) close()
        }
    }

    @Override
    int writeXmlText(Writer writer, String prefix, int dependentLevels) {
        int recordsWritten = 0
        // move back to before first if we need to
        if (haveMadeValue && internalIndex != -1) internalIndex = -1
        EntityValue value
        while ((value = this.next()) != null) {
            recordsWritten += value.writeXmlText(writer, prefix, dependentLevels)
        }
        return recordsWritten
    }

    @Override
    Iterator<EntityValue> iterator() { return this }

    @Override
    void remove() {
        throw new IllegalArgumentException("EntityListIteratorWrapper.remove() not currently supported")
        // TODO implement this
        // TODO: call EECAs
        // TODO: notify cache clear
    }

    @Override
    void set(EntityValue e) {
        throw new IllegalArgumentException("EntityListIteratorWrapper.set() not currently supported")
        // TODO implement this
        // TODO: call EECAs
        // TODO: notify cache clear
    }

    @Override
    void add(EntityValue e) {
        throw new IllegalArgumentException("EntityListIteratorWrapper.add() not currently supported")
        // TODO implement this
    }

    @Override
    protected void finalize() throws Throwable {
        if (!closed) {
            this.close()
            logger.error("EntityListIteratorWrapper not closed for entity [${entityDefinition.getEntityName()}], caught in finalize()")
        }
        Object.finalize()
    }
}
