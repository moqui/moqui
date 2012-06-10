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
package org.moqui.impl.service.runner

import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory

import org.moqui.context.ExecutionContext
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner

public class RemoteXmlRpcServiceRunner implements ServiceRunner {
    protected ServiceFacadeImpl sfi = null

    RemoteXmlRpcServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        ExecutionContext ec = sfi.ecfi.getExecutionContext()

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
