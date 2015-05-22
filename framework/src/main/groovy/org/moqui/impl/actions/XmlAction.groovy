/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.actions

import freemarker.core.Environment
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.BaseException
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.FtlNodeWrapper
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.ContextBinding

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
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
        // if (logger.isTraceEnabled()) logger.trace("Xml Action [${location}] groovyString: ${groovyString}")
        try {
            groovyClass = new GroovyClassLoader(Thread.currentThread().getContextClassLoader())
                    .parseClass(groovyString, StupidUtilities.cleanStringForJavaName(location))
        } catch (Throwable t) {
            logger.error("Error parsing groovy String at [${location}]: ${groovyString}")
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
            groovyClass = new GroovyClassLoader().parseClass(groovyString, StupidUtilities.cleanStringForJavaName(location))
        } catch (Throwable t) {
            logger.error("Error parsing groovy String at [${location}]: ${groovyString}")
            throw t
        }
    }

    protected static String makeGroovyString(ExecutionContextFactoryImpl ecfi, FtlNodeWrapper ftlNode, String location) {
        // transform XML to groovy
        String groovyText = null
        InputStream xmlStream = null
        try {
            Map root = ["xmlActionsRoot":ftlNode]

            Writer outWriter = new StringWriter()
            Environment env = ecfi.resourceFacade.xmlActionsScriptRunner.getXmlActionsTemplate()
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

        if (logger.isDebugEnabled()) logger.debug("Running groovy script: \n${writeGroovyWithLines()}\n")

        Script script = InvokerHelper.createScript(groovyClass, new ContextBinding(ec.context))
        try {
            Object result = script.run()
            return result
        } catch (Throwable t) {
            logger.error("Error running groovy script (${t.toString()}): \n${writeGroovyWithLines()}\n")
            throw t
        }
    }

    String writeGroovyWithLines() {
        StringBuilder groovyWithLines = new StringBuilder()
        int lineNo = 1
        for (String line in groovyString.split("\n")) groovyWithLines.append(lineNo++).append(" : ").append(line).append("\n")
        return groovyWithLines.toString()
    }

    boolean checkCondition(ExecutionContext ec) { return run(ec) as boolean }
}
