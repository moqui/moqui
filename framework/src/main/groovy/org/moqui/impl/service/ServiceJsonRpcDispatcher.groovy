package org.moqui.impl.service
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

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException

import javax.servlet.http.HttpServletResponse

import org.moqui.impl.context.ExecutionContextImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class ServiceJsonRpcDispatcher {
    protected final static Logger logger = LoggerFactory.getLogger(ServiceJsonRpcDispatcher.class)

    protected ExecutionContextImpl eci

    public ServiceJsonRpcDispatcher(ExecutionContextImpl eci) {
        this.eci = eci
    }

    public void dispatch(InputStream is, HttpServletResponse response) {
        // TODO: support batched calls with different IDs
        BufferedReader br = new BufferedReader(new InputStreamReader(is, (String) response.getCharacterEncoding() ?: "UTF-8"))
        StringBuilder jsonBuilder = new StringBuilder()
        String currentLine
        while ((currentLine = br.readLine()) != null) jsonBuilder.append(currentLine).append('\n');

        JSONRPC2Request jrr
        try {
            jrr = JSONRPC2Request.parse(jsonBuilder.toString())
        } catch (JSONRPC2ParseException e) {
            logger.error("JSON-RPC parse exception: ${e.toString()}", e)
            int errorCode = e.getCauseType() == JSONRPC2ParseException.JSON ? JSONRPC2Error.PARSE_ERROR.code : JSONRPC2Error.INVALID_REQUEST.code
            JSONRPC2Response respOut = new JSONRPC2Response(new JSONRPC2Error(errorCode, e.getMessage()))
            eci.getWeb().sendJsonResponse(respOut.toString())
            return
        } catch (Exception e) {
            logger.error("JSON-RPC error during parse: ${e.toString()}", e)
            JSONRPC2Response respOut = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.PARSE_ERROR.code, e.getMessage()))
            eci.getWeb().sendJsonResponse(respOut.toString())
            return
        }

        String method = jrr.getMethod()

        String errorMessage = null
        Integer errorCode = null
        ServiceDefinition sd = eci.service.getServiceDefinition(method)
        if (sd == null) {
            errorMessage = "Unknown service [${method}]"
            errorCode = JSONRPC2Error.METHOD_NOT_FOUND.getCode()
        } else if (sd.serviceNode."@allow-remote" != "true") {
            errorMessage = "Service [${sd.serviceName}] does not allow remote calls"
            errorCode = JSONRPC2Error.METHOD_NOT_FOUND.getCode()
        } else if (jrr.getParamsType() != JSONRPC2ParamsType.OBJECT) {
            // We expect named parameters (JSON object)
            errorMessage = "Parameters must be named parameters (object), got type [${jrr.getParamsType().toString()}]"
            errorCode = JSONRPC2Error.INVALID_PARAMS.getCode()
        }

        Map result = null
        if (errorMessage == null) {
            Map params = jrr.getNamedParams()
            result = eci.service.sync().name(sd.serviceName).parameters(params).call()

            if (eci.getMessage().hasError()) {
                logger.warn("Got errors in JSON-RPC call to service [${sd.serviceName}]: ${eci.message.errorsString}")
                errorMessage = eci.message.errorsString
                // could use whatever code here as long as it is not -32768 to -32000, this was chosen somewhat arbitrarily
                errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            }
        }

        if (errorMessage == null) {
            JSONRPC2Response respOut = new JSONRPC2Response(result, jrr.getID())
            eci.getWeb().sendJsonResponse(respOut.toString())
        } else {
            logger.warn("Responding with JSON-RPC error: ${errorMessage}")
            JSONRPC2Response respOut = new JSONRPC2Response(new JSONRPC2Error(errorCode, errorMessage), jrr.getID())
            eci.getWeb().sendJsonResponse(respOut.toString())
        }
    }
}
