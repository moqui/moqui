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
package org.moqui.impl.screen

import org.moqui.context.ContextStack
import org.moqui.context.ExecutionContext
import org.moqui.impl.StupidUtilities
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ScreenTree {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenTree.class)

    protected ExecutionContextFactoryImpl ecfi
    protected ScreenDefinition sd
    protected Node treeNode
    protected String location

    // protected Map<String, ScreenDefinition.ParameterItem> parameterByName = [:]
    protected Map<String, TreeNode> nodeByName = [:]
    protected List<TreeSubNode> subNodeList = []

    ScreenTree(ExecutionContextFactoryImpl ecfi, ScreenDefinition sd, Node treeNode, String location) {
        this.ecfi = ecfi
        this.sd = sd
        this.treeNode = treeNode
        this.location = location

        /* not needed, handled by sri.makeUrlByType() treating the tree element as a parameter parent node:
        // parameter
        for (Node parameterNode in treeNode."parameter")
            parameterByName.put((String) parameterNode."@name", new ScreenDefinition.ParameterItem(parameterNode, location))
        */

        // prep tree-node
        for (Node treeNodeNode in treeNode."tree-node")
            nodeByName.put(treeNodeNode."@name", new TreeNode(this, treeNodeNode, location + ".node." + treeNodeNode."@name"))

        // prep tree-sub-node
        for (Node treeSubNodeNode in treeNode."tree-sub-node")
            subNodeList.add(new TreeSubNode(this, treeSubNodeNode, location + ".subnode." + treeSubNodeNode."@node-name"))
    }

    /* not needed, handled by sri.makeUrlByType() treating the tree element as a parameter parent node:
    List<String> setAllParameters(ExecutionContext ec) {
        List<String> parameterNames = []
        // put parameters in the context
        for (ScreenDefinition.ParameterItem pi in parameterByName.values()) {
            Object value = pi.getValue(ec)
            if (value != null) ec.getContext().put(pi.getName(), value)
            parameterNames.add(pi.getName())
        }
        return parameterNames
    }
    */

    void sendSubNodeJson() {
        // NOTE: This method is very specific to jstree

        ExecutionContextImpl eci = ecfi.getEci()
        ContextStack cs = eci.getContext()

        List outputNodeList = []

        logger.warn("========= treeNodeId = ${cs.get("treeNodeId")}")
        // if this is the root node get the main tree sub-nodes, otherwise find the node and use its sub-nodes
        List<TreeSubNode> currentSubNodeList
        if (cs.get("treeNodeId") == "#") {
            currentSubNodeList = subNodeList
        } else {
            // TODO: how to find the active node's name? pass in through JS somehow...
            currentSubNodeList = nodeByName.values().first().subNodeList
        }

        for (TreeSubNode tsn in currentSubNodeList) {
            // check condition
            if (tsn.condition != null && !tsn.condition.checkCondition(eci)) continue
            // run actions
            if (tsn.actions != null) tsn.actions.run(eci)

            TreeNode tn = nodeByName.get(tsn.treeSubNodeNode."@node-name")

            // iterate over the list and add a response node for each entry
            String nodeListName = tsn.treeSubNodeNode."@list" ?: "nodeList"
            logger.warn("========= nodeListName = ${nodeListName} :: ${cs.get(nodeListName)}")
            Iterator i = cs.get(nodeListName).iterator()
            int index = 0
            while (i.hasNext()) {
                Object nodeListEntry = i.next()
                logger.warn("========= nodeListEntry = ${nodeListEntry}")
                cs.push()

                try {
                    cs.put("nodeList_entry", nodeListEntry)
                    cs.put("nodeList_index", index)
                    cs.put("nodeList_has_next", i.hasNext())

                    // check condition
                    if (tn.condition != null && !tn.condition.checkCondition(eci)) continue
                    // run actions
                    if (tn.actions != null) tn.actions.run(eci)

                    String id = eci.getResource().evaluateStringExpand((String) tn.linkNode."@id", tn.location + ".id")
                    String text = eci.getResource().evaluateStringExpand((String) tn.linkNode."@text", tn.location + ".text")
                    ScreenUrlInfo urlInfo = cs.get("sri").makeUrlByTypeGroovyNode(tn.linkNode."@url", tn.linkNode."@url-type" ?: "transition", tn.linkNode, tn.linkNode."@expand-transition-url" ?: "true")

                    boolean hasChildren = true // TODO
                    Map subNodeMap = [id:id, text:text, children:hasChildren, a_attr:[href:urlInfo.getUrlWithParams()]]
                    // TODO: for root node add children for treeOpenPath
                    outputNodeList.add(subNodeMap)
                    /* structure of JSON object from jstree docs:
                        {
                          id          : "string" // will be autogenerated if omitted
                          text        : "string" // node text
                          icon        : "string" // string for custom
                          state       : {
                            opened    : boolean  // is the node open
                            disabled  : boolean  // is the node disabled
                            selected  : boolean  // is the node selected
                          },
                          children    : []  // array of strings or objects
                          li_attr     : {}  // attributes for the generated LI node
                          a_attr      : {}  // attributes for the generated A node
                        }
                     */
                } finally {
                    cs.pop()
                }
            }
        }

        logger.warn("========= outputNodeList = ${outputNodeList}")
        eci.getWeb().sendJsonResponse(outputNodeList)
    }

    static class TreeNode {
        protected ScreenTree screenTree
        protected Node treeNodeNode
        protected String location

        protected XmlAction condition = null
        protected XmlAction actions = null
        protected Node linkNode = null
        protected List<TreeSubNode> subNodeList = []

        TreeNode(ScreenTree screenTree, Node treeNodeNode, String location) {
            this.screenTree = screenTree
            this.treeNodeNode = treeNodeNode
            this.location = location
            this.linkNode = treeNodeNode.link[0]

            // prep condition
            if (treeNodeNode.condition && treeNodeNode.condition[0].children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(screenTree.ecfi, (Node) treeNodeNode."condition"[0].children()[0], location + ".condition")
            }
            // prep actions
            if (treeNodeNode.actions) actions = new XmlAction(screenTree.ecfi, (Node) treeNodeNode."actions"[0], location + ".actions")

            // prep tree-sub-node
            for (Node treeSubNodeNode in treeNodeNode."tree-sub-node")
                subNodeList.add(new TreeSubNode(screenTree, treeSubNodeNode, location + ".subnode." + treeSubNodeNode."@node-name"))
        }
    }

    static class TreeSubNode {
        protected ScreenTree screenTree
        protected Node treeSubNodeNode
        protected String location

        protected XmlAction condition = null
        protected XmlAction actions = null

        TreeSubNode(ScreenTree screenTree, Node treeSubNodeNode, String location) {
            this.screenTree = screenTree
            this.treeSubNodeNode = treeSubNodeNode
            this.location = location

            // prep condition
            if (treeSubNodeNode.condition && treeSubNodeNode.condition[0].children()) {
                // the script is effectively the first child of the condition element
                condition = new XmlAction(screenTree.ecfi, (Node) treeSubNodeNode."condition"[0].children()[0], location + ".condition")
            }
            // prep actions
            if (treeSubNodeNode.actions) actions = new XmlAction(screenTree.ecfi, (Node) treeSubNodeNode."actions"[0], location + ".actions")
        }
    }
}
