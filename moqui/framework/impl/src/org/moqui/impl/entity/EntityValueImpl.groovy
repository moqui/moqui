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

import org.moqui.entity.EntityValue
import java.sql.Timestamp
import java.sql.Time
import java.sql.Date
import org.moqui.entity.EntityList
import org.w3c.dom.Element
import org.w3c.dom.Document

class EntityValueImpl implements EntityValue {

    /** This is a reference to where the entity value came from.
     * It is volatile so not stored when this is serialized, and will get a reference to the active EntityFacade after. 
     */
    protected volatile EntityFacadeImpl efi

    protected final String entityName
    protected EntityDefinition entityDefinition

    protected final Map valueMap = new HashMap()
    /* Original DB Value Map: not used unless the value has been modified from its original state from the DB */
    protected final Map dbValueMap = null

    protected boolean modified = false
    protected boolean mutable = true

    protected EntityValueImpl(String entityName, EntityFacadeImpl efi) {
        this.efi = efi
        this.entityName = entityName
        this.entityDefinition = this.efi.getEntityDefinition(entityName)
        if (!this.entityDefinition) {
            throw new IllegalArgumentException("Entity not found for name [${entityName}]")
        }
    }

    /** @see org.moqui.entity.EntityValue#getEntityName() */
    String getEntityName() {
        return this.entityName
    }

    /** @see org.moqui.entity.EntityValue#isModified() */
    boolean isModified() {
        return this.modified
    }

    /** @see org.moqui.entity.EntityValue#isMutable() */
    boolean isMutable() {
        return this.mutable
    }

    /** @see org.moqui.entity.EntityValue#get(String) */
    Object get(String name) {
        // TODO: if the name is not a valid field name, throw an exception
        if (!this.entityDefinition.isField(name)) {
            
        }

        // TODO: if this is not a valid field name but is a valid relationship name, do a getRelated to return an EntityList or an EntityValue

        return this.valueMap[name]
    }

    /** @see org.moqui.entity.EntityValue#containsPrimaryKey() */
    boolean containsPrimaryKey() {
        // TODO implement this
        return false
    }

    /** @see org.moqui.entity.EntityValue#set(String, Object) */
    void set(String name, Object value) {
        if (!this.mutable) throw new IllegalArgumentException("This entity value is not mutable (it is read-only)")

        // TODO: if the name is not a valid field name, throw an exception
        
        this.modified = true
        this.valueMap.put(name, value)
    }

    /** @see org.moqui.entity.EntityValue#setString(String, String) */
    void setString(String name, String value) {
        // TODO implement this
    }

    /** @see org.moqui.entity.EntityValue#getBytes(String) */
    void setBytes(String name, byte[] bytes) {
        // TODO implement this
    }

    /** @see org.moqui.entity.EntityValue#getBoolean(String) */
    Boolean getBoolean(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getString(String) */
    String getString(String name) {
        Object valueObj = this.get(name)
        return valueObj ? valueObj.toString() : null
    }

    /** @see org.moqui.entity.EntityValue#getTimestamp(String) */
    Timestamp getTimestamp(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getTime(String) */
    Time getTime(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getDate(String) */
    Date getDate(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getLong(String) */
    Long getLong(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getDouble(String) */
    Double getDouble(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getBigDecimal(String) */
    BigDecimal getBigDecimal(String name) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#getBytes(String) */
    byte[] getBytes(String name) {
        // TODO implement this
        return new byte[0];
    }

    /** @see org.moqui.entity.EntityValue#setFields(Map<java.lang.String,?>, boolean, java.lang.String, boolean) */
    void setFields(Map<String, ?> fields, boolean setIfEmpty, String namePrefix, Boolean pks) {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#compareTo(EntityValue) */
    int compareTo(EntityValue that) {
        // TODO implement this
        return 0;
    }

    /** @see org.moqui.entity.EntityValue#create() */
    void create() {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#createOrUpdate() */
    void createOrUpdate() {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#update() */
    void update() {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#delete() */
    void delete() {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#refresh() */
    void refresh() {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#getOriginalDbValue(String) */
    Object getOriginalDbValue(String name) {
        if (this.dbValueMap && this.dbValueMap[name]) {
            return this.dbValueMap[name]
        } else {
            return this.valueMap[name]
        }
    }

    /** @see org.moqui.entity.EntityValue#findRelated(String, Map<java.lang.String,?>, java.util.List<java.lang.String>, boolean) */
    EntityList findRelated(String relationshipName, Map<String, ?> byAndFields, List<String> orderBy, Boolean useCache) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#findRelatedOne(String, boolean) */
    EntityValue findRelatedOne(String relationshipName, Boolean useCache) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#deleteRelated(String) */
    void deleteRelated(String relationshipName) {
        // TODO implement this

    }

    /** @see org.moqui.entity.EntityValue#checkFks(boolean) */
    boolean checkFks(boolean insertDummy) {
        // TODO implement this
        return false;
    }

    /** @see org.moqui.entity.EntityValue#makeXmlElement(Document, String) */
    Element makeXmlElement(Document document, String prefix) {
        // TODO implement this
        return null;
    }

    /** @see org.moqui.entity.EntityValue#writeXmlText(PrintWriter, String) */
    void writeXmlText(PrintWriter writer, String prefix) {
        // TODO implement this
    }

    // ========== Map Interface Methods ==========

    /** @see java.util.Map#size() */
    int size() {
        return this.valueMap.size()
    }

    boolean isEmpty() {
        return this.valueMap.isEmpty()
    }

    boolean containsKey(Object o) {
        return this.valueMap.containsKey(o)
    }

    boolean containsValue(Object o) {
        return this.valueMap.containsValue(o)
    }

    Object get(Object o) {
        if (o instanceof String) {
            // This may throw an exception, and let it; the Map interface doesn't provide for IllegalArgumentException
            //   but it is far more useful than a log message that is likely to be ignored.
            return this.get((String) o)
        } else {
            return null
        }
    }

    Object put(String k, Object v) {
        Object original = this.get(k)
        this.set(k, v)
        return original
    }

    Object remove(Object o) {
        this.modified = true
        return this.valueMap.remove(o)
    }

    void putAll(Map<? extends String, ? extends Object> map) {
        for(Map.Entry entry in map.entrySet()) {
            this.set((String) entry.key, entry.value)
        }
    }

    void clear() {
        this.valueMap.clear()
    }

    Set<String> keySet() {
        return this.valueMap.keySet()
    }

    Collection<Object> values() {
        return this.valueMap.values()
    }

    Set<Map.Entry<String, Object>> entrySet() {
        return this.valueMap.entrySet()
    }
}
