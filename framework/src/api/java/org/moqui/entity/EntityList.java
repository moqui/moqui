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

import java.io.Writer;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Contains a list of EntityValue objects.
 * Entity List that adds some additional operations like filtering to the basic List<EntityValue>.
 *
 * The various methods here modify the internal list for efficiency and return a reference to this for convenience.
 * If you want a new EntityList with the modifications, use clone() or cloneList() then modify it.
 */
public interface EntityList extends List<EntityValue>, Iterable<EntityValue>, Cloneable {

    /** Get the first value in the list.
     *
     * @return EntityValue that is first in the list.
     */
    EntityValue getFirst();

    /** Modify this EntityList so that it contains only the values that are active for the moment passed in.
     * The results include values that match the fromDate, but exclude values that match the thruDate.
     *
     *@param fromDateName The name of the from/beginning date/time field. Defaults to "fromDate".
     *@param thruDateName The name of the thru/ending date/time field. Defaults to "thruDate".
     *@param moment The point in time to compare the values to; if null the current system date/time is used.
     *@return A reference to this for convenience.
     */
    EntityList filterByDate(String fromDateName, String thruDateName, Timestamp moment);

    /** Modify this EntityList so that it contains only the values that match the values in the fields parameter.
     *
     *@param fields The name/value pairs that must match for a value to be included in the output list.
     *@return List of EntityValue objects that match the values in the fields parameter.
     */
    EntityList filterByAnd(Map<String, ?> fields);

    /** Modify this EntityList so that is ordered by the field names passed in.
     *
     *@param fieldNames The field names for the entity values to sort the list by. Optionally prefix each field name
     * with a plus sign (+) for ascending or a minus sign (-) for descending. Defaults to ascending.
     *@return List of EntityValue objects in the specified order.
     */
    EntityList orderByFields(List<String> fieldNames);

    /** Modify this EntityList so that it includes (or excludes) values matching the condition.
     *
     * @param condition EntityCondition to filter by.
     * @param include If true include matching values, if false exclude matching values.
     *     Defaults to true (include, ie only include values that do meet condition).
     * @return List with filtered values.
     */
    EntityList filterByCondition(EntityCondition condition, Boolean include);

    /** Writes XML text with an attribute or CDATA element for each field of each record. If dependents is true also
     * writes all dependent (descendant) records.
     * @param writer A Writer object to write to
     * @param prefix A prefix to put in front of the entity name in the tag name
     * @param dependents Write dependent (descendant) records as well?
     * @return The number of records written
     */
    int writeXmlText(Writer writer, String prefix, boolean dependents);

    /** Method to implement the Iterable interface to allow an EntityList to be used in a foreach loop.
     *
     * @return Iterator<EntityValue> to iterate over internal list.
     */
    Iterator<EntityValue> iterator();

    EntityList cloneList();

    void setFromCache(boolean fc);
    boolean isFromCache();
}
