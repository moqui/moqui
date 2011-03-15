<#--
This Work is in the public domain and is provided on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
including, without limitation, any warranties or conditions of TITLE,
NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
You are solely responsible for determining the appropriateness of using
this Work and assume any risks associated with your use of this Work.

This Work includes contributions authored by David E. Jones, not as a
"work for hire", who hereby disclaims any copyright to the same.
-->
import org.moqui.impl.StupidUtilities
// these are in the context by default: ExecutionContext ec, Map<String, Object> context, Map<String, Object> result
<#visit xmlActionsRoot/>

<#macro actions>
<#recurse/>
</#macro>
<#macro "pre-actions">
<#recurse/>
</#macro>
<#macro "row-actions">
<#recurse/>
</#macro>

<#-- NOTE should we handle out-map?has_content and async!=false with a ServiceResultWaiter? -->
<#macro "service-call">
    <#assign handleResult = (.node["@out-map"]?has_content && (!.node["@async"]?has_content || .node["@async"] == "false"))>
    if (true) {
        <#if handleResult>def call_service_result = </#if>ec.service.<#if .node.@async?has_content && .node.@async != "false">async()<#else/>sync()</#if>.name("${.node.@name}")<#if .node["@async"]?if_exists == "persist">.persist(true)</#if><#if .node["@multi"]?if_exists == "true">.multi(true)</#if>
            <#if .node["@in-map"]?if_exists == "true">.parameters(context).parameters((ec.web?.parameters)?:[:])<#elseif .node["@in-map"]?has_content && .node["@in-map"] != "false">.parameters(${.node["@in-map"]})</#if><#list .node["field-map"] as fieldMap>.parameter("${fieldMap["@field-name"]}",<#if fieldMap["@from"]?has_content>${fieldMap["@from"]}<#elseif fieldMap["@value"]?has_content>"""${fieldMap["@value"]}"""<#else>${fieldMap["@field-name"]}</#if>)</#list>.call()
        <#if handleResult>
        if (context.${.node["@out-map"]} != null) {
            context.${.node["@out-map"]}.putAll(call_service_result)
        } else {
            context.${.node["@out-map"]} = call_service_result
        }
        </#if>
    }
</#macro>

<#macro "script"><#if .node["@location"]?has_content>ec.resource.runScriptInCurrentContext(${.node["@location"]}, null)</#if>
    // begin inline script
    ${.node}
    // end inline script
</#macro>

<#macro set>    <#if .node["@set-if-empty"]?has_content && .node["@set-if-empty"] == "false">${.node["@field"]}_temp_internal = <#if .node["@from"]?has_content>${.node["@from"]}<#else>"""${.node.@value}"""</#if><#if .node["@default-value"]?has_content> ?: ${.node["@default-value"]}</#if><#if .node["@type"]?has_content> as ${.node["@type"]}</#if>
if (${.node["@field"]}_temp_internal) ${.node["@field"]} = ${.node["@field"]}_temp_internal<#else/>${.node["@field"]} = <#if .node["@from"]?has_content>${.node["@from"]}<#else>"""${.node["@value"]}"""</#if><#if .node["@default-value"]?has_content> ?: ${.node["@default-value"]}</#if><#if .node["@type"]?has_content> as ${.node["@type"]}</#if></#if>
</#macro>

