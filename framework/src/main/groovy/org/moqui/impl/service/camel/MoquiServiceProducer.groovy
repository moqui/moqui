/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service.camel

import org.apache.camel.impl.DefaultProducer
import org.apache.camel.Exchange

/**
 * Camel Producer for the MoquiService endpoint. This processes messages send to an endpoint like:
 *
 * moquiservice:serviceName
 */
class MoquiServiceProducer extends DefaultProducer {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiServiceProducer.class)

    protected final MoquiServiceEndpoint moquiServiceEndpoint
    protected final String remaining


    public MoquiServiceProducer(MoquiServiceEndpoint moquiServiceEndpoint, String remaining) {
        super(moquiServiceEndpoint)
        this.moquiServiceEndpoint = moquiServiceEndpoint
        this.remaining = remaining
    }

    public void process(Exchange exchange) throws Exception {
        String serviceName = exchange.getIn().getHeader("ServiceName", this.remaining, String.class)
        //if (serviceName == null) {
        //    throw new RuntimeExchangeException("Missing ServiceName header", exchange)
        //}
        Map parameters = exchange.getIn().getBody(Map.class)

        // logger.warn("TOREMOVE: remaining=[${remaining}], serviceName=${serviceName}, parameters: ${parameters}")

        logger.info("Calling service [${serviceName}] with parameters [${parameters}]")
        Map<String, Object> result = moquiServiceEndpoint.getEcfi().getServiceFacade().sync().name(serviceName)
                .parameters(parameters).call()
        logger.info("Service [${serviceName}] result [${result}]")

        exchange.getOut().setBody(result)
    }
}
