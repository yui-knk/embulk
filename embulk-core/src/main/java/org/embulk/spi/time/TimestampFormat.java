package org.embulk.spi.time;

import java.util.Set;
import com.google.common.collect.ImmutableSet;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

public class TimestampFormat
{
    private final String format;

    @JsonCreator
    public TimestampFormat(String format)
    {
        this.format = format;
    }

    @JsonValue
    public String getFormat()
    {
        return format;
    }

    private static Set<String> availableTimeZoneNames = ImmutableSet.copyOf(DateTimeZone.getAvailableIDs());

    public static DateTimeZone parseDateTimeZone(String s)
    {
        DateTimeZone jodaDateTimeZoneTemporary = null;
        try {
            // Use TimeZone#forID, not TimeZone#getTimeZone.
            // Because getTimeZone returns GMT even if given timezone id is not found.
            jodaDateTimeZoneTemporary = DateTimeZone.forID(s);
        }
        catch (IllegalArgumentException ex) {
            jodaDateTimeZoneTemporary = null;
        }
        final DateTimeZone jodaDateTimeZone = jodaDateTimeZoneTemporary;

        // Embulk has accepted to parse Joda-Time's time zone IDs in Timestamps since v0.2.0
        // although the formats are based on Ruby's strptime. Joda-Time's time zone IDs are
        // continuously to be accepted with higher priority than Ruby's time zone IDs.
        if (jodaDateTimeZone != null && (s.startsWith("+") || s.startsWith("-"))) {
            return jodaDateTimeZone;

        } else if (s.equals("Z")) {
            return DateTimeZone.UTC;

        } else {
            try {
                // DateTimeFormat.forPattern("z").parseMillis(s) is incorrect, but kept for compatibility as of now.
                //
                // The offset of PDT (Pacific Daylight Time) should be -07:00.
                // DateTimeFormat.forPattern("z").parseMillis("PDT") however returns 8 hours (-08:00).
                // DateTimeFormat.forPattern("z").parseMillis("PDT") == 28800000
                // https://github.com/JodaOrg/joda-time/blob/v2.9.2/src/main/java/org/joda/time/DateTimeUtils.java#L446
                //
                // Embulk has used it to parse time zones for a very long time since it was v0.1.
                // https://github.com/embulk/embulk/commit/b97954a5c78397e1269bbb6979d6225dfceb4e05
                //
                // It is kept as -08:00 for compatibility as of now.
                //
                // TODO: Make time zone parsing consistent.
                // @see <a href="https://github.com/embulk/embulk/issues/860">https://github.com/embulk/embulk/issues/860</a>
                int rawOffset = (int) DateTimeFormat.forPattern("z").parseMillis(s);
                if(rawOffset == 0) {
                    return DateTimeZone.UTC;
                }
                int offset = rawOffset / -1000;
                int h = offset / 3600;
                int m = offset % 3600;
                return DateTimeZone.forOffsetHoursMinutes(h, m);
            } catch (IllegalArgumentException ex) {
                // parseMillis failed
            }

            if (jodaDateTimeZone != null && availableTimeZoneNames.contains(s)) {
                return jodaDateTimeZone;
            }

            // Parsing Ruby-style time zones in lower priority than Joda-Time because
            // TimestampParser has parsed time zones with Joda-Time for a long time
            // since ancient. The behavior is kept for compatibility.
            //
            // The following time zone IDs are duplicated in Ruby and Joda-Time 2.9.2
            // while Ruby does not care summer time and Joda-Time cares summer time.
            // "CET", "EET", "Egypt", "Iran", "MET", "WET"
            //
            // Some zone IDs (ex. "PDT") are parsed by DateTimeFormat#parseMillis as shown above.
            final int rubyStyleTimeOffsetInSecond = RubyTimeZoneTab.dateZoneToDiff(s);
            if (rubyStyleTimeOffsetInSecond != Integer.MIN_VALUE) {
                return DateTimeZone.forOffsetMillis(rubyStyleTimeOffsetInSecond * 1000);
            }

            return null;
        }
    }

    //// Java standard TimeZone
    //static TimeZone parseDateTimeZone(String s)
    //{
    //    if(s.startsWith("+") || s.startsWith("-")) {
    //        return TimeZone.getTimeZone("GMT"+s);
    //
    //    } else {
    //        ParsePosition pp = new ParsePosition(0);
    //        Date off = new SimpleDateFormat("z").parse(s, pp);
    //        if(off != null && pp.getErrorIndex() == -1) {
    //            int rawOffset = (int) off.getTime();
    //            if(rawOffset == 0) {
    //                return TimeZone.UTC;
    //            }
    //            int offset = rawOffset / -1000;
    //            int h = offset / 3600;
    //            int m = offset % 3600;
    //            return DateTimeZone.getTimeZone(String.format("GMT%+02d%02d", h, m));
    //        }
    //
    //        // TimeZone.getTimeZone returns GMT zone if given timezone id is not found
    //        // we want to only return timezone if exact match, otherwise exception
    //        if (availableTimeZoneNames.contains(s)) {
    //            return TimeZone.getTimeZone(s);
    //        }
    //        return null;
    //    }
    //}
}
