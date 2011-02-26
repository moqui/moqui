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

import javax.transaction.Transaction

import org.moqui.context.TransactionException
import org.moqui.context.TransactionFacade
import org.moqui.service.ServiceCallSync
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.service.runner.EntityAutoServiceRunner

import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.StupidUtilities
import org.moqui.impl.StupidWebUtilities
import org.owasp.esapi.errors.IntrusionException
import org.owasp.esapi.ValidationErrorList
import org.owasp.esapi.errors.ValidationException
import org.apache.commons.validator.EmailValidator
import org.apache.commons.validator.UrlValidator
import org.apache.commons.validator.CreditCardValidator

class ServiceCallSyncImpl extends ServiceCallImpl implements ServiceCallSync {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceCallSyncImpl.class)

    protected final static EmailValidator emailValidator = EmailValidator.getInstance()
    protected final static UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES)

    protected boolean requireNewTransaction = false
    /* not supported by Atomikos/etc right now, consider for later: protected int transactionIsolation = -1 */

    ServiceCallSyncImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallSync name(String serviceName) { this.setServiceName(serviceName); return this }

    @Override
    ServiceCallSync name(String v, String n) { path = null; verb = v; noun = n; return this }

    @Override
    ServiceCallSync name(String p, String v, String n) { path = p; verb = v; noun = n; return this }

    @Override
    ServiceCallSync parameters(Map<String, Object> map) { parameters.putAll(map); return this }

    @Override
    ServiceCallSync parameter(String name, Object value) { parameters.put(name, value); return this }

    @Override
    ServiceCallSync requireNewTransaction(boolean rnt) { this.requireNewTransaction = rnt; return this }

    /* not supported by Atomikos/etc right now, consider for later:
    @Override
    ServiceCallSync transactionIsolation(int ti) { this.transactionIsolation = ti; return this }
    */

    @Override
    Map<String, Object> call() {
        long callStartTime = System.currentTimeMillis()
        ExecutionContextImpl eci = (ExecutionContextImpl) sfi.ecfi.executionContext
        ServiceDefinition sd = sfi.getServiceDefinition(getServiceName())
        if (!sd) {
            // if verb is create|update|delete and noun is a valid entity name, do an implicit entity-auto
            if ((verb == "create" || verb == "update" || verb == "delete") && sfi.ecfi.entityFacade.getEntityDefinition(noun) != null) {
                Map result = runImplicitEntityAuto()
                if (logger.traceEnabled) logger.trace("Finished call to service [${getServiceName()}] in ${(System.currentTimeMillis()-callStartTime)/1000} seconds")
                return result
            } else {
                throw new IllegalArgumentException("Could not find service with name [${getServiceName()}]")
            }
        }

        String serviceType = sd.serviceNode."@type" ?: "inline"
        if (serviceType == "interface") throw new IllegalArgumentException("Cannot run interface service [${getServiceName()}]")

        // start with the settings for the default: use-or-begin
        boolean pauseResumeIfNeeded = false
        boolean beginTransactionIfNeeded = true
        if (requireNewTransaction) {
            // if the setting for this service call is in place, use it regardless of the settings on the service
            pauseResumeIfNeeded = true
        } else {
            if (sd.serviceNode."@transaction" == "ignore") {
                beginTransactionIfNeeded = false
            } else if (sd.serviceNode."@transaction" == "force-new") {
                pauseResumeIfNeeded = true
            }
        }

        Integer transactionTimeout = null
        if (sd.serviceNode."@transaction-timeout") {
            transactionTimeout = sd.serviceNode."@transaction-timeout" as Integer
        }

        ServiceRunner sr = sfi.getServiceRunner(serviceType)
        if (!sr) throw new IllegalArgumentException("Could not find service runner for type [${serviceType}] for service [${getServiceName()}]")

        // TODO sd.serviceNode."@semaphore"

        // TODO trigger SECAs

        // validation
        if (sd.serviceNode."@validate" != "false") {
            Set<String> inParameterNames = sd.getInParameterNames()
            // if service is to be validated, go through service in-parameters definition and only get valid parameters
            for (String parameterName in new HashSet(this.parameters.keySet())) {
                if (!inParameterNames.contains(parameterName)) {
                    this.parameters.remove(parameterName)
                    logger.warn("Parameter [${parameterName}] was passed to service [${getServiceName()}] but is not defined as an in parameter, removing from parameters.")
                    continue
                }

                Node parameterNode = sd.getInParameter(parameterName)
                Object parameterValue = this.parameters.get(parameterName)

                // set the default-value if applicable
                if (!parameterValue && parameterNode."@default-value") parameterValue = parameterNode."@default-value"

                // check if required
                if (!parameterValue) {
                    if (parameterNode."@required" == "true") {
                        eci.message.addError("Parameter [${parameterName}] of service [${getServiceName()}] is required")
                    }
                    // if it isn't there continue since there is nothing to do with it
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
                        this.parameters.put(parameterName, converted)
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

        // if error in parameters, return now with no results
        if (eci.message.errors) return null

        boolean userLoggedIn = false
        TransactionFacade tf = sfi.ecfi.getTransactionFacade()
        Transaction parentTransaction = null
        Map<String, Object> result = null
        try {
            // authentication
            // always try to login the user if parameters are specified
            String userId = parameters.authUserAccount ? parameters.authUserAccount.userId : parameters.authUsername
            String password = parameters.authUserAccount ? parameters.authUserAccount.currentPassword : parameters.authPassword
            String tenantId = parameters.authTenantId
            if (userId && password) {
                userLoggedIn = eci.user.loginUser(userId, password, tenantId)
            }
            if (sd.serviceNode."@authenticate" != "false" && !eci.user.userId) {
                eci.message.addError("Authentication required for service [${getServiceName()}]")
            }

            // if error in auth or for other reasons, return now with no results
            if (eci.message.errors) return null

            if (pauseResumeIfNeeded && tf.isTransactionInPlace()) parentTransaction = tf.suspend()
            boolean beganTransaction = beginTransactionIfNeeded ? tf.begin(transactionTimeout) : false
            try {
                result = sr.runService(sd, this.parameters)
                // if we got any errors added to the message list in the service, rollback for that too
                if (eci.message.errors) {
                    tf.rollback(beganTransaction, "Error running service [${getServiceName()}] (message): " + eci.message.errors[0], null)
                }
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error running service [${getServiceName()}] (Throwable)", t)
                // add all exception messages to the error messages list
                eci.message.addError(t.message)
                Throwable parent = t.cause
                while (parent != null) {
                    eci.message.addError(parent.message)
                    parent = parent.cause
                }
            } finally {
                if (tf.isTransactionInPlace()) tf.commit(beganTransaction)
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            try {
                if (parentTransaction != null) tf.resume(parentTransaction)
            } catch (Throwable t) {
                logger.error("Error resuming parent transaction after call to service [${getServiceName()}]")
            }
            try {
                if (userLoggedIn) eci.user.logoutUser()
            } catch (Throwable t) {
                logger.error("Error logging out user after call to service [${getServiceName()}]")
            }
            if (logger.traceEnabled) logger.trace("Finished call to service [${getServiceName()}] in ${(System.currentTimeMillis()-callStartTime)/1000} seconds" + (eci.message.errors ? " with ${eci.message.errors.size()} error messages" : ", was successful"))
        }

        return result
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

    protected Map<String, Object> runImplicitEntityAuto() {
        // TODO trigger SECAs
        // NOTE: no authentication, assume not required for this; security settings can override this and require
        //     permissions, which will require authentication
        TransactionFacade tf = sfi.ecfi.getTransactionFacade()
        Transaction parentTransaction = null
        Map<String, Object> result = new HashMap()
        try {
            if (requireNewTransaction && tf.isTransactionInPlace()) parentTransaction = tf.suspend()
            boolean beganTransaction = tf.begin(null)
            try {
                EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
                if (verb == "create") {
                    EntityAutoServiceRunner.createEntity(sfi, ed, parameters, result, null)
                } else if (verb == "update") {
                    EntityAutoServiceRunner.updateEntity(sfi, ed, parameters, result, null)
                } else if (verb == "delete") {
                    EntityAutoServiceRunner.deleteEntity(sfi, ed, parameters)
                }
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error getting primary sequenced ID", t)
            } finally {
                if (tf.isTransactionInPlace()) tf.commit(beganTransaction)
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            if (parentTransaction != null) tf.resume(parentTransaction)
        }
        return result
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
