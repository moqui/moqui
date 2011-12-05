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
package org.moqui.impl.context

import javax.activation.DataSource
import javax.activation.MimetypesFileTypeMap
import javax.jcr.Repository
import javax.jcr.Session
import javax.naming.InitialContext

import org.apache.commons.mail.ByteArrayDataSource
import org.apache.jackrabbit.commons.JcrUtils

import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.context.TemplateRenderer
import org.moqui.context.ResourceFacade
import org.moqui.context.ResourceReference
import org.moqui.context.ScriptRunner
import org.moqui.impl.StupidUtilities
import org.moqui.impl.context.renderer.FtlTemplateRenderer
import org.moqui.impl.context.runner.XmlActionsScriptRunner
import java.lang.reflect.Constructor

public class ResourceFacadeImpl implements ResourceFacade {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ResourceFacadeImpl.class)

    protected final MimetypesFileTypeMap mimetypesFileTypeMap = new MimetypesFileTypeMap()

    protected final ExecutionContextFactoryImpl ecfi

    final FtlTemplateRenderer ftlTemplateRenderer
    final XmlActionsScriptRunner xmlActionsScriptRunner

    protected final Cache scriptGroovyExpressionCache
    protected final Cache textLocationCache

    protected final Map<String, Class> resourceReferenceClasses = new HashMap()
    protected final Map<String, TemplateRenderer> templateRenderers = new HashMap()
    protected final Map<String, ScriptRunner> scriptRunners = new HashMap()

    protected final Map<String, Repository> contentRepositories = new HashMap()
    protected final Map<String, String> contentRepositoryWorkspaces = new HashMap()

    protected final ThreadLocal<Map<String, Session>> contentSessions = new ThreadLocal<Map<String, Session>>()

    ResourceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi

        this.ftlTemplateRenderer = new FtlTemplateRenderer()
        this.ftlTemplateRenderer.init(ecfi)

        this.xmlActionsScriptRunner = new XmlActionsScriptRunner()
        this.xmlActionsScriptRunner.init(ecfi)

        this.textLocationCache = ecfi.getCacheFacade().getCache("resource.text.location")

        this.scriptGroovyExpressionCache = ecfi.getCacheFacade().getCache("resource.groovy.expression")

        // Setup resource reference classes
        for (Node rrNode in ecfi.confXmlRoot."resource-facade"[0]."resource-reference") {
            Class rrClass = this.getClass().getClassLoader().loadClass(rrNode."@class")
            resourceReferenceClasses.put(rrNode."@scheme", rrClass)
        }

        // Setup template renderers
        for (Node templateRendererNode in ecfi.confXmlRoot."resource-facade"[0]."template-renderer") {
            TemplateRenderer tr = (TemplateRenderer) this.getClass().getClassLoader().loadClass(templateRendererNode."@class").newInstance()
            templateRenderers.put(templateRendererNode."@extension", tr.init(ecfi))
        }

        // Setup script runners
        for (Node scriptRunnerNode in ecfi.confXmlRoot."resource-facade"[0]."script-runner") {
            ScriptRunner sr = (ScriptRunner) this.getClass().getClassLoader().loadClass(scriptRunnerNode."@class").newInstance()
            scriptRunners.put(scriptRunnerNode."@extension", sr.init(ecfi))
        }

        // Setup content repositories
        for (Node repositoryNode in ecfi.confXmlRoot."repository-list"[0]."repository") {
            try {
                if (repositoryNode."@type" == "davex" || repositoryNode."@type" == "rmi") {
                    Repository repository = JcrUtils.getRepository((String) repositoryNode."@location")
                    contentRepositories.put(repositoryNode."@name", repository)
                } else if (repositoryNode."@type" == "jndi") {
                    InitialContext ic = new InitialContext()
                    Repository repository = (Repository) ic.lookup((String) repositoryNode."@location")
                    contentRepositories.put(repositoryNode."@name", repository)
                } else if (repositoryNode."@type" == "local") {
                    throw new IllegalArgumentException("The local type content repository is not yet supported, pending research into API support for the concept")
                }
                if (repositoryNode."@workspace") contentRepositoryWorkspaces.put(repositoryNode."@name", repositoryNode."@workspace")
            } catch (Exception e) {
                logger.error("Error getting JCR content repository with name [${repositoryNode."@name"}], is of type [${repositoryNode."@type"}] at location [${repositoryNode."@location"}]: ${e.toString()}")
            }
        }
    }

    void destroyAllInThread() {
        Map<String, Session> sessionMap = contentSessions.get()
        if (sessionMap) for (Session openSession in sessionMap.values()) openSession.logout()
        contentSessions.remove()
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }
    Map<String, TemplateRenderer> getTemplateRenderers() { return templateRenderers }

    Repository getContentRepository(String name) { return contentRepositories.get(name) }

    /** Get the active JCR Session for the context/thread, making sure it is live, and make one if needed. */
    Session getContentRepositorySession(String name) {
        Map<String, Session> sessionMap = contentSessions.get()
        if (sessionMap == null) {
            sessionMap = new HashMap()
            contentSessions.set(sessionMap)
        }
        Session newSession = sessionMap[name]
        if (newSession != null) {
            if (newSession.isLive()) {
                return newSession
            } else {
                sessionMap.remove(name)
                newSession = null
            }
        }

        Repository rep = contentRepositories[name]
        if (!rep) return null
        if (contentRepositoryWorkspaces[name]) {
            newSession = rep.login(contentRepositoryWorkspaces[name])
        } else {
            newSession = rep.login()
        }

        if (newSession != null) sessionMap.put(name, newSession)
        return newSession
    }

    /** @see org.moqui.context.ResourceFacade#getLocationReference(String) */
    ResourceReference getLocationReference(String location) {
        if (location == null) return null
        String scheme = "file"
        // how to get the scheme for windows? the Java URI class doesn't like spaces, the if we look for the first ":"
        //    it may be a drive letter instead of a scheme/protocol
        if (location.contains(":")) {
            String prefix = location.substring(0, location.indexOf(":"))
            if (!prefix.contains("/") && prefix.length() > 2) scheme = prefix
        }

        Class rrClass = resourceReferenceClasses.get(scheme)
        if (!rrClass) throw new IllegalArgumentException("Prefix (scheme) not supported for location [${location}]")

        ResourceReference rr = (ResourceReference) rrClass.newInstance()
        return rr.init(location, ecfi.executionContext)
    }

    /** @see org.moqui.context.ResourceFacade#getLocationStream(String) */
    InputStream getLocationStream(String location) {
        ResourceReference rr = getLocationReference(location)
        if (rr == null) return null
        return rr.openStream()
    }

    String getLocationText(String location, boolean cache) {
        if (cache && textLocationCache.containsKey(location)) return (String) textLocationCache.get(location)
        InputStream locStream = getLocationStream(location)
        if (locStream == null) logger.info("Cannot get text, no resource found at location [${location}]")
        String text = StupidUtilities.getStreamText(locStream)
        if (cache) textLocationCache.put(location, text)
        return text
    }

    DataSource getLocationDataSource(String location) {
        ResourceReference fileResourceRef = getLocationReference(location)

        TemplateRenderer tr = getTemplateRendererByLocation(fileResourceRef.location)

        String fileName = fileResourceRef.fileName
        // strip template extension(s) to avoid problems with trying to find content types based on them
        String fileContentType = getContentType(tr != null ? tr.stripTemplateExtension(fileName) : fileName)

        boolean isBinary = isBinaryContentType(fileContentType)

        if (isBinary) {
            return new ByteArrayDataSource(fileResourceRef.openStream(), fileContentType)
        } else {
            // not a binary object (hopefully), get the text and pass it over
            if (tr != null) {
                StringWriter sw = new StringWriter()
                tr.render(fileResourceRef.location, sw)
                return new ByteArrayDataSource(sw.toString(), fileContentType)
            } else {
                // no renderer found, just grab the text (cached) and throw it to the writer
                String text = getLocationText(fileResourceRef.location, true)
                return new ByteArrayDataSource(text, fileContentType)
            }
        }
    }

    /** @see org.moqui.context.ResourceFacade#renderTemplateInCurrentContext(String, Writer) */
    void renderTemplateInCurrentContext(String location, Writer writer) {
        TemplateRenderer tr = getTemplateRendererByLocation(location)
        if (tr != null) {
            tr.render(location, writer)
        } else {
            // no renderer found, just grab the text and throw it to the writer
            String text = getLocationText(location, true)
            if (text) writer.write(text)
        }
    }

    TemplateRenderer getTemplateRendererByLocation(String location) {
        // match against extension for template renderer, with as many dots that match as possible (most specific match)
        int mostDots = 0
        TemplateRenderer tr = null
        for (Map.Entry<String, TemplateRenderer> trEntry in templateRenderers.entrySet()) {
            String ext = trEntry.getKey()
            if (location.endsWith(ext)) {
                int dots = StupidUtilities.countChars(ext, (char) '.')
                if (dots > mostDots) {
                    mostDots = dots
                    tr = trEntry.getValue()
                }
            }
        }
        return tr
    }

    /** @see org.moqui.context.ResourceFacade#runScriptInCurrentContext(String, String) */
    Object runScriptInCurrentContext(String location, String method) {
        ExecutionContext ec = ecfi.executionContext
        String extension = location.substring(location.lastIndexOf("."))
        ScriptRunner sr = this.scriptRunners.get(extension)

        if (sr == null) throw new IllegalArgumentException("Cannot run script [${location}], unknown extension.")

        return sr.run(location, method, ec)
    }

    /** @see org.moqui.context.ResourceFacade#evaluateCondition(String, String) */
    boolean evaluateCondition(String expression, String debugLocation) {
        if (!expression) return false
        try {
            Script script = getGroovyScript(expression)
            Object result = script.run()
            return result as boolean
        } catch (Exception e) {
            throw new IllegalArgumentException("Error in condition [${expression}] from [${debugLocation}]", e)
        }
    }

    /** @see org.moqui.context.ResourceFacade#evaluateContextField(String, String) */
    Object evaluateContextField(String expression, String debugLocation) {
        if (!expression) return null
        try {
            Script script = getGroovyScript(expression)
            Object result = script.run()
            return result
        } catch (Exception e) {
            throw new IllegalArgumentException("Error in field expression [${expression}] from [${debugLocation}]", e)
        }
    }

    /** @see org.moqui.context.ResourceFacade#evaluateStringExpand(String, String) */
    String evaluateStringExpand(String inputString, String debugLocation) {
        if (!inputString) return ""

        // always localize string before expanding
        if (inputString.length() < 256) inputString = ecfi.l10nFacade.getLocalizedMessage(inputString)

        String expression = '"""' + inputString + '"""'
        try {
            Script script = getGroovyScript(expression)
            Object result = script.run()
            return result as String
        } catch (Exception e) {
            throw new IllegalArgumentException("Error in string expression [${expression}] from [${debugLocation}]", e)
        }
    }

    Script getGroovyScript(String expression) {
        Class groovyClass = (Class) this.scriptGroovyExpressionCache.get(expression)
        if (groovyClass == null) {
            groovyClass = new GroovyClassLoader().parseClass(expression)
            this.scriptGroovyExpressionCache.put(expression, groovyClass)
        }
        // NOTE: consider keeping the binding somewhere, like in the ExecutionContext to avoid creating repeatedly
        Script script = InvokerHelper.createScript(groovyClass, new Binding(ecfi.executionContext.context))
        return script
    }

    static String stripLocationPrefix(String location) {
        if (!location) return ""

        // first remove colon (:) and everything before it
        StringBuilder strippedLocation = new StringBuilder(location)
        int colonIndex = strippedLocation.indexOf(":")
        if (colonIndex == 0) {
            strippedLocation.deleteCharAt(0)
        } else if (colonIndex > 0) {
            strippedLocation.delete(0, colonIndex+1)
        }

        // delete all leading forward slashes
        while (strippedLocation.charAt(0) == '/') strippedLocation.deleteCharAt(0)

        return strippedLocation.toString()
    }

    String getContentType(String filename) {
        if (!filename || !filename.contains(".")) return null
        String type = mimetypesFileTypeMap.getContentType(filename)
        // strip any parameters, ie after the ;
        if (type.contains(";")) type = type.substring(0, type.indexOf(";"))
        return type
    }

    boolean isBinaryContentType(String contentType) {
        if (!contentType) return false
        if (contentType.startsWith("text/")) return false
        // aside from text/*, a few notable exceptions:
        if (contentType == "application/javascript") return false
        if (contentType == "application/rtf") return false
        if (contentType == "application/xml") return false
        if (contentType == "application/xml-dtd") return false
        return true
    }
}
