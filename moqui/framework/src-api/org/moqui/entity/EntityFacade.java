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

import java.net.URL;
import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Entity Facade Interface
 */
public interface EntityFacade {

    EntityConditionFactory getEntityConditionFactory();
    
    EntityDefinition getModelEntity(String entityName);
    
    /** Gets the group name for specified entityName
     * @param entityName The name of the entity to get the group name
     * @return String with the group name that corresponds to the entityName
     */
    String getEntityGroupName(String entityName);

    /** Creates a Entity in the form of a EntityValue without persisting it */
    EntityValue makeValue(String entityName);
    
    // ====== CrUD Methods

    /** Creates a Entity in the form of a EntityValue and write it to the datasource
     * @param value The EntityValue to create a value in the datasource from
     * @return EntityValue instance containing the new instance
     */
    EntityValue create(EntityValue value) throws EntityException;

    /** Creates or stores an Entity
     * @param value The EntityValue instance containing the new or existing instance
     * @return EntityValue instance containing the new or updated instance
     */
    EntityValue createOrUpdate(EntityValue value) throws EntityException;

   /** Store a group of values
     * @param entityName The name of the Entity as defined in the entity XML file
     * @param fieldsToSet The fields of the named entity to set in the database
     * @param condition The condition that restricts the list of stored values
     * @return int representing number of rows effected by this operation
     * @throws EntityException
     */
    int updateByCondition(String entityName, Map<String, ?> fieldsToSet, EntityCondition condition)
           throws EntityException;

    /** Store the Entity from the EntityValue to the persistent store
     * @param value EntityValue instance containing the entity
     * @return int representing number of rows effected by this operation
     */
    int update(EntityValue value) throws EntityException;

    /** Remove a Generic Value from the database
     * @param value The EntityValue object of the entity to remove.
     * @return int representing number of rows effected by this operation
     */
    int delete(EntityValue value) throws EntityException;

    /** Removes/deletes Generic Entity records found by the condition
     * @param entityName The Name of the Entity as defined in the entity XML file
     * @param condition The condition used to restrict the removing
     * @return int representing number of rows effected by this operation
     */
    int deleteByCondition(String entityName, EntityCondition condition) throws EntityException;

    /** Remove the named Related Entity for the EntityValue from the persistent store
     * @param relationName String containing the relation name which is the
     *      combination of relation.title and relation.rel-entity-name as
     *      specified in the entity XML definition file
     * @param value EntityValue instance containing the entity
     * @return int representing number of rows effected by this operation
     */
    int deleteRelated(String relationName, EntityValue value) throws EntityException;

    // ======= Find Methods

    /** Refresh the Entity for the EntityValue from the persistent store
     * @param value EntityValue instance containing the entity to refresh
     */
    void refresh(EntityValue value) throws EntityException;

    /** Find an Entity by its Primary Key
     * @param entityName The Name of the Entity as defined in the entity XML file
     * @param fields The fields of the named entity to query by with their corresponding values
     * @param useCache Look in the cache before finding in the datasource.
     * @param lockForUpdate Lock the selected record so only this transaction can change it until it is ended.
     * @return The EntityValue corresponding to the primaryKey
     */
    EntityValue findOne(String entityName, Map<String, ?> fields, boolean useCache, boolean lockForUpdate)
            throws EntityException;

    /** Finds EntityValues by the conditions specified in the EntityCondition object, the the EntityCondition javadoc
     * for more details.
     *
     * @param entityName The name of the Entity as defined in the entity XML file
     * @param whereEntityCondition The EntityCondition object that specifies how to constrain this query before any
     *     groupings are done (if this is a view entity with group-by aliases)
     * @param havingEntityCondition The EntityCondition object that specifies how to constrain this query after any
     *     groupings are done (if this is a view entity with group-by aliases)
     * @param fieldsToSelect The fields of the named entity to get from the database; if empty or null all fields will
     *     be retrieved
     * @param orderBy The fields of the named entity to order the query by; optionally add a " ASC" to the end or "+"
     *     to the beginning for ascending, or " DESC" to the end of "-" to the beginning for descending
     * @param findOptions An instance of EntityFindOptions that specifies advanced query options. See the
     *     EntityFindOptions JavaDoc for more details.
     * @return EntityListIterator representing the result of the query: NOTE THAT THIS MUST BE CLOSED (preferably in
     *     a finally block) WHEN YOU ARE DONE WITH IT, AND DON'T LEAVE IT OPEN TOO LONG BECAUSE IT WILL MAINTAIN A 
     *     DATABASE CONNECTION.
     */
    EntityListIterator find(String entityName, EntityCondition whereEntityCondition,
            EntityCondition havingEntityCondition, Set<String> fieldsToSelect, List<String> orderBy,
            EntityFindOptions findOptions) throws EntityException;

