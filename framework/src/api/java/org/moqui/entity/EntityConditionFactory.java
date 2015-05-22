/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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

    EntityCondition makeCondition(Map<String, Object> fieldMap, EntityCondition.ComparisonOperator comparisonOperator, EntityCondition.JoinOperator joinOperator);

    /** Default to ComparisonOperator of EQUALS and JoinOperator of AND */
    EntityCondition makeCondition(Map<String, Object> fieldMap);

    EntityCondition makeConditionDate(String fromFieldName, String thruFieldName, Timestamp compareStamp);

    EntityCondition makeConditionWhere(String sqlWhereClause);

    /** Get a ComparisonOperator using an enumId for enum type "ComparisonOperator" */
    EntityCondition.ComparisonOperator comparisonOperatorFromEnumId(String enumId);
}
