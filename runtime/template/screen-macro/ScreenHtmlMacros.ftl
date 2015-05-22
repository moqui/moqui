<#--
This is free and unencumbered software released into the public domain.
For specific language governing permissions and limitations refer to
the LICENSE.md file or http://unlicense.org
-->

<#include "DefaultScreenMacros.html.ftl"/>

<#macro container>
    <#assign divId><@nodeId .node/></#assign>
    <${.node["@type"]!"div"}<#if divId??> id="${divId}"</#if><#if .node["@style"]?has_content> class="${ec.resource.evaluateStringExpand(.node["@style"], "")}"</#if>><#recurse>
    </${.node["@type"]!"div"}><!-- CONTAINER OVERRIDE EXAMPLE -->
</#macro>
