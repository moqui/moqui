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

import freemarker.ext.dom.NodeModel

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.actions.XmlAction

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.xml.sax.InputSource
import org.moqui.impl.FtlNodeWrapper
import org.moqui.impl.context.ContextStack

class ScreenWidgets {
    protected final static Logger logger = LoggerFactory.getLogger(XmlAction.class)

    protected Node widgetsNode
    protected FtlNodeWrapper widgetsFtlNode
    protected String location

    ScreenWidgets(ExecutionContextFactoryImpl ecfi, Node widgetsNode, String location) {
        this.widgetsNode = widgetsNode
        this.location = location
        this.widgetsFtlNode = FtlNodeWrapper.wrapNode(widgetsNode)
    }

    void render(ScreenRenderImpl sri) {
        ContextStack cs = (ContextStack) sri.ec.context
        try {
            cs.push()
            cs.sri = sri
            cs.widgetsNode = widgetsFtlNode
            sri.template.createProcessingEnvironment(cs, sri.writer).process()
        } finally {
            cs.pop()
        }
    }
}
