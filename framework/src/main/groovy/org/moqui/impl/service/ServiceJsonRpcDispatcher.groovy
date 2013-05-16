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

import javax.servlet.http.HttpServletResponse

import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.service.ServiceException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class ServiceJsonRpcDispatcher {
    protected final static Logger logger = LoggerFactory.getLogger(ServiceJsonRpcDispatcher.class)

    protected ExecutionContextImpl eci

    public ServiceJsonRpcDispatcher(ExecutionContextImpl eci) {
        this.eci = eci
    }

    public void dispatch(InputStream is, HttpServletResponse response) {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, (String) request.getCharacterEncoding() ?: "UTF-8"))
        StringBuilder jsonBuilder = new StringBuilder()
        String currentLine
        while ((currentLine = br.readLine()) != null) jsonBuilder.append(currentLine).append('\n');

        JSONRPC2Request jrr = JSONRPC2Request.parse(jsonBuilder.toString())
        String method = jrr.getMethod()

        ServiceDefinition sd = eci.service.getServiceDefinition(method)
        if (sd == null)
            throw new ServiceException("Received JSON-RPC service call for unknown service [${method}]")
        if (sd.serviceNode."@allow-remote" != "true")
            throw new ServiceException("Received JSON-RPC service call to service [${sd.serviceName}] that does not allow remote calls.")

        // We expect named parameters (JSON object)
        JSONRPC2ParamsType paramsType = jrr.getParamsType();
        if (paramsType != JSONRPC2ParamsType.OBJECT) {
            throw new ServiceException("Received JSON-RPC service call with parameters of type [${paramsType.toString()}], we need an OBJECT (Map) type")
        }

        Map params = (Map) jrr.getParams()
        // probably don't need this: NamedParamsRetriever np = new NamedParamsRetriever(params);

        Map result = eci.service.sync().name(sd.serviceName).parameters(params).call()

        String jsonStr
        if (eci.getMessage().hasError()) {
            logger.warn("Got errors in JSON-RPC call to service [${sd.serviceName}]: ${eci.message.errorsString}")
            JSONRPC2Response respOut = new JSONRPC2Response(eci.message.errorsString, jrr.getID())
            jsonStr = respOut.toString()
        } else {
            JSONRPC2Response respOut = new JSONRPC2Response(result, jrr.getID())
            jsonStr = respOut.toString()
        }

        response.setContentType("application/x-json")
        // NOTE: String.length not correct for byte length
        String charset = response.characterEncoding ?: "UTF-8"
        int length = jsonStr.getBytes(charset).length
        response.setContentLength(length)

        if (logger.infoEnabled) logger.info("Sending JSON-RPC response of length [${length}] with [${charset}] encoding")

        try {
            response.writer.write(jsonStr)
            response.writer.flush()
        } catch (IOException e) {
            logger.error("Error sending JSON-RPC string response", e)
        }
    }
}
