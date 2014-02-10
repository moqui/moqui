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
    <#assign dynamic = true>
    <#assign dynamicActive = 0>
    <div id="auto-menu" class="ui-tabs ui-tabs-collapsible">
        <ul id="auto-edit-tabs" class="ui-tabs-nav ui-helper-clearfix ui-widget-header ui-corner-all">
            <#assign urlInfo = sri.buildUrl("AutoEditMaster").addParameter("aen", aen).addParameters(masterPrimaryKeyMap)>
            <#if dynamic>
                <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                <#if urlInfo.inCurrentScreenPath>
                    <#assign dynamicActive = 0>
                    <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                </#if>
            </#if>
            <li class="ui-state-default ui-corner-top<#if urlInfo.inCurrentScreenPath> ui-tabs-selected ui-state-active</#if>"><a href="${urlInfo.minimalPathUrlWithParams}"><span>${ec.entity.getEntityDefinition(aen).getPrettyName(null, null)}</span></a></li>
        <#list relationshipInfoList as relationshipInfo>
            <#assign urlInfo = sri.buildUrl("AutoEditDetail").addParameter("den", relationshipInfo.relatedEntityName).addParameter("aen", aen).addParameters(relationshipInfo.targetParameterMap)>
            <#if dynamic>
                <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                <#if urlInfo.inCurrentScreenPath && relationshipInfo.relatedEntityName == den?if_exists>
                    <#assign dynamicActive = relationshipInfo_index + 1>
                    <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                </#if>
            </#if>
            <li class="ui-state-default ui-corner-top<#if urlInfo.inCurrentScreenPath && relationshipInfo.relatedEntityName == den?if_exists> ui-tabs-selected ui-state-active</#if>"><a href="${urlInfo.minimalPathUrlWithParams}"><span>${relationshipInfo.prettyName}</span></a></li>
        </#list>
        </ul>
        <#if !dynamic>
            <div id="auto-menu-active" class="ui-tabs-panel">
            ${sri.renderSubscreen()}
            </div>
        </#if>
    </div>
    <#if dynamic>
        <script>
        $("#auto-menu").tabs({ collapsible: true, selected: ${dynamicActive},
            spinner: '<span class="ui-loading">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>',
            ajaxOptions: { error: function(xhr, status, index, anchor) { $(anchor.hash).html("Error loading screen..."); } },
            load: function(event, ui) { <#-- activateAllButtons(); --> }
        });
        </script>
    </#if>
