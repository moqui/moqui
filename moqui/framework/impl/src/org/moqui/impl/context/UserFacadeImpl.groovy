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

import org.moqui.context.UserFacade
import java.sql.Timestamp

class UserFacadeImpl implements UserFacade {

    /** @see org.moqui.context.UserFacade#getLocale() */
    Locale getLocale() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#getTimeZone() */
    TimeZone getTimeZone() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#getCurrencyUomId() */
    String getCurrencyUomId() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#getNowTimestamp() */
    Timestamp getNowTimestamp() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#setEffectiveTime(Timestamp) */
    void setEffectiveTime(Timestamp effectiveTime) {
        // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#getUserId() */
    String getUserId() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#getVisitUserId() */
    String getVisitUserId() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.UserFacade#getVisitId() */
    String getVisitId() {
        return null;  // TODO: implement this
    }
}
