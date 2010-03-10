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

/** For localization (l10n) functionality, like localizing messages. */
public interface L10nFacade {
    /** Use the current locale (see getLocale method) to localize the message based on data in the LocalizedMessage
     * entity. The localized message may have variables inserted using the ${} syntax and will be expanded with the
     * current context (see the getContext() method).
     *
     * The approach here is that original messages are actual messages in the primary language of the application. This
     * reduces issues with duplicated messages compared to the approach of explicit/artificial property keys. Longer
     * messages (over about 250 characters) should use an artificial message key with the actual value always coming
     * from the database.
     */
    String getLocalizedMessage(String original);
}
