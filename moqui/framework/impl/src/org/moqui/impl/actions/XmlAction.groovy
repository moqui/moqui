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

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

class XmlAction {
    protected final static Logger logger = LoggerFactory.getLogger(XmlAction.class)

    // ============ Static Fields for the Template (same one used over an over, so just always keep it here)
    protected final static String templateLocation = "template/XmlActions.groovy.ftl"
    protected final static Template template = makeTemplate()
    protected static Template makeTemplate() {
        Template newTemplate = null
        Reader templateReader = null
        try {
            URL templateUrl = XmlAction.class.getClassLoader().getResource(templateLocation)
            if (!templateUrl) templateUrl = ClassLoader.getSystemResource(templateLocation)
            InputStream templateStream = templateUrl.newInputStream()
            templateReader = new InputStreamReader(templateStream)
            newTemplate = new Template(templateLocation, templateReader, makeConfiguration())
        } catch (Exception e) {
            logger.error("Error while initializing XMLActions template at [${templateLocation}]", e)
        } finally {
            if (templateReader) templateReader.close()
        }
        return newTemplate
    }
    protected static Configuration makeConfiguration() {
        BeansWrapper defaultWrapper = BeansWrapper.getDefaultInstance()
        Configuration newConfig = new Configuration()
        newConfig.setObjectWrapper(defaultWrapper)
        newConfig.setSharedVariable("Static", defaultWrapper.getStaticModels())
        return newConfig
    }

    /** The Groovy class compiled from the script transformed from the XML actions text using the FTL template. */
    protected final Class groovyClass
    protected final String groovyString

    XmlAction(ExecutionContextFactoryImpl ecfi, Node xmlNode, String location) {
        StringWriter sw = new StringWriter()
        XmlNodePrinter xnp = new XmlNodePrinter(new PrintWriter(sw))
        xnp.print(xmlNode)
        groovyString = makeGroovyString(ecfi, sw.toString(), location)
        groovyClass = makeGroovyClass(ecfi, groovyString, location)
    }

    XmlAction(ExecutionContextFactoryImpl ecfi, String xmlText, String location) {
        groovyString = makeGroovyString(ecfi, xmlText, location)
        groovyClass = makeGroovyClass(ecfi, groovyString, location)
    }

    protected String makeGroovyString(ExecutionContextFactoryImpl ecfi, String xmlText, String location) {
        // transform XML to groovy
        String groovyText = null
        InputStream xmlStream = null
        try {
            Map root = new HashMap()
            InputSource xmlInputSource
            if (!xmlText) {
                xmlStream = ecfi.resourceFacade.getLocationStream(location)
                xmlInputSource = new InputSource(xmlStream)
            } else {
                xmlInputSource = new InputSource(new StringReader(xmlText))
            }
            root.put("doc", freemarker.ext.dom.NodeModel.parse(xmlInputSource))

            Writer outWriter = new StringWriter()
            Environment env = template.createProcessingEnvironment(root, (Writer) outWriter)
            env.process()

            groovyText = outWriter.toString()
        } catch (SAXParseException e) {
            logger.error("Error reading XML actions from [${location}], text: ${xmlText}")
            throw new BaseException("Error reading XML actions from [${location}]", e)
        } finally {
            if (xmlStream) xmlStream.close()
        }

        if (logger.traceEnabled) logger.trace("xml-actions at [${location}] produced groovy script:\n${groovyText}")
        return groovyText
    }

    protected Class makeGroovyClass(ExecutionContextFactoryImpl ecfi, String groovyText, String location) {
        // parse groovy
        return new GroovyClassLoader().parseClass(groovyText, location)
    }

    /** Run the XML actions in the current context of the ExecutionContext */
    Object run(ExecutionContext ec) {
        if (!groovyClass) throw new IllegalStateException("No Groovy class in place for XML actions, look earlier in log for the error in init")

        Script script = InvokerHelper.createScript(groovyClass, new Binding(ec.context))
        try {
            return script.run()
        } catch (Exception e) {
            logger.error("Error running groovy script [${groovyString}]", e)
            throw e
        }
    }

    boolean checkCondition(ExecutionContext ec) {
        if (!groovyClass) throw new IllegalStateException("No Groovy class in place for XML actions, look earlier in log for the error in init")

        Script script = InvokerHelper.createScript(groovyClass, new Binding(ec.context))
        try {
            // NOTE: not sure if this is the proper way to evaluate the compiled expression... will see
            return script.run() as boolean
        } catch (Exception e) {
            logger.error("Error running groovy script [${groovyString}]", e)
            throw e
        }
    }
}
