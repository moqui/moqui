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

import org.moqui.impl.context.TransactionCache

import java.sql.ResultSet
import java.sql.Timestamp

import org.apache.commons.collections.set.ListOrderedSet

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityDynamicView
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityException
import org.moqui.entity.EntityList
import org.moqui.entity.EntityListIterator
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.context.CacheImpl
import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.impl.entity.condition.ListCondition

import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class EntityFindBase implements EntityFind {
    protected final static Logger logger = LoggerFactory.getLogger(EntityFindBase.class)

    protected final EntityFacadeImpl efi
    protected final TransactionCache txCache

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

    protected boolean distinct = false
    protected Integer offset = null
    protected Integer limit = null
    protected boolean forUpdate = false

    protected int resultSetType = ResultSet.TYPE_SCROLL_INSENSITIVE
    protected int resultSetConcurrency = ResultSet.CONCUR_READ_ONLY
    protected Integer fetchSize = null
    protected Integer maxRows = null

    protected boolean disableAuthz = false

    EntityFindBase(EntityFacadeImpl efi, String entityName) {
        this.efi = efi
        this.entityName = entityName
        this.txCache = (TransactionCache) efi.getEcfi().getTransactionFacade().getActiveSynchronization("TransactionCache")
    }

    EntityFacadeImpl getEfi() { return efi }

    @Override
    EntityFind entity(String entityName) { this.entityName = entityName; return this }

    @Override
    String getEntity() { return this.entityName }

    // ======================== Conditions (Where and Having) =================

    @Override
    EntityFind condition(String fieldName, Object value) {
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap()
        this.simpleAndMap.put(fieldName, value)
        return this
    }

    @Override
    EntityFind condition(String fieldName, EntityCondition.ComparisonOperator operator, Object value) {
        if (operator == EntityCondition.EQUALS) return condition(fieldName, value)
        return condition(efi.conditionFactory.makeCondition(fieldName, operator, value))
    }
    @Override
    EntityFind condition(String fieldName, String operator, Object value) {
        EntityCondition.ComparisonOperator opObj = EntityConditionFactoryImpl.stringComparisonOperatorMap.get(operator)
        if (opObj == null) throw new IllegalArgumentException("Operator [${operator}] is not a valid field comparison operator")
        return condition(efi.conditionFactory.makeCondition(fieldName, opObj, value))
    }

    @Override
    EntityFind conditionToField(String fieldName, EntityCondition.ComparisonOperator operator, String toFieldName) {
        return condition(efi.conditionFactory.makeConditionToField(fieldName, operator, toFieldName))
    }

    @Override
    EntityFind condition(Map<String, ?> fields) {
        if (!fields) return this
        if (!this.simpleAndMap) this.simpleAndMap = new HashMap()
        getEntityDef().setFields(fields, this.simpleAndMap, true, null, null)
        return this
    }

    @Override
    EntityFind condition(EntityCondition condition) {
        if (condition == null) return this
        if (whereEntityCondition) {
            // use ListCondition instead of ANDing two at a time to avoid a bunch of nested ANDs
            if (whereEntityCondition instanceof ListCondition && whereEntityCondition.getOperator() == EntityCondition.AND) {
                ((ListCondition) whereEntityCondition).addCondition((EntityConditionImplBase) condition)
            } else {
                whereEntityCondition =
                    (EntityConditionImplBase) efi.conditionFactory.makeCondition([whereEntityCondition, condition])
            }
        } else {
            whereEntityCondition = (EntityConditionImplBase) condition
        }
        return this
    }

    @Override
    EntityFind conditionDate(String fromFieldName, String thruFieldName, Timestamp compareStamp) {
        condition(efi.conditionFactory.makeConditionDate(fromFieldName, thruFieldName, compareStamp))
        return this
    }

    @Override
    EntityFind havingCondition(EntityCondition condition) {
        if (!condition) return this
        if (havingEntityCondition) {
            // use ListCondition instead of ANDing two at a time to avoid a bunch of nested ANDs
            if (havingEntityCondition instanceof ListCondition) {
                ((ListCondition) havingEntityCondition).addCondition((EntityConditionImplBase) condition)
            } else {
                havingEntityCondition =
                    (EntityConditionImplBase) efi.conditionFactory.makeCondition([havingEntityCondition, condition])
            }
        } else {
            havingEntityCondition = (EntityConditionImplBase) condition
        }
        return this
    }

    @Override
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

    @Override
    EntityCondition getHavingEntityCondition() {
        return this.havingEntityCondition
    }

    @Override
    EntityFind searchFormInputs(String inputFieldsMapName, String defaultOrderBy, boolean alwaysPaginate) {
        ExecutionContext ec = efi.getEcfi().getExecutionContext()
        Map inf = inputFieldsMapName ? (Map) ec.context[inputFieldsMapName] : efi.ecfi.executionContext.context
        EntityDefinition ed = getEntityDef()

        for (String fn in ed.getAllFieldNames()) {
            // NOTE: do we need to do type conversion here?

            // this will handle text-find
            if (inf.containsKey(fn) || inf.containsKey(fn + "_op")) {
                Object value = inf.get(fn)
                String op = inf.get(fn + "_op") ?: "equals"
                boolean not = (inf.get(fn + "_not") == "Y")
                boolean ic = (inf.get(fn + "_ic") == "Y")

                EntityCondition cond = null
                switch (op) {
                    case "equals":
                        if (value) {
                            cond = efi.conditionFactory.makeCondition(fn,
                                    not ? EntityCondition.NOT_EQUAL : EntityCondition.EQUALS, value)
                            if (ic) cond.ignoreCase()
                        }
                        break;
                    case "like":
                        if (value) {
                            cond = efi.conditionFactory.makeCondition(fn,
                                    not ? EntityCondition.NOT_LIKE : EntityCondition.LIKE, value)
                            if (ic) cond.ignoreCase()
                        }
                        break;
                    case "contains":
                        if (value) {
                            cond = efi.conditionFactory.makeCondition(fn,
                                    not ? EntityCondition.NOT_LIKE : EntityCondition.LIKE, "%${value}%")
                            if (ic) cond.ignoreCase()
                        }
                        break;
                    case "begins":
                        if (value) {
                            cond = efi.conditionFactory.makeCondition(fn,
                                    not ? EntityCondition.NOT_LIKE : EntityCondition.LIKE, "${value}%")
                            if (ic) cond.ignoreCase()
                        }
                        break;
                    case "empty":
                        cond = efi.conditionFactory.makeCondition(
                                efi.conditionFactory.makeCondition(fn,
                                        not ? EntityCondition.NOT_EQUAL : EntityCondition.EQUALS, null),
                                not ? EntityCondition.JoinOperator.AND : EntityCondition.JoinOperator.OR,
                                efi.conditionFactory.makeCondition(fn,
                                        not ? EntityCondition.NOT_EQUAL : EntityCondition.EQUALS, ""))
                        break;
                    case "in":
                        if (value) {
                            Collection valueList = null
                            if (value instanceof CharSequence) {
                                valueList = value.toString().split(",")
                            } else if (value instanceof Collection) {
                                valueList = value
                            }
                            if (valueList) {
                                cond = efi.conditionFactory.makeCondition(fn,
                                        not ? EntityCondition.NOT_IN : EntityCondition.IN, valueList)

                            }
                        }
                        break;
                }
                if (cond != null) this.condition(cond)
            } else if (inf.get(fn + "_period")) {
                List<Timestamp> range = ec.user.getPeriodRange((String) inf.get(fn + "_period"), (String) inf.get(fn + "_poffset"))
                this.condition(efi.conditionFactory.makeCondition(fn,
                        EntityCondition.GREATER_THAN_EQUAL_TO, range[0]))
                this.condition(efi.conditionFactory.makeCondition(fn,
                        EntityCondition.LESS_THAN, range[1]))
            } else {
                // these will handle range-find and date-find
                Object fromValue = inf.get(fn + "_from")
                if (fromValue && fromValue instanceof CharSequence) fromValue = ed.convertFieldString(fn, fromValue.toString())
                Object thruValue = inf.get(fn + "_thru")
                if (thruValue && thruValue instanceof CharSequence) thruValue = ed.convertFieldString(fn, thruValue.toString())

                if (inf.get(fn + "_from")) this.condition(efi.conditionFactory.makeCondition(fn,
                        EntityCondition.GREATER_THAN_EQUAL_TO, fromValue))
                if (inf.get(fn + "_thru")) this.condition(efi.conditionFactory.makeCondition(fn,
                        EntityCondition.LESS_THAN, thruValue))
            }
        }

        // always look for an orderByField parameter too
        String orderByString = inf.get("orderByField") ?: defaultOrderBy
        ec.context.put("orderByField", orderByString)
        this.orderBy(orderByString)

        // look for the pageIndex and optional pageSize parameters; don't set these if should cache as will disable the cached query
        if ((alwaysPaginate || inf.get("pageIndex")) && !shouldCache()) {
            int pageIndex = (inf.get("pageIndex") ?: 0) as int
            int pageSize = (inf.get("pageSize") ?: (this.limit ?: 20)) as int
            offset(pageIndex, pageSize)
            limit(pageSize)
        }

        // if there is a pageNoLimit clear out the limit regardless of other settings
        if (inf.get("pageNoLimit") == "true" || inf.get("pageNoLimit") == true) {
            this.offset = null
            this.limit = null
        }

        return this
    }

    EntityFind findNode(Node node) {
        ExecutionContext ec = this.efi.ecfi.executionContext

        this.entity((String) node["@entity-name"])
        if (node["@cache"]) { this.useCache(node["@cache"] == "true") }
        if (node["@for-update"]) this.forUpdate(node["@for-update"] == "true")
        if (node["@distinct"]) this.distinct(node["@distinct"] == "true")
        if (node["@offset"]) this.offset(node["@offset"] as Integer)
        if (node["@limit"]) this.limit(node["@limit"] as Integer)
        for (Node sf in (Collection<Node>) node["select-field"]) this.selectField((String) sf["@field-name"])
        for (Node ob in (Collection<Node>) node["order-by"]) this.orderBy((String) ob["@field-name"])

        if (!this.getUseCache()) {
            for (Node df in (Collection<Node>) node["date-filter"])
                this.condition(ec.entity.conditionFactory.makeConditionDate((String) df["@from-field-name"] ?: "fromDate",
                        (String) df["@thru-field-name"] ?: "thruDate",
                        (df["@valid-date"] ? ec.resource.evaluateContextField((String) df["@valid-date"], null) as Timestamp : ec.user.nowTimestamp)))
        }

        for (Node ecn in (Collection<Node>) node["econdition"]) {
            EntityCondition econd = ((EntityConditionFactoryImpl) efi.conditionFactory).makeActionCondition(ecn)
            if (econd != null) this.condition(econd)
        }
        for (Node ecs in (Collection<Node>) node["econditions"])
            this.condition(((EntityConditionFactoryImpl) efi.conditionFactory).makeActionConditions(ecs))
        for (Node eco in (Collection<Node>) node["econdition-object"])
            this.condition((EntityCondition) ec.resource.evaluateContextField((String) eco["@field"], null))

        if (node["search-form-inputs"]) {
            Node sfiNode = (Node) node["search-form-inputs"].first
            searchFormInputs((String) sfiNode["@input-fields-map"], (String) sfiNode["@default-order-by"], (sfiNode["@paginate"] ?: "true") as boolean)
        }
        if (node["having-econditions"]) {
            for (Node havingCond in (Collection<Node>) node["having-econditions"])
                this.havingCondition(((EntityConditionFactoryImpl) efi.conditionFactory).makeActionCondition(havingCond))
        }

        // logger.info("TOREMOVE Added findNode\n${node}\n${this.toString()}")

        return this
    }

    // ======================== General/Common Options ========================

    @Override
    EntityFind selectField(String fieldToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new ListOrderedSet()
        if (fieldToSelect) this.fieldsToSelect.add(fieldToSelect)
        return this
    }

    @Override
    EntityFind selectFields(Collection<String> fieldsToSelect) {
        if (!this.fieldsToSelect) this.fieldsToSelect = new ListOrderedSet()
        if (fieldsToSelect) this.fieldsToSelect.addAll(fieldsToSelect)
        return this
    }

    @Override
    List<String> getSelectFields() { return this.fieldsToSelect ? this.fieldsToSelect.asList() : null }

    @Override
    EntityFind orderBy(String orderByFieldName) {
        if (!orderByFieldName) return this
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        if (orderByFieldName.contains(",")) {
            for (String obsPart in orderByFieldName.split(",")) {
                String orderByName = obsPart.trim()
                EntityFindBuilder.FieldOrderOptions foo = new EntityFindBuilder.FieldOrderOptions(orderByName)
                if (getEntityDef().isField(foo.fieldName)) this.orderByFields.add(orderByName)
            }
        } else {
            EntityFindBuilder.FieldOrderOptions foo = new EntityFindBuilder.FieldOrderOptions(orderByFieldName)
            if (getEntityDef().isField(foo.fieldName)) this.orderByFields.add(orderByFieldName)
        }
        return this
    }

    @Override
    EntityFind orderBy(List<String> orderByFieldNames) {
        if (!orderByFieldNames) return this
        if (!this.orderByFields) this.orderByFields = new ArrayList()
        for (String orderByFieldName in orderByFieldNames) orderBy(orderByFieldName)
        return this
    }

    @Override
    List<String> getOrderBy() { return this.orderByFields ? Collections.unmodifiableList(this.orderByFields) : null }

    @Override
    EntityFind useCache(Boolean useCache) { this.useCache = useCache; return this }

    @Override
    boolean getUseCache() { return this.useCache }

    // ======================== Advanced Options ==============================

    @Override
    EntityFind distinct(boolean distinct) { this.distinct = distinct; return this }
    @Override
    boolean getDistinct() { return this.distinct }

    @Override
    EntityFind offset(Integer offset) { this.offset = offset; return this }
    @Override
    EntityFind offset(int pageIndex, int pageSize) { offset(pageIndex * pageSize) }
    @Override
    Integer getOffset() { return this.offset }

    @Override
    EntityFind limit(Integer limit) { this.limit = limit; return this }
    @Override
    Integer getLimit() { return this.limit }

    @Override
    int getPageIndex() { return offset == null ? 0 : offset/getPageSize() }
    @Override
    int getPageSize() { return limit ?: 20 }

    @Override
    EntityFind forUpdate(boolean forUpdate) { this.forUpdate = forUpdate; return this }
    @Override
    boolean getForUpdate() { return this.forUpdate }

    // ======================== JDBC Options ==============================

    @Override
    EntityFind resultSetType(int resultSetType) { this.resultSetType = resultSetType; return this }
    @Override
    int getResultSetType() { return this.resultSetType }

    @Override
    EntityFind resultSetConcurrency(int resultSetConcurrency) {
        this.resultSetConcurrency = resultSetConcurrency
        return this
    }
    @Override
    int getResultSetConcurrency() { return this.resultSetConcurrency }

    @Override
    EntityFind fetchSize(Integer fetchSize) { this.fetchSize = fetchSize; return this }
    @Override
    Integer getFetchSize() { return this.fetchSize }

    @Override
    EntityFind maxRows(Integer maxRows) { this.maxRows = maxRows; return this }
    @Override
    Integer getMaxRows() { return this.maxRows }

    // ======================== Misc Methods ========================
    EntityDefinition getEntityDef() {
        if (entityDef != null) return entityDef
        if (dynamicView != null) {
            entityDef = dynamicView.makeEntityDefinition()
        } else {
            entityDef = efi.getEntityDefinition(entityName)
        }
        return entityDef
    }

    Map<String, Object> getSimpleMapPrimaryKeys() {
        Map<String, Object> pks = new HashMap()
        for (String fieldName in getEntityDef().getPkFieldNames()) {
            // only include PK fields which has a non-empty value, leave others out of the Map
            Object value = simpleAndMap.get(fieldName)
            // if any fields have no value we don't have a full PK so bye bye
            if (!value) return null
            if (value) pks.put(fieldName, value)
        }
        return pks
    }

    protected boolean shouldCache() {
        if (this.dynamicView) return false
        if (this.havingEntityCondition != null) return false
        if (this.limit != null || this.offset != null) return false
        if (this.useCache != null && !this.useCache) return false
        String entityCache = this.getEntityDef().getEntityNode()."@use-cache"
        return ((this.useCache == Boolean.TRUE && entityCache != "never") || entityCache == "true")
    }

    @Override
    String toString() {
        return "Find: ${entityName} WHERE [${simpleAndMap}] [${whereEntityCondition}] HAVING [${havingEntityCondition}] " +
                "SELECT [${fieldsToSelect}] ORDER BY [${orderByFields}] CACHE [${useCache}] DISTINCT [${distinct}] " +
                "OFFSET [${offset}] LIMIT [${limit}] FOR UPDATE [${forUpdate}]"
    }

    // ======================== Find and Abstract Methods ========================

    EntityFind disableAuthz() { disableAuthz = true; return this }

    abstract EntityDynamicView makeEntityDynamicView()

    @Override
    EntityValue one() throws EntityException {
        boolean enableAuthz = disableAuthz ? !efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz() : false
        try {
            return oneInternal()
        } finally {
            if (enableAuthz) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    protected EntityValue oneInternal() throws EntityException {
        if (this.dynamicView) throw new IllegalArgumentException("Dynamic View not supported for 'one' find.")

        long startTime = System.currentTimeMillis()
        EntityDefinition ed = this.getEntityDef()
        Node entityNode = ed.getEntityNode()
        ExecutionContext ec = efi.getEcfi().getExecutionContext()

        if (ed.isViewEntity() && (!entityNode."member-entity" || !entityNode."alias"))
            throw new EntityException("Cannot do find for view-entity with name [${entityName}] because it has no member entities or no aliased fields.")

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_VIEW").setActionDetail("one"),
                (entityNode."@authorize-skip" != "true" && !entityNode."@authorize-skip"?.contains("view")))

        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-one", true)

        // if over-constrained (anything in addition to a full PK), just use the full PK
        EntityConditionImplBase whereCondition
        if (ed.containsPrimaryKey(simpleAndMap)) {
            whereCondition = (EntityConditionImplBase) efi.getConditionFactory().makeCondition(ed.getPrimaryKeys(simpleAndMap))
        } else {
            whereCondition = (EntityConditionImplBase) getWhereEntityCondition()
        }

        // no condition means no condition/parameter set, so return null for find.one()
        if (!whereCondition) {
            // pop the ArtifactExecutionInfo
            ec.artifactExecution.pop()
            return null
        }

        // try the TX cache before the entity cache, may be more up-to-date
        EntityValue txcValue = txCache != null ? txCache.oneGet(this) : null

        boolean doCache = this.shouldCache()
        CacheImpl entityOneCache = doCache ? efi.getEntityCache().getCacheOne(getEntityDef().getFullEntityName()) : null
        EntityValueBase cacheHit = null
        if (doCache && txcValue == null) cacheHit = efi.getEntityCache().getFromOneCache(ed, whereCondition, entityOneCache)

        // we always want fieldsToSelect populated so that we know the order of the results coming back
        if (!this.fieldsToSelect || (txCache != null && txcValue == null) || (doCache && cacheHit == null))
            this.selectFields(ed.getFieldNames(true, true, false))

        // NOTE: do actual query condition as a separate condition because this will always be added on and isn't a
        //     part of the original where to use for the cache
        EntityConditionImplBase conditionForQuery
        EntityConditionImplBase viewWhere = ed.makeViewWhereCondition()
        if (viewWhere) {
            if (whereCondition) conditionForQuery = (EntityConditionImplBase) efi.getConditionFactory()
                    .makeCondition(whereCondition, EntityCondition.JoinOperator.AND, viewWhere)
            else conditionForQuery = viewWhere
        } else { conditionForQuery = whereCondition }


        // call the abstract method
        EntityValueBase newEntityValue = null
        if (txcValue != null) {
            if (txcValue instanceof TransactionCache.DeletedEntityValue) {
                // is deleted value, so leave newEntityValue as null
                // put in cache as null since this was deleted
                if (doCache) efi.getEntityCache().putInOneCache(ed, whereCondition, null, entityOneCache)
            } else {
                // if forUpdate unless this was a TX CREATE it'll be in the DB and should be locked, so do the query
                //     anyway, but ignore the result
                if (forUpdate && !txCache.isTxCreate(txcValue)) oneExtended(conditionForQuery)
                newEntityValue = txcValue
            }
        } else if (cacheHit != null) {
            if (cacheHit instanceof EntityCache.EmptyRecord) newEntityValue = null
            else newEntityValue = cacheHit
        } else {
            // for find one we'll always use the basic result set type and concurrency:
            this.resultSetType(ResultSet.TYPE_FORWARD_ONLY)
            this.resultSetConcurrency(ResultSet.CONCUR_READ_ONLY)

            // TODO: this will not handle query conditions on UserFields, it will blow up in fact

            newEntityValue = oneExtended(conditionForQuery)

            // it didn't come from the txCache so put it there
            if (txCache != null) txCache.onePut(newEntityValue)

            // put it in whether null or not (already know cacheHit is null)
            if (doCache) efi.getEntityCache().putInOneCache(ed, whereCondition, newEntityValue, entityOneCache)
        }

        if (logger.traceEnabled) logger.trace("Find one on entity [${ed.fullEntityName}] with condition [${whereCondition}] found value [${newEntityValue}]")

        // final ECA trigger
        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), newEntityValue, "find-one", false)
        // count the artifact hit
        efi.ecfi.countArtifactHit("entity", "one", ed.getFullEntityName(), simpleAndMap, startTime, System.currentTimeMillis(), newEntityValue ? 1 : 0)
        // pop the ArtifactExecutionInfo
        ec.getArtifactExecution().pop()

        return newEntityValue
    }

    abstract EntityValueBase oneExtended(EntityConditionImplBase whereCondition) throws EntityException

    @Override
    EntityList list() throws EntityException {
        boolean enableAuthz = disableAuthz ? !efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz() : false
        try {
            return listInternal()
        } finally {
            if (enableAuthz) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    protected EntityList listInternal() throws EntityException {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = this.getEntityDef()
        Node entityNode = ed.getEntityNode()
        ExecutionContext ec = efi.getEcfi().getExecutionContext()

        if (ed.isViewEntity() && (!entityNode."member-entity" || !entityNode."alias"))
            throw new EntityException("Cannot do find for view-entity with name [${entityName}] because it has no member entities or no aliased fields.")

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_VIEW").setActionDetail("list"),
                (entityNode."@authorize-skip" != "true" && !entityNode."@authorize-skip"?.contains("view")))

        // there may not be a simpleAndMap, but that's all we have that can be treated directly by the EECA
        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-list", true)

        List<String> orderByExpanded = new ArrayList()
        // add the manually specified ones, then the ones in the view entity's entity-condition
        if (this.getOrderBy()) orderByExpanded.addAll(this.getOrderBy())
        def ecObList = ed.getEntityNode()."entity-condition"?.first?."order-by"
        if (ecObList) for (Node orderBy in ecObList) orderByExpanded.add((String) orderBy."@field-name")

        EntityConditionImplBase whereCondition = (EntityConditionImplBase) getWhereEntityCondition()

        // try the txCache first, more recent than general cache (and for update general cache entries will be cleared anyway)
        EntityListImpl txcEli = txCache != null ? txCache.listGet(ed, whereCondition, orderByExpanded) : null

        // NOTE: don't cache if there is a having condition, for now just support where
        boolean doEntityCache = this.shouldCache()
        CacheImpl entityListCache = doEntityCache ? efi.getEntityCache().getCacheList(getEntityDef().getFullEntityName()) : null
        EntityList cacheList = null
        if (doEntityCache) cacheList = efi.getEntityCache().getFromListCache(ed, whereCondition, orderByExpanded, entityListCache)

        EntityListImpl el
        if (txcEli != null) {
            el = txcEli
            // if (ed.getFullEntityName().contains("OrderItem")) logger.warn("======== Got OrderItem from txCache ${el.size()} results where: ${whereCondition}")
        } else if (cacheList != null) {
            el = cacheList
        } else {
            // order by fields need to be selected (at least on some databases, Derby is one of them)
            if (this.fieldsToSelect && getDistinct() && orderByExpanded) {
                for (String orderByField in orderByExpanded) {
                    EntityFindBuilder.FieldOrderOptions foo = new EntityFindBuilder.FieldOrderOptions(orderByField)
                    fieldsToSelect.add(foo.fieldName)
                }
            }
            // we always want fieldsToSelect populated so that we know the order of the results coming back
            if (!this.fieldsToSelect || txCache != null || doEntityCache) this.selectFields(ed.getFieldNames(true, true, false))
            // TODO: this will not handle query conditions on UserFields, it will blow up in fact

            if (ed.isViewEntity() && ed.getEntityNode()."entity-condition"?.first?."@distinct" == "true") this.distinct(true)

            EntityConditionImplBase viewWhere = ed.makeViewWhereCondition()
            if (whereCondition && viewWhere) {
                whereCondition =
                    (EntityConditionImplBase) efi.getConditionFactory().makeCondition(whereCondition, EntityCondition.JoinOperator.AND, viewWhere)
            } else if (viewWhere) {
                whereCondition = viewWhere
            }
            EntityConditionImplBase havingCondition = (EntityConditionImplBase) getHavingEntityCondition()
            EntityConditionImplBase viewHaving = ed.makeViewHavingCondition()
            if (havingCondition && viewHaving) {
                havingCondition =
                    (EntityConditionImplBase) efi.getConditionFactory().makeCondition(havingCondition, EntityCondition.JoinOperator.AND, viewHaving)
            } else if (viewHaving) {
                havingCondition = viewHaving
            }

            // call the abstract method
            EntityListIterator eli = this.iteratorExtended(whereCondition, havingCondition, orderByExpanded)
            // these are used by the TransactionCache methods to augment the resulting list and maintain the sort order
            eli.setQueryCondition(whereCondition)
            eli.setOrderByFields(orderByExpanded)

            Node databaseNode = this.efi.getDatabaseNode(this.efi.getEntityGroupName(ed))
            if (this.limit != null && databaseNode != null && databaseNode."@offset-style" == "cursor") {
                el = eli.getPartialList(this.offset ?: 0, this.limit, true)
            } else {
                el = eli.getCompleteList(true)
            }

            if (txCache != null) txCache.listPut(ed, whereCondition, el)
            if (doEntityCache) efi.getEntityCache().putInListCache(ed, el, whereCondition, entityListCache)

            // if (ed.getFullEntityName().contains("OrderItem")) logger.warn("======== Got OrderItem from DATABASE ${el.size()} results where: ${whereCondition}")
            // logger.warn("======== Got ${ed.getFullEntityName()} from DATABASE ${el.size()} results where: ${whereCondition}")
        }

        // run the final rules
        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-list", false)
        // count the artifact hit
        efi.ecfi.countArtifactHit("entity", "list", ed.getFullEntityName(), simpleAndMap, startTime,
                System.currentTimeMillis(), el ? el.size() : 0)
        // pop the ArtifactExecutionInfo
        ec.getArtifactExecution().pop()

        return el
    }

    @Override
    EntityListIterator iterator() throws EntityException {
        boolean enableAuthz = disableAuthz ? !efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz() : false
        try {
            return iteratorInternal()
        } finally {
            if (enableAuthz) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    protected EntityListIterator iteratorInternal() throws EntityException {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = this.getEntityDef()
        Node entityNode = ed.getEntityNode()
        ExecutionContext ec = efi.getEcfi().getExecutionContext()

        if (ed.isViewEntity() && (!entityNode."member-entity" || !entityNode."alias"))
            throw new EntityException("Cannot do find for view-entity with name [${entityName}] because it has no member entities or no aliased fields.")

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_VIEW").setActionDetail("iterator"),
                (entityNode."@authorize-skip" != "true" && !entityNode."@authorize-skip"?.contains("view")))

        // there may not be a simpleAndMap, but that's all we have that can be treated directly by the EECA
        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-iterator", true)

        List<String> orderByExpanded = new ArrayList()
        // add the manually specified ones, then the ones in the view entity's entity-condition
        if (this.getOrderBy()) orderByExpanded.addAll(this.getOrderBy())
        def ecObList = ed.getEntityNode()."entity-condition"?.first?."order-by"
        if (ecObList) for (Node orderBy in ecObList) orderByExpanded.add((String) orderBy."@field-name")

        // order by fields need to be selected (at least on some databases, Derby is one of them)
        if (this.fieldsToSelect && getDistinct() && orderByExpanded) {
            for (String orderByField in orderByExpanded) {
                EntityFindBuilder.FieldOrderOptions foo = new EntityFindBuilder.FieldOrderOptions(orderByField)
                fieldsToSelect.add(foo.fieldName)
            }
        }
        // we always want fieldsToSelect populated so that we know the order of the results coming back
        if (!this.fieldsToSelect) this.selectFields(ed.getFieldNames(true, true, false))
        // TODO: this will not handle query conditions on UserFields, it will blow up in fact

        if (ed.isViewEntity() && ed.getEntityNode()."entity-condition"?.first?."@distinct" == "true") this.distinct(true)

        EntityConditionImplBase whereCondition = (EntityConditionImplBase) getWhereEntityCondition()
        EntityConditionImplBase viewWhere = ed.makeViewWhereCondition()
        if (whereCondition && viewWhere) {
            whereCondition = (EntityConditionImplBase) efi.getConditionFactory().makeCondition(whereCondition,
                    EntityCondition.JoinOperator.AND, viewWhere)
        } else if (viewWhere) {
            whereCondition = viewWhere
        }
        EntityConditionImplBase havingCondition = (EntityConditionImplBase) getHavingEntityCondition()
        EntityConditionImplBase viewHaving = ed.makeViewHavingCondition()
        if (havingCondition && viewHaving) {
            havingCondition = (EntityConditionImplBase) efi.getConditionFactory().makeCondition(havingCondition,
                        EntityCondition.JoinOperator.AND, viewHaving)
        } else if (viewHaving) {
            havingCondition = viewHaving
        }

        // call the abstract method
        EntityListIterator eli = iteratorExtended(whereCondition, havingCondition, orderByExpanded)
        eli.setQueryCondition(whereCondition)
        eli.setOrderByFields(orderByExpanded)

        // NOTE: if we are doing offset/limit with a cursor no good way to limit results, but we'll at least jump to the offset
        Node databaseNode = this.efi.getDatabaseNode(this.efi.getEntityGroupName(ed))
        // NOTE: allow databaseNode to be null because custom (non-JDBC) datasources may not have one
        if (this.offset != null && databaseNode != null && databaseNode."@offset-style" == "cursor") {
            if (!eli.absolute(offset)) {
                // can't seek to desired offset? not enough results, just go to after last result
                eli.afterLast()
            }
        }

        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-iterator", false)
        // count the artifact hit
        efi.ecfi.countArtifactHit("entity", "iterator", ed.getFullEntityName(), simpleAndMap, startTime, System.currentTimeMillis(), null)
        // pop the ArtifactExecutionInfo
        ec.getArtifactExecution().pop()

        return eli
    }

    abstract EntityListIterator iteratorExtended(EntityConditionImplBase whereCondition,
            EntityConditionImplBase havingCondition, List<String> orderByExpanded)

    @Override
    long count() throws EntityException {
        boolean enableAuthz = disableAuthz ? !efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz() : false
        try {
            return countInternal()
        } finally {
            if (enableAuthz) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    protected long countInternal() throws EntityException {
        long startTime = System.currentTimeMillis()
        EntityDefinition ed = this.getEntityDef()
        Node entityNode = ed.getEntityNode()
        ExecutionContext ec = efi.getEcfi().getExecutionContext()

        ec.getArtifactExecution().push(
                new ArtifactExecutionInfoImpl(ed.getFullEntityName(), "AT_ENTITY", "AUTHZA_VIEW").setActionDetail("count"),
                (entityNode."@authorize-skip" != "true" && !entityNode."@authorize-skip"?.contains("view")))

        // there may not be a simpleAndMap, but that's all we have that can be treated directly by the EECA
        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-count", true)

        EntityConditionImplBase whereCondition = (EntityConditionImplBase) getWhereEntityCondition()
        // NOTE: don't cache if there is a having condition, for now just support where
        boolean doCache = !this.havingEntityCondition && this.shouldCache()
        CacheImpl entityCountCache = doCache ? efi.getEntityCache().getCacheCount(getEntityDef().getFullEntityName()) : null
        Long cacheCount = null
        if (doCache) cacheCount = efi.getEntityCache().getFromCountCache(ed, whereCondition, entityCountCache)

        long count
        if (cacheCount != null) {
            count = cacheCount
        } else {
            // select all pk and nonpk fields to match what list() or iterator() would do
            if (!this.fieldsToSelect) this.selectFields(ed.getFieldNames(true, true, false))
            // TODO: this will not handle query conditions on UserFields, it will blow up in fact

            if (ed.isViewEntity() && ed.getEntityNode()."entity-condition"?.first?."@distinct" == "true") this.distinct(true)

            EntityConditionImplBase viewWhere = ed.makeViewWhereCondition()
            if (whereCondition && viewWhere) {
                whereCondition =
                    (EntityConditionImplBase) efi.getConditionFactory().makeCondition(whereCondition, EntityCondition.JoinOperator.AND, viewWhere)
            } else if (viewWhere) {
                whereCondition = viewWhere
            }

            EntityConditionImplBase havingCondition = (EntityConditionImplBase) getHavingEntityCondition()
            EntityConditionImplBase viewHaving = ed.makeViewHavingCondition()
            if (havingCondition && viewHaving) {
                havingCondition =
                    (EntityConditionImplBase) efi.getConditionFactory().makeCondition(havingCondition, EntityCondition.JoinOperator.AND, viewHaving)
            } else if (viewHaving) {
                havingCondition = viewHaving
            }

            // call the abstract method
            count = countExtended(whereCondition, havingCondition)

            if (doCache) entityCountCache.put(whereCondition, count)
        }

        // find EECA rules deprecated, not worth performance hit: efi.runEecaRules(ed.getFullEntityName(), simpleAndMap, "find-count", false)
        // count the artifact hit
        efi.ecfi.countArtifactHit("entity", "count", ed.getFullEntityName(), simpleAndMap, startTime, System.currentTimeMillis(), count)
        // pop the ArtifactExecutionInfo
        ec.getArtifactExecution().pop()

        return count
    }
    abstract long countExtended(EntityConditionImplBase whereCondition, EntityConditionImplBase havingCondition)
            throws EntityException

    @Override
    long updateAll(Map<String, ?> fieldsToSet) {
        boolean enableAuthz = disableAuthz ? !efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz() : false
        try {
            return updateAllInternal(fieldsToSet)
        } finally {
            if (enableAuthz) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    protected long updateAllInternal(Map<String, ?> fieldsToSet) {
        // NOTE: this code isn't very efficient, but will do the trick and cause all EECAs to be fired
        // NOTE: consider expanding this to do a bulk update in the DB if there are no EECAs for the entity

        if (getEntityDef().createOnly()) throw new EntityException("Entity [${getEntityDef().getFullEntityName()}] is create-only (immutable), cannot be updated.")

        this.useCache(false)
        long totalUpdated = 0
        EntityListIterator eli = null
        try {
            eli = iterator()
            EntityValue value
            while ((value = eli.next()) != null) {
                value.putAll(fieldsToSet)
                if (value.isModified()) {
                    // NOTE: consider implement and use the eli.set(value) method to update within a ResultSet
                    value.update()
                    totalUpdated++
                }
            }
        } finally {
            if (eli != null) eli.close()
        }
        return totalUpdated
    }

    @Override
    long deleteAll() {
        boolean enableAuthz = disableAuthz ? !efi.getEcfi().getExecutionContext().getArtifactExecution().disableAuthz() : false
        try {
            return deleteAllInternal()
        } finally {
            if (enableAuthz) efi.getEcfi().getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    protected long deleteAllInternal() {
        // NOTE: this code isn't very efficient (though eli.remove() is a little bit more), but will do the trick and cause all EECAs to be fired

        if (getEntityDef().createOnly()) throw new EntityException("Entity [${getEntityDef().getFullEntityName()}] is create-only (immutable), cannot be deleted.")

        // if there are no EECAs for the entity OR there is a TransactionCache in place just call ev.delete() on each
        boolean useEvDelete = txCache != null || efi.hasEecaRules(this.getEntityDef().getFullEntityName())
        if (!useEvDelete) this.resultSetConcurrency(ResultSet.CONCUR_UPDATABLE)
        this.useCache(false)
        EntityListIterator eli = null
        long totalDeleted = 0
        try {
            eli = iterator()
            EntityValue ev
            while ((ev = eli.next()) != null) {
                if (useEvDelete) {
                    ev.delete()
                } else {
                    // not longer need to clear cache, eli.remote() does that
                    eli.remove()
                }
                totalDeleted++
            }
        } finally {
            if (eli != null) eli.close()
        }
        return totalDeleted
    }
}
