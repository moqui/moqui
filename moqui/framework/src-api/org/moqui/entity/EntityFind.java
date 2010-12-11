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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Used to setup various options for an entity find (query).
 *
 * All methods to set options modify the option then return this modified object to allow method call chaining. It is
 * important to note that this object is not immutable and is modified internally, and returning EntityFind is just a
 * self reference for convenience.
 *
 * Even after a query a find object can be modified and then used to perform another query.
 */
public interface EntityFind extends java.io.Serializable {

    /** The Name of the Entity to use, as defined in an entity XML file.
     * @return Returns this for chaining of method calls.
     */
    EntityFind entity(String entityName);
    String getEntity();

    /** A dynamic view object to use instead of the entity name (if used the entity name will be ignored).
     * @return Returns this for chaining of method calls.
     */
    EntityFind entityDynamicView(EntityDynamicView dynamicView);
    String getEntityDynamicView();

    // ======================== Conditions (Where and Having) =================

    /** Add a field to the find (where clause). If any constraints are already in place this will be ANDed to them.
     * @return Returns this for chaining of method calls.
     */
    EntityFind condition(String fieldName, Object value);

    /** Add a Map of fields to the find (where clause). If any constraints are already in place this will be ANDed to
     *     them.
     * @return Returns this for chaining of method calls.
     */
    EntityFind condition(Map<String, ?> fields);

    /** Add a EntityCondition to the find (where clause). If any constraints are already in place this will be ANDed to
     *     them.
     * @return Returns this for chaining of method calls.
     */
    EntityFind condition(EntityCondition condition);

    /** Add a EntityCondition to the having clause of the find. If any having constraints are already in place this
     *     will be ANDed to them.
     * @return Returns this for chaining of method calls.
     */
    EntityFind havingCondition(EntityCondition condition);

    /** Get the current where EntityCondition. */
    EntityCondition getWhereEntityCondition();

    /** Get the current having EntityCondition. */
    EntityCondition getHavingEntityCondition();

    // ======================== General/Common Options ========================

    /** The field of the named entity to get from the database.
     * If any select fields have already been specified this will be added to the set.
     * @return Returns this for chaining of method calls.
     */
    EntityFind selectField(String fieldToSelect);

    /** The fields of the named entity to get from the database; if empty or null all fields will be retrieved.
     * @return Returns this for chaining of method calls.
     */
    EntityFind selectFields(Collection<String> fieldsToSelect);
    Set<String> getSelectFields();

    /** A field of the named entity to order the query by; optionally add a " ASC" to the end or "+" to the
     *     beginning for ascending, or " DESC" to the end of "-" to the beginning for descending.
     * If any other order by fields have already been specified this will be added to the end of the list.
     * @return Returns this for chaining of method calls.
     */
    EntityFind orderBy(String orderByFieldName);

    /** The fields of the named entity to order the query by; optionally add a " ASC" to the end or "+" to the
     *     beginning for ascending, or " DESC" to the end of "-" to the beginning for descending.
     * @return Returns this for chaining of method calls.
     */
    EntityFind orderBy(List<String> orderByFieldNames);
    List<String> getOrderBy();

    /** Look in the cache before finding in the datasource.
     * Defaults to setting on entity definition.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind useCache(boolean useCache);
    boolean getUseCache();

    /** Lock the selected record so only this transaction can change it until it is ended.
     * If this is set when the find is done the useCache setting will be ignored as this will always get the data from
     *     the database.
     * Default is false.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind forUpdate(boolean forUpdate);
    boolean getForUpdate();

    // ======================== Advanced Options ==============================

    /** Specifies how the ResultSet will be traversed. Available values: ResultSet.TYPE_FORWARD_ONLY,
     *      ResultSet.TYPE_SCROLL_INSENSITIVE or ResultSet.TYPE_SCROLL_SENSITIVE. See the java.sql.ResultSet JavaDoc for
     *      more information. If you want it to be fast, use the common option: ResultSet.TYPE_FORWARD_ONLY.
     *      For partial results where you want to jump to an index make sure to use TYPE_SCROLL_INSENSITIVE.
     * Defaults to TYPE_FORWARD_ONLY.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind setResultSetType(Integer resultSetType);
    int getResultSetType();

    /** Specifies whether or not the ResultSet can be updated. Available values:
     *      ResultSet.CONCUR_READ_ONLY or ResultSet.CONCUR_UPDATABLE. Should pretty much always be
     *      ResultSet.CONCUR_READ_ONLY with the Entity Facade since updates are generally done as separate operations.
     * Defaults to CONCUR_READ_ONLY.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind setResultSetConcurrency(int resultSetConcurrency);
    int getResultSetConcurrency();

    /** Specifies the fetch size for this query. Default (null) will fall back to datasource settings.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind setFetchSize(Integer fetchSize);
    int getFetchSize();

    /** Specifies the max number of rows to return. Default (null) means all rows.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind setMaxRows(Integer maxRows);
    int getMaxRows();

    /** Specifies whether the values returned should be filtered to remove duplicate values.
     * Default is false.
     *
     * @return Returns this for chaining of method calls.
     */
    EntityFind setDistinct(boolean distinct);
    boolean getDistinct();


    // ======================== Run Find Methods ==============================

    /** Runs a find with current options to get a single record by primary key.
     * This method ignores the cache setting and always gets results from the database.
     */
    EntityValue one() throws EntityException;

    /** Runs a find with current options to get a list of records.
     * This method ignores the cache setting and always gets results from the database.
     */
    List<EntityValue> list() throws EntityException;

    /** Runs a find with current options and returns an EntityListIterator object.
     * This method ignores the cache setting and always gets results from the database.
     */
    EntityListIterator iterator() throws EntityException;

    /** Runs a find with current options to get a count of matching records.
     * This method ignores the cache setting and always gets results from the database.
     */
    long count() throws EntityException;
}
