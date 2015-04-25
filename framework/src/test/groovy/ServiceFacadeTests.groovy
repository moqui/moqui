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


import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.service.ServiceCallback
import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.Moqui

class ServiceFacadeTests extends Specification {
    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "register callback concurrently"() {
        def sfi = (ServiceFacadeImpl)ec.service
        ServiceCallback scb = Mock(ServiceCallback)

        when:
        ConcurrentExecution.executeConcurrently(10, { sfi.registerCallback("foo", scb) })
        sfi.callRegisteredCallbacks("foo", null, null)

        then:
        10 * scb.receiveEvent(null, null)
    }
}
