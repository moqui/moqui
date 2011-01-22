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
<#recurse widgetsNode/>

<#-- ================ Subscreens ================ -->
<#macro "subscreens-menu">
    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if> class="subscreens-menu">
<!-- TODO render menu -->
    </div>
</#macro>

<#macro "subscreens-active">
    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if> class="subscreens-active">
    ${sri.renderSubscreen()}
    </div>
</#macro>

<#macro "subscreens-panel">
<!-- TODO handle type:
    <xs:attribute name="type" default="tab">
                <xs:enumeration value="tab"/>
                <xs:enumeration value="stack"/>
                <xs:enumeration value="wizard"/>
    </xs:attribute>
-->
    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if> class="subscreens-panel">
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_menu"</#if> class="subscreens-menu">
<!-- TODO render menu -->
        </div>
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_active"</#if> class="subscreens-active">
        ${sri.renderSubscreen()}
        </div>
    </div>
</#macro>

<#-- ================ Section ================ -->
<#macro section>    <div id="${.node["@name"]}">${sri.renderSection(.node["@name"])}
    </div></#macro>
<#macro section-iterate>    <div id="${.node["@name"]}">${sri.renderSection(.node["@name"])}
    </div></#macro>

<#-- ================ Containers ================ -->
<#macro container>    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@style"]?has_content> class="${.node["@style"]}"</#if>><#recurse/>
    </div>
</#macro>

<#macro "container-panel">
    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if>>
        <#if .node["panel-header"]?has_content>
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_header"</#if> class="panel-header"><#recurse .node["panel-header"][0]/>
        </div></#if>
        <#if .node["panel-left"]?has_content>
        <#-- TODO <xs:attribute name="draggable" default="false" type="boolean"/> -->
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_left"</#if> class="panel-left"><#recurse .node["panel-left"][0]/>
        </div></#if>
        <#if .node["panel-right"]?has_content>
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_right"</#if> class="panel-right"><#recurse .node["panel-right"][0]/>
        </div></#if>
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_center"</#if> class="panel-center"><#recurse .node["panel-center"][0]/>
        </div>
        <#if .node["panel-footer"]?has_content>
        <div<#if .node["@id"]?has_content> id="${.node["@id"]}_footer"</#if> class="panel-footer"><#recurse .node["panel-footer"][0]/>
        </div></#if>
    </div>
</#macro>

<#-- ================ Includes ================ -->
<#macro "include-screen">${sri.renderIncludeScreen(.node["@location"], .node["@share-scope"])}
</#macro>

<#-- ============== Tree ============== -->
<#macro tree>
            <xs:sequence>
                <xs:element maxOccurs="unbounded" ref="tree-node"/>
            </xs:sequence>
            <xs:attribute type="xs:string" name="name" use="optional"/>
            <xs:attribute type="xs:string" name="root-node-name" use="required"/>
            <xs:attribute type="xs:string" name="open-depth" default="0"/>
            <xs:attribute type="xs:string" name="entity-name"/>
<!-- TODO implement -->
</#macro>

<#macro "tree-node">
            <xs:sequence>
                <xs:element minOccurs="0" ref="condition"/>
                <xs:choice minOccurs="0">
                    <xs:element ref="entity-find-one"/>
                    <xs:element ref="call-service"/>
                </xs:choice>
                <xs:element ref="widgets"/>
                <xs:element minOccurs="0" maxOccurs="unbounded" ref="tree-sub-node"/>
            </xs:sequence>
            <xs:attribute type="xs:string" name="name" use="required"/>
            <xs:attribute type="xs:string" name="entry-name" />
            <xs:attribute type="xs:string" name="entity-name" />
            <xs:attribute type="xs:string" name="join-field-name" />
<!-- TODO implement -->
</#macro>

<#macro "tree-sub-node">
            <xs:sequence>
                <xs:choice>
                    <xs:element ref="entity-find"/>
                    <xs:element ref="call-service"/>
                </xs:choice>
                <xs:element minOccurs="0" maxOccurs="unbounded" ref="out-field-map"/>
            </xs:sequence>
            <xs:attribute type="xs:string" name="node-name" use="required"/>

            out-field-map:
            <xs:attribute type="xs:string" name="field-name" use="required"/>
            <xs:attribute type="xs:string" name="to-field-name"/>
<!-- TODO implement -->
</#macro>

<#-- ============== Render Mode Elements =============== -->
<#macro "render-mode"><#compress>
<#if .node["text"]>
    <#list .node["text"] as textNode><#if textNode["@type"]?has_content && textNode["@type"] == sri.getRenderMode()><#assign textToUse = textNode/></#if></#list>
    <#if !textToUse?has_content><#list .node["text"] as textNode><#if !textNode["@type"]?has_content || textNode["@type"] == "any"><#assign textToUse = textNode/></#if></#list></#if>
    <#if textToUse?has_content>
    ${sri.renderText(textToUse["@location"], textToUse["@template"])}
    </#if>
