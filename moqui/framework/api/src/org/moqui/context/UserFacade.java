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
package org.moqui.context;

import org.moqui.entity.EntityValue;

import java.sql.Timestamp;
import java.util.Locale;
import java.util.TimeZone;

/** For information about the user and user preferences (including locale, time zone, currency, etc). */
public interface UserFacade {
    /** @return Locale The active Locale from user preference or system default. */
    Locale getLocale();

    /** Set the user's Locale. This is used in this context and saved to the database for future contexts. */
    void setLocale(Locale locale);

    /** @return TimeZone The active TimeZone from user preference or system default. */
    TimeZone getTimeZone();

    /** Set the user's Time Zone. This is used in this context and saved to the database for future contexts. */
    void setTimeZone(TimeZone tz);

    /** @return String The active ISO currency code from user preference or system default. */
    String getCurrencyUomId();

    /** Set the user's Time Zone. This is used in this context and saved to the database for future contexts. */
    void setCurrencyUomId(String uomId);

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

    /** Authenticate a user and make active in this ExecutionContext (and session of WebExecutionContext if applicable).
     * @return true if user was logged in, otherwise false
     */
    boolean loginUser(String userId, String password);

    /** Only authenticate the user, do not make active in current context.
     * @return true if user was authenticated successfully, otherwise false
     */
    boolean authenticateUser(String userId, String password);

    /** Remove (logout) active user. */
    void logoutUser();

    /** ID of the current active user (from the UserAccount entity) */
    String getUserId();

    /** Value for the current active user (the UserAccount entity) */
    EntityValue getUserAccount();

    /** ID of the user associated with the visit. May be different from the active user ID if a service or something is run explicitly as another user. */
    String getVisitUserId();

    /** ID for the current visit (aka session; from the Visit entity). Depending on the artifact being executed this may be null. */
    String getVisitId();

    /** The current visit (aka session; from the Visit entity). Depending on the artifact being executed this may be null. */
    EntityValue getVisit();
}
