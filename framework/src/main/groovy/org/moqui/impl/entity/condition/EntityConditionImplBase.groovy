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

import org.moqui.impl.entity.EntityConditionFactoryImpl
import org.moqui.entity.EntityCondition
import org.moqui.impl.entity.EntityQueryBuilder

abstract class EntityConditionImplBase implements EntityCondition {
    EntityConditionFactoryImpl ecFactoryImpl

    EntityConditionImplBase(EntityConditionFactoryImpl ecFactoryImpl) {
        this.ecFactoryImpl = ecFactoryImpl
    }

    /** Build SQL Where text to evaluate condition in a database. */
    public abstract void makeSqlWhere(EntityQueryBuilder eqb)

    public abstract void getAllAliases(Set<String> entityAliasSet, Set<String> fieldAliasSet)
}
