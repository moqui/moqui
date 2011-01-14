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

import org.moqui.context.ExecutionContext
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.codehaus.groovy.runtime.InvokerHelper
import org.xml.sax.InputSource
import freemarker.core.Environment
import freemarker.template.Template
import freemarker.ext.beans.BeansWrapper
import freemarker.template.Configuration
import freemarker.template.TemplateException
import org.xml.sax.SAXParseException
import org.moqui.BaseException

class XmlAction {
    protected final static Logger logger = LoggerFactory.getLogger(XmlAction.class)

    protected final static String templateLocation = "classpath://template/XmlActions.groovy.ftl"
    protected final static BeansWrapper defaultWrapper = BeansWrapper.getDefaultInstance()
    protected final static Configuration defaultConfig = makeConfiguration()
    public static Configuration makeConfiguration() {
        Configuration newConfig = new Configuration()
        newConfig.setObjectWrapper(defaultWrapper)
        newConfig.setSharedVariable("Static", defaultWrapper.getStaticModels())
        return newConfig
    }

    protected final Class groovyClass

    XmlAction(ExecutionContextFactoryImpl ecfi, String xmlText, String location) {
        // transform XML to groovy
        String groovyText = null
        InputStream xmlStream = null
        Reader templateReader = null
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

            InputStream templateStream = ecfi.resourceFacade.getLocationStream(templateLocation)
            templateReader = new InputStreamReader(templateStream)
            Template template = new Template(templateLocation, templateReader, defaultConfig)

            Writer outWriter = new StringWriter()
            Environment env = template.createProcessingEnvironment(root, (Writer) outWriter)
            env.process()

            groovyText = outWriter.toString()
        } catch (SAXParseException e) {
            logger.error("Error reading XML actions from [${location}], text: ${xmlText}")
            throw new BaseException("Error reading XML actions from [${location}]", e)
        } finally {
            if (xmlStream) xmlStream.close()
            if (templateReader) templateReader.close()
        }

        logger.info("xml-actions at [${location}] produced groovy script:\n${groovyText}")

        // parse groovy
        groovyClass = new GroovyClassLoader().parseClass(groovyText, location)
    }

    /** Run the XML actions in the current context of the ExecutionContext */
    Map<String, Object> run(ExecutionContext ec) {
        Script script = InvokerHelper.createScript(groovyClass, new Binding(ec.context))
        Object result = script.run()
        if (result instanceof Map) {
            return (Map<String, Object>) result
        } else if (ec.context.get("result")) {
            return (Map<String, Object>) ec.context.get("result")
        } else {
            return null
        }
    }
}
