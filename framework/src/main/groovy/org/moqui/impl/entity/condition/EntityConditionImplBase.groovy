/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
