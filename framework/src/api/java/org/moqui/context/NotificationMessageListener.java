/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

public interface NotificationMessageListener {
    void init(ExecutionContextFactory ecf);
    void destroy();
    void onMessage(NotificationMessage nm, ExecutionContext ec);
}
