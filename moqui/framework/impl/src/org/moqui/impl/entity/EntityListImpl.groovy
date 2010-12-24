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

class EntityListImpl implements EntityList {

    protected EntityFacadeImpl efi

    protected List<EntityValue> valueList = new LinkedList<EntityValue>()

    EntityListImpl(EntityFacadeImpl efi) {
        this.efi = efi
    }

    @Override
    EntityValue getFirst() {
        return this.valueList.get(0)
    }

    @Override
    EntityList filterByDate(Timestamp moment, String fromDateName, String thruDateName) {
        // default to now
        if (!moment) moment = new Timestamp(System.currentTimeMillis())
        if (!fromDateName) fromDateName = "fromDate"
        if (!thruDateName) thruDateName = "thruDate"

        Iterator<EntityValue> valueIterator = this.valueList.iterator()
        while (valueIterator.hasNext()) {
            EntityValue value = valueIterator.next()
            Timestamp fromDate = value.getTimestamp(fromDateName)
            Timestamp thruDate = value.getTimestamp(thruDateName)

            if (!((thruDate == null || thruDate.after(moment)) &&
                    (fromDate == null || fromDate.before(moment) || fromDate.equals(moment)))) {
                valueIterator.remove()
            }
        }

        return this
    }

    @Override
    EntityList filterByAnd(Map<String, ?> fields) {
        return filterByCondition(this.efi.getConditionFactory().makeCondition(fields), true)
    }

    @Override
    EntityList orderByFields(List<String> fieldNames) {
        if (fieldNames) {
            Collections.sort(this.valueList, new EntityOrderByComparator(fieldNames))
        }
        return this
    }

    @Override
    EntityList filterByCondition(EntityCondition condition, Boolean include) {
        Iterator<EntityValue> valueIterator = this.valueList.iterator()
        while (valueIterator.hasNext()) {
            EntityValue value = valueIterator.next()
            if (condition.mapMatches(value)) {
                // matched: if include is not true or false (default exclude) remove it
                if (!include) valueIterator.remove()
            } else {
                // didn't match, if include is true remove it
                if (include) valueIterator.remove()
            }
        }
        return this
    }

    @Override
    Iterator<EntityValue> iterator() {
        return this.valueList.iterator()
    }

    @Override
    public Object clone() {
        return this.cloneList()
    }

    @Override
    public EntityList cloneList() {
        EntityListImpl newObj = new EntityListImpl(this.efi)
        newObj.valueList.addAll(this.valueList)
        return newObj
    }

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
    boolean add(EntityValue e) { return this.valueList.add(e) }

    @Override
    boolean remove(Object o) { return this.valueList.remove(o) }

    @Override
    boolean containsAll(Collection<?> objects) { return this.valueList.containsAll(objects) }

    @Override
    boolean addAll(Collection<? extends EntityValue> es) { return this.valueList.addAll(es) }

    @Override
    boolean addAll(int i, Collection<? extends EntityValue> es) { return this.valueList.addAll(i, es) }

    @Override
    boolean removeAll(Collection<?> objects) { return this.valueList.removeAll(objects) }

    @Override
    boolean retainAll(Collection<?> objects) { return this.valueList.retainAll(objects) }

    @Override
    void clear() { this.valueList.clear() }

    @Override
    EntityValue get(int i) { return this.valueList.get(i) }

    @Override
    EntityValue set(int i, EntityValue e) { return this.valueList.set(i, e) }

    @Override
    void add(int i, EntityValue e) { this.valueList.add(i, e) }

    @Override
    EntityValue remove(int i) { return this.valueList.remove(i) }

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

    public class EntityOrderByComparator implements Comparator<EntityValue> {
        protected List<String> fieldNameList = new ArrayList<String>()

        public EntityOrderByComparator(List<String> fieldNameList) {
            this.fieldNameList = fieldNameList
        }

        @Override
        public int compare(EntityValue entity1, EntityValue entity2) {
            for (String fieldName in this.fieldNameList) {
                boolean ascending = true
                if (fieldName.charAt(0) == '-') {
                    ascending = false
                    fieldName = fieldName.substring(1)
                } else if (fieldName.charAt(0) == '+') {
                    fieldName = fieldName.substring(1)
                }
                Comparable value1 = (Comparable) entity1.get(fieldName)
                Comparable value2 = (Comparable) entity2.get(fieldName)
                // NOTE: nulls go earlier in the list for ascending, later in the list for !ascending
                if (value1 == null) {
                    if (value2 != null) return ascending ? 1 : -1
                } else {
                    if (value2 == null) {
                        return ascending ? -1 : 1
                    } else {
                        int comp = value1.compareTo(value2)
                        if (comp != 0) return ascending ? comp : -comp
                    }
                }
            }
            // all evaluated to 0, so is the same, so return 0
            return 0
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EntityOrderByComparator)) return false
            EntityOrderByComparator that = (EntityOrderByComparator) obj
            return this.fieldNameList.equals(that.fieldNameList)
        }

        @Override
        public String toString() {
            return this.fieldNameList.toString()
        }
    }
}
