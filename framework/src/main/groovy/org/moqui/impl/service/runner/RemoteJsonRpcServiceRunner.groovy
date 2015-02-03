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
import org.moqui.impl.StupidWebUtilities
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner

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

        return runJsonService(sd.getServiceName(), location, method, parameters, ec)
    }

    static Map<String, Object> runJsonService(String serviceName, String location, String method,
                                              Map<String, Object> parameters, ExecutionContext ec) {
        Map jsonRequestMap = [jsonrpc:"2.0", id:1, method:method, params:parameters]
        JsonBuilder jb = new JsonBuilder()
        jb.call(jsonRequestMap)
        String jsonRequest = jb.toString()

        // logger.warn("======== JSON-RPC remote service request to location [${location}]: ${jsonRequest}")

        String jsonResponse = StupidWebUtilities.simpleHttpStringRequest(location, jsonRequest, "application/json")

        // logger.info("JSON-RPC remote service [${sd.getServiceName()}] request: ${httpPost.getRequestLine()}, ${httpPost.getAllHeaders()}, ${httpPost.getEntity().contentLength} bytes")
        // logger.warn("======== JSON-RPC remote service request entity [length:${httpPost.getEntity().contentLength}]: ${EntityUtils.toString(httpPost.getEntity())}")

        // logger.warn("======== JSON-RPC remote service response from location [${location}]: ${jsonResponse}")

        // parse and return the results
        JsonSlurper slurper = new JsonSlurper()
        Object jsonObj
        try {
            // logger.warn("========== JSON-RPC response: ${jsonResponse}")
            jsonObj = slurper.parseText(jsonResponse)
        } catch (Throwable t) {
            String errMsg = "Error parsing JSON-RPC response for service [${serviceName ?: method}]: ${t.toString()}"
            logger.error(errMsg, t)
            ec.message.addError(errMsg)
            return null
        }

        if (jsonObj instanceof Map) {
            Map responseMap = jsonObj
            if (responseMap.error) {
                logger.error("JSON-RPC service [${serviceName ?: method}] returned an error: ${responseMap.error}")
                ec.message.addError((String) responseMap.error?.message ?: "JSON-RPC error with no message, code [${responseMap.error?.code}]")
                return null
            } else {
                Object jr = responseMap.result
                if (jr instanceof Map) {
                    return jr
                } else {
                    return [response:jr]
                }
            }
        } else {
            String errMsg = "JSON-RPC response was not a object/Map for service [${serviceName ?: method}]: ${jsonObj}"
            logger.error(errMsg)
            ec.message.addError(errMsg)
            return null
        }
    }

    public void destroy() { }
}
