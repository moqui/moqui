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
    <#if .node["@out-map"]?has_content && .node.@async == "false">${.node["@out-map"]} =</#if>
            ec.service.<#if .node.@async?has_content && .node.@async != "false">async()<#else/>sync()</#if>.name("${.node.@name}")
            <#if .node["@in-map"]?has_content>.parameters(${.node["@in-map"]})</#if>
            <#list .node["field-map"] as fieldMap>.parameter(${fieldMap["@field-name"]}, <#if fieldMap["@from-field"]?has_content>${fieldMap["@from-field"]}<#else><#if fieldMap.@value?has_content>"""${fieldMap.@value}"""<#else/>${fieldMap["@field-name"]}</#if></#if>)</#list>
            .call()
    <#-- TODO handle .node.@async == "persist" -->
</#macro>

<#macro "call-script">
    <#-- TODO handle location attribute, and load/include that script too (xml or groovy) -->
    ${.node}
</#macro>

<#macro set><#-- TODO .node.@set-if-empty -->
    ${.node.@field} = <#if .node["@from-field"]?has_content>${.node["@from-field"]}<#else>"""${.node.@value}"""</#if><#if .node["@default-value"]?has_content> ?: ${.node["@default-value"]}</#if><#if .node.@type?has_content> as ${.node.@type}</#if>
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
    // TODO impl
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
    // TODO impl
</#macro>
<#macro "entity-create">
    // TODO impl
</#macro>
<#macro "entity-update">
    // TODO impl
</#macro>
<#macro "entity-delete">
    // TODO impl
</#macro>
<#macro "entity-delete-related">
    // TODO impl
</#macro>
<#macro "entity-delete-by-econdition">
    // TODO impl
</#macro>
<#macro "entity-set">
    // TODO impl
</#macro>

<#macro iterate>
    // TODO impl
</#macro>
<#macro message>
    // TODO impl
</#macro>
<#macro "check-errors">
    // TODO impl
</#macro>
<#macro return>
    // TODO impl
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
    if (<#if .node.@condition?has_content>${.node.@condition}</#if>
            <#-- TODO all condition sub-elements -->
        ) {
        <#recurse>
        <#-- TODO all then sub-elements -->
    }
    <#-- TODO all else-if sub-elements -->
    <#-- TODO all else sub-elements -->
</#macro>
<#macro while>
    while (<#if .node.@condition?has_content>${.node.@condition}</#if>
            <#-- TODO all condition sub-elements -->
        ) {
        <#recurse>
    }
</#macro>
<#macro compare>
    // TODO impl
</#macro>

<#macro "check-id">
    // TODO impl
</#macro>
<#macro log>
    // TODO impl
</#macro>
