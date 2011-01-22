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
import org.moqui.entity.EntityException
import org.moqui.impl.entity.EntityConditionFactoryImpl.EntityConditionImplBase

import java.sql.ResultSet
import java.sql.Connection
import java.sql.SQLException
import org.apache.commons.collections.set.ListOrderedSet
import org.moqui.context.Cache
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.impl.entity.EntityConditionFactoryImpl.ListCondition

class EntityFindImpl implements EntityFind {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityFindImpl.class)

    protected final EntityFacadeImpl efi

    protected String entityName
    protected EntityDefinition entityDef = null
    protected EntityDynamicViewImpl dynamicView = null

    protected Map<String, Object> simpleAndMap = null
    protected EntityConditionImplBase whereEntityCondition = null
    protected EntityConditionImplBase havingEntityCondition = null

    /** This is always a ListOrderedSet so that we can get the results in a consistent order */
    protected ListOrderedSet fieldsToSelect = null
    protected List<String> orderByFields = null

    protected Boolean useCache = null
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

    /** @see org.moqui.entity.EntityFind#makeEntityDynamicView() */
    EntityDynamicView makeEntityDynamicView() {
        if (this.dynamicView) return this.dynamicView
        this.dynamicView = new EntityDynamicViewImpl(this)
        return this.dynamicView
    }

    // ======================== Conditions (Where and Having) =================

    /** @see org.moqui.entity.EntityFind#condition(String, Object) */
    EntityFind condition(String fieldName, Object value) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap()
        this.simpleAndMap.put(fieldName, value)
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(Map<String,?>) */
    EntityFind condition(Map<String, ?> fields) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap()
        getEntityDef().setFields(fields, this.simpleAndMap, true, null, null)
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(EntityCondition) */
    EntityFind condition(EntityCondition condition) {
        if (!condition) return this
        if (whereEntityCondition) {
            // use ListCondition instead of ANDing two at a time to avoid a bunch of nested ANDs
            if (whereEntityCondition instanceof ListCondition) {
                ((ListCondition) whereEntityCondition).addCondition((EntityConditionImplBase) condition)
            } else {
                whereEntityCondition = efi.conditionFactory.makeCondition([whereEntityCondition, condition])
            }
        } else {
            whereEntityCondition = (EntityConditionImplBase) condition
        }
        return this
    }

    /** @see org.moqui.entity.EntityFind#havingCondition(EntityCondition) */
    EntityFind havingCondition(EntityCondition condition) {
        if (!condition) return this
        if (havingEntityCondition) {
            // use ListCondition instead of ANDing two at a time to avoid a bunch of nested ANDs
            if (havingEntityCondition instanceof ListCondition) {
                ((ListCondition) havingEntityCondition).addCondition((EntityConditionImplBase) condition)
            } else {
                havingEntityCondition = efi.conditionFactory.makeCondition([havingEntityCondition, condition])
            }
        } else {
            havingEntityCondition = (EntityConditionImplBase) condition
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
        if (!this.fieldsToSelect) this.fieldsToSelect = new ListOrderedSet()
        if (fieldToSelect) this.fieldsToSelect.add(fieldToSelect)
        return this
    }

    /** @see org.moqui.entity.EntityFind#selectFields(Collection<String>) */
    EntityFind selectFields(Collection<String> fieldsToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new ListOrderedSet()
        if (fieldsToSelect) this.fieldsToSelect.addAll(fieldsToSelect)
        return this
    }

    /** @see org.moqui.entity.EntityFind#getSelectFields() */
    List<String> getSelectFields() {
        return this.fieldsToSelect ? this.fieldsToSelect.asList() : null
    }

    /** @see org.moqui.entity.EntityFind#orderBy(String) */
    EntityFind orderBy(String orderByFieldName) {
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        if (orderByFieldName) this.orderByFields.add(orderByFieldName)
        return this
    }

    /** @see org.moqui.entity.EntityFind#orderBy(List<String>) */
    EntityFind orderBy(List<String> orderByFieldNames) {
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        if (orderByFieldNames) this.orderByFields.addAll(orderByFieldNames)
        return this
    }

    /** @see org.moqui.entity.EntityFind#getOrderBy() */
    List<String> getOrderBy() {
        return this.orderByFields ? Collections.unmodifiableList(this.orderByFields) : null
    }

    /** @see org.moqui.entity.EntityFind#useCache(boolean) */
    EntityFind useCache(Boolean useCache) {
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
    EntityValue one() throws EntityException {
        if (this.dynamicView) {
            throw new IllegalArgumentException("Dynamic View not supported for 'one' find.")
        }

        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        Cache entityOneCache = null
        if (this.shouldCache()) {
            entityOneCache = this.efi.ecfi.getCacheFacade().getCache("entity.one.${this.entityName}")
            if (entityOneCache.containsKey(whereCondition)) return (EntityValue) entityOneCache.get(whereCondition)
        }

        // for find one we'll always use the basic result set type and concurrency:
        this.resultSetType(ResultSet.TYPE_FORWARD_ONLY)
        this.resultSetConcurrency(ResultSet.CONCUR_READ_ONLY)

        EntityDefinition entityDefinition = this.getEntityDef()
        EntityFindBuilder efb = new EntityFindBuilder(entityDefinition, this)

        // we always want fieldsToSelect populated so that we know the order of the results coming back
        if (!this.fieldsToSelect) this.selectFields(entityDefinition.getFieldNames(false, true))
        // SELECT fields
        efb.makeSqlSelectFields(this.fieldsToSelect)
        // FROM Clause
        efb.makeSqlFromClause()

        // WHERE clause only for one/pk query
        // NOTE: do this here after caching because this will always be added on and isn't a part of the original where
        EntityConditionImplBase viewWhere = entityDefinition.makeViewWhereCondition()
        if (viewWhere) whereCondition = this.efi.getConditionFactory().makeCondition(whereCondition, JoinOperator.AND, viewWhere)
        efb.startWhereClause()
        whereCondition.makeSqlWhere(efb)

        if (this.forUpdate) efb.makeForUpdate()

        // run the SQL now that it is built
        EntityValueImpl newEntityValue = null
        try {
            efb.makeConnection()
            efb.makePreparedStatement()
            efb.setPreparedStatementValues()

            ResultSet rs = efb.executeQuery()
            if (rs.next()) {
                newEntityValue = new EntityValueImpl(entityDefinition, this.efi)
                int j = 1
                for (String fieldName in this.fieldsToSelect) {
                    EntityQueryBuilder.getResultSetValue(rs, j, entityDefinition.getFieldNode(fieldName), newEntityValue, this.efi)
                    j++
                }
            } else {
                logger.trace("Result set was empty for find on entity [${this.entityName}] with condition [${whereCondition.toString()}]")
            }
            if (rs.next()) {
                logger.trace("Found more than one result for condition [${whereCondition}] on entity [${entityDefinition.getEntityName()}]")
            }
        } finally {
            efb.closeAll()
        }

        if (this.shouldCache()) {
            // put it in whether null or not
            entityOneCache.put(whereCondition, newEntityValue)
        }
        return newEntityValue
    }

    /** @see org.moqui.entity.EntityFind#list() */
    EntityList list() throws EntityException {
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        Cache entityListCache = null
        // NOTE: don't cache if there is a having condition, for now just support where
        boolean doCache = !this.havingEntityCondition && this.shouldCache()
        if (doCache) {
            entityListCache = this.efi.ecfi.getCacheFacade().getCache("entity.list.${this.entityName}")
            if (entityListCache.containsKey(whereCondition)) return (EntityList) entityListCache.get(whereCondition)
        }

        EntityListIterator eli = null
        EntityList el = null
        try {
            eli = this.iterator()
            el = eli.getCompleteList()
        } finally {
            if (eli) eli.close()
        }

        if (doCache && el) {
            entityListCache.put(whereCondition, el)
        }
        return el
    }

    /** @see org.moqui.entity.EntityFind#iterator() */
    EntityListIterator iterator() throws EntityException {
        EntityDefinition entityDefinition = this.getEntityDef()
        EntityFindBuilder efb = new EntityFindBuilder(entityDefinition, this)

        if (this.getDistinct() || (entityDefinition.isViewEntity() &&
                entityDefinition.getEntityNode()."entity-condition"[0]."@distinct") == "true") {
            efb.makeDistinct()
        }

        // we always want fieldsToSelect populated so that we know the order of the results coming back
        if (!this.fieldsToSelect) this.selectFields(entityDefinition.getFieldNames(false, true))
        // select fields
        efb.makeSqlSelectFields(this.fieldsToSelect)
        
        // from Clause
        efb.makeSqlFromClause()

        // where clause
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        EntityConditionImplBase viewWhere = entityDefinition.makeViewWhereCondition()
        if (whereCondition && viewWhere) {
            whereCondition = this.efi.getConditionFactory().makeCondition(whereCondition, JoinOperator.AND, viewWhere)
        } else if (viewWhere) {
            whereCondition = viewWhere
        }
        if (whereCondition) {
            efb.startWhereClause()
            whereCondition.makeSqlWhere(efb)
        }

        // group by clause
        efb.makeGroupByClause()

        // having clause
        EntityConditionImplBase havingCondition = this.getHavingEntityCondition()
        EntityConditionImplBase viewHaving = entityDefinition.makeViewHavingCondition()
        if (havingCondition && viewHaving) {
            havingCondition = this.efi.getConditionFactory().makeCondition(havingCondition, JoinOperator.AND, viewHaving)
        } else if (viewHaving) {
            havingCondition = viewHaving
        }
        if (havingCondition) {
            efb.startHavingClause()
            havingCondition.makeSqlWhere(efb)
        }

        // order by clause
        List<String> orderByExpanded = new ArrayList()
        // add the manually specified ones, then the ones in the view entity's entity-condition
        if (this.getOrderBy()) orderByExpanded.addAll(this.getOrderBy())
        for (Node orderBy in entityDefinition.getEntityNode()."entity-condition"[0]?."order-by") {
            orderByExpanded.add(orderBy."@field-name")
        }
        efb.makeOrderByClause(orderByExpanded)

        if (this.forUpdate) efb.makeForUpdate()

        // run the SQL now that it is built
        EntityListIteratorImpl eli = null
        try {
            Connection con = efb.makeConnection()
            efb.makePreparedStatement()
            efb.setPreparedStatementValues()

            ResultSet rs = efb.executeQuery()

            eli = new EntityListIteratorImpl(con, rs, entityDefinition, this.fieldsToSelect, this.efi)
        } catch (SQLException e) {
            throw new EntityException("Error finding value", e)
        } finally {
            efb.closePsOnly()
            // ResultSet will be closed in the EntityListIterator
        }
        return eli
    }

    /** @see org.moqui.entity.EntityFind#count() */
    long count() throws EntityException {
        EntityConditionImplBase whereCondition = this.getWhereEntityCondition()
        Cache entityCountCache = null
        // NOTE: don't cache if there is a having condition, for now just support where
        boolean doCache = !this.havingEntityCondition && this.shouldCache()
        if (doCache) {
            entityCountCache = this.efi.ecfi.getCacheFacade().getCache("entity.count.${this.entityName}")
            if (entityCountCache.containsKey(whereCondition)) return (Long) entityCountCache.get(whereCondition)
        }

        EntityDefinition entityDefinition = this.getEntityDef()
        if (!this.fieldsToSelect) this.selectFields(entityDefinition.getFieldNames(false, true))

        EntityFindBuilder efb = new EntityFindBuilder(entityDefinition, this)

        // count function instead of select fields
        efb.makeCountFunction()

        // from Clause
        efb.makeSqlFromClause()

        // where clause
        efb.startWhereClause()
        EntityConditionImplBase viewWhere = entityDefinition.makeViewWhereCondition()
        if (whereCondition && viewWhere) {
            whereCondition = this.efi.getConditionFactory().makeCondition(whereCondition, JoinOperator.AND, viewWhere)
        } else if (viewWhere) {
            whereCondition = viewWhere
        }
        if (whereCondition) whereCondition.makeSqlWhere(efb)

        // group by clause
        efb.makeGroupByClause()

        // having clause
        efb.startHavingClause()
        EntityConditionImplBase havingCondition = this.getHavingEntityCondition()
        EntityConditionImplBase viewHaving = entityDefinition.makeViewHavingCondition()
        if (havingCondition && viewHaving) {
            havingCondition = this.efi.getConditionFactory().makeCondition(havingCondition, JoinOperator.AND, viewHaving)
        } else if (viewHaving) {
            havingCondition = viewHaving
        }
        if (havingCondition) havingCondition.makeSqlWhere(efb)

        efb.closeCountFunctionIfGroupBy()

        // run the SQL now that it is built
        long count = 0
        try {
            efb.makeConnection()
            efb.makePreparedStatement()
            efb.setPreparedStatementValues()

            ResultSet rs = efb.executeQuery()
            if (rs.next()) count = rs.getLong(1)
        } catch (SQLException e) {
            throw new EntityException("Error finding count", e)
        } finally {
            efb.closeAll()
        }

        if (doCache) {
            entityCountCache.put(whereCondition, count)
        }

        return count
    }

    long updateAll(Map<String, ?> fieldsToSet) {
        // NOTE: this code isn't very efficient, but will do the trick and cause all EECAs to be fired
        // NOTE: consider expanding this to do a bulk update in the DB if there are no EECAs for the entity
        this.useCache(false).forUpdate(true)
        EntityListIterator eli = null
        long totalUpdated = 0
        try {
            eli = iterator()
            EntityValue value
            while ((value = eli.next()) != null) {
                value.putAll(fieldsToSet)
                if (value.isModified()) {
                    // NOTE: this may blow up in some cases, if it does then change this to put all values to update in a
                    // list and update them after the eli is closed, or implement and use the eli.set(value) method
                    value.update()
                    totalUpdated++
                }
            }
        } finally {
            eli.close()
        }
        return totalUpdated
    }

    long deleteAll() {
        // NOTE: this code isn't very efficient, but will do the trick and cause all EECAs to be fired
        // NOTE: consider expanding this to do a bulk delete in the DB if there are no EECAs for the entity
        this.useCache(false).forUpdate(true)
        EntityListIterator eli = null
        long totalDeleted = 0
        try {
            eli = iterator()
            while (eli.next() != null) {
                eli.remove()
                totalDeleted++
            }
        } finally {
            eli.close()
        }
        return totalDeleted
    }

    protected EntityDefinition getEntityDef() {
        if (this.entityDef) return this.entityDef
        if (this.dynamicView) {
            this.entityDef = this.dynamicView.makeEntityDefinition()
        } else {
            this.entityDef = this.efi.getEntityDefinition(this.entityName)
        }
        return this.entityDef
    }

    protected boolean shouldCache() {
        if (this.dynamicView) return false
        String entityCache = this.getEntityDef().getEntityNode()."@use-cache" == "true"
        return ((this.useCache == Boolean.TRUE && entityCache != "never") || entityCache == "true")
    }
}
