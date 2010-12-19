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

    protected StringBuilder sql
    protected List<EntityConditionParameter> parameters

    EntityFindBuilder(EntityFindImpl entityFindImpl) {
        this.entityFindImpl = entityFindImpl

        // this is always going to start with "SELECT ", so just set it here
        this.sql = new StringBuilder("SELECT ")
        this.parameters = new ArrayList()
    }

    /** returns StringBuilder meant to be appended to */
    StringBuilder getSql() {
        return this.sql
    }

    /** returns List of EntityConditionParameter meant to be added to */
    List<EntityConditionParameter> getParameters() {
        return this.parameters
    }

    void makeSqlSelectFields(Set<String> fieldsToSelect, EntityDefinition entityDefinition) {
        if (fieldsToSelect.size() > 0) {
            Iterator fieldIter = fieldsToSelect.iterator()
            while (fieldIter.hasNext()) {
                String fieldName = fieldIter.next()
                this.sql.append(entityDefinition.getColName(fieldName))
                if (fieldIter.hasNext()) this.sql.append(", ")
            }
        } else {
            this.sql.append("*")
        }
    }

    void makeSqlFromClause(EntityDefinition entityDefinition) {
        this.sql.append(" FROM ")

        if (entityDefinition.isViewEntity()) {
            def defaultDatabaseNode = this.entityFindImpl.efi.getDefaultDatabaseNode(entityDefinition.entityName)
            def databaseNode = this.entityFindImpl.efi.getDatabaseNode(entityDefinition.entityName)
            String joinStyle = (databaseNode && databaseNode."@join-style") ? databaseNode."@join-style" : joinStyle = defaultDatabaseNode."@join-style"

            if ("ansi".equals(joinStyle) || "ansi-no-parenthesis".equals(joinStyle)) {
                boolean useParenthesis = true
                if ("ansi-no-parenthesis".equals(joinStyle)) {
                    useParenthesis = false
                }

                // keep a set of all aliases in the join so far and if the left entity alias isn't there yet, and this
                // isn't the first one, throw an exception
                Set<String> joinedAliasSet = new TreeSet<String>();

                StringBuilder openParens = null;
                if (useParenthesis) openParens = new StringBuilder();
                StringBuilder restOfStatement = new StringBuilder();

                // TODO:
                def viewLink = null;
                for (int i = 0; i < modelViewEntity.getViewLinksSize(); i++) {
                    // don't put starting parenthesis
                    if (i > 0 && useParenthesis) openParens.append('(');

                    def viewLinkNode = viewLink[i];

                    ModelEntity linkEntity = modelViewEntity.getMemberModelEntity(viewLink.getEntityAlias());
                    ModelEntity relLinkEntity = modelViewEntity.getMemberModelEntity(viewLink.getRelEntityAlias());

                    if (i == 0) {
                        // this is the first referenced member alias, so keep track of it for future use...
                        restOfStatement.append(makeViewTable(linkEntity, datasourceInfo));
                        restOfStatement.append(" ");
                        restOfStatement.append(viewLink.getEntityAlias());

                        joinedAliasSet.add(viewLink.getEntityAlias());
                    } else {
                        // make sure the left entity alias is already in the join...
                        if (!joinedAliasSet.contains(viewLink.getEntityAlias())) {
                            throw new IllegalArgumentException("Tried to link the " + viewLink.getEntityAlias() + " alias to the " + viewLink.getRelEntityAlias() + " alias of the " + modelViewEntity.getEntityName() + " view-entity, but it is not the first view-link and has not been included in a previous view-link. In other words, the left/main alias isn't connected to the rest of the member-entities yet.");
                        }
                    }
                    // now put the rel (right) entity alias into the set that is in the join
                    joinedAliasSet.add(viewLink.getRelEntityAlias());

                    if (viewLink.isRelOptional()) {
                        restOfStatement.append(" LEFT OUTER JOIN ");
                    } else {
                        restOfStatement.append(" INNER JOIN ");
                    }

                    restOfStatement.append(makeViewTable(relLinkEntity, datasourceInfo));
                    //another possible one that some dbs might need, but not sure of any yet: restOfStatement.append(" AS ");
                    restOfStatement.append(" ");
                    restOfStatement.append(viewLink.getRelEntityAlias());
                    restOfStatement.append(" ON ");

                    StringBuilder condBuffer = new StringBuilder();

                    for (int j = 0; j < viewLink.getKeyMapsSize(); j++) {
                        ModelKeyMap keyMap = viewLink.getKeyMap(j);
                        ModelField linkField = linkEntity.getField(keyMap.getFieldName());
                        if (linkField == null) {
                            throw new IllegalArgumentException("Invalid field name in view-link key-map for the " + viewLink.getEntityAlias() + " and the " + viewLink.getRelEntityAlias() + " member-entities of the " + modelViewEntity.getEntityName() + " view-entity; the field [" + keyMap.getFieldName() + "] does not exist on the [" + linkEntity.getEntityName() + "] entity.");
                        }
                        ModelField relLinkField = relLinkEntity.getField(keyMap.getRelFieldName());
                        if (relLinkField == null) {
                            throw new IllegalArgumentException("Invalid related field name in view-link key-map for the " + viewLink.getEntityAlias() + " and the " + viewLink.getRelEntityAlias() + " member-entities of the " + modelViewEntity.getEntityName() + " view-entity; the field [" + keyMap.getRelFieldName() + "] does not exist on the [" + relLinkEntity.getEntityName() + "] entity.");
                        }

                        if (condBuffer.length() > 0) {
                            condBuffer.append(" AND ");
                        }

                        condBuffer.append(viewLink.getEntityAlias());
                        condBuffer.append(".");
                        condBuffer.append(filterColName(linkField.getColName()));

                        condBuffer.append(" = ");

                        condBuffer.append(viewLink.getRelEntityAlias());
                        condBuffer.append(".");
                        condBuffer.append(filterColName(relLinkField.getColName()));
                    }
                    if (condBuffer.length() == 0) {
                        throw new IllegalArgumentException("No view-link/join key-maps found for the " + viewLink.getEntityAlias() + " and the " + viewLink.getRelEntityAlias() + " member-entities of the " + modelViewEntity.getEntityName() + " view-entity.");
                    }

                    restOfStatement.append(condBuffer.toString());

                    // don't put ending parenthesis
                    if (i < (modelViewEntity.getViewLinksSize() - 1) && useParenthesis) restOfStatement.append(')');
                }

                if (useParenthesis) sql.append(openParens.toString());
                sql.append(restOfStatement.toString());

                // handle tables not included in view-link
                boolean fromEmpty = restOfStatement.length() == 0;
                for (String aliasName: modelViewEntity.getMemberModelMemberEntities().keySet()) {
                    EntityDefinition fromEntityDefinition = modelViewEntity.getMemberModelEntity(aliasName);

                    if (!joinedAliasSet.contains(aliasName)) {
                        if (!fromEmpty) sql.append(", ");
                        fromEmpty = false;

                        sql.append(makeViewTable(fromEntity, datasourceInfo));
                        sql.append(" ");
                        sql.append(aliasName);
                    }
                }


            } else if ("theta-oracle".equals(joinStyle) || "theta-mssql".equals(joinStyle)) {
                Iterator<String> meIter = modelViewEntity.getMemberModelMemberEntities().keySet().iterator();
                while (meIter.hasNext()) {
                    String aliasName = meIter.next();
                    EntityDefinition fromEntityDefinition = modelViewEntity.getMemberModelEntity(aliasName);

                    sql.append(makeViewTable(fromEntity));
                    sql.append(" ");
                    sql.append(aliasName);
                    if (meIter.hasNext()) sql.append(", ");
                }

                // JOIN clause(s): none needed, all the work done in the where clause for theta-oracle
            } else {
                throw new IllegalArgumentException("The join-style " + joinStyle + " is not supported");
            }
        } else {
            sql.append(entityDefinition.getTableName())
        }
    }

    void startWhereClause() {
        this.sql.append(" WHERE ")
    }

    static class EntityConditionParameter {
        protected EntityDefinition.EntityFieldDefinition fieldDefinition
        protected Object value

        EntityConditionParameter(EntityDefinition.EntityFieldDefinition fieldDefinition, Object value) {
            this.fieldDefinition = fieldDefinition
            this.value = value
        }
    }
}
