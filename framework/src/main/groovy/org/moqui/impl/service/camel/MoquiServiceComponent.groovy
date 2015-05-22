/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.service.camel

import org.apache.camel.impl.DefaultComponent
import org.apache.camel.Endpoint
import org.moqui.impl.context.ExecutionContextFactoryImpl

class MoquiServiceComponent extends DefaultComponent {

    protected ExecutionContextFactoryImpl ecfi

    MoquiServiceComponent(ExecutionContextFactoryImpl ecfi) {
        super()
        this.ecfi = ecfi
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MoquiServiceEndpoint endpoint = new MoquiServiceEndpoint(uri, this, remaining)
        return endpoint
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }
}
