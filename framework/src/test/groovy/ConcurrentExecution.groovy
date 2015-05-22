/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
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
