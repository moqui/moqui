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
package org.moqui.impl.service

import org.moqui.impl.actions.XmlAction
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.StupidUtilities
import org.moqui.impl.FtlNodeWrapper

class ServiceDefinition {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceDefinition.class)

    protected ServiceFacadeImpl sfi
    protected Node serviceNode
    protected String path = null
    protected String verb = null
    protected String noun = null
    protected XmlAction xmlAction = null

    ServiceDefinition(ServiceFacadeImpl sfi, String path, Node sn) {
        this.sfi = sfi
        this.serviceNode = sn
        this.path = path
        this.verb = serviceNode."@verb"
        this.noun = serviceNode."@noun"

        Node inParameters = new Node(null, "in-parameters")
        Node outParameters = new Node(null, "out-parameters")

        // handle implements elements
        if (serviceNode."implements") for (Node implementsNode in serviceNode."implements") {
            String implServiceName = implementsNode."@service"
            String implRequired = implementsNode."@required" // no default here, only used if has a value
            ServiceDefinition sd = sfi.getServiceDefinition(implServiceName)
            if (sd == null) throw new IllegalArgumentException("Service [${implServiceName}] not found, specified in service.implements in service [${getServiceName()}]")

            // these are the first params to be set, so just deep copy them over
            if (sd.serviceNode."in-parameters"?.getAt(0)?."parameter") {
                for (Node parameter in sd.serviceNode."in-parameters"[0]."parameter") {
                    Node newParameter = StupidUtilities.deepCopyNode(parameter)
                    if (implRequired) newParameter.attributes().put("required", implRequired)
                    inParameters.append(newParameter)
                }
            }
            if (sd.serviceNode."out-parameters"?.getAt(0)?."parameter") {
                for (Node parameter in sd.serviceNode."out-parameters"[0]."parameter") {
                    Node newParameter = StupidUtilities.deepCopyNode(parameter)
                    if (implRequired) newParameter.attributes().put("required", implRequired)
                    outParameters.append(newParameter)
                }
            }
        }

        // expand auto-parameters in in-parameters and out-parameters
        if (serviceNode."in-parameters"?.getAt(0)?."auto-parameters")
            for (Node autoParameters in serviceNode."in-parameters"[0]."auto-parameters")
                mergeAutoParameters(inParameters, autoParameters)
        if (serviceNode."out-parameters"?.getAt(0)?."auto-parameters")
            for (Node autoParameters in serviceNode."out-parameters"[0]."auto-parameters")
                mergeAutoParameters(outParameters, autoParameters)

        // merge in the explicitly defined parameter elements
        if (serviceNode."in-parameters"?.getAt(0)?."parameter")
            for (Node parameterNode in serviceNode."in-parameters"[0]."parameter")
                mergeParameter(inParameters, parameterNode)
        if (serviceNode."out-parameters"?.getAt(0)?."parameter")
            for (Node parameterNode in serviceNode."out-parameters"[0]."parameter")
                mergeParameter(outParameters, parameterNode)

        // replace the in-parameters and out-parameters Nodes for the service
        if (serviceNode."in-parameters") serviceNode.remove(serviceNode."in-parameters"[0])
        serviceNode.append(inParameters)
        if (serviceNode."out-parameters") serviceNode.remove(serviceNode."out-parameters"[0])
        serviceNode.append(outParameters)

        if (logger.traceEnabled) logger.trace("After merge for service [${getServiceName()}] node is:\n${FtlNodeWrapper.prettyPrintNode(serviceNode)}")

        // if this is an inline service, get that now
        if (serviceNode."actions") {
            xmlAction = new XmlAction(sfi.ecfi, (Node) serviceNode."actions"[0], getServiceName())
        }
    }

    void mergeAutoParameters(Node parametersNode, Node autoParameters) {
        String entityName = autoParameters."@entity-name" ?: this.noun
        if (!entityName) throw new IllegalArgumentException("Error in auto-parameters in service [${getServiceName()}], no auto-parameters.@entity-name and no service.@noun for a default")
        EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(entityName)
        if (ed == null) throw new IllegalArgumentException("Error in auto-parameters in service [${getServiceName()}], the entity-name or noun [${entityName}] is not a valid entity name")

        Set<String> fieldsToExclude = new HashSet<String>()
        if (autoParameters."exclude") for (Node excludeNode in autoParameters."exclude") {
            fieldsToExclude.add(excludeNode."@field-name")
        }

        String includeStr = autoParameters."@include" ?: "all"
        String requiredStr = autoParameters."@required" ?: "false"
        String allowHtmlStr = autoParameters."@allow-html" ?: "none"
        for (String fieldName in ed.getFieldNames(includeStr == "all" || includeStr == "pk",
                includeStr == "all" || includeStr == "nonpk")) {
            String javaType = sfi.ecfi.entityFacade.getFieldJavaType(ed.getFieldNode(fieldName)."@type", entityName)
            mergeParameter(parametersNode, fieldName, [type:javaType, required:requiredStr, "allow-html":allowHtmlStr])
        }
    }

    void mergeParameter(Node parametersNode, Node overrideParameterNode) {
        Node baseParameterNode = mergeParameter(parametersNode, overrideParameterNode."@name",
                overrideParameterNode.attributes())
        // merge description, subtype, ParameterValidations
        for (Node childNode in overrideParameterNode.children()) {
            if (childNode.name() == "description" || childNode.name() == "subtype") {
                if (baseParameterNode[childNode.name()]) baseParameterNode.remove(baseParameterNode[childNode.name()][0])
            }
            // is a validation, just add it in, or the original has been removed so add the new one
            baseParameterNode.append(childNode)
        }
    }
    Node mergeParameter(Node parametersNode, String parameterName, Map attributeMap) {
        Node baseParameterNode = (Node) parametersNode."parameter".find({ it."@name" == parameterName })
        if (baseParameterNode == null) baseParameterNode = parametersNode.appendNode("parameter", [name:parameterName])
        baseParameterNode.attributes().putAll(attributeMap)
        return baseParameterNode
    }

    Node getServiceNode() { return serviceNode }

    String getServiceName() { return (path ? path + "." : "") + verb + (noun ? "#" + noun : "") }
    String getPath() { return path }
    String getVerb() { return verb }
    String getNoun() { return noun }

    static String getPathFromName(String serviceName) {
        if (!serviceName.contains(".")) return null
        return serviceName.substring(0, serviceName.lastIndexOf("."))
    }
    static String getVerbFromName(String serviceName) {
        String v = serviceName
        if (v.contains(".")) v = v.substring(v.lastIndexOf(".") + 1)
        if (v.contains("#")) v = v.substring(0, v.indexOf("#"))
        return v
    }
    static String getNounFromName(String serviceName) {
        if (!serviceName.contains("#")) return null
        return serviceName.substring(serviceName.lastIndexOf("#") + 1)
    }

    String getLocation() {
        // TODO: see if the location is an alias from the conf -> service-facade
        return serviceNode."@location"
    }

    XmlAction getXmlAction() { return xmlAction }

    Node getInParameter(String name) { return (Node) serviceNode."in-parameters"[0]."parameter".find({ it."@name" == name }) }
    Set<String> getInParameterNames() {
        Set<String> inNames = new HashSet()
        for (Node parameter in serviceNode."in-parameters"[0]."parameter") inNames.add(parameter."@name")
        return inNames
    }

    Node getOutParameter(String name) { return (Node) serviceNode."out-parameters"[0]."parameter".find({ it."@name" == name }) }
    Set<String> getOutParameterNames() {
        Set<String> outNames = new HashSet()
        for (Node parameter in serviceNode."out-parameters"[0]."parameter") outNames.add(parameter."@name")
        return outNames
    }
}
