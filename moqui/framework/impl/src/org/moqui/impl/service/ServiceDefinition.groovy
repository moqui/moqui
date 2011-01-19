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
package org.moqui.impl.service

import org.moqui.impl.actions.XmlAction

class ServiceDefinition {
    protected ServiceFacadeImpl sfi
    protected Node serviceNode
    protected String path = null
    protected String verb = null
    protected String noun = null
    protected XmlAction xmlAction = null

    ServiceDefinition(ServiceFacadeImpl sfi, String path, Node serviceNode) {
        this.sfi = sfi
        this.serviceNode = serviceNode
        this.path = path
        this.verb = serviceNode."@verb"
        this.noun = serviceNode."@noun"

        // TODO: expand auto-parameters in in-parameters and out-parameters

        // if this is an inline service, get that now
        if (serviceNode."actions") {
            xmlAction = new XmlAction(sfi.ecfi, serviceNode."actions"[0], getServiceName())
        }
    }

    Node getServiceNode() { return serviceNode }

    String getServiceName() { return (path ? path + "." : "") + verb + (noun ? "#" + noun : "") }
    String getPath() { return path }
    String getVerb() { return verb }
    String getNoun() { return noun }

    static String getPathFromName(String serviceName) {
        if (!serviceName.contains(".")) return null
        return serviceName.substring(0, serviceName.lastIndexOf("."))
    }
    static String getVerbFromName(String serviceName) {
        String v = serviceName
        if (v.contains(".")) v = v.substring(v.lastIndexOf(".") + 1)
        if (v.contains("#")) v = v.substring(0, v.indexOf("#"))
        return v
    }
    static String getNounFromName(String serviceName) {
        if (!serviceName.contains("#")) return null
        return serviceName.substring(serviceName.lastIndexOf("#") + 1)
    }

    String getLocation() {
        // TODO: see if the location is an alias from the conf -> service-facade
        return serviceNode."@location"
    }

    XmlAction getXmlAction() { return xmlAction }

    Node getInParameter(String name) { return (Node) serviceNode."in-parameters"[0]."parameter".find({ it."@name" == name }) }
    Node getOutParameter(String name) { return (Node) serviceNode."out-parameters"[0]."parameter".find({ it."@name" == name }) }
    Set<String> getOutParameterNames() {
        Set<String> outNames = new HashSet()
        for (Node parameter in serviceNode."out-parameters"[0]."parameter") outNames.add(parameter."@name")
        return outNames
    }
}
