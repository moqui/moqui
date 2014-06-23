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

import org.apache.camel.CamelExecutionException
import org.apache.camel.ProducerTemplate

import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner
import org.moqui.service.ServiceException
import org.apache.camel.Endpoint
import org.moqui.impl.service.camel.MoquiServiceConsumer

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class CamelServiceRunner implements ServiceRunner {
    protected final static Logger logger = LoggerFactory.getLogger(CamelServiceRunner.class)

    protected ServiceFacadeImpl sfi
    protected ProducerTemplate producerTemplate

    CamelServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) {
        this.sfi = sfi
        producerTemplate = sfi.ecfi.camelContext.createProducerTemplate()
        return this
    }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        // location is mandatory, method is optional and only really used to call other Moqui services (goes in the ServiceName header)
        String endpointUri = sd.getLocation()
        if (!endpointUri) throw new ServiceException("Service [${sd.serviceName}] is missing the location attribute and it is required for running a Camel service.")

        Map<String, Object> headers = new HashMap<String, Object>()

        Endpoint endpoint = sfi.ecfi.moquiServiceComponent.createEndpoint(endpointUri)
        MoquiServiceConsumer consumer = sfi.ecfi.getCamelConsumer(endpoint.getEndpointUri())
        if (consumer != null) {
            try {
                return consumer.process(sd, parameters)
            } catch (CamelExecutionException e) {
                sfi.ecfi.getExecutionContext().message.addError(e.message)
                return null
            }
        } else {
            logger.warn("No consumer found for service [${sd.serviceName}], using ProducerTemplate to send the message")
            try {
                return (Map<String, Object>) producerTemplate.requestBodyAndHeaders(endpointUri, parameters, headers)
            } catch (CamelExecutionException e) {
                sfi.ecfi.getExecutionContext().message.addError(e.message)
                return null
            }
        }
    }

    public void destroy() {
        producerTemplate.stop()
    }
}
