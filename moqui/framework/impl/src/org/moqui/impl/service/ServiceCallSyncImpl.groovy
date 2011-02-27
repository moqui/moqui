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
        if (sd == null) {
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

        ServiceRunner sr = sfi.getServiceRunner(serviceType)
        if (sr == null) throw new IllegalArgumentException("Could not find service runner for type [${serviceType}] for service [${getServiceName()}]")

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

        sfi.runSecaRules(getServiceName(), this.parameters, null, "pre-validate")

        // validation
        sd.convertValidateCleanParameters(this.parameters, eci)
        // if error(s) in parameters, return now with no results
        if (eci.message.errors) return null

        boolean userLoggedIn = false
        TransactionFacade tf = sfi.ecfi.getTransactionFacade()
        Transaction parentTransaction = null
        Map<String, Object> result = null
        try {
            // authentication
            sfi.runSecaRules(getServiceName(), this.parameters, null, "pre-auth")
            // always try to login the user if parameters are specified
            String userId = parameters.authUserAccount?.userId ?: parameters.authUsername
            String password = parameters.authUserAccount?.currentPassword ?: parameters.authPassword
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
                sfi.runSecaRules(getServiceName(), this.parameters, null, "pre-service")
                sfi.registerTxSecaRules(getServiceName(), this.parameters)
                result = sr.runService(sd, this.parameters)
                sfi.runSecaRules(getServiceName(), this.parameters, result, "post-service")
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
                sfi.runSecaRules(getServiceName(), this.parameters, result, "post-commit")
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

    protected Map<String, Object> runImplicitEntityAuto() {
        // NOTE: no authentication, assume not required for this; security settings can override this and require
        //     permissions, which will require authentication
        sfi.runSecaRules(getServiceName(), this.parameters, "pre-validate")
        sfi.runSecaRules(getServiceName(), this.parameters, "pre-auth")

        TransactionFacade tf = sfi.ecfi.getTransactionFacade()
        Transaction parentTransaction = null
        Map<String, Object> result = new HashMap()
        try {
            if (requireNewTransaction && tf.isTransactionInPlace()) parentTransaction = tf.suspend()
            boolean beganTransaction = tf.begin(null)
            try {
                sfi.runSecaRules(getServiceName(), this.parameters, "pre-service")
                sfi.registerTxSecaRules(getServiceName(), this.parameters)

                EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
                if (verb == "create") {
                    EntityAutoServiceRunner.createEntity(sfi, ed, parameters, result, null)
                } else if (verb == "update") {
                    EntityAutoServiceRunner.updateEntity(sfi, ed, parameters, result, null)
                } else if (verb == "delete") {
                    EntityAutoServiceRunner.deleteEntity(sfi, ed, parameters)
                }

                sfi.runSecaRules(getServiceName(), result, "post-service")
            } catch (Throwable t) {
                tf.rollback(beganTransaction, "Error getting primary sequenced ID", t)
            } finally {
                if (tf.isTransactionInPlace()) tf.commit(beganTransaction)
                sfi.runSecaRules(getServiceName(), this.parameters, "post-commit")
            }
        } catch (TransactionException e) {
            throw e
        } finally {
            if (parentTransaction != null) tf.resume(parentTransaction)
        }
        return result
    }
}
