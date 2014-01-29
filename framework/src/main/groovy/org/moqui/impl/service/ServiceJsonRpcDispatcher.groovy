package org.moqui.impl.service

import org.moqui.context.ArtifactAuthorizationException

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

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.moqui.impl.context.ExecutionContextImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/* NOTE: see JSON-RPC2 specs at: http://www.jsonrpc.org/specification */

public class ServiceJsonRpcDispatcher {
    protected final static Logger logger = LoggerFactory.getLogger(ServiceJsonRpcDispatcher.class)

    final static int PARSE_ERROR = -32700 // Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text.
    final static int INVALID_REQUEST = -32600 // The JSON sent is not a valid Request object.
    final static int METHOD_NOT_FOUND = -32601 // The method does not exist / is not available.
    final static int INVALID_PARAMS = -32602 // Invalid method parameter(s).
    final static int INTERNAL_ERROR = -32603 // Internal JSON-RPC error.

    protected ExecutionContextImpl eci

    public ServiceJsonRpcDispatcher(ExecutionContextImpl eci) {
        this.eci = eci
    }

    public void dispatch(HttpServletRequest request, HttpServletResponse response) {
        Map callMap = eci.web.getRequestParameters()
        if (callMap._requestBodyJsonList) {
            List callList = callMap._requestBodyJsonList
            List<Map> jsonRespList = []
            for (Object callSingleObj in callList) {
                if (callSingleObj instanceof Map) {
                    Map callSingleMap = (Map) callSingleObj
                    jsonRespList.add(callSingle(callSingleMap.method as String, callSingleMap.params, callSingleMap.id ?: null))
                } else {
                    jsonRespList.add(callSingle(null, callSingleObj, null))
                }
            }
        } else {
            // logger.info("========= JSON-RPC request with map: ${callMap}")
            Map jsonResp = callSingle(callMap.method as String, callMap.params, callMap.id ?: null)
            eci.getWeb().sendJsonResponse(jsonResp)
        }
    }

    protected Map callSingle(String method, Object paramsObj, Object id) {
        // logger.warn("========= JSON-RPC call method=[${method}], id=[${id}], params=${paramsObj}")

        String errorMessage = null
        Integer errorCode = null
        ServiceDefinition sd = method ? eci.service.getServiceDefinition(method) : null
        if (eci.web.getRequestParameters()._requestBodyJsonParseError) {
            errorMessage = eci.web.getRequestParameters()._requestBodyJsonParseError
            errorCode = PARSE_ERROR
        } else if (!method) {
            errorMessage = "No method specified"
            errorCode = INVALID_REQUEST
        } else if (sd == null) {
            errorMessage = "Unknown service [${method}]"
            errorCode = METHOD_NOT_FOUND
        } else if (!(paramsObj instanceof Map)) {
            // We expect named parameters (JSON object)
            errorMessage = "Parameters must be named parameters (JSON object, Java Map), got type [${paramsObj.class.getName()}]"
            errorCode = INVALID_PARAMS
        } else if (sd.serviceNode."@allow-remote" != "true") {
            errorMessage = "Service [${sd.serviceName}] does not allow remote calls"
            errorCode = METHOD_NOT_FOUND
        }

        Map result = null
        if (errorMessage == null) {
            try {
                result = eci.service.sync().name(sd.getServiceName()).parameters((Map) paramsObj).call()
                if (eci.getMessage().hasError()) {
                    logger.warn("Got errors in JSON-RPC call to service [${sd.serviceName}]: ${eci.message.errorsString}")
                    errorMessage = eci.message.errorsString
                    // could use whatever code here as long as it is not -32768 to -32000, this was chosen somewhat arbitrarily
                    errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                }
            } catch (ArtifactAuthorizationException e) {
                logger.error("Authz error calling service ${sd.getServiceName()} from JSON-RPC request: ${e.toString()}", e)
                errorMessage = e.getMessage()
                // could use whatever code here as long as it is not -32768 to -32000, this was chosen somewhat arbitrarily
                errorCode = HttpServletResponse.SC_FORBIDDEN
            } catch (Exception e) {
                logger.error("Error calling service ${sd.getServiceName()} from JSON-RPC request: ${e.toString()}", e)
                errorMessage = e.getMessage()
                // could use whatever code here as long as it is not -32768 to -32000, this was chosen somewhat arbitrarily
                errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            }
        }

        if (errorMessage == null) {
            return [jsonrpc:"2.0", id:id, result:result]
        } else {
            logger.warn("Responding with JSON-RPC error code [${errorCode}]: ${errorMessage}")
            return [jsonrpc:"2.0", id:id, error:[code:errorCode, message:errorMessage]]
        }
    }
}
