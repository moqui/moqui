<#--
This is free and unencumbered software released into the public domain.
For specific language governing permissions and limitations refer to
the LICENSE.md file or http://unlicense.org
-->
    <#assign dynamic = false>
    <#assign dynamicActive = 0>
    <#assign baseUrlInfo = sri.buildUrlInfo("AutoEditDetail")>
    <div id="auto-menu">
        <ul id="auto-edit-tabs" class="nav nav-tabs" role="tablist">
            <#assign urlInstance = sri.buildUrlInfo("AutoEditMaster").getInstance(sri, false).addParameter("aen", aen).addParameters(masterPrimaryKeyMap)>
            <#if dynamic>
                <#assign urlInstance = urlInstance.addParameter("lastStandalone", "true")>
                <#if urlInstance.inCurrentScreenPath>
                    <#assign dynamicActive = 0>
                    <#assign urlInstance = urlInstance.addParameters(ec.web.requestParameters)>
                </#if>
            </#if>
            <li class="<#if urlInstance.inCurrentScreenPath>active</#if>"><a href="${urlInstance.minimalPathUrlWithParams}">${ec.entity.getEntityDefinition(aen).getPrettyName(null, null)}</a></li>
        <#list relationshipInfoList as relationshipInfo>
            <#assign curKeyMap = relationshipInfo.getTargetParameterMap(context)>
            <#if curKeyMap?has_content>
                <#assign urlInstance = baseUrlInfo.getInstance(sri, false).addParameters(masterPrimaryKeyMap).addParameter("den", relationshipInfo.relatedEntityName).addParameter("aen", aen).addParameters(curKeyMap)>
                <#if dynamic>
                    <#assign urlInstance = urlInstance.addParameter("lastStandalone", "true")>
                    <#if urlInstance.inCurrentScreenPath && relationshipInfo.relatedEntityName == den!>
                        <#assign dynamicActive = relationshipInfo_index + 1>
                        <#assign urlInstance = urlInstance.addParameters(ec.web.requestParameters)>
                    </#if>
                </#if>
                <li class="<#if urlInstance.inCurrentScreenPath && relationshipInfo.relatedEntityName == den!>active</#if>"><a href="${urlInstance.minimalPathUrlWithParams}">${relationshipInfo.prettyName}</a></li>
            </#if>
        </#list>
        </ul>
    <#if !dynamic>
        ${sri.renderSubscreen()}
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
