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
package org.moqui.context;

import java.util.List;

/** For user messages including general feedback, errors, and field-specific validation errors. */
public interface MessageFacade {
    /** A freely modifiable List of general (non-error) messages that will be shown to the user. */
    List<String> getMessages();
    void addMessage(String message);

    /** A freely modifiable List of error messages that will be shown to the user. */
    List<String> getErrors();
    void addError(String error);

    /** A freely modifiable List of ValidationError objects that will be shown to the user in the context of the
     * fields that triggered the error.
     */
    List<ValidationError> getValidationErrors();
}
