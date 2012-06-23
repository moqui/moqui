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

import org.apache.camel.Producer
import org.apache.camel.Consumer
import org.apache.camel.Processor
import org.apache.camel.impl.DefaultEndpoint

import org.moqui.impl.context.ExecutionContextFactoryImpl

class MoquiServiceEndpoint extends DefaultEndpoint {
    protected String remaining
    protected ExecutionContextFactoryImpl ecfi

    public MoquiServiceEndpoint(String uri, MoquiServiceComponent component, String remaining) {
        super(uri, component)
        this.remaining = remaining
        this.ecfi = component.getEcfi()
    }

    public Producer createProducer() throws Exception {
        return new MoquiServiceProducer(this, remaining)
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new MoquiServiceConsumer(this, processor, remaining)
    }

    public boolean isSingleton() { return true }
    public ExecutionContextFactoryImpl getEcfi() { return ecfi }
}
