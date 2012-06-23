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
import org.moqui.context.ResourceReference
import org.moqui.service.ServiceFacade
import org.moqui.service.ServiceCallback
import org.moqui.service.ServiceCallSync
import org.moqui.service.ServiceCallAsync
import org.moqui.service.ServiceCallSchedule
import org.moqui.service.ServiceCallSpecial

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.reference.ClasspathResourceReference

import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import javax.mail.internet.MimeMessage
import org.moqui.context.ExecutionContext
import org.moqui.BaseException

class ServiceFacadeImpl implements ServiceFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServiceFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache serviceLocationCache

    protected final Map<String, List<ServiceEcaRule>> secaRulesByServiceName = new HashMap()
    protected final List<EmailEcaRule> emecaRuleList = new ArrayList()

    protected final Map<String, ServiceRunner> serviceRunners = new HashMap()

    protected final Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler()

    protected final Map<String, List<ServiceCallback>> callbackRegistry = new HashMap()

    ServiceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi

        this.serviceLocationCache = ecfi.getCacheFacade().getCache("service.location")

        // load Service ECA rules
        loadSecaRulesAll()
        // load Email ECA rules
        loadEmecaRulesAll()

        // load service runners from configuration
        for (Node serviceType in ecfi.confXmlRoot."service-facade"[0]."service-type") {
            ServiceRunner sr = (ServiceRunner) Thread.currentThread().getContextClassLoader().loadClass(serviceType."@runner-class").newInstance()
            serviceRunners.put(serviceType."@name", sr.init(this))
        }

        // init quartz scheduler (do last just in case it gets any jobs going right away)
        scheduler.start()
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
        // logger.warn("Getting service definition for [${serviceName}], path=[${path}] verb=[${verb}] noun=[${noun}]")

        String cacheKey = makeCacheKey(path, verb, noun)
        if (serviceLocationCache.containsKey(cacheKey)) {
            // NOTE: this could be null if it's a known non-existing service
            return (ServiceDefinition) serviceLocationCache.get(cacheKey)
        }

        return makeServiceDefinition(path, verb, noun)
    }

    protected ServiceDefinition makeServiceDefinition(String path, String verb, String noun) {
        String cacheKey = makeCacheKey(path, verb, noun)
        if (serviceLocationCache.containsKey(cacheKey)) {
            // NOTE: this could be null if it's a known non-existing service
            return (ServiceDefinition) serviceLocationCache.get(cacheKey)
        }

        Node serviceNode = findServiceNode(path, verb, noun)
        if (serviceNode == null) {
            // NOTE: don't throw an exception for service not found (this is where we know there is no def), let service caller handle that
            // Put null in the cache to remember the non-existing service
            serviceLocationCache.put(cacheKey, null)
            return null
        }

        ServiceDefinition sd = new ServiceDefinition(this, path, serviceNode)
        serviceLocationCache.put(cacheKey, sd)
        return sd
    }

    protected static String makeCacheKey(String path, String verb, String noun) {
        // use a consistent format as the key in the cache, keeping in mind that the verb and noun may be merged in the serviceName passed in
        // no # here so that it doesn't matter if the caller used one or not
        return (path ? path + "." : "") + verb + (noun ? noun : "")
    }

    protected Node findServiceNode(String path, String verb, String noun) {
        if (!path) return null

        // make a file location from the path
        String partialLocation = path.replace('.', '/') + ".xml"
        String servicePathLocation = "service/" + partialLocation

        Node serviceNode = null

        // search for the service def XML file in the components
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            // logger.warn("Finding service node for location=[${location}], servicePathLocation=[${servicePathLocation}]")
            ResourceReference serviceComponentRr = this.ecfi.resourceFacade.getLocationReference(location + "/" + servicePathLocation)
            if (serviceComponentRr.supportsExists()) {
                if (serviceComponentRr.exists) serviceNode = findServiceNode(serviceComponentRr, verb, noun)
            } else {
                // only way to see if it is a valid location is to try opening the stream, so no extra conditions here
                serviceNode = findServiceNode(serviceComponentRr, verb, noun)
            }
            if (serviceNode) break
        }

        // search for the service def XML file in the classpath LAST (allow components to override, same as in entity defs)
        if (serviceNode == null) {
            ResourceReference serviceComponentRr = new ClasspathResourceReference().init(servicePathLocation, ecfi.executionContext)
            if (serviceComponentRr.supportsExists() && serviceComponentRr.exists)
                serviceNode = findServiceNode(serviceComponentRr, verb, noun)
        }

        if (serviceNode == null) logger.info("Service ${path}.${verb}#${noun} not found; used relative location [${servicePathLocation}]")

        return serviceNode
    }

    protected Node findServiceNode(ResourceReference serviceComponentRr, String verb, String noun) {
        if (!serviceComponentRr) return null

        Node serviceNode = null
        InputStream serviceFileIs = null

        try {
            serviceFileIs = serviceComponentRr.openStream()
            Node serviceRoot = new XmlParser().parse(serviceFileIs)
            if (noun) {
                // only accept the separated names
                serviceNode = (Node) serviceRoot."service".find({ it."@verb" == verb && it."@noun" == noun })
                // try the combined name
                if (serviceNode == null)
                    serviceNode = (Node) serviceRoot."service".find({ it."@verb" == verb && it."@noun" == noun })
            } else {
                // we just have a verb, this should work if the noun field is empty, or if noun + verb makes up the verb passed in
                serviceNode = (Node) serviceRoot."service".find({ (it."@verb" + (it."@noun" ?: "")) == verb })
            }
        } catch (IOException e) {
            // probably because there is no resource at that location, so do nothing
            logger.trace("Error finding service in URL [${serviceComponentRr.location}]", e)
            return null
        } catch (Exception e) {
            throw new BaseException("Error finding service in [${serviceComponentRr.location}]", e)
        } finally {
            if (serviceFileIs != null) serviceFileIs.close()
        }

        return serviceNode
    }

    Set<String> getKnownServiceNames() {
        Set<String> sns = new TreeSet()

        // search for service def XML files in the components
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            //String location = "component://${componentName}/service"
            ResourceReference serviceRr = this.ecfi.resourceFacade.getLocationReference(location + "/service")
            if (serviceRr.supportsExists() && serviceRr.exists && serviceRr.supportsDirectory()) {
                findServicesInDir(serviceRr.location, serviceRr, sns)
            }
        }

        // TODO: how to search for service def XML files in the classpath? perhaps keep a list of service files that
        //     have been found on the classpath so we at least have those?

        return sns
    }

    protected void findServicesInDir(String baseLocation, ResourceReference dir, Set<String> sns) {
        // logger.warn("Finding services in [${dir.location}]")
        for (ResourceReference entryRr in dir.directoryEntries) {
            if (entryRr.directory) {
                findServicesInDir(baseLocation, entryRr, sns)
            } else if (entryRr.fileName.endsWith(".xml")) {
                // logger.warn("Finding services in [${entryRr.location}], baseLocation=[${baseLocation}]")
                Node serviceRoot = new XmlParser().parse(entryRr.openStream())
                if (serviceRoot.name() != "services") continue

                // get the service file location without the .xml and without everything up to the "service" directory
                String location = entryRr.location.substring(0, entryRr.location.lastIndexOf("."))
                if (location.startsWith(baseLocation)) location = location.substring(baseLocation.length())

                for (Node serviceNode in serviceRoot."service") {
                    sns.add(location + "." + serviceNode."@verb" +
                            (serviceNode."@noun" ? "#" + serviceNode."@noun" : ""))
                }
            }
        }
    }

    protected void loadSecaRulesAll() {
        if (secaRulesByServiceName.size() > 0) secaRulesByServiceName.clear()

        // search for the service def XML file in the components
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            ResourceReference serviceDirRr = this.ecfi.resourceFacade.getLocationReference(location + "/service")
            if (serviceDirRr.supportsAll()) {
                // if for some weird reason this isn't a directory, skip it
                if (!serviceDirRr.isDirectory()) continue
                for (ResourceReference rr in serviceDirRr.directoryEntries) {
                    if (!rr.fileName.endsWith(".secas.xml")) continue
                    loadSecaRulesFile(rr)
                }
            } else {
                logger.warn("Can't load SECA rules from component at [${serviceDirRr.location}] because it doesn't support exists/directory/etc")
            }
        }
    }
    protected void loadSecaRulesFile(ResourceReference rr) {
        InputStream is = null
        try {
            is = rr.openStream()
            Node serviceRoot = new XmlParser().parse(is)
            int numLoaded = 0
            for (Node secaNode in serviceRoot."seca") {
                ServiceEcaRule ser = new ServiceEcaRule(ecfi, secaNode, rr.location)
                String serviceName = ser.serviceName
                // remove the hash if there is one to more consistently match the service name
                if (serviceName.contains("#")) serviceName = serviceName.replace("#", "")
                List<ServiceEcaRule> lst = secaRulesByServiceName.get(serviceName)
                if (!lst) {
                    lst = new LinkedList()
                    secaRulesByServiceName.put(serviceName, lst)
                }
                lst.add(ser)
                numLoaded++
            }
            if (logger.infoEnabled) logger.info("Loaded [${numLoaded}] Service ECA rules from [${rr.location}]")
        } catch (IOException e) {
            // probably because there is no resource at that location, so do nothing
            if (logger.traceEnabled) logger.trace("Error loading SECA rules from [${rr.location}]", e)
        } finally {
            if (is != null) is.close()
        }
    }

    void runSecaRules(String serviceName, Map<String, Object> parameters, Map<String, Object> results, String when) {
        // remove the hash if there is one to more consistently match the service name
        if (serviceName.contains("#")) serviceName = serviceName.replace("#", "")
        List<ServiceEcaRule> lst = secaRulesByServiceName.get(serviceName)
        ExecutionContext ec = ecfi.executionContext
        for (ServiceEcaRule ser in lst) ser.runIfMatches(serviceName, parameters, results, when, ec)
    }

    void registerTxSecaRules(String serviceName, Map<String, Object> parameters) {
        // remove the hash if there is one to more consistently match the service name
        if (serviceName.contains("#")) serviceName = serviceName.replace("#", "")
        List<ServiceEcaRule> lst = secaRulesByServiceName.get(serviceName)
        for (ServiceEcaRule ser in lst) {
            if (ser.when.startsWith("tx-")) {
                ser.registerTx(serviceName, parameters, ecfi)
            }
        }
    }

    protected void loadEmecaRulesAll() {
        if (emecaRuleList.size() > 0) emecaRuleList.clear()

        // search for the service def XML file in the components
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            ResourceReference serviceDirRr = this.ecfi.resourceFacade.getLocationReference(location + "/service")
            if (serviceDirRr.supportsAll()) {
                // if for some weird reason this isn't a directory, skip it
                if (!serviceDirRr.isDirectory()) continue
                for (ResourceReference rr in serviceDirRr.directoryEntries) {
                    if (!rr.fileName.endsWith(".emecas.xml")) continue
                    loadEmecaRulesFile(rr)
                }
            } else {
                logger.warn("Can't load Email ECA rules from component at [${serviceDirRr.location}] because it doesn't support exists/directory/etc")
            }
        }
    }
    protected void loadEmecaRulesFile(ResourceReference rr) {
        InputStream is = null
        try {
            is = rr.openStream()
            Node emecasRoot = new XmlParser().parse(is)
            int numLoaded = 0
            for (Node emecaNode in emecasRoot."emeca") {
                EmailEcaRule eer = new EmailEcaRule(ecfi, emecaNode, rr.location)
                emecaRuleList.add(eer)
                numLoaded++
            }
            if (logger.infoEnabled) logger.info("Loaded [${numLoaded}] Email ECA rules from [${rr.location}]")
        } catch (IOException e) {
            // probably because there is no resource at that location, so do nothing
            if (logger.traceEnabled) logger.trace("Error loading Email ECA rules from [${rr.location}]", e)
        } finally {
            if (is != null) is.close()
        }
    }

    void runEmecaRules(MimeMessage message) {
        ExecutionContext ec = ecfi.executionContext
        for (EmailEcaRule eer in emecaRuleList) eer.runIfMatches(message, ec)
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
