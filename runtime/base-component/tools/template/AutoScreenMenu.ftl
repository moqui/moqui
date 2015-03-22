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
    <#assign dynamic = false>
    <#assign dynamicActive = 0>
    <#assign baseUrlInfo = sri.buildUrl("AutoEditDetail").addParameters(masterPrimaryKeyMap)>
    <div id="auto-menu">
        <ul id="auto-edit-tabs" class="nav nav-tabs" role="tablist">
            <#assign urlInfo = sri.buildUrl("AutoEditMaster").addParameter("aen", aen).addParameters(masterPrimaryKeyMap)>
            <#if dynamic>
                <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                <#if urlInfo.inCurrentScreenPath>
                    <#assign dynamicActive = 0>
                    <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                </#if>
            </#if>
            <li class="<#if urlInfo.inCurrentScreenPath>active</#if>"><a href="${urlInfo.minimalPathUrlWithParams}">${ec.entity.getEntityDefinition(aen).getPrettyName(null, null)}</a></li>
        <#list relationshipInfoList as relationshipInfo>
            <#assign curKeyMap = relationshipInfo.getTargetParameterMap(context)>
            <#if curKeyMap?has_content>
                <#assign urlInfo = baseUrlInfo.cloneUrlInfo().addParameter("den", relationshipInfo.relatedEntityName).addParameter("aen", aen).addParameters(curKeyMap)>
                <#if dynamic>
                    <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                    <#if urlInfo.inCurrentScreenPath && relationshipInfo.relatedEntityName == den?if_exists>
                        <#assign dynamicActive = relationshipInfo_index + 1>
                        <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                    </#if>
                </#if>
                <li class="<#if urlInfo.inCurrentScreenPath && relationshipInfo.relatedEntityName == den?if_exists>active</#if>"><a href="${urlInfo.minimalPathUrlWithParams}">${relationshipInfo.prettyName}</a></li>
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
