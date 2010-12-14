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

import org.moqui.context.MessageFacade
import org.moqui.context.ValidationError

public class MessageFacadeImpl implements MessageFacade {

    /** @see org.moqui.context.MessageFacade#getMessageList() */
    public List<String> getMessageList() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.MessageFacade#getErrorList() */
    public List<String> getErrorList() {
        // TODO: implement this
        return null;
    }

    /** @see org.moqui.context.MessageFacade#getValidationErrorList() */
    public List<ValidationError> getValidationErrorList() {
        // TODO: implement this
        return null;
    }
}
