/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.screen

import org.apache.commons.collections.map.ListOrderedMap
import org.apache.commons.collections.set.ListOrderedSet
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.FtlNodeWrapper
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities
import org.moqui.impl.entity.EntityValueImpl
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class ScreenForm {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenForm.class)

    protected ExecutionContextFactoryImpl ecfi
    protected Node formNode
    protected String location
    protected Boolean isUploadForm = null
    protected Boolean isFormHeaderFormVal = null

    protected XmlAction rowActions = null

    ScreenForm(ExecutionContextFactoryImpl ecfi, ScreenDefinition sd, Node baseFormNode, String location) {
        this.ecfi = ecfi
        this.location = location

        // settings parent to null so that this isn't found in addition to the literal form-* element
        formNode = new Node(null, baseFormNode.name())

        // if there is an extends, put that in first (everything else overrides it)
        if (baseFormNode."@extends") {
            String extendsForm = baseFormNode."@extends"
            ScreenForm esf
            if (extendsForm.contains("#")) {
                ScreenDefinition esd = ecfi.screenFacade.getScreenDefinition(extendsForm.substring(0, extendsForm.indexOf("#")))
                esf = esd ? esd.getForm(extendsForm.substring(extendsForm.indexOf("#")+1)) : null
            } else {
                esf = sd.getForm(extendsForm)
            }
            if (esf == null) throw new IllegalArgumentException("Cound not find extends form [${extendsForm}] referred to in form [${formNode."@name"}] of screen [${sd.location}]")
            mergeFormNodes(formNode, esf.formNode, true)
        }

        for (Node afsNode in baseFormNode."auto-fields-service") {
            String serviceName = afsNode."@service-name"
            ServiceDefinition serviceDef = ecfi.serviceFacade.getServiceDefinition(serviceName)
            if (serviceDef != null) {
                addServiceFields(serviceDef, afsNode."@field-type"?:"edit", formNode, ecfi)
                continue
            }
            if (serviceName.contains("#")) {
                EntityDefinition ed = ecfi.entityFacade.getEntityDefinition(serviceName.substring(serviceName.indexOf("#")+1))
                if (ed != null) {
                    addEntityFields(ed, afsNode."@field-type"?:"edit", serviceName.substring(0, serviceName.indexOf("#")), formNode)
                    continue
                }
            }
            throw new IllegalArgumentException("Cound not find service [${serviceName}] or entity noun referred to in auto-fields-service of form [${formNode."@name"}] of screen [${sd.location}]")
        }
        for (Node afeNode in baseFormNode."auto-fields-entity") {
            String entityName = afeNode."@entity-name"
            EntityDefinition ed = ecfi.entityFacade.getEntityDefinition(entityName)
            if (ed != null) {
                addEntityFields(ed, afeNode."@field-type"?:"find-display", null, formNode)
                continue
            }
            throw new IllegalArgumentException("Cound not find entity [${entityName}] referred to in auto-fields-entity of form [${formNode."@name"}] of screen [${sd.location}]")
        }

        // merge original formNode to override any applicable settings
        mergeFormNodes(formNode, baseFormNode, false)

        if (logger.traceEnabled) logger.trace("Form [${location}] resulted in expanded def: " + FtlNodeWrapper.wrapNode(formNode).toString())

        // prep row-actions
        if (formNode."row-actions") {
            rowActions = new XmlAction(ecfi, (Node) formNode."row-actions"[0], location + ".row_actions")
        }
    }

    boolean isUpload() {
        if (isUploadForm != null) return isUploadForm
        // if there is a "file" element, then it's an upload form
        isUploadForm = formNode.depthFirst().find({ it.name() == "file" }) as boolean
        return isUploadForm
    }
    boolean isFormHeaderForm() {
        if (isFormHeaderFormVal != null) return isFormHeaderFormVal
        // if there is a "file" element, then it's an upload form
        isFormHeaderFormVal = false
        for (Node hfNode in formNode.depthFirst().findAll({ it.name() == "header-field" })) {
            if (hfNode.children()) {
                isFormHeaderFormVal = true
                break
            }
        }
        return isFormHeaderFormVal
    }

    Node getFieldInParameterNode(String fieldName) {
        Node fieldNode = (Node) formNode."field".find({ it.@name == fieldName })
        if (fieldNode == null) throw new IllegalArgumentException("Tried to get in-parameter node for field [${fieldName}] that doesn't exist in form [${location}]")
        if (fieldNode."@validate-service") {
            ServiceDefinition sd = ecfi.serviceFacade.getServiceDefinition(fieldNode."@validate-service")
            if (sd == null) throw new IllegalArgumentException("Bad validate-service name [${fieldNode."@validate-service"}] in field [${fieldName}] of form [${location}]")
            return sd.getInParameter(fieldNode."@validate-parameter" ?: fieldName)
        }
        return null
    }

    protected void addServiceFields(ServiceDefinition sd, String fieldType, Node baseFormNode, ExecutionContextFactoryImpl ecfi) {
        String serviceVerb = sd.verb
        String serviceType = sd.serviceNode."@type"
        EntityDefinition ed = null
        if (serviceType == "entity-auto") ed = ecfi.entityFacade.getEntityDefinition(sd.noun)

        for (Node parameterNode in sd.serviceNode."in-parameters"[0]."parameter") {
            String spType = parameterNode."@type" ?: "String"
            String efType = ed != null ? ed.getFieldNode(parameterNode."@name")?."@type" : null

            Node newFieldNode = new Node(null, "field", [name:parameterNode."@name",
                    "validate-service":sd.serviceName, "validate-parameter":parameterNode."@name"])
            Node subFieldNode = newFieldNode.appendNode("default-field")
            switch (fieldType) {
            case "edit":
                if (parameterNode."@required" == "true" && serviceVerb.startsWith("update")) {
                    subFieldNode.appendNode("hidden")
                } else {
                    if (spType.endsWith("Date") && spType != "java.util.Date") {
                        subFieldNode.appendNode("date-time", [type:"date", format:parameterNode."@format"])
                    } else if (spType.endsWith("Time")) {
                        subFieldNode.appendNode("date-time", [type:"time", format:parameterNode."@format"])
                    } else if (spType.endsWith("Timestamp") || spType == "java.util.Date") {
                        subFieldNode.appendNode("date-time", [type:"date-time", format:parameterNode."@format"])
                    } else {
                        if (efType == "text-very-long") {
                            subFieldNode.appendNode("text-area")
                        } else {
                            subFieldNode.appendNode("text-line")
                        }
                    }
                }
                break;
            case "find":
                if (spType.endsWith("Date") && spType != "java.util.Date") {
                    subFieldNode.appendNode("date-find", [type:"date", format:parameterNode."@format"])
                } else if (spType.endsWith("Time")) {
                    subFieldNode.appendNode("date-find", [type:"time", format:parameterNode."@format"])
                } else if (spType.endsWith("Timestamp") || spType == "java.util.Date") {
                    subFieldNode.appendNode("date-find", [type:"date-time", format:parameterNode."@format"])
                } else if (spType.endsWith("BigDecimal") || spType.endsWith("Long") || spType.endsWith("Integer")
                        || spType.endsWith("Double") || spType.endsWith("Float") || spType.endsWith("Number")) {
                    subFieldNode.appendNode("range-find")
                } else {
                    subFieldNode.appendNode("text-find")
                }
                break;
            case "display":
                subFieldNode.appendNode("display", [format:parameterNode."@format"])
                break;
            case "find-display":
                Node headerFieldNode = newFieldNode.appendNode("header-field")
                if (spType.endsWith("Date") && spType != "java.util.Date") {
                    headerFieldNode.appendNode("date-find", [type:"date", format:parameterNode."@format"])
                } else if (spType.endsWith("Time")) {
                    headerFieldNode.appendNode("date-find", [type:"time", format:parameterNode."@format"])
                } else if (spType.endsWith("Timestamp") || spType == "java.util.Date") {
                    headerFieldNode.appendNode("date-find", [type:"date-time", format:parameterNode."@format"])
                } else if (spType.endsWith("BigDecimal") || spType.endsWith("Long") || spType.endsWith("Integer")
                        || spType.endsWith("Double") || spType.endsWith("Float") || spType.endsWith("Number")) {
                    headerFieldNode.appendNode("range-find")
                } else {
                    headerFieldNode.appendNode("text-find")
                }
                subFieldNode.appendNode("display", [format:parameterNode."@format"])
                break;
            case "hidden":
                subFieldNode.appendNode("hidden")
                break;
            }
            mergeFieldNode(baseFormNode, newFieldNode, false)
        }
    }

    protected void addEntityFields(EntityDefinition ed, String fieldType, String serviceVerb, Node baseFormNode) {
        ListOrderedSet pkFieldNameSet = ed.getFieldNames(true, false)
        for (String fieldName in ed.getFieldNames(true, true)) {
            String efType = ed.getFieldNode(fieldName)."@type"

            Node newFieldNode = new Node(null, "field", [name:fieldName])
            Node subFieldNode = newFieldNode.appendNode("default-field")

            switch (fieldType) {
            case "edit":
                if (pkFieldNameSet.contains(fieldName) && serviceVerb == "update") {
                    subFieldNode.appendNode("hidden")
                } else {
                    if (efType.startsWith("date") || efType.startsWith("time")) {
                        Node dateTimeNode = subFieldNode.appendNode("date-time", [type:efType])
                        if (fieldName == "fromDate") dateTimeNode.attributes().put("default-value", "\${ec.user.nowTimestamp}")
                    } else if (efType == "text-very-long") {
                        subFieldNode.appendNode("text-area")
                    } else {
                        subFieldNode.appendNode("text-line")
                    }
                }
                break;
            case "find":
                if (efType.startsWith("date") || efType.startsWith("time")) {
                    subFieldNode.appendNode("date-find", [type:efType])
                } else if (efType.startsWith("number-") || efType.startsWith("currency-")) {
                    subFieldNode.appendNode("range-find")
                } else {
                    subFieldNode.appendNode("text-find")
                }
                break;
            case "display":
                subFieldNode.appendNode("display")
                break;
            case "find-display":
                Node headerFieldNode = newFieldNode.appendNode("header-field")
                if (efType.startsWith("date") || efType.startsWith("time")) {
                    headerFieldNode.appendNode("date-find", [type:efType])
                } else if (efType.startsWith("number-") || efType.startsWith("currency-")) {
                    headerFieldNode.appendNode("range-find")
                } else {
                    headerFieldNode.appendNode("text-find")
                }
                subFieldNode.appendNode("display")
                break;
            case "hidden":
                subFieldNode.appendNode("hidden")
                break;
            }

            // logger.info("Adding form auto entity field [${fieldName}] of type [${efType}], fieldType [${fieldType}] serviceVerb [${serviceVerb}], node: ${newFieldNode}")
            mergeFieldNode(baseFormNode, newFieldNode, false)
        }
    }

    protected void mergeFormNodes(Node baseFormNode, Node overrideFormNode, boolean deepCopy) {
        baseFormNode.attributes().putAll(overrideFormNode.attributes())

        // if overrideFormNode has any row-actions add them all to the ones of the baseFormNode, ie both will run
        if (overrideFormNode."row-actions") {
            if (!baseFormNode."row-actions") baseFormNode.appendNode("row-actions")
            Node baseRowActionsNode = baseFormNode."row-actions"[0]
            for (Node actionNode in overrideFormNode."row-actions") baseRowActionsNode.append(actionNode)
        }

        for (Node overrideFieldNode in overrideFormNode."field") {
            mergeFieldNode(baseFormNode, overrideFieldNode, deepCopy)
        }

        if (overrideFormNode."field-layout") {
            // just use entire override field-layout, don't try to merge
            if (baseFormNode."field-layout") baseFormNode.remove(baseFormNode."field-layout"[0])
            baseFormNode.append(overrideFormNode."field-layout"[0])
        }
        if (overrideFormNode."form-list-column") {
            // if there are any form-list-column remove all from base and copy all from override
            if (baseFormNode."form-list-column") {
                for (Node flcNode in overrideFormNode."form-list-column") baseFormNode.remove(flcNode)
            }
            for (Node flcNode in overrideFormNode."form-list-column") baseFormNode.append(flcNode)
        }
    }

    protected void mergeFieldNode(Node baseFormNode, Node overrideFieldNode, boolean deepCopy) {
        Node baseFieldNode = (Node) baseFormNode."field".find({ it."@name" == overrideFieldNode."@name" })
        if (baseFieldNode != null) {
            baseFieldNode.attributes().putAll(overrideFieldNode.attributes())

            if (overrideFieldNode."header-field") {
                if (baseFieldNode."header-field") baseFieldNode.remove(baseFieldNode."header-field"[0])
                baseFieldNode.append(overrideFieldNode."header-field"[0])
            }
            for (Node overrideConditionalFieldNode in overrideFieldNode."conditional-field") {
                Node baseConditionalFieldNode = (Node) baseFieldNode."conditional-field"
                        .find({ it."@condition" == overrideConditionalFieldNode."@condition" })
                if (baseConditionalFieldNode != null) baseFieldNode.remove(baseConditionalFieldNode)
                baseFieldNode.append(overrideConditionalFieldNode)
            }
            if (overrideFieldNode."default-field") {
                if (baseFieldNode."default-field") baseFieldNode.remove(baseFieldNode."default-field"[0])
                baseFieldNode.append(overrideFieldNode."default-field"[0])
            }
        } else {
            baseFormNode.append(deepCopy ? StupidUtilities.deepCopyNode(overrideFieldNode) : overrideFieldNode)
        }
    }

    void runFormListRowActions(ScreenRenderImpl sri, Object listEntry) {
        // NOTE: this runs in a pushed-/sub-context, so just drop it in and it'll get cleaned up automatically
        if (formNode."@list-entry") {
            sri.ec.context.put(formNode."@list-entry", listEntry)
        } else {
            if (listEntry instanceof Map) {
                sri.ec.context.putAll((Map) listEntry)
            } else {
                sri.ec.context.put("listEntry", listEntry)
            }
        }
        if (rowActions) rowActions.run(sri.ec)
    }

    FtlNodeWrapper getFtlFormNode() { return FtlNodeWrapper.wrapNode(formNode) }

    static ListOrderedMap getFieldOptions(Node widgetNode, ExecutionContext ec) {
        Node fieldNode = widgetNode.parent().parent()
        ListOrderedMap options = new ListOrderedMap()
        for (Node childNode in widgetNode.children()) {
            /* tabled for now, not to include in 1.0:
            if (childNode.name() == "entity-options") {
                EntityListIterator eli
                try {
                    ef = ec.entity.makeFind(childNode."@entity-name")
                    // still need to build find options...
                    eli = ef.iterator()
                    EntityValue ev
                    while ((ev = eli.next()) != null) {
                        ec.context.push(ev)
                        String key = ec.resource.evaluateStringExpand(childNode."@key"?:"\${${fieldNode."@name"}}", null)
                        options.put(key, ec.resource.evaluateStringExpand(childNode."@text", null)?:key)
                        ec.context.pop()
                    }
                } finally {
                    eli.close()
                }
            } else */
            if (childNode.name() == "list-options") {
                Object listObject = ec.resource.evaluateContextField(childNode."@list", null)
                if (listObject instanceof EntityListIterator) {
                    EntityListIterator eli
                    try {
                        eli = (EntityListIterator) listObject
                        EntityValue ev
                        while ((ev = eli.next()) != null) {
                            addFieldOption(options, fieldNode, childNode, ev, ec)
                        }
                    } finally {
                        eli.close()
                    }
                } else {
                    for (Map listOption in listObject) {
                        addFieldOption(options, fieldNode, childNode, listOption, ec)
                    }
                }
            } else if (childNode.name() == "option") {
                options.put(childNode."@key", childNode."@text"?:childNode."@key")
            }
        }
        return options
    }

    static void addFieldOption(ListOrderedMap options, Node fieldNode, Node childNode, Map listOption, ExecutionContext ec) {
        ec.context.push(listOption)
        String key = null
        if (childNode."@key") {
            key = ec.resource.evaluateStringExpand(childNode."@key", null)
        } else if (listOption instanceof EntityValueImpl) {
            String keyFieldName = listOption.getEntityDefinition().getFieldNames(true, false).get(0)
            if (keyFieldName) key = ec.context.get(keyFieldName)
        }
        if (!key) key = ec.context.get(fieldNode."@name")
        if (!key) return

        options.put(key, ec.resource.evaluateStringExpand(childNode."@text", null)?:key)
        ec.context.pop()
    }
}
