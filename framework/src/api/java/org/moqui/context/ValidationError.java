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

    public ValidationError(String field, String message, Throwable nested) {
        super(message, nested);
        this.form = null;
        this.field = field;
    }

    public ValidationError(String form, String field, String message, Throwable nested) {
        super(message, nested);
        this.form = form;
        this.field = field;
    }

    public String getForm() { return this.form; }
    public String getField() { return this.field; }
}
