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

class ServiceCallSyncImpl extends ServiceCallImpl implements ServiceCallSync {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceCallSyncImpl.class)

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
            for (String parameterName in this.parameters.keySet()) {
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
                if (!parameterValue && parameterNode."@required" == "true") {
                    eci.message.addError("Parameter [${parameterName}] of service [${getServiceName()}] is required")
                    continue
                }

                // check type
                String type = parameterNode."@type" ?: "String"
                if (!StupidUtilities.isInstanceOf(parameterValue, type)) {
                    // do type conversion if possible
                    Object converted = StupidUtilities.basicConvert(parameterValue, type)
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

                // TODO: run through validations under parameter node

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
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error running service [${getServiceName()}]", t)
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
}
