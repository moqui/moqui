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
import org.moqui.impl.context.ExecutionContextImpl
import org.owasp.esapi.errors.ValidationException
import org.moqui.impl.StupidWebUtilities
import org.owasp.esapi.ValidationErrorList
import org.owasp.esapi.errors.IntrusionException
import org.apache.commons.validator.CreditCardValidator
import org.apache.commons.validator.UrlValidator
import org.apache.commons.validator.EmailValidator
import org.moqui.impl.context.ContextStack

class ServiceDefinition {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceDefinition.class)

    protected final static EmailValidator emailValidator = EmailValidator.getInstance()
    protected final static UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES)

    protected ServiceFacadeImpl sfi
    protected Node serviceNode
    protected String path = null
    protected String verb = null
    protected String noun = null
    protected XmlAction xmlAction = null

    protected String internalAuthenticate
    protected String internalServiceType
    protected boolean internalTxIgnore
    protected boolean internalTxForceNew
    protected Integer internalTransactionTimeout

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

        internalAuthenticate = serviceNode."@authenticate" ?: "true"
        internalServiceType = serviceNode."@type" ?: "inline"
        internalTxIgnore = (serviceNode."@transaction" == "ignore")
        internalTxForceNew = (serviceNode."@transaction" == "force-new")
        if (serviceNode."@transaction-timeout") {
            internalTransactionTimeout = serviceNode."@transaction-timeout" as Integer
        } else {
            internalTransactionTimeout = null
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
                includeStr == "all" || includeStr == "nonpk", includeStr == "all" || includeStr == "nonpk")) {
            if (fieldsToExclude.contains(fieldName)) continue

            String javaType = sfi.ecfi.entityFacade.getFieldJavaType(ed.getFieldNode(fieldName)."@type", entityName)
            mergeParameter(parametersNode, fieldName,
                    [type:javaType, required:requiredStr, "allow-html":allowHtmlStr,
                            "entity-name":entityName, "field-name":fieldName])
        }
    }

    void mergeParameter(Node parametersNode, Node overrideParameterNode) {
        Node baseParameterNode = mergeParameter(parametersNode, overrideParameterNode."@name",
                overrideParameterNode.attributes())
        // merge description, subtype, ParameterValidations
        for (Node childNode in overrideParameterNode.children()) {
            if (childNode.name() == "description" || childNode.name() == "subtype") {
                if (baseParameterNode[childNode.name()]) baseParameterNode.remove((Node) baseParameterNode[childNode.name()][0])
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

    String getAuthenticate() { return internalAuthenticate }
    String getServiceType() { return internalServiceType }
    boolean getTxIgnore() { return internalTxIgnore }
    boolean getTxForceNew() { return internalTxForceNew }
    Integer getTxTimeout() { return internalTransactionTimeout }

    static String getPathFromName(String serviceName) {
        String p = serviceName
        // do hash first since a noun following hash may have dots in it
        if (p.contains("#")) p = p.substring(0, p.indexOf("#"))
        if (!p.contains(".")) return null
        return p.substring(0, p.lastIndexOf("."))
    }
    static String getVerbFromName(String serviceName) {
        String v = serviceName
        // do hash first since a noun following hash may have dots in it
        if (v.contains("#")) v = v.substring(0, v.indexOf("#"))
        if (v.contains(".")) v = v.substring(v.lastIndexOf(".") + 1)
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

    void convertValidateCleanParameters(Map<String, Object> parameters, ExecutionContextImpl eci) {
        if (this.serviceNode."@validate" != "false") {
            checkParameterMap("", parameters, parameters, serviceNode."in-parameters"[0], eci)
        }
    }

    protected void checkParameterMap(String namePrefix, Map<String, Object> rootParameters, Map parameters,
                                     Node parametersParentNode, ExecutionContextImpl eci) {
        Set<String> parameterNames = new HashSet()
        for (Node parameter in parametersParentNode."parameter") parameterNames.add(parameter."@name")
        // if service is to be validated, go through service in-parameters definition and only get valid parameters
        // go through a set that is both the defined in-parameters and the keySet of passed in parameters
        Set<String> iterateSet = new HashSet(parameters.keySet())
        iterateSet.addAll(parameterNames)
        for (String parameterName in iterateSet) {
            if (!parameterNames.contains(parameterName)) {
                parameters.remove(parameterName)
                if (logger.traceEnabled && parameterName != "ec")
                    logger.trace("Parameter [${namePrefix}${parameterName}] was passed to service [${getServiceName()}] but is not defined as an in parameter, removing from parameters.")
                continue
            }

            Node parameterNode = (Node) parametersParentNode."parameter".find({ it."@name" == parameterName })
            Object parameterValue = parameters.get(parameterName)
            String type = parameterNode."@type" ?: "String"

            // check if required and empty - use groovy non-empty rules, except for Boolean objects if they are a
            //     non-null instanceof Boolean, then consider it not-empty (normally if false would eval to false)
            if (!parameterValue && !(parameterValue instanceof Boolean)) {
                if (parameterNode."@required" == "true") {
                    eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Field cannot be empty (service ${getServiceName()})", null)
                }
                // NOTE: should we change empty values to null? for now, no
                // if it isn't there continue on since since default-value, etc are handled below
            }

            // check type
            Object converted = checkConvertType(parameterNode, namePrefix, parameterName, parameterValue, rootParameters, eci)
            if (converted != null) {
                parameterValue = converted
            } else if (parameterValue) {
                // no type conversion? error time...
                eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Field was type [${parameterValue?.class?.name}], expecting type [${type}] (service ${getServiceName()})", null)
                continue
            }

            checkSubtype(parameterName, parameterNode, parameterValue, eci)
            validateParameterHtml(parameterNode, namePrefix, parameterName, parameterValue, eci)

            // do this after the convert so we can deal with objects when needed
            validateParameter(parameterNode, parameterName, parameterValue, eci)

            // put the final parameterValue back into the parameters Map
            parameters.put(parameterName, parameterValue)

            // now check parameter sub-elements
            if (parameterValue instanceof Map) {
                // any parameter sub-nodes?
                if (parameterNode."parameter")
                    checkParameterMap("${namePrefix}${parameterName}.", rootParameters, (Map) parameterValue, parameterNode, eci)
            } else if (parameterValue instanceof Node) {
                if (parameterNode."parameter")
                    checkParameterNode("${namePrefix}${parameterName}.", rootParameters, (Node) parameterValue, parameterNode, eci)
            }
        }
    }

    protected void checkParameterNode(String namePrefix, Map<String, Object> rootParameters, Node nodeValue,
                                     Node parametersParentNode, ExecutionContextImpl eci) {
        // NOTE: don't worry about extra attributes or sub-Nodes... let them through

        // go through attributes of Node, validate each that corresponds to a parameter def
        for (Map.Entry attrEntry in nodeValue.attributes()) {
            String parameterName = attrEntry.getKey()
            Node parameterNode = (Node) parametersParentNode."parameter".find({ it."@name" == parameterName })
            if (parameterNode == null) {
                // NOTE: consider complaining here to not allow additional attributes, that could be annoying though so for now do not...
                continue
            }

            Object parameterValue = nodeValue.attribute(parameterName)

            // NOTE: required check is done later, now just validating the parameters seen
            // NOTE: no type conversion for Node attributes, they are always String

            validateParameterHtml(parameterNode, namePrefix, parameterName, parameterValue, eci)

            // NOTE: only use the converted value for validation, attributes must be strings so can't put it back there
            Object converted = checkConvertType(parameterNode, namePrefix, parameterName, parameterValue, rootParameters, eci)
            validateParameter(parameterNode, parameterName, converted, eci)

            // NOTE: no sub-nodes here, it's an attribute, so ignore child parameter elements
        }

        // go through sub-Nodes and if corresponds to
        // - Node parameter, checkParameterNode
        // - otherwise, check type/etc
        // TODO - parameter with type Map, convert to Map? ...checkParameterMap; converting to Map would kill multiple values, or they could be put in a List, though that pattern is a bit annoying...
        for (Node childNode in nodeValue.children()) {
            String parameterName = childNode.name()
            Node parameterNode = (Node) parametersParentNode."parameter".find({ it."@name" == parameterName })
            if (parameterNode == null) {
                // NOTE: consider complaining here to not allow additional attributes, that could be annoying though so for now do not...
                continue
            }

            if (parameterNode."@type" == "Node" || parameterNode."@type" == "groovy.util.Node") {
                // recurse back into this method
                checkParameterNode("${namePrefix}${parameterName}.", rootParameters, childNode, parameterNode, eci)
            } else {
                Object parameterValue = childNode.text()

                // NOTE: required check is done later, now just validating the parameters seen
                // NOTE: no type conversion for Node attributes, they are always String

                validateParameterHtml(parameterNode, namePrefix, parameterName, parameterValue, eci)

                // NOTE: only use the converted value for validation, attributes must be strings so can't put it back there
                Object converted = checkConvertType(parameterNode, namePrefix, parameterName, parameterValue, rootParameters, eci)
                validateParameter(parameterNode, parameterName, converted, eci)
            }
        }

        // if there is text() under this node, use the _VALUE parameter node to validate
        Node textValueNode = (Node) parametersParentNode."parameter".find({ it."@name" == "_VALUE" })
        if (textValueNode != null) {
            Object parameterValue = nodeValue.text()
            if (!parameterValue) {
                if (textValueNode."@required" == "true") {
                    eci.message.addError("${namePrefix}_VALUE cannot be empty (service ${getServiceName()})")
                }
            } else {
                validateParameterHtml(textValueNode, namePrefix, "_VALUE", parameterValue, eci)

                // NOTE: only use the converted value for validation, attributes must be strings so can't put it back there
                Object converted = checkConvertType(textValueNode, namePrefix, "_VALUE", parameterValue, rootParameters, eci)
                validateParameter(textValueNode, "_VALUE", converted, eci)
            }
        }

        // check for missing parameters (no attribute or sub-Node) that are required
        for (Node parameterNode in parametersParentNode."parameter") {
            // skip _VALUE, checked above
            if (parameterNode."@name" == "_VALUE") continue

            if (parameterNode."@required" == "true") {
                String parameterName = parameterNode."@name"
                boolean valueFound = false
                if (nodeValue.attribute(parameterName)) {
                    valueFound = true
                } else {
                    for (Node childNode in nodeValue.children()) {
                        if (childNode.text()) {
                            valueFound = true
                            break
                        }
                    }
                }

                if (!valueFound)
                    eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Field cannot be empty (service ${getServiceName()})", null)
            }
        }
    }

    protected Object checkConvertType(Node parameterNode, String namePrefix, String parameterName, Object parameterValue,
                                      Map<String, Object> rootParameters, ExecutionContextImpl eci) {
        // set the default-value if applicable
        if (!parameterValue && !(parameterValue instanceof Boolean) && parameterNode."@default-value") {
            ((ContextStack) eci.context).push(rootParameters)
            parameterValue = eci.getResource().evaluateStringExpand(parameterNode."@default-value", "${this.location}_${parameterName}_default")
            // logger.warn("For parameter ${namePrefix}${parameterName} new value ${parameterValue} from default-value [${parameterNode.'@default-value'}] and context: ${eci.context}")
            ((ContextStack) eci.context).pop()
        }

        // if no default, don't try to convert
        if (!parameterValue) return null

        String type = parameterNode."@type" ?: "String"
        if (!StupidUtilities.isInstanceOf(parameterValue, type)) {
            // do type conversion if possible
            String format = parameterNode."@format"
            Object converted = null
            if (parameterValue instanceof String) {
                // try some String to XYZ specific conversions for parsing with format, locale, etc
                switch (type) {
                    case "Integer":
                    case "java.lang.Integer":
                    case "Long":
                    case "java.lang.Long":
                    case "Float":
                    case "java.lang.Float":
                    case "Double":
                    case "java.lang.Double":
                    case "BigDecimal":
                    case "java.math.BigDecimal":
                    case "BigInteger":
                    case "java.math.BigInteger":
                        BigDecimal bdVal = eci.l10n.parseNumber(parameterValue, format)
                        if (bdVal == null) {
                            eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""), null)
                        } else {
                            converted = StupidUtilities.basicConvert(bdVal, type)
                        }
                        break
                    case "Time":
                    case "java.sql.Time":
                        converted = eci.l10n.parseTime(parameterValue, format)
                        if (converted == null) {
                            eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""), null)
                        }
                        break
                    case "Date":
                    case "java.sql.Date":
                        converted = eci.l10n.parseDate(parameterValue, format)
                        if (converted == null) {
                            eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""), null)
                        }
                        break
                    case "Timestamp":
                    case "java.sql.Timestamp":
                        converted = eci.l10n.parseTimestamp(parameterValue, format)
                        if (converted == null) {
                            eci.message.addValidationError(null, "${namePrefix}${parameterName}", "Value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""), null)
                        }
                        break
                }
            }

            // fallback to a really simple type conversion
            if (converted == null) converted = StupidUtilities.basicConvert(parameterValue, type)

            return converted
        }
        return parameterValue
    }

    protected void validateParameterHtml(Node parameterNode, String namePrefix, String parameterName, Object parameterValue,
                                         ExecutionContextImpl eci) {
        // check for none/safe/any HTML
        if ((parameterValue instanceof String || parameterValue instanceof List) &&
                parameterNode."@allow-html" != "any") {
            boolean allowSafe = (parameterNode."@allow-html" == "safe")

            if (parameterValue instanceof String) {
                parameterValue = canonicalizeAndCheckHtml(parameterName, parameterValue, allowSafe, eci)
            } else {
                List lst = parameterValue as List
                parameterValue = new ArrayList(lst.size())
                for (Object obj in lst) {
                    if (obj instanceof String)
                        parameterValue.add(canonicalizeAndCheckHtml(parameterName, obj, allowSafe, eci))
                }
            }
        }
    }

    protected boolean validateParameter(Node vpNode, String parameterName, Object pv, ExecutionContextImpl eci) {
        // run through validations under parameter node

        // no validation done if value is empty, that should be checked with the required attribute only
        if (!pv && !(pv instanceof Boolean)) return true

        boolean allPass = true
        for (Node child in vpNode.children()) {
            if (child.name() == "description" || child.name() == "subtype") continue
            // NOTE don't break on fail, we want to get a list of all failures for the user to see
            try {
                if (!validateParameterSingle(child, parameterName, pv, eci)) allPass = false
            } catch (Throwable t) {
                logger.error("Error in validation", t)
                eci.message.addValidationError(null, parameterName, "Value [${pv}] failed [${child.name()}] validation (${t.message})", null)
            }
        }
        return allPass
    }

    protected boolean validateParameterSingle(Node valNode, String parameterName, Object pv, ExecutionContextImpl eci) {
        switch (valNode.name()) {
        case "val-or":
            boolean anyPass = false
            for (Node child in valNode.children()) if (validateParameterSingle(child, parameterName, pv, eci)) anyPass = true
            return anyPass
        case "val-and":
            boolean allPass = true
            for (Node child in valNode.children()) if (!validateParameterSingle(child, parameterName, pv, eci)) allPass = false
            return allPass
        case "val-not":
            // just in case there are multiple children treat like and, then not it
            boolean allPass = true
            for (Node child in valNode.children()) if (!validateParameterSingle(child, parameterName, pv, eci)) allPass = false
            return !allPass
        case "matches":
            if (!(pv instanceof String)) {
                eci.message.addValidationError(null, parameterName, "Value [${pv}] is not a String, cannot do matches validation.", null)
                return false
            }
            if (valNode."@regexp" && !((String) pv).matches((String) valNode."@regexp")) {
                // a message attribute should always be there, but just in case we'll have a default
                eci.message.addValidationError(null, parameterName, valNode."@message" ?: "Value [${pv}] did not match expression [${valNode."@regexp"}]", null)
                return false
            }
            return true
        case "number-range":
            // go to BigDecimal through String to get more accurate value
            BigDecimal bdVal = new BigDecimal(pv as String)
            if (valNode."@min") {
                BigDecimal min = new BigDecimal((String) valNode."@min")
                if (valNode."@min-include-equals" == "false") {
                    if (bdVal <= min) {
                        eci.message.addValidationError(null, parameterName, "Value [${pv}] must be greater than ${min}.", null)
                        return false
                    }
                } else {
                    if (bdVal < min) {
                        eci.message.addValidationError(null, parameterName, "Value [${pv}] must be greater than or equal to ${min}.", null)
                        return false
                    }
                }
            }
            if (valNode."@max") {
                BigDecimal max = new BigDecimal((String) valNode."@max")
                if (valNode."@max-include-equals" == "true") {
                    if (max > bdVal) {
                        eci.message.addValidationError(null, parameterName, "Value [${pv}] must be less than or equal to ${max}.", null)
                        return false
                    }
                } else {
                    if (max >= bdVal) {
                        eci.message.addValidationError(null, parameterName, "Value [${pv}] must be less than ${max}.", null)
                        return false
                    }
                }
            }
            return true
        case "number-integer":
            try {
                new BigInteger(pv as String)
            } catch (NumberFormatException e) {
                eci.message.addValidationError(null, parameterName, "Value [${pv}] is not a whole (integer) number.", null)
                return false
            }
            return true
        case "number-decimal":
            try {
                new BigDecimal(pv as String)
            } catch (NumberFormatException e) {
                eci.message.addValidationError(null, parameterName, "Value [${pv}] is not a decimal number.", null)
                return false
            }
            return true
        case "text-length":
            String str = pv as String
            if (valNode."@min") {
                int min = valNode."@min" as int
                if (str.length() < min) {
                    eci.message.addValidationError(null, parameterName, "Value [${pv}], length ${str.length()}, is shorter than ${min} characters.", null)
                    return false
                }
            }
            if (valNode."@max") {
                int max = valNode."@max" as int
                if (max >= str.length()) {
                    eci.message.addValidationError(null, parameterName, "Value: [${pv}] length ${str.length()}, is longer than ${max} characters.", null)
                    return false
                }
            }
            return true
        case "text-email":
            String str = pv as String
            if (!emailValidator.isValid(str)) {
                eci.message.addValidationError(null, parameterName, "Value [${str}] is not a valid email address.", null)
                return false
            }
            return true
        case "text-url":
            String str = pv as String
            if (!urlValidator.isValid(str)) {
                eci.message.addValidationError(null, parameterName, "Value [${str}] is not a valid URL.", null)
                return false
            }
            return true
        case "text-letters":
            String str = pv as String
            for (char c in str) {
                if (!Character.isLetter(c)) {
                    eci.message.addValidationError(null, parameterName, "Value [${str}] must have only letters.", null)
                    return false
                }
            }
            return true
        case "text-digits":
            String str = pv as String
            for (char c in str) {
                if (!Character.isDigit(c)) {
                    eci.message.addValidationError(null, parameterName, "Value [${str}] must have only digits.", null)
                    return false
                }
            }
            return true
        case "time-range":
            Calendar cal
            String format = valNode."@format"
            if (pv instanceof String) {
                cal = eci.getL10n().parseDateTime((String) pv, format)
            } else {
                // try letting groovy convert it
                cal = Calendar.getInstance()
                // TODO: not sure if this will work: ((pv as java.util.Date).getTime())
                cal.setTimeInMillis((pv as java.util.Date).getTime())
            }
            if (valNode."@after") {
                // handle after date/time/date-time depending on type of parameter, support "now" too
                String valString = valNode."@after"
                Calendar compareCal
                if (valString == "now") {
                    compareCal = Calendar.getInstance()
                    compareCal.setTimeInMillis(eci.user.nowTimestamp.time)
                } else {
                    compareCal = eci.l10n.parseDateTime(valString, format)
                }
                if (!cal.after(compareCal)) {
                    eci.message.addValidationError(null, parameterName, "Value [${pv}] is before ${valString}.", null)
                    return false
                }
            }
            if (valNode."@before") {
                // handle after date/time/date-time depending on type of parameter, support "now" too
                String valString = valNode."@before"
                Calendar compareCal
                if (valString == "now") {
                    compareCal = Calendar.getInstance()
                    compareCal.setTimeInMillis(eci.user.nowTimestamp.time)
                } else {
                    compareCal = eci.l10n.parseDateTime(valString, format)
                }
                if (!cal.before(compareCal)) {
                    eci.message.addValidationError(null, parameterName, "Value [${pv}] is after ${valString}.", null)
                    return false
                }
            }
            return true
        case "credit-card":
            CreditCardValidator ccv = new CreditCardValidator()
            if (valNode."@types") {
                for (String cts in ((String) valNode."@types").split(","))
                    ccv.addAllowedCardType(creditCardTypeMap.get(cts.trim()))
            } else {
                for (def cct in creditCardTypeMap.values()) ccv.addAllowedCardType(cct)
            }
            String str = pv as String
            if (!ccv.isValid(str)) {
                eci.message.addValidationError(null, parameterName, "Value [${str}] is not a valid credit card number.", null)
                return false
            }
            return true
        }
        // shouldn't get here, but just in case
        return true
    }

    protected Object canonicalizeAndCheckHtml(String parameterName, String parameterValue, boolean allowSafe,
                                              ExecutionContextImpl eci) {
        Object value
        try {
            value = StupidWebUtilities.defaultWebEncoder.canonicalize(parameterValue, true)
        } catch (IntrusionException e) {
            eci.message.addValidationError(null, parameterName, "Found character escaping (mixed or double) that is not allowed or other format consistency error: " + e.toString(), null)
            return parameterValue
        }

        if (allowSafe) {
            ValidationErrorList vel = new ValidationErrorList()
            value = StupidWebUtilities.defaultWebValidator.getValidSafeHTML(parameterName, value, Integer.MAX_VALUE, true, vel)
            for (ValidationException ve in vel.errors()) eci.message.addError(ve.message)
        } else {
            // check for "<", ">"; this will protect against HTML/JavaScript injection
            if (value.contains("<") || value.contains(">")) {
                eci.message.addValidationError(null, parameterName, "Less-than (<) and greater-than (>) symbols are not allowed.", null)
            }
        }

        return value
    }

    protected void checkSubtype(String parameterName, Node typeParentNode, Object value, ExecutionContextImpl eci) {
        if (typeParentNode."subtype") {
            if (value instanceof List) {
                // just check the first value in the list
                if (((List) value).size() > 0) {
                    String subType = typeParentNode."subtype"[0]."@type"
                    Object subValue = ((List) value).get(0)
                    if (!StupidUtilities.isInstanceOf(subValue, subType)) {
                        eci.message.addError("Parameter [${parameterName}] passed to service [${getServiceName()}] had a subtype [${subValue.class.name}], expecting subtype [${subType}]")
                    } else {
                        // try the next level down
                        checkSubtype(parameterName, typeParentNode."subtype"[0], subValue, eci)
                    }
                }
            } else if (value instanceof Map) {
                // for each subtype element check its name/type
                Map mapVal = (Map) value
                for (Node stNode in typeParentNode."subtype") {
                    String subName = stNode."@name"
                    String subType = stNode."@type"
                    if (!subName || !subType) continue

                    Object subValue = mapVal.get(subName)
                    if (!subValue) continue
                    if (!StupidUtilities.isInstanceOf(subValue, subType)) {
                        eci.message.addError("Parameter [${parameterName}] passed to service [${getServiceName()}] had a subtype [${subValue.class.name}], expecting subtype [${subType}]")
                    } else {
                        // try the next level down
                        checkSubtype(parameterName, stNode, subValue, eci)
                    }
                }
            }
        }
    }

    static final Map<String, CreditCardValidator.CreditCardType> creditCardTypeMap =
            (Map<String, CreditCardValidator.CreditCardType>) [visa:new CreditCardVisa(),
            mastercard:new CreditCardMastercard(), amex:new CreditCardAmex(),
            discover:new CreditCardDiscover(), enroute:new CreditCardEnroute(),
            jcb:new CreditCardJcb(), solo:new CreditCardSolo(),
            "switch":new CreditCardSwitch(), dinersclub:new CreditCardDinersClub(),
            visaelectron:new CreditCardVisaElectron()]
    static class CreditCardVisa implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            return (((cc.length() == 16) || (cc.length() == 13)) && (cc.substring(0, 1).equals("4")))
        }
    }
    static class CreditCardMastercard implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            int firstdig = Integer.parseInt(cc.substring(0, 1))
            int seconddig = Integer.parseInt(cc.substring(1, 2))
            return ((cc.length() == 16) && (firstdig == 5) && ((seconddig >= 1) && (seconddig <= 5)))
        }
    }
    static class CreditCardAmex implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            int firstdig = Integer.parseInt(cc.substring(0, 1))
            int seconddig = Integer.parseInt(cc.substring(1, 2))
            return ((cc.length() == 15) && (firstdig == 3) && ((seconddig == 4) || (seconddig == 7)))
        }
    }
    static class CreditCardDiscover implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            String first4digs = cc.substring(0, 4)
            return ((cc.length() == 16) && (first4digs.equals("6011")))
        }
    }
    static class CreditCardEnroute implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            String first4digs = cc.substring(0, 4)
            return ((cc.length() == 15) && (first4digs.equals("2014") || first4digs.equals("2149")))
        }
    }
    static class CreditCardJcb implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            String first4digs = cc.substring(0, 4)
            return ((cc.length() == 16) &&
                (first4digs.equals("3088") || first4digs.equals("3096") || first4digs.equals("3112") ||
                    first4digs.equals("3158") || first4digs.equals("3337") || first4digs.equals("3528")))
        }
    }
    static class CreditCardSolo implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            String first4digs = cc.substring(0, 4)
            String first2digs = cc.substring(0, 2)
            return (((cc.length() == 16) || (cc.length() == 18) || (cc.length() == 19)) &&
                    (first2digs.equals("63") || first4digs.equals("6767")))
        }
    }
    static class CreditCardSwitch implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            String first4digs = cc.substring(0, 4)
            String first6digs = cc.substring(0, 6)
            return (((cc.length() == 16) || (cc.length() == 18) || (cc.length() == 19)) &&
                (first4digs.equals("4903") || first4digs.equals("4905") || first4digs.equals("4911") ||
                    first4digs.equals("4936") || first6digs.equals("564182") || first6digs.equals("633110") ||
                    first4digs.equals("6333") || first4digs.equals("6759")))
        }
    }
    static class CreditCardDinersClub implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            int firstdig = Integer.parseInt(cc.substring(0, 1))
            int seconddig = Integer.parseInt(cc.substring(1, 2))
            return ((cc.length() == 14) && (firstdig == 3) && ((seconddig == 0) || (seconddig == 6) || (seconddig == 8)))
        }
    }
    static class CreditCardVisaElectron implements CreditCardValidator.CreditCardType {
        boolean matches(String cc) {
            String first6digs = cc.substring(0, 6)
            String first4digs = cc.substring(0, 4)
            return ((cc.length() == 16) &&
                (first6digs.equals("417500") || first4digs.equals("4917") || first4digs.equals("4913") ||
                    first4digs.equals("4508") || first4digs.equals("4844") || first4digs.equals("4027")))
        }
    }
}
