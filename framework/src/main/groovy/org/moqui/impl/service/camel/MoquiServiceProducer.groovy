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
