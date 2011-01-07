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

import org.moqui.service.ServiceCallSync
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.service.runner.EntityAutoServiceRunner

class ServiceCallSyncImpl extends ServiceCallImpl implements ServiceCallSync {
    protected boolean requireNewTransaction = false
    protected int transactionIsolation = -1

    ServiceCallSyncImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallSync requireNewTransaction(boolean rnt) { this.requireNewTransaction = rnt; return this }

    @Override
    ServiceCallSync transactionIsolation(int ti) { this.transactionIsolation = ti; return this }

    @Override
    Map<String, Object> call() {
        ServiceDefinition sd = sfi.getServiceDefinition(getServiceName())
        if (!sd) {
            // if verb is create|update|delete and noun is a valid entity name, do an implicit entity-auto
            if ((verb == "create" || verb == "update" || verb == "delete") && sfi.ecfi.entityFacade.getEntityDefinition(noun) != null) {
                return runImplicitEntityAuto()
            } else {
                throw new IllegalArgumentException("Could not find service with name [${getServiceName()}]")
            }
        }

        String type = sd.serviceNode."@type"
        if (type == "interface") throw new IllegalArgumentException("Cannot run interface service [${getServiceName()}]")

        ServiceRunner sr = sfi.getServiceRunner(type)

        // TODO trigger SECAs

        // TODO validation

        // TODO authentication

        // TODO transaction handling

        // TODO other...

        Map<String, Object> result = sr.runService(sd, this.context)

        return result
    }

    protected Map<String, Object> runImplicitEntityAuto() {
        // TODO trigger SECAs
        // TODO authentication
        // TODO transaction handling
        EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(noun)
        Map<String, Object> result = new HashMap()
        if (verb == "create") {
            EntityAutoServiceRunner.createEntity(sfi, ed, context, result, null)
        } else if (verb == "update") {
            EntityAutoServiceRunner.updateEntity(sfi, ed, context, result, null)
        } else if (verb == "delete") {
            EntityAutoServiceRunner.deleteEntity(sfi, ed, context)
        }
        return result
    }
}
