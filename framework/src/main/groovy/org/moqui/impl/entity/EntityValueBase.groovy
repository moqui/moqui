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
import org.moqui.entity.EntityException
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.context.ExecutionContextFactoryImpl

import org.w3c.dom.Document
import org.w3c.dom.Element

import java.sql.Date
import java.sql.Time
import java.sql.Timestamp

abstract class EntityValueBase implements EntityValue {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityValueBase.class)

    /** This is a reference to where the entity value came from.
     * It is volatile so not stored when this is serialized, and will get a reference to the active EntityFacade after.
     */
    protected volatile EntityFacadeImpl efi

    protected final String entityName
    protected volatile EntityDefinition entityDefinition

    protected final Map<String, Object> valueMap = new HashMap()
    /* Original DB Value Map: not used unless the value has been modified from its original state from the DB */
    protected Map<String, Object> dbValueMap = null

    protected boolean modified = false
    protected boolean mutable = true

    EntityValueBase(EntityDefinition ed, EntityFacadeImpl efip) {
        efi = efip
        entityName = ed.getEntityName()
        entityDefinition = ed
    }

    EntityFacadeImpl getEntityFacadeImpl() {
        // handle null after deserialize; this requires a static reference in Moqui.java or we'll get an error
        if (efi == null) efi = ((ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()).getEntityFacade()
        return efi
    }

    EntityDefinition getEntityDefinition() {
        if (entityDefinition == null) entityDefinition = getEntityFacadeImpl().getEntityDefinition(entityName)
        return entityDefinition
    }

    // NOTE: this is no longer protected so that external add-on code can set original values from a datasource
    Map<String, Object> getValueMap() { return valueMap }
    protected Map<String, Object> getDbValueMap() { return dbValueMap }
    protected void setDbValueMap(Map<String, Object> map) { dbValueMap = map }

    void setSyncedWithDb() { dbValueMap = null; modified = false }

    /** @see org.moqui.entity.EntityValue#getEntityName() */
    String getEntityName() { return entityName }

    /** @see org.moqui.entity.EntityValue#isModified() */
    boolean isModified() { return modified }

    /** @see org.moqui.entity.EntityValue#isMutable() */
    boolean isMutable() { return mutable }

    /** @see org.moqui.entity.EntityValue#get(String) */
    Object get(String name) {
        EntityDefinition ed = getEntityDefinition()
        Node fieldNode = ed.getFieldNode(name)

        if (!fieldNode) {
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
                        EntityFind lefFind = getEntityFacadeImpl().makeFind("moqui.basic.LocalizedEntityField")
                        lefFind.condition([entityName:entityName, fieldName:name, pkValue:pkValue, locale:localeStr])
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
                EntityFind lmFind = getEntityFacadeImpl().makeFind("moqui.basic.LocalizedMessage")
                lmFind.condition([original:valueMap.get(name), locale:localeStr])
                EntityValue lmValue = lmFind.useCache(true).one()
                if (lmValue) return lmValue.localized
            }
        }

        return valueMap.get(name)
    }

    /** @see org.moqui.entity.EntityValue#containsPrimaryKey() */
    boolean containsPrimaryKey() { return this.getEntityDefinition().containsPrimaryKey(valueMap) }

    /** @see org.moqui.entity.EntityValue#getPrimaryKeys() */
    Map<String, Object> getPrimaryKeys() {
        Map<String, Object> pks = new HashMap()
        for (String fieldName in this.getEntityDefinition().getPkFieldNames()) {
            pks.put(fieldName, valueMap.get(fieldName))
        }
        return pks
    }

    /** @see org.moqui.entity.EntityValue#set(String, Object) */
    EntityValue set(String name, Object value) {
        if (!mutable) throw new EntityException("Cannot set field [${name}], this entity value is not mutable (it is read-only)")
        if (!getEntityDefinition().isField(name)) {
            throw new EntityException("The name [${name}] is not a valid field name for entity [${entityName}]")
        }
        Object oldValue = valueMap.get(name)
        if (oldValue != value) {
            modified = true
            if (dbValueMap == null) dbValueMap = new HashMap()
            dbValueMap.put(name, oldValue)
        }
        valueMap.put(name, value)
        return this
    }

    /** @see org.moqui.entity.EntityValue#setString(String, String) */
    EntityValue setString(String name, String value) { entityDefinition.setString(name, value, this); return this }

    /** @see org.moqui.entity.EntityValue#getBoolean(String) */
    Boolean getBoolean(String name) { return this.get(name) as Boolean }

    /** @see org.moqui.entity.EntityValue#getString(String) */
    String getString(String name) {
        Object valueObj = this.get(name)
        return valueObj ? valueObj.toString() : null
    }

    /** @see org.moqui.entity.EntityValue#getTimestamp(String) */
    Timestamp getTimestamp(String name) { return (Timestamp) this.get(name).asType(Timestamp.class) }

    /** @see org.moqui.entity.EntityValue#getTime(String) */
    Time getTime(String name) { return this.get(name) as Time }

    /** @see org.moqui.entity.EntityValue#getDate(String) */
    Date getDate(String name) { return this.get(name) as Date }

    /** @see org.moqui.entity.EntityValue#getLong(String) */
    Long getLong(String name) { return this.get(name) as Long }

    /** @see org.moqui.entity.EntityValue#getDouble(String) */
    Double getDouble(String name) { return this.get(name) as Double }

    /** @see org.moqui.entity.EntityValue#getBigDecimal(String) */
    BigDecimal getBigDecimal(String name) { return this.get(name) as BigDecimal }

    /** @see org.moqui.entity.EntityValue#setFields(Map, boolean, java.lang.String, boolean) */
    EntityValue setFields(Map<String, ?> fields, boolean setIfEmpty, String namePrefix, Boolean pks) {
        entityDefinition.setFields(fields, this, setIfEmpty, namePrefix, pks)
        return this
    }

    /** @see org.moqui.entity.EntityValue#setSequencedIdPrimary() */
    EntityValue setSequencedIdPrimary() {
        List<String> pkFields = getEntityDefinition().getPkFieldNames()
        Node entityNode = getEntityDefinition().getEntityNode()
        Long staggerMax = (entityNode."@sequence-primary-stagger" as Long) ?: 1
        Long bankSize = (entityNode."@sequence-bank-size" as Long) ?: 50
        set(pkFields.get(0), getEntityFacadeImpl().sequencedIdPrimary(getEntityName(), staggerMax, bankSize))
        return this
    }

    /** @see org.moqui.entity.EntityValue#setSequencedIdSecondary() */
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
        List<EntityValue> allValues
        this.getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().disableAuthz()
        try {
            allValues = getEntityFacadeImpl().makeFind(getEntityName()).condition(lookupValue).list()
        } finally {
            this.getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().enableAuthz()
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

    /** @see org.moqui.entity.EntityValue#compareTo(EntityValue) */
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
        for (String fieldName in this.getEntityDefinition().getFieldNames(false, true)) {
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

    /** @see org.moqui.entity.EntityValue#createOrUpdate() */
    EntityValue createOrUpdate() {
        EntityValue dbValue = (EntityValue) this.clone()
        if (dbValue.refresh()) {
            return update()
        } else {
            return create()
        }
    }

    void handleAuditLog(boolean isUpdate, Map oldValues) {
        if (isUpdate && oldValues == null) return

        EntityDefinition ed = getEntityDefinition()
        if (!ed.needsAuditLog()) return
        // in this case DON'T use the ec.user.nowTimestamp because we want the real time for audits
        Timestamp nowTimestamp = new Timestamp(System.currentTimeMillis())

        ListOrderedSet pkFieldList = ed.getFieldNames(true, false)
        String firstPkField = pkFieldList.size() > 0 ? pkFieldList.remove(0) : null
        String secondPkField = pkFieldList.size() > 1 ? pkFieldList.remove(0) : null
        StringBuffer pkTextSb = new StringBuffer()
        boolean firstField = true
        for (String fieldName in pkFieldList) {
            if (firstField) firstField = false else pkTextSb.append(",")
            pkTextSb.append(fieldName).append("=").append(get(fieldName) as String)
        }
        String pkText = pkTextSb.toString()

        for (Node fieldNode in ed.getFieldNodes(true, true)) {
            if (fieldNode."@enable-audit-log" == "true") {
                String fieldName = fieldNode."@name"

                // is there a new value? if not continue
                if (!this.valueMap.containsKey(fieldName)) continue

                Object value = get(fieldName)
                Object oldValue = oldValues?.get(fieldName)

                // if isUpdate but old value == new value, then it hasn't been updated, so skip it
                if (isUpdate && value == oldValue) continue

                // don't skip for this, if a field was reset then we want to record that: if (!value) continue

                Map<String, Object> parms = (Map<String, Object>) [changedEntityName:getEntityName(),
                        changedFieldName:fieldName,
                        newValueText:(value as String), changedDate:nowTimestamp,
                        changedByUserId:getEntityFacadeImpl().ecfi.executionContext.user.userId,
                        changedInVisitId:getEntityFacadeImpl().ecfi.executionContext.user.visitId]
                parms.oldValueText = oldValue
                if (firstPkField) parms.pkPrimaryValue = get(firstPkField)
                if (secondPkField) parms.pkSecondaryValue = get(secondPkField)
                if (pkText) parms.pkRestCombinedValue = pkText

                // logger.warn("TOREMOVE: in handleAuditLog for [${ed.entityName}.${fieldName}] value=[${value}], oldValue=[${oldValue}], oldValues=[${oldValues}]", new Exception("AuditLog location"))

                getEntityFacadeImpl().ecfi.serviceFacade.async().name("create#moqui.entity.EntityAuditLog").parameters(parms).call()
            }
        }
    }

    /** @see org.moqui.entity.EntityValue#getOriginalDbValue(String) */
    Object getOriginalDbValue(String name) {
        return (dbValueMap && dbValueMap.get(name)) ? dbValueMap.get(name) : valueMap.get(name)
    }

    /** @see org.moqui.entity.EntityValue#findRelated(String, Map, java.util.List<java.lang.String>, Boolean, Boolean) */
    EntityList findRelated(String relationshipName, Map<String, ?> byAndFields, List<String> orderBy, Boolean useCache, Boolean forUpdate) {
        Node relationship = getEntityDefinition().getRelationshipNode(relationshipName)
        if (!relationship) throw new EntityException("Relationship [${relationshipName}] not found in entity [${entityName}]")

        Map keyMap = getEntityDefinition().getRelationshipExpandedKeyMap(relationship)
        if (!keyMap) throw new EntityException("Relationship [${relationshipName}] in entity [${entityName}] has no key-map sub-elements and no default values")

        // make a Map where the key is the related entity's field name, and the value is the value from this entity
        Map condMap = new HashMap()
        for (Map.Entry entry in keyMap.entrySet()) condMap.put(entry.getValue(), valueMap.get(entry.getKey()))
        if (byAndFields) condMap.putAll(byAndFields)

        EntityFind find = getEntityFacadeImpl().makeFind(relationship."@related-entity-name")
        return find.condition(condMap).orderBy(orderBy).useCache(useCache).forUpdate(forUpdate as boolean).list()
    }

    /** @see org.moqui.entity.EntityValue#findRelatedOne(String, Boolean, Boolean) */
    EntityValue findRelatedOne(String relationshipName, Boolean useCache, Boolean forUpdate) {
        Node relationship = getEntityDefinition().getRelationshipNode(relationshipName)
        if (!relationship) throw new EntityException("Relationship [${relationshipName}] not found in entity [${entityName}]")
        return findRelatedOne(relationship, useCache, forUpdate)
    }

    protected EntityValue findRelatedOne(Node relationship, Boolean useCache, Boolean forUpdate) {
        Map keyMap = getEntityDefinition().getRelationshipExpandedKeyMap(relationship)
        if (!keyMap) throw new EntityException("Relationship [${relationship."@title"}${relationship."@related-entity-name"}] in entity [${entityName}] has no key-map sub-elements and no default values")

        // make a Map where the key is the related entity's field name, and the value is the value from this entity
        Map condMap = new HashMap()
        for (Map.Entry entry in keyMap.entrySet()) condMap.put(entry.getValue(), valueMap.get(entry.getKey()))

        EntityFind find = getEntityFacadeImpl().makeFind(relationship."@related-entity-name")
        return find.condition(condMap).useCache(useCache).forUpdate(forUpdate as boolean).one()
    }

    /** @see org.moqui.entity.EntityValue#deleteRelated(String) */
    void deleteRelated(String relationshipName) {
        // NOTE: this does a select for update, may consider not doing that by default
        EntityList relatedList = findRelated(relationshipName, null, null, false, true)
        for (EntityValue relatedValue in relatedList) relatedValue.delete()
    }

    /** @see org.moqui.entity.EntityValue#checkFks(boolean) */
    boolean checkFks(boolean insertDummy) {
        for (Node oneRel in getEntityDefinition().entityNode."relationship".find({ it."@type" == "one" })) {
            EntityValue value = findRelatedOne(oneRel, true, false)
            if (!value) {
                if (insertDummy) {
                    EntityValue newValue = getEntityFacadeImpl().makeValue((String) oneRel."@related-entity-name")
                    Map keyMap = getEntityDefinition().getRelationshipExpandedKeyMap(oneRel)
                    if (!keyMap) throw new EntityException("Relationship [${oneRel."@title"}${oneRel."@related-entity-name"}] in entity [${entityName}] has no key-map sub-elements and no default values")

                    // make a Map where the key is the related entity's field name, and the value is the value from this entity
                    for (Map.Entry entry in keyMap.entrySet())
                        newValue.set((String) entry.getValue(), valueMap.get(entry.getKey()))

                    if (newValue.containsPrimaryKey()){
                        newValue.create()
                    }
                } else {
                    return false
                }
            }
        }
        // if we haven't found one missing, we're all good
        return true
    }

    /** @see org.moqui.entity.EntityValue#checkAgainstDatabase(List) */
    void checkAgainstDatabase(List messages) {
        try {
            EntityValue dbValue = this.cloneValue()
            if (!dbValue.refresh()) {
                messages.add("Entity [${getEntityName()}] record not found for primary key [${getPrimaryKeys()}]")
                return
            }

            for (String nonpkFieldName in this.getEntityDefinition().getFieldNames(false, true)) {
                // skip the lastUpdatedStamp field
                if (nonpkFieldName == "lastUpdatedStamp") continue

                Object checkFieldValue = this.get(nonpkFieldName)
                Object dbFieldValue = dbValue.get(nonpkFieldName)

                if (checkFieldValue != null && !checkFieldValue.equals(dbFieldValue)) {
                    messages.add("Field [${getEntityName()}.${nonpkFieldName}] did not match; check (file) value [${checkFieldValue}], db value [${dbFieldValue}] for primary key [${getPrimaryKeys()}]")
                }
            }
        } catch (EntityException e) {
            throw e
        } catch (Throwable t) {
            String errMsg = "Error checking entity [${getEntityName()}] with pk [${getPrimaryKeys()}]"
            messages.add(errMsg)
            logger.error(errMsg, t)
        }
    }

    /** @see org.moqui.entity.EntityValue#makeXmlElement(Document, String) */
    Element makeXmlElement(Document document, String prefix) {
        Element element = null
        if (document != null) element = document.createElement((prefix ? prefix : "") + entityName)
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

    /** @see org.moqui.entity.EntityValue#writeXmlText(PrintWriter, String, boolean) */
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
                } else {
                    logger.warn("Field [${fieldName}] on entity [${entityName}] is not of type 'byte[]', is [${obj}] so skipping, won't be in export")
                }
                continue
            }

            String valueStr = getString(fieldName)
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
                logger.warn("In EntityValue.writeXmlWithDependents() for entity [${this.getEntityName()}] could not make progress with deferred entities, so writing in raw order instead of dependent-sensitive order. Current deferredEntityNames: ${deferredEntityNames}, finishedRelationshipNames: ${finishedRelationshipNames}, edp.dependentEntities: ${edp.dependentEntities.keySet()}")
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
                    if (finishedRelationshipNames.contains(relInfo.title+relInfo.relatedEntityName)) continue
                    if (checkEn == relInfo.relatedEntityName) continue
                    EntityDefinition.EntityDependents checkEdp = edp.dependentEntities.get(relInfo.relatedEntityName)
                    if (checkEdp && checkEdp.allDescendants.contains(checkEn)) { deferredEntityNames.add(checkEn); break }
                }
            }
        }

        // get only dependent entity relationships
        for (Map relInfo in edp.relationshipInfos.values()) {
            if (deferredEntityNames.contains(relInfo.relatedEntityName)) continue
            if (finishedRelationshipNames.contains(relInfo.title+relInfo.relatedEntityName)) continue

            EntityDefinition.EntityDependents relEdp = edp.dependentEntities.get(relInfo.relatedEntityName)
            if (relEdp == null) continue
            if (relInfo.type == "many") {
                EntityListImpl el = (EntityListImpl) findRelated((relInfo.title?:"") + relInfo.relatedEntityName, null, null, false, false)
                for (EntityValueImpl ev in el) valuesWritten += ev.writeXmlWithDependentsInternal(pw, prefix, entityPksVisited, relEdp)
            } else {
                EntityValueImpl ev = (EntityValueImpl) findRelatedOne((String) relInfo.title+relInfo.relatedEntityName, false, false)
                valuesWritten += ev.writeXmlWithDependentsInternal(pw, prefix, entityPksVisited, relEdp)
            }

            finishedRelationshipNames.add(relInfo.title+relInfo.relatedEntityName)
        }

        return valuesWritten
    }

    // ========== Map Interface Methods ==========

    /** @see java.util.Map#size() */
    int size() { return valueMap.size() }

    boolean isEmpty() { return valueMap.isEmpty() }

    boolean containsKey(Object o) { return valueMap.containsKey(o) }

    boolean containsValue(Object o) { return valueMap.containsValue(o) }

    Object get(Object o) {
        if (o instanceof String) {
            // This may throw an exception, and let it; the Map interface doesn't provide for EntityException
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
        if (valueMap.containsKey(o)) modified = true
        return valueMap.remove(o)
    }

    void putAll(Map<? extends String, ? extends Object> map) {
        for(Map.Entry entry in map.entrySet()) {
            this.set((String) entry.key, entry.value)
        }
    }

    void clear() { valueMap.clear() }

    Set<String> keySet() { return Collections.unmodifiableSet(valueMap.keySet()) }

    Collection<Object> values() {
        // everything needs to go through the get method, so iterate through the keys and get the values
        List<Object> values = new ArrayList<Object>(valueMap.size())
        for (String key in valueMap.keySet()) values.add(get(key))
        return values
    }

    Set<Map.Entry<String, Object>> entrySet() {
        Set<Map.Entry<String, Object>> entries = new HashSet()
        for (String key in valueMap.keySet()) entries.add(new EntityFieldEntry(key, this))
        return entries
    }

    static class EntityFieldEntry implements Map.Entry<String, Object> {
        protected String key
        protected EntityValueImpl evi
        EntityFieldEntry(String key, EntityValueImpl evi) { this.key = key; this.evi = evi; }
        String getKey() { return key }
        Object getValue() { return evi.get(key) }
        Object setValue(Object v) { return evi.set(key, v) }
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

    public EntityValue cloneValue() {
        EntityValueImpl newObj = new EntityValueImpl(getEntityDefinition(), getEntityFacadeImpl())
        newObj.getValueMap().putAll(getValueMap())
        if (getDbValueMap()) newObj.setDbValueMap((Map<String, Object>) getDbValueMap().clone())
        // don't set mutable (default to mutable even if original was not) or modified (start out not modified)
        return newObj
    }

    // =========== The abstract methods ===========
    /** @see org.moqui.entity.EntityValue#create() */
    EntityValue create() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()

        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_CREATE"),
                (ed.entityNode."@authorize-skip" != "true" && !ed.entityNode."@authorize-skip"?.contains("create")))

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "create", true)

        if (ed.isField("lastUpdatedStamp") && !this.get("lastUpdatedStamp"))
            this.set("lastUpdatedStamp", new Timestamp(getEntityFacadeImpl().ecfi.getTransactionFacade().getCurrentTransactionStartTime()))

        ListOrderedSet fieldList = new ListOrderedSet()
        for (String fieldName in ed.getAllFieldNames()) if (valueMap.containsKey(fieldName)) fieldList.add(fieldName)

        // call the abstract method
        this.createExtended(fieldList)

        handleAuditLog(false, null)

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "create", false)
        // count the artifact hit
        getEntityFacadeImpl().ecfi.countArtifactHit("entity", "create", ed.getEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), 1)
        // pop the ArtifactExecutionInfo to clean it up
        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().pop()

        return this
    }
    /** This method should create a corresponding record in the datasource. */
    abstract void createExtended(ListOrderedSet fieldList)

    /** @see org.moqui.entity.EntityValue#update() */
    EntityValue update() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()

        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_UPDATE"),
                ed.entityNode."@authorize-skip" != "true")

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "update", true)

        boolean dbValueMapFromDb = false
        // it may be that the oldValues map is full of null values because the EntityValue didn't come from the db
        if (dbValueMap != null) for (Object val in dbValueMap.values()) if (val != null) { dbValueMapFromDb = true; break }

        Map oldValues = dbValueMap
        if (ed.needsAuditLog()) {
            boolean needsDbValue = true
            if (oldValues != null) {
                needsDbValue = !dbValueMapFromDb
            } else {
                oldValues = new HashMap()
            }
            if (needsDbValue) {
                EntityValue dbValue = (EntityValue) this.clone()
                dbValue.refresh()
                oldValues.putAll(dbValue)
            }
        }

        List<String> pkFieldList = ed.getPkFieldNames()

        ListOrderedSet nonPkAllFieldList = ed.getFieldNames(false, true)
        ListOrderedSet nonPkFieldList = new ListOrderedSet()
        for (String fieldName in nonPkAllFieldList) {
            if (valueMap.containsKey(fieldName) &&
                    (!dbValueMapFromDb || valueMap.get(fieldName) != dbValueMap.get(fieldName))) {
                nonPkFieldList.add(fieldName)
            }
        }
        if (!nonPkFieldList) {
            if (logger.infoEnabled) logger.info("Not doing update on entity with no populated non-PK fields; entity=" + this.toString())
            return
        }

        if (ed.getEntityNode()."@optimistic-lock" == "true") {
            EntityValue dbValue = (EntityValue) this.clone()
            dbValue.refresh()
            if (getTimestamp("lastUpdatedStamp") != dbValue.getTimestamp("lastUpdatedStamp"))
                throw new EntityException("This record was updated by someone else at [${getTimestamp("lastUpdatedStamp")}] which was after the version you loaded at [${dbValue.getTimestamp("lastUpdatedStamp")}]. Not updating to avoid overwriting data.")
        }

        if (ed.isField("lastUpdatedStamp") && !this.get("lastUpdatedStamp"))
            this.set("lastUpdatedStamp", new Timestamp(getEntityFacadeImpl().ecfi.getTransactionFacade().getCurrentTransactionStartTime()))

        // call the abstract method
        this.updateExtended(pkFieldList, nonPkFieldList)

        handleAuditLog(true, oldValues)

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "update", false)
        // count the artifact hit
        getEntityFacadeImpl().ecfi.countArtifactHit("entity", "update", ed.getEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), 1)
        // pop the ArtifactExecutionInfo to clean it up
        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().pop()

        return this
    }
    abstract void updateExtended(List<String> pkFieldList, ListOrderedSet nonPkFieldList)

    /** @see org.moqui.entity.EntityValue#delete() */
    EntityValue delete() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()

        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_DELETE"),
                ed.entityNode."@authorize-skip" != "true")

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "delete", true)

        // call the abstract method
        this.deleteExtended()

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "delete", false)
        // count the artifact hit
        getEntityFacadeImpl().ecfi.countArtifactHit("entity", "delete", ed.getEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), 1)
        // pop the ArtifactExecutionInfo to clean it up
        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().pop()

        return this
    }
    abstract void deleteExtended()

    /** @see org.moqui.entity.EntityValue#refresh() */
    boolean refresh() {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = getEntityDefinition()

        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_VIEW"),
                ed.entityNode."@authorize-skip" != "true")

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "find-one", true)

        List<String> pkFieldList = ed.getPkFieldNames()
        if (pkFieldList.size() == 0) throw new EntityException("Entity ${getEntityName()} has no primary key fields, cannot do refresh.")

        // call the abstract method
        boolean retVal = this.refreshExtended()

        getEntityFacadeImpl().runEecaRules(this.getEntityName(), this, "find-one", false)
        // count the artifact hit
        getEntityFacadeImpl().ecfi.countArtifactHit("entity", "refresh", ed.getEntityName(), this.getPrimaryKeys(),
                startTime, System.currentTimeMillis(), retVal ? 1 : 0)
        // pop the ArtifactExecutionInfo to clean it up
        getEntityFacadeImpl().ecfi.getExecutionContext().getArtifactExecution().pop()

        return retVal
    }
    abstract boolean refreshExtended()
}