<#macro "order-map-list">
    StupidUtilities.orderMapList(${.node["@list"]}, [<#list .node["order-by"] as ob>'${ob["@field-name"]}'<#if ob_has_next>, </#if></#list>])
</#macro>
<#macro "filter-map-list"><#if .node["field-map"]?has_content>
    StupidUtilities.filterMapList(${.node["@list"]}, [<#list .node["field-map"] as fm>"${fm["@field-name"]}":<#if fm["@from"]?has_content>${fm["@from"]}<#else/>"""${fm["@value"]}"""</#if><#if fm_has_next>, </#if></#list>])
    </#if><#list .node["date-filter"] as df>
    StupidUtilities.filterMapListByDate(${.node["@list"]}, ${df["@from-field-name"]?default("fromDate")}, ${df["@thru-field-name"]?default("thruDate")}, <#if df["@valid-date"]?has_content>${df["@valid-date"]} ?: ec.user.nowTimestamp<#else/>ec.user.nowTimestamp</#if>)
    </#list>
</#macro>

<#macro "entity-sequenced-id-primary">
    ${.node["@value-field"]}.setSequencedIdPrimary()
</#macro>
<#macro "entity-sequenced-id-secondary">
    ${.node["@value-field"]}.setSequencedIdSecondary()
</#macro>
<#macro "entity-data">
    // TODO impl entity-data
</#macro>

<#-- =================== entity-find elements =================== -->
<#macro "entity-find-one">    ${.node["@value-field"]} = ec.entity.makeFind("${.node["@entity-name"]}")<#if .node["@cache"]?has_content>.useCache(${.node["@cache"]})</#if><#if .node["@for-update"]?has_content>.forUpdate(${.node["@for-update"]})</#if>
            <#if !.node["@auto-field-map"]?has_content || .node["@auto-field-map"] == "true">.condition(context)</#if><#list .node["field-map"] as fieldMap>.condition("${fieldMap["@field-name"]}", <#if fieldMap["@from"]?has_content>${fieldMap["@from"]}<#else><#if fieldMap["@value"]?has_content>"""${fieldMap["@value"]}"""<#else/>${fieldMap["@field-name"]}</#if></#if>)</#list><#list .node["select-field"] as sf>.selectField("${sf["@field-name"]}")</#list>.one()
</#macro>
<#macro "entity-find">
    ${.node["@list"]}_xafind = ec.entity.makeFind("${.node["@entity-name"]}")<#if .node["@cache"]?has_content>.useCache(${.node["@cache"]})</#if><#if .node["@for-update"]?has_content>.forUpdate(${.node["@for-update"]})</#if><#if .node["@distinct"]?has_content>.distinct(${.node["@distinct"]})</#if><#if .node["@offset"]?has_content>.offset(${.node["@offset"]})</#if><#if .node["@limit"]?has_content>.limit(${.node["@limit"]})</#if><#list .node["select-field"] as sf>.selectField('${sf["@field-name"]}')</#list><#list .node["order-by"] as ob>.orderBy("${ob["@field-name"]}")</#list>
            <#list .node["date-filter"] as df>.condition(<#visit df/>)</#list><#list .node["econdition"] as ec>.condition(<#visit ec/>)</#list><#list .node["econditions"] as ecs>.condition(<#visit ecs/>)</#list><#list .node["econdition-object"] as eco>.condition(<#visit eco/>)</#list>
    <#if .node["search-form-inputs"]?has_content>${.node["@list"]}_xafind.searchFormInputs(${.node["search-form-inputs"][0]["@input-fields-map"]?default("null")}, "${.node["search-form-inputs"][0]["@default-order-by"]?default("")}")
    </#if>
    <#if .node["having-econditions"]?has_content>${.node["@list"]}_xafind<#list .node["having-econditions"]["*"] as havingCond>.havingCondition(<#visit havingCond/>)</#list>
    </#if>
    <#if .node["limit-range"]?has_content>
    EntityListIterator ${.node["@list"]}_xafind_eli = null
    try {
        ${.node["@list"]}_xafind_eli = ${.node["@list"]}_xafind.iterator()
        ${.node["@list"]} = ${.node["@list"]}_xafind_eli.getPartialList(${.node["@start"]}, ${.node["@size"]})
    } finally { if (${.node["@list"]}_xafind_eli != null) ${.node["@list"]}_xafind_eli.close() }
    <#elseif .node["limit-view"]?has_content>
    EntityListIterator ${.node["@list"]}_xafind_eli = null
    try {
        ${.node["@list"]}_xafind_eli = ${.node["@list"]}_xafind.iterator()
        ${.node["@list"]} = ${.node["@list"]}_xafind_eli.getPartialList((${.node["@view-index"]} - 1) * ${.node["@view-size"]}, ${.node["@view-size"]})
    } finally { if (${.node["@list"]}_xafind_eli != null) ${.node["@list"]}_xafind_eli.close() }
    <#elseif .node["use-iterator"]?has_content>
    ${.node["@list"]} = ${.node["@list"]}_xafind.iterator()
    <#else>
    ${.node["@list"]} = ${.node["@list"]}_xafind.list()
    </#if>
</#macro>
<#macro "entity-find-count">
    ${.node["@count-field"]} = ec.entity.makeFind("${.node["@entity-name"]}")<#if .node["@cache"]?has_content>.useCache(${.node["@cache"]})</#if><#if .node["@distinct"]?has_content>.distinct(${.node["@distinct"]})</#if><#list .node["@select-field"] as sf>.selectField('${sf["@field-name"]}')</#list>
            <#list .node["date-filter"] as df>.condition(<#visit df/>)</#list><#list .node["econdition"] as ec>.condition(<#visit ec/>)</#list><#list .node["econditions"] as ecs>.condition(<#visit ecs/>)</#list><#list .node["econdition-object"] as eco>.condition(<#visit eco/>)</#list><#if .node["having-econditions"]?has_content><#list .node["having-econditions"]["*"] as havingCond>.havingCondition(<#visit havingCond/>)</#list></#if>.count()
</#macro>
<#-- =================== entity-find sub-elements =================== -->
<#macro "date-filter">ec.entity.conditionFactory.makeConditionDate("${.node["@from-field-name"]?default("fromDate")}", "${.node["@thru-field-name"]?default("thruDate")}", <#if .node["@valid-date"]?has_content>.node["@valid-date"] as Timestamp<#else>ec.user.nowTimestamp</#if>)</#macro>
<#macro "econdition">ec.entity.conditionFactory.makeActionCondition("${.node["@field-name"]}", "${.node["@operator"]?default("equals")}", "${.node["@from"]?default(.node["@field-name"])}", <#if .node["@value"]?has_content>"${.node["@value"]}"<#else>null</#if>, <#if .node["@to-field-name"]?has_content>"${.node["@to-field-name"]}"<#else>null</#if>, ${.node["@ignore-case"]?default("false")}, ${.node["@ignore-if-empty"]?default("false")}, ${.node["@ignore"]?default("false")})</#macro>
<#macro "econditions">ec.entity.conditionFactory.makeCondition([<#list .node?children as subCond><#visit subCond/><#if subCond_has_next>, </#if></#list>], org.moqui.impl.entity.EntityConditionFactoryImpl.getJoinOperator("${.node["@combine"]}"))</#macro>
<#macro "econdition-object">${.node["@field"]}</#macro>

<#-- =================== entity other elements =================== -->
<#macro "entity-find-related-one">    ${.node["@to-value-field"]} = ${.node["@value-field"]}.findRelatedOne("${.node["@relationship-name"]}", ${.node["@cache"]?default("null")}, ${.node["@for-update"]?default("null")})
</#macro>
<#macro "entity-find-related">    ${.node["@list"]} = ${.node["@value-field"]}.findRelated("${.node["@relationship-name"]}", ${.node["@map"]?default("null")}, ${.node["@order-by-list"]?default("null")}, ${.node["@cache"]?default("null")}, ${.node["@for-update"]?default("null")})
</#macro>

<#macro "entity-make-value">    ${.node["@value-field"]} = ec.entity.makeValue("${.node["@entity-name"]}")<#if .node["@map"]?has_content>
    ${.node["@value-field"]}.setFields(${.node["@map"]}, true, null, null)</#if>
</#macro>
<#macro "entity-create">    ${.node["@value-field"]}<#if .node["@or-update"]?has_content && .node["@or-update"] == "true">.createOrUpdate()<#else/>.create()</#if>
</#macro>
<#macro "entity-update">    ${.node["@value-field"]}.update()
</#macro>
<#macro "entity-delete">    ${.node["@value-field"]}.delete()
</#macro>
<#macro "entity-delete-related">    ${.node["@value-field"]}.deleteRelated(${.node["@relationship-name"]})
</#macro>
<#macro "entity-delete-by-condition">    ec.entity.makeFind("${.node["@entity-name"]}")
            <#list .node["date-filter"] as df>.condition(<#visit df/>)</#list><#list .node["econdition"] as ec>.condition(<#visit ec/>)</#list><#list .node["econditions"] as ecs>.condition(<#visit ecs/>)</#list><#list .node["econdition-object"] as eco>.condition(<#visit eco/>)</#list>.deleteAll()
</#macro>
<#macro "entity-set">    ${.node["@value-field"]}.setFields(${.node["@map"]?default("context")}, ${.node["@set-if-empty"]?default("true")}, ${.node["@prefix"]?default("null")}, <#if .node["@include"]?has_content && .node["@include"] == "pk">true<#elseif .node["@include"]?has_content && .node["@include"] == "nonpk"/>false<#else/>null</#if>)
</#macro>

<#macro iterate>    if (${.node["@list"]} instanceof Map) {
        for (def ${.node["@entry"]}Entry in ${.node["@list"]}.entrySet()) {
            def ${.node["@entry"]} = ${.node["@entry"]}Entry.getKey()
            <#if .node["@key"]?has_content>def ${.node["@key"]} = ${.node["@entry"]}Entry.getValue()</#if>
        <#recurse/>
        }
    } else if (${.node["@list"]} instanceof Collection<Map.Entry>) {
        for (def ${.node["@entry"]}Entry in ${.node["@list"]}) {
            def ${.node["@entry"]} = ${.node["@entry"]}Entry.getKey()
            <#if .node["@key"]?has_content>def ${.node["@key"]} = ${.node["@entry"]}Entry.getValue()</#if>
        <#recurse/>
        }
    } else {
        for (def ${.node["@entry"]} in ${.node["@list"]}) {
        <#recurse/>
        }
        if (${.node["@list"]} instanceof EntityListIterator) ${.node["@list"]}.close()
    }
</#macro>
<#macro message><#if .node["@error"]?has_content && .node["@error"] == "true">    ec.message.addError("""${.node?trim}""")<#else/>    ec.message.addMessage("""${.node?trim}""")</#if>
</#macro>
<#macro "check-errors">    if (ec.message.errors) return
</#macro>

<#-- NOTE: if there is an error message (in ec.messages.errors) then the actions result is an error, otherwise it is not, so we need a default error message here -->
<#macro return><#assign returnMessage = .node["@message"]?default("Error in actions")/><#if .node["@error"]?has_content && .node["@error"] == "true">    ec.message.addError("""${returnMessage?trim}""")<#else/>    ec.message.addMessage(${returnMessage?trim})</#if>
    return</#macro>
<#macro assert><#list .node["*"] as childCond>
    if (!(<#visit childCond/>)) ec.message.addError("""<#if .node["@title"]?has_content>[${.node["@title"]}] </#if> Assert failed: <#visit childCond/>""")</#list>
</#macro>

<#macro "xml-consume">
    // TODO impl xml-consume
</#macro>
<#macro "xml-consume-element">
    // TODO impl xml-consume-element
</#macro>
<#macro "xml-produce">
    // TODO impl xml-produce
</#macro>
<#macro "xml-produce-element">
    // TODO impl xml-produce-element
</#macro>

<#macro if>    if (<#if .node["@condition"]?has_content>${.node["@condition"]}</#if><#if .node["@condition"]?has_content && .node["condition"]?has_content> && </#if><#if .node["condition"]?has_content><#recurse .node["condition"][0]/></#if>) {<#recurse .node/><#if .node.then?has_content>
    <#recurse .node.then/></#if>
    }<#if .node["else-if"]?has_content><#list .node["else-if"] as elseIf> else if (<#if elseIf["@condition"]?has_content>${elseIf["@condition"]}</#if><#if elseIf["@condition"]?has_content && elseIf["condition"]?has_content> && </#if><#if elseIf["condition"]?has_content><#recurse elseIf["condition"][0]/></#if>) {
    <#recurse elseIf/><#if elseIf.then?has_content>
    <#recurse elseIf.then/></#if>
    }</#list></#if><#if .node["else"]?has_content> else {
    <#recurse .node["else"][0]/>
    }</#if></#macro>

<#macro while>    while (<#if .node.@condition?has_content>${.node.@condition}</#if><#if .node["@condition"]?has_content && .node["condition"]?has_content> && </#if><#if .node["condition"]?has_content><#recurse .node["condition"][0]/></#if>) {<#recurse .node/>
    }</#macro>

<#-- =================== if/when sub-elements =================== -->
<#macro condition><#-- do nothing when visiting, only used explicitly inline --></#macro>
<#macro then><#-- do nothing when visiting, only used explicitly inline --></#macro>
<#macro "else-if"><#-- do nothing when visiting, only used explicitly inline --></#macro>
<#macro else><#-- do nothing when visiting, only used explicitly inline --></#macro>

<#macro or>(<#list .node.children as childNode><#visit childNode/><#if childNode_has_next> || </#if></#list>)</#macro>
<#macro and>(<#list .node.children as childNode><#visit childNode/><#if childNode_has_next> && </#if></#list>)</#macro>
<#macro not>!<#visit .node.children[0]/></#macro>

<#macro "compare">    <#if (.node?size > 0)>if (StupidUtilities.compare(${.node["@field"]}, <#if .node["@operator"]?has_content>"${.node["@operator"]}"<#else/>"equals"</#if>, <#if .node["@value"]?has_content>"""${.node["@value"]}"""<#else/>null</#if>, <#if .node["@to-field"]?has_content>${.node["@to-field"]}<#else/>null</#if>, <#if .node["@format"]?has_content>"${.node["@format"]}"<#else/>null</#if>, <#if .node["@type"]?has_content>"${.node["@type"]}"<#else/>"Object"</#if>)) {
        <#recurse .node/>
    }<#if .node.else?has_content> else {
        <#recurse .node.else[0]/>
    }</#if>
    <#else/>StupidUtilities.compare(${.node["@field"]}, <#if .node["@operator"]?has_content>"${.node["@operator"]}"<#else/>"equals"</#if>, <#if .node["@value"]?has_content>"""${.node["@value"]}"""<#else/>null</#if>, <#if .node["@to-field"]?has_content>${.node["@to-field"]}<#else/>null</#if>, <#if .node["@format"]?has_content>"${.node["@format"]}"<#else/>null</#if>, <#if .node["@type"]?has_content>"${.node["@type"]}"<#else/>"Object"</#if>)</#if>
</#macro>
<#macro "expression">${.node}
</#macro>

<#-- =================== other elements =================== -->
<#macro "log">    ec.logger.log(<#if .node["@level"]?has_content>"${.node["@level"]}"<#else/>"info"</#if>, """${.node["@message"]}""", null)
</#macro>
