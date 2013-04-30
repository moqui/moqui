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
package org.moqui.entity;

import java.util.Map;

/** This class is used for declaring Dynamic View Entities, to be used and thrown away.
 * A special method exists on the EntityFind to accept a EntityDynamicView instead of an entityName.
 * The methods here return a reference to itself (this) for convenience.
 */
public interface EntityDynamicView {
    /** This optionally sets a name for the dynamic view entity. If not used will default to "DynamicView" */
    public EntityDynamicView setEntityName(String entityName);

    public EntityDynamicView addMemberEntity(String entityAlias, String entityName, String joinFromAlias,
                                             Boolean joinOptional, Map<String, String> entityKeyMaps);

    public EntityDynamicView addRelationshipMember(String entityAlias, String joinFromAlias, String relationshipName,
                                                   Boolean joinOptional);

    public EntityDynamicView addAliasAll(String entityAlias, String prefix);

    public EntityDynamicView addAlias(String entityAlias, String name);

    /** Add an alias, full detail. All parameters can be null except entityAlias and name. */
    public EntityDynamicView addAlias(String entityAlias, String name, String field, String function);

    public EntityDynamicView addRelationship(String type, String title, String relatedEntityName,
                                             Map<String, String> entityKeyMaps);
}
