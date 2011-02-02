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

import javax.jcr.Repository
import javax.jcr.Session
import javax.naming.InitialContext

import org.apache.jackrabbit.commons.JcrUtils

import org.codehaus.groovy.runtime.InvokerHelper

import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.context.TemplateRenderer
import org.moqui.context.ResourceFacade
import org.moqui.context.ResourceReference
import org.moqui.impl.actions.XmlAction
import org.moqui.impl.StupidUtilities

import org.slf4j.LoggerFactory
import org.slf4j.Logger

public class ResourceFacadeImpl implements ResourceFacade {
    protected final static Logger logger = LoggerFactory.getLogger(ResourceFacadeImpl.class)

    protected final ExecutionContextFactoryImpl ecfi

    protected final Cache scriptGroovyLocationCache
    protected final Cache scriptXmlActionLocationCache
    protected final Cache textLocationCache

    protected final Map<String, Class> resourceReferenceClasses = new HashMap()
    protected final Map<String, TemplateRenderer> templateRenderers = new HashMap()

    protected final Map<String, Repository> contentRepositories = new HashMap()
    protected final Map<String, String> contentRepositoryWorkspaces = new HashMap()

    protected final ThreadLocal<Map<String, Session>> contentSessions = new ThreadLocal<Map<String, Session>>()

    protected GroovyShell localGroovyShell = null

    ResourceFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        this.textLocationCache = ecfi.getCacheFacade().getCache("resource.text.location")

        this.scriptGroovyLocationCache = ecfi.getCacheFacade().getCache("resource.groovy.location")
        this.scriptXmlActionLocationCache = ecfi.getCacheFacade().getCache("resource.xml-actions.location")

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
                logger.error("Error getting JCR content repository with name [${repositoryNode."@name"}], is of type [${repositoryNode."@type"}] at location [${repositoryNode."@location"}]", e)
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
        String scheme = "file"
        if (location.contains(":")) scheme = location.substring(0, location.indexOf(":"))

        Class rrClass = resourceReferenceClasses.get(scheme)
        if (!rrClass) throw new IllegalArgumentException("Prefix (scheme) not supported for location [${location}")

        ResourceReference rr = (ResourceReference) rrClass.newInstance()
        return rr.init(location, ecfi.executionContext)
    }

    /** @see org.moqui.context.ResourceFacade#getLocationStream(String) */
    InputStream getLocationStream(String location) {
        ResourceReference rr = getLocationReference(location)
        if (!rr) return null
        return rr.openStream()
    }

    String getLocationText(String location, boolean cache) {
        String text = cache ? (String) textLocationCache.get(location) : null
        if (text != null) return text
        text = StupidUtilities.getStreamText(getLocationStream(location))
        if (cache) textLocationCache.put(location, text)
        return text
    }

    /** @see org.moqui.context.ResourceFacade#renderTemplateInCurrentContext(String, Writer) */
    void renderTemplateInCurrentContext(String location, Writer writer) {
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

        if (tr != null) {
            tr.render(location, writer)
        } else {
            // no renderer found, just grab the text and throw it to the writer
            String text = getLocationText(location, true)
            if (text) writer.write(text)
        }
    }

    /** @see org.moqui.context.ResourceFacade#runScriptInCurrentContext(String, String) */
    Object runScriptInCurrentContext(String location, String method) {
        ExecutionContext ec = ecfi.executionContext
        if (location.endsWith(".groovy")) {
            Script script = InvokerHelper.createScript(getGroovyByLocation(location), new Binding(ec.context))
            Object result
            if (method) {
                result = script.invokeMethod(method, {})
            } else {
                result = script.run()
            }
            return result
        } else if (location.endsWith(".xml")) {
            XmlAction xa = getXmlActionByLocation(location)
            return xa.run(ec)
        } else {
            throw new IllegalArgumentException("Cannot run script [${location}], unknown extension.")
        }
    }

    Class getGroovyByLocation(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) gc = loadGroovy(location)
        return gc
    }
    protected Class loadGroovy(String location) {
        Class gc = (Class) scriptGroovyLocationCache.get(location)
        if (!gc) {
            gc = new GroovyClassLoader().parseClass(getLocationText(location), location)
            scriptGroovyLocationCache.put(location, gc)
        }
        return gc
    }

    XmlAction getXmlActionByLocation(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) loadXmlAction(location)
        return xa
    }
    protected XmlAction loadXmlAction(String location) {
        XmlAction xa = (XmlAction) scriptGroovyLocationCache.get(location)
        if (!xa) {
            xa = new XmlAction(ecfi, getLocationText(location), location)
            scriptXmlActionLocationCache.put(location, xa)
        }
        return xa
    }

    /** @see org.moqui.context.ResourceFacade#evaluateCondition(String, String) */
    boolean evaluateCondition(String expression, String debugLocation) {
        return getGroovyShell().evaluate(expression, debugLocation) as boolean
    }

    /** @see org.moqui.context.ResourceFacade#evaluateContextField(String, String) */
    Object evaluateContextField(String expression, String debugLocation) {
        return getGroovyShell().evaluate(expression, debugLocation)
    }

    /** @see org.moqui.context.ResourceFacade#evaluateStringExpand(String, String) */
    String evaluateStringExpand(String inputString, String debugLocation) {
        return getGroovyShell().evaluate('"""' + inputString + '"""', debugLocation) as String
    }
    protected GroovyShell getGroovyShell() {
        // consider not caching this; does Binding eval at runtime or when built? if at runtime can just create one
        // for the context and it will update with the context, if not then would have to be created every time an
        // expression/script is run
        if (localGroovyShell) return localGroovyShell
        localGroovyShell = new GroovyShell(new Binding(ecfi.executionContext.context))
        return localGroovyShell
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
}
