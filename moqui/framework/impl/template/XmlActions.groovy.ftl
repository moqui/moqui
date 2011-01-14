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
<#recurse doc>

<#macro call-service>
    <#if .node.@out-map>${.node.@out-map} =</#if>
            ec.service.<#if .node.@async == "false">sync()<#else/>async()</#if>.name("${.node.@service}")
            <#if .node.@in-map>.parameters(${.node.@in-map})</#if>
</#macro>

<#macro set>
    <#-- TODO .node.@set-if-empty -->
    ${.node.@field} = <#if .node.@from-field>${.node.@from-field}<#elseif .node.@value>"""${.node.@value}"""</#if>
            <#if .node.@default-value>?: ${.node.@default-value}</#if><#if .node.@type> as ${.node.@type}</#if>
</#macro>

<#macro if>
    if (<#if .node.@condition>${.node.@condition}</#if>
            <#-- TODO all condition sub-elements -->
        ) {
        <#recurse>
    }
</#macro>
