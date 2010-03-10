/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moqui.context;

import java.sql.Timestamp;
import java.util.Locale;
import java.util.TimeZone;

/** For information about the user and user preferences (including locale, time zone, currency, etc). */
public interface UserFacade {
    /** @return Locale The active Locale from user preference or system default. */
    Locale getLocale();

    /** @return TimeZone The active TimeZone from user preference or system default. */
    TimeZone getTimeZone();

    /** @return String The active ISO currency code from user preference or system default. */
    String getCurrencyUomId();

    /** Get the current date and time in a Timestamp object. This is either the current system time, or the Effective
     * Time if that has been set for this context (allowing testing of past and future system behavior).
     *
     * All internal tools and code built on the framework should treat this as the actual current time.
     */
    Timestamp getNowTimestamp();

    /** Set an EffectiveTime for the current context which will then be returned from the getNowTimestamp() method.
     * This is used to test past and future behavior of applications.
     * Pass in null to reset to the default of the current system time.
     */
    void setEffectiveTime(Timestamp effectiveTime);

    /** ID of the current active user (from the UserAccount entity) */
    String getUserId();

    /** ID of the user associated with the visit. May be different from the active user ID if a service or something is run explicitly as another user. */
    String getVisitUserId();

    /** ID for the current visit (aka session; from the Visit entity). Depending on the artifact being executed this may be null. */
    String getVisitId();
}
