/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
