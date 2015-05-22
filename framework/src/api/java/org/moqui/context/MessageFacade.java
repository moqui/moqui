/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import java.util.List;

/** For user messages including general feedback, errors, and field-specific validation errors. */
public interface MessageFacade {
    /** A freely modifiable List of general (non-error) messages that will be shown to the user. */
    List<String> getMessages();
    /** Make a single String with all messages separated by the new-line character.
     * @return String with all messages.
     */
    String getMessagesString();
    /** Add a non-error message for the user to see.
     * @param message The message to add.
     */
    void addMessage(String message);

    /** A freely modifiable List of error messages that will be shown to the user. */
    List<String> getErrors();
    /** Add a error message for the user to see.
     * NOTE: system errors not meant for the user should be thrown as exceptions instead.
     * @param error The error message to add
     */
    void addError(String error);

    /** A freely modifiable List of ValidationError objects that will be shown to the user in the context of the
     * fields that triggered the error.
     */
    List<ValidationError> getValidationErrors();
    void addValidationError(String form, String field, String serviceName, String message, Throwable nested);

    /** See if there is are any errors. Checks both error strings and validation errors. */
    boolean hasError();
    /** Make a single String with all error messages separated by the new-line character.
     * @return String with all error messages.
     */
    String getErrorsString();

    void clearErrors();
}
