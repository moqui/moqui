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

import java.sql.Timestamp
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Cookie

import org.moqui.context.UserFacade
import org.moqui.entity.EntityValue
import org.moqui.impl.StupidUtilities

class UserFacadeImpl implements UserFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserFacadeImpl.class)

    protected ExecutionContextImpl eci
    protected Timestamp effectiveTime = null

    // just keep the userId, always get the UserAccount value from the entity cache
    protected Deque<String> userIdStack = new LinkedList()

    // there may be non-web visits, so keep a copy of the visitId here
    protected String visitId = null

    // we mostly want this for the Locale default, and may be useful for other things
    protected HttpServletRequest request = null

    UserFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    void initFromHttpRequest(HttpServletRequest request, HttpServletResponse response) {
        this.request = request
        if (request.session.getAttribute("moqui.userId")) {
            // effectively login the user
            String userId = (String) request.session.getAttribute("moqui.userId")
            // better not to do this, if there was a user before this init leave it for history/debug: if (this.userIdStack) this.userIdStack.pop()
            if (this.userIdStack.size() == 0 || this.userIdStack.peekFirst() != userId) this.userIdStack.addFirst(userId)
            if (logger.traceEnabled) logger.trace("For new request found moqui.userId [${userId}] in the session; userIdStack is [${this.userIdStack}]")
        } else {
            if (logger.traceEnabled) logger.trace("For new request NO moqui.userId in the session; userIdStack is [${this.userIdStack}]")
        }
        if (request.session.getAttribute("moqui.visitId")) {
            this.visitId = (String) request.session.getAttribute("moqui.visitId")

            // handle visitorId and cookie
            String cookieVisitorId = null
            if (eci.ecfi.confXmlRoot."server-stats"[0]."@visitor-enabled" != "false") {
                Cookie[] cookies = request.getCookies()
                if (cookies != null) {
                    for (int i = 0; i < cookies.length; i++) {
                        if (cookies[i].getName().equals("moqui.visitor")) {
                            cookieVisitorId = cookies[i].getValue()
                            break
                        }
                    }
                }
                if (!cookieVisitorId) {
                    Map cvResult = eci.service.sync().name("create", "Visitor").parameter("createdDate", getNowTimestamp()).call()
                    cookieVisitorId = cvResult.visitorId
                    logger.info("Created new visitor with ID [${cookieVisitorId}] in visit [${this.visitId}]")
                }
                // whether it existed or not, add it again to keep it fresh; stale cookies get thrown away
                Cookie visitorCookie = new Cookie("moqui.visitor", cookieVisitorId)
                visitorCookie.setMaxAge(60 * 60 * 24 * 365)
                visitorCookie.setPath("/")
                response.addCookie(visitorCookie)
            }

            EntityValue visit = getVisit()
            if (!visit?.initialLocale) {
                String fullUrl = eci.web.requestUrl
                fullUrl = (fullUrl.length() > 255) ? fullUrl.substring(0, 255) : fullUrl.toString()

                Map<String, Object> uvParms = (Map<String, Object>) [visitId:visit.visitId, initialLocale:getLocale().toString(),
                            initialRequest:fullUrl, initialReferrer:request.getHeader("Referrer")?:"",
                            initialUserAgent:request.getHeader("User-Agent")?:"",
                            clientHostName:request.getRemoteHost(), clientUser:request.getRemoteUser()]
                // handle proxy original address, if exists
                if (request.getHeader("X-Forwarded-For")) {
                    uvParms.clientIpAddress = request.getHeader("X-Forwarded-For")
                } else {
                    uvParms.clientIpAddress = request.getRemoteAddr()
                }
                if (cookieVisitorId) uvParms.visitorId = cookieVisitorId

                // NOTE: disable authz for this call, don't normally want to allow update of Visit, but this is special case
                eci.artifactExecution.disableAuthz()
                try {
                // called this sync so it is ready next time referred to, like on next request
                eci.service.sync().name("update", "Visit").parameters(uvParms).call()
                } finally {
                    eci.artifactExecution.enableAuthz()
                }

                // consider this the first hit in the visit, so trigger the actions
                eci.web.runFirstHitInVisitActions()
            }
        }
    }

    /** @see org.moqui.context.UserFacade#getLocale() */
    Locale getLocale() {
        Locale locale = null
        if (this.userId) {
            String localeStr = this.userAccount.locale
            if (localeStr) locale = new Locale(localeStr)
        }
        return (locale ?: (request ? request.getLocale() : Locale.getDefault()))
    }

    /** @see org.moqui.context.UserFacade#setLocale(Locale) */
    void setLocale(Locale locale) {
        if (this.userId) {
            eci.service.sync().name("update", "UserAccount")
                    .parameters((Map<String, Object>) [userId:getUserId(), locale:locale.toString()]).call()
        } else {
            throw new IllegalStateException("No user logged in, can't set Locale")
        }
    }

    /** @see org.moqui.context.UserFacade#getTimeZone() */
    TimeZone getTimeZone() {
        TimeZone tz = null
        if (this.userId) {
            String tzStr = this.userAccount.timeZone
            if (tzStr) tz = TimeZone.getTimeZone(tzStr)
        }
        return tz ?: TimeZone.getDefault()
    }

    /** @see org.moqui.context.UserFacade#setTimeZone(TimeZone) */
    void setTimeZone(TimeZone tz) {
        if (this.userId) {
            eci.service.sync().name("update", "UserAccount")
                    .parameters((Map<String, Object>) [userId:getUserId(), timeZone:tz.getID()]).call()
        } else {
            throw new IllegalStateException("No user logged in, can't set Time Zone")
        }
    }

    /** @see org.moqui.context.UserFacade#getCurrencyUomId() */
    String getCurrencyUomId() { return this.userId ? this.userAccount.currencyUomId : null }

    /** @see org.moqui.context.UserFacade#setCurrencyUomId(String) */
    void setCurrencyUomId(String uomId) {
        if (this.userId) {
            eci.service.sync().name("update", "UserAccount")
                    .parameters((Map<String, Object>) [userId:getUserId(), currencyUomId:uomId]).call()
        } else {
            throw new IllegalStateException("No user logged in, can't set Currency")
        }
    }

    String getPreference(String preferenceTypeEnumId) {
        EntityValue up = eci.entity.makeFind("UserPreference").condition("userId", getUserId())
                .condition("preferenceTypeEnumId", preferenceTypeEnumId).useCache(true).one()
        return up ? up.userPrefValue : null
    }

    void setPreference(String preferenceTypeEnumId, String userPrefValue) {
        eci.entity.makeValue("UserPreference").set("userId", getUserId())
                .set("preferenceTypeEnumId", preferenceTypeEnumId).createOrUpdate()
    }

    /** @see org.moqui.context.UserFacade#getNowTimestamp() */
    Timestamp getNowTimestamp() {
        // NOTE: review Timestamp and nowTimestamp use, have things use this by default (except audit/etc where actual date/time is needed
        return this.effectiveTime ? this.effectiveTime : new Timestamp(System.currentTimeMillis())
    }

    /** @see org.moqui.context.UserFacade#setEffectiveTime(Timestamp) */
    void setEffectiveTime(Timestamp effectiveTime) { this.effectiveTime = effectiveTime }

    boolean loginUser(String username, String password, String tenantId) {
        boolean successful = false

        if (tenantId) {
            eci.changeTenant(tenantId)
            this.visitId = null
            if (this.eci.web != null) this.eci.web.session.removeAttribute("moqui.visitId")
        }

        String userId = null
        if (authenticateUser(username, password)) {
            successful = true

            // NOTE: special case, for this thread only and for the section of code below need to turn off artifact
            //     authz since normally the user above would have authorized with something higher up, but that can't
            //     be done at this point
            eci.artifactExecution.disableAuthz()
            try {
                EntityValue newUserAccount = eci.entity.makeFind("UserAccount").condition("username", username)
                        .useCache(true).one()
                userId = newUserAccount.userId

                // do this first so that the rest will be done as this user
                // just in case there is already a user authenticated push onto a stack to remember
                this.userIdStack.addFirst(userId)

                // no more auth failures? record the various account state updates, hasLoggedOut=N
                Map<String, Object> uaParameters = (Map<String, Object>) [userId:userId, successiveFailedLogins:0,
                        disabled:"N", disabledDateTime:null, hasLoggedOut:"N"]
                eci.service.sync().name("update", "UserAccount").parameters(uaParameters).call()

                // update visit if no user in visit yet
                EntityValue visit = getVisit()
                if (visit && !visit.userId) {
                    eci.service.sync().name("update", "Visit")
                            .parameters((Map<String, Object>) [visitId:getVisitId(), userId:userId]).call()
                }
            } finally {
                eci.artifactExecution.enableAuthz()
            }

            // if WebExecutionContext add to session
            if (eci.ecfi.executionContext.web) {
                eci.ecfi.executionContext.web.session.setAttribute("moqui.userId", userId)
            }
        }

        Node loginNode = eci.ecfi.confXmlRoot."user-facade"[0]."login"[0]

        // track the UserLoginHistory
        if (userId != null && loginNode."@history-store" != "false") {
            Map<String, Object> ulhContext =
                    (Map<String, Object>) [userId:userId, visitId:getVisitId(), successfulLogin:(successful?"Y":"N")]
            if (!successful && loginNode."@history-incorrect-password" != "false") ulhContext.passwordUsed = password
            eci.service.sync().name("create", "UserLoginHistory").parameters(ulhContext).call()
        }

        if (successful && eci.web) {
            // after successful login trigger the after-login actions
            eci.web.runAfterLoginActions()
        }

        return successful
    }

    /** @see org.moqui.context.UserFacade#authenticateUser(String, String) */
    boolean authenticateUser(String username, String password) {
        EntityValue newUserAccount = eci.entity.makeFind("UserAccount").condition("username", username).useCache(true).one()
        if (!newUserAccount) {
            eci.message.addError("Login failed. Username [${username}] and/or password incorrect.")
            logger.warn("Login failure: ${eci.message.errors}")
            return false
        }

        // check encrypted/hashed password
        String passedInHash = StupidUtilities.getHashDigest(password,
                StupidUtilities.getHashSaltFromFull((String) newUserAccount.currentPassword),
                StupidUtilities.getHashTypeFromFull((String) newUserAccount.currentPassword))
        // just compare the hash part of the full string
        if (StupidUtilities.getHashHashFromFull(passedInHash) !=
                StupidUtilities.getHashHashFromFull((String) newUserAccount.currentPassword)) {
            // only if failed on password, increment in new transaction to make sure it sticks
            eci.service.sync().name("org.moqui.impl.UserServices.incrementUserAccountFailedLogins")
                    .parameters((Map<String, Object>) [userId:newUserAccount.userId]).requireNewTransaction(true).call()
            eci.message.addError("Login failed. Username [${username}] and/or password incorrect.")
            logger.warn("Login failure: ${eci.message.errors}")
            return false
        }

        if (newUserAccount.requirePasswordChange == "Y") {
            eci.message.addError("Authenticate failed for user [${username}] because account requires password change [PWDCHG].")
            logger.warn("Login failure: ${eci.message.errors}")
            return false
        }
        if (newUserAccount.disabled == "Y") {
            Timestamp reEnableTime = null
            if (newUserAccount.disabledDateTime) {
                Integer disabledMinutes = eci.ecfi.confXmlRoot."user-facade"[0]."login"[0]."@disable-minutes" as Integer ?: 30
                reEnableTime = new Timestamp(newUserAccount.getTimestamp("disabledDateTime").getTime() + (disabledMinutes*60*1000))
            }
            if (!reEnableTime || reEnableTime < getNowTimestamp()) {
                eci.message.addError("Authenticate failed for user [${username}] because account is disabled and will not be re-enabled until [${reEnableTime}] [ACTDIS].")
                logger.warn("Login failure: ${eci.message.errors}")
                return false
            }
        }

        // check time since password was last changed, if it has been too long (user-facade.password.@change-weeks default 12) then fail
        if (newUserAccount.passwordSetDate) {
            int changeWeeks = (eci.ecfi.confXmlRoot."user-facade"[0]."password"[0]."@change-weeks" ?: 12) as int
            int wksSinceChange = (eci.user.nowTimestamp.time - newUserAccount.passwordSetDate.time) / (7*24*60*60*1000)
            if (wksSinceChange > changeWeeks) {
                eci.message.addError("Authenticate failed for user [${username}] because password was changed [${wksSinceChange}] weeks ago and should be changed every [${changeWeeks}] weeks [PWDTIM].")
                logger.warn("Login failure: ${eci.message.errors}")
                return false
            }
        }

        return true
    }

    void logoutUser() {
        // before logout trigger the before-logout actions
        if (eci.web) eci.web.runBeforeLogoutActions()

        if (userIdStack) userIdStack.removeFirst()

        if (eci.web) {
            eci.web.session.removeAttribute("moqui.userId")
            eci.web.session.removeAttribute("moqui.tenantId")
            eci.web.session.removeAttribute("moqui.visitId")
        }
    }

    /* @see org.moqui.context.UserFacade#getUserId() */
    String getUserId() {
        return this.userIdStack ? this.userIdStack.peekFirst() : null
    }

    /* @see org.moqui.context.UserFacade#getUserAccount() */
    EntityValue getUserAccount() {
        if (!userIdStack) {
            // logger.info("Getting UserAccount no userIdStack", new Exception("Trace"))
            return null
        }
        EntityValue ua = eci.entity.makeFind("UserAccount").condition("userId", userIdStack.peekFirst()).useCache(true).one()
        // logger.info("Got UserAccount [${ua}] with userIdStack [${userIdStack}]")
        return ua
    }

    /** @see org.moqui.context.UserFacade#getVisitUserId() */
    String getVisitUserId() { return visitId ? getVisit().userId : null }

    /** @see org.moqui.context.UserFacade#getVisitId() */
    String getVisitId() { return visitId }

    /** @see org.moqui.context.UserFacade#getVisit() */
    EntityValue getVisit() {
        if (!visitId) return null
        return eci.entity.makeFind("Visit").condition("visitId", visitId).useCache(true).one()
    }
}
