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

/** The main interface for general database operations in Moqui. */
public interface EntityFacade {

    /** Get a EntityConditionFactory object that can be used to create and assemble conditions used for finds.
     *
     * @return The facade's active EntityConditionFactory object.
     */
    EntityConditionFactory getConditionFactory();

    /** Creates a Entity in the form of a EntityValue without persisting it
     *
     * @param entityName The name of the entity to make a value object for.
     * @return EntityValue for the named entity. 
     */
    EntityValue makeValue(String entityName);
    
    /** Update a set of values that match a condition.
     *
     * @param entityName The name of the Entity as defined in the entity XML file
     * @param fieldsToSet The fields of the named entity to set in the database
     * @param condition The condition that restricts the list of stored values
     * @return int representing number of rows effected by this operation
     * @throws EntityException
     */
    int updateByCondition(String entityName, Map<String, ?> fieldsToSet, EntityCondition condition)
           throws EntityException;

    /** Delete entity records that match a condition.
     *
     * @param entityName The Name of the Entity as defined in the entity XML file
     * @param condition The condition used to restrict the removing
     * @return int representing number of rows effected by this operation
     * @throws EntityException
     */
    int deleteByCondition(String entityName, EntityCondition condition) throws EntityException;

    /** Create an EntityFind object that can be used to specify additional options, and then to execute one or more
     * finds (queries).
     * 
     * @param entityName The Name of the Entity as defined in the entity XML file, can be null.
     * @return An EntityFind object.
     */
    EntityFind makeFind(String entityName);

    /** Get the next guaranteed unique seq id from the sequence with the given sequence name;
     * if the named sequence doesn't exist, it will be created
     *
     * @param seqName The name of the sequence to get the next seq id from
     * @param staggerMax The maximum amount to stagger the sequenced ID, if 1 the sequence will be incremented by 1,
     *     otherwise the current sequence ID will be incremented by a value between 1 and staggerMax
     * @return Long with the next seq id for the given sequence name
     */
    String sequencedIdPrimary(String seqName, Long staggerMax);

    /** Look at existing values for a sub-entity with a sequenced secondary ID, and get the highest plus incrementBy
     * (default 1).
     *
     * @param value An existing value object with the primary sequenced field already populated.
     * @param seqFieldName The field to set the secondary sequenced value on.
     * @param paddedLength If specified front-pads the secondary sequenced value with zeroes until it is this length.
     * @param incrementBy If specified adds this value instead of the default (1) to the highest existing value. 
     */
    void sequencedIdSecondary(EntityValue value, String seqFieldName, Integer paddedLength, Integer incrementBy);

    /** Gets the group name for specified entityName
     * @param entityName The name of the entity to get the group name
     * @return String with the group name that corresponds to the entityName
     */
    String getEntityGroupName(String entityName);

    /** Use this to get a Connection if you want to do JDBC operations directly. This Connection will be enlisted in
     * the active Transaction.
     *
     * @param groupName The name of entity group to get a connection for.
     *     Corresponds to the entity.group-name attribute and the datasource.group-name attribute.
     * @return JDBC Connection object for the associated database
     * @throws EntityException
     */
    Connection getConnection(String groupName) throws EntityException;

    // ======= XML Related Methods ========

    /** Read an XML document containing value/record data for entities.
     *
     * The document should have a root element like <code>&lt;entity-facade-xml type=&quot;seed&quot;&gt;</code>. The
     * type attribute will be used to determine if the file should be loaded by whether or not it matches the values
     * specified for file types when loading the files.
     *
     * @param url The location of the document to read.
     * @return An EntityList object representing a List of EntityValue objects for the values in the XML document.
     * @throws EntityException
     */
    EntityList readXmlDocument(URL url) throws EntityException;

    /** Convert an already read XML document to entity values.
     *
     * @param xmlText String with XML text in it, ready to be parsed.
     * @return An EntityList object representing a List of EntityValue objects for the values in the XML document.
     */
    EntityList readXmlDocument(String xmlText);

    /** Make an EntityValue and populate it with the data (attributes and sub-elements) from the given XML element.
     *
     * @param element A XML DOM element representing a single value/record for an entity.
     * @return EntityValue object populated with data from the element.
     */
    EntityValue makeValue(Element element);
}
