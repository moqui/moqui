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
package org.moqui.impl.entity.condition

import org.moqui.entity.EntityCondition
import org.moqui.impl.entity.EntityQueryBuilder
import org.moqui.impl.entity.EntityConditionFactoryImpl

class FieldToFieldCondition extends EntityConditionImplBase {
    protected volatile Class localClass = null
    protected ConditionField field
    protected EntityCondition.ComparisonOperator operator
    protected ConditionField toField
    protected boolean ignoreCase = false

    FieldToFieldCondition(EntityConditionFactoryImpl ecFactoryImpl,
            ConditionField field, EntityCondition.ComparisonOperator operator, ConditionField toField) {
        super(ecFactoryImpl)
        this.field = field
        this.operator = operator ?: EQUALS
        this.toField = toField
    }

    Class getLocalClass() { if (this.localClass == null) this.localClass = this.getClass(); return this.localClass }

    @Override
    void makeSqlWhere(EntityQueryBuilder eqb) {
        StringBuilder sql = eqb.getSqlTopLevel()
        if (this.ignoreCase) sql.append("UPPER(")
        sql.append(field.getColumnName(eqb.getMainEd()))
        if (this.ignoreCase) sql.append(")")
        sql.append(' ')
        sql.append(EntityConditionFactoryImpl.getComparisonOperatorString(this.operator))
        sql.append(' ')
        if (this.ignoreCase) sql.append("UPPER(")
        sql.append(toField.getColumnName(eqb.getMainEd()))
        if (this.ignoreCase) sql.append(")")
    }

    @Override
    boolean mapMatches(Map<String, ?> map) {
        return EntityConditionFactoryImpl.compareByOperator(map.get(field.getFieldName()), this.operator, map.get(toField.getFieldName()))
    }

    @Override
    boolean populateMap(Map<String, ?> map) { return false }

    void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet) {
        // this will only be called for view-entity, so we'll either have a entityAlias or an aliased fieldName
        if (field.entityAlias) {
            entityAliasSet.add(field.entityAlias)
        } else {
            fieldAliasSet.add(field.fieldName)
        }
        if (toField.entityAlias) {
            entityAliasSet.add(toField.entityAlias)
        } else {
            fieldAliasSet.add(toField.fieldName)
        }
    }

    @Override
    EntityCondition ignoreCase() { this.ignoreCase = true; return this }

    @Override
    String toString() {
        return field + " " + EntityConditionFactoryImpl.getComparisonOperatorString(this.operator) + " " + toField
    }

    @Override
    int hashCode() {
        return (field ? field.hashCode() : 0) + operator.hashCode() + (toField ? toField.hashCode() : 0) + ignoreCase.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getLocalClass()) return false
        FieldToFieldCondition that = (FieldToFieldCondition) o
        if (!this.field.equalsConditionField(that.field)) return false
        // NOTE: for Java Enums the != is WAY faster than the .equals
        if (this.operator != that.operator) return false
        if (!this.toField.equalsConditionField(that.toField)) return false
        if (this.ignoreCase != that.ignoreCase) return false
        return true
    }
}
