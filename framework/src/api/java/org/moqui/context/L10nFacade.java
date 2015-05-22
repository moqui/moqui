/*
 * This is free and unencumbered software released into the public domain.
 * For specific language governing permissions and limitations refer to
 * the LICENSE.md file or http://unlicense.org
 */
package org.moqui.context;

/** For localization (l10n) functionality, like localizing messages. */
public interface L10nFacade {
    /** Use the current locale (see getLocale method) to localize the message based on data in the moqui.basic.LocalizedMessage
     * entity. The localized message may have variables inserted using the ${} syntax and will be expanded with the
     * current context (see the getContext() method).
     *
     * The approach here is that original messages are actual messages in the primary language of the application. This
     * reduces issues with duplicated messages compared to the approach of explicit/artificial property keys. Longer
     * messages (over 255 characters) should use an artificial message key with the actual value always coming
     * from the database.
     */
    String getLocalizedMessage(String original);

    /** Format currency amount for user to view.
     * @param amount An object representing the amount, should be a subclass of Number.
     * @param uomId The uomId (ISO currency code), required.
     * @param fractionDigits Number of digits after the decimal point to display. If null defaults to 2.
     * @return The formatted currency amount.
     */
    String formatCurrency(Object amount, String uomId, Integer fractionDigits);

    /** Format a Number, Timestamp, Date, Time, or Calendar object using the given format string. If no format string
     * is specified the default for the user's locale and time zone will be used.
     *
     * @param value The value to format. Must be a Number, Timestamp, Date, Time, or Calendar object.
     * @param format The format string used to specify how to format the value.
     * @return The value as a String formatted according to the format string.
     */
    String format(Object value, String format);
    /** Same as the format() method, exists to support code using this older method name. */
    String formatValue(Object value, String format);

    java.sql.Time parseTime(String input, String format);

    java.sql.Date parseDate(String input, String format);

    java.sql.Timestamp parseTimestamp(String input, String format);

    java.util.Calendar parseDateTime(String input, String format);

    java.math.BigDecimal parseNumber(String input, String format);
}
