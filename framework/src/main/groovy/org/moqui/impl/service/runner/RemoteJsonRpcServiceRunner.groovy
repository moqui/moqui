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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.moqui.context.ExecutionContext
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner

import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.entity.StringEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.HttpEntity
import org.apache.http.util.EntityUtils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class RemoteJsonRpcServiceRunner implements ServiceRunner {
    protected final static Logger logger = LoggerFactory.getLogger(RemoteJsonRpcServiceRunner.class)

    protected ServiceFacadeImpl sfi = null

    RemoteJsonRpcServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        ExecutionContext ec = sfi.ecfi.getExecutionContext()

        String location = sd.serviceNode."@location"
        String method = sd.serviceNode."@method"
        if (!location) throw new IllegalArgumentException("Cannot call remote service [${sd.serviceName}] because it has no location specified.")
        if (!method) throw new IllegalArgumentException("Cannot call remote service [${sd.serviceName}] because it has no method specified.")

        Map jsonRequestMap = [jsonrpc:"2.0", id:1, method:method, params:parameters]
        JsonBuilder jb = new JsonBuilder()
        jb.call(jsonRequestMap)
        String jsonRequest = jb.toString()

        // logger.warn("======== JSON-RPC remote service request to location [${location}]: ${jsonRequest}")

        String jsonResponse = null
        CloseableHttpClient httpClient = HttpClients.createDefault()
        try {
            HttpPost httpPost = new HttpPost(location)
            // send the remote call
            StringEntity requestEntity = new StringEntity(jsonRequest, ContentType.create("application/json", "UTF-8"))
            httpPost.setEntity(requestEntity)
            httpPost.setHeader("Content-Type", "application/json")

            logger.info("JSON-RPC remote service [${sd.getServiceName()}] request: ${httpPost.getRequestLine()}, ${httpPost.getAllHeaders()}, ${httpPost.getEntity().contentLength} bytes")
            // logger.warn("======== JSON-RPC remote service request entity [length:${httpPost.getEntity().contentLength}]: ${EntityUtils.toString(httpPost.getEntity())}")
            CloseableHttpResponse response = httpClient.execute(httpPost)
            try {
                HttpEntity entity = response.getEntity()
                jsonResponse = EntityUtils.toString(entity)
            } finally {
                response.close()
            }

        } finally {
            httpClient.close()
        }

        // logger.warn("======== JSON-RPC remote service response from location [${location}]: ${jsonResponse}")

        // parse and return the results
        JsonSlurper slurper = new JsonSlurper()
        Object jsonObj
        try {
            // logger.warn("========== JSON-RPC response: ${jsonResponse}")
            jsonObj = slurper.parseText(jsonResponse)
        } catch (Throwable t) {
            String errMsg = "Error parsing JSON-RPC response for service [${sd.getServiceName()}]: ${t.toString()}"
            logger.error(errMsg, t)
            ec.message.addError(errMsg)
            return null
        }

        if (jsonObj instanceof Map) {
            Map responseMap = jsonObj
            if (responseMap.error) {
                logger.error("JSON-RPC service [${sd.getServiceName()}] returned an error: ${responseMap.error}")
                ec.message.addError((String) responseMap.error?.message ?: "JSON-RPC error with no message, code [${responseMap.error?.code}]")
                return null
            } else {
                Object jr = responseMap.result
                if (jr instanceof Map<String, Object>) {
                    return jr
                } else {
                    return [response:jr]
                }
            }
        } else {
            String errMsg = "JSON-RPC response was not a object/Map for service [${sd.getServiceName()}]: ${jsonObj}"
            logger.error(errMsg)
            ec.message.addError(errMsg)
            return null
        }
    }

    public void destroy() { }
}
