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

<#include "classpath://template/DefaultScreenMacros.html.ftl"/>

<#macro container>    <div<#if .node["@id"]?has_content> id="${.node["@id"]}"</#if><#if .node["@style"]?has_content> class="${.node["@style"]}"</#if>><#recurse>
    </div><!-- CONTAINER OVERRIDE EXAMPLE -->
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

<#-- ===============================================================================================
TODO:
<#macro "date-find">
            <xs:attribute name="type" default="timestamp">
                <xs:simpleType>
                    <xs:restriction base="xs:token">
                        <xs:enumeration value="timestamp"/>
                        <xs:enumeration value="date-time"/>
                        <xs:enumeration value="date"/>
                        <xs:enumeration value="time"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
            <xs:attribute name="default-value" type="xs:string"/>
            <xs:attribute name="default-option-from" default="equals">
                <xs:simpleType>
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="equals"/>
                        <xs:enumeration value="same-day"/>
                        <xs:enumeration value="greater-day-start"/>
                        <xs:enumeration value="greater"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
            <xs:attribute name="default-option-thru" default="less">
                <xs:simpleType>
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="less"/>
                        <xs:enumeration value="up-to-day-start"/>
                        <xs:enumeration value="up-to-day-end"/>
                        <xs:enumeration value="empty"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
</#macro>

<#macro "range-find">
            <xs:attribute name="size" type="xs:positiveInteger" default="25"/>
            <xs:attribute name="maxlength" type="xs:positiveInteger"/>
            <xs:attribute name="default-value" type="xs:string"/>
            <xs:attribute name="default-option-from" default="greater-equals">
                <xs:simpleType>
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="equals"/>
                        <xs:enumeration value="greater"/>
                        <xs:enumeration value="greater-equals"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
            <xs:attribute name="default-option-thru" default="less-equals">
                <xs:simpleType>
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="less"/>
                        <xs:enumeration value="less-equals"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
</#macro>
<#macro "text-find">
            <xs:attribute name="size" type="xs:positiveInteger" default="25"/>
            <xs:attribute name="maxlength" type="xs:positiveInteger"/>
            <xs:attribute name="default-value" type="xs:string"/>
            <xs:attribute name="ignore-case" default="true" type="boolean"/>
            <xs:attribute name="default-option">
                <xs:simpleType>
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="equals"/>
                        <xs:enumeration value="not-equals"/>
                        <xs:enumeration value="like"/>
                        <xs:enumeration value="contains"/>
                        <xs:enumeration value="empty"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
            <xs:attribute name="hide-options" default="false">
                <xs:simpleType>
                    <xs:restriction base="xs:token">
                        <xs:enumeration value="true"/>
                        <xs:enumeration value="false"/>
                        <xs:enumeration value="ignore-case"/>
                        <xs:enumeration value="options"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:attribute>
</#macro>
-->
