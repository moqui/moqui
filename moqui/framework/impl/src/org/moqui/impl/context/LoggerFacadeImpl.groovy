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
package org.moqui.impl.context

import org.moqui.context.LoggerFacade
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.log4j.Level

public class LoggerFacadeImpl implements LoggerFacade {

    protected final static Logger logger = LoggerFactory.getLogger(LoggerFacadeImpl.class);

    protected final ExecutionContextFactoryImpl ecfi;

    public LoggerFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

    /** @see org.moqui.context.LoggerFacade#log(int, String, Throwable) */
    public void log(int level, String message, Throwable thrown) {
        switch (level) {
            case Level.TRACE_INT:
            logger.trace(message, thrown);
            break;

            case Level.DEBUG_INT:
            logger.debug(message, thrown);
            break;

            case Level.INFO_INT:
            logger.info(message, thrown);
            break;

            case Level.WARN_INT:
            logger.warn(message, thrown);
            break;

            case Level.ERROR_INT:
            logger.error(message, thrown);
            break;

            case Level.FATAL_INT:
            throw new IllegalArgumentException("Fatal log level not supported by SLF4J.");
            break;

            case Level.ALL_INT:
            throw new IllegalArgumentException("All log level not supported by SLF4J.");
            break;

            case Level.OFF_INT:
            // do nothing
            break;
        }
    }

    /** @see org.moqui.context.LoggerFacade#logEnabled(int) */
    public boolean logEnabled(int level) {
        switch (level) {
            case Level.TRACE_INT:
            return logger.isTraceEnabled();
            break;

            case Level.DEBUG_INT:
            return logger.isDebugEnabled();
            break;

            case Level.INFO_INT:
            return logger.isInfoEnabled();
            break;

            case Level.WARN_INT:
            return logger.isWarnEnabled();
            break;

            case Level.ERROR_INT:
            return logger.isErrorEnabled();
            break;

            case Level.FATAL_INT:
            throw new IllegalArgumentException("Fatal log level not supported by SLF4J.");
            return true;
            break;

            case Level.ALL_INT:
            throw new IllegalArgumentException("All log level not supported by SLF4J.");
            break;

            case Level.OFF_INT:
            return false;
            break;

            default:
            return false;
        }
    }
}
