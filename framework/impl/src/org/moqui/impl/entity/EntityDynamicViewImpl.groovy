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

class EntityDynamicViewImpl implements EntityDynamicView {

    protected EntityFindImpl entityFind;

    protected String entityName = "DynamicView"
    protected Node entityNode = new Node(null, "view-entity", ["entity-name":"DynamicView"])

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
    EntityDynamicView addMemberEntity(String entityAlias, String entityName) {
        this.entityNode.appendNode("member-entity", ["entity-alias":entityAlias, "entity-name":entityName])
        return this
    }

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
    EntityDynamicView addAlias(String entityAlias, String name, String field, Boolean groupBy, String function) {
        this.entityNode.appendNode("alias", ["entity-alias":entityAlias, "name":name, "field":field, "group-by":(groupBy ? "true" : "false"), "function":function])
        return this
    }

    @Override
    EntityDynamicView addViewLink(String entityAlias, String relatedEntityAlias, Boolean relatedOptional, Map<String, String> entityKeyMaps) {
        Node viewLink = this.entityNode.appendNode("alias", ["entity-alias":entityAlias, "related-entity-alias":relatedEntityAlias, "related-optional":(relatedOptional ? "true" : "false")])
        for (Map.Entry keyMapEntry in entityKeyMaps.entrySet()) {
            viewLink.appendNode("key-map", ["field-name":keyMapEntry.getKey(), "related-field-name":keyMapEntry.getValue()])
        }
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
