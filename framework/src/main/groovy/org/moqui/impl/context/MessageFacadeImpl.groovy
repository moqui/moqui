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

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class MessageFacadeImpl implements MessageFacade {
    protected final static Logger logger = LoggerFactory.getLogger(MessageFacadeImpl.class)

    protected List<String> messageList = new LinkedList<String>()
    protected List<String> errorList = new LinkedList<String>()
    protected List<ValidationError> validationErrorList = new LinkedList<ValidationError>()

    MessageFacadeImpl() { }

    @Override
    List<String> getMessages() { return this.messageList }
    String getMessagesString() {
        StringBuilder messageBuilder = new StringBuilder()
        for (String message in messageList) messageBuilder.append(message).append("\n")
        return messageBuilder.toString()
    }
    void addMessage(String message) { if (message) this.messageList.add(message) }

    @Override
    List<String> getErrors() { return this.errorList }
    void addError(String error) { if (error) this.errorList.add(error) }

    @Override
    List<ValidationError> getValidationErrors() { return this.validationErrorList }
    void addValidationError(String form, String field, String serviceName, String message, Throwable nested) {
        this.validationErrorList.add(new ValidationError(form, field, serviceName, message, nested))
    }

    boolean hasError() { return errorList || validationErrorList }
    String getErrorsString() {
        StringBuilder errorBuilder = new StringBuilder()
        for (String errorMessage in errorList) errorBuilder.append(errorMessage).append("\n")
        for (ValidationError validationError in validationErrorList) errorBuilder.append("${validationError.message} (for field ${validationError.field}${validationError.form ? ' on form ' + validationError.form : ''}${validationError.serviceName ? ' of service ' + validationError.serviceName : ''})").append("\n")
        return errorBuilder.toString()
    }

    void clearErrors() { errorList.clear(); validationErrorList.clear(); }
}
