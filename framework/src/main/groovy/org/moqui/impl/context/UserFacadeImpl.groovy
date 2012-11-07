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
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

import org.apache.shiro.authc.AuthenticationException
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.subject.Subject
import org.apache.shiro.web.subject.WebSubjectContext
import org.apache.shiro.web.subject.support.DefaultWebSubjectContext
import org.apache.shiro.web.session.HttpServletSession

import org.moqui.context.UserFacade
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.moqui.impl.entity.EntityListImpl

class UserFacadeImpl implements UserFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserFacadeImpl.class)
    protected final static Set<String> allUserGroupIdOnly = new HashSet(["ALL_USERS"])

    protected ExecutionContextImpl eci
    protected Timestamp effectiveTime = null

    protected Deque<String> usernameStack = new LinkedList()
    // keep a reference to a UserAccount for performance reasons, avoid repeated cached queries
    protected EntityValue internalUserAccount = null
    protected Set<String> internalUserGroupIdSet = null
    protected EntityList internalArtifactTarpitCheckList = null
    protected EntityList internalArtifactAuthzCheckList = null

    /** The Shiro Subject (user) */
    protected Subject currentUser = null

    // there may be non-web visits, so keep a copy of the visitId here
    protected String visitId = null

    // we mostly want this for the Locale default, and may be useful for other things
    protected HttpServletRequest request = null

    UserFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    void initFromHttpRequest(HttpServletRequest request, HttpServletResponse response) {
        this.request = request
        HttpSession session = request.getSession()

        WebSubjectContext wsc = new DefaultWebSubjectContext()
        wsc.setServletRequest(request); wsc.setServletResponse(response)
        wsc.setSession(new HttpServletSession(session, request.getServerName()))
        currentUser = eci.getEcfi().getSecurityManager().createSubject(wsc)

        if (currentUser.authenticated) {
            // effectively login the user
            String userId = (String) currentUser.principal
            // better not to do this, if there was a user before this init leave it for history/debug: if (this.userIdStack) this.userIdStack.pop()
            if (this.usernameStack.size() == 0 || this.usernameStack.peekFirst() != userId) {
                this.usernameStack.addFirst(userId)
                this.internalUserAccount = null
                this.internalUserGroupIdSet = null
                this.internalArtifactTarpitCheckList = null
                this.internalArtifactAuthzCheckList = null
            }
            if (logger.traceEnabled) logger.trace("For new request found user [${userId}] in the session; userIdStack is [${this.usernameStack}]")
        } else {
            if (logger.traceEnabled) logger.trace("For new request NO user authenticated in the session; userIdStack is [${this.usernameStack}]")
        }

        // check for HTTP Basic Authorization for Authentication purposes
        // NOTE: do this even if there is another user logged in, will go on stack
        String authzHeader = request.getHeader("Authorization")
        if (authzHeader && authzHeader.substring(0, 6).equals("Basic ")) {
            String basicAuthEncoded = authzHeader.substring(6).trim()
            String basicAuthAsString = new String(basicAuthEncoded.decodeBase64())
            if (basicAuthAsString.indexOf(":") > 0) {
                String username = basicAuthAsString.substring(0, basicAuthAsString.indexOf(":"))
                String password = basicAuthAsString.substring(basicAuthAsString.indexOf(":") + 1)
                this.loginUser(username, password, null)
            } else {
                logger.warn("For HTTP Basic Authorization got bad credentials string. Base64 encoded is [${basicAuthEncoded}] and after decoding is [${basicAuthAsString}].")
            }
        } else {
            // try the Moqui-specific parameters for instant login
            // if we have credentials coming in anywhere other than URL parameters, try logging in
            String authUsername = null
            String authPassword = null
            String authTenantId = null
            Map multiPartParameters = eci.webFacade.multiPartParameters
            Map jsonParameters = eci.webFacade.jsonParameters
            if (multiPartParameters && multiPartParameters.authUsername && multiPartParameters.authPassword) {
                authUsername = multiPartParameters.authUsername
                authPassword = multiPartParameters.authPassword
                authTenantId = multiPartParameters.authTenantId
            } else if (jsonParameters && jsonParameters.authUsername && jsonParameters.authPassword) {
                authUsername = jsonParameters.authUsername
                authPassword = jsonParameters.authPassword
                authTenantId = jsonParameters.authTenantId
            } else if (!request.getQueryString() && request.getParameter("authUsername") && request.getParameter("authPassword")) {
                authUsername = request.getParameter("authUsername")
                authPassword = request.getParameter("authPassword")
                authTenantId = request.getParameter("authTenantId")
            }
            if (authUsername) {
                this.loginUser(authUsername, authPassword, authTenantId)
            }
        }

        this.visitId = session.getAttribute("moqui.visitId")
        if (!this.visitId && !eci.getEcfi().getSkipStats()) {
            Node serverStatsNode = eci.getEcfi().getConfXmlRoot()."server-stats"[0]

            // handle visitorId and cookie
            String cookieVisitorId = null
            if (serverStatsNode."@visitor-enabled" != "false") {
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
                    // NOTE: disable authz for this call, don't normally want to allow create of Visitor, but this is a special case
                    boolean alreadyDisabled = eci.getArtifactExecution().disableAuthz()
                    try {
                        Map cvResult = eci.service.sync().name("create", "moqui.server.Visitor").parameter("createdDate", getNowTimestamp()).call()
                        cookieVisitorId = cvResult.visitorId
                        logger.info("Created new Visitor with ID [${cookieVisitorId}] in session [${session.id}]")
                    } finally {
                        if (!alreadyDisabled) eci.getArtifactExecution().enableAuthz()
                    }
                }
                // whether it existed or not, add it again to keep it fresh; stale cookies get thrown away
                Cookie visitorCookie = new Cookie("moqui.visitor", cookieVisitorId)
                visitorCookie.setMaxAge(60 * 60 * 24 * 365)
                visitorCookie.setPath("/")
                response.addCookie(visitorCookie)
            }

            if (serverStatsNode."@visit-enabled" != "false") {
                // create and persist Visit
                String contextPath = session.getServletContext().getContextPath()
                String webappId = contextPath.length() > 1 ? contextPath.substring(1) : "ROOT"
                String fullUrl = eci.web.requestUrl
                fullUrl = (fullUrl.length() > 255) ? fullUrl.substring(0, 255) : fullUrl.toString()
                Map parameters = [sessionId:session.id, webappName:webappId, fromDate:new Timestamp(session.getCreationTime()),
                        initialLocale:getLocale().toString(), initialRequest:fullUrl,
                        initialReferrer:request.getHeader("Referrer")?:"",
                        initialUserAgent:request.getHeader("User-Agent")?:"",
                        clientHostName:request.getRemoteHost(), clientUser:request.getRemoteUser()]
                InetAddress address = InetAddress.getLocalHost();
                if (address) {
                    parameters.serverIpAddress = address.getHostAddress()
                    parameters.serverHostName = address.getHostName()
                }

                // handle proxy original address, if exists
                if (request.getHeader("X-Forwarded-For")) {
                    parameters.clientIpAddress = request.getHeader("X-Forwarded-For")
                } else {
                    parameters.clientIpAddress = request.getRemoteAddr()
                }
                if (cookieVisitorId) parameters.visitorId = cookieVisitorId

                // NOTE: disable authz for this call, don't normally want to allow create of Visit, but this is special case
                boolean alreadyDisabled = eci.getArtifactExecution().disableAuthz()
                try {
                    Map result = eci.service.sync().name("create", "moqui.server.Visit").parameters(parameters).call()
                    // put visitId in session as "moqui.visitId"
                    session.setAttribute("moqui.visitId", result.visitId)
                    this.visitId = result.visitId
                    logger.info("Created new Visit with ID [${this.visitId}] in session [${session.id}]")
                } finally {
                    if (!alreadyDisabled) eci.getArtifactExecution().enableAuthz()
                }
            }
        }
    }

    /** @see org.moqui.context.UserFacade#getLocale() */
    Locale getLocale() {
        Locale locale = null
        if (this.username) {
            String localeStr = this.userAccount.locale
            if (localeStr) locale = new Locale(localeStr)
        }
        return (locale ?: (request ? request.getLocale() : Locale.getDefault()))
    }

    /** @see org.moqui.context.UserFacade#setLocale(Locale) */
    void setLocale(Locale locale) {
        if (this.username) {
            eci.service.sync().name("update", "moqui.security.UserAccount")
                    .parameters((Map<String, Object>) [userId:getUserId(), locale:locale.toString()]).call()
        } else {
            throw new IllegalStateException("No user logged in, can't set Locale")
        }
    }

    /** @see org.moqui.context.UserFacade#getTimeZone() */
    TimeZone getTimeZone() {
        TimeZone tz = null
        if (this.username) {
            String tzStr = this.userAccount.timeZone
            if (tzStr) tz = TimeZone.getTimeZone(tzStr)
        }
        return tz ?: TimeZone.getDefault()
    }

    /** @see org.moqui.context.UserFacade#setTimeZone(TimeZone) */
    void setTimeZone(TimeZone tz) {
        if (this.username) {
            eci.service.sync().name("update", "moqui.security.UserAccount")
                    .parameters((Map<String, Object>) [userId:getUserId(), timeZone:tz.getID()]).call()
        } else {
            throw new IllegalStateException("No user logged in, can't set Time Zone")
        }
    }

    /** @see org.moqui.context.UserFacade#getCurrencyUomId() */
    String getCurrencyUomId() { return this.username ? this.userAccount.currencyUomId : null }

    /** @see org.moqui.context.UserFacade#setCurrencyUomId(String) */
    void setCurrencyUomId(String uomId) {
        if (this.username) {
            eci.service.sync().name("update", "moqui.security.UserAccount")
                    .parameters((Map<String, Object>) [userId:getUserId(), currencyUomId:uomId]).call()
        } else {
            throw new IllegalStateException("No user logged in, can't set Currency")
        }
    }

    String getPreference(String preferenceKey) {
        EntityValue up = eci.getEntity().makeFind("moqui.security.UserPreference").condition("userId", getUserId())
                .condition("preferenceKey", preferenceKey).useCache(true).one()
        return up ? up.preferenceValue : null
    }

    void setPreference(String preferenceKey, String preferenceValue) {
        eci.getEntity().makeValue("moqui.security.UserPreference").set("userId", getUserId())
                .set("preferenceKey", preferenceKey).set("preferenceValue", preferenceValue).createOrUpdate()
    }

    /** @see org.moqui.context.UserFacade#getNowTimestamp() */
    Timestamp getNowTimestamp() {
        // NOTE: review Timestamp and nowTimestamp use, have things use this by default (except audit/etc where actual date/time is needed
        return this.effectiveTime ? this.effectiveTime : new Timestamp(System.currentTimeMillis())
    }

    /** @see org.moqui.context.UserFacade#setEffectiveTime(Timestamp) */
    void setEffectiveTime(Timestamp effectiveTime) { this.effectiveTime = effectiveTime }

    boolean loginUser(String username, String password, String tenantId) {
        if (tenantId) {
            eci.changeTenant(tenantId)
            this.visitId = null
            if (eci.web != null) eci.web.session.removeAttribute("moqui.visitId")
        }

        UsernamePasswordToken token = new UsernamePasswordToken(username, password)
        token.rememberMe = true
        try {
            currentUser.login(token)

            // do this first so that the rest will be done as this user
            // just in case there is already a user authenticated push onto a stack to remember
            usernameStack.addFirst(username)
            internalUserAccount = null
            internalUserGroupIdSet = null
            internalArtifactTarpitCheckList = null
            internalArtifactAuthzCheckList = null

            // after successful login trigger the after-login actions
            if (eci.web != null) eci.web.runAfterLoginActions()
        } catch (AuthenticationException ae) {
            // others to consider handling differently (these all inherit from AuthenticationException):
            //     UnknownAccountException, IncorrectCredentialsException, ExpiredCredentialsException,
            //     CredentialsException, LockedAccountException, DisabledAccountException, ExcessiveAttemptsException
            eci.message.addError(ae.message)
            logger.warn("Login failure: ${eci.message.errors}", ae)
            return false
        }

        return true
    }

    void logoutUser() {
        // before logout trigger the before-logout actions
        if (eci.web != null) eci.web.runBeforeLogoutActions()

        if (usernameStack) {
            usernameStack.removeFirst()
            internalUserAccount = null
            internalUserGroupIdSet = null
            internalArtifactTarpitCheckList = null
            internalArtifactAuthzCheckList = null
        }

        if (eci.web != null) {
            eci.web.session.removeAttribute("moqui.tenantId")
            eci.web.session.removeAttribute("moqui.visitId")
        }
        currentUser.logout()
    }

    boolean loginAnonymousIfNoUser() {
        if (usernameStack.size() == 0) {
            usernameStack.addFirst("_NA_")
            internalUserAccount = null
            internalUserGroupIdSet = null
            internalArtifactTarpitCheckList = null
            internalArtifactAuthzCheckList = null
            return true
        } else {
            return false
        }
    }

    void logoutAnonymousOnly() {
        if (usernameStack && usernameStack.getFirst() == "_NA_") {
            usernameStack.removeFirst()
            internalUserAccount = null
            internalUserGroupIdSet = null
            internalArtifactTarpitCheckList = null
            internalArtifactAuthzCheckList = null
        }
    }

    /* @see org.moqui.context.UserFacade#hasPermission(String) */
    boolean hasPermission(String userPermissionId) { return hasPermission(getUserId(), userPermissionId, getNowTimestamp(), eci) }

    static boolean hasPermission(String username, String userPermissionId, Timestamp nowTimestamp, ExecutionContextImpl eci) {
        if (nowTimestamp == null) nowTimestamp = new Timestamp(System.currentTimeMillis())
        EntityValue ua = eci.getEntity().makeFind("moqui.security.UserAccount").condition("userId", username).useCache(true).one()
        if (ua == null) ua = eci.getEntity().makeFind("moqui.security.UserAccount").condition("username", username).useCache(true).one()
        if (ua == null) return false
        return (eci.getEntity().makeFind("moqui.security.UserPermissionCheck").condition([userId:ua.userId, userPermissionId:userPermissionId])
                .useCache(true).list()
                .filterByDate("groupFromDate", "groupThruDate", nowTimestamp)
                .filterByDate("permissionFromDate", "permissionThruDate", nowTimestamp)) as boolean
    }

    /* @see org.moqui.context.UserFacade#isInGroup(String) */
    boolean isInGroup(String userGroupId) { return isInGroup(getUserId(), userGroupId, getNowTimestamp(), eci) }

    static boolean isInGroup(String username, String userGroupId, Timestamp nowTimestamp, ExecutionContextImpl eci) {
        if (nowTimestamp == null) nowTimestamp = new Timestamp(System.currentTimeMillis())
        EntityValue ua = eci.getEntity().makeFind("moqui.security.UserAccount").condition("userId", username).useCache(true).one()
        if (ua == null) ua = eci.getEntity().makeFind("moqui.security.UserAccount").condition("username", username).useCache(true).one()
        if (ua == null) return false
        return (eci.getEntity().makeFind("moqui.security.UserGroupMember").condition([userId:ua.userId, userGroupId:userGroupId])
                .useCache(true).list().filterByDate("fromDate", "thruDate", nowTimestamp)) as boolean
    }

    /* @see org.moqui.context.UserFacade#getUserGroupIdSet() */
    Set<String> getUserGroupIdSet() {
        // first get the groups the user is in (cached), always add the "ALL_USERS" group to it
        if (usernameStack.size() == 0) return allUserGroupIdOnly
        if (internalUserGroupIdSet == null) {
            internalUserGroupIdSet = new HashSet(allUserGroupIdOnly)
            boolean alreadyDisabled = eci.getArtifactExecution().disableAuthz()
            try {
                // expand the userGroupId Set with UserGroupMember
                for (EntityValue userGroupMember in eci.getEntity().makeFind("moqui.security.UserGroupMember")
                        .condition("userId", userId).useCache(true).list().filterByDate(null, null, null))
                    internalUserGroupIdSet.add((String) userGroupMember.userGroupId)
            } finally {
                if (!alreadyDisabled) eci.getArtifactExecution().enableAuthz()
            }
        }
        return internalUserGroupIdSet
    }

    EntityList getArtifactTarpitCheckList() {
        if (usernameStack.size() == 0) return EntityListImpl.EMPTY
        if (internalArtifactTarpitCheckList == null) {
            // get the list for each group separately to increase cache hits/efficiency
            internalArtifactTarpitCheckList = new EntityListImpl(eci.getEcfi().getEntityFacade())
            for (String userGroupId in getUserGroupIdSet()) {
                internalArtifactTarpitCheckList.addAll(eci.getEntity().makeFind("moqui.security.ArtifactTarpitCheckView")
                        .condition("userGroupId", userGroupId).useCache(true).list())
            }
        }
        return internalArtifactTarpitCheckList
    }

    EntityList getArtifactAuthzCheckList() {
        // NOTE: even if there is no user, still consider part of the ALL_USERS group and such: if (usernameStack.size() == 0) return EntityListImpl.EMPTY
        if (internalArtifactAuthzCheckList == null) {
            // get the list for each group separately to increase cache hits/efficiency
            internalArtifactAuthzCheckList = new EntityListImpl(eci.getEcfi().getEntityFacade())
            for (String userGroupId in getUserGroupIdSet()) {
                internalArtifactAuthzCheckList.addAll(eci.getEntity().makeFind("moqui.security.ArtifactAuthzCheckView")
                        .condition("userGroupId", userGroupId).useCache(true).list())
            }
        }
        return internalArtifactAuthzCheckList
    }

    /* @see org.moqui.context.UserFacade#getUsername() */
    String getUserId() { return getUserAccount()?.userId }

    /* @see org.moqui.context.UserFacade#getUsername() */
    String getUsername() { return this.usernameStack.size() > 0 ? this.usernameStack.peekFirst() : null }

    /* @see org.moqui.context.UserFacade#getUserAccount() */
    EntityValue getUserAccount() {
        if (this.usernameStack.size() == 0) return null
        if (internalUserAccount == null) {
            internalUserAccount = eci.getEntity().makeFind("moqui.security.UserAccount").condition("username", this.getUsername()).useCache(true).one()
        }
        // logger.info("Got UserAccount [${internalUserAccount}] with userIdStack [${userIdStack}]")
        return internalUserAccount
    }

    /** @see org.moqui.context.UserFacade#getVisitUserId() */
    String getVisitUserId() { return visitId ? getVisit().userId : null }

    /** @see org.moqui.context.UserFacade#getVisitId() */
    String getVisitId() { return visitId }

    /** @see org.moqui.context.UserFacade#getVisit() */
    EntityValue getVisit() {
        if (!visitId) return null

        EntityValue vst
        boolean alreadyDisabled = eci.getArtifactExecution().disableAuthz()
        try {
            vst = eci.getEntity().makeFind("moqui.server.Visit").condition("visitId", visitId).useCache(true).one()
        } finally {
            if (!alreadyDisabled) eci.getArtifactExecution().enableAuthz()
        }
        return vst
    }
}
