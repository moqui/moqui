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

class EntityFindBuilder {
    protected EntityFindImpl efi

    protected StringBuilder sql
    protected List<EntityConditionParam> params

    EntityFindBuilder(EntityFindImpl efi) {
        this.efi = efi
    }

    /** returns StringBuilder meant to be appended to */
    StringBuilder getSql() {
        return this.sql
    }

    /** returns List of EntityConditionParam meant to be added to */
    List<EntityConditionParam> getParams() {
        return this.params
    }

    static class EntityConditionParam {
        EntityConditionParam() {
    
        }
    }
}
