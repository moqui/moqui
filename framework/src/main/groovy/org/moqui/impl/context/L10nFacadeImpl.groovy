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

import org.moqui.context.L10nFacade
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityFind
import java.text.NumberFormat
import java.sql.Timestamp
import org.apache.commons.validator.routines.BigDecimalValidator
import org.apache.commons.validator.routines.CalendarValidator

public class L10nFacadeImpl implements L10nFacade {

    final static BigDecimalValidator bigDecimalValidator = new BigDecimalValidator(false)
    final static CalendarValidator calendarValidator = new CalendarValidator()

    protected ExecutionContextFactoryImpl ecfi

    L10nFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
    }

    protected Locale getLocale() { return ecfi.executionContext.user.locale }
    protected TimeZone getTimeZone() { return ecfi.executionContext.user.timeZone }

    /** @see org.moqui.context.L10nFacade#getLocalizedMessage(String) */
    public String getLocalizedMessage(String original) {
        if (!original) return ""
        if (original.length() > 255) {
            throw new IllegalArgumentException("Original String cannot be more than 255 characters long, passed in string was [${original.length()}] characters long")
        }

        String localeString = locale.toString()
        EntityFind find = ecfi.entityFacade.makeFind("moqui.basic.LocalizedMessage")
        find.condition(["original":original, "locale":localeString]).useCache(true)
        EntityValue localizedMessage = find.one()
        if (!localizedMessage && localeString.contains('_')) {
            localizedMessage = find.condition("locale", localeString.substring(0, localeString.indexOf('_'))).one()
        }

        return localizedMessage ? localizedMessage.localized : original
    }

    /** @see org.moqui.context.L10nFacade#formatCurrency(Object, String, int) */
    String formatCurrency(Object amount, String uomId, Integer fractionDigits) {
        if (fractionDigits == null) fractionDigits = 2
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale)
        nf.setCurrency(Currency.getInstance(uomId))
        nf.setMaximumFractionDigits(fractionDigits)
        nf.setMinimumFractionDigits(fractionDigits)
        return nf.format(amount)
    }

    /** @see org.moqui.context.L10nFacade#parseTime(String, String) */
    java.sql.Time parseTime(String input, String format) {
        if (!format) format = "HH:mm:ss.SSS"
        Calendar cal = calendarValidator.validate(input, format, getLocale(), getTimeZone())
        if (cal == null) return null
        return new java.sql.Time(cal.getTimeInMillis())
    }
    String formatTime(java.sql.Time input, String format) {
        if (!format) format = "HH:mm:ss.SSS"
        return calendarValidator.format(input, format, getLocale(), getTimeZone())
    }

    /** @see org.moqui.context.L10nFacade#parseDate(String, String) */
    java.sql.Date parseDate(String input, String format) {
        if (!format) format = "yyyy-MM-dd"
        Calendar cal = calendarValidator.validate(input, format, getLocale(), getTimeZone())
        if (cal == null) return null
        return new java.sql.Date(cal.getTimeInMillis())
    }
    String formatDate(java.sql.Date input, String format) {
        if (!format) format = "yyyy-MM-dd"
        return calendarValidator.format(input, format, getLocale(), getTimeZone())
    }

    /** @see org.moqui.context.L10nFacade#parseTimestamp(String, String) */
    java.sql.Timestamp parseTimestamp(String input, String format) {
        if (!format) format = "yyyy-MM-dd HH:mm:ss.SSS"
        Calendar cal = calendarValidator.validate(input, format, getLocale(), getTimeZone())
        if (cal == null) return null
        return new Timestamp(cal.getTimeInMillis())
    }
    String formatTimestamp(java.sql.Timestamp input, String format) {
        if (!format) format = "yyyy-MM-dd HH:mm:ss.SSS"
        return calendarValidator.format(input, format, getLocale(), getTimeZone())
    }

    /** @see org.moqui.context.L10nFacade#parseDateTime(String, String) */
    Calendar parseDateTime(String input, String format) {
        return calendarValidator.validate(input, format, getLocale(), getTimeZone())
    }
    String formatDateTime(Calendar input, String format) {
        return calendarValidator.format(input, format, getLocale(), getTimeZone())
    }

    /** @see org.moqui.context.L10nFacade#parseNumber(String, String)  */
    BigDecimal parseNumber(String input, String format) {
        return bigDecimalValidator.validate(input, format, getLocale())
    }
    String formatNumber(Number input, String format) {
        return bigDecimalValidator.format(input, format, getLocale())
    }

    /** @see org.moqui.context.L10nFacade#formatValue(Object, String) */
    String formatValue(Object value, String format) {
        if (!value) return ""
        if (value instanceof String) return value
        if (value instanceof Number) return formatNumber(value, format)
        if (value instanceof Timestamp) return formatTimestamp(value, format)
        if (value instanceof java.sql.Date) return formatDate(value, format)
        if (value instanceof java.sql.Time) return formatTime(value, format)
        if (value instanceof Calendar) return formatDateTime(value, format)
        return value as String
    }
}