</#if>
</#compress></#macro>

<#macro text><#-- do nothing, is used only through "render-mode" --></#macro>

<#-- ================== Standalone Fields ==================== -->
<#macro "link"><#compress single_line=true>
<#if (.node["@link-type"]?has_content && .node["@link-type"][0] == "anchor") ||
    ((!.node["@link-type"]?has_content || .node["@link-type"] == "auto") && .node["@url-type"]?has_content && .node["@url-type"] != "transition")>
    <#assign parameterMap = ec.getContext().get(.node["@parameter-map"]?if_exists)?if_exists/>
    <#assign parameterString><#t>
        <#t><#list .node["parameter"] as parameterNode>${parameterNode["@name"][0]?url}=${sri.makeValue(parameterNode["from-field"],parameterNode["value"])?url}<#if parameterNode_has_next>&amp;</#if></#list>
        <#t><#if .node["parameter"]?has_content && .node["@parameter-map"]?has_content && ec.getContext().get(.node["@parameter-map"])?has_content>&amp;</#if>
        <#t><#list parameterMap?keys as pKey>${pKey?url}=${parameterMap[pKey]?url}<#if pKey_has_next>&amp;</#if></#list>
    <#t></#assign>
    <a href="${sri.makeUrl((.node["@url"][0] + "?" + parameterString), .node["@url-type"][0]!"content")}"<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@target-window"]?has_content> target="${.node["@target-window"]}"</#if><#if .node["@confirmation"]?has_content> onclick="return confirm('${.node["@confirmation"][0]?js_string}')"</#if>>
    <#if .node["image"]?has_content><#visit .node["image"]/><#else/>${.node["@text"]}</#if>
    </a>
<else/>
    <form method="post" action="${sri.makeUrl(.node["@url"][0], .node["@url-type"][0]!"content")}" name="${.node["@id"][0]!""}"<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@target-window"]?has_content> target="${.node["@target-window"]}"</#if> onsubmit="javascript:submitFormDisableSubmit(this)">
        <#list .node["parameter"] as parameterNode><input name="${parameterNode["@name"][0]?html}" value="${sri.makeValue(parameterNode["from-field"],parameterNode["value"])?html}" type="hidden"/></#list>
        <#list parameterMap?keys as pKey><input name="${pKey?html}" value="${parameterMap[pKey]?html}" type="hidden"/></#list>
    </form>
    <#if .node["image"]?has_content><#assign imageNode = .node["image"][0]/>
    <input type="image" src="${sri.makeUrl(imageNode["@url"],imageNode["@url-type"][0]!"content")}"<#if imageNode["@alt"]?has_content> alt="${imageNode["@alt"]}"</#if><#if .node["@confirmation"]?has_content> onclick="return confirm('${.node["@confirmation"][0]?js_string}')"</#if>/>
    <#else/>
    <input type="submit" value="${.node["@text"]}"<#if .node["@confirmation"]?has_content> onclick="return confirm('${.node["@confirmation"][0]?js_string}')"</#if>
    </#if>
    <#-- NOTE: consider using a link instead of submit buttons/image, would look something like this (would require id attribute, or add a name attribute):
        <a href="javascript:document.${.node["@id"]}.submit()">
            <#if .node["image"]?has_content>
            <img src="${sri.makeUrl(imageNode["@url"],imageNode["@url-type"][0]!"content")}"<#if imageNode["@alt"]?has_content> alt="${imageNode["@alt"]}"</#if>/>
            <#else/>
            ${.node["@text"]}
            <#/if>
        </a>
    -->
</#if>
</#compress></#macro>
<#macro "image"><img src="${sri.makeUrl(.node["@url"],.node["@url-type"][0]!"content")}" alt="${.node["@alt"][0]!"image"}"</#if><#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@width"]?has_content> width="${.node["@width"]}"</#if><#if .node["@height"]?has_content> height="${.node["@height"]}"</#if>/></#macro>
<#macro "label"><span<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if>>${.node["@text"]}</span></#macro>
<#macro "parameter"><#-- do nothing, used directly in other elements --></#macro>

<#-- ============================================ -->
<#-- ================== Form ==================== -->
<#macro form-single>
    <!-- TODO: make form markup -->
    <form name="${.node["@name"]}" id="${.node["@name"]}" method="post">
    ${sri.renderFormSingle(.node["@name"])}
    </form>
</#macro>
<#macro form-list>
    <!-- TODO: make form markup -->
    <form name="${.node["@name"]}" id="${.node["@name"]}" method="post">
    ${sri.renderFormList(.node["@name"])}
    </form>
</#macro>
