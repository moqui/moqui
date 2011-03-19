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

import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response
import org.moqui.context.ExecutionContext
import org.apache.http.entity.StringEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.HttpResponse
import org.apache.http.HttpHost
import org.apache.http.HttpEntity
import org.apache.http.util.EntityUtils

public class RemoteJsonRpcServiceRunner implements ServiceRunner {
    protected ServiceFacadeImpl sfi = null

    RemoteJsonRpcServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        ExecutionContext ec = sfi.ecfi.getExecutionContext()

        String location = sd.serviceNode."@location"
        String method = sd.serviceNode."@method"
        if (!location) throw new IllegalArgumentException("Cannot call remote service [${sd.serviceName}] because it has no location specified.")
        if (!method) throw new IllegalArgumentException("Cannot call remote service [${sd.serviceName}] because it has no method specified.")

        Long callId = UUID.randomUUID().leastSignificantBits
        JSONRPC2Request jrr = new JSONRPC2Request(method, parameters, callId)
        String jsonRequest = jrr.toString()

        // send the remote call
        StringEntity responseEntity = new StringEntity(jsonRequest, "application/x-json; charset=\"UTF-8\"")
        responseEntity.setChunked(true)
        HttpPost httpPost = new HttpPost(location)
        httpPost.setEntity(responseEntity)

        DefaultHttpClient httpClient = new DefaultHttpClient()
        HttpContext localContext = new BasicHttpContext()
        HttpResponse response = httpClient.execute(httpPost, localContext)

        HttpEntity entity = response.getEntity()
        String jsonResponse = EntityUtils.toString(entity)

        // parse and return the results
        JSONRPC2Response respOut = JSONRPC2Response.parse(jsonResponse)
        if (respOut.indicatesSuccess()) {
            Object jr = respOut.result
            if (jr instanceof Map<String, Object>) {
                return jr
            } else {
                return [response:jr]
            }
        } else {
            ec.message.addError(respOut.error.message)
            return null
        }
    }

    public void destroy() { }
}
