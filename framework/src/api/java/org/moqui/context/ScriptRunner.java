/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import org.moqui.BaseException;

public interface ScriptRunner {
    ScriptRunner init(ExecutionContextFactory ecf);
    Object run(String location, String method, ExecutionContext ec) throws BaseException;
    void destroy();
}
