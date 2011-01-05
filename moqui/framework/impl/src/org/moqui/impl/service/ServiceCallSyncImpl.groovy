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
        // TODO implement
        return null
    }
}
