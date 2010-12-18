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

import java.util.List;
import java.util.Map;

/**
 * This class is used for declaring Dynamic View Entities, to be used and thrown away.
 * A special method exists on the EntityFacade to accept a EntityDynamicView instead
 * of an entityName.
 *
 */
public interface EntityDynamicView {
    /** Getter for property entityName.
     * @return Value of property entityName.
     *
     */
    public String getEntityName();

    /** Setter for property entityName.
     * @param entityName New value of property entityName.
     *
     */
    public void setEntityName(String entityName);

    /** Getter for property packageName.
     * @return Value of property packageName.
     *
     */
    public String getPackageName();

    /** Setter for property packageName.
     * @param packageName New value of property packageName.
     *
     */
    public void setPackageName(String packageName);

    /** Getter for property defaultResourceName.
     * @return Value of property defaultResourceName.
     *
     */
    public String getDefaultResourceName();

    /** Setter for property defaultResourceName.
     * @param defaultResourceName New value of property defaultResourceName.
     *
     */
    public void setDefaultResourceName(String defaultResourceName);

    /** Getter for property title.
     * @return Value of property title.
     *
     */
    public String getTitle();

    /** Setter for property title.
     * @param title New value of property title.
     *
     */
    public void setTitle(String title);

    public void addMemberEntity(String entityAlias, String entityName);

    public void addAliasAll(String entityAlias, String prefix);

    public void addAlias(String entityAlias, String name);

    /** Add an alias, full detail. All parameters can be null except entityAlias and name. */
    public void addAlias(String entityAlias, String name, String field, String colAlias, Boolean primKey, Boolean groupBy, String function);

    public void addViewLink(String entityAlias, String relEntityAlias, Boolean relOptional, Map<String, String> entityKeyMaps);

    public void addRelation(String type, String title, String relEntityName, Map<String, String> entityKeyMaps);
}
