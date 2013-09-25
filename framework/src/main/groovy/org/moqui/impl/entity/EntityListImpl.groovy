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

import java.sql.Timestamp

import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityCondition
import org.moqui.impl.StupidUtilities.MapOrderByComparator
import org.moqui.entity.EntityException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EntityListImpl implements EntityList {
    protected final static Logger logger = LoggerFactory.getLogger(EntityConditionFactoryImpl.class)
    public static final EntityList EMPTY = new EmptyEntityList()

    protected EntityFacadeImpl efi

    protected List<EntityValue> valueList = new LinkedList<EntityValue>()
    protected boolean fromCache = false

    EntityListImpl(EntityFacadeImpl efi) { this.efi = efi }

    @Override
    EntityValue getFirst() { return valueList ? valueList.get(0) : null }

    @Override
    EntityList filterByDate(String fromDateName, String thruDateName, Timestamp moment) {
        if (fromCache) return this.cloneList().filterByDate(fromDateName, thruDateName, moment)

        // default to now
        Long momentLong = moment ? moment.getTime() : System.currentTimeMillis()
        if (!fromDateName) fromDateName = "fromDate"
        if (!thruDateName) thruDateName = "thruDate"

        Iterator<EntityValue> valueIterator = this.valueList.iterator()
        while (valueIterator.hasNext()) {
            EntityValue value = valueIterator.next()
            Object fromDateObj = value.get(fromDateName)
            Object thruDateObj = value.get(thruDateName)

            Long fromDateLong
            Long thruDateLong

            if (fromDateObj instanceof Date) fromDateLong = fromDateObj.getTime()
            else if (fromDateObj instanceof Long) fromDateLong = fromDateObj
            else fromDateLong = null

            if (thruDateObj instanceof Date) thruDateLong = thruDateObj.getTime()
            else if (thruDateObj instanceof Long) thruDateLong = thruDateObj
            else thruDateLong = null

            if (!((thruDateLong == null || thruDateLong > momentLong) &&
                    (fromDateLong == null || fromDateLong <= momentLong))) {
                valueIterator.remove()
            }
        }

        return this
    }

    @Override
    EntityList filterByAnd(Map<String, ?> fields) {
        if (fromCache) return this.cloneList().filterByAnd(fields)
        return filterByCondition(this.efi.getConditionFactory().makeCondition(fields), true)
    }

    @Override
    EntityList orderByFields(List<String> fieldNames) {
        if (fromCache) return this.cloneList().orderByFields(fieldNames)
        if (fieldNames) Collections.sort(this.valueList, new MapOrderByComparator(fieldNames))
        return this
    }

    @Override
    EntityList filterByCondition(EntityCondition condition, Boolean include) {
        if (fromCache) return this.cloneList().filterByCondition(condition, include)
        if (include == null) include = true
        Iterator<EntityValue> valueIterator = this.valueList.iterator()
        while (valueIterator.hasNext()) {
            EntityValue value = valueIterator.next()
            boolean matches = condition.mapMatches(value)
            // logger.warn("TOREMOVE filter value [${value}] with condition [${condition}] include=${include}, matches=${matches}")
            // matched: if include is not true or false (default exclude) remove it
            // didn't match, if include is true remove it
            if ((matches && !include) || (!matches && include)) valueIterator.remove()
        }
        return this
    }

    @Override
    int writeXmlText(Writer writer, String prefix, boolean dependents) {
        int recordsWritten = 0
        for (EntityValue ev in this) recordsWritten += ev.writeXmlText(writer, prefix, dependents)
        return recordsWritten
    }

    @Override
    Iterator<EntityValue> iterator() { return this.valueList.iterator() }

    @Override
    Object clone() { return this.cloneList() }

    @Override
    EntityList cloneList() {
        EntityListImpl newObj = new EntityListImpl(this.efi)
        newObj.valueList.addAll(this.valueList)
        // NOTE: when cloning don't clone the fromCache value (normally when from cache will be cloned before filtering)
        return newObj
    }

    void setFromCache(boolean fc) { fromCache = fc }
    boolean isFromCache() { return fromCache }

    // ========== List Interface Methods ==========

    @Override
    int size() { return this.valueList.size() }

    @Override
    boolean isEmpty() { return this.valueList.isEmpty() }

    @Override
    boolean contains(Object o) { return this.valueList.contains(o) }

    @Override
    Object[] toArray() { return this.valueList.toArray() }

    @Override
    Object[] toArray(Object[] ts) { return this.valueList.toArray((EntityValue[]) ts) }

    @Override
    boolean add(EntityValue e) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.add(e)
    }

    @Override
    boolean remove(Object o) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.remove(o)
    }

    @Override
    boolean containsAll(Collection<?> objects) { return this.valueList.containsAll(objects) }

    @Override
    boolean addAll(Collection<? extends EntityValue> es) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.addAll(es)
    }

    @Override
    boolean addAll(int i, Collection<? extends EntityValue> es) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.addAll(i, es)
    }

    @Override
    boolean removeAll(Collection<?> objects) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.removeAll(objects)
    }

    @Override
    boolean retainAll(Collection<?> objects) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.retainAll(objects)
    }

    @Override
    void clear() {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        this.valueList.clear()
    }

    @Override
    EntityValue get(int i) { return this.valueList.get(i) }

    @Override
    EntityValue set(int i, EntityValue e) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.set(i, e)
    }

    @Override
    void add(int i, EntityValue e) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        this.valueList.add(i, e)
    }

    @Override
    EntityValue remove(int i) {
        if (fromCache) throw new EntityException("Cannot modify EntityList from cache")
        return this.valueList.remove(i)
    }

    @Override
    int indexOf(Object o) { return this.valueList.indexOf(o) }

    @Override
    int lastIndexOf(Object o) { return this.valueList.lastIndexOf(o) }

    @Override
    ListIterator<EntityValue> listIterator() { return this.valueList.listIterator() }

    @Override
    ListIterator<EntityValue> listIterator(int i) { return this.valueList.listIterator(i) }

    @Override
    List<EntityValue> subList(int start, int end) { return this.valueList.subList(start, end) }

    @Override
    String toString() { this.valueList.toString() }

    static class EmptyEntityList implements EntityList {
        static final ListIterator emptyIterator = new LinkedList().listIterator()

        EmptyEntityList() { }

        EntityValue getFirst() { return null }
        EntityList filterByDate(String fromDateName, String thruDateName, Timestamp moment) { return this }
        EntityList filterByAnd(Map<String, ?> fields) { return this }
        EntityList orderByFields(List<String> fieldNames) { return this }
        EntityList filterByCondition(EntityCondition condition, Boolean include) { return this }
        Iterator<EntityValue> iterator() { return emptyIterator }
        Object clone() { return this.cloneList() }
        int writeXmlText(Writer writer, String prefix, boolean dependents) { return 0 }

        EntityList cloneList() { return this }
        void setFromCache(boolean fc) { }
        boolean isFromCache() { return false }

        // ========== List Interface Methods ==========
        int size() { return 0 }
        boolean isEmpty() { return true }
        boolean contains(Object o) { return false }
        Object[] toArray() { return new Object[0] }
        Object[] toArray(Object[] ts) { return new Object[0] }
        boolean add(EntityValue e) { throw new IllegalArgumentException("EmptyEntityList does not support add") }
        boolean remove(Object o) { return false }
        boolean containsAll(Collection<?> objects) { return false }
        boolean addAll(Collection<? extends EntityValue> es) { throw new IllegalArgumentException("EmptyEntityList does not support addAll") }
        boolean addAll(int i, Collection<? extends EntityValue> es) { throw new IllegalArgumentException("EmptyEntityList does not support addAll") }
        boolean removeAll(Collection<?> objects) { return false }
        boolean retainAll(Collection<?> objects) { return false }
        void clear() { }
        EntityValue get(int i) { return null }
        EntityValue set(int i, EntityValue e) { throw new IllegalArgumentException("EmptyEntityList does not support set") }
        void add(int i, EntityValue e) { throw new IllegalArgumentException("EmptyEntityList does not support add") }
        EntityValue remove(int i) { return null }
        int indexOf(Object o) { return -1 }
        int lastIndexOf(Object o) { return -1 }
        ListIterator<EntityValue> listIterator() { return emptyIterator }
        ListIterator<EntityValue> listIterator(int i) { return emptyIterator }
        List<EntityValue> subList(int start, int end) { return this }
        String toString() { return "[]" }
    }
}
