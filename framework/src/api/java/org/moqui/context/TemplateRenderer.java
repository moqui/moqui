/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import org.moqui.BaseException;

import java.io.Writer;

public interface TemplateRenderer {
    TemplateRenderer init(ExecutionContextFactory ecf);
    void render(String location, Writer writer) throws BaseException;
    String stripTemplateExtension(String fileName);
    void destroy();
}
