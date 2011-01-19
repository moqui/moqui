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

import org.moqui.context.Cache
import org.moqui.service.ServiceFacade
import org.moqui.service.ServiceCallback
import org.moqui.service.ServiceCallSync
import org.moqui.service.ServiceCallAsync
import org.moqui.service.ServiceCallSchedule
import org.moqui.service.ServiceCallSpecial
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.service.runner.JavaServiceRunner
import org.moqui.impl.service.runner.ScriptServiceRunner
import org.moqui.impl.service.runner.EntityAutoServiceRunner
import org.moqui.impl.service.runner.InlineServiceRunner
import org.moqui.impl.service.runner.ProxyHttpServiceRunner
import org.moqui.impl.service.runner.ProxyJmsServiceRunner
import org.moqui.impl.service.runner.ProxyRmiServiceRunner
import org.moqui.impl.service.runner.RemoteXmlrpcServiceRunner

import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ServiceFacadeImpl implements ServiceFacade {
    protected final static Logger logger = LoggerFactory.getLogger(ServiceFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache serviceLocationCache

    protected final Map<String, ServiceRunner> serviceRunners = new HashMap()

    protected final Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler()

    protected final Map<String, List<ServiceCallback>> callbackRegistry = new HashMap()

    ServiceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi

        this.serviceLocationCache = ecfi.getCacheFacade().getCache("service.location")

        // init quartz scheduler
        scheduler.start()
        // TODO: load SECA rule tables

        // init service runners
        serviceRunners.put("inline", new InlineServiceRunner().init(this))
        serviceRunners.put("entity-auto", new EntityAutoServiceRunner().init(this))
        serviceRunners.put("script", new ScriptServiceRunner().init(this))
        serviceRunners.put("java", new JavaServiceRunner().init(this))
        serviceRunners.put("proxy-http", new ProxyHttpServiceRunner().init(this))
        serviceRunners.put("proxy-jms", new ProxyJmsServiceRunner().init(this))
        serviceRunners.put("proxy-rmi", new ProxyRmiServiceRunner().init(this))
        serviceRunners.put("remote-xmlrpc", new RemoteXmlrpcServiceRunner().init(this))

        // load other service runners from configuration
        for (Node serviceType in ecfi.confXmlRoot."service-facade"[0]."service-type") {
            ServiceRunner sr = (ServiceRunner) this.getClass().getClassLoader().loadClass(serviceType."@runner-class").newInstance()
            serviceRunners.put(serviceType."@name", sr.init(this))
        }
    }

    void destroy() {
        // destroy all service runners
        for (ServiceRunner sr in serviceRunners.values()) sr.destroy()

        // destroy quartz scheduler, after allowing currently executing jobs to complete
        scheduler.shutdown(true)
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }

    ServiceRunner getServiceRunner(String type) { return serviceRunners.get(type) }

    ServiceDefinition getServiceDefinition(String serviceName) {
        String path = ServiceDefinition.getPathFromName(serviceName)
        String verb = ServiceDefinition.getVerbFromName(serviceName)
        String noun = ServiceDefinition.getNounFromName(serviceName)

        ServiceDefinition sd = (ServiceDefinition) serviceLocationCache.get(makeCacheKey(path, verb, noun))
        if (sd) return sd

        return makeServiceDefinition(path, verb, noun)
    }

    protected synchronized ServiceDefinition makeServiceDefinition(String path, String verb, String noun) {
        String cacheKey = makeCacheKey(path, verb, noun)
        ServiceDefinition sd = (ServiceDefinition) serviceLocationCache.get(cacheKey)
        if (sd) return sd

        Node serviceNode = findServiceNode(path, verb, noun)
        if (serviceNode == null) {
            throw new IllegalArgumentException("Cound not find definition for service name [${cacheKey}]")
        }

        sd = new ServiceDefinition(this, path, serviceNode)
        serviceLocationCache.put(cacheKey, sd)
        return sd
    }

    protected static String makeCacheKey(String path, String verb, String noun) {
        // use a consistent format as the key in the cache, keeping in mind that the verb and noun may be merged in the serviceName passed in
        // no # here so that it doesn't matter if the caller used one or not
        return (path ? path + "." : "") + verb + (noun ? noun : "")
    }

    protected Node findServiceNode(String path, String verb, String noun) {
        // make a file location from the path
        String partialLocation = path.replace('.', '/') + ".xml"
        String servicePathLocation = "service/" + partialLocation

        Node serviceNode = null

        // search for the service def XML file in the components
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            URL serviceComponentUrl = this.ecfi.resourceFacade.getLocationUrl(location + "/" + servicePathLocation)
            if (serviceComponentUrl.getProtocol() == "file") {
                File serviceComponentFile = new File(serviceComponentUrl.toURI())
                if (serviceComponentFile.exists()) serviceNode = findServiceNode(serviceComponentUrl, verb, noun)
            } else {
                // only way to see if it is a valid location is to try opening the stream, so no extra conditions here
                serviceNode = findServiceNode(serviceComponentUrl, verb, noun)
            }
            if (serviceNode) break
        }

        // search for the service def XML file in the classpath LAST (allow components to override, same as in entity defs)
        // first try the ClassLoader that loaded this class
        if (serviceNode == null) {
            URL serviceFileUrl = this.getClass().getClassLoader().getResource(servicePathLocation)
            if (serviceFileUrl) serviceNode = findServiceNode(serviceFileUrl, verb, noun)
        }
        // no luck? try the system ClassLoader
        if (serviceNode == null) {
            URL serviceFileUrl = ClassLoader.getSystemResource(servicePathLocation)
            if (serviceFileUrl) serviceNode = findServiceNode(serviceFileUrl, verb, noun)
        }

        if (serviceNode == null) logger.info("Service ${path}.${verb}#${noun} not found; used relative location [${servicePathLocation}]")

        return serviceNode
    }

    protected Node findServiceNode(URL serviceFileUrl, String verb, String noun) {
        if (!serviceFileUrl) return null

        Node serviceNode = null
        InputStream serviceFileIs = null

        try {
            serviceFileIs = serviceFileUrl.openStream()
            Node serviceRoot = new XmlParser().parse(serviceFileIs)
            if (noun) {
                // only accept the separated names
                serviceNode = (Node) serviceRoot."service".find({ it."@verb" == verb && it."@noun" == noun })
                // try the combined name
                if (serviceNode == null)
                    serviceNode = (Node) serviceRoot."service".find({ it."@verb" == verb && it."@noun" == noun })
            } else {
                // we just have a verb, this should work if the noun field is empty, or if noun + verb makes up the verb passed in
                serviceNode = (Node) serviceRoot."service".find({ (it."@verb" + it."@noun") == verb })
            }
        } catch (IOException e) {
            // probably because there is no resource at that location, so do nothing
            logger.trace("Error finding service in URL ${serviceFileUrl.toString()}", e)
            return null
        } finally {
            if (serviceFileIs != null) serviceFileIs.close()
        }
        return serviceNode
    }

    @Override
    ServiceCallSync sync() { return new ServiceCallSyncImpl(this) }

    @Override
    ServiceCallAsync async() { return new ServiceCallAsyncImpl(this) }

    @Override
    ServiceCallSchedule schedule() { return new ServiceCallScheduleImpl(this) }

    @Override
    ServiceCallSpecial special() { return new ServiceCallSpecialImpl(this) }

    /** @see org.moqui.service.ServiceFacade#registerCallback(String, ServiceCallback) */
    @Override
    synchronized void registerCallback(String serviceName, ServiceCallback serviceCallback) {
        List<ServiceCallback> callbackList = callbackRegistry.get(serviceName)
        if (callbackList == null) {
            callbackList = new ArrayList()
            callbackRegistry.put(serviceName, callbackList)
        }
        callbackList.add(serviceCallback)
    }
}
