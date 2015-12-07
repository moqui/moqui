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
import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityFind
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class RestApi {
    protected final static Logger logger = LoggerFactory.getLogger(RestApi.class)

    protected ExecutionContextFactoryImpl ecfi
    Map<String, ResourceNode> rootResourceMap = [:]

    RestApi(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi

        // TODO: find *.rest.xml files in component/service directories, put in rootResourceMap

    }

    Object run(List<String> pathList, ExecutionContext ec) {
        if (!pathList) throw new ResourceNotFoundException("Cannot run REST service with no path")
        String firstPath = pathList[0]
        ResourceNode resourceNode = rootResourceMap.get(firstPath)
        if (resourceNode == null) throw new ResourceNotFoundException("Root resource not found with name ${firstPath}")
        return resourceNode.visit(pathList, 1, ec)
    }

    static abstract class MethodHandler {
        String method
        MethodHandler(String method) { this.method = method }
        abstract Object run(List<String> pathList, ExecutionContext ec)
    }
    static class MethodService extends MethodHandler {
        String serviceName
        MethodService(String method, Node serviceNode) {
            super(method)
            serviceName = serviceNode.attribute("name")
        }
        Object run(List<String> pathList, ExecutionContext ec) {
            Map result = ec.getService().sync().name(serviceName).parameters(ec.context).call()
            return result
        }
    }
    static class MethodEntity extends MethodHandler {
        String entityName, masterName, operation
        MethodEntity(String method, Node entityNode) {
            super(method)
            entityName = entityNode.attribute("name")
            masterName = entityNode.attribute("masterName")
            operation = entityNode.attribute("operation")
        }
        Object run(List<String> pathList, ExecutionContext ec) {
            if (operation == 'one') {
                EntityFind ef = ec.entity.find(entityName).searchFormMap(ec.context, null, false)
                if (masterName) {
                    return ef.oneMaster(masterName)
                } else {
                    return ef.one()
                }
            } else if (operation == 'list') {
                EntityFind ef = ec.entity.find(entityName).searchFormMap(ec.context, null, false)
                // we don't want to go overboard with these requests, never do an unlimited find, if no limit use 100
                if (!ef.getLimit()) ef.limit(100)
                if (masterName) {
                    return ef.listMaster(masterName)
                } else {
                    return ef.list()
                }
            } else if (operation == 'count') {
                EntityFind ef = ec.entity.find(entityName).searchFormMap(ec.context, null, false)
                return ef.count()
            } else if (operation in ['create', 'update', 'store', 'delete']) {
                Map result = ec.getService().sync().name(operation, entityName).parameters(ec.context).call()
                return result
            } else {
                throw new IllegalArgumentException("Entity operation ${operation} not supported, must be one of: one, list, count, create, update, store, delete")
            }
        }
    }

    static abstract class PathNode {
        Map<String, MethodHandler> methodMap = [:]
        Map<String, ResourceNode> resourceMap = [:]
        IdNode idNode = null

        PathNode(Node node) {
            for (Object childObj in node.children()) {
                if (childObj instanceof Node) {
                    Node childNode = (Node) childObj
                    if (childNode.name() == "method") {
                        String method = childNode.attribute("type")

                        Object methodObj = childNode.children().first()
                        if (methodObj instanceof Node) {
                            Node methodNode = (Node) methodObj
                            if (methodNode.name() == "service") {
                                methodMap.put(method, new MethodService(method, methodNode))
                            } else if (methodNode.name() == "entity") {
                                methodMap.put(method, new MethodEntity(method, methodNode))
                            }
                        }
                    } else if (childNode.name() == "resource") {
                        ResourceNode resourceNode = new ResourceNode(childNode)
                        resourceMap.put(resourceNode.name, resourceNode)
                    } else if (childNode.name() == "id") {
                        idNode = new IdNode(childNode)
                    }
                }
            }
        }

        Object runByMethod(List<String> pathList, ExecutionContext ec) {
            String method = ec.web.getRequest().getMethod().toLowerCase()
            MethodHandler mh = methodMap.get(method)
            if (mh == null) throw new MethodNotSupportedException("Method ${method} not supported at ${pathList}")
            return mh.run(pathList, ec)
        }

        Object visitChildOrRun(List<String> pathList, int pathIndex, ExecutionContext ec) {
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

        abstract Object visit(List<String> pathList, int pathIndex, ExecutionContext ec)
    }
    static class ResourceNode extends PathNode {
        String name, displayName, description
        ResourceNode(Node node) {
            super(node)
            name = node.attribute("name")
            displayName = node.attribute("displayName")
            description = node.attribute("description")
        }
        Object visit(List<String> pathList, int pathIndex, ExecutionContext ec) {
            // do nothing else but visit child or run here
            visitChildOrRun(pathList, pathIndex, ec)
        }
    }
    static class IdNode extends PathNode {
        String name
        IdNode(Node node) {
            super(node)
            name = node.attribute("name")
        }
        Object visit(List<String> pathList, int pathIndex, ExecutionContext ec) {
            // set ID value in context
            ec.context.put(name, pathList[pathIndex])
            // visit child or run here
            visitChildOrRun(pathList, pathIndex, ec)
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
