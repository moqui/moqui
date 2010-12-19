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

import org.moqui.entity.EntityFind
import org.moqui.entity.EntityDynamicView
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.moqui.entity.EntityListIterator
import java.sql.ResultSet
import org.moqui.impl.entity.EntityConditionFactoryImpl.EntityConditionImplBase

class EntityFindImpl implements EntityFind {

    protected final EntityFacadeImpl efi

    protected String entityName
    protected EntityDynamicView dynamicView = null

    protected Map<String, Object> simpleAndMap = null
    protected EntityConditionImplBase whereEntityCondition = null
    protected EntityConditionImplBase havingEntityCondition = null

    protected Set<String> fieldsToSelect = null
    protected List<String> orderByFields = null

    protected boolean useCache = false
    protected boolean forUpdate = false

    protected int resultSetType = ResultSet.TYPE_SCROLL_INSENSITIVE
    protected int resultSetConcurrency = ResultSet.CONCUR_READ_ONLY
    protected Integer fetchSize = null
    protected Integer maxRows = null
    protected boolean distinct = false

    EntityFindImpl(EntityFacadeImpl efi, String entityName) {
        this.efi = efi
        this.entityName = entityName
    }

    /** @see org.moqui.entity.EntityFind#entity(String) */
    EntityFind entity(String entityName) {
        this.entityName = entityName
        return this
    }

    /** @see org.moqui.entity.EntityFind#getEntity() */
    String getEntity() {
        return this.entityName
    }

    /** @see org.moqui.entity.EntityFind#entityDynamicView(EntityDynamicView) */
    EntityFind entityDynamicView(EntityDynamicView dynamicView) {
        this.dynamicView = dynamicView
        return this
    }

    /** @see org.moqui.entity.EntityFind#getEntityDynamicView() */
    String getEntityDynamicView() {
        return this.dynamicView
    }

    // ======================== Conditions (Where and Having) =================

    /** @see org.moqui.entity.EntityFind#condition(String, Object) */
    EntityFind condition(String fieldName, Object value) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap();
        this.simpleAndMap.put(fieldName, value)
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(Map<String,?>) */
    EntityFind condition(Map<String, ?> fields) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap();
        this.simpleAndMap.putAll(fields)
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(EntityCondition) */
    EntityFind condition(EntityCondition condition) {
        if (this.whereEntityCondition) {
            this.whereEntityCondition = this.efi.conditionFactory.makeCondition(
                    this.whereEntityCondition, EntityCondition.JoinOperator.AND, condition)
        } else {
            this.whereEntityCondition = condition;
        }
        return this
    }

    /** @see org.moqui.entity.EntityFind#havingCondition(EntityCondition) */
    EntityFind havingCondition(EntityCondition condition) {
        if (this.havingEntityCondition) {
            this.havingEntityCondition = this.efi.conditionFactory.makeCondition(
                    this.havingEntityCondition, EntityCondition.JoinOperator.AND, condition)
        } else {
            this.havingEntityCondition = condition;
        }
        return this
    }

    /** @see org.moqui.entity.EntityFind#getWhereEntityCondition() */
    EntityCondition getWhereEntityCondition() {
        if (this.simpleAndMap) {
            EntityCondition simpleAndMapCond = this.efi.conditionFactory.makeCondition(this.simpleAndMap)
            if (this.whereEntityCondition) {
                return this.efi.conditionFactory.makeCondition(simpleAndMapCond, EntityCondition.JoinOperator.AND, this.whereEntityCondition)
            } else {
                return simpleAndMapCond
            }
        } else {
            return this.whereEntityCondition
        }
    }

    /** @see org.moqui.entity.EntityFind#getHavingEntityCondition() */
    EntityCondition getHavingEntityCondition() {
        return this.havingEntityCondition
    }

    // ======================== General/Common Options ========================

    /** @see org.moqui.entity.EntityFind#selectField(String) */
    EntityFind selectField(String fieldToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new HashSet()
        this.fieldsToSelect.add(fieldToSelect)
        return this
    }

    /** @see org.moqui.entity.EntityFind#selectFields(Collection<String>) */
    EntityFind selectFields(Collection<String> fieldsToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new HashSet()
        this.fieldsToSelect.addAll(fieldsToSelect)
        return this
    }

    /** @see org.moqui.entity.EntityFind#getSelectFields() */
    Set<String> getSelectFields() {
        return Collections.unmodifiableSet(this.fieldsToSelect)
    }

