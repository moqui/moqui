/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    protected final String field;

    public ValidationError(String field, String message, Throwable nested) {
        super(message, nested);
        this.field = field;
    }

    public String getField() {
        return this.field;
    }
}
