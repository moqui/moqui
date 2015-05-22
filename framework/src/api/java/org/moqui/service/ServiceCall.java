/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.service;

import java.util.Map;

public interface ServiceCall {
    public enum TimeUnit { SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

    String getServiceName();

    /** Map of name, value pairs that make up the context (in parameters) passed to the service. */
    Map<String, Object> getCurrentParameters();
}
