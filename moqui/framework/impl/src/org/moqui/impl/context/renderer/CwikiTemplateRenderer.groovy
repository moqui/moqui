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
package org.moqui.impl.context.renderer

import org.moqui.context.TemplateRenderer
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.moqui.context.Cache
import org.eclipse.mylyn.wikitext.core.parser.builder.HtmlDocumentBuilder
import org.eclipse.mylyn.wikitext.core.parser.MarkupParser
import org.eclipse.mylyn.wikitext.confluence.core.ConfluenceLanguage
import org.moqui.context.ExecutionContext
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.context.ExecutionContextFactory

class CwikiTemplateRenderer implements TemplateRenderer {
    protected final static Logger logger = LoggerFactory.getLogger(CwikiTemplateRenderer.class)

    protected ExecutionContextFactoryImpl ecfi

    protected final Cache templateCwikiLocationCache

    CwikiTemplateRenderer() { }

    TemplateRenderer init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        this.templateCwikiLocationCache = ecfi.cacheFacade.getCache("resource.cwiki.location")
        return this
    }

    void render(String location, Writer writer) {
        String cwikiText = templateCwikiLocationCache.get(location)
        if (cwikiText) writer.write(cwikiText)

        StringWriter localWriter = new StringWriter()
        HtmlDocumentBuilder builder = new HtmlDocumentBuilder(localWriter)
        // avoid the <html> and <body> tags
        builder.setEmitAsDocument(false)
        MarkupParser parser = new MarkupParser(new ConfluenceLanguage())
        parser.setBuilder(builder)
        parser.parse(ecfi.resourceFacade.getLocationText(location, false))

        cwikiText = localWriter.toString()
        if (cwikiText) {
            templateCwikiLocationCache.put(location, cwikiText)
            writer.write(cwikiText)
        }
    }

    void destroy() { }
}
