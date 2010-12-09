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

    EntityDefinition getModelEntity();

    boolean isModified();

    boolean isMutable();

    Object get(String name);

    /** Returns true if the entity contains all of the primary key fields. */
    boolean containsPrimaryKey();

    /** Sets the named field to the passed value, even if the value is null
     * @param name The field name to set
     * @param value The value to set
     */
    void set(String name, Object value);

    /** Sets the named field to the passed value, converting the value from a String to the corrent type using <code>Type.valueOf()</code>
     * @param name The field name to set
     * @param value The String value to convert and set
     */
    void setString(String name, String value);

    /** Sets a field with an array of bytes, wrapping them automatically for easy use.
     * @param name The field name to set
     * @param bytes The byte array to be wrapped and set
     */
    void setBytes(String name, byte[] bytes);

    Boolean getBoolean(String name);

    String getString(String name);

    java.sql.Timestamp getTimestamp(String name);

    java.sql.Time getTime(String name);

    java.sql.Date getDate(String name);

    Long getLong(String name);

    Double getDouble(String name);

    BigDecimal getBigDecimal(String name);

    byte[] getBytes(String name);

    /** Same as the getResource method that does not take resource name, but instead allows manually
     *    specifying the resource name. In general you should use the other method for more consistent
     *    naming and use of the corresponding properties files.
     * @param name The name of the field on the entity
     * @param resource The name of the resource to get the value from; if null defaults to the
     *    default-resource-name on the entity definition, if specified there
     * @param locale The locale to use when finding the ResourceBundle, if null uses the default
     *    locale for the current instance of Java
     * @return If the specified resource is found and contains a key as described above, then that
     *    property value is returned; otherwise returns the field value
     */
    Object get(String name, String resource, Locale locale);

    /** Intelligently sets fields on this entity from the Map of fields passed in
     * @param fields The fields Map to get the values from
     * @param setIfEmpty Used to specify whether empty/null values in the field Map should over-write non-empty values in this entity
     * @param namePrefix If not null or empty will be pre-pended to each field name (upper-casing the first letter of the field name first), and that will be used as the fields Map lookup name instead of the field-name
     * @param pks If null, get all values, if TRUE just get PKs, if FALSE just get non-PKs
     */
    void setFields(Map<String, ?> fields, boolean setIfEmpty, String namePrefix, Boolean pks);

    /** Makes an XML Element object with an attribute for each field of the entity
     *@param document The XML Document that the new Element will be part of
     *@param prefix A prefix to put in front of the entity name in the tag name
     *@return org.w3c.dom.Element object representing this generic entity
     */
    Element makeXmlElement(Document document, String prefix);

    /** Writes XML text with an attribute or CDATA element for each field of the entity
     *@param writer A PrintWriter to write to
     *@param prefix A prefix to put in front of the entity name in the tag name
     */
    void writeXmlText(PrintWriter writer, String prefix);

    /** Compares this GenericEntity to the passed object
     *@param that Object to compare this to
     *@return int representing the result of the comparison (-1,0, or 1)
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
     *@param relationName String containing the relation name which is the combination of relation.title and relation.rel-entity-name as specified in the entity XML definition file
     * @param byAndFields the fields that must equal in order to keep; may be null
     * @param orderBy The fields of the named entity to order the query by; may be null;
     *      optionally add a " ASC" for ascending or " DESC" for descending
     *@return List of EntityValue instances as specified in the relation definition
     */
    List<EntityValue> findRelated(String relationName, Map<String, ?> byAndFields, List<String> orderBy, boolean useCache) throws EntityException;

    /** Get the named Related Entity for the EntityValue from the persistent store
     *@param relationName String containing the relation name which is the combination of relation.title and relation.rel-entity-name as specified in the entity XML definition file
     *@return List of EntityValue instances as specified in the relation definition
     */
    EntityValue findRelatedOne(String relationName, boolean useCache) throws EntityException;

    /** Remove the named Related Entity for the EntityValue from the persistent store
     *@param relationName String containing the relation name which is the combination of relation.title and relation.rel-entity-name as specified in the entity XML definition file
     */
    void deleteRelated(String relationName) throws EntityException;

    /**
     * Checks to see if all foreign key records exist in the database. Will create a dummy value for
     * those missing when specified.
     *
     * @param insertDummy Create a dummy record using the provided fields
     * @return true if all FKs exist (or when all missing are created)
     * @throws EntityException
     */
    boolean checkFks(boolean insertDummy) throws EntityException;
}
