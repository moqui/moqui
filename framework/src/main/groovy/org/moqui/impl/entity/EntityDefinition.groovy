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
import org.moqui.impl.StupidUtilities

import javax.sql.rowset.serial.SerialBlob
import java.sql.Timestamp

import org.apache.commons.collections.set.ListOrderedSet
import org.apache.commons.collections.map.ListOrderedMap

import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityException
import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.impl.entity.condition.ConditionField
import org.moqui.impl.entity.condition.FieldValueCondition
import org.moqui.impl.entity.condition.FieldToFieldCondition
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.moqui.BaseException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class EntityDefinition {
    protected final static Logger logger = LoggerFactory.getLogger(EntityDefinition.class)

    protected EntityFacadeImpl efi
    protected String internalEntityName
    protected String fullEntityName
    protected String shortAlias
    protected String groupName = null
    protected Node internalEntityNode
    protected final Map<String, Boolean> fieldSimpleMap = new HashMap<String, Boolean>()
    protected final Map<String, Node> fieldNodeMap = new HashMap<String, Node>()
    // TODO: get rid of this, refactor code to use getRelationshipMap()
    protected final Map<String, Node> relationshipNodeMap = new HashMap<String, Node>()
    protected final Map<String, String> columnNameMap = new HashMap<String, String>()
    protected List<String> pkFieldNameList = null
    protected List<String> allFieldNameList = null
    protected Boolean hasUserFields = null
    protected Boolean allowUserField = null
    protected Map<String, Map> mePkFieldToAliasNameMapMap = null

    protected Boolean isView = null
    protected Boolean needsAuditLogVal = null
    protected Boolean needsEncryptVal = null
    protected Boolean createOnlyVal = null

    protected List<Node> expandedRelationshipList = null
    // this is kept separately for quick access to relationships by name or short-alias
    protected Map<String, RelationshipInfo> relationshipInfoMap = null
    protected List<RelationshipInfo> relationshipInfoList = null

    EntityDefinition(EntityFacadeImpl efi, Node entityNode) {
        this.efi = efi
        // copy the entityNode because we may be modifying it
        this.internalEntityNode = StupidUtilities.deepCopyNode(entityNode)
        this.internalEntityName = (internalEntityNode."@entity-name").intern()
        this.fullEntityName = (internalEntityNode."@package-name" + "." + this.internalEntityName).intern()
        this.shortAlias = internalEntityNode."@short-alias" ?: null

        if (isViewEntity()) {
            // get group-name, etc from member-entity
            for (Node memberEntity in internalEntityNode."member-entity") {
                EntityDefinition memberEd = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
                Node memberEntityNode = memberEd.getEntityNode()
                if (memberEntityNode."@group-name") internalEntityNode.attributes().put("group-name", memberEntityNode."@group-name")
            }
            // if this is a view-entity, expand the alias-all elements into alias elements here
            this.expandAliasAlls()
            // set @type, set is-pk on all alias Nodes if the related field is-pk
            for (Node aliasNode in internalEntityNode."alias") {
                Node memberEntity = (Node) internalEntityNode."member-entity".find({ it."@entity-alias" == aliasNode."@entity-alias" })
                if (memberEntity == null) {
                    if (aliasNode."complex-alias") {
                        continue
                    } else {
                        throw new EntityException("Could not find member-entity with entity-alias [${aliasNode."@entity-alias"}] in view-entity [${internalEntityName}]")
                    }
                }
                EntityDefinition memberEd = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
                String fieldName = aliasNode."@field" ?: aliasNode."@name"
                Node fieldNode = memberEd.getFieldNode(fieldName)
                if (fieldNode == null) throw new EntityException("In view-entity [${internalEntityName}] alias [${aliasNode."@name"}] referred to field [${fieldName}] that does not exist on entity [${memberEd.internalEntityName}].")
                aliasNode.attributes().put("type", fieldNode.attributes().get("type"))
                if (fieldNode."@is-pk" == "true") aliasNode."@is-pk" = "true"
            }
            for (Node aliasNode in internalEntityNode."alias") {
                fieldNodeMap.put((String) aliasNode."@name", aliasNode)
            }
        } else {
            if (internalEntityNode."@no-update-stamp" != "true") {
                // automatically add the lastUpdatedStamp field
                internalEntityNode.appendNode("field", [name:"lastUpdatedStamp", type:"date-time"])
            }
            if (internalEntityNode."@allow-user-field" == "true") allowUserField = true

            for (Node fieldNode in this.internalEntityNode.field) {
                fieldNodeMap.put((String) fieldNode."@name", fieldNode)
            }
        }
    }

    String getEntityName() { return this.internalEntityName }
    String getFullEntityName() { return this.fullEntityName }
    String getShortAlias() { return this.shortAlias }

    Node getEntityNode() { return this.internalEntityNode }

    boolean isViewEntity() {
        if (isView == null) isView = (this.internalEntityNode.name() == "view-entity")
        return isView
    }
    boolean hasFunctionAlias() { return isViewEntity() && this.internalEntityNode."alias".find({ it."@function" }) }

    String getEntityGroupName() {
        if (groupName == null) {
            if (entityNode."@is-dynamic-view" == "true") {
                Node entityNode = this.internalEntityNode
                // use the name of the first member-entity
                String memberEntityName = entityNode."member-entity".find({ !it."@join-from-alias" })?."@entity-name"
                groupName = efi.getEntityGroupName(memberEntityName)
            }
            groupName = entityNode."@group-name" ?: efi.getDefaultGroupName()
        }
        return groupName
    }

    String getDefaultDescriptionField() {
        ListOrderedSet nonPkFields = getFieldNames(false, true, false)
        // find the first *Name
        for (String fn in nonPkFields)
            if (fn.endsWith("Name")) return fn

        // no name? try literal description
        if (isField("description")) return "description"

        // no description? just use the first non-pk field: nonPkFields.get(0)
        // not any more, can be confusing... just return empty String
        return ""
    }

    boolean createOnly() {
        if (createOnlyVal != null) return createOnlyVal
        createOnlyVal = internalEntityNode."@create-only" == "true"
        return createOnlyVal
    }

    boolean needsAuditLog() {
        if (needsAuditLogVal != null) return needsAuditLogVal
        needsAuditLogVal = false
        for (Node fieldNode in getFieldNodes(true, true, false))
            if (getFieldAuditLog(fieldNode) == "true" || getFieldAuditLog(fieldNode) == "update") needsAuditLogVal = true
        if (needsAuditLogVal) return true

        for (Node fieldNode in getFieldNodes(false, false, true))
            if (getFieldAuditLog(fieldNode) == "true" || getFieldAuditLog(fieldNode) == "update") needsAuditLogVal = true
        return needsAuditLogVal
    }
    String getFieldAuditLog(Node fieldNode) {
        String fieldAuditLog = fieldNode."@enable-audit-log"
        if (fieldAuditLog) return fieldAuditLog
        return internalEntityNode."@enable-audit-log"
    }

    boolean needsEncrypt() {
        if (needsEncryptVal != null) return needsEncryptVal
        needsEncryptVal = false
        for (Node fieldNode in getFieldNodes(true, true, false)) {
            if (fieldNode."@encrypt" == "true") needsEncryptVal = true
        }
        if (needsEncryptVal) return true

        for (Node fieldNode in getFieldNodes(false, false, true)) {
            if (fieldNode."@encrypt" == "true") needsEncryptVal = true
        }

        return needsEncryptVal
    }

    Node getFieldNode(String fieldName) {
        Node fn = fieldNodeMap.get(fieldName)
        if (fn != null) return fn

        if (allowUserField && !this.isViewEntity() && !fieldName.contains('.')) {
            // if fieldName has a dot it is likely a relationship name, so don't look for UserField

            boolean alreadyDisabled = efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz()
            try {
                EntityList userFieldList = efi.find("moqui.entity.UserField").condition("entityName", getFullEntityName()).useCache(true).list()
                if (userFieldList) {
                    Set<String> userGroupIdSet = efi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet()
                    for (EntityValue userField in userFieldList) {
                        if (userField.fieldName != fieldName) continue
                        if (userGroupIdSet.contains(userField.userGroupId)) {
                            fn = makeUserFieldNode(userField)
                            break
                        }
                    }
                }
            } finally {
                if (!alreadyDisabled) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
            }
        }

        return fn
    }

    protected Node makeUserFieldNode(EntityValue userField) {
        String fieldType = userField.fieldType ?: "text-long"
        if (fieldType == "text-very-long" || fieldType == "binary-very-long")
            throw new EntityException("UserField for entityName ${getFullEntityName()}, fieldName ${userField.fieldName} and userGroupId ${userField.userGroupId} has a fieldType that is not allowed: ${fieldType}")

        Node fieldNode = new Node(null, "field", [name: userField.fieldName, type: fieldType, "is-user-field": "true"])

        fieldNode.attributes().put("user-group-id", userField.userGroupId)
        if (userField.enableAuditLog == "Y") fieldNode.attributes().put("enable-audit-log", "true")
        if (userField.enableAuditLog == "U") fieldNode.attributes().put("enable-audit-log", "update")
        if (userField.enableLocalization == "Y") fieldNode.attributes().put("enable-localization", "true")
        if (userField.encrypt == "Y") fieldNode.attributes().put("encrypt", "true")

        return fieldNode
    }

    Node getRelationshipNode(String relationshipName) {
        Node relNode = relationshipNodeMap.get(relationshipName)
        if (relNode != null) return relNode

        String relatedEntityName = relationshipName.contains("#") ? relationshipName.substring(relationshipName.indexOf("#") + 1) : relationshipName
        String title = relationshipName.contains("#") ? relationshipName.substring(0, relationshipName.indexOf("#")) : null

        // if no 'title' see if relationshipName is actually a short-alias
        if (!title) {
            relNode = (Node) this.internalEntityNode."relationship".find({ it."@short-alias" == relationshipName })
            if (relNode != null) {
                // this isn't really necessary, we already found the relationship Node, but may be useful
                relatedEntityName = relNode."@related-entity-name"
                title = relNode."@title"
            }
        }

        if (relNode == null) {
            EntityDefinition relatedEd = null
            try {
                relatedEd = efi.getEntityDefinition(relatedEntityName)
            } catch (EntityException e) {
                // ignore if entity doesn't exist
                if (logger.isTraceEnabled()) logger.trace("Ignoring entity not found exception: ${e.toString()}")
                return null
            }

            relNode = (Node) this.internalEntityNode."relationship"
                    .find({ ((it."@title" ?: "") + it."@related-entity-name") == relationshipName ||
                        ((it."@title" ?: "") + "#" + it."@related-entity-name") == relationshipName ||
                        (relatedEd != null && it."@title" == title &&
                            (it."@related-entity-name" == relatedEd.getEntityName() ||
                                    it."@related-entity-name" == relatedEd.getFullEntityName())) })
        }

        // handle automatic reverse-many nodes (based on one node coming the other way)
        if (relNode == null) {
            // see if there is an entity matching the relationship name that has a relationship coming this way
            EntityDefinition relEd = null
            try {
                relEd = efi.getEntityDefinition(relatedEntityName)
            } catch (EntityException e) {
                // probably means not a valid entity name, which may happen a lot since we're checking here to see, so just ignore
                if (logger.isTraceEnabled()) logger.trace("Ignoring entity not found exception: ${e.toString()}")
            }
            if (relEd != null) {
                // don't call ed.getRelationshipNode(), may result in infinite recursion
                Node reverseRelNode = (Node) relEd.internalEntityNode."relationship".find(
                        { ((it."@related-entity-name" == this.internalEntityName || it."@related-entity-name" == this.fullEntityName)
                            && (it."@type" == "one" || it."@type" == "one-nofk") && ((!title && !it."@title") || it."@title" == title)) })
                if (reverseRelNode != null) {
                    Map keyMap = getRelationshipExpandedKeyMap(reverseRelNode, this)
                    String relType = (this.getPkFieldNames() == relEd.getPkFieldNames()) ? "one-nofk" : "many"
                    Node newRelNode = this.internalEntityNode.appendNode("relationship",
                            ["related-entity-name":relatedEntityName, "type":relType])
                    if (title) newRelNode.attributes().put("title", title)
                    for (Map.Entry keyEntry in keyMap) {
                        // add a key-map with the reverse fields
                        newRelNode.appendNode("key-map", ["field-name":keyEntry.value, "related-field-name":keyEntry.key])
                    }
                    relNode = newRelNode
                }
            }
        }

        relationshipNodeMap.put(relationshipName, relNode)
        return relNode
    }

    static Map getRelationshipExpandedKeyMap(Node relationship, EntityDefinition relEd) {
        ListOrderedMap eKeyMap = new ListOrderedMap()
        if (!relationship."key-map" && ((String) relationship."@type").startsWith("one")) {
            // go through pks of related entity, assume field names match
            for (String pkFieldName in relEd.getPkFieldNames()) eKeyMap.put(pkFieldName, pkFieldName)
        } else {
            for (Node keyMap in relationship."key-map") {
                String relFn = keyMap."@related-field-name" ?: keyMap."@field-name"
                if (!relEd.isField(relFn) && ((String) relationship."@type").startsWith("one")) {
                    List<String> pks = relEd.getPkFieldNames()
                    if (pks.size() == 1) relFn = pks.get(0)
                    // if we don't match these constraints and get this default we'll get an error later...
                }
                eKeyMap.put(keyMap."@field-name", relFn)
            }
        }
        return eKeyMap
    }

    RelationshipInfo getRelationshipInfo(String relationshipName) {
        if (!relationshipName) return null
        return getRelationshipInfoMap().get(relationshipName)
    }

    Map<String, RelationshipInfo> getRelationshipInfoMap() {
        if (relationshipInfoMap != null) return relationshipInfoMap
        relationshipInfoMap = new HashMap<String, RelationshipInfo>()
        List<RelationshipInfo> relInfoList = getRelationshipsInfo(false)
        for (RelationshipInfo relInfo in relInfoList) {
            relationshipInfoMap.put(relInfo.relationshipName, relInfo)
            if (relInfo.shortAlias) relationshipInfoMap.put(relInfo.shortAlias, relInfo)
        }
        return relationshipInfoMap
    }

    List<RelationshipInfo> getRelationshipsInfo(boolean dependentsOnly) {
        if (relationshipInfoList == null) makeRelInfoList()

        List<RelationshipInfo> infoListCopy = []
        for (RelationshipInfo info in relationshipInfoList) if (!dependentsOnly || info.dependent) infoListCopy.add(info)
        return infoListCopy
    }
    private synchronized void makeRelInfoList() {
        if (relationshipInfoList != null) return

        if (!this.expandedRelationshipList) {
            // make sure this is done before as this isn't done by default
            efi.createAllAutoReverseManyRelationships()
            this.expandedRelationshipList = this.internalEntityNode."relationship"
        }

        List<RelationshipInfo> infoList = []
        for (Node relNode in this.expandedRelationshipList) {
            RelationshipInfo relInfo = new RelationshipInfo(relNode, this, efi)
            infoList.add(relInfo)
        }
        relationshipInfoList = infoList
    }

    static class RelationshipInfo {
        String type
        String title
        String relatedEntityName
        EntityDefinition fromEd
        EntityDefinition relatedEd
        Node relNode

        String relationshipName
        String shortAlias
        String prettyName
        Map keyMap
        boolean dependent

        RelationshipInfo(Node relNode, EntityDefinition fromEd, EntityFacadeImpl efi) {
            this.relNode = relNode
            type = relNode.'@type'
            title = relNode.'@title' ?: ''
            relatedEntityName = relNode.'@related-entity-name'
            this.fromEd = fromEd
            relatedEd = efi.getEntityDefinition(relatedEntityName)
            relatedEntityName = relatedEd.getFullEntityName()

            relationshipName = (title ? title + '#' : '') + relatedEntityName
            shortAlias = relNode.'@short-alias' ?: ''
            prettyName = relatedEd.getPrettyName(title, fromEd.internalEntityName)
            keyMap = getRelationshipExpandedKeyMap(relNode, relatedEd)
            dependent = hasReverse()
        }

        private boolean hasReverse() {
            Node reverseRelNode = (Node) relatedEd.internalEntityNode."relationship".find(
                    { ((it."@related-entity-name" == fromEd.internalEntityName || it."@related-entity-name" == fromEd.fullEntityName)
                            && (it."@type" == "one" || it."@type" == "one-nofk")
                            && ((!title && !it."@title") || it."@title" == title)) })
            return reverseRelNode != null
        }

        Map getTargetParameterMap(Map valueSource) {
            if (!valueSource) return [:]
            Map targetParameterMap = new HashMap()
            for (Map.Entry keyEntry in keyMap) {
                Object value = valueSource.get(keyEntry.key)
                if (!StupidUtilities.isEmpty(value)) targetParameterMap.put(keyEntry.value, value)
            }
            return targetParameterMap
        }
    }

    EntityDependents getDependentsTree(Deque<String> ancestorEntities) {
        EntityDependents edp = new EntityDependents()
        edp.entityName = fullEntityName
        edp.ed = this

        if (ancestorEntities == null) ancestorEntities = new LinkedList()
        ancestorEntities.addFirst(this.fullEntityName)

        List<RelationshipInfo> relInfoList = getRelationshipsInfo(true)
        for (RelationshipInfo relInfo in relInfoList) {
            edp.allDescendants.add((String) relInfo.relatedEntityName)
            String relName = (String) relInfo.relationshipName
            edp.relationshipInfos.put(relName, relInfo)
            // if (relInfo.shortAlias) edp.relationshipInfos.put((String) relInfo.shortAlias, relInfo)
            EntityDefinition relEd = efi.getEntityDefinition((String) relInfo.relatedEntityName)
            if (!edp.dependentEntities.containsKey(relName) && !ancestorEntities.contains(relEd.fullEntityName)) {
                EntityDependents relEpd = relEd.getDependentsTree(ancestorEntities)
                edp.allDescendants.addAll(relEpd.allDescendants)
                edp.dependentEntities.put(relName, relEpd)
            }
        }

        ancestorEntities.removeFirst()

        return edp
    }

    static class EntityDependents {
        String entityName
        EntityDefinition ed
        Map<String, EntityDependents> dependentEntities = new HashMap()
        Set<String> allDescendants = new HashSet()
        Map<String, RelationshipInfo> relationshipInfos = new HashMap()

        String toString() {
            StringBuilder builder = new StringBuilder()
            buildString(builder, 0)
            return builder.toString()
        }
        static final String indentBase = '- '
        void buildString(StringBuilder builder, int level) {
            StringBuilder ib = new StringBuilder()
            for (int i = 0; i < level; i++) ib.append(indentBase)
            String indent = ib.toString()

            // builder.append(indent).append(entityName).append('\n')
            for (Map.Entry<String, EntityDependents> entry in dependentEntities) {
                RelationshipInfo relInfo = relationshipInfos.get(entry.getKey())
                builder.append(indent).append(indentBase).append(relInfo.relationshipName).append(' ').append(relInfo.keyMap).append('\n')
                entry.getValue().buildString(builder, level+1)
            }
        }
    }

    String getPrettyName(String title, String baseName) {
        StringBuilder prettyName = new StringBuilder()
        for (String part in internalEntityName.split("(?=[A-Z])")) {
            if (baseName && part == baseName) continue
            if (prettyName) prettyName.append(" ")
            prettyName.append(part)
        }
        if (title) {
            boolean addParens = prettyName as boolean
            if (addParens) prettyName.append(" (")
            for (String part in title.split("(?=[A-Z])")) prettyName.append(part).append(" ")
            prettyName.deleteCharAt(prettyName.length()-1)
            if (addParens) prettyName.append(")")
        }
        return prettyName.toString()
    }

    String getColumnName(String fieldName, boolean includeFunctionAndComplex) {
        String cn = columnNameMap.get(fieldName)
        if (cn != null) return cn

        Node fieldNode = this.getFieldNode(fieldName)
        if (!fieldNode) {
            throw new EntityException("Invalid field-name [${fieldName}] for the [${this.getFullEntityName()}] entity")
        }

        if (isViewEntity()) {
            // NOTE: for view-entity the incoming fieldNode will actually be for an alias element
            StringBuilder colNameBuilder = new StringBuilder()
            if (includeFunctionAndComplex) {
                // column name for view-entity (prefix with "${entity-alias}.")
                //colName.append(fieldNode."@entity-alias").append('.')
                if (logger.isTraceEnabled()) logger.trace("For view-entity include function and complex not yet supported, for entity [${internalEntityName}], may get bad SQL...")
            }
            // else {

            if (fieldNode."complex-alias") {
                String function = fieldNode."@function"
                if (function) {
                    colNameBuilder.append(getFunctionPrefix(function))
                }
                buildComplexAliasName(fieldNode, "+", colNameBuilder)
                if (function) colNameBuilder.append(')')
            } else {
                String function = fieldNode."@function"
                if (function) {
                    colNameBuilder.append(getFunctionPrefix(function))
                }
                // column name for view-entity (prefix with "${entity-alias}.")
                colNameBuilder.append(fieldNode."@entity-alias").append('.')

                String memberFieldName = fieldNode."@field" ?: fieldNode."@name"
                colNameBuilder.append(getBasicFieldColName(internalEntityNode, (String) fieldNode."@entity-alias", memberFieldName))

                if (function) colNameBuilder.append(')')
            }

            // }
            cn = colNameBuilder.toString()
        } else {
            if (fieldNode."@column-name") {
                cn = fieldNode."@column-name"
            } else {
                cn = camelCaseToUnderscored((String) fieldNode."@name")
            }
        }

        columnNameMap.put(fieldName, cn)
        return cn
    }

    protected String getBasicFieldColName(Node entityNode, String entityAlias, String fieldName) {
        Node memberEntity = (Node) entityNode."member-entity".find({ it."@entity-alias" == entityAlias })
        EntityDefinition memberEd = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
        return memberEd.getColumnName(fieldName, false)
    }

    protected void buildComplexAliasName(Node parentNode, String operator, StringBuilder colNameBuilder) {
        colNameBuilder.append('(')
        boolean isFirst = true
        for (Node childNode in (List<Node>) parentNode.children()) {
            if (isFirst) isFirst=false else colNameBuilder.append(' ').append(operator).append(' ')

            if (childNode.name() == "complex-alias") {
                buildComplexAliasName(childNode, (String) childNode."@operator", colNameBuilder)
            } else if (childNode.name() == "complex-alias-field") {
                String entityAlias = childNode."@entity-alias"
                String basicColName = getBasicFieldColName(internalEntityNode, entityAlias, (String) childNode."@field")
                String colName = entityAlias + "." + basicColName
                String defaultValue = childNode."@default-value"
                String function = childNode."@function"

                if (defaultValue) {
                    colName = "COALESCE(" + colName + "," + defaultValue + ")"
                }
                if (function) {
                    String prefix = getFunctionPrefix(function)
                    colName = prefix + colName + ")"
                }

                colNameBuilder.append(colName)
            }
        }
        colNameBuilder.append(')')
    }

    static protected String getFunctionPrefix(String function) {
        return (function == "count-distinct") ? "COUNT(DISTINCT " : function.toUpperCase() + '('
    }

    /** Returns the table name, ie table-name or converted entity-name */
    String getTableName() {
        if (this.internalEntityNode."@table-name") {
            return this.internalEntityNode."@table-name"
        } else {
            return camelCaseToUnderscored((String) this.internalEntityNode."@entity-name")
        }
    }

    String getFullTableName() {
        if (efi.getDatabaseNode(getEntityGroupName())?."@use-schemas" != "false") {
            String schemaName = getSchemaName()
            return schemaName ? schemaName + "." + getTableName() : getTableName()
        } else {
            return getTableName()
        }
    }

    String getSchemaName() {
        String schemaName = efi.getDatasourceNode(getEntityGroupName())?."@schema-name"
        return schemaName ?: null
    }

    boolean isField(String fieldName) { return getFieldNode(fieldName) != null }
    boolean isPkField(String fieldName) {
        Node fieldNode = getFieldNode(fieldName)
        if (fieldNode == null) return false
        return fieldNode."@is-pk" == "true"
    }
    boolean isSimpleField(String fieldName) {
        Boolean isSimpleVal = fieldSimpleMap.get(fieldName)
        if (isSimpleVal != null) return isSimpleVal

        Node fieldNode = getFieldNode(fieldName)
        boolean isSimple = fieldNode != null && !(fieldNode."@enable-localization" == "true") && !(fieldNode."@is-user-field" == "true")
        fieldSimpleMap.put(fieldName, isSimple)
        return isSimple
    }

    boolean containsPrimaryKey(Map fields) {
        if (!fields) return false
        if (!getPkFieldNames()) return false
        for (String fieldName in getPkFieldNames()) if (!fields[fieldName]) return false
        return true
    }

    Map<String, Object> getPrimaryKeys(Map fields) {
        Map<String, Object> pks = new HashMap()
        for (String fieldName in this.getPkFieldNames()) pks.put(fieldName, fields[fieldName])
        return pks
    }

    ListOrderedSet getFieldNames(boolean includePk, boolean includeNonPk, boolean includeUserFields) {
        ListOrderedSet nameSet = new ListOrderedSet()
        String nodeName = this.isViewEntity() ? "alias" : "field"
        for (Node node in (Collection<Node>) this.internalEntityNode[nodeName]) {
            if ((includePk && node."@is-pk" == "true") || (includeNonPk && node."@is-pk" != "true")) {
                nameSet.add(node."@name")
            }
        }

        if (includeUserFields && allowUserField && !this.isViewEntity()) {
            boolean alreadyDisabled = efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz()
            try {
                EntityList userFieldList = efi.find("moqui.entity.UserField").condition("entityName", getFullEntityName()).useCache(true).list()
                if (userFieldList) {
                    Set<String> userGroupIdSet = efi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet()
                    for (EntityValue userField in userFieldList) {
                        if (userGroupIdSet.contains(userField.userGroupId)) nameSet.add((String) userField.fieldName)
                    }
                }
            } finally {
                if (!alreadyDisabled) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
            }
        }

        return nameSet
    }
    List<String> getPkFieldNames() {
        if (pkFieldNameList == null) {
            pkFieldNameList = Collections.unmodifiableList(new ArrayList(getFieldNames(true, false, false).asList()))
        }
        return pkFieldNameList
    }
    List<String> getAllFieldNames() {
        if (allFieldNameList == null) {
            allFieldNameList = Collections.unmodifiableList(new ArrayList(getFieldNames(true, true, false).asList()))
        }

        if (!allowUserField || (hasUserFields != null && !hasUserFields)) return allFieldNameList

        List<String> returnList = null

        // add UserFields to it if needed
        boolean alreadyDisabled = efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz()
        try {
            EntityList userFieldList = efi.find("moqui.entity.UserField").condition("entityName", getFullEntityName()).useCache(true).list()
            if (userFieldList) {
                hasUserFields = true

                Set<String> userGroupIdSet = efi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet()
                Set<String> userFieldNames = new HashSet<String>()
                for (EntityValue userField in userFieldList) {
                    if (userGroupIdSet.contains(userField.userGroupId)) userFieldNames.add((String) userField.fieldName)
                }
                if (userFieldNames) {
                    returnList = new ArrayList<String>(allFieldNameList)
                    returnList.addAll(userFieldNames)
                }
            } else {
                hasUserFields = false
            }
        } finally {
            if (!alreadyDisabled) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }

        return returnList ? Collections.unmodifiableList(returnList) : allFieldNameList
    }

    List<Node> getFieldNodes(boolean includePk, boolean includeNonPk, boolean includeUserFields) {
        // NOTE: this is not necessarily the fastest way to do this, if it becomes a performance problem replace it with a local List of field Nodes
        List<Node> nodeList = new ArrayList<Node>()
        String nodeName = this.isViewEntity() ? "alias" : "field"
        for (Node node in (Collection<Node>) this.internalEntityNode[nodeName]) {
            if ((includePk && node."@is-pk" == "true") || (includeNonPk && node."@is-pk" != "true")) {
                nodeList.add(node)
            }
        }

        if (includeUserFields && allowUserField && !this.isViewEntity()) {
            boolean alreadyDisabled = efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz()
            try {
                EntityList userFieldList = efi.find("moqui.entity.UserField").condition("entityName", getFullEntityName()).useCache(true).list()
                if (userFieldList) {
                    Set<String> userGroupIdSet = efi.getEcfi().getExecutionContext().getUser().getUserGroupIdSet()
                    for (EntityValue userField in userFieldList) {
                        if (userGroupIdSet.contains(userField.userGroupId)) {
                            nodeList.add(makeUserFieldNode(userField))
                            break
                        }
                    }
                }
            } finally {
                if (!alreadyDisabled) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
            }
        }

        return nodeList
    }

    Map getMePkFieldToAliasNameMap(String entityAlias) {
        if (mePkFieldToAliasNameMapMap == null) mePkFieldToAliasNameMapMap = new HashMap<String, Map>()
        Map mePkFieldToAliasNameMap = mePkFieldToAliasNameMapMap.get(entityAlias)

        //logger.warn("TOREMOVE 1 getMePkFieldToAliasNameMap entityAlias=${entityAlias} cached value=${mePkFieldToAliasNameMap}; entityNode=${entityNode}")
        if (mePkFieldToAliasNameMap != null) return mePkFieldToAliasNameMap

        mePkFieldToAliasNameMap = new HashMap()

        // do a reverse map on member-entity pk fields to view-entity aliases
        Node memberEntityNode = (Node) entityNode."member-entity".find({ it."@entity-alias" == entityAlias })
        //logger.warn("TOREMOVE 2 getMePkFieldToAliasNameMap entityAlias=${entityAlias} memberEntityNode=${memberEntityNode}")
        EntityDefinition med = this.efi.getEntityDefinition((String) memberEntityNode."@entity-name")
        List<String> pkFieldNames = med.getPkFieldNames()
        for (String pkName in pkFieldNames) {
            Node matchingAliasNode = (Node) entityNode."alias".find({
                it."@entity-alias" == memberEntityNode."@entity-alias" &&
                (it."@field" == pkName || (!it."@field" && it."@name" == pkName)) })
            //logger.warn("TOREMOVE 3 getMePkFieldToAliasNameMap entityAlias=${entityAlias} for pkName=${pkName}, matchingAliasNode=${matchingAliasNode}")
            if (matchingAliasNode) {
                // found an alias Node
                mePkFieldToAliasNameMap.put(pkName, matchingAliasNode."@name")
                continue
            }

            // no alias, try to find in join key-maps that map to other aliased fields

            // first try the current member-entity
            if (memberEntityNode."@join-from-alias" && memberEntityNode."key-map") {
                boolean foundOne = false
                for (Node keyMapNode in memberEntityNode."key-map") {
                    //logger.warn("TOREMOVE 4 getMePkFieldToAliasNameMap entityAlias=${entityAlias} for pkName=${pkName}, keyMapNode=${keyMapNode}")
                    if (keyMapNode."@related-field-name" == pkName ||
                            (!keyMapNode."@related-field-name" && keyMapNode."@field-name" == pkName)) {
                        String relatedPkName = keyMapNode."@field-name"
                        Node relatedMatchingAliasNode = (Node) entityNode."alias".find({
                            it."@entity-alias" == memberEntityNode."@join-from-alias" &&
                            (it."@field" == relatedPkName || (!it."@field" && it."@name" == relatedPkName)) })
                        //logger.warn("TOREMOVE 5 getMePkFieldToAliasNameMap entityAlias=${entityAlias} for pkName=${pkName}, relatedAlias=${memberEntityNode.'@join-from-alias'}, relatedPkName=${relatedPkName}, relatedMatchingAliasNode=${relatedMatchingAliasNode}")
                        if (relatedMatchingAliasNode) {
                            mePkFieldToAliasNameMap.put(pkName, relatedMatchingAliasNode."@name")
                            foundOne = true
                            break
                        }
                    }
                }
                if (foundOne) continue
            }

            // then go through all other member-entity that might relate back to this one
            for (Node relatedMeNode in entityNode."member-entity") {
                if (relatedMeNode."@join-from-alias" == memberEntityNode."@entity-alias" && relatedMeNode."key-map") {
                    boolean foundOne = false
                    for (Node keyMapNode in relatedMeNode."key-map") {
                        if (keyMapNode."@field-name" == pkName) {
                            String relatedPkName = keyMapNode."@related-field-name" ?: keyMapNode."@field-name"
                            Node relatedMatchingAliasNode = (Node) entityNode."alias".find({
                                it."@entity-alias" == relatedMeNode."@entity-alias" &&
                                (it."@field" == relatedPkName || (!it."@field" && it."@name" == relatedPkName)) })
                            if (relatedMatchingAliasNode) {
                                mePkFieldToAliasNameMap.put(pkName, relatedMatchingAliasNode."@name")
                                foundOne = true
                                break
                            }
                        }
                    }
                    if (foundOne) break
                }
            }
        }

        if (pkFieldNames.size() != mePkFieldToAliasNameMap.size()) {
            logger.warn("Not all primary-key fields in view-entity [${fullEntityName}] for member-entity [${memberEntityNode.'@entity-name'}], skipping cache reverse-association, and note that if this record is updated the cache won't automatically clear; pkFieldNames=${pkFieldNames}; partial mePkFieldToAliasNameMap=${mePkFieldToAliasNameMap}")
        }

        return mePkFieldToAliasNameMap
    }

    Map cloneMapRemoveFields(Map theMap, Boolean pks) {
        Map newMap = new HashMap(theMap)
        for (String fieldName in (pks != null ? this.getFieldNames(pks, !pks, !pks) : this.getAllFieldNames())) {
            if (newMap.containsKey(fieldName)) newMap.remove(fieldName)
        }
        return newMap
    }

    void setFields(Map<String, ?> src, Map<String, Object> dest, boolean setIfEmpty, String namePrefix, Boolean pks) {
        if (src == null) return

        EntityValue ev = src instanceof EntityValue ? (EntityValue) src : null
        for (String fieldName in (pks != null ? this.getFieldNames(pks, !pks, !pks) : this.getAllFieldNames())) {
            String sourceFieldName
            if (namePrefix) {
                sourceFieldName = namePrefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)
            } else {
                sourceFieldName = fieldName
            }

            if (ev != null ? ev.isFieldSet(sourceFieldName) : src.containsKey(sourceFieldName)) {
                Object value = src.get(sourceFieldName)
                if (!StupidUtilities.isEmpty(value)) {
                    if (value instanceof CharSequence) {
                        try {
                            this.setString(fieldName, value.toString(), dest)
                        } catch (BaseException be) {
                            this.efi.ecfi.executionContext.message.addValidationError(null, fieldName, null, be.getMessage(), be)
                        }
                    } else {
                        dest.put(fieldName, value)
                    }
                } else if (setIfEmpty && src.containsKey(sourceFieldName)) {
                    // treat empty String as null, otherwise set as whatever null or empty type it is
                    if (value != null && value instanceof CharSequence) {
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
        Node fieldNode = this.getFieldNode(name)
        if (fieldNode == null) dest.put(name, value) // cause an error on purpose
        dest.put(name, convertFieldString(name, value))
    }

    Object convertFieldString(String name, String value) {
        if (value == "null") value = null

        Object outValue
        Node fieldNode = this.getFieldNode(name)

        String fieldType = fieldNode."@type"
        String javaType = fieldType ? (EntityFacadeImpl.fieldTypeJavaMap.get(fieldType) ?: efi.getFieldJavaType(fieldType, this)) : "String"
        Integer typeValue = (fieldType ? EntityFacadeImpl.fieldTypeIntMap.get(fieldType) : null) ?: EntityFacadeImpl.getJavaTypeInt(javaType)

        boolean isEmpty = value.length() == 0

        try {
            switch (typeValue) {
                case 1: outValue = value; break
                case 2: // outValue = java.sql.Timestamp.valueOf(value);
                    if (isEmpty) { outValue = null; break }
                    outValue = efi.getEcfi().getL10nFacade().parseTimestamp(value, null)
                    if (outValue == null) throw new BaseException("The value [${value}] is not a valid date/time")
                    break
                case 3: // outValue = java.sql.Time.valueOf(value);
                    if (isEmpty) { outValue = null; break }
                    outValue = efi.getEcfi().getL10nFacade().parseTime(value, null)
                    if (outValue == null) throw new BaseException("The value [${value}] is not a valid time")
                    break
                case 4: // outValue = java.sql.Date.valueOf(value);
                    if (isEmpty) { outValue = null; break }
                    outValue = efi.getEcfi().getL10nFacade().parseDate(value, null)
                    if (outValue == null) throw new BaseException("The value [${value}] is not a valid date")
                    break
                case 5: // outValue = Integer.valueOf(value); break
                case 6: // outValue = Long.valueOf(value); break
                case 7: // outValue = Float.valueOf(value); break
                case 8: // outValue = Double.valueOf(value); break
                case 9: // outValue = new BigDecimal(value); break
                    if (isEmpty) { outValue = null; break }
                    BigDecimal bdVal = efi.getEcfi().getL10nFacade().parseNumber(value, null)
                    if (bdVal == null) {
                        throw new BaseException("The value [${value}] is not valid for type [${javaType}]")
                    } else {
                        outValue = StupidUtilities.basicConvert(bdVal.stripTrailingZeros(), javaType)
                    }
                    break
                case 10:
                    if (isEmpty) { outValue = null; break }
                    outValue = Boolean.valueOf(value); break
                case 11: outValue = value; break
                case 12: outValue = new SerialBlob(value.getBytes()); break
                case 13: outValue = value; break
                case 14:
                    if (isEmpty) { outValue = null; break }
                    outValue = value.asType(Date.class); break
            // better way for Collection (15)? maybe parse comma separated, but probably doesn't make sense in the first place
                case 15: outValue = value; break
                default: outValue = value; break
            }
        } catch (IllegalArgumentException e) {
            throw new BaseException("The value [${value}] is not valid for type [${javaType}]", e)
        }

        return outValue
    }

    String getFieldString(String name, Object value) {
        if (value == null) return null

        String outValue
        Node fieldNode = this.getFieldNode(name)

        String fieldType = fieldNode."@type"
        String javaType = fieldType ? (EntityFacadeImpl.fieldTypeJavaMap.get(fieldType) ?: efi.getFieldJavaType(fieldType, this)) : "String"
        Integer typeValue = (fieldType ? EntityFacadeImpl.fieldTypeIntMap.get(fieldType) : null) ?: EntityFacadeImpl.getJavaTypeInt(javaType)

        try {
            switch (typeValue) {
                case 1: outValue = value; break
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    if (value instanceof BigDecimal) value = ((BigDecimal) value).stripTrailingZeros()
                    outValue = efi.getEcfi().getL10nFacade().format(value, null)
                    break
                case 10: outValue = value.toString(); break
                case 11: outValue = value; break
                case 12:
                    if (value instanceof byte[]) {
                        outValue = new String(Base64.encodeBase64((byte[]) value));
                    } else {
                        logger.info("Field [${name}] on entity [${entityName}] is not of type 'byte[]', is [${value}] so using plain toString()")
                        outValue = value.toString()
                    }
                    break
                case 13: outValue = value; break
                case 14: outValue = value.toString(); break
            // better way for Collection (15)? maybe parse comma separated, but probably doesn't make sense in the first place
                case 15: outValue = value; break
                default: outValue = value; break
            }
        } catch (IllegalArgumentException e) {
            throw new BaseException("The value [${value}] is not valid for type [${javaType}]", e)
        }

        return outValue
    }

    String getFieldStringForFile(String name, Object value) {
        if (value == null) return null

        String outValue
        if (value instanceof Timestamp) {
            // use a Long number, no TZ issues
            outValue = ((Timestamp) value).getTime().toString()
        } else if (value instanceof BigDecimal) {
            outValue = ((BigDecimal) value).toPlainString()
        } else {
            outValue = getFieldString(name, value)
        }

        return outValue
    }

    protected void expandAliasAlls() {
        if (!isViewEntity()) return
        for (Node aliasAll: this.internalEntityNode."alias-all") {
            Node memberEntity = (Node) this.internalEntityNode."member-entity".find({ it."@entity-alias" == aliasAll."@entity-alias" })
            if (!memberEntity) {
                logger.error("In alias-all with entity-alias [${aliasAll."@entity-alias"}], member-entity with same entity-alias not found, ignoring")
                continue;
            }

            EntityDefinition aliasedEntityDefinition = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
            if (!aliasedEntityDefinition) {
                logger.error("Entity [${memberEntity."@entity-name"}] referred to in member-entity with entity-alias [${aliasAll."@entity-alias"}] not found, ignoring")
                continue;
            }

            for (Node fieldNode in aliasedEntityDefinition.internalEntityNode."field") {
                // never auto-alias these
                if (fieldNode."@name" == "lastUpdatedStamp") continue
                // if specified as excluded, leave it out
                if (aliasAll."exclude".find({ it."@field" == fieldNode."@name"})) continue

                String aliasName = fieldNode."@name"
                if (aliasAll."@prefix") {
                    StringBuilder newAliasName = new StringBuilder((String) aliasAll."@prefix")
                    newAliasName.append(Character.toUpperCase(aliasName.charAt(0)))
                    newAliasName.append(aliasName.substring(1))
                    aliasName = newAliasName.toString()
                }

                Node existingAliasNode = (Node) this.internalEntityNode.alias.find({ it."@name" == aliasName })
                if (existingAliasNode) {
                    //log differently if this is part of a member-entity view link key-map because that is a common case when a field will be auto-expanded multiple times
                    boolean isInViewLink = false
                    for (Node viewMeNode in this.internalEntityNode."member-entity") {
                        boolean isRel = false
                        if (viewMeNode."@entity-alias" == aliasAll."@entity-alias") {
                            isRel = true
                        } else if (!viewMeNode."@join-from-alias" == aliasAll."@entity-alias") {
                            // not the rel-entity-alias or the entity-alias, so move along
                            continue;
                        }
                        for (Node keyMap in viewMeNode."key-map") {
                            if (!isRel && keyMap."@field-name" == fieldNode."@name") {
                                isInViewLink = true
                                break
                            } else if (isRel && (keyMap."@related-field-name" ?: keyMap."@field-name") == fieldNode."@name") {
                                isInViewLink = true
                                break
                            }
                        }
                        if (isInViewLink) break
                    }

                    // already exists... probably an override, but log just in case
                    String warnMsg = "Throwing out field alias in view entity " + this.getFullEntityName() +
                            " because one already exists with the alias name [" + aliasName + "] and field name [" +
                            memberEntity."@entity-alias" + "(" + aliasedEntityDefinition.getFullEntityName() + ")." +
                            fieldNode."@name" + "], existing field name is [" + existingAliasNode."@entity-alias" + "." +
                            existingAliasNode."@field" + "]"
                    if (isInViewLink) {if (logger.isTraceEnabled()) logger.trace(warnMsg)} else {logger.info(warnMsg)}

                    // ship adding the new alias
                    continue
                }

                Node newAlias = this.internalEntityNode.appendNode("alias",
                        [name:aliasName, field:fieldNode."@name", "entity-alias":aliasAll."@entity-alias",
                        "if-from-alias-all":true])
                if (fieldNode."description") newAlias.appendNode(fieldNode."description")
            }
        }
    }

    EntityConditionImplBase makeViewWhereCondition() {
        if (!this.isViewEntity()) return null
        // add the view-entity.entity-condition.econdition(s)
        Node entityCondition = this.internalEntityNode."entity-condition"[0]
        return makeViewListCondition(entityCondition)
    }
    protected EntityConditionImplBase makeViewListCondition(Node conditionsParent) {
        if (conditionsParent == null) return null
        List<EntityConditionImplBase> condList = new ArrayList()
        for (Node dateFilter in conditionsParent."date-filter") {
            // NOTE: this doesn't do context expansion of the valid-date as it doesn't make sense for an entity def to depend on something being in the context
            condList.add((EntityConditionImplBase) this.efi.conditionFactory.makeConditionDate(
                    (String) dateFilter."@from-field-name", (String) dateFilter."@thru-field-name",
                    dateFilter."@valid-date" ? efi.getEcfi().getResourceFacade().evaluateStringExpand((String) dateFilter."@valid-date", "") as Timestamp : null))
        }
        for (Node econdition in conditionsParent."econdition") {
            EntityConditionImplBase cond;
            ConditionField field
            if (econdition."@entity-alias") {
                Node memberEntity = (Node) this.internalEntityNode."member-entity".find({ it."@entity-alias" == econdition."@entity-alias"})
                if (!memberEntity) throw new EntityException("The entity-alias [${econdition."@entity-alias"}] was not found in view-entity [${this.internalEntityName}]")
                EntityDefinition aliasEntityDef = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
                field = new ConditionField((String) econdition."@entity-alias", (String) econdition."@field-name", aliasEntityDef)
            } else {
                field = new ConditionField((String) econdition."@field-name")
            }
            if (econdition."@value" != null) {
                // NOTE: may need to convert value from String to object for field
                String condValue = econdition."@value" ?: null
                // NOTE: only expand if contains "${", expanding normal strings does l10n and messes up key values; hopefully this won't result in a similar issue
                if (condValue && condValue.contains("\${")) condValue = efi.getEcfi().getResourceFacade().evaluateStringExpand(condValue, "")
                cond = new FieldValueCondition((EntityConditionFactoryImpl) this.efi.conditionFactory, field,
                        EntityConditionFactoryImpl.getComparisonOperator((String) econdition."@operator"), condValue)
            } else {
                ConditionField toField
                if (econdition."@to-entity-alias") {
                    Node memberEntity = (Node) this.internalEntityNode."member-entity".find({ it."@entity-alias" == econdition."@to-entity-alias"})
                    if (!memberEntity) throw new EntityException("The entity-alias [${econdition."@to-entity-alias"}] was not found in view-entity [${this.internalEntityName}]")
                    EntityDefinition aliasEntityDef = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
                    toField = new ConditionField((String) econdition."@to-entity-alias", (String) econdition."@to-field-name", aliasEntityDef)
                } else {
                    toField = new ConditionField((String) econdition."@to-field-name")
                }
                cond = new FieldToFieldCondition((EntityConditionFactoryImpl) this.efi.conditionFactory, field,
                        EntityConditionFactoryImpl.getComparisonOperator((String) econdition."@operator"), toField)
            }
            if (cond && econdition."@ignore-case" == "true") cond.ignoreCase()

            if (cond && econdition."@or-null" == "true") {
                cond = (EntityConditionImplBase) this.efi.conditionFactory.makeCondition(cond, JoinOperator.OR,
                        new FieldValueCondition((EntityConditionFactoryImpl) this.efi.conditionFactory, field, EntityCondition.EQUALS, null))
            }

            if (cond) condList.add(cond)
        }
        for (Node econditions in conditionsParent."econditions") {
            EntityConditionImplBase cond = this.makeViewListCondition(econditions)
            if (cond) condList.add(cond)
        }
        if (!condList) return null
        if (condList.size() == 1) return (EntityConditionImplBase) condList.get(0)
        JoinOperator op = (conditionsParent."@combine" == "or" ? JoinOperator.OR : JoinOperator.AND)
        EntityConditionImplBase entityCondition = (EntityConditionImplBase) this.efi.conditionFactory.makeCondition((List<EntityCondition>) condList, op)
        // logger.info("============== In makeViewListCondition for entity [${entityName}] resulting entityCondition: ${entityCondition}")
        return entityCondition
    }

    EntityConditionImplBase makeViewHavingCondition() {
        if (!this.isViewEntity()) return null
        // add the view-entity.entity-condition.having-econditions
        Node havingEconditions = (Node) this.internalEntityNode."entity-condition"?.getAt(0)?."having-econditions"?.getAt(0)
        if (!havingEconditions) return null
        return makeViewListCondition(havingEconditions)
    }

    @Override
    int hashCode() {
        return this.internalEntityName.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getClass()) return false
        EntityDefinition that = (EntityDefinition) o
        if (!this.internalEntityName.equals(that.internalEntityName)) return false
        return true
    }

    protected static Map<String, String> camelToUnderscoreMap = new HashMap()
    static String camelCaseToUnderscored(String camelCase) {
        if (!camelCase) return ""
        String usv = camelToUnderscoreMap.get(camelCase)
        if (usv) return usv

        StringBuilder underscored = new StringBuilder()
        underscored.append(Character.toUpperCase(camelCase.charAt(0)))
        int inPos = 1
        while (inPos < camelCase.length()) {
            char curChar = camelCase.charAt(inPos)
            if (Character.isUpperCase(curChar)) underscored.append('_')
            underscored.append(Character.toUpperCase(curChar))
            inPos++
        }

        usv = underscored.toString()
        camelToUnderscoreMap.put(camelCase, usv)
        return usv
    }
}
