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

import org.moqui.context.ScreenFacade

public class ScreenFacadeImpl implements ScreenFacade {

    protected final ExecutionContextFactoryImpl ecfi;

    public ScreenFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

    /** @see org.moqui.context.ScreenFacade#renderScreenText(String, Appendable, String, String, String) */
    public void renderScreenText(String screenLocation, Appendable appender, String outputType, String characterEncoding, String macroTemplateLocation) {
        // TODO: implement this
    }
}
