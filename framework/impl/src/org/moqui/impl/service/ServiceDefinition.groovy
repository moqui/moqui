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

    void convertValidateCleanParameters(Map<String, Object> parameters, ExecutionContextImpl eci) {
        if (this.serviceNode."@validate" != "false") {
            Set<String> inParameterNames = this.getInParameterNames()
            // if service is to be validated, go through service in-parameters definition and only get valid parameters
            for (String parameterName in new HashSet(parameters.keySet())) {
                if (!inParameterNames.contains(parameterName)) {
                    parameters.remove(parameterName)
                    if (logger.traceEnabled && parameterName != "ec")
                        logger.trace("Parameter [${parameterName}] was passed to service [${getServiceName()}] but is not defined as an in parameter, removing from parameters.")
                    continue
                }

                Node parameterNode = this.getInParameter(parameterName)
                Object parameterValue = parameters.get(parameterName)

                // set the default-value if applicable
                if (!parameterValue && parameterNode."@default-value") parameterValue = parameterNode."@default-value"

                // check if required
                if (!parameterValue) {
                    if (parameterNode."@required" == "true") {
                        eci.message.addError("Parameter [${parameterName}] of service [${getServiceName()}] is required")
                    }
                    // if it isn't there continue since there is nothing to do with it
                    // TODO: should we change empty values to null?
                    continue
                }

                // check type
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
                            BigDecimal bdVal = eci.user.parseNumber(parameterValue, format)
                            if (bdVal == null) {
                                eci.message.addError("Parameter ${parameterName} with value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""))
                            } else {
                                converted = StupidUtilities.basicConvert(bdVal, type)
                            }
                            break
                        case "Time":
                        case "java.sql.Time":
                            converted = eci.user.parseTime(parameterValue, format)
                            if (converted == null) {
                                eci.message.addError("Parameter ${parameterName} with value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""))
                            }
                            break
                        case "Date":
                        case "java.sql.Date":
                            converted = eci.user.parseDate(parameterValue, format)
                            if (converted == null) {
                                eci.message.addError("Parameter ${parameterName} with value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""))
                            }
                            break
                        case "Timestamp":
                        case "java.sql.Timestamp":
                            converted = eci.user.parseTimestamp(parameterValue, format)
                            if (converted == null) {
                                eci.message.addError("Parameter ${parameterName} with value [${parameterValue}] could not be converted to a ${type}" + (format ? " using format [${format}]": ""))
                            }
                            break
                        }
                    }

                    // fallback to a really simple type conversion
                    if (converted == null) converted = StupidUtilities.basicConvert(parameterValue, type)

                    if (converted != null) {
                        parameterValue = converted
                        parameters.put(parameterName, converted)
                    } else {
                        // no type conversion? blow up
                        eci.message.addError("Parameter [${parameterName}] passed to service [${getServiceName()}] was type [${parameterValue.class.name}], expecting type [${type}]")
                        continue
                    }
                }

                checkSubtype(parameterName, parameterNode, parameterValue, eci)

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

                // run through validations under parameter node
                // do this after the convert so we can deal with objects when needed
                validateParameter(parameterNode, parameterName, parameterValue, eci)
            }
        }
    }

    protected boolean validateParameter(Node vpNode, String parameterName, Object pv, ExecutionContextImpl eci) {
        // no validation done if value is empty, that should be checked with the required attribute only
        if (!pv) return true

        boolean allPass = true
        for (Node child in vpNode.children()) {
            if (child.name() == "description" || child.name() == "subtype") continue
            // NOTE don't break on fail, we want to get a list of all failures for the user to see
            try {
                if (!validateParameterSingle(child, parameterName, pv, eci)) allPass = false
            } catch (Throwable t) {
                eci.message.addError("Parameter ${parameterName} with value [${pv}] failed [${child.name()}] validation (${t.message})")
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
                eci.message.addError("Parameter ${parameterName} with value [${pv}] is not a String, cannot do matches validation.")
                return false
            }
            if (valNode."@regexp" && !((String) pv).matches((String) valNode."@regexp")) {
                // a message attribute should always be there, but just in case we'll have a default
                eci.message.addError(valNode."@message" ?: "Parameter ${parameterName} with value [${pv}] did not match expression [${valNode."@regexp"}]")
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
                        eci.message.addError("Parameter ${parameterName} with value [${pv}] must be greater than ${min}.")
                        return false
                    }
                } else {
                    if (bdVal < min) {
                        eci.message.addError("Parameter ${parameterName} with value [${pv}] must be greater than or equal to ${min}.")
                        return false
                    }
                }
            }
            if (valNode."@max") {
                BigDecimal max = new BigDecimal((String) valNode."@max")
                if (valNode."@max-include-equals" == "true") {
                    if (max > bdVal) {
                        eci.message.addError("Parameter ${parameterName} with value [${pv}] must be less than or equal to ${max}.")
                        return false
                    }
                } else {
                    if (max >= bdVal) {
                        eci.message.addError("Parameter ${parameterName} with value [${pv}] must be less than ${max}.")
                        return false
                    }
                }
            }
            return true
        case "number-integer":
            try {
                new BigInteger(pv as String)
            } catch (NumberFormatException e) {
                eci.message.addError("Parameter ${parameterName} with value [${pv}] must be a integer number.")
                return false
            }
            return true
        case "number-decimal":
            try {
                new BigDecimal(pv as String)
            } catch (NumberFormatException e) {
                eci.message.addError("Parameter ${parameterName} with value [${pv}] must be a decimal number.")
                return false
            }
            return true
        case "text-length":
            String str = pv as String
            if (valNode."@min") {
                int min = valNode."@min" as int
                if (str.length() < min) {
                    eci.message.addError("Parameter ${parameterName} with value [${pv}] and length ${str.length()} must have a length greater than or equal to ${min}.")
                    return false
                }
            }
            if (valNode."@max") {
                int max = valNode."@max" as int
                if (max >= str.length()) {
                    eci.message.addError("Parameter ${parameterName} with value [${pv}] and length ${str.length()} must have a length les than or equal to ${max}.")
                    return false
                }
            }
            return true
        case "text-email":
            String str = pv as String
            if (!emailValidator.isValid(str)) {
                eci.message.addError("Parameter ${parameterName} with value [${str}] must be a valid email address.")
                return false
            }
            return true
        case "text-url":
            String str = pv as String
            if (!urlValidator.isValid(str)) {
                eci.message.addError("Parameter ${parameterName} with value [${str}] must be a valid URL.")
                return false
            }
            return true
        case "text-letters":
            String str = pv as String
            for (char c in str) {
                if (!Character.isLetter(c)) {
                    eci.message.addError("Parameter ${parameterName} with value [${str}] must have only letters.")
                    return false
                }
            }
            return true
        case "text-digits":
            String str = pv as String
            for (char c in str) {
                if (!Character.isDigit(c)) {
                    eci.message.addError("Parameter ${parameterName} with value [${str}] must have only digits.")
                    return false
                }
            }
            return true
        case "time-range":
            Calendar cal = Calendar.newInstance()
            // TODO: not sure if this will work: ((pv as java.util.Date).getTime())
            cal.setTimeInMillis((pv as java.util.Date).getTime())
            if (valNode."@after") {
                // TODO handle after date/time/date-time depending on type of parameter, support "now" too
            }
            if (valNode."@before") {
                // TODO handle after date/time/date-time depending on type of parameter, support "now" too
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
                eci.message.addError("Parameter ${parameterName} with value [${str}] must be a valid credit card number.")
                return false
            }
            return true
        }
        // shouldn't get here, but just in case
        return true
    }

    protected Object canonicalizeAndCheckHtml(String parameterName, String parameterValue, boolean allowSafe, ExecutionContextImpl eci) {
        Object value
        try {
            value = StupidWebUtilities.defaultWebEncoder.canonicalize(parameterValue, true)
        } catch (IntrusionException e) {
            eci.message.addError("In field [${parameterName}] found character escaping (mixed or double) that is not allowed or other format consistency error: " + e.toString())
            return parameterValue
        }

        if (allowSafe) {
            ValidationErrorList vel = new ValidationErrorList()
            value = StupidWebUtilities.defaultWebValidator.getValidSafeHTML(parameterName, value, Integer.MAX_VALUE, true, vel)
            for (ValidationException ve in vel.errors()) eci.message.addError(ve.message)
        } else {
            // check for "<", ">"; this will protect against HTML/JavaScript injection
            if (value.contains("<") || value.contains(">")) {
                eci.message.addError("In field [${parameterName}] less-than (<) and greater-than (>) symbols are not allowed.")
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
