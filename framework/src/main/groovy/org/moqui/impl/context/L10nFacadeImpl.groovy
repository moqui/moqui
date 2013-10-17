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

import org.moqui.context.Cache
import org.moqui.context.L10nFacade
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityFind
import java.text.NumberFormat
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import org.apache.commons.validator.routines.BigDecimalValidator
import org.apache.commons.validator.routines.CalendarValidator

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class L10nFacadeImpl implements L10nFacade {
    protected final static Logger logger = LoggerFactory.getLogger(L10nFacadeImpl.class)

    final static BigDecimalValidator bigDecimalValidator = new BigDecimalValidator(false)
    final static CalendarValidator calendarValidator = new CalendarValidator()

    protected final ExecutionContextFactoryImpl ecfi
    protected final Cache l10nMessage

    L10nFacadeImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi
        l10nMessage = ecfi.getCacheFacade().getCache("l10n.message")
    }

    protected Locale getLocale() { return ecfi.getExecutionContext().getUser().getLocale() }
    protected TimeZone getTimeZone() { return ecfi.getExecutionContext().getUser().getTimeZone() }

    @Override
    public String getLocalizedMessage(String original) {
        if (!original) return ""
        if (original.length() > 255) {
            throw new IllegalArgumentException("Original String cannot be more than 255 characters long, passed in string was [${original.length()}] characters long")
        }

        String localeString = getLocale().toString()

        String cacheKey = original + "::" + localeString
        String lmsg = l10nMessage.get(cacheKey)
        if (lmsg != null) return lmsg

        EntityFind find = ecfi.getEntityFacade().makeFind("moqui.basic.LocalizedMessage")
        find.condition(["original":original, "locale":localeString]).useCache(true)
        EntityValue localizedMessage = find.one()
        if (!localizedMessage && localeString.contains('_')) {
            localizedMessage = find.condition("locale", localeString.substring(0, localeString.indexOf('_'))).one()
        }

        String result = localizedMessage ? localizedMessage.localized : original
        l10nMessage.put(cacheKey, result)
        return result
    }

    @Override
    String formatCurrency(Object amount, String uomId, Integer fractionDigits) {
        if (amount == null) return ""
        if (amount instanceof String) {
            if (((String) amount).length() == 0) {
                return ""
            } else {
                amount = parseNumber((String) amount, null)
            }
        }

        if (fractionDigits == null) fractionDigits = 2
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale)
        if (uomId) nf.setCurrency(Currency.getInstance(uomId))
        nf.setMaximumFractionDigits(fractionDigits)
        nf.setMinimumFractionDigits(fractionDigits)
        String formattedAmount = nf.format(amount)
        return formattedAmount
    }

    @Override
    Time parseTime(String input, String format) {
        Locale curLocale = getLocale()
        TimeZone curTz = getTimeZone()
        if (!format) format = "HH:mm:ss.SSS"
        Calendar cal = calendarValidator.validate(input, format, curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "HH:mm:ss", curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "HH:mm", curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "h:mm a", curLocale, curTz)
        if (cal == null) return null
        Time time = new Time(cal.getTimeInMillis())
        // logger.warn("============== parseTime input=${input} cal=${cal} long=${cal.getTimeInMillis()} time=${time} time long=${time.getTime()} util date=${new java.util.Date(cal.getTimeInMillis())} timestamp=${new java.sql.Timestamp(cal.getTimeInMillis())}")
        return time
    }
    String formatTime(Time input, String format) {
        if (!format) format = "HH:mm:ss"
        String timeStr = calendarValidator.format(input, format, getLocale(), getTimeZone())
        // logger.warn("============= formatTime input=${input} timeStr=${timeStr} long=${input.getTime()}")
        return timeStr
    }

    @Override
    Date parseDate(String input, String format) {
        Locale curLocale = getLocale()
        TimeZone curTz = getTimeZone()
        if (!format) format = "yyyy-MM-dd"
        Calendar cal = calendarValidator.validate(input, format, curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "MM/dd/yyyy", curLocale, curTz)
        if (cal == null) return null
        Date date = new Date(cal.getTimeInMillis())
        // logger.warn("============== parseDate input=${input} cal=${cal} long=${cal.getTimeInMillis()} date=${date} date long=${date.getTime()} util date=${new java.util.Date(cal.getTimeInMillis())} timestamp=${new java.sql.Timestamp(cal.getTimeInMillis())}")
        return date
    }
    String formatDate(Date input, String format) {
        if (!format) format = "yyyy-MM-dd"
        String dateStr = calendarValidator.format(input, format, getLocale(), getTimeZone())
        // logger.warn("============= formatDate input=${input} dateStr=${dateStr} long=${input.getTime()}")
        return dateStr
    }

    @Override
    Timestamp parseTimestamp(String input, String format) {
        Locale curLocale = getLocale()
        TimeZone curTz = getTimeZone()
        if (!format) format = "yyyy-MM-dd HH:mm:ss.SSS z"
        Calendar cal = calendarValidator.validate(input, format, curLocale, curTz)
        // try a couple of other format strings
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd HH:mm:ss.SSS", curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd'T'HH:mm:ss", curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd HH:mm:ss", curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd HH:mm", curLocale, curTz)
        if (cal == null) cal = calendarValidator.validate(input, "yyyy-MM-dd", curLocale, curTz)
        // logger.warn("=========== input=${input}, cal=${cal}, long=${cal?.getTimeInMillis()}, locale=${curLocale}, timeZone=${curTz}, System=${System.currentTimeMillis()}")
        if (cal != null) return new Timestamp(cal.getTimeInMillis())

        // try interpreting the String as a long
        try {
            Long lng = Long.valueOf(input)
            return new Timestamp(lng)
        } catch (NumberFormatException e) {
            if (logger.isTraceEnabled()) logger.trace("Ignoring NumberFormatException for Timestamp parse, setting to null: ${e.toString()}")
            return null
        }
    }
    String formatTimestamp(Timestamp input, String format) {
        if (!format) format = "yyyy-MM-dd HH:mm:ss.SSS"
        return calendarValidator.format(input, format, getLocale(), getTimeZone())
    }

    @Override
    Calendar parseDateTime(String input, String format) {
        return calendarValidator.validate(input, format, getLocale(), getTimeZone())
    }
    String formatDateTime(Calendar input, String format) {
        return calendarValidator.format(input, format, getLocale(), getTimeZone())
    }

    @Override
    BigDecimal parseNumber(String input, String format) {
        return bigDecimalValidator.validate(input, format, getLocale())
    }
    String formatNumber(Number input, String format) {
        return bigDecimalValidator.format(input, format, getLocale())
    }

    @Override
    String formatValue(Object value, String format) {
        if (!value) return ""
        if (value instanceof String) return value
        if (value instanceof Number) return formatNumber(value, format)
        if (value instanceof Timestamp) return formatTimestamp(value, format)
        if (value instanceof Date) return formatDate(value, format)
        if (value instanceof Time) return formatTime(value, format)
        if (value instanceof Calendar) return formatDateTime(value, format)
        return value as String
    }
}
