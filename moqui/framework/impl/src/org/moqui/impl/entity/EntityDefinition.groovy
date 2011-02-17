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
import org.apache.commons.collections.set.ListOrderedSet
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityCondition
import org.moqui.impl.entity.EntityConditionFactoryImpl.EntityConditionImplBase
import org.moqui.impl.entity.EntityConditionFactoryImpl.ConditionField
import org.moqui.impl.entity.EntityConditionFactoryImpl.FieldToFieldCondition
import org.moqui.impl.entity.EntityConditionFactoryImpl.FieldValueCondition

public class EntityDefinition {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityDefinition.class)

    protected EntityFacadeImpl efi
    protected String entityName
    protected Node entityNode

    EntityDefinition(EntityFacadeImpl efi, Node entityNode) {
        this.efi = efi
        this.entityName = entityNode."@entity-name"
        this.entityNode = entityNode

        if (isViewEntity()) {
            // if this is a view-entity, expand the alias-all elements into alias elements here
            this.expandAliasAlls()
            // set @type, set is-pk on all alias Nodes if the related field is-pk
            for (Node aliasNode in entityNode."alias") {
                Node memberEntity = (Node) entityNode."member-entity".find({ it."@entity-alias" == aliasNode."@entity-alias" })
                if (memberEntity == null) throw new IllegalArgumentException("Could not find member-entity with entity-alias [${aliasNode."@entity-alias"}] in view-entity [${entityName}]")
                EntityDefinition memberEd = this.efi.getEntityDefinition(memberEntity."@entity-name")
                Node fieldNode = memberEd.getFieldNode(aliasNode."@field" ?: aliasNode."@name")
                aliasNode.attributes().put("type", fieldNode.attributes().get("type"))
                if (fieldNode."@is-pk" == "true") aliasNode."@is-pk" = "true"
            }
        } else {
            if (!"false" == this.entityNode."@no-update-stamp") {
                // automatically add the lastUpdatedStamp field
                this.entityNode.appendNode("field", [name:"lastUpdatedStamp", type:"date-time"])
            }
        }
    }

    String getEntityName() {
        return this.entityName
    }

    Node getEntityNode() {
        return this.entityNode
    }

    boolean isViewEntity() {
        return this.entityNode.name() == "view-entity"
    }

    Node getFieldNode(String fieldName) {
        String nodeName = this.isViewEntity() ? "alias" : "field"
        return (Node) this.entityNode[nodeName].find({ it.@name == fieldName })
    }

    Node getRelationshipNode(String relationshipName) {
        return (Node) this.entityNode."relationship".find(
                { "${it.'@title'}${it.'@related-entity-name'}" == relationshipName })
    }

    Map getRelationshipExpandedKeyMap(Node relationship) {
        Map eKeyMap = new HashMap()
        EntityDefinition relEd = this.efi.getEntityDefinition(relationship."@related-entity-name")
        if (!relEd) throw new IllegalArgumentException("Could not find entity [${relationship."@related-entity-name"}] referred to in a relationship in entity [${entityName}]")
        if (!relationship."key-map" && ((String) relationship."@type").startsWith("one")) {
            // go through pks of related entity, assume field names match
            ListOrderedSet pkFieldNames = relEd.getFieldNames(true, false)
            for (String pkFieldName in pkFieldNames) eKeyMap.put(pkFieldName, pkFieldName)
        } else {
            for (Node keyMap in relationship."key-map") eKeyMap.put(keyMap."@field-name",
                    keyMap."@related-field-name" ? keyMap."@related-field-name" : keyMap."@field-name")
        }
        return eKeyMap
    }

    String getColumnName(String fieldName, boolean includeFunctionAndComplex) {
        Node fieldNode = this.getFieldNode(fieldName)
        if (!fieldNode) {
            throw new IllegalArgumentException("Invalid field-name [${fieldName}] for the [${this.getEntityName()}] entity")
        }

        if (isViewEntity()) {
            // NOTE: for view-entity the incoming fieldNode will actually be for an alias element
            StringBuilder colName = new StringBuilder()
            if (includeFunctionAndComplex) {
                // TODO: column name view-entity complex-alias (build expression based on complex-alias)
                // TODO: column name for view-entity alias with function (wrap in function, after complex-alias to wrap that too when used)

                // column name for view-entity (prefix with "${entity-alias}.")
                //colName.append(fieldNode."@entity-alias").append('.')
                logger.trace("For view-entity include function and complex not yet supported, for entity [${entityName}], may get bad SQL...")
            }
            // else {
                // column name for view-entity (prefix with "${entity-alias}.")
                colName.append(fieldNode."@entity-alias").append('.')

                Node memberEntity = (Node) entityNode."member-entity".find({ it."@entity-alias" == fieldNode."@entity-alias" })
                EntityDefinition memberEd = this.efi.getEntityDefinition(memberEntity."@entity-name")
                String memberFieldName = fieldNode."@field" ? fieldNode."@field" : fieldNode."@name"
                colName.append(memberEd.getColumnName(memberFieldName, false))
            // }
            return colName.toString()
        } else {
            if (fieldNode."@column-name") {
                return fieldNode."@column-name"
            } else {
                return camelCaseToUnderscored(fieldNode."@name")
            }
        }
    }

    /** Returns the table name, ie table-name or converted entity-name */
    String getTableName() {
        if (this.entityNode."@table-name") {
            return this.entityNode."@table-name"
        } else {
            return camelCaseToUnderscored(this.entityNode."@entity-name")
        }
    }

    String getFullTableName() {
        if (efi.getDatabaseNode(efi.getEntityGroupName(entityName))?."@use-schemas" != "false") {
            String schemaName = getSchemaName()
            return schemaName ? schemaName + "." + getTableName() : getTableName()
        } else {
            return getTableName()
        }
    }

    String getSchemaName() {
        String schemaName = efi.getDatasourceNode(efi.getEntityGroupName(entityName))?."@schema-name"
        return schemaName ?: null
    }

    boolean isField(String fieldName) {
        // NOTE: this is not necessarily the fastest way to do this, if it becomes a performance problem replace it with a local Set of field names
        return (this.getFieldNode(fieldName)) ? true : false
    }

    boolean containsPrimaryKey(Map fields) {
        for (String fieldName in this.getFieldNames(true, false)) {
            if (!fields[fieldName]) return false
        }
        return true
    }

    Map<String, Object> getPrimaryKeys(Map fields) {
        Map<String, Object> pks = new HashMap()
        for (String fieldName in this.getFieldNames(true, false)) {
            pks.put(fieldName, fields[fieldName])
        }
        return pks
    }

    ListOrderedSet getFieldNames(boolean includePk, boolean includeNonPk) {
        // NOTE: this is not necessarily the fastest way to do this, if it becomes a performance problem replace it with a local Set of field names
        ListOrderedSet nameSet = new ListOrderedSet()
        String nodeName = this.isViewEntity() ? "alias" : "field"
        for (Node node in this.entityNode[nodeName]) {
            if ((includePk && node."@is-pk" == "true") || (includeNonPk && node."@is-pk" != "true")) {
                nameSet.add(node."@name")
            }
        }
        return nameSet
    }

    void setFields(Map<String, ?> src, Map<String, Object> dest, boolean setIfEmpty, String namePrefix, Boolean pks) {
        if (src == null) return

        Set fieldNameSet
        if (pks != null) {
            fieldNameSet = this.getFieldNames(pks, !pks)
        } else {
            fieldNameSet = this.getFieldNames(true, true)
        }

        for (String fieldName in fieldNameSet) {
            String sourceFieldName
            if (namePrefix) {
                sourceFieldName = namePrefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)
            } else {
                sourceFieldName = fieldName
            }

            if (src.containsKey(sourceFieldName)) {
                Object value = src.get(sourceFieldName)
                if (value) {
                    if (value instanceof String) {
                        this.setString(fieldName, (String) value, dest)
                    } else {
                        dest.put(fieldName, value)
                    }
                } else if (setIfEmpty) {
                    // treat empty String as null, otherwise set as whatever null or empty type it is
                    if (value != null && value instanceof String) {
                        dest.put(fieldName, null)
                    } else {
                        dest.put(fieldName, value)
                    }
                }
            }
        }
    }

    void setString(String name, String value, Map<String, Object> dest) {
        if (value == null || value == "null") {
            dest.put(name, null)
            return
        }
        if (value == "\null") value == "null"

        Node fieldNode = this.getFieldNode(name)
        if (!fieldNode) dest.put(name, value) // cause an error on purpose

        String javaType = this.efi.getFieldJavaType(fieldNode."@type", entityName)
        switch (EntityFacadeImpl.getJavaTypeInt(javaType)) {
        case 1: dest.put(name, value); break
        case 2: dest.put(name, java.sql.Timestamp.valueOf(value)); break
        case 3: dest.put(name, java.sql.Time.valueOf(value)); break
        case 4: dest.put(name, java.sql.Date.valueOf(value)); break
        case 5: dest.put(name, Integer.valueOf(value)); break
        case 6: dest.put(name, Long.valueOf(value)); break
        case 7: dest.put(name, Float.valueOf(value)); break
        case 8: dest.put(name, Double.valueOf(value)); break
        case 9: dest.put(name, new BigDecimal(value)); break
        case 10: dest.put(name, Boolean.valueOf(value)); break
        case 11: dest.put(name, value); break
        // better way for Blob (12)? probably not...
        case 12: dest.put(name, value); break
        case 13: dest.put(name, value); break
        case 14: dest.put(name, value.asType(java.util.Date.class)); break
        // better way for Collection (15)? maybe parse comma separated, but probably doesn't make sense in the first place
        case 15: dest.put(name, value); break
        }
    }

    protected void expandAliasAlls() {
        if (!isViewEntity()) return
        for (Node aliasAll: this.entityNode."alias-all") {
            Node memberEntity = (Node) this.entityNode."member-entity".find({ it."@entity-alias" == aliasAll."@entity-alias" })
            if (!memberEntity) {
                logger.error("In alias-all with entity-alias [${aliasAll."@entity-alias"}], member-entity with same entity-alias not found, ignoring")
                continue;
            }

            EntityDefinition aliasedEntityDefinition = this.efi.getEntityDefinition(memberEntity."@entity-name")
            if (!aliasedEntityDefinition) {
                logger.error("Entity [${memberEntity."@entity-name"}] referred to in member-entity with entity-alias [${aliasAll."@entity-alias"}] not found, ignoring")
                continue;
            }

            for (Node fieldNode in aliasedEntityDefinition.entityNode.field) {
                // never auto-alias these
                if (fieldNode."@name" == "lastUpdatedStamp") continue
                // if specified as excluded, leave it out
                if (aliasAll.exclude.find({ it.@field == fieldNode."@name"})) continue

                String aliasName = fieldNode."@name"
                if (aliasAll.@prefix) {
                    StringBuilder newAliasName = new StringBuilder((String) aliasAll.@prefix)
                    newAliasName.append(Character.toUpperCase(aliasName.charAt(0)))
                    newAliasName.append(aliasName.substring(1))
                    aliasName = newAliasName.toString()
                }

                Node existingAliasNode = (Node) this.entityNode.alias.find({ it."@name" == aliasName })
                if (existingAliasNode) {
                    //log differently if this is part of a view-link key-map because that is a common case when a field will be auto-expanded multiple times
                    boolean isInViewLink = false
                    for (Node viewLink in this.entityNode."view-link") {
                        boolean isRel = false
                        if (viewLink."@related-entity-alias" == aliasAll."@entity-alias") {
                            isRel = true
                        } else if (!viewLink."@entity-alias" == aliasAll."@entity-alias") {
                            // not the rel-entity-alias or the entity-alias, so move along
                            continue;
                        }
                        for (Node keyMap in viewLink."key-map") {
                            if (!isRel && keyMap."@field-name" == fieldNode."@name") {
                                isInViewLink = true
                                break
                            } else if (isRel && keyMap."@related-field-name" == fieldNode."@name") {
                                isInViewLink = true
                                break
                            }
                        }
                        if (isInViewLink) break
                    }

                    //already exists, oh well... probably an override, but log just in case
                    String warnMsg = "Throwing out field alias in view entity " + this.entityName +
                            " because one already exists with the alias name [" + aliasName + "] and field name [" +
                            memberEntity."@entity-alias" + "(" + aliasedEntityDefinition.entityName + ")." +
                            fieldNode."@name" + "], existing field name is [" + existingAliasNode."@entity-alias" + "." +
                            existingAliasNode."@field" + "]"
                    if (isInViewLink) {logger.trace(warnMsg)} else {logger.info(warnMsg)}

                    // ship adding the new alias
                    continue
                }

                Node newAlias = this.entityNode.appendNode("alias",
                        [name:aliasName, field:fieldNode."@name",
                        "entity-alias":aliasAll."@entity-alias",
                        "if-from-alias-all":true,
                        "group-by":aliasAll."@group-by"])
                if (fieldNode.description) newAlias.appendNode(fieldNode."description")
            }
        }
    }

    EntityConditionImplBase makeViewWhereCondition() {
        if (!this.isViewEntity()) return null
        // add the view-entity.entity-condition.econdition(s)
        Node entityCondition = this.entityNode."entity-condition"[0]
        return makeViewListCondition(entityCondition)
    }
    protected EntityConditionImplBase makeViewListCondition(Node conditionsParent) {
        if (conditionsParent == null) return null
        List<EntityConditionImplBase> condList = new ArrayList()
        for (Node dateFilter in conditionsParent."date-filter") {
            // NOTE: this doesn't do context expansion of the valid-date as it doesn't make sense for an entity def to depend on something being in the context
            condList.add((EntityConditionImplBase) this.efi.conditionFactory.makeConditionDate(
                dateFilter."@from-field-name", dateFilter."@thru-field-name", dateFilter."@valid-date" as Timestamp))
        }
        for (Node econdition in conditionsParent."econdition") {
            EntityConditionImplBase cond;
            if (econdition."@value") {
                ConditionField field
                if (econdition."@entity-alias") {
                    Node memberEntity = (Node) this.entityNode."member-entity".find({ it."@entity-alias" == econdition."@entity-alias"})
                    if (!memberEntity) throw new IllegalArgumentException("The entity-alias [${econdition."@entity-alias"}] was not found in view-entity [${this.entityName}]")
                    EntityDefinition aliasEntityDef = this.efi.getEntityDefinition(memberEntity."@entity-name")
                    field = new ConditionField(econdition."@entity-alias", econdition."@field-name", aliasEntityDef)
                } else {
                    field = new ConditionField(econdition."@field-name")
                }
                // NOTE: may need to convert value from String to object for field
                cond = new FieldValueCondition(this.efi.conditionFactory, field,
                        EntityConditionFactoryImpl.getComparisonOperator(econdition."@operator"), econdition."@value")
            } else {
                ConditionField field
                if (econdition."@entity-alias") {
                    Node memberEntity = (Node) this.entityNode."member-entity".find({ it."@entity-alias" == econdition."@entity-alias"})
                    if (!memberEntity) throw new IllegalArgumentException("The entity-alias [${econdition."@entity-alias"}] was not found in view-entity [${this.entityName}]")
                    EntityDefinition aliasEntityDef = this.efi.getEntityDefinition(memberEntity."@entity-name")
                    field = new ConditionField(econdition."@entity-alias", econdition."@field-name", aliasEntityDef)
                } else {
                    field = new ConditionField(econdition."@field-name")
                }
                ConditionField toField
                if (econdition."@to-entity-alias") {
                    Node memberEntity = (Node) this.entityNode."member-entity".find({ it."@entity-alias" == econdition."@to-entity-alias"})
                    if (!memberEntity) throw new IllegalArgumentException("The entity-alias [${econdition."@to-entity-alias"}] was not found in view-entity [${this.entityName}]")
                    EntityDefinition aliasEntityDef = this.efi.getEntityDefinition(memberEntity."@entity-name")
                    toField = new ConditionField(econdition."@to-entity-alias", econdition."@to-field-name", aliasEntityDef)
                } else {
                    toField = new ConditionField(econdition."@to-field-name")
                }
                cond = new FieldToFieldCondition(this.efi.conditionFactory, field,
                        EntityConditionFactoryImpl.getComparisonOperator(econdition."@operator"), toField)
            }
            if (cond && econdition."@ignore-case" == "true") cond.ignoreCase()
            if (cond) condList.add(cond)
        }
        for (Node econditions in conditionsParent."econditions") {
            EntityConditionImplBase cond = this.makeViewListCondition(econditions)
            if (cond) condList.add(cond)
        }
        //logger.info("TOREMOVE In makeViewListCondition for entity [${entityName}] resulting condList: ${condList}")
        if (!condList) return null
        if (condList.size() == 1) return condList.get(0)
        JoinOperator op = (conditionsParent."@combine" == "or" ? JoinOperator.OR : JoinOperator.AND)
        return (EntityConditionImplBase) this.efi.conditionFactory.makeCondition((List<EntityCondition>) condList, op)
    }

    EntityConditionImplBase makeViewHavingCondition() {
        if (!this.isViewEntity()) return null
        // add the view-entity.entity-condition.having-econditions
        Node havingEconditions = (Node) this.entityNode."entity-condition"?.getAt(0)?."having-econditions"?.getAt(0)
        if (!havingEconditions) return null
        return makeViewListCondition(havingEconditions)
    }

    @Override
    int hashCode() {
        return this.entityName.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (!(o instanceof EntityDefinition)) return false
        EntityDefinition that = (EntityDefinition) o
        if (!this.entityName.equals(that.entityName)) return false
        return true
    }

    static String camelCaseToUnderscored(String camelCase) {
        if (!camelCase) return ""
        StringBuilder underscored = new StringBuilder()

        underscored.append(Character.toUpperCase(camelCase.charAt(0)))
        int inPos = 1
        while (inPos < camelCase.length()) {
            char curChar = camelCase.charAt(inPos)
            if (Character.isUpperCase(curChar)) underscored.append('_')
            underscored.append(Character.toUpperCase(curChar))
            inPos++
        }

        return underscored.toString()
    }
}
