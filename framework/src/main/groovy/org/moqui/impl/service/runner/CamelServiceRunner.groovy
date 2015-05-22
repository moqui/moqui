/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
