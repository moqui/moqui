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

import org.moqui.service.ServiceCall

class ServiceCallImpl implements ServiceCall {
    protected ServiceFacadeImpl sfi
    protected String path = null
    protected String verb = null
    protected String noun = null

    protected Map<String, Object> context = new HashMap<String, Object>()

    ServiceCallImpl(ServiceFacadeImpl sfi) {
        this.sfi = sfi
    }

    @Override
    ServiceCall serviceName(String serviceName) {
        StringBuilder sn = new StringBuilder(serviceName)
        if (sn.indexOf("#") > 0) {
            noun = sn.substring(sn.indexOf("#") + 1)
            sn.delete(sn.indexOf("#"), sn.length())
        }
        if (sn.indexOf(".") > 0) {
            verb = sn.substring(sn.lastIndexOf(".") + 1)
            sn.delete(sn.lastIndexOf("."), sn.length())
            path = sn ? sn.toString() : null
        } else {
            path = null
            verb = sn.toString()
        }
        return this
    }

    @Override
    ServiceCall serviceName(String v, String n) { path = null; verb = v; noun = n; return this }

    @Override
    ServiceCall serviceName(String p, String v, String n) { path = p; verb = v; noun = n; return this }

    @Override
    String getServiceName() { return "${path}.${verb}#${noun}" }

    @Override
    ServiceCall context(Map<String, Object> map) { context.putAll(map); return this }

    @Override
    ServiceCall context(String name, Object value) { context.put(name, value); return this }

    @Override
    Map<String, Object> getContext() { return context }
}
