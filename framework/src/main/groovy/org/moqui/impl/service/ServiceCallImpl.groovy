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
    protected ServiceDefinition sd = null

    protected Map<String, Object> parameters = new HashMap<String, Object>()

    ServiceCallImpl(ServiceFacadeImpl sfi) {
        this.sfi = sfi
    }

    protected void setServiceName(String serviceName) {
        sd = sfi.getServiceDefinition(serviceName)
        if (sd != null) {
            path = sd.getPath()
            verb = sd.getVerb()
            noun = sd.getNoun()
        } else {
            path = ServiceDefinition.getPathFromName(serviceName)
            verb = ServiceDefinition.getVerbFromName(serviceName)
            noun = ServiceDefinition.getNounFromName(serviceName)
        }
    }

    @Override
    String getServiceName() { return (path ? path + "." : "") + verb + (noun ? "#" + noun : "") }

    @Override
    Map<String, Object> getCurrentParameters() { return parameters }

    ServiceDefinition getServiceDefinition() {
        if (sd == null) sd = sfi.getServiceDefinition(getServiceName())
        return sd
    }

    boolean isEntityAutoPattern() { return sfi.isEntityAutoPattern(path, verb, noun) }
}
