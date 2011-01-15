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
// import org.moqui.context.ExecutionContext
// ExecutionContext ec
// Map<String, Object> context
// Map<String, Object> result
<#recurse doc/>

<#macro actions>
<#recurse/>
</#macro>

<#macro "call-service">
    <#-- TODO should we handle out-map?has_content and async!=false? -->
    <#if .node["@out-map"]?has_content && (!.node["@async"]?has_content || .node["@async"] == "false")>${.node["@out-map"]} =</#if>
            ec.service.<#if .node.@async?has_content && .node.@async != "false">async()<#else/>sync()</#if>.name("${.node.@name}")
            <#if .node["@in-map"]?has_content>.parameters(${.node["@in-map"]})</#if><#list .node["field-map"] as fieldMap>.parameter(${fieldMap["@field-name"]}, <#if fieldMap["@from-field"]?has_content>${fieldMap["@from-field"]}<#else><#if fieldMap.@value?has_content>"""${fieldMap.@value}"""<#else/>${fieldMap["@field-name"]}</#if></#if>)</#list>.call()
    <#-- TODO handle .node.@async == "persist" -->
</#macro>

<#macro "call-script">
    <#-- TODO handle location attribute, and load/include that script too (xml or groovy) -->
    ${.node}
</#macro>

<#macro set>
    <#if .node["@set-if-empty"] == "false">
    ${.node["@field"]}_temp_internal = <#if .node["@from-field"]?has_content>${.node["@from-field"]}<#else>"""${.node.@value}"""</#if><#if .node["@default-value"]?has_content> ?: ${.node["@default-value"]}</#if><#if .node["@type"]?has_content> as ${.node["@type"]}
    if (${.node["@field"]}_temp_internal) ${.node["@field"]} = ${.node["@field"]}_temp_internal
    <#else/>
    ${.node["@field"]} = <#if .node["@from-field"]?has_content>${.node["@from-field"]}<#else>"""${.node["@value"]}"""</#if><#if .node["@default-value"]?has_content> ?: ${.node["@default-value"]}</#if><#if .node["@type"]?has_content> as ${.node["@type"]}</#if>
    </#if>
</#macro>

<#macro "order-map-list">
    // TODO impl
</#macro>
<#macro "filter-map-list">
    // TODO impl
</#macro>

<#macro "entity-sequenced-id-primary">
    // TODO impl
</#macro>
<#macro "entity-sequenced-id-secondary">
    // TODO impl
</#macro>
<#macro "entity-data">
    // TODO impl
</#macro>

<#macro "entity-find-one">
    ${.node["@value-field"]} = ec.entity.find("${.node["@entity-name"]}")<#if .node["@cache"]?has_content>.useCache(${.node["@cache"]})</#if><#if .node["@for-update"]?has_content>.forUpdate(${.node["@for-update"]})</#if>
            <#if !.node["@auto-field-map"]?has_content || .node["@auto-field-map"] == "true">.condition(context)</#if><#list .node["field-map"] as fieldMap>.condition(${fieldMap["@field-name"]}, <#if fieldMap["@from-field"]?has_content>${fieldMap["@from-field"]}<#else><#if fieldMap["@value"]?has_content>"""${fieldMap["@value"]}"""<#else/>${fieldMap["@field-name"]}</#if></#if>)</#list><#list .node["@select-field"] as sf>.selectField('${sf["@field-name"]}')</#list>.one()
</#macro>
<#macro "entity-find">
    // TODO impl
</#macro>
<#macro "entity-find-count">
    // TODO impl
</#macro>

<#macro "entity-find-related-one">
    // TODO impl
</#macro>
<#macro "entity-find-related">
    // TODO impl
</#macro>

<#macro "entity-make-value">
    ${.node["@value-field"]} = ec.entity.makeValue(${.node["@entity-name"]})
    <#if .node["@map"]?has_content>${.node["@value-field"]}.setFields(${.node["@map"]}, true, null, null)</#if>
</#macro>
<#macro "entity-create">
    ${.node["@value-field"]}<#if .node["@or-update"]?has_content && .node["@or-update"] == "true">.createOrUpdate()<#else/>.create()</#if>
</#macro>
<#macro "entity-update">
    ${.node["@value-field"]}.update()
</#macro>
<#macro "entity-delete">
    ${.node["@value-field"]}.delete()
</#macro>
<#macro "entity-delete-related">
    ${.node["@value-field"]}.deleteRelated(${.node["@relationship-name"]})
</#macro>
<#macro "entity-delete-by-condition">
    // TODO impl
</#macro>
<#macro "entity-set">
    ${.node["@value-field"]}.setFields(${.node["@map"]}, ${.node["@set-if-empty"]?default("true")}, ${.node["@prefix"]?default("null")}, <#if .node["@include"]?has_content && .node["@include"] == "pk">true<#elseif .node["@include"]?has_content && .node["@include"] == "nonpk"/>false<#else/>null</#if>)
</#macro>

<#macro iterate>
    if (${.node["@list"]} instanceof Map) {
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
    }
</#macro>
<#macro message>
    <#if .node["@error"]?has_content && .node["@error"] == "true">ec.message.addError("""${.node}""")<#else/>ec.message.addMessage("""${.node}""")</#if>
</#macro>
<#macro "check-errors">
    if (ec.message.errors) return
</#macro>
<#macro return>
    <#-- NOTE: if there is an error message (in ec.messages.errors) then the actions result is an error, otherwise it is not, so we need a default error message here -->
    <#assign returnMessage = """${.node["@message"]?default("Error in actions")}"""/>
    <#if .node["@error"]?has_content && .node["@error"] == "true">ec.message.addError(${returnMessage})<#else/>ec.message.addMessage(${returnMessage})</#if>
</#macro>
<#macro assert>
    // TODO impl
</#macro>

<#macro "xml-consume">
    // TODO impl
</#macro>
<#macro "xml-consume-element">
    // TODO impl
</#macro>
<#macro "xml-produce">
    // TODO impl
</#macro>
<#macro "xml-produce-element">
    // TODO impl
</#macro>

<#macro if>
    if (<#if .node.@condition?has_content>${.node.@condition}</#if>) {
        <#-- TODO all condition sub-elements -->
        <#recurse>
        <#-- TODO all then sub-elements -->
    }
    <#-- TODO all else-if sub-elements -->
    <#-- TODO all else sub-elements -->
</#macro>
<#macro while>
    while (<#if .node.@condition?has_content>${.node.@condition}</#if>) {
        <#-- TODO all condition sub-elements -->
        <#recurse>
    }
</#macro>
<#macro compare>
    <#-- if the node has children then consider it a stand-alone node, otherwise consider it part of an if.condition -->
    <#if .node.children?has_content>
    if (ec.compareInContext(${.node["@field"]}, ${.node["@operator"]?default("equals")}, ${.node["@value"]?if_exists}, ${.node["@to-field"]?if_exists}, ${.node["@format"]?if_exists}, ${.node["@type"]?default("Object")})) {
        <#recurse/>
    } <#if .node.else?has_content>else {
        <#recurse .node.else[0]/>
    }</#if>
    <#else/>ec.compareInContext(${.node["@field"]}, ${.node["@operator"]?default("equals")}, ${.node["@value"]?if_exists}, ${.node["@to-field"]?if_exists}, ${.node["@format"]?if_exists}, ${.node["@type"]?default("Object")})</#if>
</#macro>
<#macro expression>
    ${.node}
</#macro>

<#macro "check-id">
    // TODO impl
</#macro>
<#macro log>
    // TODO impl
</#macro>
