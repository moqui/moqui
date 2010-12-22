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

import org.moqui.entity.EntityConditionFactory
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityCondition.ComparisonOperator
import java.sql.Timestamp

class EntityConditionFactoryImpl implements EntityConditionFactory {

    protected final EntityFacadeImpl efi;

    EntityConditionFactoryImpl(EntityFacadeImpl efi) {
        this.efi = efi;
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(EntityCondition, JoinOperator, EntityCondition) */
    EntityCondition makeCondition(EntityCondition lhs, JoinOperator operator, EntityCondition rhs) {
        return new BasicJoinCondition(this, lhs, operator, rhs)
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
        return new ListCondition(this, conditionList, operator)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(List<EntityCondition>) */
    EntityCondition makeCondition(List<EntityCondition> conditionList) {
        return new ListCondition(this, conditionList, JoinOperator.AND)
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
        protected EntityCondition lhs
        protected JoinOperator operator
        protected EntityCondition rhs

        BasicJoinCondition(EntityConditionFactoryImpl ecFactoryImpl,
                EntityCondition lhs, JoinOperator operator, EntityCondition rhs) {
            super(ecFactoryImpl)
            this.lhs = lhs
            this.operator = operator
            this.rhs = rhs
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // general SQL where clause style text with values included
            // TODO implement this
            return null
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
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // TODO implement this
            return null
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
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // TODO implement this
            return null
        }
    }

    public static class ListCondition extends EntityConditionImplBase {
        protected List<EntityCondition> conditionList
        protected JoinOperator operator

        ListCondition(EntityConditionFactoryImpl ecFactoryImpl,
                List<EntityCondition> conditionList, JoinOperator operator) {
            super(ecFactoryImpl)
            this.conditionList = conditionList
            this.operator = operator
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // TODO implement this
            return null
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
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // TODO implement this
            return null
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
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // TODO implement this
            return null
        }
    }

    public static class WhereCondition extends EntityConditionImplBase {
        protected String sqlWhereClause

        WhereCondition(EntityConditionFactoryImpl ecFactoryImpl, String sqlWhereClause) {
            super(ecFactoryImpl)
            this.sqlWhereClause = sqlWhereClause
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            // TODO implement this
        }

        boolean mapMatches(Map<String, ?> map) {
            // TODO implement this
            return false
        }

        String toString() {
            // TODO implement this
            return null
        }
    }
}
