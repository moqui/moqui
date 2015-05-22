/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.screen

import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.impl.FtlNodeWrapper
import org.moqui.context.ContextStack

@CompileStatic
class ScreenWidgets {
    protected final static Logger logger = LoggerFactory.getLogger(ScreenWidgets.class)

    protected Node widgetsNode
    protected FtlNodeWrapper widgetsFtlNode
    protected String location

    ScreenWidgets(Node widgetsNode, String location) {
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
