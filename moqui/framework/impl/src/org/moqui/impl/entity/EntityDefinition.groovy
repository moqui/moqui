package org.moqui.impl.entity

public class EntityDefinition {
    protected EntityFacadeImpl efi
    protected String entityName
    protected Node entityNode

    EntityDefinition(EntityFacadeImpl efi, Node entityNode) {
        this.efi = efi
        this.entityName = entityNode."@entity-name"
        this.entityNode = entityNode
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

    String getColName(String fieldName) {
        // TODO: implement this
        return null
    }

    String getTableName() {
        // TODO: implement this
        return null
    }

    // ============= Possibly Useful Methods - 2nd Priority ==============

    /** Returns the full table name including the prefix from the datasource config and the table-name or converted entity-name */
    String getFullTableName() {
        // TODO: implement this
        return null
    }

    boolean isField(String fieldName) {
        // TODO: implement this
        return false
    }

    List<String> getFieldNames(boolean includePk, boolean includeNonPk) {
        // TODO: implement this
        return null
    }

    List<EntityFieldDefinition> getFields() {
        // TODO: implement this
        return null
    }

    EntityFieldDefinition getField(String fieldName) {
        // TODO: implement this
        return null
    }

    static class EntityFieldDefinition {

        boolean isAutoCreatedInternal() {
            // TODO: implement this
            return false
        }
    }

    static class EntityKeyMapDefinition {
        
    }

    static class EntityRelationshipDefinition {

        /** Returns the full relationship name which is: ${title}${related-entity-name} */
        String getRelationshipName() {
            // TODO: implement this
            return null
        }
    }
}
