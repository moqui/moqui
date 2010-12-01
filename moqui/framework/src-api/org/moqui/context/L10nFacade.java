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
