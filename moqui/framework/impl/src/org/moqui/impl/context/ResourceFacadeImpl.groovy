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

import org.moqui.context.ResourceFacade
import org.moqui.context.Cache
import java.nio.charset.Charset
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.impl.actions.XmlAction

public class ResourceFacadeImpl implements ResourceFacade {
    protected final static Logger logger = LoggerFactory.getLogger(ResourceFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache scriptGroovyLocationCache
    protected final Cache scriptXmlActionLocationCache

    ResourceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.scriptGroovyLocationCache = ecfi.getCacheFacade().getCache("script.groovy.location")
        this.scriptXmlActionLocationCache = ecfi.getCacheFacade().getCache("script.xml-actions.location")
    }

    /** @see org.moqui.context.ResourceFacade#getLocationUrl(String) */
    URL getLocationUrl(String location) {
        String strippedLocation = stripLocationPrefix(location)

        if (location.startsWith("component://")) {
            // turn this into another URL using the component location
            StringBuffer baseLocation = new StringBuffer(strippedLocation)

            // componentName is everything before the first slash
            String componentName;
            int firstSlash = baseLocation.indexOf("/")
            if (firstSlash > 0) {
                componentName = baseLocation.substring(0, firstSlash)
                // got the componentName, now remove it from the baseLocation
                baseLocation.delete(0, firstSlash + 1)
            } else {
                componentName = baseLocation
                baseLocation.delete(0, baseLocation.length())
            }

            baseLocation.insert(0, '/')
            baseLocation.insert(0, this.ecfi.getComponentBaseLocations().get(componentName))
            location = baseLocation.toString()
        }

        URL locationUrl
        if (location.startsWith("classpath://")) {
            // first try the ClassLoader that loaded this class
            locationUrl = this.getClass().getClassLoader().getResource(strippedLocation)
            // no luck? try the system ClassLoader
            if (!locationUrl) locationUrl = ClassLoader.getSystemResource(strippedLocation)
        } else if (location.startsWith("https://") || location.startsWith("http://") ||
                   location.startsWith("ftp://") || location.startsWith("file:") ||
                   location.startsWith("jar:")) {
            locationUrl = new URL(location)
        } else if (location.indexOf(":/") < 0) {
            // no prefix, local file: if starts with '/' is absolute, otherwise is relative to runtime path
            if (location.charAt(0) != '/') location = this.ecfi.runtimePath + '/' + location
            locationUrl = new File(location).toURI().toURL()
        } else {
            throw new IllegalArgumentException("Prefix (scheme) not supported for location [${location}")
        }

        return locationUrl
    }

    /** @see org.moqui.context.ResourceFacade#getLocationStream(String) */
    InputStream getLocationStream(String location) {
        URL lu = getLocationUrl(location)
        if (!lu) return null
        return lu.newInputStream()
    }

    String getLocationText(String location) {
        InputStream is = null
        Reader r = null
        try {
            is = getLocationStream(location)
            if (!is) return null

            r = new InputStreamReader(new BufferedInputStream(is), Charset.forName("UTF-8"))

            StringBuilder sb = new StringBuilder()
            char[] buf = new char[4096]
            int i
            while ((i = r.read(buf, 0, 4096)) > 0) {
                sb.append(buf, 0, i)
            }
            return sb.toString()
        } finally {
            // closing r should close is, if not add that here
            try { if (r) r.close() } catch (IOException e) { logger.warn("Error in close after reading text", e) }
        }
    }

    protected static String stripLocationPrefix(String location) {
        if (!location) return ""

        // first remove colon (:) and everything before it
        StringBuilder strippedLocation = new StringBuilder(location)
        int colonIndex = strippedLocation.indexOf(":")
        if (colonIndex == 0) {
            strippedLocation.deleteCharAt(0)
        } else if (colonIndex > 0) {
            strippedLocation.delete(0, colonIndex+1)
        }

        // delete all leading forward slashes
        while (strippedLocation.charAt(0) == '/') strippedLocation.deleteCharAt(0)

        return strippedLocation.toString()
    }

    Class getGroovyByLocation(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) gc = loadGroovy(location)
        return gc
    }
    protected synchronized Class loadGroovy(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) {
            gc = new GroovyClassLoader().parseClass(getLocationText(location), location)
            scriptGroovyLocationCache.put(location, gc)
        }
        return gc
    }

    XmlAction getXmlActionByLocation(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) loadXmlAction(location)
        return xa
    }
    protected synchronized XmlAction loadXmlAction(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) {
            xa = new XmlAction(ecfi, getLocationText(location), location)
            scriptXmlActionLocationCache.put(location, xa)
        }
        return xa
    }
}
