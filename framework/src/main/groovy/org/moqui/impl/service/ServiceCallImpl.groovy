/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service

import groovy.transform.CompileStatic
import org.moqui.service.ServiceCall

@CompileStatic
class ServiceCallImpl implements ServiceCall {
    protected ServiceFacadeImpl sfi
    protected String path = null
    protected String verb = null
    protected String noun = null
    protected ServiceDefinition sd = null

    protected String serviceName = null
    protected String serviceNameNoHash = null

    protected Map<String, Object> parameters = new HashMap<String, Object>()

    ServiceCallImpl(ServiceFacadeImpl sfi) { this.sfi = sfi }

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
    String getServiceName() {
        if (serviceName == null) serviceName = (path ? path + "." : "") + verb + (noun ? "#" + noun : "")
        return serviceName
    }
    String getServiceNameNoHash() {
        if (serviceNameNoHash == null) serviceNameNoHash = (path ? path + "." : "") + verb + (noun ?: "")
        return serviceNameNoHash
    }

    @Override
    Map<String, Object> getCurrentParameters() { return parameters }

    ServiceDefinition getServiceDefinition() {
        if (sd == null) sd = sfi.getServiceDefinition(getServiceName())
        return sd
    }

    boolean isEntityAutoPattern() { return sfi.isEntityAutoPattern(path, verb, noun) }
}
