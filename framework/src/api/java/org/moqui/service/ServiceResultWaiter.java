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
package org.moqui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ServiceFacade Result Waiter Class
 */
public class ServiceResultWaiter implements ServiceResultReceiver {

    protected final static Logger logger = LoggerFactory.getLogger(ServiceResultWaiter.class);

    /** Status code for a running service */
    public static final int SERVICE_RUNNING = -1;
    /** Status code for a failed service */
    public static final int SERVICE_FAILED = 0;
    /** Status code for a successful service */
    public static final int SERVICE_FINISHED = 1;

    private Map<String, Object> result = null;
    private Throwable t = null;
    private Lock stateLock = new ReentrantLock();

    /**
     * @see ServiceResultReceiver#receiveResult(java.util.Map)
     */
    public synchronized void receiveResult(Map<String, Object> result) {
        stateLock.lock();
        this.result = result;
        stateLock.unlock();
        notify();
    }

    /**
     * @see ServiceResultReceiver#receiveThrowable(java.lang.Throwable)
     */
    public synchronized void receiveThrowable(Throwable t) {
        stateLock.lock();
        this.t = t;
        stateLock.unlock();
        notify();
    }

    /**
     * Returns the status of the service.
     * @return int Status code
     */
    public int status() {
        stateLock.lock();
        try {
            if (this.result != null) return SERVICE_FINISHED;
            if (this.t != null) return SERVICE_FAILED;
            return SERVICE_RUNNING;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * If the service has completed return true
     * @return boolean
     */
    public boolean isCompleted() {
        stateLock.lock();
        try {
            return this.result != null || this.t != null;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the exception which was thrown or null if none
     * @return Exception
     */
    public Throwable getThrowable() {
        stateLock.lock();
        try {
            if (!isCompleted()) {
                throw new java.lang.IllegalStateException("Cannot return exception, service has not completed.");
            }
            return this.t;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Gets the results of the service or null if none
     * @return Map
     */
    public Map<String, Object> getResult() {
        stateLock.lock();
        try {
            if (!isCompleted()) {
                throw new java.lang.IllegalStateException("Cannot return result, service has not completed.");
            }
            return result;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Waits for the service to complete
     * @return Map
     */
    public Map<String, Object> waitForResult() { return this.waitForResult(10); }

    /**
     * Waits for the service to complete, check the status every n milliseconds
     * @param milliseconds Time in milliseconds to wait
     * @return Map
     */
    public synchronized Map<String, Object> waitForResult(long milliseconds) {
        while (!isCompleted()) {
            try {
                this.wait(milliseconds);
            } catch (java.lang.InterruptedException e) {
                logger.error("Error while waiting for result of async call to service", e);
            }
        }
        return this.getResult();
    }
}

