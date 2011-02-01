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

class ScreenWidgets {
    protected final static Logger logger = LoggerFactory.getLogger(XmlAction.class)

    protected Node widgetsNode
    protected NodeModel widgetsNodeModel
    protected String location

    ScreenWidgets(ExecutionContextFactoryImpl ecfi, Node widgetsNode, String location) {
        this.widgetsNode = widgetsNode
        this.location = location

        // translate the Groovy Node into an FTL NodeModel going through text
        StringWriter sw = new StringWriter()
        new XmlNodePrinter(new PrintWriter(sw)).print(widgetsNode)
        this.widgetsNodeModel = freemarker.ext.dom.NodeModel.parse(new InputSource(new StringReader(sw.toString())))
    }

    void render(ScreenRenderImpl sri) {
        Map root = new HashMap(sri.ec.context)
        root.sri = sri
        root.ec = sri.ec
        root.widgetsNode = widgetsNodeModel
        sri.template.createProcessingEnvironment(root, sri.writer).process()
    }
}
