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

<#macro @element><!-- doing nothing for element ${.node?node_name} --></#macro>

<#macro widgets>
<#if sri.doBoundaryComments()><!-- BEGIN screen[@location=${sri.getActiveScreenDef().location}].widgets --></#if>
<#recurse/>
<#if sri.doBoundaryComments()><!-- END   screen[@location=${sri.getActiveScreenDef().location}].widgets --></#if>
</#macro>
<#macro "fail-widgets">
<#if sri.doBoundaryComments()><!-- BEGIN screen[@location=${sri.getActiveScreenDef().location}].fail-widgets --></#if>
<#recurse/>
<#if sri.doBoundaryComments()><!-- END   screen[@location=${sri.getActiveScreenDef().location}].fail-widgets --></#if>
</#macro>

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
<#macro section>
    <#if sri.doBoundaryComments()><!-- BEGIN section[@name=${.node["@name"]}] --></#if>
    <div id="${.node["@name"]}">${sri.renderSection(.node["@name"])}
    </div>
    <#if sri.doBoundaryComments()><!-- END   section[@name=${.node["@name"]}] --></#if>
</#macro>
<#macro "section-iterate">
    <#if sri.doBoundaryComments()><!-- BEGIN section-iterate[@name=${.node["@name"]}] --></#if>
    <div id="${.node["@name"]}">${sri.renderSection(.node["@name"])}
    </div>
    <#if sri.doBoundaryComments()><!-- END   section-iterate[@name=${.node["@name"]}] --></#if>
</#macro>

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
<#macro "include-screen">
<#if sri.doBoundaryComments()><!-- BEGIN include-screen[@location=${.node["@location"]}][@share-scope=${.node["@share-scope"]?if_exists}] --></#if>
${sri.renderIncludeScreen(.node["@location"], .node["@share-scope"]?if_exists)}
<#if sri.doBoundaryComments()><!-- END   include-screen[@location=${.node["@location"]}][@share-scope=${.node["@share-scope"]?if_exists}] --></#if>
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
<#macro "render-mode">
<#if .node["text"]?has_content>
    <#list .node["text"] as textNode><#if textNode["@type"]?has_content && textNode["@type"] == sri.getRenderMode()><#assign textToUse = textNode/></#if></#list>
    <#if !textToUse?has_content><#list .node["text"] as textNode><#if !textNode["@type"]?has_content || textNode["@type"] == "any"><#assign textToUse = textNode/></#if></#list></#if>
    <#if textToUse?has_content>
<#if sri.doBoundaryComments()><!-- BEGIN render-mode.text[@location=${textToUse["@location"]}][@template=${textToUse["@template"][0]?if_exists}] --></#if>
    ${sri.renderText(textToUse["@location"], textToUse["@template"][0]?if_exists)}
<#if sri.doBoundaryComments()><!-- END   render-mode.text[@location=${textToUse["@location"]}][@template=${textToUse["@template"][0]?if_exists}] --></#if>
    </#if>
</#if>
</#macro>

<#macro text><#-- do nothing, is used only through "render-mode" --></#macro>

<#-- ================== Standalone Fields ==================== -->
<#macro "link">
<#assign parameterMap = ec.getContext().get(.node["@parameter-map"][0]?if_exists)?if_exists/>
<#if (.node["@link-type"]?has_content && .node["@link-type"][0] == "anchor") ||
    ((!.node["@link-type"]?has_content || .node["@link-type"] == "auto") && .node["@url-type"]?has_content && .node["@url-type"] != "transition")>
    <#assign parameterString><#t>
        <#t><#list .node["parameter"] as parameterNode>${parameterNode["@name"][0]?url}=${sri.makeValue(parameterNode["from-field"],parameterNode["value"])?url}<#if parameterNode_has_next>&amp;</#if></#list>
        <#t><#if .node["parameter"]?has_content && .node["@parameter-map"]?has_content && ec.getContext().get(.node["@parameter-map"])?has_content>&amp;</#if>
        <#t><#list parameterMap?keys as pKey>${pKey?url}=${parameterMap[pKey]?url}<#if pKey_has_next>&amp;</#if></#list>
    <#t></#assign>
    <a href="${sri.makeUrlByType((.node["@url"][0] + "?" + parameterString), .node["@url-type"][0]!"transition")}"<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@target-window"]?has_content> target="${.node["@target-window"]}"</#if><#if .node["@confirmation"]?has_content> onclick="return confirm('${.node["@confirmation"][0]?js_string}')"</#if>>
    <#if .node["image"]?has_content><#visit .node["image"]/><#else/>${.node["@text"]}</#if>
    </a>
