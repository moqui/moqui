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
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(L10nFacadeImpl.class)

    final static BigDecimalValidator bigDecimalValidator = new BigDecimalValidator(false)
    final static CalendarValidator calendarValidator = new CalendarValidator()

    protected ExecutionContextFactoryImpl ecfi

    L10nFacadeImpl(ExecutionContextFactoryImpl ecfi) { this.ecfi = ecfi }

    protected Locale getLocale() { return ecfi.getExecutionContext().getUser().getLocale() }
    protected TimeZone getTimeZone() { return ecfi.getExecutionContext().getUser().getTimeZone() }

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
        if (amount == null) return ""
        if (amount instanceof String) if (((String) amount).length() == 0) {
            return ""
        } else {
            amount = new BigDecimal((String) amount)
        }

        if (fractionDigits == null) fractionDigits = 2
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale)
        if (uomId) nf.setCurrency(Currency.getInstance(uomId))
        nf.setMaximumFractionDigits(fractionDigits)
        nf.setMinimumFractionDigits(fractionDigits)
        String formattedAmount = nf.format(amount)
        return formattedAmount
    }

    /** @see org.moqui.context.L10nFacade#parseTime(String, String) */
    java.sql.Time parseTime(String input, String format) {
        if (!format) format = "HH:mm:ss.SSS"
        Calendar cal = calendarValidator.validate(input, format, getLocale(), getTimeZone())
        if (cal == null) cal = calendarValidator.validate(input, "HH:mm:ss", getLocale(), getTimeZone())
        if (cal == null) cal = calendarValidator.validate(input, "HH:mm", getLocale(), getTimeZone())
        if (cal == null) cal = calendarValidator.validate(input, "h:mm a", getLocale(), getTimeZone())
        if (cal == null) return null
        java.sql.Time time = new java.sql.Time(cal.getTimeInMillis())
        // logger.warn("============== parseTime input=${input} cal=${cal} long=${cal.getTimeInMillis()} time=${time} time long=${time.getTime()} util date=${new java.util.Date(cal.getTimeInMillis())} timestamp=${new java.sql.Timestamp(cal.getTimeInMillis())}")
        return time
    }
    String formatTime(java.sql.Time input, String format) {
        if (!format) format = "HH:mm:ss"
        String timeStr = calendarValidator.format(input, format, getLocale(), getTimeZone())
        // logger.warn("============= formatTime input=${input} timeStr=${timeStr} long=${input.getTime()}")
        return timeStr
    }

    /** @see org.moqui.context.L10nFacade#parseDate(String, String) */
    java.sql.Date parseDate(String input, String format) {
        if (!format) format = "yyyy-MM-dd"
        Calendar cal = calendarValidator.validate(input, format, getLocale(), getTimeZone())
        if (cal == null) cal = calendarValidator.validate(input, "MM/dd/yyyy", getLocale(), getTimeZone())
        if (cal == null) return null
        java.sql.Date date = new java.sql.Date(cal.getTimeInMillis())
        // logger.warn("============== parseDate input=${input} cal=${cal} long=${cal.getTimeInMillis()} date=${date} date long=${date.getTime()} util date=${new java.util.Date(cal.getTimeInMillis())} timestamp=${new java.sql.Timestamp(cal.getTimeInMillis())}")
        return date
    }
    String formatDate(java.sql.Date input, String format) {
        if (!format) format = "yyyy-MM-dd"
        String dateStr = calendarValidator.format(input, format, getLocale(), getTimeZone())
        // logger.warn("============= formatDate input=${input} dateStr=${dateStr} long=${input.getTime()}")
        return dateStr
    }

    /** @see org.moqui.context.L10nFacade#parseTimestamp(String, String) */
    java.sql.Timestamp parseTimestamp(String input, String format) {
        if (!format) format = "yyyy-MM-dd HH:mm:ss.SSS z"
        Calendar cal = calendarValidator.validate(input, format, getLocale(), getTimeZone())
        // try a couple of other format strings
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd HH:mm:ss.SSS", getLocale(), getTimeZone())
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd HH:mm:ss", getLocale(), getTimeZone())
        // logger.warn("=========== input=${input}, cal=${cal}, long=${cal?.getTimeInMillis()}, locale=${getLocale()}, timeZone=${getTimeZone()}, System=${System.currentTimeMillis()}")
        if (cal != null) return new Timestamp(cal.getTimeInMillis())

        // try interpreting the String as a long
        try {
            Long lng = Long.valueOf(input)
            return new Timestamp(lng)
        } catch (NumberFormatException e) {
            return null
        }

        return null
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
