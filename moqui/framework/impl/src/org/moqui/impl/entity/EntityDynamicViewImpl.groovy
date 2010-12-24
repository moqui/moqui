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
    protected Node entityNode = new Node(null, "view-entity")

    EntityDynamicViewImpl(EntityFindImpl entityFind) {
        this.entityFind = entityFind
    }

    EntityDefinition makeEntityDefinition() {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView setEntityName(String entityName) {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView addMemberEntity(String entityAlias, String entityName) {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView addAliasAll(String entityAlias, String prefix) {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView addAlias(String entityAlias, String name) {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView addAlias(String entityAlias, String name, String field, Boolean primKey, Boolean groupBy, String function) {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView addViewLink(String entityAlias, String relatedEntityAlias, Boolean relatedOptional, Map<String, String> entityKeyMaps) {
        // TODO implement this
        return null
    }

    @Override
    EntityDynamicView addRelationship(String type, String title, String relatedEntityName, Map<String, String> entityKeyMaps) {
        // TODO implement this
        return null
    }
}
