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

import org.moqui.impl.entity.EntityConditionFactoryImpl.EntityConditionImplBase
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityCondition

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
            // TODO: set is-pk on all alias Nodes, or change code to look up other entities to get pk/nonPk lists
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
        return (Node) this.entityNode[nodeName].find({ it.@name == fieldName })[0]
    }

    String getColumnName(String fieldName, boolean includeFunctionAndComplex) {
        Node fieldNode = this.getFieldNode(fieldName)
        if (!fieldNode) {
            throw new IllegalArgumentException("Invalid field-name [${fieldName}] for the [${this.getEntityName()}] entity")
        }

        if (isViewEntity()) {
            // NOTE: for view-entity the incoming fieldNode will actually be for an alias element

            // TODO: column name for view-entity (prefix with "${entity-alias}.")
            if (includeFunctionAndComplex) {
                // TODO: column name view-entity complex-alias (build expression based on complex-alias)
                // TODO: column name for view-entity alias with function (wrap in function, after complex-alias to wrap that too when used)
            }
            return null
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

    boolean isField(String fieldName) {
        // NOTE: this is not necessarily the fastest way to do this, if it becomes a performance problem replace it with a local Set of field names
        return (this.getFieldNode(fieldName)) ? true : false
    }

    TreeSet<String> getFieldNames(boolean includePk, boolean includeNonPk) {
        // NOTE: this is not necessarily the fastest way to do this, if it becomes a performance problem replace it with a local Set of field names
        TreeSet<String> nameSet = new TreeSet()
        String nodeName = this.isViewEntity() ? "alias" : "field"
        for (Node node in this.entityNode[nodeName]) {
            if ((includePk && node."@is-pk" == "true") || (includeNonPk && node."@is-pk" != "true")) {
                nameSet.add(node."@name")
            }
        }
        return nameSet
    }

    protected void expandAliasAlls() {
        if (!isViewEntity()) return
        for (Node aliasAll: this.entityNode."alias-all") {
            Node memberEntity = (Node) this.entityNode."member-entity".find({ it."@entity-alias" = aliasAll."@entity-alias" })[0]
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

                Node existingAliasNode = (Node) this.entityNode.alias.find({ it."@name" == aliasName })[0]
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
                if (fieldNode.description) newAlias.appendNode(fieldNode.description[0].clone())
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
        List<EntityConditionImplBase> condList = new ArrayList()
        for (Node dateFilter in conditionsParent."date-filter") {
            /*
            <xs:attribute type="xs:string" name="valid-date">
            <xs:attribute type="xs:string" name="from-field-name" default="fromDate">
            <xs:attribute type="xs:string" name="thru-field-name" default="thruDate">
             */
            // TODO impl this
        }
        for (Node econdition in conditionsParent."econdition") {
            // TODO impl this
        }
        for (Node econditions in conditionsParent."econditions") {
            EntityConditionImplBase cond = this.makeViewListCondition(econditions)
            if (cond) condList.add(cond)
        }
        if (!condList) return null
        if (condList.size() == 1) return condList.get(0)
        JoinOperator op = (conditionsParent."@combine" == "or" ? JoinOperator.OR : JoinOperator.AND)
        return (EntityConditionImplBase) this.efi.conditionFactory.makeCondition((List<EntityCondition>) condList, op)
    }

    EntityConditionImplBase makeViewHavingCondition() {
        if (!this.isViewEntity()) return null
        // add the view-entity.entity-condition.having-econditions
        Node havingEconditions = this.entityNode."entity-condition"[0]."having-econditions"[0]
        return makeViewListCondition(havingEconditions)
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
