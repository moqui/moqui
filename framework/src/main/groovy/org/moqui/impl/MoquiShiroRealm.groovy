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
package org.moqui.impl

import java.sql.Timestamp

import org.apache.shiro.authc.AuthenticationInfo
import org.apache.shiro.authc.AuthenticationToken
import org.apache.shiro.authc.AuthenticationException
import org.apache.shiro.authc.credential.CredentialsMatcher
import org.apache.shiro.authc.IncorrectCredentialsException
import org.apache.shiro.authc.LockedAccountException
import org.apache.shiro.authc.SaltedAuthenticationInfo
import org.apache.shiro.authc.SimpleAuthenticationInfo
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.authc.UnknownAccountException
import org.apache.shiro.authz.Permission
import org.apache.shiro.realm.Realm
import org.apache.shiro.subject.PrincipalCollection
import org.apache.shiro.util.SimpleByteSource

import org.moqui.entity.EntityValue
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.Moqui

class MoquiShiroRealm implements Realm {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MoquiShiroRealm.class)

    protected ExecutionContextFactoryImpl ecfi
    protected String realmName = "moquiRealm"

    protected Class<? extends AuthenticationToken> authenticationTokenClass = UsernamePasswordToken.class

    MoquiShiroRealm() {
        // with this sort of init we may only be able to get ecfi through static reference
        this.ecfi = (ExecutionContextFactoryImpl) Moqui.executionContextFactory
    }

    MoquiShiroRealm(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
    }

    void setName(String n) { realmName = n }
    String getName() { return realmName }

    //Class getAuthenticationTokenClass() { return authenticationTokenClass }
    //void setAuthenticationTokenClass(Class<? extends AuthenticationToken> atc) { authenticationTokenClass = atc }

    boolean supports(AuthenticationToken token) {
        return token != null && authenticationTokenClass.isAssignableFrom(token.getClass())
    }

    AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String username = token.principal
        String userId = null
        boolean successful = false

        EntityValue newUserAccount
        SaltedAuthenticationInfo info = null
        try {
            boolean alreadyDisabled = ecfi.executionContext.artifactExecution.disableAuthz()
            try {
                newUserAccount = ecfi.entityFacade.makeFind("UserAccount").condition("username", username).useCache(true).one()
            } finally {
                if (!alreadyDisabled) ecfi.executionContext.artifactExecution.enableAuthz()
            }

            if (!newUserAccount) throw new UnknownAccountException("Username [${username}] and/or password incorrect.")

            // create the SaltedAuthenticationInfo object
            info = new SimpleAuthenticationInfo(username, newUserAccount.currentPassword,
                    newUserAccount.passwordSalt ? new SimpleByteSource((String) newUserAccount.passwordSalt) : null,
                    realmName)

            CredentialsMatcher cm = ecfi.getCredentialsMatcher((String) newUserAccount.passwordHashType)
            if (!cm.doCredentialsMatch(token, info)) {
                // only if failed on password, increment in new transaction to make sure it sticks
                ecfi.serviceFacade.sync().name("org.moqui.impl.UserServices.incrementUserAccountFailedLogins")
                        .parameters((Map<String, Object>) [userId:newUserAccount.userId]).requireNewTransaction(true).call()
                throw new IncorrectCredentialsException("Username [${username}] and/or password incorrect.")
            }

            // the password did match, but check a few additional things
            if (newUserAccount.requirePasswordChange == "Y") {
                throw new LockedAccountException("Authenticate failed for user [${username}] because account requires password change [PWDCHG].")
            }
            if (newUserAccount.disabled == "Y") {
                Timestamp reEnableTime = null
                if (newUserAccount.disabledDateTime) {
                    Integer disabledMinutes = ecfi.confXmlRoot."user-facade"[0]."login"[0]."@disable-minutes" as Integer ?: 30
                    reEnableTime = new Timestamp(newUserAccount.getTimestamp("disabledDateTime").getTime() + (disabledMinutes*60*1000))
                }
                if (!reEnableTime || reEnableTime < getNowTimestamp()) {
                    throw new LockedAccountException("Authenticate failed for user [${username}] because account is disabled and will not be re-enabled until [${reEnableTime}] [ACTDIS].")
                }
            }
            // check time since password was last changed, if it has been too long (user-facade.password.@change-weeks default 12) then fail
            if (newUserAccount.passwordSetDate) {
                int changeWeeks = (ecfi.confXmlRoot."user-facade"[0]."password"[0]."@change-weeks" ?: 12) as int
                int wksSinceChange = (ecfi.executionContext.user.nowTimestamp.time - newUserAccount.passwordSetDate.time) / (7*24*60*60*1000)
                if (wksSinceChange > changeWeeks) {
                    throw new LockedAccountException("Authenticate failed for user [${username}] because password was changed [${wksSinceChange}] weeks ago and should be changed every [${changeWeeks}] weeks [PWDTIM].")
                }
            }

            // at this point the user is successfully authenticated
            successful = true
            logger.warn("User [${username}] successfully authc'ed")

            // NOTE: special case, for this thread only and for the section of code below need to turn off artifact
            //     authz since normally the user above would have authorized with something higher up, but that can't
            //     be done at this point
            alreadyDisabled = ecfi.executionContext.artifactExecution.disableAuthz()
            try {
                userId = newUserAccount.userId

                // no more auth failures? record the various account state updates, hasLoggedOut=N
                Map<String, Object> uaParameters = (Map<String, Object>) [userId:userId, successiveFailedLogins:0,
                        disabled:"N", disabledDateTime:null, hasLoggedOut:"N"]
                ecfi.serviceFacade.sync().name("update", "UserAccount").parameters(uaParameters).call()

                // update visit if no user in visit yet
                EntityValue visit = ecfi.executionContext.user.visit
                if (visit && !visit.userId) {
                    ecfi.serviceFacade.sync().name("update", "Visit")
                            .parameters((Map<String, Object>) [visitId:visit.visitId, userId:userId]).call()
                }
            } finally {
                if (!alreadyDisabled) ecfi.executionContext.artifactExecution.enableAuthz()
            }

            // TODO: Shiro automatically adds to session?
        } finally {
            // track the UserLoginHistory, whether the above succeeded or failed (ie even if an exception was thrown)
            Node loginNode = ecfi.confXmlRoot."user-facade"[0]."login"[0]
            if (userId != null && loginNode."@history-store" != "false") {
                Map<String, Object> ulhContext =
                        (Map<String, Object>) [userId:userId, visitId:ecfi.executionContext.user.visitId,
                                successfulLogin:(successful?"Y":"N")]
                if (!successful && loginNode."@history-incorrect-password" != "false") ulhContext.passwordUsed = token.credentials
                boolean alreadyDisabled = ecfi.executionContext.artifactExecution.disableAuthz()
                try {
                    ecfi.serviceFacade.sync().name("create", "UserLoginHistory").parameters(ulhContext).call()
                } finally {
                    if (!alreadyDisabled) ecfi.executionContext.artifactExecution.enableAuthz()
                }
            }
        }

        return info;
    }

    // ========== Authorization Methods ==========

    boolean isPermitted(PrincipalCollection principalCollection, String s) {
        // TODO
        return false
    }

    boolean isPermitted(PrincipalCollection principalCollection, Permission permission) {
        // TODO
        return false
    }

    boolean[] isPermitted(PrincipalCollection principalCollection, String... strings) {
        // TODO
        return new boolean[0]
    }

    boolean[] isPermitted(PrincipalCollection principalCollection, List<Permission> permissions) {
        // TODO
        return new boolean[0]
    }

    boolean isPermittedAll(PrincipalCollection principalCollection, String... strings) {
        // TODO
        return false
    }

    boolean isPermittedAll(PrincipalCollection principalCollection, Collection<Permission> permissions) {
        // TODO
        return false
    }

    void checkPermission(PrincipalCollection principalCollection, String s) {
        // TODO
    }

    void checkPermission(PrincipalCollection principalCollection, Permission permission) {
        // TODO
    }

    void checkPermissions(PrincipalCollection principalCollection, String... strings) {
        // TODO
    }

    void checkPermissions(PrincipalCollection principalCollection, Collection<Permission> permissions) {
        // TODO
    }

    boolean hasRole(PrincipalCollection principalCollection, String s) {
        // TODO
        return false
    }

    boolean[] hasRoles(PrincipalCollection principalCollection, List<String> strings) {
        // TODO
        return new boolean[0]
    }

    boolean hasAllRoles(PrincipalCollection principalCollection, Collection<String> strings) {
        // TODO
        return false
    }

    void checkRole(PrincipalCollection principalCollection, String s) {
        // TODO
    }

    void checkRoles(PrincipalCollection principalCollection, Collection<String> strings) {
        // TODO
    }

    void checkRoles(PrincipalCollection principalCollection, String... strings) {
        // TODO
    }
}
