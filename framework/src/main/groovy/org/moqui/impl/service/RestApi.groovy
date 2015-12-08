/*
 * This software is in the public domain under CC0 1.0 Universal plus a 
 * Grant of Patent License.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.impl.service

import groovy.transform.CompileStatic
import groovy.xml.QName
import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.context.ResourceReference
import org.moqui.entity.EntityFind
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletResponse

@CompileStatic
class RestApi {
    protected final static Logger logger = LoggerFactory.getLogger(RestApi.class)

    protected ExecutionContextFactoryImpl ecfi
    Map<String, ResourceNode> rootResourceMap = [:]

    RestApi(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi

        // find *.rest.xml files in component/service directories, put in rootResourceMap
        for (String location in this.ecfi.getComponentBaseLocations().values()) {
            ResourceReference serviceDirRr = this.ecfi.resourceFacade.getLocationReference(location + "/service")
            if (serviceDirRr.supportsAll()) {
                // if for some weird reason this isn't a directory, skip it
                if (!serviceDirRr.isDirectory()) continue
                for (ResourceReference rr in serviceDirRr.directoryEntries) {
                    if (!rr.fileName.endsWith(".rest.xml")) continue
                    Node rootNode = new XmlParser().parseText(rr.getText())
                    ResourceNode rn = new ResourceNode(rootNode, null, ecfi)
                    rootResourceMap.put(rn.name, rn)
                    logger.info("Loaded REST API from ${rr.getLocation()}")
                    // logger.info(rn.toString())
                }
            } else {
                logger.warn("Can't load REST APIs from component at [${serviceDirRr.location}] because it doesn't support exists/directory/etc")
            }
        }
    }

    RestResult run(List<String> pathList, ExecutionContext ec) {
        if (!pathList) throw new ResourceNotFoundException("Cannot run REST service with no path")
        String firstPath = pathList[0]
        ResourceNode resourceNode = rootResourceMap.get(firstPath)
        if (resourceNode == null) throw new ResourceNotFoundException("Root resource not found with name ${firstPath}")
        return resourceNode.visit(pathList, 0, ec)
    }

    Map<String, Object> getSwaggerMap(String rootResourceName, String hostName, String basePath) {
        ResourceNode resourceNode = rootResourceMap.get(rootResourceName)
        if (resourceNode == null) throw new ResourceNotFoundException("Root resource not found with name ${rootResourceName}")

        Map<String, Object> swaggerMap = [swagger:'2.0',
            info:[title:(resourceNode.displayName ?: 'Service REST API'), version:(resourceNode.version ?: '1.0'), description:(resourceNode.description ?: '')],
            host:hostName, basePath:basePath, schemes:['http', 'https'],
            consumes:['application/json', 'multipart/form-data'], produces:['application/json'],
            paths:[:], definitions:[:]
        ]

        resourceNode.addToSwaggerMap(swaggerMap)

        return swaggerMap
    }

    static abstract class MethodHandler {
        ExecutionContextFactoryImpl ecfi
        String method
        PathNode pathNode
        MethodHandler(String method, PathNode pathNode, ExecutionContextFactoryImpl ecfi) {
            this.ecfi = ecfi
            this.method = method
            this.pathNode = pathNode
        }
        abstract RestResult run(List<String> pathList, ExecutionContext ec)
        abstract void addToSwaggerMap(Map<String, Object> swaggerMap, Map<String, Map<String, Object>> resourceMap)
        abstract void toString(int level, StringBuilder sb)
    }

    protected static final Map<String, String> objectTypeJsonMap = [
            Integer:"integer", Long:"integer", Short:"integer", Float:"number", Double:"number",
            BigDecimal:"number", BigInteger:"integer",
            Boolean:"boolean", List:"array", Set:"array", Collection:"array", Map:"object" ]
    static String getJsonType(String javaType) {
        if (!javaType) return "string"
        if (javaType.contains(".")) javaType = javaType.substring(javaType.lastIndexOf(".") + 1)
        return objectTypeJsonMap.get(javaType) ?: "string"
    }
    protected static final Map<String, String> objectJsonFormatMap = [
            Integer:"int32", Long:"int64", Short:"int32", Float:"float", Double:"double",
            BigDecimal:"", BigInteger:"int64", Date:"date", Timestamp:"date-time",
            Boolean:"", List:"", Set:"", Collection:"", Map:"" ]
    static String getJsonFormat(String javaType) {
        if (!javaType) return ""
        if (javaType.contains(".")) javaType = javaType.substring(javaType.lastIndexOf(".") + 1)
        return objectJsonFormatMap.get(javaType) ?: ""
    }

    static class MethodService extends MethodHandler {
        String serviceName
        MethodService(String method, Node serviceNode, PathNode pathNode, ExecutionContextFactoryImpl ecfi) {
            super(method, pathNode, ecfi)
            serviceName = serviceNode.attribute("name")
        }
        RestResult run(List<String> pathList, ExecutionContext ec) {
            Map result = ec.getService().sync().name(serviceName).parameters(ec.context).call()
            return new RestResult(result, null)
        }

        void addToSwaggerMap(Map<String, Object> swaggerMap, Map<String, Map<String, Object>> resourceMap) {
            ServiceDefinition sd = ecfi.getServiceFacade().getServiceDefinition(serviceName)
            Node serviceNode = sd.getServiceNode()

            NodeList descNodeList = serviceNode.getAt(new QName("description"))
            Node descNode = descNodeList.size() > 0 ? (Node) descNodeList.get(0) : null

            // add parameters, including path parameters
            List<Map> parameters = []
            for (String pathParm in pathNode.pathParameters) {
                Node parmNode = sd.getInParameter(pathParm)
                if (parmNode == null) throw new IllegalArgumentException("No in parameter found for path parameter ${pathParm} in service ${sd.getServiceName()}")
                NodeList pdescNodeList = parmNode.getAt(new QName("description"))
                Node pdescNode = pdescNodeList.size() > 0 ? (Node) pdescNodeList.get(0) : null
                parameters.add([name:pathParm, in:'path', required:true, type:getJsonType((String) parmNode?.attribute('type')),
                                description:(pdescNode?.text() ?: '')])
            }
            if (sd.getInParameterNames()) {
                parameters.add([name:'body', in:'body', required:true, schema:['$ref':"#/definitions/${sd.getServiceName()}.In".toString()]])
                // add a definition for service in parameters
                ((Map) swaggerMap.definitions).put("${sd.getServiceName()}.In".toString(), sd.getJsonSchemaMapIn())
            }

            // add responses
            Map responses = ["403":[description:"Access Forbidden (no authz)"], "429":[description:"Too Many Requests (tarpit)"],
                             "500":[description:"General Error"]]
            if (sd.getOutParameterNames()) {
                responses.put("200", [description:'Success', schema:['$ref':"#/definitions/${sd.getServiceName()}.Out".toString()]])
                ((Map) swaggerMap.definitions).put("${sd.getServiceName()}.Out".toString(), sd.getJsonSchemaMapOut())
            }

            resourceMap.put(method, [summary:(serviceNode.attribute("displayName") ?: "${sd.verb} ${sd.noun}"),
                    description:(descNode?.text() ?: ''), parameters:parameters, responses:responses])
        }
        void toString(int level, StringBuilder sb) {
            for (int i=0; i < (level * 4); i++) sb.append(" ")
            sb.append(method).append(": service - ").append(serviceName).append("\n")
        }
    }

    static class MethodEntity extends MethodHandler {
        String entityName, masterName, operation
        MethodEntity(String method, Node entityNode, PathNode pathNode, ExecutionContextFactoryImpl ecfi) {
            super(method, pathNode, ecfi)
            entityName = entityNode.attribute("name")
            masterName = entityNode.attribute("masterName")
            operation = entityNode.attribute("operation")
        }
        RestResult run(List<String> pathList, ExecutionContext ec) {
            if (operation == 'one') {
                EntityFind ef = ec.entity.find(entityName).searchFormMap(ec.context, null, false)
                if (masterName) {
                    return new RestResult(ef.oneMaster(masterName), null)
                } else {
                    return new RestResult(ef.one(), null)
                }
            } else if (operation == 'list') {
                EntityFind ef = ec.entity.find(entityName).searchFormMap(ec.context, null, false)
                // we don't want to go overboard with these requests, never do an unlimited find, if no limit use 100
                if (!ef.getLimit()) ef.limit(100)

                int count = ef.count() as int
                int pageIndex = ef.getPageIndex()
                int pageSize = ef.getPageSize()
                int pageMaxIndex = ((count - 1) as BigDecimal).divide(pageSize as BigDecimal, 0, BigDecimal.ROUND_DOWN).intValue()
                int pageRangeLow = pageIndex * pageSize + 1
                int pageRangeHigh = (pageIndex * pageSize) + pageSize
                if (pageRangeHigh > count) pageRangeHigh = count
                Map<String, Object> headers = ['X-Total-Count':count, 'X-Page-Index':pageIndex, 'X-Page-Size':pageSize,
                    'X-Page-Max-Index':pageMaxIndex, 'X-Page-Range-Low':pageRangeLow, 'X-Page-Range-High':pageRangeHigh] as Map<String, Object>

                if (masterName) {
                    return new RestResult(ef.listMaster(masterName), headers)
                } else {
                    return new RestResult(ef.list(), headers)
                }
            } else if (operation == 'count') {
                EntityFind ef = ec.entity.find(entityName).searchFormMap(ec.context, null, false)
                long count = ef.count()
                Map<String, Object> headers = ['X-Total-Count':count] as Map<String, Object>
                return new RestResult(count, headers)
            } else if (operation in ['create', 'update', 'store', 'delete']) {
                Map result = ec.getService().sync().name(operation, entityName).parameters(ec.context).call()
                return new RestResult(result, null)
            } else {
                throw new IllegalArgumentException("Entity operation ${operation} not supported, must be one of: one, list, count, create, update, store, delete")
            }
        }

        void addToSwaggerMap(Map<String, Object> swaggerMap, Map<String, Map<String, Object>> resourceMap) {
            // TODO
        }
        void toString(int level, StringBuilder sb) {
            for (int i=0; i < (level * 4); i++) sb.append(" ")
            sb.append(method).append(": entity - ").append(operation).append(" - ").append(entityName)
            if (masterName) sb.append(" (master: ").append(masterName).append(")")
            sb.append("\n")
        }
    }

    static abstract class PathNode {
        ExecutionContextFactoryImpl ecfi

        Map<String, MethodHandler> methodMap = [:]
        IdNode idNode = null
        Map<String, ResourceNode> resourceMap = [:]

        String name
        PathNode parent
        String fullPath
        Set<String> pathParameters = new LinkedHashSet<String>()

        PathNode(Node node, PathNode parent, ExecutionContextFactoryImpl ecfi, boolean isId) {
            this.ecfi = ecfi
            this.parent = parent
            if (parent != null) this.pathParameters.addAll(parent.pathParameters)
            name = node.attribute("name")
            fullPath = isId ? "${parent?.fullPath ?: ''}/{${name}}" : "${parent?.fullPath ?: ''}/${name}"

            for (Object childObj in node.children()) {
                if (childObj instanceof Node) {
                    Node childNode = (Node) childObj
                    if (childNode.name() == "method") {
                        String method = childNode.attribute("type")

                        Object methodObj = childNode.children().first()
                        if (methodObj instanceof Node) {
                            Node methodNode = (Node) methodObj
                            if (methodNode.name() == "service") {
                                methodMap.put(method, new MethodService(method, methodNode, this, ecfi))
                            } else if (methodNode.name() == "entity") {
                                methodMap.put(method, new MethodEntity(method, methodNode, this, ecfi))
                            }
                        }
                    } else if (childNode.name() == "resource") {
                        ResourceNode resourceNode = new ResourceNode(childNode, this, ecfi)
                        resourceMap.put(resourceNode.name, resourceNode)
                    } else if (childNode.name() == "id") {
                        idNode = new IdNode(childNode, this, ecfi)
                    }
                }
            }
        }

        RestResult runByMethod(List<String> pathList, ExecutionContext ec) {
            String method = ec.web.getRequest().getMethod().toLowerCase()
            MethodHandler mh = methodMap.get(method)
            if (mh == null) throw new MethodNotSupportedException("Method ${method} not supported at ${pathList}")
            return mh.run(pathList, ec)
        }

        RestResult visitChildOrRun(List<String> pathList, int pathIndex, ExecutionContext ec) {
            // more in path? visit the next, otherwise run by request method
            int nextPathIndex = pathIndex + 1
            if (pathList.size() > nextPathIndex) {
                String nextPath = pathList[nextPathIndex]
                // first try resources
                ResourceNode rn = resourceMap.get(nextPath)
                if (rn != null) {
                    return rn.visit(pathList, nextPathIndex, ec)
                } else if (idNode != null) {
                    // no resource? if there is an idNode treat as ID
                    return idNode.visit(pathList, nextPathIndex, ec)
                } else {
                    // not a resource and no idNode, is a bad path
                    throw new ResourceNotFoundException("Resource ${nextPath} not valid, index ${pathIndex} in path ${pathList}; resources available are ${resourceMap.keySet()}")
                }
            } else {
                return runByMethod(pathList, ec)
            }
        }

        void addToSwaggerMap(Map<String, Object> swaggerMap) {
            // if we have method handlers add this, otherwise just do children
            if (methodMap) {
                Map<String, Map<String, Object>> rsMap = [:]
                for (MethodHandler mh in methodMap.values()) mh.addToSwaggerMap(swaggerMap, rsMap)
                ((Map) swaggerMap.paths).put(fullPath, rsMap)
            }
            // add the id node if there is one
            if (idNode != null) idNode.addToSwaggerMap(swaggerMap)
            // add any resource nodes there might be
            for (ResourceNode rn in resourceMap.values()) rn.addToSwaggerMap(swaggerMap)
        }

        void toStringChildren(int level, StringBuilder sb) {
            for (MethodHandler mh in methodMap.values()) mh.toString(level + 1, sb)
            for (ResourceNode rn in resourceMap.values()) rn.toString(level + 1, sb)
            if (idNode != null) idNode.toString(level + 1, sb)
        }

        abstract Object visit(List<String> pathList, int pathIndex, ExecutionContext ec)
    }
    static class ResourceNode extends PathNode {
        String displayName, description, version
        ResourceNode(Node node, PathNode parent, ExecutionContextFactoryImpl ecfi) {
            super(node, parent, ecfi, false)
            displayName = node.attribute("displayName")
            description = node.attribute("description")
            version = node.attribute("version")
        }
        RestResult visit(List<String> pathList, int pathIndex, ExecutionContext ec) {
            logger.info("Visit resource ${name}")
            // do nothing else but visit child or run here
            visitChildOrRun(pathList, pathIndex, ec)
        }
        String toString() {
            StringBuilder sb = new StringBuilder()
            toString(0, sb)
            return sb.toString()
        }
        void toString(int level, StringBuilder sb) {
            for (int i=0; i < (level * 4); i++) sb.append(" ")
            sb.append("/").append(name)
            if (displayName) sb.append(" - ").append(displayName)
            sb.append("\n")
            toStringChildren(level, sb)
        }
    }
    static class IdNode extends PathNode {
        IdNode(Node node, PathNode parent, ExecutionContextFactoryImpl ecfi) {
            super(node, parent, ecfi, true)
            pathParameters.add(name)
        }
        RestResult visit(List<String> pathList, int pathIndex, ExecutionContext ec) {
            logger.info("Visit id ${name}")
            // set ID value in context
            ec.context.put(name, pathList[pathIndex])
            // visit child or run here
            visitChildOrRun(pathList, pathIndex, ec)
        }
        void toString(int level, StringBuilder sb) {
            for (int i=0; i < (level * 4); i++) sb.append(" ")
            sb.append("/{").append(name).append("}\n")
            toStringChildren(level, sb)
        }
    }

    static class RestResult {
        Object responseObj
        Map<String, Object> headers = [:]
        RestResult(Object responseObj, Map<String, Object> headers) {
            this.responseObj = responseObj
            if (headers) this.headers.putAll(headers)
        }
        void setHeaders(HttpServletResponse response) {
            for (Map.Entry<String, Object> entry in headers) {
                Object value = entry.value
                if (value == null) continue
                if (value instanceof Integer) {
                    response.setIntHeader(entry.key, (int) value)
                } else if (value instanceof Date) {
                    response.setDateHeader(entry.key, value.getTime())
                } else {
                    response.setHeader(entry.key, value.toString())
                }
            }
        }
    }

    static class ResourceNotFoundException extends BaseException {
        ResourceNotFoundException(String str) { super(str) }
        // ResourceNotFoundException(String str, Throwable nested) { super(str, nested) }
    }
    static class MethodNotSupportedException extends BaseException {
        MethodNotSupportedException(String str) { super(str) }
        // MethodNotSupportedException(String str, Throwable nested) { super(str, nested) }
    }
}