    /** Finds EntityValues by the conditions specified in the EntityCondition object, the the EntityCondition javadoc
     * for more details.
     *
     * @param entityName The name of the Entity as defined in the entity XML file
     * @param entityCondition The EntityCondition object that specifies how to constrain this query before any
     *     groupings are done (if this is a view entity with group-by aliases)
     * @param fieldsToSelect The fields of the named entity to get from the database; if empty or null all fields will
     *     be retrieved
     * @param orderBy The fields of the named entity to order the query by; optionally add a " ASC" to the end or "+"
     *     to the beginning for ascending, or " DESC" to the end of "-" to the beginning for descending
     * @param findOptions An instance of EntityFindOptions that specifies advanced query options. See the
     *     EntityFindOptions JavaDoc for more details.
     * @return List of EntityValue objects representing the result
     */
    List<EntityValue> findList(String entityName, EntityCondition entityCondition,
            Set<String> fieldsToSelect, List<String> orderBy, EntityFindOptions findOptions, boolean useCache)
            throws EntityException;

    /** Finds EntityValues by the conditions specified in the EntityCondition object, the the EntityCondition javadoc
     * for more details.
     * @param dynamicView The EntityDynamicView to use for the entity model for this query; generally created on the
     *     fly for limited use
     * @param whereEntityCondition The EntityCondition object that specifies how to constrain this query before any
     *     groupings are done (if this is a view entity with group-by aliases)
     * @param havingEntityCondition The EntityCondition object that specifies how to constrain this query after any
     *     groupings are done (if this is a view entity with group-by aliases)
     * @param fieldsToSelect The fields of the named entity to get from the database; if empty or null all fields will
     *     be retreived
     * @param orderBy The fields of the named entity to order the query by; optionally add a " ASC" to the end or "+"
     *     to the beginning for ascending, or " DESC" to the end of "-" to the beginning for descending
     * @param findOptions An instance of EntityFindOptions that specifies advanced query options. See the
     *     EntityFindOptions JavaDoc for more details.
     * @return EntityListIterator representing the result of the query: NOTE THAT THIS MUST BE CLOSED WHEN YOU ARE
     *     DONE WITH IT, AND DON'T LEAVE IT OPEN TOO LONG BECAUSE IT WILL MAINTAIN AN OPEN DATABASE CONNECTION.
     */
    EntityListIterator find(EntityDynamicView dynamicView, EntityCondition whereEntityCondition,
            EntityCondition havingEntityCondition, Collection<String> fieldsToSelect, List<String> orderBy,
            EntityFindOptions findOptions) throws EntityException;

    /** Finds a count of the records that match the given condition.
     */
    long findCount(String entityName, EntityCondition whereEntityCondition,
            EntityCondition havingEntityCondition, EntityFindOptions findOptions) throws EntityException;

    /** Get the named Related Entity for the EntityValue from the persistent store
     * @param relationName String containing the relation name which is the
     *      combination of relation.title and relation.rel-entity-name as
     *      specified in the entity XML definition file
     * @param byAndFields the fields that must equal in order to keep; may be null
     * @param orderBy The fields of the named entity to order the query by; may be null;
     *      optionally add a " ASC" for ascending or " DESC" for descending
     * @param value EntityValue instance containing the entity
     * @return List of EntityValue instances as specified in the relation definition
     */
    List<EntityValue> findRelated(String relationName, Map<String, ?> byAndFields, List<String> orderBy,
            EntityValue value) throws EntityException;

    /** Get related entity where relation is of type one, uses findByPrimaryKey
     * @throws IllegalArgumentException if the list found has more than one item
     */
    EntityValue findRelatedOne(String relationName, EntityValue value, boolean useCache) throws EntityException;

    // ======= XML Related Methods ========
    List<EntityValue> readXmlDocument(URL url) throws EntityException;

    List<EntityValue> readXmlDocument(Document document);

    EntityValue makeValue(Element element);

    // ======= Misc Methods ========

    /** Get the next guaranteed unique seq id from the sequence with the given sequence name;
     * if the named sequence doesn't exist, it will be created
     *
     * @param seqName The name of the sequence to get the next seq id from
     * @param staggerMax The maximum amount to stagger the sequenced ID, if 1 the sequence will be incremented by 1,
     *     otherwise the current sequence ID will be incremented by a value between 1 and staggerMax
     * @return Long with the next seq id for the given sequence name
     */
    String sequencedIdPrimary(String seqName, Long staggerMax);

    /** Look at existing values for a sub-entity with a sequenced secondary ID, and get the highest plus 1 */
    void sequencedIdSecondary(EntityValue value, String seqFieldName, Integer numericPadding, Integer incrementBy);

    /** Use this to get a Connection if you want to do JDBC operations directly. This Connection will be enlisted in
     * the active Transaction. */
    Connection getConnection() throws EntityException;
}
