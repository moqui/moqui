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

<#macro @element><p>=== Doing nothing for element ${.node?node_name}, not yet implemented. ===</p></#macro>

<#macro widgets>
<#if sri.doBoundaryComments()><!-- BEGIN screen[@location=${sri.getActiveScreenDef().location}].widgets --></#if>
<#recurse>
<#if sri.doBoundaryComments()><!-- END   screen[@location=${sri.getActiveScreenDef().location}].widgets --></#if>
</#macro>
<#macro "fail-widgets">
<#if sri.doBoundaryComments()><!-- BEGIN screen[@location=${sri.getActiveScreenDef().location}].fail-widgets --></#if>
<#recurse>
<#if sri.doBoundaryComments()><!-- END   screen[@location=${sri.getActiveScreenDef().location}].fail-widgets --></#if>
</#macro>

<#-- ================ Subscreens ================ -->
<#macro "subscreens-menu">
    <#assign displayMenu = sri.activeInCurrentMenu?if_exists>
    <#assign menuId = .node["@id"]!"subscreensMenu">
    <#if .node["@type"]?if_exists == "popup">
        <#assign menuTitle = .node["@title"]!sri.getActiveScreenDef().getDefaultMenuName()!"Menu">
        <ul id="${menuId}"<#if .node["@width"]?has_content> style="width: ${.node["@width"]};"</#if>>
            <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem>
                <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                <#if urlInfo?exists && urlInfo.inCurrentScreenPath><#assign currentItemName = ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)></#if>
            </#list>
            <li><a href="#">${menuTitle}<#-- very usable without this: <#if currentItemName?has_content> (${currentItemName})</#if> --></a>
                <ul>
                    <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem><#if subscreensItem.menuInclude>
                        <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                        <#if urlInfo.isPermitted()>
                            <li class="<#if urlInfo.inCurrentScreenPath>ui-state-active</#if>"><a href="<#if urlInfo.disableLink>#<#else>${urlInfo.minimalPathUrlWithParams}</#if>">${ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)}</a></li>
                        </#if>
                    </#if></#list>
                </ul>
            </li>
        </ul>
        <#-- NOTE: not putting this script at the end of the document so that it doesn't appear unstyled for as long -->
        <script>
            <#-- move the menu to the header-menus container -->
            $("#${.node["@header-menus-id"]!"header-menus"}").append($("#${menuId}"));
            $("#${menuId}").menu({position: { my: "right top", at: "right bottom" }});
        </script>
    <#elseif .node["@type"]?if_exists == "popup-tree">
    <#else>
        <#-- default to type=tab -->
        <#if displayMenu?if_exists>
        <div class="ui-tabs ui-tabs-collapsible">
            <ul<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if> class="ui-tabs-nav ui-helper-clearfix ui-widget-header ui-corner-all">
                <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem><#if subscreensItem.menuInclude>
                    <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                    <#if urlInfo.isPermitted()>
                        <li class="ui-state-default ui-corner-top<#if urlInfo.inCurrentScreenPath> ui-tabs-selected ui-state-active</#if>"><#if urlInfo.disableLink>${ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)}<#else><a href="${urlInfo.minimalPathUrlWithParams}">${ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)}</a></#if></li>
                    </#if>
                </#if></#list>
            </ul>
        </div>
        </#if>
    </#if>
</#macro>

<#macro "subscreens-active">
    <#-- NOTE DEJ20130803 is there any reason to do this, or even support an id? disabling for now as it complicates things with jquery layout:
    <#if .node["@id"]?has_content>
        <div class="ui-tabs">
            <div id="${.node["@id"]}" class="ui-tabs-panel">
            ${sri.renderSubscreen()}
            </div>
        </div>
    <#else>
    -->
        ${sri.renderSubscreen()}
    <#-- </#if> -->
</#macro>

<#macro "subscreens-panel">
    <#assign dynamic = .node["@dynamic"]?if_exists == "true" && .node["@id"]?has_content>
    <#assign dynamicActive = 0>
    <#assign displayMenu = sri.activeInCurrentMenu?if_exists>
    <#if .node["@type"]?if_exists == "popup">
        <#assign menuTitle = .node["@title"]!sri.getActiveScreenDef().getDefaultMenuName()!"Menu">
        <#assign menuId><#if .node["@id"]?has_content>${.node["@id"]}-menu<#else>subscreensPanelMenu</#if></#assign>
        <ul id="${menuId}"<#if .node["@width"]?has_content> style="width: ${.node["@menu-width"]};"</#if>>
            <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem>
                <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                <#if urlInfo.inCurrentScreenPath><#assign currentItemName = ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)></#if>
            </#list>
            <li><a href="#">${menuTitle}<#-- very usable without this: <#if currentItemName?has_content> (${currentItemName})</#if> --></a>
                <ul>
                    <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem><#if subscreensItem.menuInclude>
                        <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                        <#if urlInfo.isPermitted()>
                            <li class="<#if urlInfo.inCurrentScreenPath>ui-state-active</#if>"><a href="<#if urlInfo.disableLink>#<#else>${urlInfo.minimalPathUrlWithParams}</#if>">${ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)}</a></li>
                        </#if>
                    </#if></#list>
                </ul>
            </li>
        </ul>
        <#-- NOTE: not putting this script at the end of the document so that it doesn't appear unstyled for as long -->
        <script>
            <#-- move the menu to the header menus section -->
            $("#${.node["@header-menus-id"]!"header-menus"}").append($("#${menuId}"));
            $("#${menuId}").menu({position: { my: "right top", at: "right bottom" }});
        </script>

        ${sri.renderSubscreen()}
    <#elseif .node["@type"]?if_exists == "stack">
        <h1>LATER stack type subscreens-panel not yet supported.</h1>
    <#elseif .node["@type"]?if_exists == "wizard">
        <h1>LATER wizard type subscreens-panel not yet supported.</h1>
    <#else>
        <#-- default to type=tab -->
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if> class="ui-tabs ui-tabs-collapsible">
        <#if displayMenu?if_exists>
            <ul<#if .node["@id"]?has_content> id="${.node["@id"]}-menu"</#if> class="ui-tabs-nav ui-helper-clearfix ui-widget-header ui-corner-all">
            <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem><#if subscreensItem.menuInclude>
                <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                <#if urlInfo.isPermitted()>
                    <#if dynamic>
                        <#assign urlInfo = urlInfo.addParameter("lastStandalone", "true")>
                        <#if urlInfo.inCurrentScreenPath>
                            <#assign dynamicActive = subscreensItem_index>
                            <#assign urlInfo = urlInfo.addParameters(ec.web.requestParameters)>
                        </#if>
                    </#if>
                    <li class="ui-state-default ui-corner-top<#if urlInfo.disableLink> ui-state-disabled<#elseif urlInfo.inCurrentScreenPath> ui-tabs-selected ui-state-active</#if>"><a href="<#if urlInfo.disableLink>#<#else>${urlInfo.minimalPathUrlWithParams}</#if>"><span>${subscreensItem.menuTitle}</span></a></li>
                </#if>
            </#if></#list>
            </ul>
        </#if>
        </div>
        <#if !dynamic || !displayMenu>
        <#-- these make it more similar to the HTML produced when dynamic, but not needed: <div<#if .node["@id"]?has_content> id="${.node["@id"]}-active"</#if> class="ui-tabs-panel"> -->
        ${sri.renderSubscreen()}
        <#-- </div> -->
        </#if>
        <#if dynamic && displayMenu?if_exists>
            <#assign afterScreenScript>
                $("#${.node["@id"]}").tabs({ collapsible: true, selected: ${dynamicActive},
                    spinner: '<span class="ui-loading">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>',
                    ajaxOptions: { error: function(xhr, status, index, anchor) { $(anchor.hash).html("Error loading screen..."); } },
                    load: function(event, ui) { activateAllButtons(); }
                });
            </#assign>
            <#t>${sri.appendToScriptWriter(afterScreenScript)}
        </#if>
    </#if>
</#macro>

<#-- ================ Section ================ -->
<#macro section>
    <#if sri.doBoundaryComments()><!-- BEGIN section[@name=${.node["@name"]}] --></#if>
    ${sri.renderSection(.node["@name"])}
    <#if sri.doBoundaryComments()><!-- END   section[@name=${.node["@name"]}] --></#if>
</#macro>
<#macro "section-iterate">
    <#if sri.doBoundaryComments()><!-- BEGIN section-iterate[@name=${.node["@name"]}] --></#if>
    ${sri.renderSection(.node["@name"])}
    <#if sri.doBoundaryComments()><!-- END   section-iterate[@name=${.node["@name"]}] --></#if>
</#macro>

<#-- ================ Containers ================ -->
<#macro container>    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@style"]?has_content> class="${.node["@style"]}"</#if>><#recurse>
    </div>
</#macro>

