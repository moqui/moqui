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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * Generic Entity Value Interface - Represents a single database record.
 *
 */
public interface EntityValue extends Map<String, Object>, Serializable, Comparable<EntityValue>, Cloneable {

    String getEntityName();

    boolean isModified();

    boolean isMutable();

    /** Get the named field.
     *
     * If there is a matching entry in the LocalizedEntityField entity using the Locale in the current ExecutionContext
     * then that will be returned instead.
     *
     * This method also supports getting related entities using their relationship name, formatted as
     * "${title}${related-entity-name}". When doing so it is like calling
     * <code>findRelated(relationshipName, null, null, null)</code> for type many relationships, or
     * <code>findRelatedOne(relationshipName, null)</code> for type one relationships.
     *
     * @param name The field name to get, or the name of the relationship to get one or more related values from.
     * @return Object with the value of the field, or the related EntityValue or EntityList.
     */
    Object get(String name);

    /** Returns true if the entity contains all of the primary key fields. */
    boolean containsPrimaryKey();

    /** Sets the named field to the passed value, even if the value is null
     * @param name The field name to set
     * @param value The value to set
     */
    void set(String name, Object value);

    /** Sets the named field to the passed value, converting the value from a String to the corresponding type using 
     *   <code>Type.valueOf()</code>
     *
     * If the String "null" is passed in it will be treated the same as a null value. If you really want to set a
     * String of "null" then pass in "\null".
     *
     * @param name The field name to set
     * @param value The String value to convert and set
     */
    void setString(String name, String value);

    Boolean getBoolean(String name);

    String getString(String name);

    java.sql.Timestamp getTimestamp(String name);

    java.sql.Time getTime(String name);

    java.sql.Date getDate(String name);

    Long getLong(String name);

    Double getDouble(String name);

    BigDecimal getBigDecimal(String name);

    /** Sets fields on this entity from the Map of fields passed in using the entity definition to only get valid
     * fields from the Map. For any String values passed in this will call setString to convert based on the field
     * definition, otherwise it sets the Object as-is.
     *
     * @param fields The fields Map to get the values from
     * @param setIfEmpty Used to specify whether empty/null values in the field Map should be set
     * @param namePrefix If not null or empty will be pre-pended to each field name (upper-casing the first letter of
     *   the field name first), and that will be used as the fields Map lookup name instead of the field-name
     * @param pks If null, get all values, if TRUE just get PKs, if FALSE just get non-PKs
     */
    void setFields(Map<String, ?> fields, boolean setIfEmpty, String namePrefix, Boolean pks);

    /** Compares this GenericEntity to the passed object
     * @param that Object to compare this to
     * @return int representing the result of the comparison (-1,0, or 1)
     */
    int compareTo(EntityValue that);

    /** Creates a record for this entity value. */
    void create() throws EntityException;

    /** Creates a record for this entity value, or updates the record if one exists that matches the primary key. */
    void createOrUpdate() throws EntityException;

    /** Updates the record that matches the primary key. */
    void update() throws EntityException;

    /** Deletes the record that matches the primary key. */
    void delete() throws EntityException;

    /** Refreshes this value based on the record that matches the primary key. */
    void refresh() throws EntityException;

    Object getOriginalDbValue(String name);

    /** Get the named Related Entity for the EntityValue from the persistent store
     * @param relationshipName String containing the relationship name which is the combination of relationship.title
     *   and relationship.related-entity-name as specified in the entity XML definition file
     * @param byAndFields the fields that must equal in order to keep; may be null
     * @param orderBy The fields of the named entity to order the query by; may be null;
     *      optionally add a " ASC" for ascending or " DESC" for descending
     * @param useCache Look in the cache before finding in the datasource. Defaults to setting on entity definition.
     * @return List of EntityValue instances as specified in the relation definition
     */
    EntityList findRelated(String relationshipName, Map<String, ?> byAndFields, List<String> orderBy,
                                  Boolean useCache) throws EntityException;

    /** Get the named Related Entity for the EntityValue from the persistent store
     * @param relationshipName String containing the relationship name which is the combination of relationship.title
     *   and relationship.related-entity-name as specified in the entity XML definition file
     * @param useCache Look in the cache before finding in the datasource. Defaults to setting on entity definition.
     * @return List of EntityValue instances as specified in the relation definition
     */
    EntityValue findRelatedOne(String relationshipName, Boolean useCache) throws EntityException;

    /** Remove the named Related Entity for the EntityValue from the persistent store
     * @param relationshipName String containing the relationship name which is the combination of relationship.title
     *   and relationship.related-entity-name as specified in the entity XML definition file
     */
    void deleteRelated(String relationshipName) throws EntityException;

    /**
     * Checks to see if all foreign key records exist in the database. Will create a dummy value for
     * those missing when specified.
     *
     * @param insertDummy Create a dummy record using the provided fields
     * @return true if all FKs exist (or when all missing are created)
     * @throws EntityException
     */
    boolean checkFks(boolean insertDummy) throws EntityException;

    /** Makes an XML Element object with an attribute for each field of the entity
     * @param document The XML Document that the new Element will be part of
     * @param prefix A prefix to put in front of the entity name in the tag name
     * @return org.w3c.dom.Element object representing this generic entity
     */
    Element makeXmlElement(Document document, String prefix);

    /** Writes XML text with an attribute or CDATA element for each field of the entity
     * @param writer A PrintWriter to write to
     * @param prefix A prefix to put in front of the entity name in the tag name
     */
    void writeXmlText(PrintWriter writer, String prefix);
}
