    <#assign dynamic = true>
    <#assign dynamicActive = 0>
    <div id="auto-menu" class="ui-tabs ui-tabs-collapsible">
        <ul id="auto-edit-tabs" class="ui-tabs-nav ui-helper-clearfix ui-widget-header ui-corner-all">
            <#assign urlInfo = sri.buildUrl("AutoEditMaster").addParameter("entityName", entityName).addParameters(masterPrimaryKeyMap)>
            <#if dynamic>
                <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                <#if urlInfo.inCurrentScreenPath>
                    <#assign dynamicActive = 0>
                    <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                </#if>
            </#if>
            <li class="ui-state-default ui-corner-top<#if urlInfo.inCurrentScreenPath> ui-tabs-selected ui-state-active</#if>"><a href="${urlInfo.minimalPathUrlWithParams}"><span>${ec.entity.getEntityDefinition(entityName).getPrettyName(null, null)}</span></a></li>
        <#list relationshipInfoList as relationshipInfo>
            <#assign urlInfo = sri.buildUrl("AutoEditDetail").addParameter("detailEntityName", relationshipInfo.relatedEntityName).addParameter("entityName", entityName).addParameters(relationshipInfo.targetParameterMap)>
            <#if dynamic>
                <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                <#if urlInfo.inCurrentScreenPath && relationshipInfo.relatedEntityName == detailEntityName?if_exists>
                    <#assign dynamicActive = relationshipInfo_index + 1>
                    <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                </#if>
            </#if>
            <li class="ui-state-default ui-corner-top<#if urlInfo.inCurrentScreenPath && relationshipInfo.relatedEntityName == detailEntityName?if_exists> ui-tabs-selected ui-state-active</#if>"><a href="${urlInfo.minimalPathUrlWithParams}"><span>${relationshipInfo.prettyName}</span></a></li>
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
        $("#auto-menu").tabs({ collapsible: true, selected: ${dynamicActive}, spinner: '<span class="ui-loading">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>',
            ajaxOptions: { error: function(xhr, status, index, anchor) { $(anchor.hash).html("Error loading screen..."); } }
        });
        </script>
    </#if>
