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
    public enum ComparisonOperator { EQUALS, NOT_EQUAL,
        LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL_TO, GREATER_THAN_EQUAL_TO,
        IN, NOT_IN, BETWEEN, LIKE, NOT_LIKE }

    public enum JoinOperator { AND, OR }

    /** Evaluate the condition in memory. */
    boolean mapMatches(Map<String, ?> map);
}
