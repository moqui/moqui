package org.moqui.impl.entity

public class EntityDefinition {
    protected EntityFacadeImpl efi
    protected String entityName
    protected Node entityNode

    EntityDefinition(EntityFacadeImpl efi, Node entityNode) {
        this.efi = efi
        this.entityName = entityNode."@entity-name"
        this.entityNode = entityNode

        // TODO if this is a view-entity, expand the alias-all elements into alias elements here
    }

    // ============= Actually Used Methods ==============
    String getEntityName() {
        return this.entityName
    }

    Node getEntityNode() {
        return this.entityNode
    }

    boolean isViewEntity() {
        return this.entityNode.name() == "view-entity"
    }

    String getColumnName(String fieldName, boolean includeFunctionAndComplex) {
        if (isViewEntity()) {
            // NOTE: for view-entity the incoming fieldNode will actually be for an alias element
            Node aliasNode = this.entityNode.alias.find({ it.@name == fieldName })
            if (!aliasNode) {
                throw new IllegalArgumentException("Invalid field-name [${fieldName}] for the [${this.getEntityName()}] view-entity")
            }

            // TODO: column name for view-entity (prefix with "${entity-alias}.")
            if (includeFunctionAndComplex) {
                // TODO: column name view-entity complex-alias (build expression based on complex-alias)
                // TODO: column name for view-entity alias with function (wrap in function, after complex-alias to wrap that too when used)
            }
            return null
        } else {
            Node fieldNode = this.entityNode.field.find({ it.@name == fieldName })
            if (!fieldNode) {
                throw new IllegalArgumentException("Invalid field-name [${fieldName}] for the [${this.getEntityName()}] entity")
            }

            if (fieldNode."@column-name") {
                return fieldNode."@column-name"
            } else {
                return camelCaseToUnderscored(fieldNode."@name")
            }
        }
    }

    /** Returns the table name, ie table-name or converted entity-name */
    String getTableName() {
        if (this.entityNode."@table-name") {
            return this.entityNode."@table-name"
        } else {
            return camelCaseToUnderscored(this.entityNode."@entity-name")
        }
    }

    // ============= Possibly Useful Methods - 2nd Priority ==============

    boolean isField(String fieldName) {
        // TODO: implement this
        return false
    }

    List<String> getFieldNames(boolean includePk, boolean includeNonPk) {
        // TODO: implement this
        return null
    }

    static String camelCaseToUnderscored(String camelCase) {
        if (!camelCase) return ""
        StringBuilder underscored = new StringBuilder()

        underscored.append(Character.toUpperCase(camelCase.charAt(0)))
        int inPos = 1
        while (inPos < camelCase.length()) {
            char curChar = camelCase.charAt(inPos)
            if (Character.isUpperCase(curChar)) underscored.append('_')
            underscored.append(Character.toUpperCase(curChar))
            inPos++
        }

        return underscored.toString()
    }
}
