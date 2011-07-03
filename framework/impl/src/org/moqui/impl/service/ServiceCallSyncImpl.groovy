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

import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.service.runner.EntityAutoServiceRunner
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.service.ServiceException

class ServiceCallSyncImpl extends ServiceCallImpl implements ServiceCallSync {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceCallSyncImpl.class)

    protected boolean requireNewTransaction = false
    /* not supported by Atomikos/etc right now, consider for later: protected int transactionIsolation = -1 */

    protected boolean multi = false

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

    @Override
    ServiceCallSync multi(boolean mlt) { this.multi = mlt; return this }

    /* not supported by Atomikos/etc right now, consider for later:
    @Override
    ServiceCallSync transactionIsolation(int ti) { this.transactionIsolation = ti; return this }
    */

    @Override
    Map<String, Object> call() {
        ServiceDefinition sd = sfi.getServiceDefinition(getServiceName())
        ExecutionContextImpl eci = (ExecutionContextImpl) sfi.ecfi.executionContext

        Set<String> inParameterNames = null
        if (sd != null) {
            inParameterNames = sd.getInParameterNames()
        } else {
            EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
            if (ed != null) inParameterNames = ed.getFieldNames(true, true)
        }
        if (multi) {
            for (int i = 0; ; i++) {
                Map<String, Object> currentParms = new HashMap()
                for (String ipn in inParameterNames) {
                    String key = ipn + "_" + i
                    if (this.parameters.containsKey(key)) {
                        currentParms.put(ipn, this.parameters.get(key))
                    } else if (this.parameters.containsKey(ipn)) {
                        currentParms.put(ipn, this.parameters.get(ipn))
                    }
                }
                // if the map stayed empty we have no parms, so we're done
                if (currentParms.size() == 0) break
                // call the service, ignore the result...
                callSingle(currentParms, sd, eci)
                // ... and break if there are any errors
                if (eci.message.errors) break
            }
        } else {
            return callSingle(this.parameters, sd, eci)
        }
    }

    Map<String, Object> callSingle(Map<String, Object> currentParameters, ServiceDefinition sd, ExecutionContextImpl eci) {
        long callStartTime = System.currentTimeMillis()

        // default to require the "All" authz action, and for special verbs default to something more appropriate
        String authzAction = "AUTHZA_ALL"
        if (verb == "create") authzAction = "AUTHZA_CREATE"
        else if (verb == "update") authzAction = "AUTHZA_UPDATE"
        else if (verb == "delete") authzAction = "AUTHZA_DELETE"
        else if (verb == "view" || verb == "find") authzAction = "AUTHZA_VIEW"
        eci.artifactExecution.push(new ArtifactExecutionInfoImpl(getServiceName(), "AT_SERVICE", authzAction),
                sd == null ? false : sd.serviceNode."@authenticate" != "false")
        // NOTE: don't require authz if the service def doesn't authenticate
        // NOTE: if no sd then requiresAuthz is false, ie let the authz get handled at the entity level (but still put
        //     the service on the stack)

        if (sd == null) {
            // if verb is create|update|delete and noun is a valid entity name, do an implicit entity-auto
            if ((verb == "create" || verb == "update" || verb == "delete" || verb == "store") &&
                    sfi.ecfi.entityFacade.getEntityDefinition(noun) != null) {
                Map result = runImplicitEntityAuto(currentParameters, eci)

                if (logger.traceEnabled) logger.trace("Finished call to service [${getServiceName()}] in ${(System.currentTimeMillis()-callStartTime)/1000} seconds")
                long endTime = System.currentTimeMillis()
                sfi.ecfi.countArtifactHit("service", "entity-implicit", getServiceName(), currentParameters, callStartTime, endTime, null)

                eci.artifactExecution.pop()
                return result
            } else {
                throw new ServiceException("Could not find service with name [${getServiceName()}]")
            }
        }

        String serviceType = sd.serviceNode."@type" ?: "inline"
        if (serviceType == "interface") throw new ServiceException("Cannot run interface service [${getServiceName()}]")

        ServiceRunner sr = sfi.getServiceRunner(serviceType)
        if (sr == null) throw new ServiceException("Could not find service runner for type [${serviceType}] for service [${getServiceName()}]")

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

        // TODO (future) sd.serviceNode."@semaphore"

        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-validate")

        // validation
        sd.convertValidateCleanParameters(currentParameters, eci)
        // if error(s) in parameters, return now with no results
        if (eci.message.errors) return null

        boolean userLoggedIn = false
        TransactionFacade tf = sfi.ecfi.getTransactionFacade()
        boolean suspendedTransaction = false
        Map<String, Object> result = null
        try {
            // authentication
            sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-auth")
            // always try to login the user if parameters are specified
            String userId = currentParameters.authUserAccount?.userId ?: currentParameters.authUsername
            String password = currentParameters.authUserAccount?.currentPassword ?: currentParameters.authPassword
            String tenantId = currentParameters.authTenantId
            if (userId && password) {
                userLoggedIn = eci.user.loginUser(userId, password, tenantId)
            }
            if (sd.serviceNode."@authenticate" != "false" && !eci.user.userId) {
                eci.message.addError("Authentication required for service [${getServiceName()}]")
            }

            // if error in auth or for other reasons, return now with no results
            if (eci.message.errors) return null

            if (pauseResumeIfNeeded && tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = beginTransactionIfNeeded ? tf.begin(transactionTimeout) : false
            try {
                sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-service")
                sfi.registerTxSecaRules(getServiceName(), currentParameters)
                result = sr.runService(sd, currentParameters)
                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-service")
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
                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-commit")
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            try {
                if (suspendedTransaction) tf.resume()
            } catch (Throwable t) {
                logger.error("Error resuming parent transaction after call to service [${getServiceName()}]")
            }
            try {
                if (userLoggedIn) eci.user.logoutUser()
            } catch (Throwable t) {
                logger.error("Error logging out user after call to service [${getServiceName()}]")
            }

            long endTime = System.currentTimeMillis()
            sfi.ecfi.countArtifactHit("service", serviceType, getServiceName(), currentParameters, callStartTime, endTime, null)

            if (logger.traceEnabled) logger.trace("Finished call to service [${getServiceName()}] in ${(endTime-callStartTime)/1000} seconds" + (eci.message.errors ? " with ${eci.message.errors.size()} error messages" : ", was successful"))
        }

        // all done so pop the artifact info; don't bother making sure this is done on errors/etc like in a finally clause because if there is an error this will help us know how we got there
        eci.artifactExecution.pop()

        return result
    }

    protected Map<String, Object> runImplicitEntityAuto(Map<String, Object> currentParameters, ExecutionContextImpl eci) {
        // NOTE: no authentication, assume not required for this; security settings can override this and require
        //     permissions, which will require authentication
        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-validate")
        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-auth")

        TransactionFacade tf = sfi.ecfi.getTransactionFacade()
        boolean suspendedTransaction = false
        Map<String, Object> result = new HashMap()
        try {
            if (requireNewTransaction && tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = tf.begin(null)
            try {
                sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-service")
                sfi.registerTxSecaRules(getServiceName(), currentParameters)

                EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
                if (verb == "create") {
                    EntityAutoServiceRunner.createEntity(sfi, ed, currentParameters, result, null)
                } else if (verb == "update") {
                    EntityAutoServiceRunner.updateEntity(sfi, ed, currentParameters, result, null, null)
                } else if (verb == "delete") {
                    EntityAutoServiceRunner.deleteEntity(sfi, ed, currentParameters)
                } else if (verb == "store") {
                    EntityAutoServiceRunner.storeEntity(sfi, ed, currentParameters, result, null)
                }

                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-service")
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
                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-commit")
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            if (suspendedTransaction) tf.resume()
        }
        return result
    }
}
