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
import org.moqui.impl.service.runner.ServiceRunner

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

        String type = sd.serviceNode."@type"
        if (type == "interface") throw new IllegalArgumentException("Cannot run interface service [${getServiceName()}]")

        ServiceRunner sr = sfi.getServiceRunner(type)

        // TODO trigger SECAs

        // TODO validation

        // TODO authentication

        // TODO other...

        // TODO this is a simple implementation so add support for transaction handling, etc
        Map<String, Object> result = sr.runService(sd, this.context)

        return result
    }
}
