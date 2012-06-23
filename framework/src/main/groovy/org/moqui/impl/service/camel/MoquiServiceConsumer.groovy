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

class MoquiServiceConsumer extends DefaultConsumer {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiServiceConsumer.class)

    protected final MoquiServiceEndpoint moquiServiceEndpoint
    protected final String remaining


    public MoquiServiceConsumer(MoquiServiceEndpoint moquiServiceEndpoint, Processor processor, String remaining) {
        super(moquiServiceEndpoint, processor)
        this.moquiServiceEndpoint = moquiServiceEndpoint
        this.remaining = remaining
    }

    // TODO: what else to do here to be a from in a route?
}
