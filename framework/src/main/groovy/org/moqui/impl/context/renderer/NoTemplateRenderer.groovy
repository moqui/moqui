/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.impl.context.renderer

import org.moqui.impl.context.ResourceFacadeImpl
import org.moqui.context.TemplateRenderer
import org.moqui.context.ExecutionContext
import org.moqui.context.ExecutionContextFactory
import org.moqui.impl.context.ExecutionContextFactoryImpl

class NoTemplateRenderer implements TemplateRenderer {
    protected ExecutionContextFactoryImpl ecfi

    NoTemplateRenderer() { }

    TemplateRenderer init(ExecutionContextFactory ecf) {
        this.ecfi = (ExecutionContextFactoryImpl) ecf
        return this
    }

    void render(String location, Writer writer) {
        String text = ecfi.resourceFacade.getLocationText(location, true)
        if (text) writer.write(text)
    }

    String stripTemplateExtension(String fileName) { return fileName }

    void destroy() { }
}
