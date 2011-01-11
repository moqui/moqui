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

import org.moqui.service.ServiceCallSpecial

class ServiceCallSpecialImpl extends ServiceCallImpl implements ServiceCallSpecial {

    ServiceCallSpecialImpl(ServiceFacadeImpl sfi) {
        super(sfi)
    }

    @Override
    ServiceCallSpecial name(String serviceName) { this.setServiceName(serviceName); return this }

    @Override
    ServiceCallSpecial name(String v, String n) { path = null; verb = v; noun = n; return this }

    @Override
    ServiceCallSpecial name(String p, String v, String n) { path = p; verb = v; noun = n; return this }

    @Override
    ServiceCallSpecial context(Map<String, Object> map) { context.putAll(map); return this }

    @Override
    ServiceCallSpecial context(String name, Object value) { context.put(name, value); return this }

    @Override
    void registerOnCommit() {
        // TODO implement
    }

    @Override
    void registerOnRollback() {
        // TODO implement
    }
}