<#macro "container-panel">
    <#assign panelId = ec.resource.evaluateStringExpand(.node["@id"], "")>
    <#if .node["@dynamic"]?if_exists == "true">
        <#assign afterScreenScript>
        $("#${panelId}").layout({
        defaults: { closable: true, resizable: true, slidable: false, livePaneResizing: true, spacing_open: 5 },
        <#if .node["panel-header"]?has_content><#assign panelNode = .node["panel-header"][0]>north: { showOverflowOnHover: true, closable: ${panelNode["@closable"]!"true"}, resizable: ${panelNode["@resizable"]!"false"}, spacing_open: ${panelNode["@spacing"]!"5"}, size: "${panelNode["@size"]!"auto"}"<#if panelNode["@size-min"]?has_content>, minSize: ${panelNode["@size-min"]}</#if><#if panelNode["@size-min"]?has_content>, maxSize: ${panelNode["@size-max"]}</#if> },</#if>
        <#if .node["panel-footer"]?has_content><#assign panelNode = .node["panel-footer"][0]>south: { showOverflowOnHover: true, closable: ${panelNode["@closable"]!"true"}, resizable: ${panelNode["@resizable"]!"false"}, spacing_open: ${panelNode["@spacing"]!"5"}, size: "${panelNode["@size"]!"auto"}"<#if panelNode["@size-min"]?has_content>, minSize: ${panelNode["@size-min"]}</#if><#if panelNode["@size-min"]?has_content>, maxSize: ${panelNode["@size-max"]}</#if> },</#if>
        <#if .node["panel-left"]?has_content><#assign panelNode = .node["panel-left"][0]>west: { closable: ${panelNode["@closable"]!"true"}, resizable: ${panelNode["@resizable"]!"true"}, spacing_open: ${panelNode["@spacing"]!"5"}, size: "${panelNode["@size"]!"180"}"<#if panelNode["@size-min"]?has_content>, minSize: ${panelNode["@size-min"]}</#if><#if panelNode["@size-min"]?has_content>, maxSize: ${panelNode["@size-max"]}</#if> },</#if>
        <#if .node["panel-right"]?has_content><#assign panelNode = .node["panel-right"][0]>east: { closable: ${panelNode["@closable"]!"true"}, resizable: ${panelNode["@resizable"]!"true"}, spacing_open: ${panelNode["@spacing"]!"5"}, size: "${panelNode["@size"]!"180"}"<#if panelNode["@size-min"]?has_content>, minSize: ${panelNode["@size-min"]}</#if><#if panelNode["@size-min"]?has_content>, maxSize: ${panelNode["@size-max"]}</#if> },</#if>
        center: { minWidth: 200 }
        });
        </#assign>
        <#t>${sri.appendToScriptWriter(afterScreenScript)}
        <div<#if panelId?has_content> id="${panelId}"</#if>>
            <#if .node["panel-header"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-header"</#if> class="ui-layout-north ui-helper-clearfix"><#recurse .node["panel-header"][0]>
                </div></#if>
            <#if .node["panel-left"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-left"</#if> class="ui-layout-west"><#recurse .node["panel-left"][0]>
                </div>
            </#if>
            <div<#if panelId?has_content> id="${panelId}-center"</#if> class="ui-layout-center"><#recurse .node["panel-center"][0]>
            </div>
            <#if .node["panel-right"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-right"</#if> class="ui-layout-east"><#recurse .node["panel-right"][0]>
                </div>
            </#if>
            <#if .node["panel-footer"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-footer"</#if> class="ui-layout-south"><#recurse .node["panel-footer"][0]>
                </div></#if>
        </div>
    <#else>
        <div<#if panelId?has_content> id="${panelId}"</#if> class="panel-outer">
            <#if .node["panel-header"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-header"</#if> class="panel-header"><#recurse .node["panel-header"][0]>
                </div>
            </#if>
            <div class="panel-middle">
                <#if .node["panel-left"]?has_content>
                    <div<#if panelId?has_content> id="${panelId}-left"</#if> class="panel-left" style="width: ${.node["panel-left"][0]["@size"]!"180"}px;"><#recurse .node["panel-left"][0]>
                    </div>
                </#if>
                <#assign centerClass><#if .node["panel-left"]?has_content><#if .node["panel-right"]?has_content>panel-center-both<#else>panel-center-left</#if><#else><#if .node["panel-right"]?has_content>panel-center-right<#else>panel-center-only</#if></#if></#assign>
                <div<#if panelId?has_content> id="${panelId}-center"</#if> class="${centerClass}"><#recurse .node["panel-center"][0]>
            </div>
            <#if .node["panel-right"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-right"</#if> class="panel-right" style="width: ${.node["panel-right"][0]["@size"]!"180"}px;"><#recurse .node["panel-right"][0]>
                </div>
            </#if>
            </div>
            <#if .node["panel-footer"]?has_content>
                <div<#if panelId?has_content> id="${panelId}-footer"</#if> class="panel-footer"><#recurse .node["panel-footer"][0]>
                </div>
            </#if>
        </div>
    </#if>
</#macro>

<#macro "container-dialog">
    <#assign buttonText = ec.resource.evaluateStringExpand(.node["@button-text"], "")>
    <button id="${.node["@id"]}-button" iconcls="ui-icon-newwin">${buttonText}</button>
    <div id="${.node["@id"]}" title="${buttonText}" style="display: none;">
    <#recurse>
    </div>
    <#assign afterScreenScript>
        $("#${.node["@id"]}").dialog({autoOpen:false, height:${.node["@height"]!"600"}, width:${.node["@width"]!"600"}, modal:false });
        <#--, buttons: { Close: function() { $(this).dialog("close"); } } -->
        <#--, close: function() { } -->
        $("#${.node["@id"]}-button").click(function() { $("#${.node["@id"]}").dialog("open"); });
    </#assign>
    <#t>${sri.appendToScriptWriter(afterScreenScript)}
</#macro>

<#macro "dynamic-container">
    <#assign divId>${.node["@id"]}<#if listEntryIndex?has_content>_${listEntryIndex}</#if></#assign>
    <#assign urlInfo = sri.makeUrlByType(.node["@transition"], "transition", .node, "true").addParameter("_dynamic_container_id", divId)>
    <div id="${divId}"><img src="/images/wait_anim_16x16.gif" alt="Loading..."></div>
    <#assign afterScreenScript>
        function load${divId}() { $("#${divId}").load("${urlInfo.passThroughSpecialParameters().urlWithParams}", function() { activateAllButtons() }) }
        load${divId}();
    </#assign>
    <#t>${sri.appendToScriptWriter(afterScreenScript)}
</#macro>

<#macro "dynamic-dialog">
    <#assign buttonText = ec.resource.evaluateStringExpand(.node["@button-text"], "")>
    <#assign urlInfo = sri.makeUrlByType(.node["@transition"], "transition", .node, "true")>
    <#assign divId>${ec.resource.evaluateStringExpand(.node["@id"], "")}<#if listEntryIndex?has_content>_${listEntryIndex}</#if></#assign>
    <button id="${divId}Button" iconcls="ui-icon-newwin">${buttonText}</button>
    <div id="${divId}" title="${buttonText}"></div>
    <#assign afterScreenScript>
        $("#${divId}").dialog({autoOpen:false, height:${.node["@height"]!"600"}, width:${.node["@width"]!"600"},
            modal:false, open: function() { $(this).load('${urlInfo.urlWithParams}', function() { activateAllButtons() }) } });
        $("#${divId}Button").click(function() { $("#${divId}").dialog("open"); return false; });
    </#assign>
    <#t>${sri.appendToScriptWriter(afterScreenScript)}
</#macro>

<#-- ==================== Includes ==================== -->
<#macro "include-screen">
<#if sri.doBoundaryComments()><!-- BEGIN include-screen[@location=${.node["@location"]}][@share-scope=${.node["@share-scope"]?if_exists}] --></#if>
${sri.renderIncludeScreen(.node["@location"], .node["@share-scope"]?if_exists)}
<#if sri.doBoundaryComments()><!-- END   include-screen[@location=${.node["@location"]}][@share-scope=${.node["@share-scope"]?if_exists}] --></#if>
</#macro>

<#-- ============== Tree ============== -->
<#-- TABLED, not to be part of 1.0:
<#macro tree>
</#macro>
<#macro "tree-node">
</#macro>
<#macro "tree-sub-node">
</#macro>
-->

<#-- ============== Render Mode Elements ============== -->
<#macro "render-mode">
<#if .node["text"]?has_content>
    <#list .node["text"] as textNode>
        <#if textNode["@type"]?has_content && textNode["@type"] == sri.getRenderMode()><#assign textToUse = textNode/></#if>
    </#list>
    <#if !textToUse?has_content>
        <#list .node["text"] as textNode><#if !textNode["@type"]?has_content || textNode["@type"] == "any"><#assign textToUse = textNode/></#if></#list>
    </#if>
    <#if textToUse?exists>
        <#if textToUse["@location"]?has_content>
        <#assign textLocation = ec.resource.evaluateStringExpand(textToUse["@location"], "")>
<#if sri.doBoundaryComments()><!-- BEGIN render-mode.text[@location=${textLocation}][@template=${textToUse["@template"]?default("true")}] --></#if>
    <#-- NOTE: this still won't encode templates that are rendered to the writer -->
    <#if .node["@encode"]!"false" == "true">${sri.renderText(textLocation, textToUse["@template"]?if_exists)?html}<#else>${sri.renderText(textLocation, textToUse["@template"]?if_exists)}</#if>
<#if sri.doBoundaryComments()><!-- END   render-mode.text[@location=${textLocation}][@template=${textToUse["@template"]?default("true")}] --></#if>
        </#if>
        <#assign inlineTemplateSource = textToUse?string/>
        <#if inlineTemplateSource?has_content>
<#if sri.doBoundaryComments()><!-- BEGIN render-mode.text[inline][@template=${textToUse["@template"]?default("true")}] --></#if>
          <#if !textToUse["@template"]?has_content || textToUse["@template"] == "true">
            <#assign inlineTemplate = [inlineTemplateSource, sri.getActiveScreenDef().location + ".render_mode.text"]?interpret>
            <@inlineTemplate/>
          <#else>
            <#if .node["@encode"]!"false" == "true">${inlineTemplateSource?html}<#else>${inlineTemplateSource}</#if>
          </#if>
<#if sri.doBoundaryComments()><!-- END   render-mode.text[inline][@template=${textToUse["@template"]?default("true")}] --></#if>
        </#if>
    </#if>
</#if>
</#macro>

<#macro text><#-- do nothing, is used only through "render-mode" --></#macro>

