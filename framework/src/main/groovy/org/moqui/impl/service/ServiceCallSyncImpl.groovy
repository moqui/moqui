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

import org.moqui.context.ArtifactAuthorizationException
import org.moqui.context.TransactionException
import org.moqui.context.TransactionFacade
import org.moqui.entity.EntityValue
import org.moqui.impl.context.ArtifactExecutionInfoImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.context.UserFacadeImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.service.runner.EntityAutoServiceRunner
import org.moqui.service.ServiceCallSync
import org.moqui.service.ServiceException

import java.sql.Timestamp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ServiceCallSyncImpl extends ServiceCallImpl implements ServiceCallSync {
    protected final static Logger logger = LoggerFactory.getLogger(ServiceCallSyncImpl.class)

    protected boolean requireNewTransaction = false
    /* not supported by Atomikos/etc right now, consider for later: protected int transactionIsolation = -1 */

    protected boolean multi = false
    protected boolean disableAuthz = false

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
    ServiceCallSync parameters(Map<String, ?> map) { parameters.putAll(map); return this }

    @Override
    ServiceCallSync parameter(String name, Object value) { parameters.put(name, value); return this }

    @Override
    ServiceCallSync requireNewTransaction(boolean rnt) { this.requireNewTransaction = rnt; return this }

    @Override
    ServiceCallSync multi(boolean mlt) { this.multi = mlt; return this }

    @Override
    ServiceCallSync disableAuthz() { disableAuthz = true; return this }

    /* not supported by Atomikos/etc right now, consider for later:
    @Override
    ServiceCallSync transactionIsolation(int ti) { this.transactionIsolation = ti; return this }
    */

    @Override
    Map<String, Object> call() {
        ServiceDefinition sd = getServiceDefinition()
        ExecutionContextImpl eci = (ExecutionContextImpl) sfi.getEcfi().getExecutionContext()

        Collection<String> inParameterNames = null
        if (sd != null) {
            inParameterNames = sd.getInParameterNames()
        } else if (isEntityAutoPattern()) {
            EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
            if (ed != null) inParameterNames = ed.getAllFieldNames()
        }

        boolean enableAuthz = disableAuthz ? !eci.getArtifactExecution().disableAuthz() : false
        try {
            if (multi) {
                // run all service calls in a single transaction for multi form submits, ie all succeed or fail together
                boolean beganTransaction = eci.getTransaction().begin(null)
                try {
                    for (int i = 0; ; i++) {
                        if ((parameters.get("_useRowSubmit") == "true" || parameters.get("_useRowSubmit_" + i) == "true")
                                && parameters.get("_rowSubmit_" + i) != "true") continue
                        Map<String, Object> currentParms = new HashMap()
                        for (String ipn in inParameterNames) {
                            String key = ipn + "_" + i
                            if (parameters.containsKey(key)) currentParms.put(ipn, parameters.get(key))
                        }
                        // if the map stayed empty we have no parms, so we're done
                        if (currentParms.size() == 0) break
                        // now that we have checked the per-row parameters, add in others available
                        for (String ipn in inParameterNames) {
                            if (!currentParms.get(ipn) && parameters.get(ipn)) currentParms.put(ipn, parameters.get(ipn))
                        }
                        // call the service, ignore the result...
                        callSingle(currentParms, sd, eci)
                        // ... and break if there are any errors
                        if (eci.getMessage().hasError()) break
                    }
                } catch (Throwable t) {
                    eci.getTransaction().rollback(beganTransaction, "Uncaught error running service [${sd.getServiceName()}] in multi mode", t)
                    throw t
                } finally {
                    if (eci.getTransaction().isTransactionInPlace()) {
                        if (eci.getMessage().hasError()) {
                            eci.getTransaction().rollback(beganTransaction, "Error message found running service [${sd.getServiceName()}] in multi mode", null)
                        } else {
                            eci.getTransaction().commit(beganTransaction)
                        }
                    }
                }
            } else {
                return callSingle(this.parameters, sd, eci)
            }
        } finally {
            if (enableAuthz) eci.getArtifactExecution().enableAuthz()
        }
    }

    Map<String, Object> callSingle(Map<String, Object> currentParameters, ServiceDefinition sd, ExecutionContextImpl eci) {
        // NOTE: checking this here because service won't generally run after input validation, etc anyway
        if (eci.getMessage().hasError()) {
            logger.warn("Found error(s) before service [${getServiceName()}], so not running service. Errors: ${eci.getMessage().getErrorsString()}")
            return null
        }
        if (eci.getTransaction().getStatus() == 1) {
            logger.warn("Transaction marked for rollback, not running service [${getServiceName()}]. Errors: ${eci.getMessage().getErrorsString()}")
            return null
        }

        if (logger.traceEnabled) logger.trace("Calling service [${getServiceName()}] initial input: ${currentParameters}")

        long callStartTime = System.currentTimeMillis()

        // in-parameter validation
        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-validate")
        if (sd != null) sd.convertValidateCleanParameters(currentParameters, eci)
        // if error(s) in parameters, return now with no results
        if (eci.getMessage().hasError()) {
            logger.warn("Found error(s) when validating input parameters for service [${getServiceName()}], so not running service. Errors: ${eci.getMessage().getErrorsString()}; the artifact stack is:\n ${eci.artifactExecution.stack}")
            return null
        }

        boolean userLoggedIn = false

        // always try to login the user if parameters are specified
        String userId = currentParameters.authUserAccount?.userId ?: currentParameters.authUsername
        String password = currentParameters.authUserAccount?.currentPassword ?: currentParameters.authPassword
        String tenantId = currentParameters.authTenantId
        if (userId && password) userLoggedIn = eci.getUser().loginUser(userId, password, tenantId)

        // pre authentication and authorization SECA rules
        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-auth")

        // push service call artifact execution, checks authz too
        // NOTE: don't require authz if the service def doesn't authenticate
        // NOTE: if no sd then requiresAuthz is false, ie let the authz get handled at the entity level (but still put
        //     the service on the stack)
        eci.getArtifactExecution().push(new ArtifactExecutionInfoImpl(getServiceName(), "AT_SERVICE",
                    ServiceDefinition.getVerbAuthzActionId(verb)).setParameters(currentParameters),
                (sd != null && sd.getAuthenticate() == "true"))

        // must be done after the artifact execution push so that AEII object to set anonymous authorized is in place
        boolean loggedInAnonymous = false
        if (sd != null && sd.getAuthenticate() == "anonymous-all") {
            eci.getArtifactExecution().setAnonymousAuthorizedAll()
            loggedInAnonymous = ((UserFacadeImpl) eci.getUser()).loginAnonymousIfNoUser()
        } else if (sd != null && sd.getAuthenticate() == "anonymous-view") {
            eci.getArtifactExecution().setAnonymousAuthorizedView()
            loggedInAnonymous = ((UserFacadeImpl) eci.getUser()).loginAnonymousIfNoUser()
        }

        if (sd == null) {
            if (isEntityAutoPattern()) {
                Map result = runImplicitEntityAuto(currentParameters, eci)

                long endTime = System.currentTimeMillis()
                if (logger.traceEnabled) logger.trace("Finished call to service [${getServiceName()}] in ${(endTime-callStartTime)/1000} seconds")
                sfi.getEcfi().countArtifactHit("service", "entity-implicit", getServiceName(), currentParameters, callStartTime, endTime, null)

                eci.artifactExecution.pop()
                return result
            } else {
                throw new ServiceException("Could not find service with name [${getServiceName()}]")
            }
        }

        String serviceType = sd.getServiceType()
        if ("interface".equals(serviceType)) throw new ServiceException("Cannot run interface service [${getServiceName()}]")

        ServiceRunner sr = sfi.getServiceRunner(serviceType)
        if (sr == null) throw new ServiceException("Could not find service runner for type [${serviceType}] for service [${getServiceName()}]")

        // start with the settings for the default: use-or-begin
        boolean pauseResumeIfNeeded = false
        boolean beginTransactionIfNeeded = true
        if (requireNewTransaction) {
            // if the setting for this service call is in place, use it regardless of the settings on the service
            pauseResumeIfNeeded = true
        } else {
            if (sd.getTxIgnore()) {
                beginTransactionIfNeeded = false
            } else if (sd.getTxForceNew()) {
                pauseResumeIfNeeded = true
            }
        }

        TransactionFacade tf = sfi.getEcfi().getTransactionFacade()
        boolean suspendedTransaction = false
        Map<String, Object> result = null
        try {
            // if error in auth or for other reasons, return now with no results
            if (eci.getMessage().hasError()) {
                logger.warn("Found error(s) when checking authc for service [${getServiceName()}], so not running service. Errors: ${eci.getMessage().getErrorsString()}; the artifact stack is:\n ${eci.artifactExecution.stack}")
                return null
            }

            if (pauseResumeIfNeeded && tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = beginTransactionIfNeeded ? tf.begin(sd.getTxTimeout()) : false
            if (sd.getTxUseCache()) tf.initTransactionCache()
            try {
                // handle sd.serviceNode."@semaphore"; do this after local transaction created, etc.
                checkAddSemaphore(sd, eci)

                sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-service")

                if (logger.traceEnabled) logger.trace("Calling service [${getServiceName()}] pre-call input: ${currentParameters}")

                try {
                    // run the service through the ServiceRunner
                    result = sr.runService(sd, currentParameters)
                } finally {
                    sfi.registerTxSecaRules(getServiceName(), currentParameters, result)
                }

                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-service")
                // if we got any errors added to the message list in the service, rollback for that too
                if (eci.getMessage().hasError()) {
                    tf.rollback(beganTransaction, "Error running service [${getServiceName()}] (message): " + eci.getMessage().getErrorsString(), null)
                }

                if (logger.traceEnabled) logger.trace("Calling service [${getServiceName()}] result: ${result}")
            } catch (ArtifactAuthorizationException e) {
                // this is a local call, pass certain exceptions through
                throw e
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error running service [${getServiceName()}] (Throwable)", t)
                logger.warn("Error running service [${getServiceName()}] (Throwable)", t)
                // add all exception messages to the error messages list
                eci.getMessage().addError(t.getMessage())
                Throwable parent = t.getCause()
                while (parent != null) {
                    eci.getMessage().addError(parent.getMessage())
                    parent = parent.getCause()
                }
            } finally {
                // clear the semaphore
                String semaphore = sd.getServiceNode()."@semaphore"
                if (semaphore == "fail" || semaphore == "wait") {
                    eci.getService().sync().name("delete", "moqui.service.semaphore.ServiceSemaphore")
                            .parameters([serviceName:getServiceName()]).requireNewTransaction(true).call()
                }

                try {
                    if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
                } catch (Throwable t) {
                    logger.warn("Error committing transaction for service [${getServiceName()}]", t)
                    // add all exception messages to the error messages list
                    eci.getMessage().addError(t.getMessage())
                    Throwable parent = t.getCause()
                    while (parent != null) {
                        eci.getMessage().addError(parent.getMessage())
                        parent = parent.getCause()
                    }
                }
                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-commit")
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            try {
                if (suspendedTransaction) tf.resume()
            } catch (Throwable t) {
                logger.error("Error resuming parent transaction after call to service [${getServiceName()}]", t)
            }
            try {
                if (userLoggedIn) eci.getUser().logoutUser()
            } catch (Throwable t) {
                logger.error("Error logging out user after call to service [${getServiceName()}]", t)
            }

            long endTime = System.currentTimeMillis()
            sfi.getEcfi().countArtifactHit("service", serviceType, getServiceName(), currentParameters, callStartTime, endTime, null)

            if (logger.traceEnabled) logger.trace("Finished call to service [${getServiceName()}] in ${(endTime-callStartTime)/1000} seconds" + (eci.getMessage().hasError() ? " with ${eci.getMessage().getErrors().size() + eci.getMessage().getValidationErrors().size()} error messages" : ", was successful"))
        }

        // all done so pop the artifact info; don't bother making sure this is done on errors/etc like in a finally clause because if there is an error this will help us know how we got there
        eci.getArtifactExecution().pop()

        if (loggedInAnonymous) ((UserFacadeImpl) eci.getUser()).logoutAnonymousOnly()

        return result
    }

    protected void checkAddSemaphore(ServiceDefinition sd, ExecutionContextImpl eci) {
        String semaphore = sd.getServiceNode()."@semaphore"
        if (semaphore == "fail" || semaphore == "wait") {
            EntityValue serviceSemaphore = eci.getEntity().find("moqui.service.semaphore.ServiceSemaphore")
                    .condition("serviceName", getServiceName()).useCache(false).one()
            if (serviceSemaphore) {
                long ignoreMillis = ((sd.getServiceNode()."@semaphore-ignore" ?: "3600") as Long) * 1000
                Timestamp lockTime = serviceSemaphore.getTimestamp("lockTime")
                if (System.currentTimeMillis() > (lockTime.getTime() + ignoreMillis)) {
                    eci.getService().sync().name("delete", "moqui.service.semaphore.ServiceSemaphore")
                            .parameters([serviceName:getServiceName()]).requireNewTransaction(true).call()
                }

                if (semaphore == "fail") {
                    throw new ServiceException("An instance of service [${getServiceName()}] is already running (thread [${serviceSemaphore.lockThread}], locked at ${serviceSemaphore.lockTime}) and it is setup to fail on semaphore conflict.")
                } else {
                    long sleepTime = ((sd.getServiceNode()."@semaphore-sleep" ?: "5") as Long) * 1000
                    long timeoutTime = ((sd.getServiceNode()."@semaphore-timeout" ?: "120") as Long) * 1000
                    long startTime = System.currentTimeMillis()
                    boolean semaphoreCleared = false
                    while (System.currentTimeMillis() < (startTime + timeoutTime)) {
                        Thread.wait(sleepTime)
                        if (eci.getEntity().find("moqui.service.semaphore.ServiceSemaphore")
                                .condition("serviceName", getServiceName()).useCache(false).one() == null) {
                            semaphoreCleared = true
                            break
                        }
                    }
                    if (!semaphoreCleared) {
                        throw new ServiceException("An instance of service [${getServiceName()}] is already running (thread [${serviceSemaphore.lockThread}], locked at ${serviceSemaphore.lockTime}) and it is setup to wait on semaphore conflict, but the semaphore did not clear in ${timeoutTime/1000} seconds.")
                    }
                }
            }

            // if we got to here the semaphore didn't exist or has cleared, so create one
            eci.getService().sync().name("create", "moqui.service.semaphore.ServiceSemaphore")
                    .parameters([serviceName:getServiceName(), lockThread:Thread.currentThread().getName(),
                    lockTime:new Timestamp(System.currentTimeMillis())])
                    .requireNewTransaction(true).call()
        }
    }

    protected Map<String, Object> runImplicitEntityAuto(Map<String, Object> currentParameters, ExecutionContextImpl eci) {
        // NOTE: no authentication, assume not required for this; security settings can override this and require
        //     permissions, which will require authentication
        // done in calling method: sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-auth")

        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-validate")

        TransactionFacade tf = sfi.getEcfi().getTransactionFacade()
        boolean suspendedTransaction = false
        Map<String, Object> result = new HashMap()
        try {
            if (requireNewTransaction && tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = tf.begin(null)
            try {
                sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-service")

                try {
                    EntityDefinition ed = sfi.getEcfi().getEntityFacade().getEntityDefinition(noun)
                    switch (verb) {
                        case "create": EntityAutoServiceRunner.createEntity(sfi, ed, currentParameters, result, null); break
                        case "update": EntityAutoServiceRunner.updateEntity(sfi, ed, currentParameters, result, null, null); break
                        case "delete": EntityAutoServiceRunner.deleteEntity(sfi, ed, currentParameters); break
                        case "store": EntityAutoServiceRunner.storeEntity(sfi, ed, currentParameters, result, null); break
                    }
                } finally {
                    sfi.registerTxSecaRules(getServiceName(), currentParameters, result)
                }

                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-service")
            } catch (ArtifactAuthorizationException e) {
                tf.rollback(beganTransaction, "Authorization error running service [${getServiceName()}] ", e)
                // this is a local call, pass certain exceptions through
                throw e
            } catch (Throwable t) {
                logger.error("Error running service [${getServiceName()}]", t)
                tf.rollback(beganTransaction, "Error running service [${getServiceName()}] (Throwable)", t)
                // add all exception messages to the error messages list
                eci.getMessage().addError(t.getMessage())
                Throwable parent = t.getCause()
                while (parent != null) {
                    eci.getMessage().addError(parent.getMessage())
                    parent = parent.getCause()
                }
            } finally {
                try {
                    if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
                } catch (Throwable t) {
                    logger.warn("Error committing transaction for entity-auto service [${getServiceName()}]", t)
                    // add all exception messages to the error messages list
                    eci.getMessage().addError(t.getMessage())
                    Throwable parent = t.getCause()
                    while (parent != null) {
                        eci.getMessage().addError(parent.getMessage())
                        parent = parent.getCause()
                    }
                }
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
