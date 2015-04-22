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

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class ConcurrentExecution {

    def static executeConcurrently(int threads, Closure closure) {
        ExecutorService executor = Executors.newFixedThreadPool(threads)
        CyclicBarrier barrier = new CyclicBarrier(threads)

        def futures = []
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(new Callable() {
                public def call() throws Exception {
                    barrier.await()
                    closure.call()
                }
            }))
        }

        def values = []
        for (Future future: futures) {
            try {
                def value = future.get()
                values << value
            } catch (ExecutionException e) {
                values << e.cause
            }
        }

        return values
    }

}
