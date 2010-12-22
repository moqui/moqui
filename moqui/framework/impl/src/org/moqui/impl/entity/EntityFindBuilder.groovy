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
    protected EntityFindImpl entityFindImpl

    protected StringBuilder sqlTopLevel
    protected List<EntityConditionParameter> parameters

    EntityFindBuilder(EntityFindImpl entityFindImpl) {
        this.entityFindImpl = entityFindImpl

        // this is always going to start with "SELECT ", so just set it here
        this.sqlTopLevel = new StringBuilder("SELECT ")
        this.parameters = new ArrayList()
    }

    /** returns StringBuilder meant to be appended to */
    StringBuilder getSqlTopLevel() {
        return this.sqlTopLevel
    }

    /** returns List of EntityConditionParameter meant to be added to */
    List<EntityConditionParameter> getParameters() {
        return this.parameters
    }

    void makeSqlSelectFields(Set<String> fieldsToSelect, EntityDefinition entityDefinition) {
        if (fieldsToSelect.size() > 0) {
            boolean isFirst = true
            for (String fieldName in fieldsToSelect) {
                if (isFirst) isFirst = false else this.sqlTopLevel.append(", ")
                this.sqlTopLevel.append(entityDefinition.getColumnName(fieldName, true))
            }
        } else {
            this.sqlTopLevel.append("*")
        }
    }

    void makeSqlFromClause(EntityDefinition entityDefinition, StringBuilder localBuilder) {
        if (!localBuilder) localBuilder = this.sqlTopLevel
        localBuilder.append(" FROM ")

        Node entityNode = entityDefinition.entityNode

        if (entityDefinition.isViewEntity()) {
            Node databaseNode = this.entityFindImpl.efi.getDatabaseNode(entityDefinition.entityName)
            String joinStyle = databaseNode."@join-style"

            if ("ansi" != joinStyle && "ansi-no-parenthesis" != joinStyle) {
                throw new IllegalArgumentException("The join-style " + joinStyle + " is not supported")
            }

            boolean useParenthesis = ("ansi" == joinStyle)

            // keep a set of all aliases in the join so far and if the left entity alias isn't there yet, and this
            // isn't the first one, throw an exception
            Set<String> joinedAliasSet = new TreeSet<String>()

            // on initial pass only add opening parenthesis since easier than going back and inserting them, then insert the rest
            StringBuilder restOfStatement = new StringBuilder()
            boolean isFirst = true
            for (Node viewLink in entityNode."view-link") {
                if (useParenthesis) localBuilder.append('(')

                String linkEntityName = entityNode."member-entity".find({ it."@entity-alias" == viewLink."@entity-alias" })."@entity-name"
                EntityDefinition linkEntityDefinition = this.entityFindImpl.efi.getEntityDefinition(linkEntityName)
                String relatedLinkEntityName = entityNode."member-entity".find({ it."@entity-alias" == viewLink."@related-entity-alias" })."@entity-name"
                EntityDefinition relatedLinkEntityDefinition = this.entityFindImpl.efi.getEntityDefinition(relatedLinkEntityName)

                if (isFirst) {
                    // first link, add link entity for this one only, for others add related link entity
                    makeSqlViewTableName(linkEntityDefinition, restOfStatement)
                    restOfStatement.append(" ")
                    restOfStatement.append(viewLink."@entity-alias")

                    joinedAliasSet.add(viewLink."@entity-alias")
                } else {
                    // make sure the left entity alias is already in the join...
                    if (!joinedAliasSet.contains(viewLink."@entity-alias")) {
                        throw new IllegalArgumentException("Tried to link the " + viewLink."@entity-alias" +
                                " alias to the " + viewLink."@related-entity-alias" + " alias of the " +
                                entityDefinition.getEntityName() + " view-entity, but it is not the first view-link and has not been included in a previous view-link. In other words, the left/main alias isn't connected to the rest of the member-entities yet.")
                    }
                }
                // now put the rel (right) entity alias into the set that is in the join
                joinedAliasSet.add(viewLink."@related-entity-alias")

                if (viewLink."@related-optional" == "true") {
                    restOfStatement.append(" LEFT OUTER JOIN ")
                } else {
                    restOfStatement.append(" INNER JOIN ")
                }

                makeSqlViewTableName(relatedLinkEntityDefinition, restOfStatement)
                restOfStatement.append(" ")
                restOfStatement.append(viewLink."@related-entity-alias")
                restOfStatement.append(" ON ")

                if (!viewLink."key-map") {
                    throw new IllegalArgumentException("No view-link/join key-maps found for the " +
                            viewLink."@entity-alias" + " and the " + viewLink."@related-entity-alias" + 
                            " member-entities of the " + entityDefinition.getEntityName() + " view-entity.")
                }

                boolean isFirstKeyMap = true
                for (Node keyMap in viewLink."key-map") {
                    if (isFirstKeyMap) isFirstKeyMap = false else restOfStatement.append(" AND ")

                    restOfStatement.append(viewLink."@entity-alias")
                    restOfStatement.append(".")
                    restOfStatement.append(sanitizeColumnName(linkEntityDefinition.getColumnName(keyMap."@field-name", false)))

                    restOfStatement.append(" = ")

                    restOfStatement.append(viewLink."@related-entity-alias")
                    restOfStatement.append(".")
                    restOfStatement.append(sanitizeColumnName(relatedLinkEntityDefinition.getColumnName(keyMap."@related-field-name", false)))
                }

                if (viewLink."entity-condition") {
                    // TODO: add any additional manual conditions for the view-link here
                }

                if (useParenthesis) restOfStatement.append(')')
                isFirst = false
            }

            localBuilder.append(restOfStatement.toString())

            // handle member-entities not referenced in any view-link element
            boolean fromEmpty = restOfStatement.length() == 0
            for (Node memberEntity in entityNode."member-entity") {
                EntityDefinition fromEntityDefinition = this.entityFindImpl.efi.getEntityDefinition(memberEntity."@entity-name")
                if (!joinedAliasSet.contains(memberEntity."@entity-alias")) {
                    if (fromEmpty) fromEmpty = false else localBuilder.append(", ")
                    makeSqlViewTableName(fromEntityDefinition, localBuilder)
                    localBuilder.append(" ")
                    localBuilder.append(memberEntity."@entity-alias")
                }
            }
        } else {
            localBuilder.append(entityDefinition.getTableName())
        }
    }

    void makeSqlViewTableName(EntityDefinition entityDefinition, StringBuilder localBuilder) {
        if (entityDefinition.isViewEntity()) {
            localBuilder.append("(SELECT ")

            boolean isFirst = true
            for (Node aliasNode in entityDefinition.entityNode.alias) {
                if (isFirst) isFirst = false else localBuilder.append(", ")
                localBuilder.append(entityDefinition.getColumnName(aliasNode, true))
                // TODO: are the next two lines really needed? have removed AS stuff elsewhere since it is not commonly used and not needed
                localBuilder.append(" AS ")
                localBuilder.append(sanitizeColumnName(entityDefinition.getColumnName(aliasNode, false)))
            }

            makeSqlFromClause(entityDefinition, localBuilder)

            def groupByAliases = entityDefinition.entityNode.alias.find({ it."@group-by" == "true" })
            if (groupByAliases) {
                localBuilder.append(" GROUP BY ")
                boolean isFirstGroupBy = true
                for (Node groupByAlias in groupByAliases) {
                    if (isFirstGroupBy) isFirstGroupBy = false else localBuilder.append(", ")
                    localBuilder.append(getColumnName(groupByAlias, entityDefinition, true));
                }
            }

            localBuilder.append(")");
        } else {
            localBuilder.append(entityDefinition.getTableName())
        }
    }

    void startWhereClause() {
        this.sql.append(" WHERE ")
    }

    String sanitizeColumnName(String colName) {
        return colName.replace('.', '_').replace('(','_').replace(')','_');
    }

    static class EntityConditionParameter {
        protected Node fieldNode
        protected Object value

        EntityConditionParameter(Node fieldNode, Object value) {
            this.fieldNode = fieldNode
            this.value = value
        }

        Node getFieldNode() {
            return this.fieldNode
        }

        Object getValue() {
            return this.value
        }
    }
}
