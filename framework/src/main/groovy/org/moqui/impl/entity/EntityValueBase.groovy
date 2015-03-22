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

import org.apache.commons.codec.binary.Base64
import org.apache.commons.collections.set.ListOrderedSet

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.TransactionCache
import org.w3c.dom.Document
import org.w3c.dom.Element

import java.sql.Connection
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import javax.sql.rowset.serial.SerialBlob

import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class EntityValueBase implements EntityValue {
    protected final static Logger logger = LoggerFactory.getLogger(EntityValueBase.class)

    /** This is a reference to where the entity value came from.
     * It is volatile so not stored when this is serialized, and will get a reference to the active EntityFacade after.
     */
    protected volatile EntityFacadeImpl efi
    protected volatile TransactionCache txCache


    protected final String entityName
    protected volatile EntityDefinition entityDefinition

    private final Map<String, Object> valueMap = [:]
    /* Original DB Value Map: not used unless the value has been modified from its original state from the DB */
    private Map<String, Object> dbValueMap = null
    private Map<String, Object> internalPkMap = null
    /* Used to keep old field values such as before an update or other sync with DB; mostly useful for EECA rules */
    private Map<String, Object> oldDbValueMap = null

    protected boolean modified = false
    protected boolean pkModified = false
    protected boolean mutable = true
    protected boolean isFromDb = false

    EntityValueBase(EntityDefinition ed, EntityFacadeImpl efip) {
        efi = efip
        entityName = ed.getFullEntityName()
        entityDefinition = ed
    }

    EntityFacadeImpl getEntityFacadeImpl() {
        // handle null after deserialize; this requires a static reference in Moqui.java or we'll get an error
        if (efi == null) efi = ((ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()).getEntityFacade()
        return efi
    }
    TransactionCache getTxCache() {
        if (txCache == null) txCache = (TransactionCache) efi.getEcfi().getTransactionFacade().getActiveSynchronization("TransactionCache")
        return txCache
    }

    EntityDefinition getEntityDefinition() {
        if (entityDefinition == null) entityDefinition = getEntityFacadeImpl().getEntityDefinition(entityName)
        return entityDefinition
    }

    // NOTE: this is no longer protected so that external add-on code can set original values from a datasource
    Map<String, Object> getValueMap() { return valueMap }
    protected Map<String, Object> getDbValueMap() { return dbValueMap }
    protected void setDbValueMap(Map<String, Object> map) { dbValueMap = map; isFromDb = true }
    protected Map<String, Object> getOldDbValueMap() { return oldDbValueMap }

    void setSyncedWithDb() { oldDbValueMap = dbValueMap; dbValueMap = null; modified = false; isFromDb = true }
    boolean getIsFromDb() { return isFromDb }

    @Override
    String getEntityName() { return entityName }

    @Override
    boolean isModified() { return modified }

    @Override
    boolean isFieldModified(String name) {
        return dbValueMap && dbValueMap.containsKey(name) && dbValueMap.get(name) != valueMap.get(name)
    }

    @Override
    boolean isFieldSet(String name) { return valueMap.containsKey(name) }

    @Override
    boolean isMutable() { return mutable }

    @Override
    Map getMap() { return new HashMap(valueMap) }

    @Override
    Object get(String name) {
        EntityDefinition ed = getEntityDefinition()

        // if this is a simple field (is field, no l10n, not user field) just get the value right away (vast majority of use)
        if (ed.isSimpleField(name)) return valueMap.get(name)

        Node fieldNode = ed.getFieldNode(name)

        if (fieldNode == null) {
            // if this is not a valid field name but is a valid relationship name, do a getRelated or getRelatedOne to return an EntityList or an EntityValue
            Node relationship = ed.getRelationshipNode(name)
            if (relationship != null) {
                if (relationship."@type" == "many") {
                    return this.findRelated(name, null, null, null, null)
                } else {
                    return this.findRelatedOne(name, null, null)
                }
            } else {
                throw new EntityException("The name [${name}] is not a valid field name or relationship name for entity [${entityName}]")
            }
        }

        // if enabled use moqui.basic.LocalizedEntityField for any localized fields
        if (fieldNode."@enable-localization" == "true") {
            String localeStr = getEntityFacadeImpl().ecfi.getExecutionContext().getUser().getLocale()?.toString()
            if (localeStr) {
                List<String> pks = ed.getPkFieldNames()
                if (pks.size() == 1) {
                    String pkValue = get(pks.get(0))
                    if (pkValue) {
                        EntityFind lefFind = getEntityFacadeImpl().find("moqui.basic.LocalizedEntityField")
                        lefFind.condition([entityName:ed.getFullEntityName(), fieldName:name, pkValue:pkValue, locale:localeStr])
                        EntityValue lefValue = lefFind.useCache(true).one()
                        if (lefValue) return lefValue.localized
                        // no result found, try with shortened locale
                        if (localeStr.contains("_")) {
                            lefFind.condition("locale", localeStr.substring(0, localeStr.indexOf("_")))
                            lefValue = lefFind.useCache(true).one()
                            if (lefValue) return lefValue.localized
                        }
                    }
                }
                // no luck? try getting a localized value from moqui.basic.LocalizedMessage
                EntityFind lmFind = getEntityFacadeImpl().find("moqui.basic.LocalizedMessage")
                lmFind.condition([original:valueMap.get(name), locale:localeStr])
                EntityValue lmValue = lmFind.useCache(true).one()
                if (lmValue) return lmValue.localized
            }
        }

        if (fieldNode."@is-user-field" == "true") {
            // get if from the UserFieldValue entity instead
            Map<String, Object> parms = [entityName: ed.getFullEntityName(), fieldName: name]
            addThreeFieldPkValues(parms)

            boolean alreadyDisabled = efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz()
            try {
                Set<String> userGroupIdSet = efi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet()
                EntityList userFieldValueList = efi.find("moqui.entity.UserFieldValue")
                        .condition("userGroupId", EntityCondition.IN, userGroupIdSet)
                        .condition(parms).list()
                if (userFieldValueList) {
                    // do type conversion according to field type
                    return ed.convertFieldString(name, (String) userFieldValueList[0].valueText)
                }
            } finally {
                if (!alreadyDisabled) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
            }
        }

        return valueMap.get(name)
    }

    @Override
    Object getOriginalDbValue(String name) {
        return (dbValueMap && dbValueMap.containsKey(name)) ? dbValueMap.get(name) : valueMap.get(name)
    }
    Object getOldDbValue(String name) {
        if (oldDbValueMap && oldDbValueMap.containsKey(name)) return oldDbValueMap.get(name)
        return getOriginalDbValue(name)
    }


    @Override
    boolean containsPrimaryKey() { return this.getEntityDefinition().containsPrimaryKey(valueMap) }

    @Override
    Map<String, Object> getPrimaryKeys() {
        if (internalPkMap != null) return (Map<String, Object>) internalPkMap.clone()
        Map<String, Object> pks = new HashMap()
        for (String fieldName in this.getEntityDefinition().getPkFieldNames()) {
            // only include PK fields which has a non-empty value, leave others out of the Map
            Object value = valueMap.get(fieldName)
            if (value) pks.put(fieldName, value)
        }
        internalPkMap = pks
        return (Map<String, Object>) internalPkMap.clone()
    }

    @Override
    EntityValue set(String name, Object value) {
        if (!mutable) throw new EntityException("Cannot set field [${name}], this entity value is not mutable (it is read-only)")
        if (!getEntityDefinition().isField(name)) {
            throw new EntityException("The name [${name}] is not a valid field name for entity [${entityName}]")
        }
        if (valueMap.get(name) != value) {
            modified = true
            if (entityDefinition.isPkField(name)) pkModified = true
            if (valueMap.containsKey(name)) {
                if (dbValueMap == null) dbValueMap = [:]
                dbValueMap.put(name, valueMap.get(name))
            }
        }
        valueMap.put(name, value)
        return this
    }

    @Override
    EntityValue setAll(Map<String, ?> fields) {
        entityDefinition.setFields(fields, this, true, null, null)
        return this
    }

    @Override
    EntityValue setString(String name, String value) { entityDefinition.setString(name, value, this); return this }

    @Override
    Boolean getBoolean(String name) { return this.get(name) as Boolean }

    @Override
    String getString(String name) {
        Object valueObj = this.get(name)
        return entityDefinition.getFieldString(name, valueObj)
    }

    @Override
    Timestamp getTimestamp(String name) { return (Timestamp) this.get(name)?.asType(Timestamp.class) }

    @Override
    Time getTime(String name) { return this.get(name) as Time }

    @Override
    Date getDate(String name) { return this.get(name) as Date }

    @Override
    Long getLong(String name) { return this.get(name) as Long }

    @Override
    Double getDouble(String name) { return this.get(name) as Double }

    @Override
    BigDecimal getBigDecimal(String name) { return this.get(name) as BigDecimal }

    byte[] getBytes(String name) {
        Object o = this.get(name)
        if (o == null) return null
        if (o instanceof SerialBlob) {
            if (((SerialBlob) o).length() == 0) return new byte[0]
            return ((SerialBlob) o).getBytes(1, (int) o.length())
        }
        if (o instanceof byte[]) return o
        // try groovy...
        return o as byte[]
    }
    EntityValue setBytes(String name, byte[] theBytes) {
        if (theBytes != null) set(name, new SerialBlob(theBytes))
        return this
    }

    SerialBlob getSerialBlob(String name) {
        Object o = this.get(name)
        if (o == null) return null
        if (o instanceof SerialBlob) return o
        if (o instanceof byte[]) return new SerialBlob((byte[]) o)
        // try groovy...
        return o as SerialBlob
    }

    @Override
    EntityValue setFields(Map<String, ?> fields, boolean setIfEmpty, String namePrefix, Boolean pks) {
        entityDefinition.setFields(fields, this, setIfEmpty, namePrefix, pks)
        return this
    }

    @Override
    EntityValue setSequencedIdPrimary() {
        List<String> pkFields = getEntityDefinition().getPkFieldNames()
        Node entityNode = getEntityDefinition().getEntityNode()
        Long staggerMax = (entityNode."@sequence-primary-stagger" as Long) ?: 1
        Long bankSize = (entityNode."@sequence-bank-size" as Long) ?: 50

        // get the entity-specific prefix, support string expansion for it too
        String entityPrefix = ""
        String rawPrefix = getEntityDefinition().getEntityNode()?."@sequence-primary-prefix"
        if (rawPrefix) entityPrefix = efi.getEcfi().getResourceFacade().evaluateStringExpand(rawPrefix, null, valueMap)

        set(pkFields.get(0), entityPrefix + getEntityFacadeImpl().sequencedIdPrimary(getEntityName(), staggerMax, bankSize))
        return this
    }

    @Override
    EntityValue setSequencedIdSecondary() {
        List<String> pkFields = getEntityDefinition().getPkFieldNames()
        if (pkFields.size() < 2) throw new EntityException("Cannot call setSequencedIdSecondary() on entity [${getEntityName()}], there are not at least 2 primary key fields.")
        // sequenced field will be the last pk
        String seqFieldName = pkFields.get(pkFields.size()-1)
        int paddedLength  = (getEntityDefinition().entityNode."@sequence-secondary-padded-length" as Integer) ?: 2

        this.remove(seqFieldName)
        EntityValue lookupValue = getEntityFacadeImpl().makeValue(getEntityName())
        lookupValue.setFields(this, false, null, true)

        // temporarily disable authz for this, just doing lookup to get next value and to allow for a
        //     authorize-skip="create" with authorize-skip of view too this is necessary
        List<EntityValue> allValues = null
        boolean alreadyDisabled = this.getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().disableAuthz()
        try {
            // NOTE: DEJ 2012-10-11 Added the call to getPrimaryKeys() even though the setFields() call above is only
            //     supposed to move over PK fields; somehow a bunch of other fields were getting set to null, causing
            //     null constraints for those fields; based on debug logging this happened somewhere after the last
            //     line of the EntityValueBase.put() method (according to a log statement there) and a log statement
            //     after the line that calls put() in the EntityDefinition.setString() method; theory is that groovy
            //     is doing something that results in fields getting set to null, probably a call to a method on
            //     EntityValueBase or EntityValueImpl that is not expected to be called
            EntityFind ef = getEntityFacadeImpl().find(getEntityName()).condition(lookupValue.getPrimaryKeys())
            // logger.warn("TOREMOVE in setSequencedIdSecondary ef WHERE=${ef.getWhereEntityCondition()}")
            allValues = ef.list()
        } finally {
            if (!alreadyDisabled) this.getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().enableAuthz()
        }

        Integer highestSeqVal = null
        for (EntityValue curValue in allValues) {
            String currentSeqId = curValue.getString(seqFieldName)
            if (currentSeqId) {
                try {
                    int seqVal = Integer.parseInt(currentSeqId)
                    if (highestSeqVal == null || seqVal > highestSeqVal) highestSeqVal = seqVal
                } catch (Exception e) {
                    logger.warn("Error in secondary sequenced ID converting SeqId [${currentSeqId}] in field [${seqFieldName}] from entity [${getEntityName()}] to a number: ${e.toString()}")
                }
            }
        }

        int seqValToUse = (highestSeqVal ? highestSeqVal+1 : 1)
        this.set(seqFieldName, StupidUtilities.paddedNumber(seqValToUse, paddedLength))
        return this
    }

    @Override
    int compareTo(EntityValue that) {
        // nulls go earlier
        if (that == null) return -1

        // first entity names
        int result = this.entityName.compareTo(that.getEntityName())
        if (result != 0) return result

        // next compare PK fields
        for (String pkFieldName in this.getEntityDefinition().getPkFieldNames()) {
            result = compareFields(that, pkFieldName)
            if (result != 0) return result
        }
        // then non-PK fields
        for (String fieldName in this.getEntityDefinition().getFieldNames(false, true, true)) {
            result = compareFields(that, fieldName)
            if (result != 0) return result
        }

        // all the same, result should be 0
        return result
    }

    protected int compareFields(EntityValue that, String name) {
        Comparable thisVal = (Comparable) this.valueMap.get(name)
        Object thatVal = that.get(name)
        // NOTE: nulls go earlier in the list
        if (thisVal == null) {
            return thatVal == null ? 0 : 1
        } else {
            return thatVal == null ? -1 : thisVal.compareTo(thatVal)
        }
    }

    @Override
    boolean mapMatches(Map<String, Object> theMap) {
        boolean matches = true
        for (Map.Entry entry in theMap.entrySet())
            if (entry.getValue() != this.valueMap.get(entry.getKey())) { matches = false; break }
        return matches
    }

    @Override
    EntityValue createOrUpdate() {
        if ((isFromDb && !pkModified) || this.cloneValue().refresh()) {
            return update()
        } else {
            return create()
        }
    }
    @Override
    EntityValue store() { return createOrUpdate() }

    void handleAuditLog(boolean isUpdate, Map oldValues) {
        if (isUpdate && oldValues == null) return

        EntityDefinition ed = getEntityDefinition()
        if (!ed.needsAuditLog()) return

        ExecutionContext ec = getEntityFacadeImpl().getEcfi().getExecutionContext()
        Timestamp nowTimestamp = ec.getUser().getNowTimestamp()

        Map<String, Object> pksValueMap = new HashMap<String, Object>()
        addThreeFieldPkValues(pksValueMap)

        for (Node fieldNode in ed.getFieldNodes(true, true, true)) {
            if (ed.getFieldAuditLog(fieldNode) == "true" || (isUpdate && ed.getFieldAuditLog(fieldNode) == "update")) {
                String fieldName = fieldNode."@name"

                // is there a new value? if not continue
                if (!this.valueMap.containsKey(fieldName)) continue

                Object value = get(fieldName)
                Object oldValue = oldValues?.get(fieldName)

                // if isUpdate but old value == new value, then it hasn't been updated, so skip it
                if (isUpdate && value == oldValue) continue
                // if it's a create and there is no value don't log a change
                if (!isUpdate && value == null) continue

                // don't skip for this, if a field was reset then we want to record that: if (!value) continue

                String stackNameString = ec.artifactExecution.getStackNameString()
                if (stackNameString.length() > 4000) stackNameString = stackNameString.substring(0, 4000)
                Map<String, Object> parms = (Map<String, Object>) [changedEntityName:getEntityName(),
                        changedFieldName:fieldName, newValueText:(value as String), changedDate:nowTimestamp,
                        changedByUserId:ec.getUser().getUserId(), changedInVisitId:ec.getUser().getVisitId(),
                        artifactStack:stackNameString]
                parms.oldValueText = oldValue
                parms.putAll(pksValueMap)

                // logger.warn("TOREMOVE: in handleAuditLog for [${ed.entityName}.${fieldName}] value=[${value}], oldValue=[${oldValue}], oldValues=[${oldValues}]", new Exception("AuditLog location"))

                // NOTE: if this is changed to async the time zone on nowTimestamp gets messed up (user's time zone lost)
                getEntityFacadeImpl().getEcfi().getServiceFacade().sync().name("create#moqui.entity.EntityAuditLog").parameters(parms).call()
            }
        }
    }

    protected void addThreeFieldPkValues(Map<String, Object> parms) {
        EntityDefinition ed = getEntityDefinition()

        // get pkPrimaryValue, pkSecondaryValue, pkRestCombinedValue (just like the AuditLog stuff)
        ListOrderedSet pkFieldList = ed.getFieldNames(true, false, false)
        String firstPkField = pkFieldList.size() > 0 ? pkFieldList.remove(0) : null
        String secondPkField = pkFieldList.size() > 0 ? pkFieldList.remove(0) : null
        StringBuffer pkTextSb = new StringBuffer()
        boolean firstField = true
        for (String fieldName in pkFieldList) {
            if (firstField) firstField = false else pkTextSb.append(",")
            pkTextSb.append(fieldName).append(":'").append(ed.getFieldStringForFile(fieldName, get(fieldName))).append("'")
        }
        String pkText = pkTextSb.toString()

        if (firstPkField) parms.pkPrimaryValue = get(firstPkField)
        if (secondPkField) parms.pkSecondaryValue = get(secondPkField)
        if (pkText) parms.pkRestCombinedValue = pkText
    }

    @Override
    EntityList findRelated(String relationshipName, Map<String, ?> byAndFields, List<String> orderBy, Boolean useCache, Boolean forUpdate) {
        Node relationship = getEntityDefinition().getRelationshipNode(relationshipName)
        if (!relationship) throw new EntityException("Relationship [${relationshipName}] not found in entity [${entityName}]")

        String relatedEntityName = relationship."@related-entity-name"
        Map keyMap = EntityDefinition.getRelationshipExpandedKeyMap(relationship, efi.getEntityDefinition(relatedEntityName))
        if (!keyMap) throw new EntityException("Relationship [${relationshipName}] in entity [${entityName}] has no key-map sub-elements and no default values")

        // make a Map where the key is the related entity's field name, and the value is the value from this entity
        Map condMap = new HashMap()
        for (Map.Entry entry in keyMap.entrySet()) condMap.put(entry.getValue(), valueMap.get(entry.getKey()))
        if (byAndFields) condMap.putAll(byAndFields)

        EntityFind find = getEntityFacadeImpl().find(relatedEntityName)
        return find.condition(condMap).orderBy(orderBy).useCache(useCache).forUpdate(forUpdate as boolean).list()
    }

    @Override
    EntityValue findRelatedOne(String relationshipName, Boolean useCache, Boolean forUpdate) {
        Node relationship = getEntityDefinition().getRelationshipNode(relationshipName)
        if (!relationship) throw new EntityException("Relationship [${relationshipName}] not found in entity [${entityName}]")
        return findRelatedOne(relationship, useCache, forUpdate)
    }

    protected EntityValue findRelatedOne(Node relationship, Boolean useCache, Boolean forUpdate) {
        String relatedEntityName = relationship."@related-entity-name"
        Map keyMap = EntityDefinition.getRelationshipExpandedKeyMap(relationship, efi.getEntityDefinition(relatedEntityName))
        if (!keyMap) throw new EntityException("Relationship [${relationship."@title"}${relationship."@related-entity-name"}] in entity [${entityName}] has no key-map sub-elements and no default values")

        // make a Map where the key is the related entity's field name, and the value is the value from this entity
        Map condMap = new HashMap()
        for (Map.Entry entry in keyMap.entrySet()) condMap.put(entry.getValue(), valueMap.get(entry.getKey()))

        EntityFind find = getEntityFacadeImpl().find(relatedEntityName)
        return find.condition(condMap).useCache(useCache).forUpdate(forUpdate as boolean).one()
    }

    @Override
    void deleteRelated(String relationshipName) {
        // NOTE: this does a select for update, may consider not doing that by default
        EntityList relatedList = findRelated(relationshipName, null, null, false, true)
        for (EntityValue relatedValue in relatedList) relatedValue.delete()
    }

    @Override
    boolean checkFks(boolean insertDummy) {
        for (Node oneRel in (Collection<Node>) getEntityDefinition().entityNode."relationship".findAll({ it."@type" == "one" })) {
            EntityValue value = findRelatedOne(oneRel, true, false)
            if (!value) {
                if (insertDummy) {
                    String relatedEntityName = oneRel."@related-entity-name"
                    EntityValue newValue = getEntityFacadeImpl().makeValue(relatedEntityName)
                    Map keyMap = EntityDefinition.getRelationshipExpandedKeyMap(oneRel, efi.getEntityDefinition(relatedEntityName))
                    if (!keyMap) throw new EntityException("Relationship [${oneRel."@title"}#${oneRel."@related-entity-name"}] in entity [${entityName}] has no key-map sub-elements and no default values")

                    // make a Map where the key is the related entity's field name, and the value is the value from this entity
                    for (Map.Entry entry in keyMap.entrySet())
                        newValue.set((String) entry.getValue(), valueMap.get(entry.getKey()))

                    if (newValue.containsPrimaryKey()) newValue.create()
                } else {
                    return false
                }
            }
        }
        // if we haven't found one missing, we're all good
        return true
    }

    @Override
    void checkAgainstDatabase(List messages) {
        try {
            EntityValue dbValue = this.cloneValue()
            if (!dbValue.refresh()) {
                messages.add("Entity [${getEntityName()}] record not found for primary key [${getPrimaryKeys()}]")
                return
            }

            for (String nonpkFieldName in this.getEntityDefinition().getFieldNames(false, true, true)) {
                // skip the lastUpdatedStamp field
                if (nonpkFieldName == "lastUpdatedStamp") continue

                Object checkFieldValue = this.get(nonpkFieldName)
                Object dbFieldValue = dbValue.get(nonpkFieldName)

                // use compareTo if available, generally more lenient (for BigDecimal ignores scale, etc)
                if (checkFieldValue != null && (checkFieldValue instanceof Comparable && dbFieldValue != null ? checkFieldValue.compareTo(dbFieldValue) != 0 : checkFieldValue != dbFieldValue)) {
                    messages.add("Field [${getEntityName()}.${nonpkFieldName}] did not match; check (file) value [${checkFieldValue}], db value [${dbFieldValue}] for primary key [${getPrimaryKeys()}]")
                }
            }
        } catch (EntityException e) {
            throw e
        } catch (Throwable t) {
            String errMsg = "Error checking entity [${getEntityName()}] with pk [${getPrimaryKeys()}]: ${t.toString()}"
            messages.add(errMsg)
            logger.error(errMsg, t)
        }
    }

    @Override
    Element makeXmlElement(Document document, String prefix) {
        Element element = null
        if (document != null) element = document.createElement((prefix ?: "") + entityName)
        if (!element) return null

        for (String fieldName in getEntityDefinition().getAllFieldNames()) {
            String value = getString(fieldName)
            if (value) {
                if (value.contains('\n') || value.contains('\r')) {
                    Element childElement = document.createElement(fieldName)
                    element.appendChild(childElement)
                    childElement.appendChild(document.createCDATASection(value))
                } else {
                    element.setAttribute(fieldName, value)
                }
            }
        }

        return element
    }

    @Override
    int writeXmlText(Writer pw, String prefix, boolean dependents) {
        if (dependents) {
            // to avoid loops (shouldn't happen but could)
            Map<String, Set> entityPksVisited = new HashMap()
            EntityDefinition.EntityDependents edp = this.getEntityDefinition().getDependentsTree(new LinkedList([this.getEntityName()]))
            int valuesWritten = this.writeXmlWithDependentsInternal(pw, prefix, entityPksVisited, edp)
            return valuesWritten
        }

        // indent 4 spaces
        String indentString = "    "
        // if a CDATA element is needed for a field it goes in this Map to be added at the end
        Map<String, String> cdataMap = new HashMap()

        pw.print(indentString); pw.print('<'); if (prefix) pw.print(prefix); pw.print(entityName);

        for (String fieldName in getEntityDefinition().getAllFieldNames()) {
            Node fieldNode = getEntityDefinition().getFieldNode(fieldName)
            String type = fieldNode."@type"

            if (type == "binary-very-long") {
                Object obj = get(fieldName)
                if (obj instanceof byte[]) {
                    cdataMap.put(fieldName, new String(Base64.encodeBase64((byte[]) obj)))
                } else if (obj instanceof SerialBlob) {
                    if (((SerialBlob) obj).length() == 0) continue
                    byte[] objBytes = ((SerialBlob) obj).getBytes(1, (int) obj.length())
                    cdataMap.put(fieldName, new String(Base64.encodeBase64(objBytes)))
                } else {
                    logger.warn("Field [${fieldName}] on entity [${entityName}] is not of type 'byte[]', is [${obj}] so skipping, won't be in export")
                }
                continue
            }

            String valueStr = getEntityDefinition().getFieldStringForFile(fieldName, get(fieldName))
            if (!valueStr) continue
            if (valueStr.contains('\n') || valueStr.contains('\r')) {
                cdataMap.put(fieldName, valueStr)
                continue
            }

            pw.print(' '); pw.print(fieldName); pw.print("=\"");
            pw.print(StupidUtilities.encodeForXmlAttribute(valueStr))
            pw.print("\"")
        }

        if (cdataMap.size() == 0) {
            // self-close the entity element
            pw.println("/>")
        } else {
            pw.println('>')

            for (Map.Entry<String, String> entry in cdataMap.entrySet()) {
                pw.print(indentString); pw.print(indentString);
                pw.print('<'); pw.print(entry.getKey()); pw.print('>');
                pw.print("<![CDATA["); pw.print(entry.getValue()); pw.print("]]>");
                pw.print("</"); pw.print(entry.getKey()); pw.println('>');
            }

            // close the entity element
            pw.print(indentString); pw.print("</"); pw.print(entityName); pw.println(">");
        }

        return 1
    }

    int writeXmlWithDependentsInternal(Writer pw, String prefix, Map<String, Set> entityPksVisited, EntityDefinition.EntityDependents edp) {
        int valuesWritten = 0
        String en = this.getEntityName()
        Map pkMap = this.getPrimaryKeys()
        if (entityPksVisited.get(en)?.contains(pkMap)) {
            if (logger.infoEnabled) logger.info("Tried to visit entity [${en}] pk [${pkMap}] more than once in writeXmlWithDependents()")
            return valuesWritten
        }
        // track that we visited this record
        StupidUtilities.addToSetInMap(en, pkMap, entityPksVisited)
        // logger.warn("===== Added ${en}:${pkMap}, entityPksVisited=${entityPksVisited}")
        // write this
        this.writeXmlText(pw, prefix, false)
        valuesWritten++

        // if a relationship is to an entity that is a descendant of another child of this entity, defer until
        // after other entity (ie a sort of depth first with entities in deeper position run before those in higher
        // position); for fk and grouping reasons
        Set<String> deferredEntityNames = new HashSet()
        Set<String> finishedRelationshipNames = new HashSet()

        valuesWritten += writeXmlWithDependentsInternalLoop(pw, prefix, entityPksVisited, edp, deferredEntityNames, finishedRelationshipNames, true)

        while (deferredEntityNames) {
            int deferredSize = deferredEntityNames.size()
            valuesWritten += writeXmlWithDependentsInternalLoop(pw, prefix, entityPksVisited, edp, deferredEntityNames, finishedRelationshipNames, true)
            if (deferredSize == deferredEntityNames.size()) {
                // uh-oh, made no progress... just do it without defer and we get what we get
                logger.info("In EntityValue.writeXmlWithDependents() for entity [${this.getEntityName()}] could not make progress with deferred entities, so writing in raw order instead of dependent-sensitive order.\n========== Current deferredEntityNames: ${deferredEntityNames}\n========== finishedRelationshipNames: ${finishedRelationshipNames}\n========== edp.dependentEntities: ${edp.dependentEntities.keySet()}")
                valuesWritten += writeXmlWithDependentsInternalLoop(pw, prefix, entityPksVisited, edp, deferredEntityNames, finishedRelationshipNames, false)
                break
            }
        }

        return valuesWritten
    }

    int writeXmlWithDependentsInternalLoop(Writer pw, String prefix, Map<String, Set> entityPksVisited, EntityDefinition.EntityDependents edp,
                                           Set<String> deferredEntityNames, Set<String> finishedRelationshipNames, boolean doDefer) {
        int valuesWritten = 0

        // for each dependent entity, if it is a dependent of another entity then defer it
        deferredEntityNames.clear()

        if (doDefer) {
            for (String checkEn in edp.dependentEntities.keySet()) {
                for (Map relInfo in edp.relationshipInfos.values()) {
                    if (finishedRelationshipNames.contains((relInfo.title ? relInfo.title + "#" : "") + relInfo.relatedEntityName)) continue
                    if (checkEn == relInfo.relatedEntityName) continue
                    EntityDefinition.EntityDependents checkEdp = edp.dependentEntities.get(relInfo.relatedEntityName)
                    if (checkEdp != null && checkEdp.allDescendants.contains(checkEn)) { deferredEntityNames.add(checkEn); break }
                }
            }
        }

        // get only dependent entity relationships
        for (Map relInfo in edp.relationshipInfos.values()) {
            if (deferredEntityNames.contains(relInfo.relatedEntityName)) continue
            if (finishedRelationshipNames.contains((relInfo.title ? relInfo.title + "#" : "") + relInfo.relatedEntityName)) continue

            EntityDefinition.EntityDependents relEdp = edp.dependentEntities.get(relInfo.relatedEntityName)
            if (relEdp == null) continue
            if (relInfo.type == "many") {
                EntityList el = findRelated((relInfo.title ? relInfo.title + "#" : "") + relInfo.relatedEntityName, null, null, false, false)
                for (EntityValue ev in el)
                    valuesWritten += ((EntityValueBase) ev).writeXmlWithDependentsInternal(pw, prefix, entityPksVisited, relEdp)
            } else {
                EntityValueImpl ev = (EntityValueImpl) findRelatedOne((String) relInfo.title+relInfo.relatedEntityName, false, false)
                if (ev != null) valuesWritten += ev.writeXmlWithDependentsInternal(pw, prefix, entityPksVisited, relEdp)
            }

            finishedRelationshipNames.add((relInfo.title ? relInfo.title + "#" : "") + relInfo.relatedEntityName)
        }

        return valuesWritten
    }

    Map<String, Object> getPlainValueMap(int dependentLevels) {
        Map<String, Object> vMap = StupidUtilities.removeNullsFromMap(new HashMap(valueMap))
        if (dependentLevels > 0) {
            List<EntityDefinition.RelationshipInfo> relInfoList = getEntityDefinition().getRelationshipsInfo(true)
            for (EntityDefinition.RelationshipInfo relInfo in relInfoList) {
                String relationshipName = relInfo.relationshipName
                String entryName = relInfo.shortAlias ?: relationshipName
                if (relInfo.type == "many") {
                    EntityList relList = findRelated(relationshipName, null, null, null, false)
                    if (relList) {
                        List plainRelList = []
                        for (EntityValue relEv in relList) plainRelList.add(((EntityValueBase) relEv).getPlainValueMap(dependentLevels-1))
                        vMap.put(entryName, plainRelList)
                    }
                } else {
                    EntityValue relEv = findRelatedOne(relationshipName, null, false)
                    if (relEv != null) vMap.put(entryName, ((EntityValueBase) relEv).getPlainValueMap(dependentLevels-1))
                }
            }
        }
        return vMap
    }

    // ========== Map Interface Methods ==========

    @Override
    int size() { return valueMap.size() }

    @Override
    boolean isEmpty() { return valueMap.isEmpty() }

    @Override
    boolean containsKey(Object o) { return o instanceof CharSequence ? valueMap.containsKey(o.toString()) : false }

    @Override
    boolean containsValue(Object o) { return values().contains(o) }

    @Override
    Object get(Object o) {
        if (o instanceof CharSequence) {
            // This may throw an exception, and let it; the Map interface doesn't provide for EntityException
            //   but it is far more useful than a log message that is likely to be ignored.
            return this.get(o.toString())
        } else {
            return null
        }
    }

    @Override
    Object put(String k, Object v) {
        Object original = this.get(k)
        this.set(k, v)
        return original
    }

    @Override
    Object remove(Object o) {
        if (valueMap.containsKey(o)) modified = true
        return valueMap.remove(o)
    }

    @Override
    void putAll(Map<? extends String, ? extends Object> map) {
        for (Map.Entry entry in map.entrySet()) {
            this.set((String) entry.key, entry.value)
        }
    }

    @Override
    void clear() { valueMap.clear() }

    @Override
    Set<String> keySet() {
        // Was this way through 1.1.0, only showing currently populated fields (not good for User Fields or other
        //     convenient things): return Collections.unmodifiableSet(valueMap.keySet())
        return new HashSet<String>(getEntityDefinition().getAllFieldNames())
    }

    @Override
    Collection<Object> values() {
        // everything needs to go through the get method, so iterate through the fields and get the values
        List<String> allFieldNames = getEntityDefinition().getAllFieldNames()
        List<Object> values = new ArrayList<Object>(allFieldNames.size())
        for (String fieldName in allFieldNames) values.add(get(fieldName))
        return values
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        // everything needs to go through the get method, so iterate through the fields and get the values
        List<String> allFieldNames = getEntityDefinition().getAllFieldNames()
        Set<Map.Entry<String, Object>> entries = new HashSet()
        for (String fieldName in allFieldNames) entries.add(new EntityFieldEntry(fieldName, this))
        return entries
    }

    static class EntityFieldEntry implements Map.Entry<String, Object> {
        protected String key
        protected EntityValueBase evb
        EntityFieldEntry(String key, EntityValueBase evb) { this.key = key; this.evb = evb; }
        String getKey() { return key }
        Object getValue() { return evb.get(key) }
        Object setValue(Object v) { return evb.set(key, v) }
    }

    // ========== Object Override Methods ==========

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) return false
        // reuse the compare method
        return this.compareTo((EntityValue) obj) == 0
    }

    @Override
    public int hashCode() {
        // NOTE: consider caching the hash code in the future for performance
        return entityName.hashCode() + valueMap.hashCode()
    }

    @Override
    String toString() { return "[${entityName}: ${valueMap}]" }

    @Override
    public Object clone() { return this.cloneValue() }

    abstract EntityValue cloneValue();
    abstract EntityValue cloneDbValue(boolean getOld);

    // =========== The CrUD and abstract methods ===========

    boolean doDataFeed() {
        // skip ArtifactHitBin, causes funny recursion
        return this.getEntityDefinition().getFullEntityName() != "moqui.server.ArtifactHitBin"
    }

    void checkSetFieldDefaults(boolean isCreate, EntityDefinition ed, ExecutionContext ec) {
        for (Node fieldNode in ed.getFieldNodes(isCreate, true, false)) {
            String defaultStr = fieldNode."@default"
            if (!defaultStr) continue
            String fieldName = fieldNode."@name"
            Object curVal = valueMap.get(fieldName)
            if (StupidUtilities.isEmpty(curVal)) {
                ec.getContext().push(valueMap)
                try {
                    Object newVal = ec.getResource().evaluateContextField(defaultStr, "")
                    if (newVal != null) valueMap.put(fieldName, newVal)
                } finally {
                    ec.getContext().pop()
                }
            }
        }
    }

    @Override
    EntityValue create() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_CREATE").setParameters(valueMap),
                (ed.entityNode."@authorize-skip" != "true" && !ed.entityNode."@authorize-skip"?.contains("create")))

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "create", true)

        Long lastUpdatedLong = ecfi.getTransactionFacade().getCurrentTransactionStartTime() ?: System.currentTimeMillis()
        if (ed.isField("lastUpdatedStamp") && !this.getValueMap().lastUpdatedStamp)
            this.set("lastUpdatedStamp", new Timestamp(lastUpdatedLong))

        // check/set defaults
        checkSetFieldDefaults(true, ed, ec)

        // do this before the db change so modified flag isn't cleared
        if (doDataFeed()) getEntityFacadeImpl().getEntityDataFeed().dataFeedCheckAndRegister(this, false, valueMap, null)

        ListOrderedSet fieldList = new ListOrderedSet()
        for (String fieldName in ed.getFieldNames(true, true, false)) if (valueMap.containsKey(fieldName)) fieldList.add(fieldName)


        // if there is not a txCache or the txCache doesn't handle the create, call the abstract method to create the main record
        if (getTxCache() == null || !getTxCache().create(this)) this.basicCreate(fieldList, null)


        // NOTE: cache clear is the same for create, update, delete; even on create need to clear one cache because it
        // might have a null value for a previous query attempt
        getEntityFacadeImpl().getEntityCache().clearCacheForValue(this, true)

        handleAuditLog(false, null)

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "create", false)
        // count the artifact hit
        ecfi.countArtifactHit("entity", "create", ed.getFullEntityName(), this.getPrimaryKeys(), startTime,
                System.currentTimeMillis(), 1)
        // pop the ArtifactExecutionInfo to clean it up
        ec.getArtifactExecution().pop()

        return this
    }
    void basicCreate(Connection con) {
        EntityDefinition ed = getEntityDefinition()
        ListOrderedSet fieldList = new ListOrderedSet()
        for (String fieldName in ed.getFieldNames(true, true, false)) if (valueMap.containsKey(fieldName)) fieldList.add(fieldName)

        basicCreate(fieldList, con)
    }
    void basicCreate(ListOrderedSet fieldList, Connection con) {
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        this.createExtended(fieldList, con)

        // create records for the UserFields
        ListOrderedSet userFieldNameList = ed.getFieldNames(false, false, true)
        if (userFieldNameList) {
            boolean alreadyDisabled = ec.getArtifactExecution().disableAuthz()
            try {
                for (String userFieldName in userFieldNameList) {
                    Node userFieldNode = ed.getFieldNode(userFieldName)
                    Object valueObj = this.getValueMap().get(userFieldName)
                    if (valueObj == null) continue

                    Map<String, Object> parms = [entityName: ed.getFullEntityName(), fieldName: userFieldName,
                            userGroupId: userFieldNode."@user-group-id", valueText: valueObj as String]
                    addThreeFieldPkValues(parms)
                    EntityValue newUserFieldValue = efi.makeValue("moqui.entity.UserFieldValue").setAll(parms)
                    newUserFieldValue.setSequencedIdPrimary().create()
                }
            } finally {
                if (!alreadyDisabled) ec.getArtifactExecution().enableAuthz()
            }
        }
    }
    /** This method should create a corresponding record in the datasource. */
    abstract void createExtended(ListOrderedSet fieldList, Connection con)

    @Override
    EntityValue update() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        if (ed.createOnly()) throw new EntityException("Entity [${getEntityName()}] is create-only (immutable), cannot be updated.")

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_UPDATE").setParameters(valueMap),
                ed.entityNode."@authorize-skip" != "true")

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "update", true)

        // check/set defaults
        checkSetFieldDefaults(false, ed, ec)

        boolean dbValueMapFromDb = false
        // it may be that the oldValues map is full of null values because the EntityValue didn't come from the db
        if (dbValueMap) for (Object val in dbValueMap.values()) if (val != null) { dbValueMapFromDb = true; break }

        List entityInfoList = doDataFeed() ? getEntityFacadeImpl().getEntityDataFeed().getDataFeedEntityInfoList(ed.getFullEntityName()) : []

        EntityValueImpl refreshedValue = null
        if (ed.needsAuditLog() || entityInfoList || ed.getEntityNode()."@optimistic-lock" == "true") {
            refreshedValue = (EntityValueImpl) this.clone()
            refreshedValue.refresh()
        }

        Map oldValues = refreshedValue ? refreshedValue.getValueMap() : (dbValueMapFromDb ? dbValueMap : [:])

        List<String> pkFieldList = ed.getPkFieldNames()
        ListOrderedSet nonPkAllFieldList = ed.getFieldNames(false, true, false)
        ListOrderedSet nonPkFieldList = new ListOrderedSet()
        for (String fieldName in nonPkAllFieldList) {
            if (valueMap.containsKey(fieldName) &&
                    (!dbValueMapFromDb || valueMap.get(fieldName) != dbValueMap.get(fieldName))) {
                nonPkFieldList.add(fieldName)
            }
        }
        // logger.warn("================ evb.update() ${getEntityName()} nonPkFieldList=${nonPkFieldList};\nvalueMap=${valueMap};\ndbValueMap=${dbValueMap}")
        if (!nonPkFieldList) {
            if (logger.isTraceEnabled()) logger.trace("Not doing update on entity with no populated non-PK fields; entity=" + this.toString())
            return this
        }

        if (ed.getEntityNode()."@optimistic-lock" == "true") {
            if (getTimestamp("lastUpdatedStamp") != refreshedValue.getTimestamp("lastUpdatedStamp"))
                throw new EntityException("This record was updated by someone else at [${getTimestamp("lastUpdatedStamp")}] which was after the version you loaded at [${refreshedValue.getTimestamp("lastUpdatedStamp")}]. Not updating to avoid overwriting data.")
        }

        Long lastUpdatedLong = ecfi.getTransactionFacade().getCurrentTransactionStartTime() ?: System.currentTimeMillis()
        // not sure why this condition was there, doesn't make sense so removed: && !this.getValueMap().lastUpdatedStamp
        if (ed.isField("lastUpdatedStamp")) this.set("lastUpdatedStamp", new Timestamp(lastUpdatedLong))

        // do this before the db change so modified flag isn't cleared
        getEntityFacadeImpl().getEntityDataFeed().dataFeedCheckAndRegister(this, true, valueMap, oldValues)


        // if there is not a txCache or the txCache doesn't handle the update, call the abstract method to update the main record
        if (getTxCache() == null || !getTxCache().update(this)) this.basicUpdate(dbValueMapFromDb, pkFieldList, nonPkFieldList, null)


        // clear the entity cache
        getEntityFacadeImpl().getEntityCache().clearCacheForValue(this, false)

        handleAuditLog(true, oldValues)

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "update", false)
        // count the artifact hit
        ecfi.countArtifactHit("entity", "update", ed.getFullEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), 1)
        // pop the ArtifactExecutionInfo to clean it up
        ec.getArtifactExecution().pop()

        return this
    }
    void basicUpdate(Connection con) {
        EntityDefinition ed = getEntityDefinition()

        boolean dbValueMapFromDb = false
        // it may be that the oldValues map is full of null values because the EntityValue didn't come from the db
        if (dbValueMap) for (Object val in dbValueMap.values()) if (val != null) { dbValueMapFromDb = true; break }

        List<String> pkFieldList = ed.getPkFieldNames()
        ListOrderedSet nonPkAllFieldList = ed.getFieldNames(false, true, false)
        ListOrderedSet nonPkFieldList = new ListOrderedSet()
        for (String fieldName in nonPkAllFieldList) {
            if (valueMap.containsKey(fieldName) &&
                    (!dbValueMapFromDb || valueMap.get(fieldName) != dbValueMap.get(fieldName))) {
                nonPkFieldList.add(fieldName)
            }
        }

        basicUpdate(dbValueMapFromDb, pkFieldList, nonPkFieldList, con)
    }
    void basicUpdate(boolean dbValueMapFromDb, List<String> pkFieldList, ListOrderedSet nonPkFieldList, Connection con) {
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        // call abstract method
        this.updateExtended(pkFieldList, nonPkFieldList, con)

        // create or update records for the UserFields
        ListOrderedSet userFieldNameList = ed.getFieldNames(false, false, true)
        if (userFieldNameList) {
            boolean alreadyDisabled = ec.getArtifactExecution().disableAuthz()
            try {
                // get values for all fields in one query, for all groups the user is in
                Map<String, Object> findParms = [entityName: ed.getFullEntityName()]
                addThreeFieldPkValues(findParms)
                Set<String> userGroupIdSet = efi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet()
                EntityList userFieldValueList = efi.find("moqui.entity.UserFieldValue")
                        .condition("userGroupId", EntityCondition.IN, userGroupIdSet)
                        .condition(findParms).list()

                for (String userFieldName in userFieldNameList) {
                    // if the field hasn't been updated, skip it
                    if (!(valueMap.containsKey(userFieldName) &&
                            (!dbValueMapFromDb || valueMap.get(userFieldName) != dbValueMap?.get(userFieldName)))) {
                        continue
                    }

                    EntityList fieldOnlyUserFieldValueList = userFieldValueList.filterByAnd([fieldName: userFieldName])
                    if (fieldOnlyUserFieldValueList) {
                        for (EntityValue userFieldValue in fieldOnlyUserFieldValueList) {
                            userFieldValue.valueText = this.getValueMap().get(userFieldName) as String
                            userFieldValue.update()
                        }
                    } else {
                        Node userFieldNode = ed.getFieldNode(userFieldName)

                        Map<String, Object> parms = [entityName: ed.getFullEntityName(), fieldName: userFieldName,
                                userGroupId: userFieldNode."@user-group-id", valueText: this.getValueMap().get(userFieldName) as String]
                        addThreeFieldPkValues(parms)
                        EntityValue newUserFieldValue = efi.makeValue("moqui.entity.UserFieldValue").setAll(parms)
                        newUserFieldValue.setSequencedIdPrimary().create()
                    }
                }
            } finally {
                if (!alreadyDisabled) ec.getArtifactExecution().enableAuthz()
            }
        }
    }
    abstract void updateExtended(List<String> pkFieldList, ListOrderedSet nonPkFieldList, Connection con)

    @Override
    EntityValue delete() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        if (ed.createOnly()) throw new EntityException("Entity [${getEntityName()}] is create-only (immutable), cannot be deleted.")

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_DELETE").setParameters(valueMap),
                ed.entityNode."@authorize-skip" != "true")

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "delete", true)
        // this needs to be called before the actual update so we know which fields are modified
        // NOTE: consider not doing this on delete, DataDocuments are not great for representing absence of records
        // NOTE2: this might be useful, but is a bit of a pain and utility is dubious, leave out for now
        // getEntityFacadeImpl().getEntityDataFeed().dataFeedCheckAndRegister(this, true, valueMap, null)


        // if there is not a txCache or the txCache doesn't handle the delete, call the abstract method to delete the main record
        if (getTxCache() == null || !getTxCache().delete(this)) this.basicDelete(null)


        // clear the entity cache
        getEntityFacadeImpl().getEntityCache().clearCacheForValue(this, false)

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "delete", false)
        // count the artifact hit
        ecfi.countArtifactHit("entity", "delete", ed.getFullEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), 1)
        // pop the ArtifactExecutionInfo to clean it up
        ec.getArtifactExecution().pop()

        return this
    }
    void basicDelete(Connection con) {
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        this.deleteExtended(con)

        // delete records for the UserFields
        ListOrderedSet userFieldNameList = ed.getFieldNames(false, false, true)
        if (userFieldNameList) {
            boolean alreadyDisabled = ec.getArtifactExecution().disableAuthz()
            try {
                // get values for all fields in one query, for all groups the user is in
                Map<String, Object> findParms = [entityName: ed.getFullEntityName()]
                addThreeFieldPkValues(findParms)
                Set<String> userGroupIdSet = ec.getUser().getUserGroupIdSet()
                efi.find("moqui.entity.UserFieldValue")
                        .condition("userGroupId", EntityCondition.IN, userGroupIdSet)
                        .condition(findParms).deleteAll()
            } finally {
                if (!alreadyDisabled) ec.getArtifactExecution().enableAuthz()
            }
        }
    }
    abstract void deleteExtended(Connection con)

    @Override
    boolean refresh() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()
        ExecutionContextFactoryImpl ecfi = getEntityFacadeImpl().getEcfi()
        ExecutionContext ec = ecfi.getExecutionContext()

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_VIEW")
                        .setActionDetail("refresh").setParameters(valueMap),
                ed.entityNode."@authorize-skip" != "true")

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "find-one", true)

        List<String> pkFieldList = ed.getPkFieldNames()
        if (pkFieldList.size() == 0) throw new EntityException("Entity ${getEntityName()} has no primary key fields, cannot do refresh.")

        // if there is not a txCache or the txCache doesn't handle the refresh, call the abstract method to refresh
        boolean retVal = false
        if (getTxCache() != null) retVal = getTxCache().refresh(this)
        // call the abstract method
        if (!retVal) {
            retVal = this.refreshExtended()
            if (retVal && getTxCache() != null) getTxCache().onePut(this)
        }

        // NOTE: clear out UserFields

        getEntityFacadeImpl().runEecaRules(ed.getFullEntityName(), this, "find-one", false)
        // count the artifact hit
        ecfi.countArtifactHit("entity", "refresh", ed.getFullEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), retVal ? 1 : 0)
        // pop the ArtifactExecutionInfo to clean it up
        ec.getArtifactExecution().pop()

        return retVal
    }
    abstract boolean refreshExtended()
}
