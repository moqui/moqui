/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

import org.moqui.BaseException;

/**
 * ValidationError - used to track information about validation errors.
 *
 * This extends the BaseException and has additional information about the field that had the error, etc.
 *
 * This is not generally thrown all the way up to the user and is instead added to a list of validation errors as
 * things are running, and then all of them can be shown in context of the fields with the errors.
 */
public class ValidationError extends BaseException {
    protected final String form;
    protected final String field;
    protected final String serviceName;

    public ValidationError(String field, String message, Throwable nested) {
        super(message, nested);
        this.form = null;
        this.field = field;
        this.serviceName = null;
    }

    public ValidationError(String form, String field, String serviceName, String message, Throwable nested) {
        super(message, nested);
        this.form = form;
        this.field = field;
        this.serviceName = serviceName;
    }

    public String getForm() { return form; }
    public String getField() { return field; }
    public String getServiceName() { return serviceName; }
}
