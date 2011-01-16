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
import org.moqui.impl.entity.EntityQueryBuilder.EntityConditionParameter
import org.moqui.impl.StupidUtilities

class EntityConditionFactoryImpl implements EntityConditionFactory {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityConditionFactoryImpl.class)

    protected final EntityFacadeImpl efi

    EntityConditionFactoryImpl(EntityFacadeImpl efi) {
        this.efi = efi
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(EntityCondition, JoinOperator, EntityCondition) */
    EntityCondition makeCondition(EntityCondition lhs, JoinOperator operator, EntityCondition rhs) {
        return new BasicJoinCondition(this, (EntityConditionImplBase) lhs, operator, (EntityConditionImplBase) rhs)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(String, ComparisonOperator, Object) */
    EntityCondition makeCondition(String fieldName, ComparisonOperator operator, Object value) {
        return new FieldValueCondition(this, new ConditionField(fieldName), operator, value)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionToField(String, ComparisonOperator, String) */
    EntityCondition makeConditionToField(String fieldName, ComparisonOperator operator, String toFieldName) {
        return new FieldToFieldCondition(this, new ConditionField(fieldName), operator, new ConditionField(toFieldName))
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

    EntityCondition makeActionCondition(String fieldName, String operator, Object fromField, String value, String toFieldName, boolean ignoreCase, boolean ignoreIfEmpty, boolean ignore) {
        if (ignore) return null

        if (toFieldName) {
            EntityCondition ec = makeConditionToField(fieldName, getComparisonOperator(operator), toFieldName)
            if (ignoreCase) ec.ignoreCase()
            return ec
        } else {
            Object condValue = null
            if (fromField) {
                condValue = fromField
            } else if (value) {
                // NOTE: have to convert value (if needed) later on because we don't know which entity/field this is for, or change to pass in entity?
                condValue = value
            } else {
                if (ignoreIfEmpty) return null
            }

            EntityCondition ec = makeCondition(fieldName, getComparisonOperator(operator), condValue)
            if (ignoreCase) ec.ignoreCase()
            return ec
        }
    }

    public static abstract class EntityConditionImplBase implements EntityCondition {
        EntityConditionFactoryImpl ecFactoryImpl

        EntityConditionImplBase(EntityConditionFactoryImpl ecFactoryImpl) {
            this.ecFactoryImpl = ecFactoryImpl
        }

        /** Build SQL Where text to evaluate condition in a database. */
        public abstract void makeSqlWhere(EntityQueryBuilder eqb)
    }

    public static class BasicJoinCondition extends EntityConditionImplBase {
        protected EntityConditionImplBase lhs
        protected JoinOperator operator
        protected EntityConditionImplBase rhs

        BasicJoinCondition(EntityConditionFactoryImpl ecFactoryImpl,
                EntityConditionImplBase lhs, JoinOperator operator, EntityConditionImplBase rhs) {
            super(ecFactoryImpl)
            this.lhs = lhs
            this.operator = operator ? operator : JoinOperator.AND
            this.rhs = rhs
        }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append('(')
            this.lhs.makeSqlWhere(eqb)
            sql.append(' ').append(getJoinOperatorString(this.operator)).append(' ')
            this.rhs.makeSqlWhere(eqb)
            sql.append(')')
        }

        @Override
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

        @Override
        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        @Override
        String toString() {
            // general SQL where clause style text with values included
            return "(" + lhs.toString() + " " + getJoinOperatorString(this.operator) + " " + rhs.toString() + ")"
        }

        @Override
        int hashCode() {
            return (lhs ? lhs.hashCode() : 0) + operator.hashCode() + (rhs ? rhs.hashCode() : 0)
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            BasicJoinCondition that = (BasicJoinCondition) o
            if (!this.lhs.equals(that.lhs)) return false
            if (this.operator != that.operator) return false
            if (!this.rhs.equals(that.rhs)) return false
            return true
        }
    }

    public static class FieldValueCondition extends EntityConditionImplBase {
        protected ConditionField field
        protected ComparisonOperator operator
        protected Object value
        protected boolean ignoreCase = false

        FieldValueCondition(EntityConditionFactoryImpl ecFactoryImpl,
                ConditionField field, ComparisonOperator operator, Object value) {
            super(ecFactoryImpl)
            this.field = field
            this.operator = operator ? operator : ComparisonOperator.EQUALS
            this.value = value
        }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            StringBuilder sql = eqb.getSqlTopLevel()
            if (this.ignoreCase) sql.append("UPPER(")
            sql.append(field.getColumnName(eqb.mainEntityDefinition))
            if (this.ignoreCase) sql.append(')')
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
                sql.append(getComparisonOperatorString(this.operator))
                if ((this.operator == ComparisonOperator.IN || this.operator == ComparisonOperator.NOT_IN) &&
                        this.value instanceof Collection) {
                    sql.append(" (")
                    boolean isFirst = true
                    for (Object curValue in this.value) {
                        if (isFirst) isFirst = false else sql.append(", ")
                        sql.append("?")
                        if (this.ignoreCase && curValue instanceof String) curValue = ((String) curValue).toUpperCase()
                        eqb.getParameters().add(new EntityConditionParameter(field.getFieldNode(eqb.mainEntityDefinition), curValue, eqb))
                    }
                    sql.append(')')
                } else if (this.operator == ComparisonOperator.BETWEEN &&
                        this.value instanceof Collection && ((Collection) this.value).size() == 2) {
                    sql.append(" ? AND ?")
                    Iterator iterator = ((Collection) this.value).iterator()
                    Object value1 = iterator.next()
                    if (this.ignoreCase && value1 instanceof String) value1 = ((String) value1).toUpperCase()
                    Object value2 = iterator.next()
                    if (this.ignoreCase && value2 instanceof String) value2 = ((String) value2).toUpperCase()
                    eqb.getParameters().add(new EntityConditionParameter(field.getFieldNode(eqb.mainEntityDefinition), value1, eqb))
                    eqb.getParameters().add(new EntityConditionParameter(field.getFieldNode(eqb.mainEntityDefinition), value2, eqb))
                } else {
                    if (this.ignoreCase && this.value instanceof String) this.value = ((String) this.value).toUpperCase()
                    sql.append(" ?")
                    eqb.getParameters().add(new EntityConditionParameter(field.getFieldNode(eqb.mainEntityDefinition), this.value, eqb))
                }
            }
        }

        @Override
        boolean mapMatches(Map<String, ?> map) { return compareByOperator(map.get(field.fieldName), operator, value) }

        @Override
        EntityCondition ignoreCase() { this.ignoreCase = true; return this }

        @Override
        String toString() {
            return field + " " + getComparisonOperatorString(this.operator) + " " + this.value
        }

        @Override
        int hashCode() {
            return (field ? field.hashCode() : 0) + operator.hashCode() + (value ? value.hashCode() : 0) + ignoreCase.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            FieldValueCondition that = (FieldValueCondition) o
            if (this.field != that.field) return false
            if (this.operator != that.operator) return false
            if (this.value != that.value) return false
            if (this.ignoreCase != that.ignoreCase) return false
            return true
        }
    }

    public static class FieldToFieldCondition extends EntityConditionImplBase {
        protected ConditionField field
        protected ComparisonOperator operator
        protected ConditionField toField
        protected boolean ignoreCase = false

        FieldToFieldCondition(EntityConditionFactoryImpl ecFactoryImpl,
                ConditionField field, ComparisonOperator operator, ConditionField toField) {
            super(ecFactoryImpl)
            this.field = field
            this.operator = operator ? operator : ComparisonOperator.EQUALS
            this.toField = toField
        }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            StringBuilder sql = eqb.getSqlTopLevel()
            if (this.ignoreCase) sql.append("UPPER(")
            sql.append(field.getColumnName(eqb.mainEntityDefinition))
            if (this.ignoreCase) sql.append(")")
            sql.append(' ')
            sql.append(getComparisonOperatorString(this.operator))
            sql.append(' ')
            if (this.ignoreCase) sql.append("UPPER(")
            sql.append(toField.getColumnName(eqb.mainEntityDefinition))
            if (this.ignoreCase) sql.append(")")
        }

        @Override
        boolean mapMatches(Map<String, ?> map) {
            return compareByOperator(map.get(field.fieldName), this.operator, map.get(toField.fieldName))
        }

        @Override
        EntityCondition ignoreCase() { this.ignoreCase = true; return this }

        @Override
        String toString() {
            return field + " " + getComparisonOperatorString(this.operator) + " " + toField
        }

        @Override
        int hashCode() {
            return (field ? field.hashCode() : 0) + operator.hashCode() + (toField ? toField.hashCode() : 0) + ignoreCase.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            FieldToFieldCondition that = (FieldToFieldCondition) o
            if (!this.field.equals(that.field)) return false
            if (this.operator != that.operator) return false
            if (!this.toField.equals(that.toField)) return false
            if (this.ignoreCase != that.ignoreCase) return false
            return true
        }
    }

    public static class ListCondition extends EntityConditionImplBase {
        protected List<EntityConditionImplBase> conditionList
        protected JoinOperator operator

        ListCondition(EntityConditionFactoryImpl ecFactoryImpl,
                List<EntityConditionImplBase> conditionList, JoinOperator operator) {
            super(ecFactoryImpl)
            this.conditionList = conditionList ? conditionList : new LinkedList()
            this.operator = operator ? operator : JoinOperator.AND
        }

        void addCondition(EntityConditionImplBase condition) { conditionList.add(condition) }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            if (!this.conditionList) return

            StringBuilder sql = eqb.getSqlTopLevel()
            sql.append('(')
            boolean isFirst = true
            for (EntityConditionImplBase condition in this.conditionList) {
                if (isFirst) isFirst = false else {
                    sql.append(' ').append(getJoinOperatorString(this.operator)).append(' ')
                }
                condition.makeSqlWhere(eqb)
            }
        }

        @Override
        boolean mapMatches(Map<String, ?> map) {
            for (EntityConditionImplBase condition in this.conditionList) {
                boolean conditionMatches = condition.mapMatches(map)
                if (conditionMatches && this.operator == JoinOperator.OR) return true
                if (!conditionMatches && this.operator == JoinOperator.AND) return false
            }
            // if we got here it means that it's an OR with no trues, or an AND with no falses
            return (this.operator == JoinOperator.AND)
        }

        @Override
        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        @Override
        String toString() {
            StringBuilder sb = new StringBuilder()
            for (EntityConditionImplBase condition in this.conditionList) {
                if (sb.length() > 0) sb.append(' ').append(getJoinOperatorString(this.operator)).append(' ')
                sb.append(condition.toString())
            }
            return sb.toString()
        }

        @Override
        int hashCode() {
            return (conditionList ? conditionList.hashCode() : 0) + operator.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            ListCondition that = (ListCondition) o
            if (this.operator != that.operator) return false
            if (!this.conditionList.equals(that.conditionList)) return false
            return true
        }
    }

    public static class MapCondition extends EntityConditionImplBase {
        protected Map<String, ?> fieldMap
        protected ComparisonOperator comparisonOperator
        protected JoinOperator joinOperator
        protected boolean ignoreCase = false

        MapCondition(EntityConditionFactoryImpl ecFactoryImpl,
                Map<String, ?> fieldMap, ComparisonOperator comparisonOperator, JoinOperator joinOperator) {
            super(ecFactoryImpl)
            this.fieldMap = fieldMap ? fieldMap : new HashMap()
            this.comparisonOperator = comparisonOperator ? comparisonOperator : ComparisonOperator.EQUALS
            this.joinOperator = joinOperator ? joinOperator : JoinOperator.AND
        }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            this.makeCondition().makeSqlWhere(eqb)
        }

        @Override
        boolean mapMatches(Map<String, ?> map) {
            return this.makeCondition().mapMatches(map)
        }

        @Override
        EntityCondition ignoreCase() { this.ignoreCase = true; return this }

        @Override
        String toString() {
            return this.makeCondition().toString()
            /* might want to do something like this at some point, but above is probably better for now
            StringBuilder sb = new StringBuilder()
            for (Map.Entry fieldEntry in this.fieldMap.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(' ')
                    sb.append(getJoinOperatorString(this.joinOperator))
                    sb.append(' ')
                }
                sb.append(fieldEntry.getKey())
                sb.append(' ')
                sb.append(getComparisonOperatorString(this.comparisonOperator))
                sb.append(' ')
                sb.append(fieldEntry.getValue())
            }
            return sb.toString()
            */
        }

        protected EntityConditionImplBase makeCondition() {
            List conditionList = new LinkedList()
            for (Map.Entry<String, ?> fieldEntry in this.fieldMap.entrySet()) {
                EntityConditionImplBase newCondition = this.ecFactoryImpl.makeCondition(fieldEntry.getKey(),
                        this.comparisonOperator, fieldEntry.getValue())
                if (this.ignoreCase) newCondition.ignoreCase()
                conditionList.add(newCondition)
            }
            return this.ecFactoryImpl.makeCondition(conditionList, this.joinOperator)
        }

        @Override
        int hashCode() {
            return (fieldMap ? fieldMap.hashCode() : 0) + comparisonOperator.hashCode() + joinOperator.hashCode() +
                    ignoreCase.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            MapCondition that = (MapCondition) o
            if (this.comparisonOperator != that.comparisonOperator) return false
            if (this.joinOperator != that.joinOperator) return false
            if (this.ignoreCase != that.ignoreCase) return false
            if (!this.fieldMap.equals(that.fieldMap)) return false
            return true
        }
    }

    public static class DateCondition extends EntityConditionImplBase {
        protected String fromFieldName
        protected String thruFieldName
        protected Timestamp compareStamp

        DateCondition(EntityConditionFactoryImpl ecFactoryImpl,
                String fromFieldName, String thruFieldName, Timestamp compareStamp) {
            super(ecFactoryImpl)
            this.fromFieldName = fromFieldName ? fromFieldName : "fromDate"
            this.thruFieldName = thruFieldName ? thruFieldName : "thruDate"
            this.compareStamp = compareStamp
        }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            this.makeCondition().makeSqlWhere(eqb)
        }

        @Override
        boolean mapMatches(Map<String, ?> map) {
            return this.makeCondition().mapMatches(map)
        }

        @Override
        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        @Override
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

        @Override
        int hashCode() {
            return (compareStamp ? compareStamp.hashCode() : 0) + fromFieldName.hashCode() + thruFieldName.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            DateCondition that = (DateCondition) o
            if (!this.fromFieldName.equals(that.fromFieldName)) return false
            if (!this.thruFieldName.equals(that.thruFieldName)) return false
            if (this.compareStamp != that.compareStamp) return false
            return true
        }
    }

    public static class WhereCondition extends EntityConditionImplBase {
        protected String sqlWhereClause

        WhereCondition(EntityConditionFactoryImpl ecFactoryImpl, String sqlWhereClause) {
            super(ecFactoryImpl)
            this.sqlWhereClause = sqlWhereClause ? sqlWhereClause : ""
        }

        @Override
        void makeSqlWhere(EntityQueryBuilder eqb) {
            eqb.getSqlTopLevel().append(this.sqlWhereClause)
        }

        @Override
        boolean mapMatches(Map<String, ?> map) {
            // NOTE: always return false unless we eventually implement some sort of SQL parsing, for caching/etc
            // always consider not matching
            logger.warn("The mapMatches for the SQL Where Condition is not supported, text is [${this.sqlWhereClause}]")
            return false
        }

        @Override
        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        @Override
        String toString() { return this.sqlWhereClause }

        @Override
        int hashCode() { return (sqlWhereClause ? sqlWhereClause.hashCode() : 0) }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            WhereCondition that = (WhereCondition) o
            if (!this.sqlWhereClause.equals(that.sqlWhereClause)) return false
            return true
        }
    }

    protected static class ConditionField {
        String entityAlias = null
        String fieldName
        EntityDefinition aliasEntityDef = null

        ConditionField(String fieldName) {
            this.fieldName = fieldName
        }
        ConditionField(String entityAlias, String fieldName, EntityDefinition aliasEntityDef) {
            this.entityAlias = entityAlias
            this.fieldName = fieldName
            this.aliasEntityDef = aliasEntityDef
        }

        String getColumnName(EntityDefinition ed) {
            StringBuilder colName = new StringBuilder()
            // NOTE: this could have issues with view-entities as member entities where they have functions/etc; we may
            // have to pass the prefix in to have it added inside functions/etc
            if (this.entityAlias) colName.append(this.entityAlias).append('.')
            if (this.aliasEntityDef) {
                colName.append(this.aliasEntityDef.getColumnName(this.fieldName, false))
            } else {
                colName.append(ed.getColumnName(this.fieldName, false))
            }
            return colName.toString()
        }

        Node getFieldNode(EntityDefinition ed) {
            if (this.aliasEntityDef) {
                return this.aliasEntityDef.getFieldNode(fieldName)
            } else {
                return ed.getFieldNode(fieldName)
            }
        }

        @Override
        String toString() { return (entityAlias ? entityAlias+"." : "") + fieldName }

        @Override
        int hashCode() {
            return (entityAlias ? entityAlias.hashCode() : 0) + (fieldName ? fieldName.hashCode() : 0) +
                   (aliasEntityDef ? aliasEntityDef.hashCode() : 0)
        }

        @Override
        boolean equals(Object o) {
            if (o == null || o.getClass() != this.getClass()) return false
            ConditionField that = (ConditionField) o
            if (this.entityAlias != that.entityAlias) return false
            if (this.aliasEntityDef != that.aliasEntityDef) return false
            if (!this.fieldName.equals(that.fieldName)) return false
            return true
        }
    }

    protected static final Map<ComparisonOperator, String> comparisonOperatorStringMap = new HashMap()
    static {
        comparisonOperatorStringMap.put(ComparisonOperator.EQUALS, "=")
        comparisonOperatorStringMap.put(ComparisonOperator.NOT_EQUAL, "<>")
        comparisonOperatorStringMap.put(ComparisonOperator.LESS_THAN, "<")
        comparisonOperatorStringMap.put(ComparisonOperator.GREATER_THAN, ">")
        comparisonOperatorStringMap.put(ComparisonOperator.LESS_THAN_EQUAL_TO, "<=")
        comparisonOperatorStringMap.put(ComparisonOperator.GREATER_THAN_EQUAL_TO, ">=")
        comparisonOperatorStringMap.put(ComparisonOperator.IN, "IN")
        comparisonOperatorStringMap.put(ComparisonOperator.NOT_IN, "NOT IN")
        comparisonOperatorStringMap.put(ComparisonOperator.BETWEEN, "BETWEEN")
        comparisonOperatorStringMap.put(ComparisonOperator.LIKE, "LIKE")
        comparisonOperatorStringMap.put(ComparisonOperator.NOT_LIKE, "NOT LIKE")
    }
    protected static final Map<String, ComparisonOperator> stringComparisonOperatorMap = [
            "=":ComparisonOperator.EQUALS,
            "equals":ComparisonOperator.EQUALS,

            "not-equals":ComparisonOperator.NOT_EQUAL,
            "not-equal":ComparisonOperator.NOT_EQUAL,
            "!=":ComparisonOperator.NOT_EQUAL,
            "<>":ComparisonOperator.NOT_EQUAL,

            "less-than":ComparisonOperator.LESS_THAN,
            "less":ComparisonOperator.LESS_THAN,
            "<":ComparisonOperator.LESS_THAN,

            "greater-than":ComparisonOperator.GREATER_THAN,
            "greater":ComparisonOperator.GREATER_THAN,
            ">":ComparisonOperator.GREATER_THAN,

            "less-than-equal-to":ComparisonOperator.LESS_THAN_EQUAL_TO,
            "less-equals":ComparisonOperator.LESS_THAN_EQUAL_TO,
            "<=":ComparisonOperator.LESS_THAN_EQUAL_TO,

            "greater-than-equal-to":ComparisonOperator.GREATER_THAN_EQUAL_TO,
            "greater-equals":ComparisonOperator.GREATER_THAN_EQUAL_TO,
            ">=":ComparisonOperator.GREATER_THAN_EQUAL_TO,

            "in":ComparisonOperator.IN,
            "IN":ComparisonOperator.IN,

            "not-in":ComparisonOperator.NOT_IN,
            "NOT IN":ComparisonOperator.NOT_IN,

            "between":ComparisonOperator.BETWEEN,
            "BETWEEN":ComparisonOperator.BETWEEN,

            "like":ComparisonOperator.LIKE,
            "LIKE":ComparisonOperator.LIKE,

            "not-like":ComparisonOperator.LIKE,
            "NOT LIKE":ComparisonOperator.NOT_LIKE
    ]

    static String getJoinOperatorString(JoinOperator op) {
        return op == JoinOperator.OR ? "OR" : "AND"
    }
    static JoinOperator getJoinOperator(String opName) {
        if (!opName) return JoinOperator.AND
        switch (opName) {
            case "or":
            case "OR": return JoinOperator.OR
            case "and":
            case "AND":
            default: return JoinOperator.AND
        }
    }
    static String getComparisonOperatorString(ComparisonOperator op) {
        return comparisonOperatorStringMap.get(op)
    }
    static ComparisonOperator getComparisonOperator(String opName) {
        return stringComparisonOperatorMap.get(opName)
    }

    static boolean compareByOperator(Object value1, ComparisonOperator op, Object value2) {
        switch (op) {
        case ComparisonOperator.EQUALS:
            return value1 == value2
        case ComparisonOperator.NOT_EQUAL:
            return value1 != value2
        case ComparisonOperator.LESS_THAN:
            return value1 < value2
        case ComparisonOperator.GREATER_THAN:
            return value1 > value2
        case ComparisonOperator.LESS_THAN_EQUAL_TO:
            return value1 <= value2
        case ComparisonOperator.GREATER_THAN_EQUAL_TO:
            return value1 >= value2
        case ComparisonOperator.IN:
            if (value2 instanceof Collection) {
                return ((Collection) value2).contains(value1)
            } else {
                // not a Collection, try equals
                return value1 == value2
            }
        case ComparisonOperator.NOT_IN:
            if (value2 instanceof Collection) {
                return !((Collection) value2).contains(value1)
            } else {
                // not a Collection, try not-equals
                return value1 != value2
            }
        case ComparisonOperator.BETWEEN:
            if (value2 instanceof Collection && ((Collection) value2).size() == 2) {
                Iterator iterator = ((Collection) value2).iterator()
                Object lowObj = iterator.next()
                Object highObj = iterator.next()
                return lowObj <= value1 && value1 < highObj
            } else {
                return false
            }
        case ComparisonOperator.LIKE:
            return StupidUtilities.compareLike(value1, value2)
        case ComparisonOperator.NOT_LIKE:
            return !StupidUtilities.compareLike(value1, value2)
        }
        // default return false
        return false
    }
}
