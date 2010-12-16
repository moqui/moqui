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

class EntityFindImpl implements EntityFind {

    protected final EntityFacadeImpl efi;

    protected String entityName;
    protected EntityDynamicView dynamicView = null;

    public EntityFindImpl(EntityFacadeImpl efi, String entityName) {
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
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(Map<String,?>) */
    EntityFind condition(Map<String, ?> fields) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#condition(EntityCondition) */
    EntityFind condition(EntityCondition condition) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#havingCondition(EntityCondition) */
    EntityFind havingCondition(EntityCondition condition) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getWhereEntityCondition() */
    EntityCondition getWhereEntityCondition() {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getHavingEntityCondition() */
    EntityCondition getHavingEntityCondition() {
        // TODO: implement this
        return this
    }

    // ======================== General/Common Options ========================

    /** @see org.moqui.entity.EntityFind#selectField(String) */
    EntityFind selectField(String fieldToSelect) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#selectFields(Collection<String>) */
    EntityFind selectFields(Collection<String> fieldsToSelect) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getSelectFields() */
    Set<String> getSelectFields() {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#orderBy(String) */
    EntityFind orderBy(String orderByFieldName) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#orderBy(List<String>) */
    EntityFind orderBy(List<String> orderByFieldNames) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getOrderBy() */
    List<String> getOrderBy() {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#useCache(boolean) */
    EntityFind useCache(boolean useCache) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getUseCache() */
    boolean getUseCache() {
        // TODO: implement this
        return false
    }

    /** @see org.moqui.entity.EntityFind#forUpdate(boolean) */
    EntityFind forUpdate(boolean forUpdate) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getForUpdate() */
    boolean getForUpdate() {
        // TODO: implement this
        return false
    }

    // ======================== Advanced Options ==============================

    /** @see org.moqui.entity.EntityFind#resultSetType(int) */
    EntityFind resultSetType(Integer resultSetType) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getResultSetType() */
    int getResultSetType() {
        // TODO: implement this
        return 0
    }

    /** @see org.moqui.entity.EntityFind#resultSetConcurrency(int) */
    EntityFind resultSetConcurrency(int resultSetConcurrency) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getResultSetConcurrency() */
    int getResultSetConcurrency() {
        // TODO: implement this
        return 0
    }

    /** @see org.moqui.entity.EntityFind#fetchSize(int) */
    EntityFind fetchSize(Integer fetchSize) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getFetchSize() */
    int getFetchSize() {
        // TODO: implement this
        return 0
    }

    /** @see org.moqui.entity.EntityFind#maxRows(int) */
    EntityFind maxRows(Integer maxRows) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getMaxRows() */
    int getMaxRows() {
        // TODO: implement this
        return 0
    }

    /** @see org.moqui.entity.EntityFind#distinct(boolean) */
    EntityFind distinct(boolean distinct) {
        // TODO: implement this
        return this
    }

    /** @see org.moqui.entity.EntityFind#getDistinct() */
    boolean getDistinct() {
        // TODO: implement this
        return false
    }

    // ======================== Run Find Methods ==============================

    /** @see org.moqui.entity.EntityFind#one() */
    EntityValue one() {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#list() */
    EntityList list() {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#iterator() */
    EntityListIterator iterator() {
        // TODO: implement this
        return null
    }

    /** @see org.moqui.entity.EntityFind#count() */
    long count() {
        // TODO: implement this
        return 0;
    }
}
