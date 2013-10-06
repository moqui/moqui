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
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFindImpl
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.FtlNodeWrapper
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities
import org.moqui.impl.entity.EntityValueImpl
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.entity.EntityList
import org.moqui.entity.EntityException
import org.moqui.impl.screen.ScreenDefinition.TransitionItem
import org.moqui.entity.EntityCondition
import java.sql.Timestamp
import org.moqui.impl.entity.EntityListImpl
import org.moqui.impl.entity.EntityValueBase

class ScreenForm {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenForm.class)

    protected static final Set<String> fieldAttributeNames = new HashSet<String>(["name", "entry-name", "hide", "validate-service", "validate-parameter"])
    protected static final Set<String> subFieldAttributeNames = new HashSet<String>(["title", "tooltip", "red-when"])

    protected ExecutionContextFactoryImpl ecfi
    protected ScreenDefinition sd
    protected Node internalFormNode
    protected String location
    protected String formName
    protected String fullFormName
    protected Boolean isUploadForm = null
    protected Boolean isFormHeaderFormVal = null
    protected boolean hasDbExtensions = false
    protected boolean isDynamic = false

    protected XmlAction rowActions = null

    protected Map<String, Node> dbFormNodeById = null
    protected List<Node> nonReferencedFieldList = null

    ScreenForm(ExecutionContextFactoryImpl ecfi, ScreenDefinition sd, Node baseFormNode, String location) {
        this.ecfi = ecfi
        this.sd = sd
        this.location = location
        this.formName = baseFormNode."@name"
        this.fullFormName = sd.getLocation() + "#" + formName

        // is this a dynamic form?
        isDynamic = (baseFormNode."@dynamic" == "true")

        // does this form have DbForm extensions?
        boolean alreadyDisabled = ecfi.getExecutionContext().getArtifactExecution().disableAuthz()
        try {
            EntityList dbFormLookupList = this.ecfi.getEntityFacade().makeFind("DbFormLookup")
                    .condition("modifyXmlScreenForm", fullFormName).useCache(true).list()
            if (dbFormLookupList) hasDbExtensions = true
        } finally {
            if (!alreadyDisabled) ecfi.getExecutionContext().getArtifactExecution().enableAuthz()
        }

        if (isDynamic) {
            internalFormNode = baseFormNode
        } else {
            // setting parent to null so that this isn't found in addition to the literal form-* element
            internalFormNode = new Node(null, baseFormNode.name())
            initForm(baseFormNode, internalFormNode)
        }
    }

    void initForm(Node baseFormNode, Node newFormNode) {
        // if there is an extends, put that in first (everything else overrides it)
        if (baseFormNode."@extends") {
            String extendsForm = baseFormNode."@extends"
            if (isDynamic) extendsForm = ecfi.resourceFacade.evaluateStringExpand(extendsForm, "")
            ScreenForm esf
            if (extendsForm.contains("#")) {
                ScreenDefinition esd = ecfi.screenFacade.getScreenDefinition(extendsForm.substring(0, extendsForm.indexOf("#")))
                esf = esd ? esd.getForm(extendsForm.substring(extendsForm.indexOf("#")+1)) : null
            } else {
                esf = sd.getForm(extendsForm)
            }
            if (esf == null) throw new IllegalArgumentException("Cound not find extends form [${extendsForm}] referred to in form [${newFormNode."@name"}] of screen [${sd.location}]")
            mergeFormNodes(newFormNode, esf.formNode, true, true)
        }

        for (Node formSubNode in (Collection<Node>) baseFormNode.children()) {
            if (formSubNode.name() == "field") {
                Node nodeCopy = StupidUtilities.deepCopyNode(formSubNode)
                expandFieldNode(newFormNode, nodeCopy)
                mergeFieldNode(newFormNode, nodeCopy, false)
            } else if (formSubNode.name() == "auto-fields-service") {
                String serviceName = formSubNode."@service-name"
                if (isDynamic) serviceName = ecfi.resourceFacade.evaluateStringExpand(serviceName, "")
                ServiceDefinition serviceDef = ecfi.serviceFacade.getServiceDefinition(serviceName)
                if (serviceDef != null) {
                    addServiceFields(serviceDef, formSubNode."@include"?:"in", formSubNode."@field-type"?:"edit", newFormNode, ecfi)
                    continue
                }
                if (serviceName.contains("#")) {
                    EntityDefinition ed = ecfi.entityFacade.getEntityDefinition(serviceName.substring(serviceName.indexOf("#")+1))
                    if (ed != null) {
                        addEntityFields(ed, "all", formSubNode."@field-type"?:"edit", serviceName.substring(0, serviceName.indexOf("#")), newFormNode)
                        continue
                    }
                }
                throw new IllegalArgumentException("Cound not find service [${serviceName}] or entity noun referred to in auto-fields-service of form [${newFormNode."@name"}] of screen [${sd.location}]")
            } else if (formSubNode.name() == "auto-fields-entity") {
                String entityName = formSubNode."@entity-name"
                if (isDynamic) entityName = ecfi.resourceFacade.evaluateStringExpand(entityName, "")
                EntityDefinition ed = ecfi.entityFacade.getEntityDefinition(entityName)
                if (ed != null) {
                    addEntityFields(ed, formSubNode."@include"?:"all", formSubNode."@field-type"?:"find-display", null, newFormNode)
                    continue
                }
                throw new IllegalArgumentException("Cound not find entity [${entityName}] referred to in auto-fields-entity of form [${newFormNode."@name"}] of screen [${sd.location}]")
            }
        }

        // merge original formNode to override any applicable settings
        mergeFormNodes(newFormNode, baseFormNode, false, false)

        // populate validate-service and validate-parameter attributes if the target transition calls a single service
        if (newFormNode."@transition") {
            TransitionItem ti = this.sd.getTransitionItem((String) newFormNode."@transition", null)
            if (ti != null && ti.getSingleServiceName()) {
                String singleServiceName = ti.getSingleServiceName()
                ServiceDefinition sd = ecfi.serviceFacade.getServiceDefinition(singleServiceName)
                if (sd != null) {
                    Set<String> inParamNames = sd.getInParameterNames()
                    for (Node fieldNode in newFormNode."field") {
                        // if the field matches an in-parameter name and does not already have a validate-service, then set it
                        if (inParamNames.contains(fieldNode."@name") && !fieldNode."@validate-service") {
                            fieldNode.attributes().put("validate-service", singleServiceName)
                        }
                    }
                }
            }
        }

        /*
        // add a moquiFormId field to all forms (also: maybe handle in macro ftl file to avoid issue with field-layout
        //     not including this field), and TODO: save in the session
        Node newFieldNode = new Node(null, "field", [name:"moquiFormId"])
        Node subFieldNode = newFieldNode.appendNode("default-field")
        subFieldNode.appendNode("hidden", ["default-value":"((Math.random() * 9999999999) as Long) as String"])
        mergeFieldNode(newFormNode, newFieldNode, false)
         */

        // check form-single.field-layout and add ONLY hidden fields that are missing
        Node fieldLayoutNode = newFormNode."field-layout"[0]
        if (fieldLayoutNode && !fieldLayoutNode.depthFirst().find({ it.name() == "fields-not-referenced" })) {
            for (Node fieldNode in newFormNode."field") {
                if (!fieldLayoutNode.depthFirst().find({ it.name() == "field-ref" && it."@name" == fieldNode."@name" })
                        && fieldNode.depthFirst().find({ it.name() == "hidden" }))
                    addFieldToFieldLayout(newFormNode, fieldNode)
            }
        }

        if (logger.traceEnabled) logger.trace("Form [${location}] resulted in expanded def: " + FtlNodeWrapper.wrapNode(newFormNode).toString())
        // if (location.contains("FOO")) logger.warn("======== Form [${location}] resulted in expanded def: " + FtlNodeWrapper.wrapNode(newFormNode).toString())

        // prep row-actions
        if (newFormNode."row-actions") {
            rowActions = new XmlAction(ecfi, (Node) newFormNode."row-actions"[0], location + ".row_actions")
        }
    }

    List<Node> getFieldLayoutNonReferencedFieldList() {
        if (nonReferencedFieldList != null) return nonReferencedFieldList
        List<Node> fieldList = []

        if (getFormNode()."field-layout") for (Node fieldNode in getFormNode()."field") {
            Node fieldLayoutNode = getFormNode()."field-layout"[0]
            if (!fieldLayoutNode.depthFirst().find({ it.name() == "field-ref" && it."@name" == fieldNode."@name" }))
                fieldList.add(fieldNode)
        }

        nonReferencedFieldList = fieldList
        return fieldList
    }

    List<Node> getColumnNonReferencedHiddenFieldList() {
        if (nonReferencedFieldList != null) return nonReferencedFieldList
        List<Node> fieldList = []

        if (getFormNode()."form-list-column") for (Node fieldNode in getFormNode()."field") {
            if (!fieldNode.depthFirst().find({ it.name() == "hidden" })) continue
            List<Node> formListColumnNodeList = getFormNode()."form-list-column"
            boolean foundReference = false
            for (Node formListColumnNode in formListColumnNodeList)
                if (formListColumnNode.depthFirst().find({ it.name() == "field-ref" && it."@name" == fieldNode."@name" }))
                    foundReference = true
            if (!foundReference) fieldList.add(fieldNode)
        }

        nonReferencedFieldList = fieldList
        return fieldList
    }

    List<Node> getDbFormNodeList() {
        if (!hasDbExtensions) return null

        boolean alreadyDisabled = ecfi.getExecutionContext().getArtifactExecution().disableAuthz()
        try {
            // find DbForm records and merge them in as well
            String formName = sd.getLocation() + "#" + internalFormNode."@name"
            EntityList dbFormLookupList = this.ecfi.getEntityFacade().makeFind("DbFormLookup")
                    .condition("userGroupId", EntityCondition.IN, ecfi.getExecutionContext().getUser().getUserGroupIdSet())
                    .condition("modifyXmlScreenForm", formName)
                    .useCache(true).list()
            // logger.warn("TOREMOVE: looking up DbForms for form [${formName}], found: ${dbFormLookupList}")

            if (!dbFormLookupList) return null

            List<Node> formNodeList = new ArrayList<Node>()
            for (EntityValue dbFormLookup in dbFormLookupList) formNodeList.add(getDbFormNode(dbFormLookup.getString("formId")))

            return formNodeList
        } finally {
            if (!alreadyDisabled) ecfi.getExecutionContext().getArtifactExecution().enableAuthz()
        }
    }
    Node getDbFormNode(String formId) {
        if (dbFormNodeById == null) dbFormNodeById = new HashMap<String, Node>()
        Node dbFormNode = dbFormNodeById.get(formId)

        if (dbFormNode == null) {
            EntityValue dbForm = ecfi.getEntityFacade().makeFind("DbForm").condition("formId", formId).useCache(true).one()
            dbFormNode = new Node(null, (dbForm.isListForm == "Y" ? "form-list" : "form-single"),null)

            EntityList dbFormFieldList = ecfi.getEntityFacade().makeFind("moqui.screen.form.DbFormField").condition("formId", formId)
                    .useCache(true).list()
            for (EntityValue dbFormField in dbFormFieldList) {
                String fieldName = dbFormField.fieldName
                Node newFieldNode = new Node(null, "field", [name:fieldName])
                if (dbFormField.entryName) newFieldNode.attributes().put("entry-name", dbFormField.entryName)
                Node subFieldNode = newFieldNode.appendNode("default-field", [:])
                if (dbFormField.title) subFieldNode.attributes().put("title", dbFormField.title)

                String fieldType = dbFormField.fieldTypeEnumId
                if (!fieldType) throw new IllegalArgumentException("DbFormField record with formId [${formId}] and fieldName [${fieldName}] has no fieldTypeEnumId")

                String widgetName = fieldType.substring(6)
                Node widgetNode = subFieldNode.appendNode(widgetName, [:])

                EntityList dbFormFieldAttributeList = ecfi.getEntityFacade().makeFind("DbFormFieldAttribute")
                        .condition([formId:formId, fieldName:fieldName]).useCache(true).list()
                for (EntityValue dbFormFieldAttribute in dbFormFieldAttributeList) {
                    String attributeName = dbFormFieldAttribute.attributeName
                    if (fieldAttributeNames.contains(attributeName)) {
                        newFieldNode.attributes().put(attributeName, dbFormFieldAttribute.value)
                    } else if (subFieldAttributeNames.contains(attributeName)) {
                        subFieldNode.attributes().put(attributeName, dbFormFieldAttribute.value)
                    } else {
                        widgetNode.attributes().put(attributeName, dbFormFieldAttribute.value)
                    }
                }

                // add option settings when applicable
                EntityList dbFormFieldOptionList = ecfi.getEntityFacade().makeFind("moqui.screen.form.DbFormFieldOption")
                        .condition([formId:formId, fieldName:fieldName]).useCache(true).list()
                EntityList dbFormFieldEntOptsList = ecfi.getEntityFacade().makeFind("moqui.screen.form.DbFormFieldEntOpts")
                        .condition([formId:formId, fieldName:fieldName]).useCache(true).list()
                EntityList combinedOptionList = new EntityListImpl(ecfi.getEntityFacade())
                combinedOptionList.addAll(dbFormFieldOptionList)
                combinedOptionList.addAll(dbFormFieldEntOptsList)
                combinedOptionList.orderByFields(["sequenceNum"])

                for (EntityValue optionValue in combinedOptionList) {
                    if (optionValue.getEntityName() == "moqui.screen.form.DbFormFieldOption") {
                        widgetNode.appendNode("option", [key:optionValue.keyValue, text:optionValue.text])
                    } else {
                        Node entityOptionsNode = widgetNode.appendNode("entity-options", [text:(optionValue.text ?: "\${description}")])
                        Node entityFindNode = entityOptionsNode.appendNode("entity-find", ["entity-name":optionValue.entityName])

                        EntityList dbFormFieldEntOptsCondList = ecfi.getEntityFacade().makeFind("moqui.screen.form.DbFormFieldEntOptsCond")
                                .condition([formId:formId, fieldName:fieldName, sequenceNum:optionValue.sequenceNum])
                                .useCache(true).list()
                        for (EntityValue dbFormFieldEntOptsCond in dbFormFieldEntOptsCondList) {
                            entityFindNode.appendNode("econdition", ["field-name":dbFormFieldEntOptsCond.entityFieldName, value:dbFormFieldEntOptsCond.value])
                        }

                        EntityList dbFormFieldEntOptsOrderList = ecfi.getEntityFacade().makeFind("moqui.screen.form.DbFormFieldEntOptsOrder")
                                .condition([formId:formId, fieldName:fieldName, sequenceNum:optionValue.sequenceNum])
                                .orderBy("orderSequenceNum").useCache(true).list()
                        for (EntityValue dbFormFieldEntOptsOrder in dbFormFieldEntOptsOrderList) {
                            entityFindNode.appendNode("order-by", ["field-name":dbFormFieldEntOptsOrder.entityFieldName])
                        }
                    }
                }

                // logger.warn("TOREMOVE Adding DbForm field [${fieldName}] widgetName [${widgetName}] at layout sequence [${dbFormField.getLong("layoutSequenceNum")}], node: ${newFieldNode}")
                if (dbFormField.getLong("layoutSequenceNum") != null) {
                    newFieldNode.attributes().put("layoutSequenceNum", dbFormField.getLong("layoutSequenceNum"))
                }
                mergeFieldNode(dbFormNode, newFieldNode, false)
            }

            dbFormNodeById.put(formId, dbFormNode)
        }

        return dbFormNode
    }

    /** This is the main method for using an XML Form, the rendering is done based on the Node returned. */
    Node getFormNode() {
        // NOTE: this is cached in the ScreenRenderImpl as it may be called multiple times for a single form render
        List<Node> dbFormNodeList = getDbFormNodeList()
        ExecutionContext ec = ecfi.getExecutionContext()
        boolean isDisplayOnly = ec.getContext().get("formDisplayOnly") == "true" || ec.getContext().get("formDisplayOnly_${formName}") == "true"

        if (isDynamic) {
            Node newFormNode = new Node(null, internalFormNode.name())
            initForm(internalFormNode, newFormNode)
            if (dbFormNodeList) {
                for (Node dbFormNode in dbFormNodeList) mergeFormNodes(newFormNode, dbFormNode, false, true)
            }
            return newFormNode
        } else if (dbFormNodeList || isDisplayOnly) {
            Node newFormNode = new Node(null, internalFormNode.name(), [:])
            // deep copy true to avoid bleed over of new fields and such
            mergeFormNodes(newFormNode, internalFormNode, true, true)
            // logger.warn("========== merging in dbFormNodeList: ${dbFormNodeList}", new BaseException("getFormNode call location"))
            for (Node dbFormNode in dbFormNodeList) mergeFormNodes(newFormNode, dbFormNode, false, true)

            if (isDisplayOnly) {
                // change all non-display fields to simple display elements
                for (Node fieldNode in newFormNode."field") {
                    // don't replace header form, should be just for searching: if (fieldNode."header-field") fieldSubNodeToDisplay(newFormNode, fieldNode, (Node) fieldNode."header-field"[0])
                    for (Node conditionalFieldNode in fieldNode."conditional-field") fieldSubNodeToDisplay(newFormNode, fieldNode, conditionalFieldNode)
                    if (fieldNode."default-field") fieldSubNodeToDisplay(newFormNode, fieldNode, (Node) fieldNode."default-field"[0])
                }
            }

            return newFormNode
        } else {
            return internalFormNode
        }
    }

    static Set displayOnlyIgnoreNodeNames = ["hidden", "ignored", "label", "image"] as Set
    protected void fieldSubNodeToDisplay(Node baseFormNode, Node fieldNode, Node fieldSubNode) {
        Node widgetNode = fieldSubNode.children() ? (Node) fieldSubNode.children().first() : null
        if (widgetNode == null) return
        if (widgetNode.name().toString().contains("display") || displayOnlyIgnoreNodeNames.contains(widgetNode.name())) return

        if (widgetNode.name() == "reset" || widgetNode.name() == "submit") {
            fieldSubNode.remove(widgetNode)
            return
        }

        if (widgetNode.name() == "link") {
            // if it goes to a transition with service-call or actions then remove it, otherwise leave it
            if ((!widgetNode."@url-type" || widgetNode."@url-type" == "transition") &&
                    sd.getTransitionItem((String) widgetNode."@url", null).hasActionsOrSingleService()) {
                fieldSubNode.remove(widgetNode)
            }
            return
        }

        // otherwise change it to a display Node
        widgetNode.replaceNode { node -> new Node(fieldSubNode, "display") }
        // not as good, puts it after other child Nodes: fieldSubNode.remove(widgetNode); fieldSubNode.appendNode("display")
    }

    FtlNodeWrapper getFtlFormNode() { return FtlNodeWrapper.wrapNode(getFormNode()) }

    boolean isUpload(Node cachedFormNode) {
        if (isUploadForm != null) return isUploadForm

        // if there is a "file" element, then it's an upload form
        boolean internalFileNode = internalFormNode.depthFirst().find({ it instanceof Node && it.name() == "file" }) as boolean
        if (internalFileNode) {
            isUploadForm = true
            return true
        } else {
            if (isDynamic || hasDbExtensions) {
                Node testNode = cachedFormNode ?: getFormNode()
                return testNode.depthFirst().find({ it instanceof Node && it.name() == "file" }) as boolean
            } else {
                return false
            }
        }
    }
    boolean isFormHeaderForm(Node cachedFormNode) {
        if (isFormHeaderFormVal != null) return isFormHeaderFormVal

        // if there is a "header-field" element, then it needs a header form
        boolean internalFormHeaderFormVal = false
        for (Node hfNode in (Collection<Node>) internalFormNode.depthFirst().findAll({ it instanceof Node && it.name() == "header-field" })) {
            if (hfNode.children()) {
                internalFormHeaderFormVal = true
                break
            }
        }
        if (internalFormHeaderFormVal) {
            isFormHeaderFormVal = true
            return true
        } else {
            if (isDynamic || hasDbExtensions) {
                boolean extFormHeaderFormVal = false
                Node testNode = cachedFormNode ?: getFormNode()
                for (Node hfNode in (Collection<Node>) testNode.depthFirst().findAll({ it instanceof Node && it.name() == "header-field" })) {
                    if (hfNode.children()) {
                        extFormHeaderFormVal = true
                        break
                    }
                }
                return extFormHeaderFormVal
            } else {
                return false
            }
        }
    }

    Node getFieldInParameterNode(String fieldName, Node cachedFormNode) {
        Node formNodeToUse = cachedFormNode ?: getFormNode()
        Node fieldNode = (Node) formNodeToUse."field".find({ it.@name == fieldName })
        if (fieldNode == null) throw new IllegalArgumentException("Tried to get in-parameter node for field [${fieldName}] that doesn't exist in form [${location}]")
        if (fieldNode."@validate-service") {
            ServiceDefinition sd = ecfi.serviceFacade.getServiceDefinition((String) fieldNode."@validate-service")
            if (sd == null) throw new IllegalArgumentException("Bad validate-service name [${fieldNode."@validate-service"}] in field [${fieldName}] of form [${location}]")
            Node parameterNode = sd.getInParameter(fieldNode."@validate-parameter" ?: fieldName)
            return parameterNode
        }
        return null
    }

    void addAutoServiceField(ServiceDefinition sd, EntityDefinition nounEd, Node parameterNode, String fieldType,
                             String serviceVerb, Node newFieldNode, Node subFieldNode, Node baseFormNode) {
        // if the parameter corresponds to an entity field, we can do better with that
        EntityDefinition fieldEd = nounEd
        if (parameterNode."@entity-name") fieldEd = ecfi.entityFacade.getEntityDefinition((String) parameterNode."@entity-name")
        String fieldName = parameterNode."@field-name" ?: parameterNode."@name"
        if (fieldEd != null && fieldEd.getFieldNode(fieldName) != null) {
            addAutoEntityField(fieldEd, fieldName, fieldType, serviceVerb, newFieldNode, subFieldNode, baseFormNode)
            return
        }

        // otherwise use the old approach and do what we can with the service def
        String spType = parameterNode."@type" ?: "String"
        String efType = fieldEd != null ? fieldEd.getFieldNode((String) parameterNode."@name")?."@type" : null

        switch (fieldType) {
            case "edit":
                // lastUpdatedStamp is always hidden for edit (needed for optimistic lock)
                if (parameterNode."@name" == "lastUpdatedStamp") {
                    subFieldNode.appendNode("hidden")
                    break
                }

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
                break
            case "find":
                if (spType.endsWith("Date") && spType != "java.util.Date") {
                    subFieldNode.appendNode("date-find", [type:"date", format:parameterNode."@format"])
                } else if (spType.endsWith("Time")) {
                    subFieldNode.appendNode("date-find", [type:"time", format:parameterNode."@format"])
                } else if (spType.endsWith("Timestamp") || spType == "java.util.Date") {
                    subFieldNode.appendNode("date-find", [type:"date-time", format:parameterNode."@format"])
                } else if (spType.endsWith("BigDecimal") || spType.endsWith("BigInteger") || spType.endsWith("Long") ||
                        spType.endsWith("Integer") || spType.endsWith("Double") || spType.endsWith("Float") ||
                        spType.endsWith("Number")) {
                    subFieldNode.appendNode("range-find")
                } else {
                    subFieldNode.appendNode("text-find")
                }
                break
            case "display":
                subFieldNode.appendNode("display", [format:parameterNode."@format"])
                break
            case "find-display":
                Node headerFieldNode = newFieldNode.appendNode("header-field")
                if (spType.endsWith("Date") && spType != "java.util.Date") {
                    headerFieldNode.appendNode("date-find", [type:"date", format:parameterNode."@format"])
                } else if (spType.endsWith("Time")) {
                    headerFieldNode.appendNode("date-find", [type:"time", format:parameterNode."@format"])
                } else if (spType.endsWith("Timestamp") || spType == "java.util.Date") {
                    headerFieldNode.appendNode("date-find", [type:"date-time", format:parameterNode."@format"])
                } else if (spType.endsWith("BigDecimal") || spType.endsWith("BigInteger") || spType.endsWith("Long") ||
                        spType.endsWith("Integer") || spType.endsWith("Double") || spType.endsWith("Float") ||
                        spType.endsWith("Number")) {
                    headerFieldNode.appendNode("range-find")
                } else {
                    headerFieldNode.appendNode("text-find")
                }
                subFieldNode.appendNode("display", [format:parameterNode."@format"])
                break
            case "hidden":
                subFieldNode.appendNode("hidden")
                break
        }
    }
    void addServiceFields(ServiceDefinition sd, String include, String fieldType, Node baseFormNode,
                          ExecutionContextFactoryImpl ecfi) {
        String serviceVerb = sd.verb
        //String serviceType = sd.serviceNode."@type"
        EntityDefinition nounEd = null
        try {
            nounEd = ecfi.entityFacade.getEntityDefinition(sd.noun)
        } catch (EntityException e) {
            if (logger.isTraceEnabled()) logger.trace("Ignoring entity exception, may not be real entity name: ${e.toString()}")
        }

        List<Node> parameterNodes = []
        if (include == "in" || include == "all") parameterNodes.addAll((Collection<Node>) sd.serviceNode."in-parameters"[0]."parameter")
        if (include == "out" || include == "all") parameterNodes.addAll((Collection<Node>) sd.serviceNode."out-parameters"[0]."parameter")

        for (Node parameterNode in parameterNodes) {
            Node newFieldNode = new Node(null, "field", [name:parameterNode."@name",
                    "validate-service":sd.serviceName, "validate-parameter":parameterNode."@name"])
            Node subFieldNode = newFieldNode.appendNode("default-field")
            addAutoServiceField(sd, nounEd, parameterNode, fieldType, serviceVerb, newFieldNode, subFieldNode, baseFormNode)
            mergeFieldNode(baseFormNode, newFieldNode, false)
        }
    }

    void addEntityFields(EntityDefinition ed, String include, String fieldType, String serviceVerb, Node baseFormNode) {
        for (String fieldName in ed.getFieldNames(include == "all" || include == "pk", include == "all" || include == "nonpk", include == "all" || include == "nonpk")) {
            Node newFieldNode = new Node(null, "field", [name:fieldName])
            Node subFieldNode = newFieldNode.appendNode("default-field")

            addAutoEntityField(ed, fieldName, fieldType, serviceVerb, newFieldNode, subFieldNode, baseFormNode)

            // logger.info("Adding form auto entity field [${fieldName}] of type [${efType}], fieldType [${fieldType}] serviceVerb [${serviceVerb}], node: ${newFieldNode}")
            mergeFieldNode(baseFormNode, newFieldNode, false)
        }
        // logger.info("TOREMOVE: after addEntityFields formNode is: ${baseFormNode}")
    }

    void addAutoEntityField(EntityDefinition ed, String fieldName, String fieldType, String serviceVerb,
                            Node newFieldNode, Node subFieldNode, Node baseFormNode) {
        List<String> pkFieldNameSet = ed.getPkFieldNames()

        String efType = ed.getFieldNode(fieldName)."@type" ?: "text-long"

        // to see if this should be a drop-down with data from another entity,
        // find first relationship that has this field as the only key map and is not a many relationship
        Node oneRelNode = null
        Map oneRelKeyMap = null
        for (Node rn in ed.entityNode."relationship") {
            Map km = ed.getRelationshipExpandedKeyMap(rn)
            if (km.size() == 1 && km.containsKey(fieldName) && rn."@type" != "many" && rn."@is-one-reverse" != "true") {
                oneRelNode = rn
                oneRelKeyMap = km
            }
        }
        String relatedEntityName = oneRelNode?."@related-entity-name"
        EntityDefinition relatedEd = relatedEntityName ? ecfi.entityFacade.getEntityDefinition(relatedEntityName) : null
        String keyField = oneRelKeyMap?.keySet()?.iterator()?.next()
        String relKeyField = oneRelKeyMap?.values()?.iterator()?.next()
        String relDefaultDescriptionField = relatedEd?.getDefaultDescriptionField()

        switch (fieldType) {
        case "edit":
            // lastUpdatedStamp is always hidden for edit (needed for optimistic lock)
            if (fieldName == "lastUpdatedStamp") {
                subFieldNode.appendNode("hidden")
                break
            }

            if (pkFieldNameSet.contains(fieldName) && serviceVerb == "update") {
                subFieldNode.appendNode("hidden")
            } else {
                if (baseFormNode.name() == "form-list" && !newFieldNode."header-field")
                    newFieldNode.appendNode("header-field", ["show-order-by":"case-insensitive"])
                if (efType.startsWith("date") || efType.startsWith("time")) {
                    Node dateTimeNode = subFieldNode.appendNode("date-time", [type:efType])
                    if (fieldName == "fromDate") dateTimeNode.attributes().put("default-value", "\${ec.user.nowTimestamp}")
                } else if (efType == "text-very-long") {
                    subFieldNode.appendNode("text-area")
                } else if (efType == "text-indicator") {
                    Node dropDownNode = subFieldNode.appendNode("drop-down", ["allow-empty":"true"])
                    dropDownNode.appendNode("option", ["key":"Y"])
                    dropDownNode.appendNode("option", ["key":"N"])
                } else {
                    if (oneRelNode != null) {
                        String title = oneRelNode."@title"

                        if (relatedEd == null) subFieldNode.appendNode("text-line")

                        // use the combo-box just in case the drop-down as a default is over-constrained
                        Node dropDownNode = subFieldNode.appendNode("drop-down", ["combo-box":"true", "allow-empty":"true"])
                        Node entityOptionsNode = dropDownNode.appendNode("entity-options")
                        Node entityFindNode = entityOptionsNode.appendNode("entity-find",
                                ["entity-name":relatedEntityName, "list":"optionsValueList", "offset":0, "limit":200])
                        // don't require any permissions for this
                        boolean alreadyDisabled = this.ecfi.getExecutionContext().getArtifactExecution().disableAuthz()
                        try {
                            if (relatedEntityName == "moqui.basic.Enumeration") {
                                // make sure the title is an actual enumTypeId before adding condition
                                if (ecfi.entityFacade.makeFind("moqui.basic.EnumerationType").condition("enumTypeId", title).count() > 0) {
                                    entityFindNode.appendNode("econdition", ["field-name":"enumTypeId", "value":title])
                                }
                            } else if (relatedEntityName == "moqui.basic.StatusItem") {
                                // make sure the title is an actual statusTypeId before adding condition
                                if (ecfi.entityFacade.makeFind("moqui.basic.StatusType").condition("statusTypeId", title).count() > 0) {
                                    entityFindNode.appendNode("econdition", ["field-name":"statusTypeId", "value":title])
                                }
                            }
                        } finally {
                            if (!alreadyDisabled) this.ecfi.getExecutionContext().getArtifactExecution().enableAuthz()
                        }

                        if (relDefaultDescriptionField) {
                            entityOptionsNode.attributes().put("text", "\${" + relDefaultDescriptionField + "} [\${" + relKeyField + "}]")
                            entityFindNode.appendNode("order-by", ["field-name":relDefaultDescriptionField])
                        }
                    } else {
                        subFieldNode.appendNode("text-line")
                    }
                }
            }
            break
        case "find":
            if (baseFormNode.name() == "form-list" && !newFieldNode."header-field")
                newFieldNode.appendNode("header-field", ["show-order-by":"case-insensitive"])
            if (efType.startsWith("date") || efType.startsWith("time")) {
                subFieldNode.appendNode("date-find", [type:efType])
            } else if (efType.startsWith("number-") || efType.startsWith("currency-")) {
                subFieldNode.appendNode("range-find")
            } else {
                subFieldNode.appendNode("text-find")
            }
            break
        case "display":
            if (baseFormNode.name() == "form-list" && !newFieldNode."header-field")
                newFieldNode.appendNode("header-field", ["show-order-by":"case-insensitive"])
            if (oneRelNode != null) subFieldNode.appendNode("display-entity",
                    ["entity-name":oneRelNode."@related-entity-name", "text":"\${" + relDefaultDescriptionField + "} [\${" + relKeyField + "}]"])
            else subFieldNode.appendNode("display")
            break
        case "find-display":
            if (baseFormNode.name() == "form-list" && !newFieldNode."header-field")
                newFieldNode.appendNode("header-field", ["show-order-by":"case-insensitive"])
            Node headerFieldNode = newFieldNode."header-field" ?
                newFieldNode."header-field"[0] : newFieldNode.appendNode("header-field")
            if (efType.startsWith("date") || efType.startsWith("time")) {
                headerFieldNode.appendNode("date-find", [type:efType])
            } else if (efType.startsWith("number-") || efType.startsWith("currency-")) {
                headerFieldNode.appendNode("range-find")
            } else {
                headerFieldNode.appendNode("text-find")
            }
            if (oneRelNode != null) {
                subFieldNode.appendNode("display-entity", ["entity-name":oneRelNode."@related-entity-name",
                        "text":"\${" + relDefaultDescriptionField + "} [\${" + relKeyField + "}]"])
            } else {
                subFieldNode.appendNode("display")
            }
            break
        case "hidden":
            subFieldNode.appendNode("hidden")
            break
        }

        // NOTE: don't like where this is located, would be nice to have a generic way for forms to add this sort of thing
        if (oneRelNode != null) {
            if (internalFormNode."@name" == "UpdateMasterEntityValue") {
                Node linkNode = subFieldNode.appendNode("link",
                        ["url":"edit", "text":"Edit ${relatedEd.getPrettyName(null, null)} [\${fieldValues." + keyField + "}]"])
                linkNode.appendNode("parameter", [name:"aen", value:relatedEntityName])
                linkNode.appendNode("parameter", [name:relKeyField, from:"fieldValues.${keyField}"])
            }
        }
    }

    protected void expandFieldNode(Node baseFormNode, Node fieldNode) {
        if (fieldNode."header-field") expandFieldSubNode(baseFormNode, fieldNode, (Node) fieldNode."header-field"[0])
        for (Node conditionalFieldNode in fieldNode."conditional-field") expandFieldSubNode(baseFormNode, fieldNode, conditionalFieldNode)
        if (fieldNode."default-field") expandFieldSubNode(baseFormNode, fieldNode, (Node) fieldNode."default-field"[0])
    }

    protected void expandFieldSubNode(Node baseFormNode, Node fieldNode, Node fieldSubNode) {
        Node widgetNode = fieldSubNode.children() ? (Node) fieldSubNode.children().first() : null
        if (widgetNode == null) return
        if (widgetNode.name() == "auto-widget-service") {
            fieldSubNode.remove(widgetNode)
            addAutoWidgetServiceNode(baseFormNode, fieldNode, fieldSubNode, widgetNode)
        } else if (widgetNode.name() == "auto-widget-entity") {
            fieldSubNode.remove(widgetNode)
            addAutoWidgetEntityNode(baseFormNode, fieldNode, fieldSubNode, widgetNode)
        } else if (widgetNode.name() == "widget-template-include") {
            List setNodeList = widgetNode."set"

            String templateLocation = widgetNode."@location"
            if (!templateLocation) throw new IllegalArgumentException("widget-template-include.@location cannot be empty")
            if (!templateLocation.contains("#")) throw new IllegalArgumentException("widget-template-include.@location must contain a hash/pound sign to separate the file location and widget-template.@name: [${templateLocation}]")
            String fileLocation = templateLocation.substring(0, templateLocation.indexOf("#"))
            String widgetTemplateName = templateLocation.substring(templateLocation.indexOf("#") + 1)

            Node widgetTemplatesNode = ecfi.getScreenFacade().getWidgetTemplatesNodeByLocation(fileLocation)
            Node widgetTemplateNode = (Node) widgetTemplatesNode?.find({ it."@name" == widgetTemplateName })
            if (widgetTemplateNode == null) throw new IllegalArgumentException("Could not find widget-template [${widgetTemplateName}] in [${fileLocation}]")
            boolean isFirst = true
            for (Node widgetChildNode in (Collection<Node>) widgetTemplateNode.children()) {
                if (isFirst) {
                    widgetNode.replaceNode { node -> StupidUtilities.deepCopyNode(widgetChildNode, fieldSubNode) }
                    isFirst = false
                } else {
                    fieldSubNode.append(StupidUtilities.deepCopyNode(widgetChildNode))
                }
            }

            for (Node setNode in setNodeList) fieldSubNode.append(StupidUtilities.deepCopyNode(setNode))
        }
    }

    protected Node addAutoWidgetServiceNode(Node baseFormNode, Node fieldNode, Node fieldSubNode, Node widgetNode) {
        String serviceName = widgetNode."@service-name"
        if (isDynamic) serviceName = ecfi.resourceFacade.evaluateStringExpand(serviceName, "")
        ServiceDefinition serviceDef = ecfi.serviceFacade.getServiceDefinition(serviceName)
        if (serviceDef != null) {
            addAutoServiceField(serviceDef, (String) widgetNode."@parameter-name"?:fieldNode."@name",
                    widgetNode."@field-type"?:"edit", fieldNode, fieldSubNode, baseFormNode)
            return
        }
        if (serviceName.contains("#")) {
            EntityDefinition ed = ecfi.entityFacade.getEntityDefinition(serviceName.substring(serviceName.indexOf("#")+1))
            if (ed != null) {
                addAutoEntityField(ed, (String) widgetNode."@parameter-name"?:fieldNode."@name", widgetNode."@field-type"?:"edit",
                        serviceName.substring(0, serviceName.indexOf("#")), fieldNode, fieldSubNode, baseFormNode)
                return
            }
        }
        throw new IllegalArgumentException("Cound not find service [${serviceName}] or entity noun referred to in auto-fields-service of form [${newFormNode."@name"}] of screen [${sd.location}]")

    }
    void addAutoServiceField(ServiceDefinition sd, String parameterName, String fieldType,
                             Node newFieldNode, Node subFieldNode, Node baseFormNode) {
        EntityDefinition nounEd = null
        try {
            nounEd = ecfi.entityFacade.getEntityDefinition(sd.noun)
        } catch (EntityException e) {
            // ignore, anticipating there may be no entity def
            if (logger.isTraceEnabled()) logger.trace("Ignoring entity exception, not necessarily an entity name: ${e.toString()}")
        }
        Node parameterNode = sd.serviceNode."in-parameters"[0].find({ it."@name" == parameterName })

        if (parameterNode == null) throw new IllegalArgumentException("Cound not find parameter [${parameterName}] in service [${sd.serviceName}] referred to in auto-widget-service of form [${baseFormNode."@name"}] of screen [${sd.location}]")
        addAutoServiceField(sd, nounEd, parameterNode, fieldType, sd.verb, newFieldNode, subFieldNode, baseFormNode)
    }

    protected void addAutoWidgetEntityNode(Node baseFormNode, Node fieldNode, Node fieldSubNode, Node widgetNode) {
        String entityName = widgetNode."@entity-name"
        if (isDynamic) entityName = ecfi.resourceFacade.evaluateStringExpand(entityName, "")
        EntityDefinition ed = null
        try {
            ed = ecfi.entityFacade.getEntityDefinition(entityName)
        } catch (EntityException e) {
            // ignore, anticipating there may be no entity def
            if (logger.isTraceEnabled()) logger.trace("Ignoring entity exception, not necessarily an entity name: ${e.toString()}")
        }
        if (ed == null) throw new IllegalArgumentException("Cound not find entity [${entityName}] referred to in auto-widget-entity of form [${baseFormNode."@name"}] of screen [${sd.location}]")
        addAutoEntityField(ed, (String) widgetNode."@field-name"?:fieldNode."@name", widgetNode."@field-type"?:"find-display",
                null, fieldNode, fieldSubNode, baseFormNode)

    }

    protected static void mergeFormNodes(Node baseFormNode, Node overrideFormNode, boolean deepCopy, boolean copyFields) {
        if (overrideFormNode.attributes()) baseFormNode.attributes().putAll(overrideFormNode.attributes())

        // if overrideFormNode has any row-actions add them all to the ones of the baseFormNode, ie both will run
        if (overrideFormNode."row-actions") {
            if (!baseFormNode."row-actions") baseFormNode.appendNode("row-actions")
            Node baseRowActionsNode = baseFormNode."row-actions"[0]
            for (Node actionNode in overrideFormNode."row-actions") baseRowActionsNode.append(actionNode)
        }

        if (copyFields) {
            for (Node overrideFieldNode in overrideFormNode."field") {
                mergeFieldNode(baseFormNode, overrideFieldNode, deepCopy)
            }
        }

        if (overrideFormNode."field-layout") {
            // just use entire override field-layout, don't try to merge
            if (baseFormNode."field-layout") baseFormNode.remove((Node) baseFormNode."field-layout"[0])
            baseFormNode.append(StupidUtilities.deepCopyNode((Node) overrideFormNode."field-layout"[0]))
        }
        if (overrideFormNode."form-list-column") {
            // if there are any form-list-column remove all from base and copy all from override
            if (baseFormNode."form-list-column") {
                for (Node flcNode in overrideFormNode."form-list-column") baseFormNode.remove(flcNode)
            }
            for (Node flcNode in overrideFormNode."form-list-column") baseFormNode.append(StupidUtilities.deepCopyNode(flcNode))
        }
    }

    protected static void mergeFieldNode(Node baseFormNode, Node overrideFieldNode, boolean deepCopy) {
        Node baseFieldNode = (Node) baseFormNode."field".find({ it."@name" == overrideFieldNode."@name" })
        if (baseFieldNode != null) {
            baseFieldNode.attributes().putAll(overrideFieldNode.attributes())

            if (overrideFieldNode."header-field") {
                if (baseFieldNode."header-field") baseFieldNode.remove((Node) baseFieldNode."header-field"[0])
                baseFieldNode.append((Node) overrideFieldNode."header-field"[0])
            }
            for (Node overrideConditionalFieldNode in overrideFieldNode."conditional-field") {
                Node baseConditionalFieldNode = (Node) baseFieldNode."conditional-field"
                        .find({ it."@condition" == overrideConditionalFieldNode."@condition" })
                if (baseConditionalFieldNode != null) baseFieldNode.remove(baseConditionalFieldNode)
                baseFieldNode.append(overrideConditionalFieldNode)
            }
            if (overrideFieldNode."default-field") {
                if (baseFieldNode."default-field") baseFieldNode.remove((Node) baseFieldNode."default-field"[0])
                baseFieldNode.append((Node) overrideFieldNode."default-field"[0])
            }
        } else {
            baseFormNode.append(deepCopy ? StupidUtilities.deepCopyNode(overrideFieldNode) : overrideFieldNode)
            // this is a new field... if the form has a field-layout element add a reference under that too
            if (baseFormNode."field-layout") addFieldToFieldLayout(baseFormNode, overrideFieldNode)
        }
    }

    static void addFieldToFieldLayout(Node formNode, Node fieldNode) {
        Node fieldLayoutNode = formNode."field-layout"[0]
        Long layoutSequenceNum = fieldNode.attribute("layoutSequenceNum") as Long
        if (layoutSequenceNum == null) {
            fieldLayoutNode.appendNode("field-ref", [name:fieldNode."@name"])
        } else {
            formNode.remove(fieldLayoutNode)
            Node newFieldLayoutNode = formNode.appendNode("field-layout", fieldLayoutNode.attributes())
            int index = 0
            boolean addedNode = false
            for (Node child in (Collection<Node>) fieldLayoutNode.children()) {
                if (index == layoutSequenceNum) {
                    newFieldLayoutNode.appendNode("field-ref", [name:fieldNode."@name"])
                    addedNode = true
                }
                newFieldLayoutNode.append(child)
                index++
            }
            if (!addedNode) {
                newFieldLayoutNode.appendNode("field-ref", [name:fieldNode."@name"])
            }
        }
    }

    void runFormListRowActions(ScreenRenderImpl sri, Object listEntry, int index, boolean hasNext) {
        // NOTE: this runs in a pushed-/sub-context, so just drop it in and it'll get cleaned up automatically
        Node localFormNode = getFormNode()
        if (localFormNode."@list-entry") {
            sri.ec.context.put((String) localFormNode."@list-entry", listEntry)
            sri.ec.context.put(((String) localFormNode."@list-entry") + "_index", index)
            sri.ec.context.put(((String) localFormNode."@list-entry") + "_has_next", hasNext)
        } else {
            if (listEntry instanceof Map) {
                sri.ec.context.putAll((Map) listEntry)
            } else {
                sri.ec.context.put("listEntry", listEntry)
            }
            sri.ec.context.put(((String) localFormNode."@list") + "_index", index)
            sri.ec.context.put(((String) localFormNode."@list") + "_has_next", hasNext)
        }
        if (rowActions) rowActions.run(sri.ec)
    }

    static ListOrderedMap getFieldOptions(Node widgetNode, ExecutionContext ec) {
        Node fieldNode = widgetNode.parent().parent()
        ListOrderedMap options = new ListOrderedMap()
        for (Node childNode in (Collection<Node>) widgetNode.children()) {
            if (childNode.name() == "entity-options") {
                Node entityFindNode = childNode."entity-find"[0]
                EntityFindImpl ef = (EntityFindImpl) ec.entity.makeFind((String) entityFindNode."@entity-name")
                ef.findNode(entityFindNode)

                EntityList eli = ef.list()

                if (ef.getUseCache()) {
                    // do the date filtering after the query
                    for (Node df in (Collection<Node>) entityFindNode["date-filter"]) {
                        EntityCondition dateEc = ec.entity.conditionFactory.makeConditionDate((String) df["@from-field-name"] ?: "fromDate",
                                (String) df["@thru-field-name"] ?: "thruDate",
                                (df["@valid-date"] ? ec.resource.evaluateContextField((String) df["@valid-date"], null) as Timestamp : ec.user.nowTimestamp))
                        // logger.warn("TOREMOVE getFieldOptions cache=${ef.getUseCache()}, dateEc=${dateEc} list before=${eli}")
                        eli = eli.filterByCondition(dateEc, true)
                    }
                }

                for (EntityValue ev in eli) {
                    ec.context.push(ev)
                    addFieldOption(options, fieldNode, childNode, ev, ec)
                    ec.context.pop()
                }
            } else if (childNode.name() == "list-options") {
                Object listObject = ec.resource.evaluateContextField((String) childNode."@list", null)
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
                    for (Object listOption in listObject) {
                        if (listOption instanceof Map) {
                            addFieldOption(options, fieldNode, childNode, listOption, ec)
                        } else {
                            options.put(listOption, listOption)
                            // addFieldOption(options, fieldNode, childNode, [entry:listOption], ec)
                        }
                    }
                }
            } else if (childNode.name() == "option") {
                String key = ec.resource.evaluateStringExpand((String) childNode."@key", null)
                String text = ec.resource.evaluateStringExpand((String) childNode."@text", null)
                options.put(key, text ?: key)
            }
        }
        return options
    }

    static void addFieldOption(ListOrderedMap options, Node fieldNode, Node childNode, Map listOption,
                               ExecutionContext ec) {
        ec.context.push(listOption)
        try {
            String key = null
            if (childNode."@key") {
                key = ec.resource.evaluateStringExpand((String) childNode."@key", null)
                // we just did a string expand, if it evaluates to a literal "null" then there was no value
                if (key == "null") key = null
            } else if (listOption instanceof EntityValueImpl) {
                String keyFieldName = listOption.getEntityDefinition().getPkFieldNames().get(0)
                if (keyFieldName) key = ec.context.get(keyFieldName)
            }
            if (key == null) key = ec.context.get(fieldNode."@name")
            if (key == null) return

            String text = childNode."@text"
            if (!text) {
                if ((!(listOption instanceof EntityValueBase)
                            || ((EntityValueBase) listOption).getEntityDefinition().isField("description"))
                        && listOption["description"]) {
                    options.put(key, listOption["description"])
                } else {
                    options.put(key, key)
                }

            } else {
                String value = ec.resource.evaluateStringExpand(text, null)
                if (value == "null") value = key
                options.put(key, value)
            }
        } finally {
            ec.context.pop()
        }
    }
}
