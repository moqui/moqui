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
import org.moqui.context.ExecutionContext
import org.moqui.context.WebExecutionContext
import org.moqui.impl.StupidUtilities

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
        if (session.getAttribute("moqui.user")) {
            this.userAccount = (EntityValue) session.getAttribute("moqui.user")
        } else {
            // TODO if no userAccount get better defaults from webapp for locale, time zone?
        }
        if (session.getAttribute("moqui.visit")) {
            this.visit = (EntityValue) session.getAttribute("moqui.visit")
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

    boolean loginUser(String userId, String password) {
        boolean successful = false
        if (authenticateUser(userId, password)) {
            successful = true

            EntityValue newUserAccount = eci.entity.makeFind("UserAccount").condition("userId", userId).useCache(true).one()

            // TODO: if there is already a user authenticated, what to do? push onto a stack to remember?


            // TODO if hasLoggedOut==Y set hasLoggedOut=N

            // update visit if no user in visit yet
            if (this.visit && !this.visit.userId) {
                this.visit.userId = userId
                this.visit.update()
            }

            // if WebExecutionContext add to session
            if (eci.ecfi.getExecutionContext() instanceof WebExecutionContext) {
                WebExecutionContext wec = (WebExecutionContext) eci.ecfi.getExecutionContext()
                wec.getSession().setAttribute("moqui.user", newUserAccount)
            }

            this.userAccount = newUserAccount
        }
        /* TODO: on success or failure, save history:
        <entity entity-name="UserLoginHistory" package-name="org.moqui.security.user" cache="never">
            <field name="userId" type="id-vlong" is-pk="true"/>
            <field name="fromDate" type="date-time" is-pk="true"/>
            <field name="thruDate" type="date-time"/>
            <field name="visitId" type="id"/>
            <field name="passwordUsed" type="text-medium" encrypt="true"/>
            <field name="successfulLogin" type="text-indicator"/>
            <relationship type="one-nofk" related-entity-name="UserAccount">
                <description>No FK in order to allow externally authenticated users.</description>
            </relationship>
        </entity>
         */

        return successful
    }

    /** @see org.moqui.context.UserFacade#authenticateUser(String, String) */
    boolean authenticateUser(String userId, String password) {
        EntityValue newUserAccount = eci.entity.makeFind("UserAccount").condition("userId", userId).useCache(true).one()
        if (!newUserAccount) return false

        // check encrypted/hashed password
        String passedInHash = StupidUtilities.getHashDigest(password,
                StupidUtilities.getHashSaltFromFull((String) newUserAccount.currentPassword),
                StupidUtilities.getHashTypeFromFull((String) newUserAccount.currentPassword))
        // just compare the hash part of the full string
        if (StupidUtilities.getHashHashFromFull(passedInHash) != StupidUtilities.getHashHashFromFull((String) newUserAccount.currentPassword)) {
            /* TODO if failed on password only, increment
             * if over configured amount, set disabled and disabledDateTime
            <field name="successiveFailedLogins" type="number-integer"/>
             */
            return false
        }

        /* TODO check and fail on:
        <field name="disabled" type="text-indicator"/>
        <field name="disabledDateTime" type="date-time"/>
        <field name="requirePasswordChange" type="text-indicator"/>
         */

        return true
    }

    void logoutUser() {

    }

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
