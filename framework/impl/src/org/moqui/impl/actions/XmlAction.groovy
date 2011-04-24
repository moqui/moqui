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
package org.moqui.impl.actions

import freemarker.core.Environment
import freemarker.template.Template
import freemarker.ext.beans.BeansWrapper
import freemarker.template.Configuration

import org.codehaus.groovy.runtime.InvokerHelper
import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.FtlNodeWrapper
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class XmlAction {
    protected final static Logger logger = LoggerFactory.getLogger(XmlAction.class)

    /** The Groovy class compiled from the script transformed from the XML actions text using the FTL template. */
    protected final Class groovyClass
    protected final String groovyString
    protected final String location

    XmlAction(ExecutionContextFactoryImpl ecfi, Node xmlNode, String location) {
        this.location = location
        FtlNodeWrapper ftlNode = FtlNodeWrapper.wrapNode(xmlNode)
        groovyString = makeGroovyString(ecfi, ftlNode, location)
        // logger.info("Xml Action [${location}] groovyString: ${groovyString}")
        try {
            groovyClass = new GroovyClassLoader(Thread.currentThread().getContextClassLoader()).parseClass(groovyString, location)
        } catch (Throwable t) {
            logger.error("Error parsing groovy String: ${groovyString}")
            throw t
        }
    }

    XmlAction(ExecutionContextFactoryImpl ecfi, String xmlText, String location) {
        this.location = location
        FtlNodeWrapper ftlNode
        if (xmlText) {
            ftlNode = FtlNodeWrapper.makeFromText(xmlText)
        } else {
            ftlNode = FtlNodeWrapper.makeFromText(ecfi.resourceFacade.getLocationText(location, false))
        }
        groovyString = makeGroovyString(ecfi, ftlNode, location)
        try {
            groovyClass = new GroovyClassLoader().parseClass(groovyString, location)
        } catch (Throwable t) {
            logger.error("Error parsing groovy String: ${groovyString}")
            throw t
        }
    }

    protected String makeGroovyString(ExecutionContextFactoryImpl ecfi, FtlNodeWrapper ftlNode, String location) {
        // transform XML to groovy
        String groovyText = null
        InputStream xmlStream = null
        try {
            Map root = ["xmlActionsRoot":ftlNode]

            Writer outWriter = new StringWriter()
            Environment env = ecfi.resourceFacade.getXmlActionsTemplate()
                    .createProcessingEnvironment(root, (Writer) outWriter)
            env.process()

            groovyText = outWriter.toString()
        } catch (Exception e) {
            logger.error("Error reading XML actions from [${location}], text: ${ftlNode.toString()}")
            throw new BaseException("Error reading XML actions from [${location}]", e)
        } finally {
            if (xmlStream) xmlStream.close()
        }

        if (logger.traceEnabled) logger.trace("xml-actions at [${location}] produced groovy script:\n${groovyText}\nFrom ftlNode:${ftlNode}")
        return groovyText
    }

    /** Run the XML actions in the current context of the ExecutionContext */
    Object run(ExecutionContext ec) {
        if (!groovyClass) throw new IllegalStateException("No Groovy class in place for XML actions, look earlier in log for the error in init")

        Script script = InvokerHelper.createScript(groovyClass, new Binding(ec.context))
        try {
            Object result = script.run()
            return result
        } catch (Throwable t) {
            logger.error("Error running groovy script [${groovyString}]: ${t.toString()}")
            throw t
        }
    }

    boolean checkCondition(ExecutionContext ec) { return run(ec) as boolean }
}
