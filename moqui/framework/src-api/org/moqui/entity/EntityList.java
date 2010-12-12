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

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Contains a list of EntityValue objects.
 * Entity List that adds some additional operations like filtering to the basic List<EntityValue>.
 */
public interface EntityList extends List<EntityValue>, Iterable<EntityValue> {

    /** Get the first value in the list. */
    EntityValue getFirst();

    /** Make a new EntityList containing only the values that are active for the moment passed in.
     *
     *@param moment The point in time to compare the values to
     *@param fromDateName The name of the from/beginning date/time field. Defaults to "fromDate".
     *@param thruDateName The name of the thru/ending date/time field. Defaults to "thruDate".
     *@return EntityList for all values that are active at the moment passed in
     */
    EntityList filterByDate(Timestamp moment, String fromDateName, String thruDateName);

    /** Make a new EntityList containing only the values that match the values in the fields parameter.
     *
     *@param fields The name/value pairs that must match for a value to be included in the output list.
     *@return List of EntityValue objects that match the values in the fields parameter.
     */
    EntityList filterByAnd(Map<String, ?> fields);

    /** Make a new EntityList that is ordered by the field names passed in.
     *
     *@param fieldNames The field names for the entity values to sort the list by. Optionally prefix each field name
     * with a plus sign (+) for ascending or a minus sign (-) for descending. Defaults to ascending.
     *@return List of EntityValue objects in the specified order.
     */
    EntityList orderByFields(List<String> fieldNames);

    /** Make a new EntityList that includes (or excludes) values matching the condition.
     *
     * @param condition EntityCondition to filter by.
     * @param include If true include matching values, if false exclude matching values. Defaults to true (include).
     * @return
     */
    EntityList filterByCondition(EntityCondition condition, Boolean include);

    /** Method to implement the Iterable interface to allow an EntityList to be used in a foreach loop.
     *
     * @return Iterator<EntityValue> to iterate over internal list.
     */
    Iterator<EntityValue> iterator();
}
