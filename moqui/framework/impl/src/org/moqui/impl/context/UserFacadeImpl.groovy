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
import org.moqui.entity.EntityValue
import javax.servlet.http.HttpSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class UserFacadeImpl implements UserFacade {
    protected final static Logger logger = LoggerFactory.getLogger(UserFacadeImpl.class)

    protected ExecutionContextImpl eci

    protected Timestamp effectiveTime = null

    protected EntityValue userAccount = null
    protected EntityValue visit = null

    UserFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    void initFromHttpSession(HttpSession session) {
        if (session.getAttribute("userAccount")) {
            this.userAccount = (EntityValue) session.getAttribute("userAccount")
        } else {

        // TODO if no userAccount get better defaults from webapp for locale, time zone?
        }
        if (session.getAttribute("visit")) {
            this.visit = (EntityValue) session.getAttribute("visit")
        } else {
            // this should ALWAYS be there, if not warn
            logger.warn("In UserFacade init no visit was found in session [${session.getId()}]")
        }
    }

    /** @see org.moqui.context.UserFacade#getLocale() */
    Locale getLocale() {
        return this.userAccount ? new Locale((String) this.userAccount.locale) : Locale.getDefault()
    }

    /** @see org.moqui.context.UserFacade#setLocale(Locale) */
    void setLocale(Locale locale) {
        if (this.userAccount) {
            // TODO: change this to a service to tx mgmt, etc
            this.userAccount.refresh()
            this.userAccount.locale = locale.toString()
            this.userAccount.update()
        } else {
            throw new IllegalStateException("No user logged in, can't set Locale")
        }
    }

    /** @see org.moqui.context.UserFacade#getTimeZone() */
    TimeZone getTimeZone() {
        return this.userAccount ? TimeZone.getTimeZone((String) this.userAccount.timeZone) : TimeZone.getDefault()
    }

    /** @see org.moqui.context.UserFacade#setTimeZone(TimeZone) */
    void setTimeZone(TimeZone tz) {
        if (this.userAccount) {
            // TODO: change this to a service to tx mgmt, etc
            this.userAccount.refresh()
            this.userAccount.timeZone = tz.getID()
            this.userAccount.update()
        } else {
            throw new IllegalStateException("No user logged in, can't set Time Zone")
        }
    }

    /** @see org.moqui.context.UserFacade#getCurrencyUomId() */
    String getCurrencyUomId() { return this.userAccount ? this.userAccount.currencyUomId : null }

    /** @see org.moqui.context.UserFacade#setCurrencyUomId(String) */
    void setCurrencyUomId(String uomId) {
        if (this.userAccount) {
            // TODO: change this to a service to tx mgmt, etc
            this.userAccount.refresh()
            this.userAccount.currencyUomId = uomId
            this.userAccount.update()
        } else {
            throw new IllegalStateException("No user logged in, can't set Currency")
        }
    }

    /** @see org.moqui.context.UserFacade#getNowTimestamp() */
    Timestamp getNowTimestamp() {
        // TODO: review Timestamp use, have things use this by default
        return this.effectiveTime ? this.effectiveTime : new Timestamp(System.currentTimeMillis())
    }

    /** @see org.moqui.context.UserFacade#setEffectiveTime(Timestamp) */
    void setEffectiveTime(Timestamp effectiveTime) { this.effectiveTime = effectiveTime }

    /** @see org.moqui.context.UserFacade#getUserId() */
    String getUserId() { return this.userAccount ? this.userAccount.userId : null }

    /** @see org.moqui.context.UserFacade#getUserAccount() */
    EntityValue getUserAccount() { return this.userAccount }

    /** @see org.moqui.context.UserFacade#getVisitUserId() */
    String getVisitUserId() { return this.visit ? this.visit.userId : null }

    /** @see org.moqui.context.UserFacade#getVisitId() */
    String getVisitId() { return this.visit ? this.visit.visitId : null }

    /** @see org.moqui.context.UserFacade#getVisit() */
    EntityValue getVisit() { return this.visit }
}
