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

public class ResourceFacadeImpl implements ResourceFacade {

    protected final ExecutionContextFactoryImpl ecfi;

    public ResourceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

    /** @see org.moqui.context.ResourceFacade#getLocationUrl(String) */
    public URL getLocationUrl(String location) {
        String strippedLocation = stripLocationPrefix(location);
        URL locationUrl = null;

        if (location.startsWith("component://")) {
            // turn this into another URL using the component location
            StringBuffer baseLocation = new StringBuffer(strippedLocation);

            // componentName is everything before the first slash
            String componentName;
            int firstSlash = baseLocation.indexOf("/");
            if (firstSlash > 0) {
                componentName = baseLocation.substring(0, firstSlash);
                // got the componentName, now remove it from the baseLocation
                baseLocation.delete(0, secondSlash + 1);
            } else {
                componentName = baseLocation;
                baseLocation = new StringBuffer("");
            }

            String rootLocation = this.ecfi.getComponentBaseLocations().get(componentName);
            baseLocation.insert(0, '/');
            baseLocation.insert(0, rootLocation);

            location = baseLocation.toString();
        }

        if (location.startsWith("classpath://")) {
            locationUrl = ClassLoader.getResource(strippedLocation);
            if (!locationUrl) locationUrl = ClassLoader.getSystemResource(strippedLocation);
        } else if (location.startsWith("https://") || location.startsWith("http://") ||
                   location.startsWith("ftp://") || location.startsWith("file://") ||
                   location.startsWith("jar://")) {
            locationUrl = new URL(location);
        } else if (location.indexOf(":/") < 0) {
            // no prefix, local file: if starts with '/' is absolute, otherwise is relative to runtime path
            if (location.charAt(0) != '/') {
                location = this.runtimeFile.getAbsolutePath() + '/' + location;
            }
            locationUrl = new File(location).toURI().toURL();
        } else {
            // unsupported location...
            throw new IllegalArgumentException("Unsupported location, prefix (scheme) no supported in location: " + location);
        }

        return locationUrl;
    }

    /** @see org.moqui.context.ResourceFacade#getLocationStream(String) */
    public InputStream getLocationStream(String location) {
        URL locationUrl = this.getLocationUrl(location);
        return locationUrl.newInputStream();
    }

    protected static String stripLocationPrefix(String location) {
        if (!location) {
            return "";
        }

        // first remove colon (:) and everything before it
        StringBuilder strippedLocation = new StringBuilder(location);
        int colonIndex = strippedLocation.indexOf(":");
        if (colonIndex == 0) {
            strippedLocation.deleteCharAt(0);
        } else if (colonIndex > 0) {
            strippedLocation.delete(0, colonIndex+1);
        }

        // now remove all forward slashes
        while (strippedLocation.charAt(0) == '/') {
            strippedLocation.deleteCharAt(0);
        }

        return strippedLocation.toString();
    }
}
