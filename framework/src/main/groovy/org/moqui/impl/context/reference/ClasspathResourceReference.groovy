/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.reference

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ResourceReference
import org.moqui.impl.context.ResourceFacadeImpl

class ClasspathResourceReference extends UrlResourceReference {

    protected String strippedLocation

    ClasspathResourceReference() { super() }

    ResourceReference init(String location, ExecutionContextFactory ecf) {
        this.ecf = ecf
        strippedLocation = ResourceFacadeImpl.stripLocationPrefix(location)
        // first try the current thread's context ClassLoader
        locationUrl = Thread.currentThread().getContextClassLoader().getResource(strippedLocation)
        // next try the ClassLoader that loaded this class
        if (locationUrl == null) locationUrl = this.getClass().getClassLoader().getResource(strippedLocation)
        // no luck? try the system ClassLoader
        if (locationUrl == null) locationUrl = ClassLoader.getSystemResource(strippedLocation)
        // if the URL was found this way then it exists, so remember that
        if (locationUrl != null) {
            this.exists = true
            this.isFileProtocol = (locationUrl?.protocol == "file")
        } else {
            logger.warn("Could not find location [${strippedLocation}] on the classpath")
        }

        return this
    }
}