<#else/>
    <form method="post" action="${sri.makeUrlByType(.node["@url"][0], .node["@url-type"][0]!"transition")}" name="${.node["@id"][0]!""}"<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@target-window"]?has_content> target="${.node["@target-window"]}"</#if> onsubmit="javascript:submitFormDisableSubmit(this)">
        <#list .node["parameter"] as parameterNode><input name="${parameterNode["@name"][0]?html}" value="${sri.makeValue(parameterNode["from-field"],parameterNode["value"])?html}" type="hidden"/></#list>
        <#list parameterMap?if_exists?keys as pKey><input name="${pKey?html}" value="${parameterMap[pKey]?html}" type="hidden"/></#list>
    <#if .node["image"]?has_content><#assign imageNode = .node["image"][0]/>
    <input type="image" src="${sri.makeUrlByType(imageNode["@url"],imageNode["@url-type"][0]!"content")}"<#if imageNode["@alt"]?has_content> alt="${imageNode["@alt"]}"</#if><#if .node["@confirmation"]?has_content> onclick="return confirm('${.node["@confirmation"][0]?js_string}')"</#if>/>
    <#else/>
    <input type="submit" value="${.node["@text"]}"<#if .node["@confirmation"]?has_content> onclick="return confirm('${.node["@confirmation"][0]?js_string}')"</#if>/>
    </#if>
    </form>
    <#-- NOTE: consider using a link instead of submit buttons/image, would look something like this (would require id attribute, or add a name attribute):
        <a href="javascript:document.${.node["@id"]}.submit()">
            <#if .node["image"]?has_content>
            <img src="${sri.makeUrlByType(imageNode["@url"],imageNode["@url-type"][0]!"content")}"<#if imageNode["@alt"]?has_content> alt="${imageNode["@alt"]}"</#if>/>
            <#else/>
            ${.node["@text"]}
            <#/if>
        </a>
    -->
</#if>
</#macro>
<#macro "image"><img src="${sri.makeUrlByType(.node["@url"],.node["@url-type"][0]!"content")}" alt="${.node["@alt"][0]!"image"}"<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@width"]?has_content> width="${.node["@width"]}"</#if><#if .node["@height"]?has_content> height="${.node["@height"]}"</#if>/></#macro>
<#macro "label"><span<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if>>${.node["@text"]}</span></#macro>
<#macro "parameter"><#-- do nothing, used directly in other elements --></#macro>

<#-- ============================================ -->
<#-- ================== Form ==================== -->
<#macro "form-single">
<#if sri.doBoundaryComments()><!-- BEGIN form-single[@name=${.node["@name"]}] --></#if>
    <!-- TODO: make form markup -->
    <form name="${.node["@name"]}" id="${.node["@name"]}" method="post">
    ${sri.renderFormSingle(.node["@name"])}
    </form>
<#if sri.doBoundaryComments()><!-- END   form-single[@name=${.node["@name"]}] --></#if>
</#macro>
<#macro "form-list">
<#if sri.doBoundaryComments()><!-- BEGIN form-list[@name=${.node["@name"]}] --></#if>
    <!-- TODO: make form markup -->
    <form name="${.node["@name"]}" id="${.node["@name"]}" method="post">
    ${sri.renderFormList(.node["@name"])}
    </form>
<#if sri.doBoundaryComments()><!-- END   form-list[@name=${.node["@name"]}] --></#if>
</#macro>
