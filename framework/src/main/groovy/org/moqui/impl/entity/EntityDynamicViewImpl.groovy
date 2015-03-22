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

import org.moqui.entity.EntityDynamicView
import org.moqui.entity.EntityException

class EntityDynamicViewImpl implements EntityDynamicView {

    protected EntityFindImpl entityFind;

    protected String entityName = "DynamicView"
    protected Node entityNode = new Node(null, "view-entity", ["package-name":"moqui.internal", "entity-name":"DynamicView", "is-dynamic-view":"true"])

    EntityDynamicViewImpl(EntityFindImpl entityFind) {
        this.entityFind = entityFind
    }

    EntityDefinition makeEntityDefinition() {
        return new EntityDefinition(this.entityFind.efi, this.entityNode)
    }

    @Override
    EntityDynamicView setEntityName(String entityName) {
        this.entityNode."@entity-name" = entityName
        return this
    }

    @Override
    EntityDynamicView addMemberEntity(String entityAlias, String entityName, String joinFromAlias, Boolean joinOptional,
                                      Map<String, String> entityKeyMaps) {
        Node memberEntity = this.entityNode.appendNode("member-entity", ["entity-alias":entityAlias, "entity-name":entityName])
        if (joinFromAlias) {
            memberEntity.attributes().put("join-from-alias", joinFromAlias)
            memberEntity.attributes().put("join-optional", (joinOptional ? "true" : "false"))
        }
        if (entityKeyMaps) for (Map.Entry keyMapEntry in entityKeyMaps.entrySet()) {
            memberEntity.appendNode("key-map", ["field-name":keyMapEntry.getKey(), "related-field-name":keyMapEntry.getValue()])
        }
        return this
    }

    @Override
    EntityDynamicView addRelationshipMember(String entityAlias, String joinFromAlias, String relationshipName,
                                            Boolean joinOptional) {
        Node joinFromMemberEntityNode = (Node) this.entityNode."member-entity".find({ it."@entity-alias" == joinFromAlias })
        String entityName = joinFromMemberEntityNode."@entity-name"
        EntityDefinition joinFromEd = entityFind.getEfi().getEntityDefinition(entityName)
        Node relationshipNode = joinFromEd.getRelationshipNode(relationshipName)
        if (relationshipNode == null) throw new EntityException("Relationship not found with name [${relationshipName}] on entity [${entityName}]")

        String relatedEntityName = relationshipNode."@related-entity-name"
        Map relationshipKeyMap = EntityDefinition.getRelationshipExpandedKeyMap(relationshipNode,
                entityFind.getEfi().getEntityDefinition(relatedEntityName))

        Node memberEntity = this.entityNode.appendNode("member-entity", ["entity-alias":entityAlias, "entity-name":relatedEntityName])
        memberEntity.attributes().put("join-from-alias", joinFromAlias)
        memberEntity.attributes().put("join-optional", (joinOptional ? "true" : "false"))
        for (Map.Entry keyMapEntry in relationshipKeyMap.entrySet()) {
            memberEntity.appendNode("key-map", ["field-name":keyMapEntry.getKey(), "related-field-name":keyMapEntry.getValue()])
        }
        return this
    }

    Node getViewEntityNode() { return entityNode }

    @Override
    List<Node> getMemberEntityNodes() { return this.entityNode."member-entity" }

    @Override
    EntityDynamicView addAliasAll(String entityAlias, String prefix) {
        this.entityNode.appendNode("alias-all", ["entity-alias":entityAlias, "prefix":prefix])
        return this
    }

    @Override
    EntityDynamicView addAlias(String entityAlias, String name) {
        this.entityNode.appendNode("alias", ["entity-alias":entityAlias, "name":name])
        return this
    }

    @Override
    EntityDynamicView addAlias(String entityAlias, String name, String field, String function) {
        this.entityNode.appendNode("alias", ["entity-alias":entityAlias, "name":name, "field":field, "function":function])
        return this
    }

    @Override
    EntityDynamicView addRelationship(String type, String title, String relatedEntityName, Map<String, String> entityKeyMaps) {
        Node viewLink = this.entityNode.appendNode("relationship", ["type":type, "title":title, "related-entity-name":relatedEntityName])
        for (Map.Entry keyMapEntry in entityKeyMaps.entrySet()) {
            viewLink.appendNode("key-map", ["field-name":keyMapEntry.getKey(), "related-field-name":keyMapEntry.getValue()])
        }
        return this
    }
}
