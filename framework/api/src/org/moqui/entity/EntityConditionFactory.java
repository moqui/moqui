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
import java.util.List;
import java.util.Map;

/**
 * Represents the conditions to be used to constrain a query.
 *
 * These can be used in various combinations using the different condition types.
 *
 */
public interface EntityConditionFactory {

    EntityCondition makeCondition(EntityCondition lhs, EntityCondition.JoinOperator operator, EntityCondition rhs);

    EntityCondition makeCondition(String fieldName, EntityCondition.ComparisonOperator operator, Object value);

    EntityCondition makeConditionToField(String fieldName, EntityCondition.ComparisonOperator operator, String toFieldName);

    EntityCondition makeCondition(List<EntityCondition> conditionList, EntityCondition.JoinOperator operator);

    /** Default to JoinOperator of AND */
    EntityCondition makeCondition(List<EntityCondition> conditionList);

    EntityCondition makeCondition(Map<String, ?> fieldMap, EntityCondition.ComparisonOperator comparisonOperator, EntityCondition.JoinOperator joinOperator);

    /** Default to ComparisonOperator of EQUALS and JoinOperator of AND */
    EntityCondition makeCondition(Map<String, ?> fieldMap);

    EntityCondition makeConditionDate(String fromFieldName, String thruFieldName, Timestamp compareStamp);

    EntityCondition makeConditionWhere(String sqlWhereClause);

    /** Get a ComparisonOperator using an enumId for enum type "ComparisonOperator" */
    EntityCondition.ComparisonOperator comparisonOperatorFromEnumId(String enumId);
}
