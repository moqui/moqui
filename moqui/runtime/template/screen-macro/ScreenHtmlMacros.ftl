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
            <xs:attribute name="id" type="xs:string"/>
<!-- TODO implement -->
</#macro>

<#macro "subscreens-active">
            <xs:attribute name="id" type="xs:string"/>
<!-- TODO implement -->
</#macro>

<#macro "subscreens-panel">
            <xs:attribute name="id" type="xs:string"/>
            <xs:attribute name="type" default="tab">
                        <xs:enumeration value="tab"/>
                        <xs:enumeration value="stack"/>
                        <xs:enumeration value="wizard"/>
            </xs:attribute>
<!-- TODO implement -->
</#macro>

<#-- ================ Section ================ -->
<#macro section>    <div id="${.node["@name"]}">${sri.renderSection(.node["@name"])}
    </div>
</#macro>
<#macro section-iterate>    <div id="${.node["@name"]}">${sri.renderSection(.node["@name"])}
    </div>
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
<!-- TODO implement -->
</#macro>

<#macro "out-field-map">
            <xs:attribute type="xs:string" name="field-name" use="required"/>
            <xs:attribute type="xs:string" name="to-field-name"/>
<!-- TODO implement -->
</#macro>

<#-- ============== Render Mode Elements =============== -->
<#macro "render-mode">
<#if .node["text"]>
    <#list .node["text"] as textNode><#if textNode["@type"]?has_content && textNode["@type"] == sri.getRenderMode()><#assign textToUse = textNode/></#if></#list>
    <#if !textToUse?has_content><#list .node["text"] as textNode><#if !textNode["@type"]?has_content || textNode["@type"] == "any"><#assign textToUse = textNode/></#if></#list></#if>
    <#if textToUse?has_content>
    ${sri.renderText(textToUse["@location"], textToUse["@template"])}
    </#if>
</#if>
</#macro>

<#macro text><#-- do nothing, is used only through "render-mode" --></#macro>