<#-- ================== Standalone Fields ==================== -->
<#macro link>
    <#assign urlInfo = sri.makeUrlByType(.node["@url"], .node["@url-type"]!"transition", .node, .node["@expand-transition-url"]!"true")>
    <#assign linkNode = .node>
    <@linkFormForm linkNode linkNode["@id"]?if_exists urlInfo/>
    <@linkFormLink linkNode linkNode["@id"]?if_exists urlInfo/>
</#macro>
<#macro linkFormLink linkNode linkFormId urlInfo>
    <#if urlInfo.disableLink>
        <span<#if linkFormId?has_content> id="${linkFormId}"</#if>>${ec.resource.evaluateStringExpand(linkNode["@text"], "")}</span>
    <#else>
        <#assign confirmationMessage = ec.resource.evaluateStringExpand(linkNode["@confirmation"]?if_exists, "")/>
        <#if (linkNode["@link-type"]?if_exists == "anchor" || linkNode["@link-type"]?if_exists == "anchor-button") ||
            ((!linkNode["@link-type"]?has_content || linkNode["@link-type"] == "auto") &&
             ((linkNode["@url-type"]?has_content && linkNode["@url-type"] != "transition") || (!urlInfo.hasActions)))>
            <a href="${urlInfo.urlWithParams}"<#if linkFormId?has_content> id="${linkFormId}"</#if><#if linkNode["@target-window"]?has_content> target="${linkNode["@target-window"]}"</#if><#if confirmationMessage?has_content> onclick="return confirm('${confirmationMessage?js_string}')"</#if><#if linkNode["@link-type"]?if_exists == "anchor-button"> class="button"</#if><#if linkNode["@icon"]?has_content> iconcls="ui-icon-${linkNode["@icon"]}"</#if>>
            <#t><#if linkNode["image"]?has_content><#visit linkNode["image"]><#else>${ec.resource.evaluateStringExpand(linkNode["@text"], "")}</#if>
            <#t></a>
        <#else>
            <#if linkFormId?has_content>
            <button type="submit" form="${linkFormId}"<#if linkNode["@icon"]?has_content> iconcls="ui-icon-${linkNode["@icon"]}"</#if><#if confirmationMessage?has_content> onclick="return confirm('${confirmationMessage?js_string}')"</#if><#if linkNode["@link-type"]?if_exists == "hidden-form-link"> class="button-plain"</#if>>
                <#if linkNode["image"]?has_content>
                    <#t><img src="${sri.makeUrlByType(imageNode["@url"],imageNode["@url-type"]!"content",null,"true")}"<#if imageNode["@alt"]?has_content> alt="${imageNode["@alt"]}"</#if>/>
                <#else>
                    <#t>${ec.resource.evaluateStringExpand(linkNode["@text"], "")}
                </#if>
            </button>
            </#if>
        </#if>
    </#if>
</#macro>
<#macro linkFormForm linkNode linkFormId urlInfo>
    <#if urlInfo.disableLink>
        <#-- do nothing -->
    <#else>
        <#if (linkNode["@link-type"]?if_exists == "anchor" || linkNode["@link-type"]?if_exists == "anchor-button") ||
            ((!linkNode["@link-type"]?has_content || linkNode["@link-type"] == "auto") &&
             ((linkNode["@url-type"]?has_content && linkNode["@url-type"] != "transition") || (!urlInfo.hasActions)))>
            <#-- do nothing -->
        <#else>
            <form method="post" action="${urlInfo.url}" name="${linkFormId!""}"<#if linkFormId?has_content> id="${linkFormId}"</#if><#if linkNode["@target-window"]?has_content> target="${linkNode["@target-window"]}"</#if>>
                <#assign targetParameters = urlInfo.getParameterMap()>
                <#-- NOTE: using .keySet() here instead of ?keys because ?keys was returning all method names with the other keys, not sure why -->
                <#if targetParameters?has_content><#list targetParameters.keySet() as pKey>
                    <input type="hidden" name="${pKey?html}" value="${targetParameters.get(pKey)?default("")?html}"/>
                </#list></#if>
                <#if !linkFormId?has_content>
                    <#assign confirmationMessage = ec.resource.evaluateStringExpand(linkNode["@confirmation"]?if_exists, "")/>
                    <#if linkNode["image"]?has_content><#assign imageNode = linkNode["image"][0]/>
                        <input type="image" src="${sri.makeUrlByType(imageNode["@url"],imageNode["@url-type"]!"content",null,"true")}"<#if imageNode["@alt"]?has_content> alt="${imageNode["@alt"]}"</#if><#if confirmationMessage?has_content> onclick="return confirm('${confirmationMessage?js_string}')"</#if>>
                    <#else>
                        <button type="submit"<#if linkNode["@icon"]?has_content> iconcls="ui-icon-${linkNode["@icon"]}"</#if><#if confirmationMessage?has_content> onclick="return confirm('${confirmationMessage?js_string}')"</#if><#if linkNode["@link-type"]?if_exists == "hidden-form-link"> class="button-plain"</#if>>${ec.resource.evaluateStringExpand(linkNode["@text"], "")}</button>
                    </#if>
                </#if>
            </form>
        </#if>
    </#if>
</#macro>

<#macro image><img src="${sri.makeUrlByType(.node["@url"],.node["@url-type"]!"content",null,"true")}" alt="${.node["@alt"]!"image"}"<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@width"]?has_content> width="${.node["@width"]}"</#if><#if .node["@height"]?has_content> height="${.node["@height"]}"</#if>/></#macro>
<#macro label>
    <#assign labelType = .node["@type"]?default("span")/>
    <#assign labelValue = ec.resource.evaluateStringExpand(.node["@text"], "")/>
    <#if (labelValue?has_content && labelValue?length < 255)><#assign labelValue = ec.l10n.getLocalizedMessage(labelValue)/></#if>
    <#if labelValue?trim?has_content>
        <${labelType}<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if>><#if !.node["@encode"]?has_content || .node["@encode"] == "true">${labelValue?html?replace("\n", "<br>")}<#else>${labelValue}</#if></${labelType}>
    </#if>
