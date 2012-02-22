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
 *
 * Based on Redstone XML-RPC which is Copyright (c) 2005 Redstone Handelsbolag
 * which is licensed under the GNU LGPL license.
 */

import org.moqui.impl.context.ExecutionContextImpl;

import redstone.xmlrpc.XmlRpcParser
import redstone.xmlrpc.XmlRpcServer
import redstone.xmlrpc.XmlRpcException
import redstone.xmlrpc.XmlRpcMessages
import redstone.xmlrpc.XmlRpcFault

public class ServiceXmlRpcDispatcher extends XmlRpcParser {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceXmlRpcDispatcher.class)

    protected XmlRpcServer server
    protected ExecutionContextImpl eci

    protected String methodName
    protected List arguments = new ArrayList()

    public ServiceXmlRpcDispatcher(ExecutionContextImpl eci) {
        this.server = eci.ecfi.xmlRpcServer
        this.eci = eci
    }

    public void dispatch(InputStream xmlInput, Writer responseOutput) throws XmlRpcException {
        // Parse the inbound XML-RPC message. May throw an exception.
        parse(xmlInput)

        try {
            ServiceDefinition sd = eci.service.getServiceDefinition(methodName)
            if (sd == null)
                throw new IllegalArgumentException("Received XML-RPC service call for unknown service [${methodName}]")
            if (sd.serviceNode."@allow-remote" != "true")
                throw new IllegalArgumentException("Received XML-RPC service call to service [${sd.serviceName}] that does not allow remote calls.")

            Map params = (Map) arguments.get(0)

            Map result = eci.service.sync().name(sd.serviceName).parameters(params).call()

            // write the return to the responseOutput
            if (result) {
                server.getSerializer().writeEnvelopeHeader(result, responseOutput)
                server.getSerializer().serialize(result, responseOutput)
                server.getSerializer().writeEnvelopeFooter(result, responseOutput)
            }
        } catch (Throwable t) {
            int code = -1;
            if (t instanceof XmlRpcFault) code = ((XmlRpcFault) t).getErrorCode()
            writeError(code, t.getClass().getName() + ": " + t.getMessage(), responseOutput)
        }
    }

    public void endElement(String uri, String name, String qualifiedName) {
        if (name.equals("methodName")) methodName = this.consumeCharData()
        else super.endElement(uri, name, qualifiedName);
    }

    protected void handleParsedValue(Object value) { arguments.add(value) }

    /** Creates an XML-RPC fault struct and puts it into the writer buffer. */
    protected void writeError(int code, String message, Writer responseOutput) {
        try {
            logger.warn(message)
            this.server.getSerializer().writeError(code, message, responseOutput)
        } catch (IOException ignore) {
            logger.error(XmlRpcMessages.getString("XmlRpcDispatcher.ErrorSendingFault"))
        }
    }
}
