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


public class LoggerFacadeImpl implements LoggerFacade {
    protected final static Logger logger = LoggerFactory.getLogger(LoggerFacadeImpl.class);

    protected final ExecutionContextFactoryImpl ecfi;

    public LoggerFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

    public void log(String levelStr, String message, Throwable thrown) {
        int level = OFF_INT
        switch (levelStr) {
            case "trace": level = TRACE_INT; break;
            case "debug": level = DEBUG_INT; break;
            case "info": level = INFO_INT; break;
            case "warn": level = WARN_INT; break;
            case "error": level = ERROR_INT; break;
            case "off": // do nothing
            default: return;
        }
        log(level, message, thrown)
    }

    /** @see org.moqui.context.LoggerFacade#log(int, String, Throwable) */
    public void log(int level, String message, Throwable thrown) {
        switch (level) {
            case TRACE_INT:
            logger.trace(message, thrown);
            break;

            case DEBUG_INT:
            logger.debug(message, thrown);
            break;

            case INFO_INT:
            logger.info(message, thrown);
            break;

            case WARN_INT:
            logger.warn(message, thrown);
            break;

            case ERROR_INT:
            logger.error(message, thrown);
            break;

            case FATAL_INT:
            throw new IllegalArgumentException("Fatal log level not supported by SLF4J.");
            break;

            case ALL_INT:
            throw new IllegalArgumentException("All log level not supported by SLF4J.");
            break;

            case OFF_INT:
            // do nothing
            break;
        }
    }

    void trace(String message) { log(TRACE_INT, message, null) }
    void debug(String message) { log(DEBUG_INT, message, null) }
    void info(String message) { log(INFO_INT, message, null) }
    void warn(String message) { log(WARN_INT, message, null) }
    void error(String message) { log(ERROR_INT, message, null) }

    /** @see org.moqui.context.LoggerFacade#logEnabled(int) */
    public boolean logEnabled(int level) {
        switch (level) {
            case TRACE_INT:
            return logger.isTraceEnabled();
            break;

            case DEBUG_INT:
            return logger.isDebugEnabled();
            break;

            case INFO_INT:
            return logger.isInfoEnabled();
            break;

            case WARN_INT:
            return logger.isWarnEnabled();
            break;

            case ERROR_INT:
            return logger.isErrorEnabled();
            break;

            case FATAL_INT:
            throw new IllegalArgumentException("Fatal log level not supported by SLF4J.");
            return true;
            break;

            case ALL_INT:
            throw new IllegalArgumentException("All log level not supported by SLF4J.");
            break;

            case OFF_INT:
            return false;
            break;

            default:
            return false;
        }
    }
}
