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

import java.sql.PreparedStatement
import java.sql.SQLException

class EntityFindBuilder extends EntityQueryBuilder {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityFindBuilder.class)

    protected EntityFindImpl entityFindImpl

    EntityFindBuilder(EntityDefinition entityDefinition, EntityFindImpl entityFindImpl) {
        super(entityDefinition, entityFindImpl.efi)
        this.entityFindImpl = entityFindImpl

        // this is always going to start with "SELECT ", so just set it here
        this.sqlTopLevel.append("SELECT ")
    }

    void addLimitOffset(Integer limit, Integer offset) {
        if (limit == null && offset == null) return
        Node databaseNode = this.efi.getDatabaseNode(this.efi.getEntityGroupName(mainEntityDefinition.entityName))
        if (databaseNode."@offset-style" == "limit") {
            // use the LIMIT/OFFSET style
            this.sqlTopLevel.append(" LIMIT ").append(limit ?: "ALL")
            this.sqlTopLevel.append(" OFFSET ").append(offset ?: 0)
        } else {
            // use SQL2008 OFFSET/FETCH style by default
            if (offset != null) this.sqlTopLevel.append(" OFFSET ").append(offset).append(" ROWS")
            if (limit != null) this.sqlTopLevel.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY")
        }
    }

    /** Adds FOR UPDATE, should be added to end of query */
    void makeForUpdate() { this.sqlTopLevel.append(" FOR UPDATE") }

    void makeDistinct() { this.sqlTopLevel.append("DISTINCT ") }

    void makeCountFunction() {
        boolean isDistinct = this.entityFindImpl.getDistinct() || (this.mainEntityDefinition.isViewEntity() &&
                "true" == this.mainEntityDefinition.getEntityNode()."entity-condition"?.getAt(0)?."@distinct")
        boolean isGroupBy = this.mainEntityDefinition.hasFunctionAlias()

        if (isGroupBy) {
            this.sqlTopLevel.append("COUNT(1) FROM (SELECT ")
        }

        if (isDistinct) {
            // old style, not sensitive to selecting limited columns: sql.append("DISTINCT COUNT(*) ")

            /* NOTE: the code below was causing problems so the line above may be used instead, in view-entities in
             * some cases it seems to cause the "COUNT(DISTINCT " to appear twice, causing an attempt to try to count
             * a count (function="count-distinct", distinct=true in find options)
             */
            if (this.entityFindImpl.fieldsToSelect) {
                Node aliasNode = this.mainEntityDefinition.getFieldNode(this.entityFindImpl.fieldsToSelect[0])
                if (aliasNode && aliasNode."@function") {
                    // if the field has a function already we don't want to count just it, would be meaningless
                    this.sqlTopLevel.append("COUNT(DISTINCT *) ")
                } else {
                    this.sqlTopLevel.append("COUNT(DISTINCT ")
                    // TODO: possible to do all fieldsToSelect, or only one in SQL? if do all col names here it will blow up...
                    this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(this.entityFindImpl.fieldsToSelect[0], false))
                    this.sqlTopLevel.append(")")
                }
            } else {
                this.sqlTopLevel.append("COUNT(DISTINCT *) ")
            }
        } else {
            // This is COUNT(1) instead of COUNT(*) for better performance, and should get the same results at least
            // when there is no DISTINCT
            this.sqlTopLevel.append("COUNT(1) ")
        }
    }

    void closeCountFunctionIfGroupBy() {
        if (this.mainEntityDefinition.hasFunctionAlias()) {
            this.sqlTopLevel.append(") TEMP_NAME")
        }
    }

    void makeSqlSelectFields(Set<String> fieldsToSelect) {
        if (fieldsToSelect.size() > 0) {
            boolean isFirst = true
            for (String fieldName in fieldsToSelect) {
                if (isFirst) isFirst = false else this.sqlTopLevel.append(", ")
                this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(fieldName, true))
            }
        } else {
            this.sqlTopLevel.append("*")
        }
    }

    void makeSqlFromClause() {
        makeSqlFromClause(this.mainEntityDefinition, this.sqlTopLevel)
    }
    void makeSqlFromClause(EntityDefinition localEntityDefinition, StringBuilder localBuilder) {
        localBuilder.append(" FROM ")

        Node entityNode = localEntityDefinition.entityNode

        if (localEntityDefinition.isViewEntity()) {
            Node databaseNode = this.efi.getDatabaseNode(this.efi.getEntityGroupName(localEntityDefinition.entityName))
            String joinStyle = databaseNode."@join-style" ?: "ansi"

            if ("ansi" != joinStyle && "ansi-no-parenthesis" != joinStyle) {
                throw new IllegalArgumentException("The join-style [${joinStyle}] is not supported, found on database [${databaseNode."@name"}]")
            }

            boolean useParenthesis = ("ansi" == joinStyle)

            // keep a set of all aliases in the join so far and if the left entity alias isn't there yet, and this
            // isn't the first one, throw an exception
            Set<String> joinedAliasSet = new TreeSet<String>()

            // on initial pass only add opening parenthesis since easier than going back and inserting them, then insert the rest
            StringBuilder restOfStatement = new StringBuilder()
            boolean isFirst = true
            for (Node relatedMemberEntity in entityNode."member-entity") {
                if (!relatedMemberEntity."@join-from-alias") continue

                if (useParenthesis) localBuilder.append('(')

                String linkEntityName = entityNode."member-entity".find({ it."@entity-alias" == relatedMemberEntity."@join-from-alias" })?."@entity-name"
                EntityDefinition linkEntityDefinition = this.efi.getEntityDefinition(linkEntityName)
                String relatedLinkEntityName = relatedMemberEntity."@entity-name"
                EntityDefinition relatedLinkEntityDefinition = this.efi.getEntityDefinition(relatedLinkEntityName)

                if (isFirst) {
                    // first link, add link entity for this one only, for others add related link entity
                    makeSqlViewTableName(linkEntityDefinition, restOfStatement)
                    restOfStatement.append(" ")
                    restOfStatement.append(relatedMemberEntity."@join-from-alias")

                    joinedAliasSet.add(relatedMemberEntity."@join-from-alias")
                } else {
                    // make sure the left entity alias is already in the join...
                    if (!joinedAliasSet.contains(relatedMemberEntity."@join-from-alias")) {
                        throw new IllegalArgumentException("Tried to link the " + relatedMemberEntity."@join-from-alias" +
                                " alias to the " + relatedMemberEntity."@entity-alias" + " alias of the " +
                                localEntityDefinition.getEntityName() + " view-entity, but it is not the first view-link and has not been included in a previous view-link. In other words, the left/main alias isn't connected to the rest of the member-entities yet.")
                    }
                }
                // now put the rel (right) entity alias into the set that is in the join
                joinedAliasSet.add(relatedMemberEntity."@entity-alias")

                if (relatedMemberEntity."@join-optional" == "true") {
                    restOfStatement.append(" LEFT OUTER JOIN ")
                } else {
                    restOfStatement.append(" INNER JOIN ")
                }

                makeSqlViewTableName(relatedLinkEntityDefinition, restOfStatement)
                restOfStatement.append(" ")
                restOfStatement.append(relatedMemberEntity."@entity-alias")
                restOfStatement.append(" ON ")

                if (!relatedMemberEntity."key-map") {
                    throw new IllegalArgumentException("No view-link/join key-maps found for the " +
                            relatedMemberEntity."@join-from-alias" + " and the " + relatedMemberEntity."@entity-alias" +
                            " member-entities of the " + localEntityDefinition.getEntityName() + " view-entity.")
                }

                boolean isFirstKeyMap = true
                for (Node keyMap in relatedMemberEntity."key-map") {
                    if (isFirstKeyMap) isFirstKeyMap = false else restOfStatement.append(" AND ")

                    restOfStatement.append(relatedMemberEntity."@join-from-alias")
                    restOfStatement.append(".")
                    restOfStatement.append(sanitizeColumnName(linkEntityDefinition.getColumnName(keyMap."@field-name", false)))

                    restOfStatement.append(" = ")

                    String relatedFieldName = keyMap."@related-field-name" ?: keyMap."@field-name"
                    restOfStatement.append(relatedMemberEntity."@entity-alias")
                    restOfStatement.append(".")
                    restOfStatement.append(sanitizeColumnName(relatedLinkEntityDefinition.getColumnName(relatedFieldName, false)))
                }

                if (relatedMemberEntity."entity-condition") {
                    // TABLED: add any additional manual conditions for the view-link here
                }

                if (useParenthesis) restOfStatement.append(')')
                isFirst = false
            }

            localBuilder.append(restOfStatement.toString())

            // handle member-entities not referenced in any view-link element
            boolean fromEmpty = restOfStatement.length() == 0
            for (Node memberEntity in entityNode."member-entity") {
                EntityDefinition fromEntityDefinition = this.efi.getEntityDefinition(memberEntity."@entity-name")
                if (!joinedAliasSet.contains(memberEntity."@entity-alias")) {
                    if (fromEmpty) fromEmpty = false else localBuilder.append(", ")
                    makeSqlViewTableName(fromEntityDefinition, localBuilder)
                    localBuilder.append(" ")
                    localBuilder.append(memberEntity."@entity-alias")
                }
            }
        } else {
            localBuilder.append(localEntityDefinition.getFullTableName())
        }
    }

    void makeSqlViewTableName(StringBuilder localBuilder) {
        makeSqlViewTableName(this.mainEntityDefinition, localBuilder)
    }
    void makeSqlViewTableName(EntityDefinition localEntityDefinition, StringBuilder localBuilder) {
        if (localEntityDefinition.isViewEntity()) {
            localBuilder.append("(SELECT ")

            boolean isFirst = true
            Set<String> fieldsToSelect = new HashSet<String>()
            for (Node aliasNode in localEntityDefinition.entityNode."alias") {
                fieldsToSelect.add(aliasNode."@name")
                if (isFirst) isFirst = false else localBuilder.append(", ")
                localBuilder.append(localEntityDefinition.getColumnName(aliasNode."@name", true))
                // TODO: are the next two lines really needed? have removed AS stuff elsewhere since it is not commonly used and not needed
                localBuilder.append(" AS ")
                localBuilder.append(sanitizeColumnName(localEntityDefinition.getColumnName(aliasNode."@name", false)))
            }

            makeSqlFromClause(localEntityDefinition, localBuilder)


            StringBuilder gbClause = new StringBuilder()
            if (localEntityDefinition.hasFunctionAlias()) {
                // do a different approach to GROUP BY: add all fields that are selected and don't have a function
                for (Node aliasNode in localEntityDefinition.getEntityNode()."alias") {
                    if (fieldsToSelect.contains(aliasNode."@name") && !aliasNode."@function") {
                        if (gbClause) gbClause.append(", ")
                        gbClause.append(localEntityDefinition.getColumnName(aliasNode."@name", false))
                    }
                }
            }
            if (gbClause) {
                localBuilder.append(" GROUP BY ")
                localBuilder.append(gbClause.toString())
            }

            localBuilder.append(")");
        } else {
            localBuilder.append(localEntityDefinition.getFullTableName())
        }
    }

    void startWhereClause() {
        this.sqlTopLevel.append(" WHERE ")
    }

    void makeGroupByClause(Set<String> fieldsToSelect) {
        if (this.mainEntityDefinition.isViewEntity()) {
            StringBuilder gbClause = new StringBuilder()
            if (this.mainEntityDefinition.hasFunctionAlias()) {
                // do a different approach to GROUP BY: add all fields that are selected and don't have a function
                for (Node aliasNode in this.mainEntityDefinition.getEntityNode()."alias") {
                    if (fieldsToSelect.contains(aliasNode."@name") && !aliasNode."@function") {
                        if (gbClause) gbClause.append(", ")
                        gbClause.append(this.mainEntityDefinition.getColumnName(aliasNode."@name", false))
                    }
                }
            }
            if (gbClause) {
                this.sqlTopLevel.append(" GROUP BY ")
                this.sqlTopLevel.append(gbClause.toString())
            }
        }
    }

    void startHavingClause() {
        this.sqlTopLevel.append(" HAVING ")
    }

    void makeOrderByClause(List orderByFieldList) {
        if (orderByFieldList) {
            this.sqlTopLevel.append(" ORDER BY ")
        }
        boolean isFirst = true
        for (String fieldName in orderByFieldList) {
            if (isFirst) isFirst = false else this.sqlTopLevel.append(", ")

            // Parse the fieldName (can have other stuff in it, need to tear down to just the field name)
            Boolean nullsFirstLast = null
            boolean descending = false
            Boolean caseUpperLower = null

            fieldName = fieldName.trim()

            if (fieldName.toUpperCase().endsWith("NULLS FIRST")) {
                nullsFirstLast = true
                fieldName = fieldName.substring(0, fieldName.length() - "NULLS FIRST".length()).trim()
            }
            if (fieldName.toUpperCase().endsWith("NULLS LAST")) {
                nullsFirstLast = false
                fieldName = fieldName.substring(0, fieldName.length() - "NULLS LAST".length()).trim()
            }

            int startIndex = 0
            int endIndex = fieldName.length()
            if (fieldName.endsWith(" DESC")) {
                descending = true
                endIndex -= 5
            } else if (fieldName.endsWith(" ASC")) {
                descending = false
                endIndex -= 4
            } else if (fieldName.startsWith("-")) {
                descending = true
                startIndex++
            } else if (fieldName.startsWith("+")) {
                descending = false
                startIndex++
            }

            if (fieldName.endsWith(")")) {
                String upperText = fieldName.toUpperCase()
                endIndex--
                if (upperText.startsWith("UPPER(")) {
                    caseUpperLower = true
                    startIndex += 6
                } else if (upperText.startsWith("LOWER(")) {
                    caseUpperLower = false
                    startIndex += 6
                }
            }

            fieldName = fieldName.substring(startIndex, endIndex)

            // not that it's all torn down, build it back up using the column name
            if (caseUpperLower != null) this.sqlTopLevel.append(caseUpperLower ? "UPPER(" : "LOWER(")
            this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(fieldName, false))
            if (caseUpperLower != null) this.sqlTopLevel.append(")")

            this.sqlTopLevel.append(descending ? " DESC" : " ASC")

            if (nullsFirstLast != null) this.sqlTopLevel.append(nullsFirstLast ? " NULLS FIRST" : " NULLS LAST")
        }
    }

    @Override
    PreparedStatement makePreparedStatement() {
        if (!this.connection) throw new IllegalStateException("Cannot make PreparedStatement, no Connection in place")
        String sql = this.getSqlTopLevel().toString()
        try {
            this.ps = connection.prepareStatement(sql, this.entityFindImpl.resultSetType, this.entityFindImpl.resultSetConcurrency)
            if (this.entityFindImpl.maxRows > 0) this.ps.setMaxRows(this.entityFindImpl.maxRows)
            if (this.entityFindImpl.fetchSize > 0) this.ps.setFetchSize(this.entityFindImpl.fetchSize)
        } catch (SQLException e) {
            handleSqlException(e, sql)
        }
        return this.ps
    }
}
