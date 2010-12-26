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

import org.moqui.context.L10nFacade
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityFind

public class L10nFacadeImpl implements L10nFacade {

    protected ExecutionContextImpl eci

    L10nFacadeImpl(ExecutionContextImpl eci) {
        this.eci = eci
    }

    /** @see org.moqui.context.L10nFacade#getLocalizedMessage(String) */
    public String getLocalizedMessage(String original) {
        if (!original) return null
        if (original.length() > 255) {
            throw new IllegalArgumentException("Original String cannot be more than 255 characters long, passed in string was [${original.length()}] characters long")
        }

        String localeString = this.eci.user.locale.toString()
        EntityFind find = this.eci.entity.find("LocalizedMessage")
        find.condition(["original":original, "locale":localeString]).useCache(true)
        EntityValue localizedMessage = find.one()
        if (!localizedMessage && localeString.contains('_')) {
            localizedMessage = find.condition("locale", localeString.substring(0, localeString.indexOf('_'))).one()
        }

        return localizedMessage ? localizedMessage.localized : null
    }
}
