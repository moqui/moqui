/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service.runner

import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory

import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner

public class RemoteXmlrpcServiceRunner implements ServiceRunner {
    protected ServiceFacadeImpl sfi = null

    RemoteXmlrpcServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        String location = sd.serviceNode."@location"
        String method = sd.serviceNode."@method"
        if (!location) throw new IllegalArgumentException("Cannot call remote service [${sd.serviceName}] because it has no location specified.")
        if (!method) throw new IllegalArgumentException("Cannot call remote service [${sd.serviceName}] because it has no method specified.")

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl()
        config.setServerURL(new URL(location))
        XmlRpcClient client = new XmlRpcClient()
        client.setTransportFactory(new XmlRpcCommonsTransportFactory(client))
        client.setConfig(config)

        Object result = client.execute(method, [parameters])

        if (!result) return null
        if (result instanceof Map<String, Object>) {
            return result
        } else if (result instanceof List && ((List) result).size() == 1 && ((List) result).get(0) instanceof Map<String, Object>) {
            return (Map<String, Object>) ((List) result).get(0)
        } else {
            return [response:result]
        }
    }

    public void destroy() { }
}
