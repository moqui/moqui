/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.reference

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ResourceReference
import org.moqui.impl.context.ResourceFacadeImpl

class ComponentResourceReference extends WrapperResourceReference {

    protected String componentLocation

    ComponentResourceReference() { super() }

    ResourceReference init(String location, ExecutionContextFactory ecf) {
        this.ecf = ecf

        if (location.endsWith("/")) location = location.substring(0, location.length()-1)
        this.componentLocation = location

        String strippedLocation = ResourceFacadeImpl.stripLocationPrefix(location)

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
        baseLocation.insert(0, ecf.getComponentBaseLocations().get(componentName))

        this.rr = ecf.resource.getLocationReference(baseLocation.toString())

        return this
    }

    @Override
    String getLocation() { return componentLocation?.toString() }

    @Override
    List<ResourceReference> getDirectoryEntries() {
        // a little extra work to keep the directory entries as component-based locations
        List<ResourceReference> nestedList = this.rr.getDirectoryEntries()
        List<ResourceReference> newList = new ArrayList(nestedList.size())
        for (ResourceReference entryRr in nestedList) {
            String entryLoc = entryRr.location
            if (entryLoc.endsWith("/")) entryLoc = entryLoc.substring(0, entryLoc.length()-1)
            String newLocation = this.componentLocation + "/" + entryLoc.substring(entryLoc.lastIndexOf("/")+1)
            newList.add(new ComponentResourceReference().init(newLocation, ecf))
        }
        return newList
    }
}
