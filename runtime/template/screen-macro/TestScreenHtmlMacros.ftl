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

<#include "DefaultScreenMacros.html.ftl"/>

<#macro container>
    <#assign tagName = .node["@type"]!"div">
    <#assign divId><#if .node["@id"]?has_content>${ec.resource.evaluateStringExpand(.node["@id"], "")}<#if listEntryIndex?has_content>_${listEntryIndex}</#if></#if></#assign>
    <${tagName}<#if divId??> id="${divId}"</#if><#if .node["@style"]?has_content> class="${.node["@style"]}"</#if>><#recurse>
    </${tagName}><!-- CONTAINER OVERRIDE FOR THE Example.xml screen -->
</#macro>
