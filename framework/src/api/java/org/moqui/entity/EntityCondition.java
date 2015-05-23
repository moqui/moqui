/*
 * This software is in the public domain under CC0 1.0 Universal.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.entity;

import java.io.Serializable;
import java.util.Map;

/** Represents the conditions to be used to constrain a query.
 *
 * These can be used in various combinations using the different condition types.
 *
 * This class is mostly empty because it is a placeholder for use in the EntityConditionFactory and most functionality
 * is internal only.
 */
public interface EntityCondition extends Serializable {

    public static final ComparisonOperator EQUALS = ComparisonOperator.EQUALS;
    public static final ComparisonOperator NOT_EQUAL = ComparisonOperator.NOT_EQUAL;
    public static final ComparisonOperator LESS_THAN = ComparisonOperator.LESS_THAN;
    public static final ComparisonOperator GREATER_THAN = ComparisonOperator.GREATER_THAN;
    public static final ComparisonOperator LESS_THAN_EQUAL_TO = ComparisonOperator.LESS_THAN_EQUAL_TO;
    public static final ComparisonOperator GREATER_THAN_EQUAL_TO = ComparisonOperator.GREATER_THAN_EQUAL_TO;
    public static final ComparisonOperator IN = ComparisonOperator.IN;
    public static final ComparisonOperator NOT_IN = ComparisonOperator.NOT_IN;
    public static final ComparisonOperator BETWEEN = ComparisonOperator.BETWEEN;
    public static final ComparisonOperator NOT_BETWEEN = ComparisonOperator.NOT_BETWEEN;
    public static final ComparisonOperator LIKE = ComparisonOperator.LIKE;
    public static final ComparisonOperator NOT_LIKE = ComparisonOperator.NOT_LIKE;
    public static final ComparisonOperator IS_NULL = ComparisonOperator.IS_NULL;
    public static final ComparisonOperator IS_NOT_NULL = ComparisonOperator.IS_NOT_NULL;

    public static final JoinOperator AND = JoinOperator.AND;
    public static final JoinOperator OR = JoinOperator.OR;

    public enum ComparisonOperator { EQUALS, NOT_EQUAL,
        LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL_TO, GREATER_THAN_EQUAL_TO,
        IN, NOT_IN, BETWEEN, NOT_BETWEEN, LIKE, NOT_LIKE, IS_NULL, IS_NOT_NULL }

    public enum JoinOperator { AND, OR }

    /** Evaluate the condition in memory. */
    boolean mapMatches(Map<String, ?> map);
    /** Create a map of name/value pairs representing the condition. Returns false if the condition can't be
     * represented as simple name/value pairs ANDed together. */
    boolean populateMap(Map<String, ?> map);

    /** Set this condition to ignore case in the query.
     * This may not have an effect for all types of conditions.
     *
     * @return Returns reference to the query for convenience.
     */
    EntityCondition ignoreCase();
}
