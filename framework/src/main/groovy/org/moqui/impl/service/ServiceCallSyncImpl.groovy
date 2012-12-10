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
    ServiceCallSync parameters(Map<String, ?> map) { parameters.putAll(map); return this }

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

        Collection<String> inParameterNames = null
        if (sd != null) {
            inParameterNames = sd.getInParameterNames()
        } else {
            EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
            if (ed != null) inParameterNames = ed.getAllFieldNames()
        }

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
    }

    Map<String, Object> callSingle(Map<String, Object> currentParameters, ServiceDefinition sd, ExecutionContextImpl eci) {
        if (eci.getMessage().hasError()) {
            logger.warn("Found error(s) before service [${getServiceName()}], so not running service. Errors: ${eci.getMessage().getErrorsString()}")
            return null
        }

        long callStartTime = System.currentTimeMillis()

        boolean userLoggedIn = false

        // always try to login the user if parameters are specified
        String userId = currentParameters.authUserAccount?.userId ?: currentParameters.authUsername
        String password = currentParameters.authUserAccount?.currentPassword ?: currentParameters.authPassword
        String tenantId = currentParameters.authTenantId
        if (userId && password) userLoggedIn = eci.getUser().loginUser(userId, password, tenantId)

        // default to require the "All" authz action, and for special verbs default to something more appropriate
        String authzAction = "AUTHZA_ALL"
        switch (verb) {
            case "create": authzAction = "AUTHZA_CREATE"; break
            case "update": authzAction = "AUTHZA_UPDATE"; break
            case "delete": authzAction = "AUTHZA_DELETE"; break
            case "view":
            case "find": authzAction = "AUTHZA_VIEW"; break
        }
        eci.getArtifactExecution().push(new ArtifactExecutionInfoImpl(getServiceName(), "AT_SERVICE", authzAction),
                (sd != null && sd.getAuthenticate() == "true"))

        boolean loggedInAnonymous = false
        if (sd != null && sd.getAuthenticate() == "anonymous-all") {
            eci.getArtifactExecution().setAnonymousAuthorizedAll()
            loggedInAnonymous = ((UserFacadeImpl) eci.getUser()).loginAnonymousIfNoUser()
        } else if (sd != null && sd.getAuthenticate() == "anonymous-view") {
            eci.getArtifactExecution().setAnonymousAuthorizedView()
            loggedInAnonymous = ((UserFacadeImpl) eci.getUser()).loginAnonymousIfNoUser()
        }
        // NOTE: don't require authz if the service def doesn't authenticate
        // NOTE: if no sd then requiresAuthz is false, ie let the authz get handled at the entity level (but still put
        //     the service on the stack)

        if (sd == null) {
            // if no path, verb is create|update|delete and noun is a valid entity name, do an implicit entity-auto
            if (!path && ("create".equals(verb) || "update".equals(verb) || "delete".equals(verb) || "store".equals(verb)) &&
                    sfi.getEcfi().getEntityFacade().getEntityDefinition(noun) != null) {
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

        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-validate")

        // in-parameter validation
        sd.convertValidateCleanParameters(currentParameters, eci)
        // if error(s) in parameters, return now with no results
        if (eci.getMessage().hasError()) {
            logger.warn("Found error(s) when validating input parameters for service [${getServiceName()}], so not running service. Errors: ${eci.getMessage().getErrorsString()}; the artifact stack is:\n ${eci.artifactExecution.stack}")
            return null
        }

        TransactionFacade tf = sfi.getEcfi().getTransactionFacade()
        boolean suspendedTransaction = false
        Map<String, Object> result = null
        try {
            // authentication
            sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-auth")
            // NOTE: auto user login done above, before first authz check
            if (sd.getAuthenticate() == "true" && !eci.getUser().getUserId() &&
                    !((UserFacadeImpl) eci.getUser()).getLoggedInAnonymous())
                eci.getMessage().addError("Authentication required for service [${getServiceName()}]")

            // if error in auth or for other reasons, return now with no results
            if (eci.getMessage().hasError()) {
                logger.warn("Found error(s) when checking authc for service [${getServiceName()}], so not running service. Errors: ${eci.getMessage().getErrorsString()}; the artifact stack is:\n ${eci.artifactExecution.stack}")
                return null
            }

            if (pauseResumeIfNeeded && tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = beginTransactionIfNeeded ? tf.begin(sd.getTxTimeout()) : false
            try {
                // handle sd.serviceNode."@semaphore"; do this after local transaction created, etc.
                checkAddSemaphore(sd, eci)

                sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-service")
                sfi.registerTxSecaRules(getServiceName(), currentParameters)

                // run the service through the ServiceRunner
                result = sr.runService(sd, currentParameters)

                sfi.runSecaRules(getServiceName(), currentParameters, result, "post-service")
                // if we got any errors added to the message list in the service, rollback for that too
                if (eci.getMessage().hasError()) {
                    tf.rollback(beganTransaction, "Error running service [${getServiceName()}] (message): " + eci.getMessage().getErrorsString(), null)
                }
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

                if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
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
            EntityValue serviceSemaphore = eci.getEntity().makeFind("moqui.service.semaphore.ServiceSemaphore")
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
                        if (eci.getEntity().makeFind("moqui.service.semaphore.ServiceSemaphore")
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
        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-validate")
        sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-auth")

        TransactionFacade tf = sfi.getEcfi().getTransactionFacade()
        boolean suspendedTransaction = false
        Map<String, Object> result = new HashMap()
        try {
            if (requireNewTransaction && tf.isTransactionInPlace()) suspendedTransaction = tf.suspend()
            boolean beganTransaction = tf.begin(null)
            try {
                sfi.runSecaRules(getServiceName(), currentParameters, null, "pre-service")
                sfi.registerTxSecaRules(getServiceName(), currentParameters)

                EntityDefinition ed = sfi.getEcfi().getEntityFacade().getEntityDefinition(noun)
                switch (verb) {
                    case "create": EntityAutoServiceRunner.createEntity(sfi, ed, currentParameters, result, null); break
                    case "update": EntityAutoServiceRunner.updateEntity(sfi, ed, currentParameters, result, null, null); break
                    case "delete": EntityAutoServiceRunner.deleteEntity(sfi, ed, currentParameters); break
                    case "store": EntityAutoServiceRunner.storeEntity(sfi, ed, currentParameters, result, null); break
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
                if (beganTransaction && tf.isTransactionInPlace()) tf.commit()
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
