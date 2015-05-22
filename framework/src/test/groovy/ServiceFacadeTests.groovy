/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
