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
