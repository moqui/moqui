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
import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.entity.EntityException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EntityFindBuilder extends EntityQueryBuilder {
    protected final static Logger logger = LoggerFactory.getLogger(EntityFindBuilder.class)

    protected EntityFindBase entityFindBase

    EntityFindBuilder(EntityDefinition entityDefinition, EntityFindBase entityFindBase) {
        super(entityDefinition, entityFindBase.efi)
        this.entityFindBase = entityFindBase

        // this is always going to start with "SELECT ", so just set it here
        this.sqlTopLevel.append("SELECT ")
    }

    void addLimitOffset(Integer limit, Integer offset) {
        if (limit == null && offset == null) return
        Node databaseNode = this.efi.getDatabaseNode(this.efi.getEntityGroupName(mainEntityDefinition))
        // if no databaseNode do nothing, means it is not a standard SQL/JDBC database
        if (databaseNode != null) {
            if (databaseNode."@offset-style" == "limit") {
                // use the LIMIT/OFFSET style
                this.sqlTopLevel.append(" LIMIT ").append(limit ?: "ALL")
                this.sqlTopLevel.append(" OFFSET ").append(offset ?: 0)
            } else if (databaseNode."@offset-style" == "fetch" || !databaseNode."@offset-style") {
                // use SQL2008 OFFSET/FETCH style by default
                if (offset != null) this.sqlTopLevel.append(" OFFSET ").append(offset).append(" ROWS")
                if (limit != null) this.sqlTopLevel.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY")
            }
            // do nothing here for offset-style=cursor, taken care of in EntityFindImpl
        }
    }

    /** Adds FOR UPDATE, should be added to end of query */
    void makeForUpdate() { this.sqlTopLevel.append(" FOR UPDATE") }

    void makeDistinct() { this.sqlTopLevel.append("DISTINCT ") }

    void makeCountFunction() {
        boolean isDistinct = this.entityFindBase.getDistinct() || (this.mainEntityDefinition.isViewEntity() &&
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
            if (this.entityFindBase.fieldsToSelect) {
                Node aliasNode = this.mainEntityDefinition.getFieldNode((String) this.entityFindBase.fieldsToSelect.get(0))
                if (aliasNode && aliasNode."@function") {
                    // if the field has a function already we don't want to count just it, would be meaningless
                    this.sqlTopLevel.append("COUNT(DISTINCT *) ")
                } else {
                    this.sqlTopLevel.append("COUNT(DISTINCT ")
                    // TODO: possible to do all fieldsToSelect, or only one in SQL? if do all col names here it will blow up...
                    this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName((String) this.entityFindBase.fieldsToSelect.get(0), false))
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

    void expandJoinFromAlias(Node entityNode, String searchEntityAlias, Set<String> entityAliasUsedSet, Set<String> entityAliasesJoinedInSet) {
        // first see if it needs expanding
        if (entityAliasesJoinedInSet.contains(searchEntityAlias)) return
        // find the a link back one in the set
        Node memberEntityNode = (Node) entityNode."member-entity".find({ it."@entity-alias" == searchEntityAlias })
        if (memberEntityNode == null)
            throw new EntityException("Could not find member-entity with entity-alias [${searchEntityAlias}] in entity [${entityNode."@entity-name"}]")
        String joinFromAlias = memberEntityNode."@join-from-alias"
        if (entityAliasesJoinedInSet.contains(joinFromAlias)) {
            entityAliasesJoinedInSet.add(searchEntityAlias)
            entityAliasUsedSet.add(joinFromAlias)
            entityAliasUsedSet.add(searchEntityAlias)
        } else {
            // recurse to find member-entity with joinFromAlias, add in its joinFromAlias until one is found that is already in the set
            expandJoinFromAlias(entityNode, joinFromAlias, entityAliasUsedSet, entityAliasesJoinedInSet)
        }
    }

    void makeSqlFromClause() {
        makeSqlFromClause(this.mainEntityDefinition, this.sqlTopLevel)
    }
    void makeSqlFromClause(EntityDefinition localEntityDefinition, StringBuilder localBuilder) {
        localBuilder.append(" FROM ")

        Node entityNode = localEntityDefinition.getEntityNode()

        if (localEntityDefinition.isViewEntity()) {
            Node databaseNode = this.efi.getDatabaseNode(this.efi.getEntityGroupName(localEntityDefinition))
            String joinStyle = databaseNode?."@join-style" ?: "ansi"

            if ("ansi" != joinStyle && "ansi-no-parenthesis" != joinStyle) {
                throw new IllegalArgumentException("The join-style [${joinStyle}] is not supported, found on database [${databaseNode?."@name"}]")
            }

            boolean useParenthesis = ("ansi" == joinStyle)

            // get a list of all aliased fields selected or ordered by and don't bother joining in a member-entity
            //     that is not selected or ordered by
            Set<String> entityAliasUsedSet = new HashSet<String>()
            Set<String> fieldUsedSet = new HashSet<String>()
            EntityConditionImplBase viewWhere = localEntityDefinition.makeViewWhereCondition()
            if (viewWhere != null) viewWhere.getAllAliases(entityAliasUsedSet, fieldUsedSet)
            if (entityFindBase.whereEntityCondition != null)
                entityFindBase.whereEntityCondition.getAllAliases(entityAliasUsedSet, fieldUsedSet)
            if (entityFindBase.havingEntityCondition != null)
                entityFindBase.havingEntityCondition.getAllAliases(entityAliasUsedSet, fieldUsedSet)
            fieldUsedSet.addAll(entityFindBase.fieldsToSelect)
            if (entityFindBase.orderByFields) fieldUsedSet.addAll(entityFindBase.orderByFields)
            for (String fieldName in fieldUsedSet) {
                Node aliasNode = localEntityDefinition.getFieldNode(fieldName)
                if (aliasNode?."@entity-alias") entityAliasUsedSet.add((String) aliasNode."@entity-alias")
            }

            // make sure each entityAlias in the entityAliasUsedSet links back to the main
            String mainEntityAlias = entityNode."member-entity".find({ !it."@join-from-alias" })?."@entity-alias"
            Set<String> entityAliasesJoinedInSet = new HashSet<String>()
            entityAliasesJoinedInSet.add(mainEntityAlias)
            for (String entityAlias in new HashSet(entityAliasUsedSet)) {
                expandJoinFromAlias(entityNode, entityAlias, entityAliasUsedSet, entityAliasesJoinedInSet)
            }

            // logger.warn("============== entityAliasUsedSet=${entityAliasUsedSet} for entity ${localEntityDefinition.entityName}, fieldsToSelect=${entityFindBase.fieldsToSelect}, orderByFields=${entityFindBase.orderByFields}")

            // keep a set of all aliases in the join so far and if the left entity alias isn't there yet, and this
            // isn't the first one, throw an exception
            Set<String> joinedAliasSet = new TreeSet<String>()

            // on initial pass only add opening parenthesis since easier than going back and inserting them, then insert the rest
            boolean isFirst = true
            boolean fromEmpty = true
            for (Node relatedMemberEntity in entityNode."member-entity") {
                // logger.warn("=================== joining member-entity ${relatedMemberEntity}")
                // if this isn't joined in skip it (should be first one only); the first is handled below
                if (!relatedMemberEntity."@join-from-alias") continue
                // if entity alias not used don't join it in
                if (!entityAliasUsedSet.contains(relatedMemberEntity."@entity-alias")) continue

                if (isFirst && useParenthesis) localBuilder.append('(')

                // adding to from, then it's not empty
                fromEmpty = false

                String linkEntityName = entityNode."member-entity".find({ it."@entity-alias" == relatedMemberEntity."@join-from-alias" })?."@entity-name"
                EntityDefinition linkEntityDefinition = this.efi.getEntityDefinition(linkEntityName)
                String relatedLinkEntityName = relatedMemberEntity."@entity-name"
                EntityDefinition relatedLinkEntityDefinition = this.efi.getEntityDefinition(relatedLinkEntityName)

                if (isFirst) {
                    // first link, add link entity for this one only, for others add related link entity
                    makeSqlViewTableName(linkEntityDefinition, localBuilder)
                    localBuilder.append(" ")
                    localBuilder.append(relatedMemberEntity."@join-from-alias")

                    joinedAliasSet.add((String) relatedMemberEntity."@join-from-alias")
                } else {
                    // make sure the left entity alias is already in the join...
                    if (!joinedAliasSet.contains(relatedMemberEntity."@join-from-alias")) {
                        logger.error("For view-entity [${localEntityDefinition.getFullEntityName()}] found member-entity with @join-from-alias [${relatedMemberEntity."@join-from-alias"}] that isn't in the joinedAliasSet: ${joinedAliasSet}; view-entity Node: ${entityNode}")
                        throw new IllegalArgumentException("Tried to link the " + relatedMemberEntity."@entity-alias" +
                                " alias to the " + relatedMemberEntity."@join-from-alias" + " alias of the " +
                                localEntityDefinition.getFullEntityName() + " view-entity, but it is not the first member-entity and has not been joined to a previous member-entity. In other words, the left/main alias isn't connected to the rest of the member-entities yet.")
                    }
                }
                // now put the rel (right) entity alias into the set that is in the join
                joinedAliasSet.add((String) relatedMemberEntity."@entity-alias")

                if (relatedMemberEntity."@join-optional" == "true") {
                    localBuilder.append(" LEFT OUTER JOIN ")
                } else {
                    localBuilder.append(" INNER JOIN ")
                }

                makeSqlViewTableName(relatedLinkEntityDefinition, localBuilder)
                localBuilder.append(" ")
                localBuilder.append(relatedMemberEntity."@entity-alias")
                localBuilder.append(" ON ")

                if (!relatedMemberEntity."key-map") {
                    throw new IllegalArgumentException("No member-entity/join key-maps found for the " +
                            relatedMemberEntity."@join-from-alias" + " and the " + relatedMemberEntity."@entity-alias" +
                            " member-entities of the " + localEntityDefinition.getFullEntityName() + " view-entity.")
                }

                boolean isFirstKeyMap = true
                Collection<Node> keyMaps = (Collection<Node>) relatedMemberEntity."key-map".findAll()
                for (Node keyMap in keyMaps) {
                    if (isFirstKeyMap) isFirstKeyMap = false else localBuilder.append(" AND ")

                    localBuilder.append(relatedMemberEntity."@join-from-alias")
                    localBuilder.append(".")
                    localBuilder.append(sanitizeColumnName(linkEntityDefinition.getColumnName((String) keyMap."@field-name", false)))

                    localBuilder.append(" = ")

                    String relatedFieldName = keyMap."@related-field-name" ?: keyMap."@field-name"
                    if (!relatedLinkEntityDefinition.isField(relatedFieldName) &&
                            relatedLinkEntityDefinition.pkFieldNames.size() == 1 && keyMaps.size() == 1) {
                        relatedFieldName = relatedLinkEntityDefinition.pkFieldNames[0]
                        // if we don't match these constraints and get this default we'll get an error later...
                    }
                    localBuilder.append(relatedMemberEntity."@entity-alias")
                    localBuilder.append(".")
                    localBuilder.append(sanitizeColumnName(relatedLinkEntityDefinition.getColumnName(relatedFieldName, false)))
                }

                if (relatedMemberEntity."entity-condition") {
                    // add any additional manual conditions for the member-entity view link here
                    Node entityCondition = relatedMemberEntity."entity-condition"[0]
                    EntityConditionImplBase linkEcib = localEntityDefinition.makeViewListCondition(entityCondition)
                    localBuilder.append(" AND ")
                    linkEcib.makeSqlWhere(this)
                }

                isFirst = false
            }
            if (!fromEmpty && useParenthesis) localBuilder.append(')')

            // handle member-entities not referenced in any member-entity.@join-from-alias attribute
            for (Node memberEntity in entityNode."member-entity") {
                // if entity alias not used don't join it in
                if (!entityAliasUsedSet.contains(memberEntity."@entity-alias")) continue

                if (joinedAliasSet.contains(memberEntity."@entity-alias")) continue

                EntityDefinition fromEntityDefinition = this.efi.getEntityDefinition((String) memberEntity."@entity-name")
                if (fromEmpty) fromEmpty = false else localBuilder.append(", ")
                makeSqlViewTableName(fromEntityDefinition, localBuilder)
                localBuilder.append(" ")
                localBuilder.append((String) memberEntity."@entity-alias")
            }
        } else {
            localBuilder.append(localEntityDefinition.getFullTableName())
        }
    }

    /* void makeSqlViewTableName(StringBuilder localBuilder) {
        makeSqlViewTableName(this.mainEntityDefinition, localBuilder)
    } */
    void makeSqlViewTableName(EntityDefinition localEntityDefinition, StringBuilder localBuilder) {
        if (localEntityDefinition.isViewEntity()) {
            localBuilder.append("(SELECT ")

            boolean isFirst = true
            Set<String> fieldsToSelect = new HashSet<String>()
            for (Node aliasNode in localEntityDefinition.entityNode."alias") {
                fieldsToSelect.add((String) aliasNode."@name")
                if (isFirst) isFirst = false else localBuilder.append(", ")
                localBuilder.append(localEntityDefinition.getColumnName((String) aliasNode."@name", true))
                // TODO: are the next two lines really needed? have removed AS stuff elsewhere since it is not commonly used and not needed
                localBuilder.append(" AS ")
                localBuilder.append(sanitizeColumnName(localEntityDefinition.getColumnName((String) aliasNode."@name", false)))
            }

            makeSqlFromClause(localEntityDefinition, localBuilder)


            StringBuilder gbClause = new StringBuilder()
            if (localEntityDefinition.hasFunctionAlias()) {
                // do a different approach to GROUP BY: add all fields that are selected and don't have a function
                for (Node aliasNode in localEntityDefinition.getEntityNode()."alias") {
                    if (fieldsToSelect.contains(aliasNode."@name") && !aliasNode."@function") {
                        if (gbClause) gbClause.append(", ")
                        gbClause.append(localEntityDefinition.getColumnName((String) aliasNode."@name", false))
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
                        gbClause.append(this.mainEntityDefinition.getColumnName((String) aliasNode."@name", false))
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
            if (!fieldName) continue

            if (isFirst) isFirst = false else this.sqlTopLevel.append(", ")

            // Parse the fieldName (can have other stuff in it, need to tear down to just the field name)
            FieldOrderOptions foo = new FieldOrderOptions(fieldName)
            fieldName = foo.fieldName

            int typeValue = 1
            Node fieldNode = getMainEd().getFieldNode(fieldName)
            if (fieldNode != null) {
                String javaType = efi.getFieldJavaType((String) fieldNode."@type", getMainEd())
                typeValue = EntityFacadeImpl.getJavaTypeInt(javaType)
            } else {
                logger.warn("Making ORDER BY clause, could not find field [${fieldName}] in entity [${getMainEd().getFullEntityName()}]")
            }

            // not that it's all torn down, build it back up using the column name
            if (foo.caseUpperLower != null && typeValue == 1) this.sqlTopLevel.append(foo.caseUpperLower ? "UPPER(" : "LOWER(")
            this.sqlTopLevel.append(this.mainEntityDefinition.getColumnName(fieldName, false))
            if (foo.caseUpperLower != null && typeValue == 1) this.sqlTopLevel.append(")")

            this.sqlTopLevel.append(foo.descending ? " DESC" : " ASC")

            if (foo.nullsFirstLast != null) this.sqlTopLevel.append(foo.nullsFirstLast ? " NULLS FIRST" : " NULLS LAST")
        }
    }

    static class FieldOrderOptions {
        String fieldName = null
        Boolean nullsFirstLast = null
        boolean descending = false
        Boolean caseUpperLower = null

        FieldOrderOptions(String orderByName) {
            orderByName = orderByName.trim()
            if (!orderByName) return

            if (orderByName.toUpperCase().endsWith("NULLS FIRST")) {
                nullsFirstLast = true
                orderByName = orderByName.substring(0, orderByName.length() - "NULLS FIRST".length()).trim()
            }
            if (orderByName.toUpperCase().endsWith("NULLS LAST")) {
                nullsFirstLast = false
                orderByName = orderByName.substring(0, orderByName.length() - "NULLS LAST".length()).trim()
            }

            int startIndex = 0
            int endIndex = orderByName.length()
            if (orderByName.endsWith(" DESC")) {
                descending = true
                endIndex -= 5
            } else if (orderByName.endsWith(" ASC")) {
                descending = false
                endIndex -= 4
            } else if (orderByName.startsWith("-")) {
                descending = true
                startIndex++
            } else if (orderByName.startsWith("+")) {
                descending = false
                startIndex++
            }
            orderByName = orderByName.substring(startIndex, endIndex)

            String upperText = orderByName.toUpperCase()
            if (upperText.startsWith("UPPER(")) {
                caseUpperLower = true
                orderByName = orderByName.substring(6)
            } else if (upperText.startsWith("^")) {
                caseUpperLower = true
                orderByName = orderByName.substring(1)
            } else if (upperText.startsWith("LOWER(")) {
                caseUpperLower = false
                orderByName = orderByName.substring(6)
            }
            if (orderByName.endsWith(")")) { orderByName = orderByName.substring(0,orderByName.length()-1) }

            fieldName = orderByName
        }
    }

    @Override
    PreparedStatement makePreparedStatement() {
        if (!this.connection) throw new IllegalStateException("Cannot make PreparedStatement, no Connection in place")
        String sql = this.getSqlTopLevel().toString()
        // if (this.mainEntityDefinition.getFullEntityName().contains("FindPartyView")) logger.warn("========= making find PreparedStatement for SQL: ${sql}")
        if (logger.isDebugEnabled()) logger.debug("making find PreparedStatement for SQL: ${sql}")
        try {
            this.ps = connection.prepareStatement(sql, this.entityFindBase.resultSetType, this.entityFindBase.resultSetConcurrency)
            if (this.entityFindBase.maxRows > 0) this.ps.setMaxRows(this.entityFindBase.maxRows)
            if (this.entityFindBase.fetchSize > 0) this.ps.setFetchSize(this.entityFindBase.fetchSize)
        } catch (SQLException e) {
            handleSqlException(e, sql)
        }
        return this.ps
    }
}
