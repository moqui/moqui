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
package org.moqui.impl.entity

import java.sql.Timestamp

import org.moqui.entity.EntityConditionFactory
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityCondition.ComparisonOperator
import org.moqui.impl.entity.EntityFindBuilder.EntityConditionParameter
import org.moqui.impl.StupidUtilities

class EntityConditionFactoryImpl implements EntityConditionFactory {

    protected final EntityFacadeImpl efi;

    EntityConditionFactoryImpl(EntityFacadeImpl efi) {
        this.efi = efi;
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(EntityCondition, JoinOperator, EntityCondition) */
    EntityCondition makeCondition(EntityCondition lhs, JoinOperator operator, EntityCondition rhs) {
        return new BasicJoinCondition(this, (EntityConditionImplBase) lhs, operator, (EntityConditionImplBase) rhs)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(String, ComparisonOperator, Object) */
    EntityCondition makeCondition(String fieldName, ComparisonOperator operator, Object value) {
        return new FieldValueCondition(this, fieldName, operator, value)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionToField(String, ComparisonOperator, String) */
    EntityCondition makeConditionToField(String fieldName, ComparisonOperator operator, String toFieldName) {
        return new FieldToFieldCondition(this, fieldName, operator, toFieldName)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(List<EntityCondition>, JoinOperator) */
    EntityCondition makeCondition(List<EntityCondition> conditionList, JoinOperator operator) {
        return new ListCondition(this, (List<EntityConditionImplBase>) conditionList, operator)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(List<EntityCondition>) */
    EntityCondition makeCondition(List<EntityCondition> conditionList) {
        return new ListCondition(this, (List<EntityConditionImplBase>) conditionList, JoinOperator.AND)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(Map<String,?>, ComparisonOperator, JoinOperator) */
    EntityCondition makeCondition(Map<String, ?> fieldMap, ComparisonOperator comparisonOperator, JoinOperator joinOperator) {
        return new MapCondition(this, fieldMap, comparisonOperator, joinOperator)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(Map<String,?>) */
    EntityCondition makeCondition(Map<String, ?> fieldMap) {
        return new MapCondition(this, fieldMap, ComparisonOperator.EQUALS, JoinOperator.AND)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionDate(String, String, Timestamp) */
    EntityCondition makeConditionDate(String fromFieldName, String thruFieldName, Timestamp compareStamp) {
        return new DateCondition(this, fromFieldName, thruFieldName, compareStamp)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionWhere(String) */
    EntityCondition makeConditionWhere(String sqlWhereClause) {
        return new WhereCondition(this, sqlWhereClause)
    }

    public static abstract class EntityConditionImplBase implements EntityCondition {
        EntityConditionFactoryImpl ecFactoryImpl;

        EntityConditionImplBase(EntityConditionFactoryImpl ecFactoryImpl) {
            this.ecFactoryImpl = ecFactoryImpl;
        }

        /** Build SQL Where text to evaluate condition in a database. */
        public abstract void makeSqlWhere(EntityFindBuilder efb);
    }

    public static class BasicJoinCondition extends EntityConditionImplBase {
        protected EntityConditionImplBase lhs
        protected JoinOperator operator
        protected EntityConditionImplBase rhs

        BasicJoinCondition(EntityConditionFactoryImpl ecFactoryImpl,
                EntityConditionImplBase lhs, JoinOperator operator, EntityConditionImplBase rhs) {
            super(ecFactoryImpl)
            this.lhs = lhs
            this.operator = operator
            this.rhs = rhs
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            StringBuilder sql = efb.getSqlTopLevel()
            sql.append('(')
            this.lhs.makeSqlWhere(efb)
            sql.append(' ')
            sql.append(StupidUtilities.getJoinOperatorString(this.operator))
            sql.append(' ')
            this.rhs.makeSqlWhere(efb)
            sql.append(')')
        }

        boolean mapMatches(Map<String, ?> map) {
            boolean lhsMatches = this.lhs.mapMatches(map)

            // handle cases where we don't need to evaluate rhs
            if (lhsMatches && operator == JoinOperator.OR) return true
            if (!lhsMatches && operator == JoinOperator.AND) return false

            // handle opposite cases since we know cases above aren't true (ie if OR then lhs=false, if AND then lhs=true
            // if rhs then result is true whether AND or OR
            // if !rhs then result is false whether AND or OR
            return this.rhs.mapMatches(map)
        }

        String toString() {
            // general SQL where clause style text with values included
            return "(" + lhs.toString() + " " + StupidUtilities.getJoinOperatorString(this.operator) + " " + rhs.toString() + ")"
        }
    }

    public static class FieldValueCondition extends EntityConditionImplBase {
        protected String fieldName
        protected ComparisonOperator operator
        protected Object value

        FieldValueCondition(EntityConditionFactoryImpl ecFactoryImpl,
                String fieldName, ComparisonOperator operator, Object value) {
            super(ecFactoryImpl)
            this.fieldName = fieldName
            this.operator = operator
            this.value = value
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            StringBuilder sql = efb.getSqlTopLevel()
            sql.append(efb.mainEntityDefinition.getColumnName(this.fieldName, false))
            sql.append(' ')
            boolean valueDone = false
            if (this.value == null) {
                if (this.operator == ComparisonOperator.EQUALS || this.operator == ComparisonOperator.LIKE ||
                        this.operator == ComparisonOperator.IN) {
                    sql.append(" IS NULL")
                    valueDone = true
                } else if (this.operator == ComparisonOperator.NOT_EQUAL || this.operator == ComparisonOperator.NOT_LIKE ||
                        this.operator == ComparisonOperator.NOT_IN) {
                    sql.append(" IS NOT NULL")
                    valueDone = true
                }
            }
            if (!valueDone) {
                sql.append(StupidUtilities.getComparisonOperatorString(this.operator))
                if ((this.operator == ComparisonOperator.IN || this.operator == ComparisonOperator.NOT_IN) &&
                        this.value instanceof Collection) {
                    sql.append(" (")
                    boolean isFirst = true
                    for (Object curValue in this.value) {
                        if (isFirst) isFirst = false else sql.append(", ")
                        sql.append("?")
                        efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), curValue, efb))
                    }
                    sql.append(')')
                } else if (this.operator == ComparisonOperator.BETWEEN &&
                        this.value instanceof Collection && ((Collection) this.value).size() == 2) {
                    Iterator iterator = ((Collection) this.value).iterator()
                    sql.append(" ? AND ?")
                    efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), iterator.next(), efb))
                    efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), iterator.next(), efb))
                } else {
                    sql.append(" ?")
                    efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), this.value, efb))
                }
            }
        }

        boolean mapMatches(Map<String, ?> map) {
            Object value1 = map.get(this.fieldName)
            return compareByOperator(value1, this.operator, this.value)
        }

        String toString() {
            return this.fieldName + " " + StupidUtilities.getComparisonOperatorString(this.operator) + " " + this.value
        }
    }

    public static class FieldToFieldCondition extends EntityConditionImplBase {
        protected String fieldName
        protected ComparisonOperator operator
        protected String toFieldName

        FieldToFieldCondition(EntityConditionFactoryImpl ecFactoryImpl,
                String fieldName, ComparisonOperator operator, String toFieldName) {
            super(ecFactoryImpl)
            this.fieldName = fieldName
            this.operator = operator
            this.toFieldName = toFieldName
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            StringBuilder sql = efb.getSqlTopLevel()
            sql.append(efb.mainEntityDefinition.getColumnName(this.fieldName, false))
            sql.append(' ')
            sql.append(StupidUtilities.getComparisonOperatorString(this.operator))
            sql.append(' ')
            sql.append(efb.mainEntityDefinition.getColumnName(this.toFieldName, false))
        }

        boolean mapMatches(Map<String, ?> map) {
            Object value1 = map.get(this.fieldName)
            Object value2 = map.get(this.toFieldName)
            return compareByOperator(value1, this.operator, value2)
        }

        String toString() {
            return this.fieldName + " " + StupidUtilities.getComparisonOperatorString(this.operator) + " " + this.toFieldName
        }
    }

    public static class ListCondition extends EntityConditionImplBase {
        protected List<EntityConditionImplBase> conditionList
        protected JoinOperator operator

        ListCondition(EntityConditionFactoryImpl ecFactoryImpl,
                List<EntityConditionImplBase> conditionList, JoinOperator operator) {
            super(ecFactoryImpl)
            this.conditionList = conditionList
            this.operator = operator
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            if (!this.conditionList) return

            StringBuilder sql = efb.getSqlTopLevel()
            sql.append('(')
            boolean isFirst = true
            for (EntityConditionImplBase condition in this.conditionList) {
                if (isFirst) isFirst = false else {
                    sql.append(' ')
                    sql.append(StupidUtilities.getJoinOperatorString(this.operator))
                    sql.append(' ')
                }
                condition.makeSqlWhere(efb)
            }
        }

        boolean mapMatches(Map<String, ?> map) {
            for (EntityConditionImplBase condition in this.conditionList) {
                boolean conditionMatches = condition.mapMatches(map)
                if (conditionMatches && this.operator == JoinOperator.OR) return true
                if (!conditionMatches && this.operator == JoinOperator.AND) return false
            }
            // if we got here it means that it's an OR with no trues, or an AND with no falses
            return (this.operator == JoinOperator.AND)
        }

        String toString() {
            StringBuilder sb = new StringBuilder()
            for (EntityConditionImplBase condition in this.conditionList) {
                if (sb.length() > 0) {
                    sb.append(' ')
                    sb.append(StupidUtilities.getJoinOperatorString(this.operator))
                    sb.append(' ')
                }
                sb.append(condition.toString())
            }
            return sb.toString()
        }
    }

    public static class MapCondition extends EntityConditionImplBase {
        protected Map<String, ?> fieldMap
        protected ComparisonOperator comparisonOperator
        protected JoinOperator joinOperator

        MapCondition(EntityConditionFactoryImpl ecFactoryImpl,
                Map<String, ?> fieldMap, ComparisonOperator comparisonOperator, JoinOperator joinOperator) {
            super(ecFactoryImpl)
            this.fieldMap = fieldMap
            this.comparisonOperator = comparisonOperator
            this.joinOperator = joinOperator
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            this.makeCondition().makeSqlWhere(efb)
        }

        boolean mapMatches(Map<String, ?> map) {
            return this.makeCondition().mapMatches(map)
        }

        String toString() {
            return this.makeCondition().toString()
            /* might want to do something like this at some point, but above is probably better for now
            StringBuilder sb = new StringBuilder()
            for (Map.Entry fieldEntry in this.fieldMap.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(' ')
                    sb.append(StupidUtilities.getJoinOperatorString(this.joinOperator))
                    sb.append(' ')
                }
                sb.append(fieldEntry.getKey())
                sb.append(' ')
                sb.append(StupidUtilities.getComparisonOperatorString(this.comparisonOperator))
                sb.append(' ')
                sb.append(fieldEntry.getValue())
            }
            return sb.toString()
            */
        }

        protected EntityConditionImplBase makeCondition() {
            List conditionList = new LinkedList()
            for (Map.Entry<String, ?> fieldEntry in this.fieldMap.entrySet()) {
                conditionList.add(this.ecFactoryImpl.makeCondition(fieldEntry.getKey(), this.comparisonOperator, fieldEntry.getValue()))
            }
            return this.ecFactoryImpl.makeCondition(conditionList, this.joinOperator)
        }
    }

    public static class DateCondition extends EntityConditionImplBase {
        protected String fromFieldName
        protected String thruFieldName
        protected Timestamp compareStamp

        DateCondition(EntityConditionFactoryImpl ecFactoryImpl,
                String fromFieldName, String thruFieldName, Timestamp compareStamp) {
            super(ecFactoryImpl)
            this.fromFieldName = fromFieldName
            this.thruFieldName = thruFieldName
            this.compareStamp = compareStamp
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            this.makeCondition().makeSqlWhere(efb)
        }

        boolean mapMatches(Map<String, ?> map) {
            return this.makeCondition().mapMatches(map)
        }

        String toString() {
            return this.makeCondition().toString()
        }

        protected EntityConditionImplBase makeCondition() {
            return this.ecFactoryImpl.makeCondition(
                this.ecFactoryImpl.makeCondition(
                    this.ecFactoryImpl.makeCondition(this.thruFieldName, ComparisonOperator.EQUALS, null),
                    JoinOperator.OR,
                    this.ecFactoryImpl.makeCondition(this.thruFieldName, ComparisonOperator.GREATER_THAN, this.compareStamp)
                ),
                JoinOperator.AND,
                this.ecFactoryImpl.makeCondition(
                    this.ecFactoryImpl.makeCondition(this.fromFieldName, ComparisonOperator.EQUALS, null),
                    JoinOperator.OR,
                    this.ecFactoryImpl.makeCondition(this.fromFieldName, ComparisonOperator.LESS_THAN_EQUAL_TO, this.compareStamp)
                )
            )
        }
    }

    public static class WhereCondition extends EntityConditionImplBase {
        protected String sqlWhereClause

        WhereCondition(EntityConditionFactoryImpl ecFactoryImpl, String sqlWhereClause) {
            super(ecFactoryImpl)
            this.sqlWhereClause = sqlWhereClause
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            efb.getSqlTopLevel().append(this.sqlWhereClause)
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this, or not...
            return false
        }

        String toString() {
            return this.sqlWhereClause
        }
    }
}