</#macro>
<#macro editable>
    <#-- for docs on JS usage see: http://www.appelsiini.net/projects/jeditable -->
    <#assign urlInfo = sri.makeUrlByType(.node["@transition"], "transition", .node, "true")>
    <#assign urlParms = urlInfo.getParameterMap()>
    <#assign divId>${.node["@id"]}<#if listEntryIndex?has_content>_${listEntryIndex}</#if></#assign>
    <#assign labelType = .node["@type"]?default("span")>
    <#assign labelValue = ec.resource.evaluateStringExpand(.node["@text"], "")>
    <#assign parameterName = .node["@parameter-name"]!"value">
    <#if labelValue?trim?has_content>
        <${labelType} id="${divId}"><#if .node["@encode"]?if_exists == "true">${labelValue?html?replace("\n", "<br>")}<#else>${labelValue}</#if></${labelType}>
        <#assign afterScreenScript>
        $("#${divId}").editable("${urlInfo.url}", { indicator:"${ec.l10n.getLocalizedMessage("Saving")}",
            tooltip:"${ec.l10n.getLocalizedMessage("Click to edit")}", cancel:"${ec.l10n.getLocalizedMessage("Cancel")}",
            submit:"${ec.l10n.getLocalizedMessage("Save")}", name:"${parameterName}",
            type:"${.node["@widget-type"]!"textarea"}", cssclass:"editable-form",
            submitdata:{<#list urlParms.keySet() as parameterKey>${parameterKey}:"${urlParms[parameterKey]}", </#list>parameterName:"${parameterName}"}
            <#if .node["editable-load"]?has_content>
                <#assign loadNode = .node["editable-load"][0]>
                <#assign loadUrlInfo = sri.makeUrlByType(loadNode["@transition"], "transition", loadNode, "true")>
                <#assign loadUrlParms = loadUrlInfo.getParameterMap()>
            , loadurl:"${loadUrlInfo.url}", loadtype:"POST", loaddata:function(value, settings) { return {<#list loadUrlParms.keySet() as parameterKey>${parameterKey}:"${loadUrlParms[parameterKey]}", </#list>currentValue:value}; }
            </#if>});
        </#assign>
        <#t>${sri.appendToScriptWriter(afterScreenScript)}
    </#if>
</#macro>
<#macro parameter><#-- do nothing, used directly in other elements --></#macro>

<#-- ====================================================== -->
<#-- ======================= Form ========================= -->
<#macro "form-single">
<#if sri.doBoundaryComments()><!-- BEGIN form-single[@name=${.node["@name"]}] --></#if>
    <#-- Use the formNode assembled based on other settings instead of the straight one from the file: -->
    <#assign formNode = sri.getFtlFormNode(.node["@name"])>
    ${sri.setSingleFormMapInContext(formNode)}
    <#assign skipStart = (formNode["@skip-start"]?if_exists == "true")>
    <#assign skipEnd = (formNode["@skip-end"]?if_exists == "true")>
    <#assign urlInfo = sri.makeUrlByType(formNode["@transition"], "transition", null, "false")>
    <#assign formId = formNode["@name"]>
    <#assign listEntryIndex = "">
    <#if !skipStart>
    <form name="${formNode["@name"]}" id="${formId}" method="post" action="${urlInfo.url}"<#if sri.isFormUpload(formNode["@name"])> enctype="multipart/form-data"</#if>>
        <input type="hidden" name="moquiFormName" value="${formNode["@name"]}">
    </#if>
    <#if formNode["field-layout"]?has_content>
        <#assign fieldLayout = formNode["field-layout"][0]>
        <fieldset class="form-single-outer">
            <#assign accordionId = fieldLayout["@id"]?default(formNode["@name"] + "-accordion")>
            <#assign collapsible = (fieldLayout["@collapsible"]?if_exists == "true")>
            <#assign active = fieldLayout["@active"]?if_exists>
            <#assign collapsibleOpened = false>
            <#list formNode["field-layout"][0]?children as layoutNode>
                <#if layoutNode?node_name == "field-ref">
                  <#if collapsibleOpened>
                    <#assign collapsibleOpened = false>
                    </div>
                    <#assign afterFormScript>
                        $("#${accordionId}").accordion({ collapsible: true,<#if active?has_content> active: ${active},</#if> heightStyle: "content" });
                    </#assign>
                    <#t>${sri.appendToScriptWriter(afterFormScript)}
                    <#assign accordionId = accordionId + "_A"><#-- set this just in case another accordion is opened -->
                  </#if>
                    <#assign fieldRef = layoutNode["@name"]>
                    <#assign fieldNode = "invalid">
                    <#list formNode["field"] as fn><#if fn["@name"] == fieldRef><#assign fieldNode = fn><#break></#if></#list>
                    <#if fieldNode == "invalid">
                        <div>Error: could not find field with name [${fieldRef}] referred to in a field-ref.@name attribute.</div>
                    <#else>
                        <@formSingleSubField fieldNode/>
                    </#if>
                <#elseif layoutNode?node_name == "fields-not-referenced">
                    <#assign nonReferencedFieldList = sri.getFtlFormFieldLayoutNonReferencedFieldList(.node["@name"])>
                    <#list nonReferencedFieldList as nonReferencedField><@formSingleSubField nonReferencedField/></#list>
                <#elseif layoutNode?node_name == "field-row">
                  <#if collapsibleOpened>
                    <#assign collapsibleOpened = false>
                    </div>
                    <#assign afterFormScript>
                        $("#${accordionId}").accordion({ collapsible: true,<#if active?has_content> active: ${active},</#if> heightStyle: "content" });
                    </#assign>
                    <#t>${sri.appendToScriptWriter(afterFormScript)}
                    <#assign accordionId = accordionId + "_A"><#-- set this just in case another accordion is opened -->
                  </#if>
                    <div class="field-row ui-helper-clearfix">
                    <#assign inFieldRow = true>
                    <#list layoutNode?children as rowChildNode>
                        <#if rowChildNode?node_name == "field-ref">
                            <div class="field-row-item">
                                <#assign fieldRef = rowChildNode["@name"]>
                                <#assign fieldNode = "invalid">
                                <#list formNode["field"] as fn><#if fn["@name"] == fieldRef><#assign fieldNode = fn><#break></#if></#list>
                                <#if fieldNode == "invalid">
                                    <div>Error: could not find field with name [${fieldRef}] referred to in a field-ref.@name attribute.</div>
                                <#else>
                                    <@formSingleSubField fieldNode/>
                                </#if>
                            </div>
                        <#elseif rowChildNode?node_name == "fields-not-referenced">
                            <#assign nonReferencedFieldList = sri.getFtlFormFieldLayoutNonReferencedFieldList(.node["@name"])>
                            <#list nonReferencedFieldList as nonReferencedField><@formSingleSubField nonReferencedField/></#list>
                        </#if>
                    </#list>
                    <#assign inFieldRow = false>
                    </div>
                <#elseif layoutNode?node_name == "field-group">
                  <#if collapsible && !collapsibleOpened><#assign collapsibleOpened = true>
                    <div id="${accordionId}">
                  </#if>
                    <h3><a href="#">${layoutNode["@title"]?default("Section " + layoutNode_index)}</a></h3>
                    <div<#if layoutNode["@style"]?has_content> class="${layoutNode["@style"]}"</#if>>
                        <#list layoutNode?children as groupNode>
                            <#if groupNode?node_name == "field-ref">
                                <#assign fieldRef = groupNode["@name"]>
                                <#assign fieldNode = "invalid">
                                <#list formNode["field"] as fn><#if fn["@name"] == fieldRef><#assign fieldNode = fn><#break></#if></#list>
                                <@formSingleSubField fieldNode/>
                            <#elseif groupNode?node_name == "fields-not-referenced">
                                <#assign nonReferencedFieldList = sri.getFtlFormFieldLayoutNonReferencedFieldList(.node["@name"])>
                                <#list nonReferencedFieldList as nonReferencedField><@formSingleSubField nonReferencedField/></#list>
                            <#elseif groupNode?node_name == "field-row">
                                <div class="field-row ui-helper-clearfix">
                                <#list groupNode?children as rowChildNode>
                                    <#if rowChildNode?node_name == "field-ref">
                                        <div class="field-row-item">
                                            <#assign fieldRef = rowChildNode["@name"]>
                                            <#assign fieldNode = "invalid">
                                            <#list formNode["field"] as fn><#if fn["@name"] == fieldRef><#assign fieldNode = fn><#break></#if></#list>
                                            <#if fieldNode == "invalid">
                                                <div>Error: could not find field with name [${fieldRef}] referred to in a field-ref.@name attribute.</div>
                                            <#else>
                                                <@formSingleSubField fieldNode/>
                                            </#if>
                                        </div>
                                    <#elseif rowChildNode?node_name == "fields-not-referenced">
                                        <#assign nonReferencedFieldList = sri.getFtlFormFieldLayoutNonReferencedFieldList(.node["@name"])>
                                        <#list nonReferencedFieldList as nonReferencedField><@formSingleSubField nonReferencedField/></#list>
                                    </#if>
                                </#list>
                                </div>
                            </#if>
                        </#list>
                    </div>
                </#if>
            </#list>
            <#if collapsibleOpened>
                </div>
                <#assign afterFormScript>
                    $("#${accordionId}").accordion({ collapsible: true,<#if active?has_content> active: ${active},</#if> heightStyle: "content" });
                </#assign>
                <#t>${sri.appendToScriptWriter(afterFormScript)}
            </#if>
        </fieldset>
    <#else>
        <fieldset class="form-single-outer">
            <#list formNode["field"] as fieldNode><@formSingleSubField fieldNode/></#list>
        </fieldset>
    </#if>
    <#if !skipEnd></form></#if>
    <#if !skipStart>
        <#assign afterFormScript>
            <#-- init form validation -->
            $("#${formNode["@name"]}").validate();
            <#-- init tooltips -->
            $(document).tooltip();

            <#-- if background-submit=true init ajaxForm; for examples see http://www.malsup.com/jquery/form/#ajaxForm -->
            <#if formNode["@background-submit"]?if_exists == "true">
            function backgroundSuccess${formId}(responseText, statusText, xhr, $form) {
                <#if formNode["@background-reload-id"]?has_content>
                    load${formNode["@background-reload-id"]}();
                </#if>
                <#if formNode["@background-message"]?has_content>
                <#-- TODO: do something much fancier than a dumb alert box -->
                    alert("${formNode["@background-message"]}");
                </#if>
            }
            $("#${formId}").ajaxForm({ success: backgroundSuccess${formId}, dataType: 'json', resetForm: false });
            </#if>
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
    <#if formNode["@focus-field"]?has_content>
        <#assign afterFormScript>
            $("#${formNode["@name"]}_${formNode["@focus-field"]}").focus();
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
    <#if sri.doBoundaryComments()><!-- END   form-single[@name=${.node["@name"]}] --></#if>
</#macro>
<#macro formSingleSubField fieldNode>
    <#list fieldNode["conditional-field"] as fieldSubNode>
        <#if ec.resource.evaluateCondition(fieldSubNode["@condition"], "")>
            <@formSingleWidget fieldSubNode/>
            <#return>
        </#if>
    </#list>
    <#if fieldNode["default-field"]?has_content>
        <@formSingleWidget fieldNode["default-field"][0]/>
        <#return>
    </#if>
</#macro>
<#macro formSingleWidget fieldSubNode>
    <#if fieldSubNode["ignored"]?has_content && (fieldSubNode?parent["@hide"]?if_exists != "false")><#return></#if>
    <#if fieldSubNode["hidden"]?has_content && (fieldSubNode?parent["@hide"]?if_exists != "false")><#recurse fieldSubNode/><#return></#if>
    <#if fieldSubNode?parent["@hide"]?if_exists == "true"><#return></#if>
    <div class="single-form-field">
        <#assign curFieldTitle><@fieldTitle fieldSubNode/></#assign>
        <#if !fieldSubNode["submit"]?has_content && !(inFieldRow?if_exists && !curFieldTitle?has_content)><label class="form-title" for="${formNode["@name"]}_${fieldSubNode?parent["@name"]}">${curFieldTitle}</label></#if>
        ${sri.pushContext()}
        <#list fieldSubNode?children as widgetNode><#if widgetNode?node_name == "set">${sri.setInContext(widgetNode)}</#if></#list>
        <#list fieldSubNode?children as widgetNode>
            <#if widgetNode?node_name == "link">
                <#assign linkNode = widgetNode>
                <#assign linkUrlInfo = sri.makeUrlByType(linkNode["@url"], linkNode["@url-type"]!"transition", linkNode, linkNode["@expand-transition-url"]!"true")>
                <#assign linkFormId><@fieldId linkNode/></#assign>
                <#assign afterFormText><@linkFormForm linkNode linkFormId linkUrlInfo/></#assign>
                <#t>${sri.appendToAfterScreenWriter(afterFormText)}
                <#t><@linkFormLink linkNode linkFormId linkUrlInfo/>
            <#elseif widgetNode?node_name == "set"><#-- do nothing, handled above -->
            <#else><#t><#visit widgetNode>
            </#if>
        </#list>
        ${sri.popContext()}
    </div>
</#macro>

<#macro "form-list">
<#if sri.doBoundaryComments()><!-- BEGIN form-list[@name=${.node["@name"]}] --></#if>
    <#-- Use the formNode assembled based on other settings instead of the straight one from the file: -->
    <#assign formNode = sri.getFtlFormNode(.node["@name"])>
    <#assign isMulti = formNode["@multi"]?if_exists == "true">
    <#assign isMultiFinalRow = false>
    <#assign skipStart = (formNode["@skip-start"]?if_exists == "true")>
    <#assign skipEnd = (formNode["@skip-end"]?if_exists == "true")>
    <#assign skipForm = (formNode["@skip-form"]?if_exists == "true")>
    <#assign urlInfo = sri.makeUrlByType(formNode["@transition"], "transition", null, "false")>
    <#assign listName = formNode["@list"]>
    <#assign listObject = ec.resource.evaluateContextField(listName, "")?if_exists>
    <#assign formListColumnList = formNode["form-list-column"]?if_exists>
    <#if !(formNode["@paginate"]?if_exists == "false") && context[listName + "Count"]?exists &&
            (context[listName + "Count"]?if_exists > 0) &&
            (!formNode["@paginate-always-show"]?has_content || formNode["@paginate-always-show"]?if_exists == "true" || (context[listName + "PageMaxIndex"] > 0))>
        <div class="form-list-paginate">
            <!-- page ${context[listName + "PageIndex"]} of ${context[listName + "PageMaxIndex"]} -->
            <#if (context[listName + "PageIndex"] > 0)>
                <#assign firstUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("pageIndex", 0)>
                <#assign previousUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("pageIndex", (context[listName + "PageIndex"] - 1))>
                <a href="${firstUrlInfo.getUrlWithParams()}">|&lt;</a>
                <a href="${previousUrlInfo.getUrlWithParams()}">&lt;</a>
            <#else>
                <span>|&lt;</span>
                <span>&lt;</span>
            </#if>
            <span>${context[listName + "PageRangeLow"]} - ${context[listName + "PageRangeHigh"]} / ${context[listName + "Count"]}</span>
            <#if (context[listName + "PageIndex"] < context[listName + "PageMaxIndex"])>
                <#assign lastUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("pageIndex", context[listName + "PageMaxIndex"])>
                <#assign nextUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("pageIndex", context[listName + "PageIndex"] + 1)>
                <a href="${nextUrlInfo.getUrlWithParams()}">&gt;</a>
                <a href="${lastUrlInfo.getUrlWithParams()}">&gt;|</a>
            <#else>
                <span>&gt;</span>
                <span>&gt;|</span>
            </#if>
        </div>
    </#if>
    <#if formListColumnList?exists && (formListColumnList?size > 0)>
        <#if !skipStart>
        <div class="form-list-outer" id="${formNode["@name"]}-table">
            <div class="form-header-group">
                <#assign needHeaderForm = sri.isFormHeaderForm(formNode["@name"])>
                <#if needHeaderForm && !skipForm>
                    <#assign curUrlInfo = sri.getCurrentScreenUrl()>
                    <#assign headerFormId>${formNode["@name"]}-header</#assign>
                <form name="${headerFormId}" id="${headerFormId}" class="form-header-row" method="post" action="${curUrlInfo.url}">
                    <input type="hidden" name="moquiFormName" value="${formNode["@name"]}">
                    <#assign nonReferencedFieldList = sri.getFtlFormListColumnNonReferencedHiddenFieldList(.node["@name"])>
                    <#list nonReferencedFieldList as nonReferencedField><#if nonReferencedField["header-field"]?has_content><#recurse nonReferencedField["header-field"][0]/></#if></#list>
                <#else>
                <div class="form-header-row">
                </#if>
                    <#list formListColumnList as fieldListColumn>
                        <div class="form-header-cell">
                        <#list fieldListColumn["field-ref"] as fieldRef>
                            <#assign fieldRefName = fieldRef["@name"]>
                            <#assign fieldNode = "invalid">
                            <#list formNode["field"] as fn><#if fn["@name"] == fieldRefName><#assign fieldNode = fn><#break></#if></#list>
                            <#if fieldNode == "invalid">
                                <div>Error: could not find field with name [${fieldRefName}] referred to in a form-list-column.field-ref.@name attribute.</div>
                            <#else>
                                <#if !(fieldNode["@hide"]?if_exists == "true" ||
                                        ((!fieldNode["@hide"]?has_content) && fieldNode?children?size == 1 &&
                                        (fieldNode?children[0]["hidden"]?has_content || fieldNode?children[0]["ignored"]?has_content)))>
                                    <div><@formListHeaderField fieldNode/></div>
                                </#if>
                            </#if>
                        </#list>
                        </div>
                    </#list>
                <#if needHeaderForm && !skipForm>
                </form>
                    <#if _dynamic_container_id?has_content>
                        <#-- if we have an _dynamic_container_id this was loaded in a dynamic-container so init ajaxForm; for examples see http://www.malsup.com/jquery/form/#ajaxForm -->
                        <#assign afterFormScript>
                            $("#${headerFormId}").ajaxForm({ target: '#${_dynamic_container_id}', success: activateAllButtons, resetForm: false });
                        </#assign>
                        <#t>${sri.appendToScriptWriter(afterFormScript)}
                    </#if>
                <#else>
                </div>
                </#if>
            </div>
            <#if isMulti && !skipForm>
                <form name="${formNode["@name"]}" id="${formNode["@name"]}" class="form-body" method="post" action="${urlInfo.url}">
                    <input type="hidden" name="moquiFormName" value="${formNode["@name"]}">
                    <input type="hidden" name="_isMulti" value="true">
            <#else>
                <div class="form-body">
            </#if>
        </#if>
        <#list listObject?if_exists as listEntry>
            <#assign listEntryIndex = listEntry_index>
            <#-- NOTE: the form-list.@list-entry attribute is handled in the ScreenForm class through this call: -->
            ${sri.startFormListRow(formNode["@name"], listEntry, listEntry_index, listEntry_has_next)}
            <#if isMulti || skipForm>
            <div class="form-row">
            <#else>
            <form name="${formNode["@name"]}_${listEntryIndex}" id="${formNode["@name"]}_${listEntryIndex}" class="form-row" method="post" action="${urlInfo.url}">
            </#if>
            <#assign nonReferencedFieldList = sri.getFtlFormListColumnNonReferencedHiddenFieldList(.node["@name"])>
            <#list nonReferencedFieldList as nonReferencedField><@formListSubField nonReferencedField/></#list>
            <#list formNode["form-list-column"] as fieldListColumn>
                <div class="form-cell">
                <#list fieldListColumn["field-ref"] as fieldRef>
                    <#assign fieldRefName = fieldRef["@name"]>
                    <#assign fieldNode = "invalid">
                    <#list formNode["field"] as fn><#if fn["@name"] == fieldRefName><#assign fieldNode = fn><#break></#if></#list>
                    <#if fieldNode == "invalid">
                        <div>Error: could not find field with name [${fieldRefName}] referred to in a form-list-column.field-ref.@name attribute.</div>
                    <#else>
                        <#assign formListSkipClass = true>
                        <@formListSubField fieldNode/>
                    </#if>
                </#list>
                </div>
            </#list>
            <#if isMulti || skipForm>
            </div>
            <#else>
                <#assign afterFormScript>
                    $("#${formNode["@name"]}_${listEntryIndex}").validate();
                </#assign>
                <#t>${sri.appendToScriptWriter(afterFormScript)}
            </form>
            </#if>
            ${sri.endFormListRow()}
        </#list>
        <#assign listEntryIndex = "">
        ${sri.safeCloseList(listObject)}<#-- if listObject is an EntityListIterator, close it -->
        <#if !skipEnd>
            <#if isMulti && !skipForm>
                <div class="form-bottom-row">
                    <#assign isMultiFinalRow = true>
                    <#list formNode["field"] as fieldNode><@formListSubField fieldNode/></#list>
                </div>
                </form>
            <#else>
                </div><!-- close form-body -->
            </#if>
            </div><!-- close table -->
        </#if>
        <#if isMulti && !skipStart && !skipForm>
            <#assign afterFormScript>
                $("#${formNode["@name"]}").validate(); $(document).tooltip();
            </#assign>
            <#t>${sri.appendToScriptWriter(afterFormScript)}
        </#if>
    <#else>
        <#if !skipStart>
        <div class="form-list-outer" id="${formNode["@name"]}-table">
            <div class="form-header-group">
                <#assign needHeaderForm = sri.isFormHeaderForm(formNode["@name"])>
                <#if needHeaderForm && !skipStart && !skipForm>
                    <#assign curUrlInfo = sri.getCurrentScreenUrl()>
                    <form name="${formNode["@name"]}-header" id="${formNode["@name"]}-header" class="form-header-row" method="post" action="${curUrlInfo.url}">
                <#else>
                    <div class="form-header-row">
                </#if>
                    <#list formNode["field"] as fieldNode>
                        <#assign allHidden = true>
                        <#list fieldNode?children as fieldSubNode><#if !(fieldSubNode["hidden"]?has_content || fieldSubNode["ignored"]?has_content)><#assign allHidden = false></#if></#list>
                        <#if !(fieldNode["@hide"]?if_exists == "true" || allHidden ||
                                ((!fieldNode["@hide"]?has_content) && fieldNode?children?size == 1 &&
                                (fieldNode["header-field"][0]?if_exists["hidden"]?has_content || fieldNode["header-field"][0]?if_exists["ignored"]?has_content))) &&
                                !(isMulti && fieldNode["default-field"]?has_content && fieldNode["default-field"][0]["submit"]?has_content)>
                            <div class="form-header-cell"><@formListHeaderField fieldNode/></div>
                        <#elseif fieldNode["header-field"][0]?if_exists["hidden"]?has_content>
                            <#recurse fieldNode["header-field"][0]/>
                        </#if>
                    </#list>
                <#if needHeaderForm && !skipStart && !skipForm>
                    </form>
                <#else>
                    </div>
                </#if>
            </div>
            <#if isMulti && !skipForm>
                <form name="${formNode["@name"]}" id="${formNode["@name"]}" class="form-body" method="post" action="${urlInfo.url}">
                    <input type="hidden" name="moquiFormName" value="${formNode["@name"]}">
                    <input type="hidden" name="_isMulti" value="true">
            <#else>
                <div class="form-body">
            </#if>
        </#if>
        <#list listObject?if_exists as listEntry>
            <#assign listEntryIndex = listEntry_index>
            <#-- NOTE: the form-list.@list-entry attribute is handled in the ScreenForm class through this call: -->
            ${sri.startFormListRow(formNode["@name"], listEntry, listEntry_index, listEntry_has_next)}
            <#if isMulti || skipForm>
                <div class="form-row">
            <#else>
                <form name="${formNode["@name"]}_${listEntryIndex}" id="${formNode["@name"]}_${listEntryIndex}" class="form-row" method="post" action="${urlInfo.url}">
            </#if>
                <#list formNode["field"] as fieldNode><@formListSubField fieldNode/></#list>
            <#if isMulti || skipForm>
                </div>
            <#else>
                <#assign afterFormScript>
                    $("#${formNode["@name"]}_${listEntryIndex}").validate();
                </#assign>
                <#t>${sri.appendToScriptWriter(afterFormScript)}
                </form>
            </#if>
            ${sri.endFormListRow()}
        </#list>
        <#assign listEntryIndex = "">
        ${sri.safeCloseList(listObject)}<#-- if listObject is an EntityListIterator, close it -->
        <#if !skipEnd>
            <#if isMulti && !skipForm>
                <div class="form-bottom-row">
                    <#assign isMultiFinalRow = true>
                    <#list formNode["field"] as fieldNode><@formListSubField fieldNode/></#list>
                </div>
                </form>
            <#else>
                </div>
            </#if>
            </div>
        </#if>
        <#if isMulti && !skipStart && !skipForm>
            <#assign afterFormScript>$("#${formNode["@name"]}").validate(); $(document).tooltip();</#assign>
            <#t>${sri.appendToScriptWriter(afterFormScript)}
        </#if>
    </#if>
<#if sri.doBoundaryComments()><!-- END   form-list[@name=${.node["@name"]}] --></#if>
</#macro>
<#macro formListHeaderField fieldNode>
    <#if fieldNode["header-field"]?has_content>
        <#assign fieldSubNode = fieldNode["header-field"][0]>
    <#elseif fieldNode["default-field"]?has_content>
        <#assign fieldSubNode = fieldNode["default-field"][0]>
    <#else>
        <#-- this only makes sense for fields with a single conditional -->
        <#assign fieldSubNode = fieldNode["conditional-field"][0]>
    </#if>
    <#assign headerFieldNode = fieldNode["header-field"][0]?if_exists>
    <#assign defaultFieldNode = fieldNode["default-field"][0]?if_exists>
    <div class="form-title">
        <#if fieldSubNode["submit"]?has_content>&nbsp;<#else><#if headerFieldNode["@title"]?has_content><@fieldTitle headerFieldNode/><#elseif defaultFieldNode["@title"]?has_content><@fieldTitle defaultFieldNode/><#else><@fieldTitle fieldSubNode/></#if></#if>
        <#if fieldSubNode["@show-order-by"]?if_exists == "true" || fieldSubNode["@show-order-by"]?if_exists == "case-insensitive">
            <#assign caseInsensitive = fieldSubNode["@show-order-by"]?if_exists == "case-insensitive">
            <#assign orderByField = ec.web.requestParameters.orderByField?if_exists>
            <#assign ascActive = orderByField?has_content && orderByField?contains(fieldNode["@name"]) && !orderByField?starts_with("-")>
            <#assign ascOrderByUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("orderByField", "+" + caseInsensitive?string("^","") + fieldNode["@name"])>
            <#assign descActive = orderByField?has_content && orderByField?contains(fieldNode["@name"]) && orderByField?starts_with("-")>
            <#assign descOrderByUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("orderByField", "-" + caseInsensitive?string("^","") + fieldNode["@name"])>
            <a href="${ascOrderByUrlInfo.getUrlWithParams()}" class="form-order-by<#if ascActive> active</#if>">+</a><a href="${descOrderByUrlInfo.getUrlWithParams()}" class="form-order-by<#if descActive> active</#if>">-</a>
            <#-- the old way, show + or -:
            <#if !orderByField?has_content || orderByField?starts_with("-") || !orderByField?contains(fieldNode["@name"])><#assign orderByField = ("+" + fieldNode["@name"])><#else><#assign orderByField = ("-" + fieldNode["@name"])></#if>
            <#assign orderByUrlInfo = sri.getCurrentScreenUrl().cloneUrlInfo().addParameter("orderByField", orderByField)>
            <a href="${orderByUrlInfo.getUrlWithParams()}" class="form-order-by">${orderByField?substring(0,1)}</a>
            -->
        </#if>
    </div>
    <#if fieldNode["header-field"]?has_content && fieldNode["header-field"][0]?children?has_content>
    <div class="form-header-field">
        <@formListWidget fieldNode["header-field"][0] true/>
        <#-- <#recurse fieldNode["header-field"][0]/> -->
    </div>
    </#if>
</#macro>
<#macro formListSubField fieldNode>
    <#list fieldNode["conditional-field"] as fieldSubNode>
        <#if ec.resource.evaluateCondition(fieldSubNode["@condition"], "")>
            <@formListWidget fieldSubNode/>
            <#return>
        </#if>
    </#list>
    <#if fieldNode["default-field"]?has_content>
        <#assign isHeaderField=false>
        <@formListWidget fieldNode["default-field"][0]/>
        <#return>
    </#if>
</#macro>
<#macro formListWidget fieldSubNode isHeaderField=false>
    <#if fieldSubNode["ignored"]?has_content><#return/></#if>
    <#if fieldSubNode?parent["@hide"]?if_exists == "true"><#return></#if>
    <#-- don't do a column for submit fields, they'll go in their own row at the bottom -->
    <#t><#if !isHeaderField && isMulti && !isMultiFinalRow && fieldSubNode["submit"]?has_content><#return/></#if>
    <#t><#if !isHeaderField && isMulti && isMultiFinalRow && !fieldSubNode["submit"]?has_content><#return/></#if>
    <#if fieldSubNode["hidden"]?has_content><#recurse fieldSubNode/><#return/></#if>
    <#if !isMultiFinalRow><div<#if !formListSkipClass?if_exists> class="form-cell"</#if>></#if>
        ${sri.pushContext()}
        <#list fieldSubNode?children as widgetNode><#if widgetNode?node_name == "set">${sri.setInContext(widgetNode)}</#if></#list>
        <#list fieldSubNode?children as widgetNode>
            <#if widgetNode?node_name == "link">
                <#assign linkNode = widgetNode>
                <#assign linkUrlInfo = sri.makeUrlByType(linkNode["@url"], linkNode["@url-type"]!"transition", linkNode, linkNode["@expand-transition-url"]!"true")>
                <#assign linkFormId><@fieldId linkNode/></#assign>
                <#assign afterFormText><@linkFormForm linkNode linkFormId linkUrlInfo/></#assign>
                <#t>${sri.appendToAfterScreenWriter(afterFormText)}
                <#t><@linkFormLink linkNode linkFormId linkUrlInfo/>
            <#elseif widgetNode?node_name == "set"><#-- do nothing, handled above -->
            <#else><#t><#visit widgetNode></#if>
        </#list>
        ${sri.popContext()}
    <#if !isMultiFinalRow></div></#if>
</#macro>
<#macro "row-actions"><#-- do nothing, these are run by the SRI --></#macro>

<#macro fieldName widgetNode><#assign fieldNode=widgetNode?parent?parent/>${fieldNode["@name"]?html}<#if isMulti?exists && isMulti && listEntryIndex?has_content>_${listEntryIndex}</#if></#macro>
<#macro fieldId widgetNode><#assign fieldNode=widgetNode?parent?parent/>${fieldNode?parent["@name"]}_${fieldNode["@name"]}<#if listEntryIndex?has_content>_${listEntryIndex}</#if></#macro>
<#macro fieldTitle fieldSubNode><#assign titleValue><#if fieldSubNode["@title"]?has_content>${ec.resource.evaluateStringExpand(fieldSubNode["@title"], "")}<#else><#list fieldSubNode?parent["@name"]?split("(?=[A-Z])", "r") as nameWord>${nameWord?cap_first?replace("Id", "ID")}<#if nameWord_has_next> </#if></#list></#if></#assign>${ec.l10n.getLocalizedMessage(titleValue)}</#macro>

<#macro "field"><#-- shouldn't be called directly, but just in case --><#recurse/></#macro>
<#macro "conditional-field"><#-- shouldn't be called directly, but just in case --><#recurse/></#macro>
<#macro "default-field"><#-- shouldn't be called directly, but just in case --><#recurse/></#macro>
<#macro "set"><#-- shouldn't be called directly, but just in case --><#recurse/></#macro>

<#-- ================== Form Field Widgets ==================== -->

<#macro check>
    <#assign options = {"":""}/><#assign options = sri.getFieldOptions(.node)>
    <#assign currentValue = sri.getFieldValueString(.node?parent?parent, "", null)>
    <#if !currentValue?has_content><#assign currentValue = ec.resource.evaluateStringExpand(.node["@no-current-selected-key"]?if_exists, "")/></#if>
    <#assign id><@fieldId .node/></#assign>
    <#assign curName><@fieldName .node/></#assign>
    <#list (options.keySet())?if_exists as key>
        <#assign allChecked = ec.resource.evaluateStringExpand(.node["@all-checked"]?if_exists, "")>
        <span id="${id}<#if (key_index > 0)>_${key_index}</#if>"><input type="checkbox" name="${curName}" value="${key?html}"<#if allChecked?if_exists == "true"> checked="checked"<#elseif currentValue?has_content && currentValue==key> checked="checked"</#if><#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>${options.get(key)?default("")}</span>
    </#list>
</#macro>

<#macro "date-find">
<span class="form-date-find">
    <#assign curFieldName><@fieldName .node/></#assign>
    <#if .node["@type"]?if_exists == "time"><#assign size=9/><#assign maxlength=12/><#elseif .node["@type"]?if_exists == "date"><#assign size=10/><#assign maxlength=10/><#else><#assign size=23/><#assign maxlength=23/></#if>
    <#assign id><@fieldId .node/></#assign>
    <span>${ec.l10n.getLocalizedMessage("From")}&nbsp;</span><input type="text" name="${curFieldName}_from" value="${ec.web.parameters.get(curFieldName + "_from")?if_exists?default(.node["@default-value-from"]!"")?html}" size="${size}" maxlength="${maxlength}" id="${id}_from"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <span>${ec.l10n.getLocalizedMessage("Thru")}&nbsp;</span><input type="text" name="${curFieldName}_thru" value="${ec.web.parameters.get(curFieldName + "_thru")?if_exists?default(.node["@default-value-thru"]!"")?html}" size="${size}" maxlength="${maxlength}" id="${id}_thru"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <#if .node["@type"]?if_exists != "time">
        <#assign afterFormScript>
            <#if .node["@type"]?if_exists == "date">
            $("#${id}_from,#${id}_thru").datepicker({
            <#else>
            $("#${id}_from,#${id}_thru").datetimepicker({showSecond: true, timeFormat: 'hh:mm:ss', stepHour: 1, stepMinute: 5, stepSecond: 5,
            </#if>showOn: 'button', buttonImage: '', buttonText: '...', buttonImageOnly: false, dateFormat: 'yy-mm-dd'});
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
</span>
</#macro>
<#macro "date-time">
<span class="form-date-time">
    <#assign fieldValue = sri.getFieldValueString(.node?parent?parent, .node["@default-value"]!"", .node["@format"]?if_exists)>
    <#if .node["@type"]?if_exists == "time"><#assign size=9/><#assign maxlength=13/><#elseif .node["@type"]?if_exists == "date"><#assign size=10/><#assign maxlength=10/><#else><#assign size=19/><#assign maxlength=23/></#if>
    <#assign id><@fieldId .node/></#assign>
    <input type="text" name="<@fieldName .node/>" value="${fieldValue?html}" size="${size}" maxlength="${maxlength}" id="${id}"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <#if .node["@type"]?if_exists != "time">
        <#assign afterFormScript>
                <#if .node["@type"]?if_exists == "date">
                $("#${id}").datepicker({
                <#else>
                $("#${id}").datetimepicker({showSecond: true, timeFormat: 'hh:mm:ss', stepHour: 1, stepMinute: 1, stepSecond: 1,
                </#if>showOn: 'button', buttonImage: '/images/icon-calendar.png', buttonText: 'Calendar', buttonImageOnly: true, dateFormat: 'yy-mm-dd', changeMonth: true, changeYear: true, showButtonPanel: true});
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
</span>
</#macro>

<#macro display>
    <#assign fieldValue = ""/>
    <#if .node["@text"]?has_content>
        <#assign fieldValue = ec.resource.evaluateStringExpand(.node["@text"], "")>
        <#if .node["@currency-unit-field"]?has_content>
            <#assign fieldValue = ec.l10n.formatCurrency(fieldValue, ec.resource.evaluateContextField(.node["@currency-unit-field"], ""), 2)>
        </#if>
    <#elseif .node["@currency-unit-field"]?has_content>
        <#assign fieldValue = ec.l10n.formatCurrency(sri.getFieldValue(.node?parent?parent, ""), ec.resource.evaluateContextField(.node["@currency-unit-field"], ""), 2)>
    <#else>
        <#assign fieldValue = sri.getFieldValueString(.node?parent?parent, "", .node["@format"]?if_exists)>
    </#if>
    <#t><span id="<@fieldId .node/>" class="${sri.getFieldValueClass(.node?parent?parent)}<#if .node["@currency-unit-field"]?has_content> currency</#if>"><#if .node["@encode"]?if_exists == "false">${fieldValue!"&nbsp;"}<#else>${(fieldValue!" ")?html?replace("\n", "<br>")}</#if></span>
    <#t><#if !.node["@also-hidden"]?has_content || .node["@also-hidden"] == "true">
        <#-- use getFieldValuePlainString() and not getFieldValueString() so we don't do timezone conversions, etc -->
        <input type="hidden" name="<@fieldName .node/>" value="${sri.getFieldValuePlainString(.node?parent?parent, fieldValue!"")?html}">
    </#if>
</#macro>
<#macro "display-entity">
    <#assign fieldValue = ""/><#assign fieldValue = sri.getFieldEntityValue(.node)/>
    <#t><#if formNode?node_name == "form-single"><span id="<@fieldId .node/>"></#if><#if .node["@encode"]!"true" == "false">${fieldValue!"&nbsp;"}<#else>${(fieldValue!" ")?html?replace("\n", "<br>")}</#if><#if formNode?node_name == "form-single"></span></#if>
    <#t><#if !.node["@also-hidden"]?has_content || .node["@also-hidden"] == "true"><input type="hidden" name="<@fieldName .node/>" value="${sri.getFieldValueString(.node?parent?parent, fieldValue!"", null)?html}"></#if>
</#macro>

<#macro "drop-down">
    <#assign options = {"":""}/><#assign options = sri.getFieldOptions(.node)/>
    <#assign currentValue = sri.getFieldValueString(.node?parent?parent, "", null)/>
    <#if !currentValue?has_content><#assign currentValue = ec.resource.evaluateStringExpand(.node["@no-current-selected-key"]?if_exists, "")/></#if>
    <#assign currentDescription = (options.get(currentValue))?if_exists/>
    <#if !currentDescription?has_content && .node["@current-description"]?has_content>
        <#assign currentDescription = ec.resource.evaluateStringExpand(.node["@current-description"], "")/>
    </#if>
    <#assign id><@fieldId .node/></#assign>
    <select name="<@fieldName .node/>" id="${id}"<#if .node["@allow-multiple"]?if_exists == "true"> multiple="multiple"</#if><#if .node["@size"]?has_content> size="${.node["@size"]}"</#if><#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <#if currentValue?has_content && (.node["@current"]?if_exists != "selected") && !(.node["@allow-multiple"]?if_exists == "true")>
        <option selected="selected" value="${currentValue}"><#if currentDescription?has_content>${currentDescription}<#else>${currentValue}</#if></option><#rt/>
        <option value="${currentValue}">---</option><#rt/>
    </#if>
    <#assign allowEmpty = ec.resource.evaluateStringExpand(.node["@allow-empty"]?if_exists, "")/>
    <#if (allowEmpty?if_exists == "true") || !(options?has_content)>
        <option value="">&nbsp;</option>
    </#if>

    <#if !.node["dynamic-options"]?has_content>
        <#list (options.keySet())?if_exists as key>
            <option<#if currentValue?has_content && currentValue == key> selected="selected"</#if> value="${key}">${options.get(key)}</option>
        </#list>
    </#if>
    </select>

    <#if .node["dynamic-options"]?has_content>
        <#assign doNode = .node["dynamic-options"][0]>
        <#assign depNodeList = doNode["depends-on"]>
        <#assign formName = formNode["@name"]>
        <#assign afterFormScript>
            function populate_${id}() {
                $.ajax({ type:'POST', url:'${sri.screenUrlInfo.url}/${doNode["@transition"]}', data:{ <#list depNodeList as depNode>'${depNode["@field"]}': $('#${formName}_${depNode["@field"]}').val()<#if depNode_has_next>, </#if></#list> }, dataType:'json' }).done(
                    function(list) {
                        if (list) {
                            $('#${id}').html(""); /* clear out the drop-down */
                            <#if allowEmpty?if_exists == "true">
                            $('#${id}').append('<option value="">&nbsp;</option>');
                            </#if>
                            $.each(list, function(key, value) {
                                $('#${id}').append("<option value = '" + value["${doNode["@value-field"]!"value"}"] + "'>" + value["${doNode["@label-field"]!"label"}"] + "</option>");
                            }) }; } ); };
            $(document).ready(function() {
                <#list depNodeList as depNode>
                    $("#${formName}_${depNode["@field"]}").change(function() { populate_${id}(); });
                </#list>
                populate_${id}();
            });
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
    <#if .node["@combo-box"]?if_exists == "true">
        <#assign afterFormScript>
            $("#${id}").combobox();
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
</#macro>

<#macro file><input type="file" name="<@fieldName .node/>" value="${sri.getFieldValueString(.node?parent?parent, .node["@default-value"]!"", null)?html}" size="${.node.@size!"30"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if><#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>></#macro>

<#macro hidden>
    <#-- use getFieldValuePlainString() and not getFieldValueString() so we don't do timezone conversions, etc -->
    <#assign id><@fieldId .node/></#assign>
    <input type="hidden" name="<@fieldName .node/>" value="${sri.getFieldValuePlainString(.node?parent?parent, .node["@default-value"]!"")?html}" id="${id}">
</#macro>

<#macro ignored><#-- shouldn't ever be called as it is checked in the form-* macros --></#macro>

<#-- TABLED, not to be part of 1.0:
<#macro "lookup">
    <#assign curFieldName = .node?parent?parent["@name"]?html/>
    <#assign curFormName = .node?parent?parent?parent["@name"]?html/>
    <#assign id><@fieldId .node/></#assign>
    <input type="text" name="${curFieldName}" value="${sri.getFieldValueString(.node?parent?parent, .node["@default-value"]!"", null)?html}" size="${.node.@size!"30"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if><#if ec.resource.evaluateCondition(.node.@disabled!"false", "")> disabled="disabled"</#if> id="${id}">
    <#assign ajaxUrl = ""/><#- - LATER once the JSON service stuff is in place put something real here - ->
    <#- - LATER get lookup code in place, or not... - ->
    <script>
        $(document).ready(function() {
            new ConstructLookup("${.node["@target-screen"]}", "${id}", document.${curFormName}.${curFieldName},
            <#if .node["@secondary-field"]?has_content>document.${curFormName}.${.node["@secondary-field"]}<#else>null</#if>,
            "${curFormName}", "${width!""}", "${height!""}", "${position!"topcenter"}", "${fadeBackground!"true"}", "${ajaxUrl!""}", "${showDescription!""}", ''); });
    </script>
</#macro>
-->

<#macro password><input type="password" name="<@fieldName .node/>" size="${.node.@size!"25"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if> id="<@fieldId .node/>"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>></#macro>

<#macro radio>
    <#assign options = {"":""}/><#assign options = sri.getFieldOptions(.node)/>
    <#assign currentValue = sri.getFieldValueString(.node?parent?parent, "", null)/>
    <#if !currentValue?has_content><#assign currentValue = ec.resource.evaluateStringExpand(.node["@no-current-selected-key"]?if_exists, "")/></#if>
    <#assign id><@fieldId .node/></#assign>
    <#assign curName><@fieldName .node/></#assign>
    <#list (options.keySet())?if_exists as key>
        <span id="${id}<#if (key_index > 0)>_${key_index}</#if>"><input type="radio" name="${curName}" value="${key?html}"<#if currentValue?has_content && currentValue==key> checked="checked"</#if><#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>${options.get(key)?default("")}</span>
    </#list>
</#macro>

<#macro "range-find">
<span class="form-range-find">
    <#assign curFieldName><@fieldName .node/></#assign>
    <#assign id><@fieldId .node/></#assign>
    <span>${ec.l10n.getLocalizedMessage("From")}&nbsp;</span><input type="text" name="${curFieldName}_from" value="${ec.web.parameters.get(curFieldName + "_from")?if_exists?default(.node["@default-value-from"]!"")?html}" size="${.node.@size!"10"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if> id="${id}_from"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <span>${ec.l10n.getLocalizedMessage("Through")}&nbsp;</span><input type="text" name="${curFieldName}_thru" value="${ec.web.parameters.get(curFieldName + "_thru")?if_exists?default(.node["@default-value-thru"]!"")?html}" size="${.node.@size!"10"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if> id="${id}_thru"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
</span>
</#macro>

<#macro reset><input type="reset" name="<@fieldName .node/>" value="<@fieldTitle .node?parent/>" id="<@fieldId .node/>"<#if .node["@icon"]?has_content> iconcls="ui-icon-${.node["@icon"]}"</#if><#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>></#macro>

<#macro submit>
    <#assign confirmationMessage = ec.resource.evaluateStringExpand(.node["@confirmation"]?if_exists, "")/>
    <button type="submit" name="<@fieldName .node/>"<#if .node["@icon"]?has_content> iconcls="ui-icon-${.node["@icon"]}"</#if><#if confirmationMessage?has_content> onclick="return confirm('${confirmationMessage?js_string}');"</#if> id="<@fieldId .node/>"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <#if .node["image"]?has_content><#assign imageNode = .node["image"][0]>
        <img src="${sri.makeUrlByType(imageNode["@url"],imageNode["@url-type"]!"content",null,"true")}" alt="<#if imageNode["@alt"]?has_content>${imageNode["@alt"]}<#else><@fieldTitle .node?parent/></#if>"<#if imageNode["@width"]?has_content> width="${imageNode["@width"]}"</#if><#if imageNode["@height"]?has_content> height="${imageNode["@height"]}"</#if>>
    <#else>
        <#t><@fieldTitle .node?parent/>
    </#if>
    </button>
</#macro>

<#macro "text-area"><textarea name="<@fieldName .node/>" cols="${.node["@cols"]!"60"}" rows="${.node["@rows"]!"3"}"<#if .node["@read-only"]!"false" == "true"> readonly="readonly"</#if><#if .node["@maxlength"]?has_content> maxlength="${maxlength}"</#if> id="<@fieldId .node/>"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>${sri.getFieldValueString(.node?parent?parent, .node["@default-value"]!"", null)?html}</textarea></#macro>

<#macro "text-line">
    <#assign id><@fieldId .node/></#assign>
    <#assign name><@fieldName .node/></#assign>
    <#assign fieldValue = sri.getFieldValueString(.node?parent?parent, .node["@default-value"]!"", .node["@format"]?if_exists)>
    <#assign validationClasses = sri.getFormFieldValidationClasses(.node?parent?parent?parent["@name"], .node?parent?parent["@name"])>
    <#-- NOTE: removed number type (<#elseif validationClasses?contains("number")>number) because on Safari, maybe others, ignores size and behaves funny for decimal values -->
    <input type="<#if validationClasses?contains("email")>email<#elseif validationClasses?contains("url")>url<#else>text</#if>" name="${name}" value="${fieldValue?html}" size="${.node.@size!"30"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if><#if ec.resource.evaluateCondition(.node.@disabled!"false", "")> disabled="disabled"</#if> id="${id}"<#if validationClasses?has_content> class="${validationClasses}"</#if><#if validationClasses?contains("required")> required</#if><#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>
    <#if .node["@ac-transition"]?has_content>
        <span id="${id}_value" class="form-autocomplete-value">&nbsp;</span>
        <#assign afterFormScript>
            $("#${id}").autocomplete({
                source: function(request, response) { $.ajax({
                    url: "${sri.screenUrlInfo.url}/${.node["@ac-transition"]}", type: "POST", dataType: "json", data: { term: request.term },
                    success: function(data) { response($.map(data, function(item) { return { label: item.label, value: item.value } })); }
                }); },
                <#t><#if .node["@ac-delay"]?has_content>delay: ${.node["@ac-delay"]},</#if>
                <#t><#if .node["@ac-min-length"]?has_content>minLength: ${.node["@ac-min-length"]},</#if>
                select: function( event, ui ) {
                    if (ui.item) { this.value = ui.item.value; if (ui.item.label) { $("#${id}_value").html(ui.item.label); } }
                }
            });
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
    <#assign regexpInfo = sri.getFormFieldValidationRegexpInfo(.node?parent?parent?parent["@name"], .node?parent?parent["@name"])?if_exists>
    <#if regexpInfo?has_content>
        <#assign afterFormScript>
        $("#${formNode["@name"]}").validate();
        $.validator.addMethod("${id}_v", function (value, element) { return this.optional(element) || /${regexpInfo.regexp}/.test(value); }, "${regexpInfo.message!"Input invalid"}");
        $("#${id}").rules("add", { ${id}_v:true })
        </#assign>
        <#t>${sri.appendToScriptWriter(afterFormScript)}
    </#if>
</#macro>

<#macro "text-find">
<span class="form-text-find">
    <#assign defaultOperator = .node["@default-operator"]?default("contains")>
    <#assign curFieldName><@fieldName .node/></#assign>
    <#if .node["@hide-options"]?if_exists == "true" || .node["@hide-options"]?if_exists == "operator">
        <input type="hidden" name="${curFieldName}_op" value="${defaultOperator}">
    <#else>
        <span><input type="checkbox" name="${curFieldName}_not" value="Y"<#if ec.web.parameters.get(curFieldName + "_not")?if_exists == "Y"> checked="checked"</#if>>&nbsp;${ec.l10n.getLocalizedMessage("Not")}</span>
        <select name="${curFieldName}_op">
            <option value="equals"<#if defaultOperator == "equals"> selected="selected"</#if>>${ec.l10n.getLocalizedMessage("Equals")}</option>
            <option value="like"<#if defaultOperator == "like"> selected="selected"</#if>>${ec.l10n.getLocalizedMessage("Like")}</option>
            <option value="contains"<#if defaultOperator == "contains"> selected="selected"</#if>>${ec.l10n.getLocalizedMessage("Contains")}</option>
            <option value="empty"<#rt/><#if defaultOperator == "empty"> selected="selected"</#if>>${ec.l10n.getLocalizedMessage("Empty")}</option>
        </select>
    </#if>

    <input type="text" name="${curFieldName}" value="${sri.getFieldValueString(.node?parent?parent, .node["@default-value"]!"", null)?html}" size="${.node.@size!"30"}"<#if .node.@maxlength?has_content> maxlength="${.node.@maxlength}"</#if> id="<@fieldId .node/>"<#if .node?parent["@tooltip"]?has_content> title="${.node?parent["@tooltip"]}"</#if>>

    <#assign ignoreCase = (ec.web.parameters.get(curFieldName + "_ic")?if_exists == "Y") || !(.node["@ignore-case"]?has_content) || (.node["ignore-case"] == "true")>
    <#if .node["@hide-options"]?if_exists == "true" || .node["@hide-options"]?if_exists == "ignore-case">
        <input type="hidden" name="${curFieldName}_ic" value="Y"<#if ignoreCase> checked="checked"</#if>>
    <#else>
        <span><input type="checkbox" name="${curFieldName}_ic" value="Y"<#if ignoreCase> checked="checked"</#if>>&nbsp;${ec.l10n.getLocalizedMessage("Ignore Case")}</span>
    </#if>
</span>
</#macro>
