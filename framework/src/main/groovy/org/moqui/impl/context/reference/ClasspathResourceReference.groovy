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
package org.moqui.impl.context.reference

import org.moqui.context.ResourceReference
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ResourceFacadeImpl

class ClasspathResourceReference extends UrlResourceReference {

    protected String strippedLocation

    ClasspathResourceReference() { super() }

    ResourceReference init(String location, ExecutionContext ec) {
        this.ec = ec
        strippedLocation = ResourceFacadeImpl.stripLocationPrefix(location)
        // first try the current thread's context ClassLoader
        locationUrl = Thread.currentThread().getContextClassLoader().getResource(strippedLocation)
        // next try the ClassLoader that loaded this class
        if (locationUrl == null) locationUrl = this.getClass().getClassLoader().getResource(strippedLocation)
        // no luck? try the system ClassLoader
        if (locationUrl == null) locationUrl = ClassLoader.getSystemResource(strippedLocation)
        // if the URL was found this way then it exists, so remember that
        if (locationUrl != null) {
            exists = true
        } else {
            logger.warn("Could not find location [${strippedLocation}] on the classpath")
        }

        return this
    }
}
