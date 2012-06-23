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

import org.apache.camel.impl.DefaultConsumer
import org.apache.camel.Processor
import org.moqui.impl.service.ServiceDefinition
import org.apache.camel.Exchange
import org.apache.camel.RuntimeCamelException
import org.apache.camel.Message

class MoquiServiceConsumer extends DefaultConsumer {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiServiceConsumer.class)

    protected final MoquiServiceEndpoint moquiServiceEndpoint
    protected final String remaining


    MoquiServiceConsumer(MoquiServiceEndpoint moquiServiceEndpoint, Processor processor, String remaining) {
        super(moquiServiceEndpoint, processor)
        this.moquiServiceEndpoint = moquiServiceEndpoint
        this.remaining = remaining

        moquiServiceEndpoint.getEcfi().registerCamelConsumer(moquiServiceEndpoint.getEndpointUri(), this)
    }

    Map<String, Object> process(ServiceDefinition sd, Map<String, Object> parameters) {
        try {
            Exchange exchange = getEndpoint().createExchange()

            // populate exchange.in
            exchange.in.setBody(parameters)

            try {
                getProcessor().process(exchange)
            } catch (Exception e) {
                exchange.setException(e)
            }

            // get response (exchange.out)
            Map<String, Object> result = exchange.out.getBody(Map.class)
            return result
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot process request", e);
        }
    }
}
