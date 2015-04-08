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

import groovy.transform.CompileStatic
import org.moqui.BaseException
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.StupidJavaUtilities

@CompileStatic
class ConditionField {
    String entityAlias = null
    String fieldName
    EntityDefinition aliasEntityDef = null
    String aliasEntityName = null
    protected int curHashCode

    ConditionField(String fieldName) {
        if (!fieldName) throw new BaseException("Empty fieldName not allowed")
        this.fieldName = fieldName.intern()
        curHashCode = createHashCode()
    }
    ConditionField(String entityAlias, String fieldName, EntityDefinition aliasEntityDef) {
        if (!fieldName) throw new BaseException("Empty fieldName not allowed")
        this.entityAlias = entityAlias.intern()
        this.fieldName = fieldName.intern()
        this.aliasEntityDef = aliasEntityDef
        // NOTE: this is already intern()'ed
        if (aliasEntityDef != null) aliasEntityName = aliasEntityDef.getFullEntityName()
        curHashCode = createHashCode()
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

    EntityDefinition.FieldInfo getFieldInfo(EntityDefinition ed) {
        if (this.aliasEntityDef) {
            return this.aliasEntityDef.getFieldInfo(fieldName)
        } else {
            return ed.getFieldInfo(fieldName)
        }
    }

    @Override
    String toString() { return (entityAlias ? entityAlias+"." : "") + fieldName }

    @Override
    int hashCode() { return curHashCode }
    protected int createHashCode() {
        return (entityAlias ? entityAlias.hashCode() : 0) + (fieldName ? fieldName.hashCode() : 0) +
                (aliasEntityDef ? aliasEntityDef.hashCode() : 0)
    }

    @Override
    boolean equals(Object o) {
        if (o == null || o.getClass() != this.getClass()) return false
        ConditionField that = (ConditionField) o
        return equalsConditionField(that)
    }
    boolean equalsConditionField(ConditionField that) {
        if (that == null) return false
        if (!StupidJavaUtilities.internedNonNullStringsEqual(this.fieldName, that.fieldName)) return false
        if (!StupidJavaUtilities.internedStringsEqual(this.entityAlias, that.entityAlias)) return false
        if (!StupidJavaUtilities.internedStringsEqual(this.aliasEntityName, that.aliasEntityName)) return false
        return true
    }
}