    /** @see org.moqui.entity.EntityFind#orderBy(String) */
    EntityFind orderBy(String orderByFieldName) {
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        this.orderByFields.add(orderByFieldName)
        return this
    }

    /** @see org.moqui.entity.EntityFind#orderBy(List<String>) */
    EntityFind orderBy(List<String> orderByFieldNames) {
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        this.orderByFields.addAll(orderByFieldNames)
        return this
    }

    /** @see org.moqui.entity.EntityFind#getOrderBy() */
    List<String> getOrderBy() {
        return Collections.unmodifiableList(this.orderByFields)
    }

    /** @see org.moqui.entity.EntityFind#useCache(boolean) */
    EntityFind useCache(boolean useCache) {
        this.useCache = useCache
        return this
    }

    /** @see org.moqui.entity.EntityFind#getUseCache() */
    boolean getUseCache() {
        return this.useCache
    }

    /** @see org.moqui.entity.EntityFind#forUpdate(boolean) */
    EntityFind forUpdate(boolean forUpdate) {
        this.forUpdate = forUpdate
        return this
    }

    /** @see org.moqui.entity.EntityFind#getForUpdate() */
    boolean getForUpdate() {
        return this.forUpdate
    }

    // ======================== Advanced Options ==============================

    /** @see org.moqui.entity.EntityFind#resultSetType(int) */
    EntityFind resultSetType(int resultSetType) {
        this.resultSetType = resultSetType
        return this
    }

    /** @see org.moqui.entity.EntityFind#getResultSetType() */
    int getResultSetType() {
        return this.resultSetType
    }

    /** @see org.moqui.entity.EntityFind#resultSetConcurrency(int) */
    EntityFind resultSetConcurrency(int resultSetConcurrency) {
        this.resultSetConcurrency = resultSetConcurrency
        return this
    }

    /** @see org.moqui.entity.EntityFind#getResultSetConcurrency() */
    int getResultSetConcurrency() {
        return this.resultSetConcurrency
    }

    /** @see org.moqui.entity.EntityFind#fetchSize(int) */
    EntityFind fetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize
        return this
    }

    /** @see org.moqui.entity.EntityFind#getFetchSize() */
    Integer getFetchSize() {
        return this.fetchSize
    }

    /** @see org.moqui.entity.EntityFind#maxRows(int) */
    EntityFind maxRows(Integer maxRows) {
        this.maxRows = maxRows
        return this
    }

    /** @see org.moqui.entity.EntityFind#getMaxRows() */
    Integer getMaxRows() {
        return this.maxRows
    }

    /** @see org.moqui.entity.EntityFind#distinct(boolean) */
    EntityFind distinct(boolean distinct) {
        this.distinct = distinct
        return this
    }

    /** @see org.moqui.entity.EntityFind#getDistinct() */
    boolean getDistinct() {
        return this.distinct
    }

    // ======================== Run Find Methods ==============================

    /** @see org.moqui.entity.EntityFind#one() */
    EntityValue one() {
        EntityFindBuilder efb = new EntityFindBuilder(this);

        if (this.dynamicView) {
            throw new IllegalArgumentException("Dynamic View not supported for 'one' find.");
        }
        EntityDefinition entityDefinition = this.efi.getDefinition(this.entityName)

        efb.makeSqlSelectFields(this.fieldsToSelect, entityDefinition)
        efb.makeSqlFromClause(entityDefinition)

        // where clause only for one/pk query
        efb.startWhereClause()
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        whereCondition.makeSqlWhere(efb)

        //String viewClause = makeViewWhereClause(modelEntity, joinStyle);

        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#list() */
    EntityList list() {
        EntityListIterator eli = this.iterator()
        return eli.getCompleteList()
    }

    /** @see org.moqui.entity.EntityFind#iterator() */
    EntityListIterator iterator() {
        EntityFindBuilder efb = new EntityFindBuilder(this);

        if (this.dynamicView) {
            // TODO: implement for dynamic views
        } else {
            EntityDefinition entityDefinition = this.efi.getDefinition(this.entityName)
            // TODO from/etc like in one() above
        }

        // where clause first
        efb.startWhereClause()
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        whereCondition.makeSqlWhere(efb)

        //String viewClause = makeViewWhereClause(modelEntity, joinStyle);

        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#count() */
    long count() {
        // TODO: implement this
        return 0;
    }
}
