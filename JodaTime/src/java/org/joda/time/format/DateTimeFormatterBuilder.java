/*
 * Joda Software License, Version 1.0
 *
 *
 * Copyright (c) 2001-03 Stephen Colebourne.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Joda project (http://www.joda.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The name "Joda" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact licence@joda.org.
 *
 * 5. Products derived from this software may not be called "Joda",
 *    nor may "Joda" appear in their name, without prior written
 *    permission of the Joda project.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE JODA AUTHORS OR THE PROJECT
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Joda project and was originally
 * created by Stephen Colebourne <scolebourne@joda.org>. For more
 * information on the Joda project, please see <http://www.joda.org/>.
 */
package org.joda.time.format;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Locale;

import org.joda.time.Chronology;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.ReadableInstant;
import org.joda.time.chrono.FractionalDateTimeField;
import org.joda.time.chrono.RemainderDateTimeField;
import org.joda.time.chrono.iso.ISOChronology;

/**
 * DateTimeFormatterBuilder is used for constructing {@link DateTimeFormatter}s.
 * DateTimeFormatters can be built by appending specific fields, patterns, or
 * other formatters.
 *
 * <p>
 * For example, a formatter that prints month and year, like "January 1970", can
 * be constructed as follows:
 * <p>
 * <pre>
 * DateTimeFormatter monthAndYear = new DateTimeFormatterBuilder()
 *     .appendMonthOfYearText()
 *     .appendLiteral(' ')
 *     .appendYear(4, 4)
 *     .toFormatter();
 * </pre>
 *
 * @see DateTimeFormat
 * @author Brian S O'Neill
 */
public class DateTimeFormatterBuilder {

    private static String parseToken(String pattern, int[] indexRef) {
        StringBuffer buf = new StringBuffer();

        int i = indexRef[0];
        int length = pattern.length();

        char c = pattern.charAt(i);
        if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
            // Scan a run of the same character, which indicates a time
            // pattern.
            buf.append(c);

            while (i + 1 < length) {
                char peek = pattern.charAt(i + 1);
                if (peek == c) {
                    buf.append(c);
                    i++;
                } else {
                    break;
                }
            }
        } else {
            // This will identify token as text.
            buf.append('\'');

            boolean inLiteral = false;

            for (; i < length; i++) {
                c = pattern.charAt(i);
                
                if (c == '\'') {
                    if (i + 1 < length && pattern.charAt(i + 1) == '\'') {
                        // '' is treated as escaped '
                        i++;
                        buf.append(c);
                    } else {
                        inLiteral = !inLiteral;
                    }
                } else if (!inLiteral &&
                           (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
                    i--;
                    break;
                } else {
                    buf.append(c);
                }
            }
        }

        indexRef[0] = i;
        return buf.toString();
    }

    private final Chronology iChrono;
    private final Chronology iChronoUTC;
    private final Locale iLocale;

    // Array contents alternate between printers and parsers.
    private ArrayList iElementPairs;
    private Object iFormatter;

    /**
     * Creates a DateTimeFormatterBuilder with {@link ISOChronology}, in the
     * default time zone and locale.
     */
    public DateTimeFormatterBuilder() {
        this(ISOChronology.getInstance());
    }

    /**
     * Creates a DateTimeFormatterBuilder with {@link ISOChronology}, in the
     * given time zone, with the default locale.
     */
    public DateTimeFormatterBuilder(DateTimeZone zone) {
        this(ISOChronology.getInstance(zone));
    }

    /**
     * Creates a DateTimeFormatterBuilder with {@link ISOChronology}, in the
     * given time zone, with any locale.
     */
    public DateTimeFormatterBuilder(DateTimeZone zone, Locale locale) {
        this(ISOChronology.getInstance(zone), locale);
    }

    /**
     * Creates a DateTimeFormatterBuilder with any chronology and the default
     * locale.
     *
     * @param chrono Chronology to use
     */
    public DateTimeFormatterBuilder(Chronology chrono) {
        this(chrono, Locale.getDefault());
    }

    /**
     * Creates a DateTimeFormatterBuilder with any chronology and locale.
     *
     * @param chrono Chronology to use
     * @param locale Locale to use
     */
    public DateTimeFormatterBuilder(Chronology chrono, Locale locale) {
        if (chrono == null) {
            throw new IllegalArgumentException("The Chronology must not be null");
        }
        if (locale == null) {
            throw new IllegalArgumentException("The Locale must not be null");
        }
        iChrono = chrono;
        iChronoUTC = chrono.withUTC();
        DateTimeZone zone = chrono.getDateTimeZone();
        iLocale = locale;
        iElementPairs = new ArrayList();
    }

    /**
     * Returns the chronology being used by the formatter builder.
     */
    public Chronology getChronology() {
        return iChrono;
    }

    /**
     * Returns the locale being used the formatter builder.
     */
    public Locale getLocale() {
        return iLocale;
    }

    /**
     * Converts to a DateTimePrinter that prints using all the appended
     * elements. Subsequent changes to this builder do not affect the returned
     * printer.
     *
     * @throws UnsupportedOperationException if any formatter element doesn't support
     * printing
     */
    public DateTimePrinter toPrinter() throws UnsupportedOperationException {
        Object f = getFormatter();
        if (isPrinter(f)) {
            return (DateTimePrinter)f;
        }
        throw new UnsupportedOperationException("Printing not supported");
    }

    /**
     * Converts to a DateTimeParser that parses using all the appended
     * elements. Subsequent changes to this builder do not affect the returned
     * parser.
     *
     * @throws UnsupportedOperationException if any formatter element doesn't support
     * parsing
     */
    public DateTimeParser toParser() throws UnsupportedOperationException {
        Object f = getFormatter();
        if (isParser(f)) {
            return (DateTimeParser)f;
        }
        throw new UnsupportedOperationException("Parsing not supported");
    }

    /**
     * Converts to a DateTimeFormatter that prints and parses using all the
     * appended elements. Subsequent changes to this builder do not affect the
     * returned formatter.
     *
     * @throws UnsupportedOperationException if any formatter element doesn't support
     * both printing and parsing
     */
    public DateTimeFormatter toFormatter() throws UnsupportedOperationException {
        Object f = getFormatter();
        if (isFormatter(f)) {
            return (DateTimeFormatter)f;
        }
        throw new UnsupportedOperationException("Both printing and parsing not supported");
    }

    /**
     * Returns true if toPrinter can be called without throwing an
     * UnsupportedOperationException.
     */
    public boolean canBuildPrinter() {
        return isPrinter(getFormatter());
    }

    /**
     * Returns true if toParser can be called without throwing an
     * UnsupportedOperationException.
     */
    public boolean canBuildParser() {
        return isParser(getFormatter());
    }

    /**
     * Returns true if toFormatter can be called without throwing an
     * UnsupportedOperationException.
     */
    public boolean canBuildFormatter() {
        return isFormatter(getFormatter());
    }

    /**
     * Clears out all the appended elements, allowing this builder to be
     * reused.
     */
    public void clear() {
        iFormatter = null;
        iElementPairs.clear();
    }

    /**
     * Appends another formatter.
     *
     * @throws IllegalArgumentException if formatter is null
     */
    public DateTimeFormatterBuilder append(DateTimeFormatter formatter)
        throws IllegalArgumentException
    {
        if (formatter == null) {
            throw new IllegalArgumentException("No formatter supplied");
        }
        return append0(formatter);
    }

    /**
     * Appends just a printer. With no matching parser, a parser cannot be
     * built from this DateTimeFormatterBuilder.
     *
     * @throws IllegalArgumentException if printer is null
     */
    public DateTimeFormatterBuilder append(DateTimePrinter printer)
        throws IllegalArgumentException
    {
        if (printer == null) {
            throw new IllegalArgumentException("No printer supplied");
        }
        return append0(printer, null);
    }

    /**
     * Appends just a parser. With no matching printer, a printer cannot be
     * built from this builder.
     *
     * @throws IllegalArgumentException if parser is null
     */
    public DateTimeFormatterBuilder append(DateTimeParser parser) {
        if (parser == null) {
            throw new IllegalArgumentException("No parser supplied");
        }
        return append0(null, parser);
    }

    /**
     * Appends a printer/parser pair.
     *
     * @throws IllegalArgumentException if printer or parser is null
     */
    public DateTimeFormatterBuilder append(DateTimePrinter printer,
                                           DateTimeParser parser)
        throws IllegalArgumentException
    {
        if (printer == null) {
            throw new IllegalArgumentException("No printer supplied");
        }
        if (parser == null) {
            throw new IllegalArgumentException("No parser supplied");
        }
        return append0(printer, parser);
    }

    /**
     * Appends a printer and a set of matching parsers. When parsing, the first
     * parser in the list is selected for parsing. If it fails, the next is
     * chosen, and so on. If none of these parsers succeeds, then the failed
     * position of the parser that made the greatest progress is returned.
     * <p>
     * Only the printer is optional. In addtion, it is illegal for any but the
     * last of the parser array elements to be null. If the last element is
     * null, this represents the empty parser. The presence of an empty parser
     * indicates that the entire array of parse formats is optional.
     *
     * @throws IllegalArgumentException if any parser element but the last is null
     */
    public DateTimeFormatterBuilder append(DateTimePrinter printer,
                                           DateTimeParser[] parsers)
        throws IllegalArgumentException
    {
        if (parsers == null) {
            throw new IllegalArgumentException("No parsers supplied");
        }
        int length = parsers.length;
        if (length == 1) {
            // If the last element is null, an exception is still thrown.
            return append(printer, parsers[0]);
        }

        DateTimeParser[] copyOfParsers = new DateTimeParser[length];
        int i;
        for (i = 0; i < length - 1; i++) {
            if ((copyOfParsers[i] = parsers[i]) == null) {
                throw new IllegalArgumentException("Incomplete parser array");
            }
        }
        copyOfParsers[i] = parsers[i];

        return append0(printer, new MatchingParser(iChrono, copyOfParsers));
    }

    /**
     * Appends just a parser element which is optional. With no matching
     * printer, a printer cannot be built from this DateTimeFormatterBuilder.
     *
     * @throws IllegalArgumentException if parser is null
     */
    public DateTimeFormatterBuilder appendOptional(DateTimeParser parser) {
        if (parser == null) {
            throw new IllegalArgumentException("No parser supplied");
        }
        return append0(null, new MatchingParser(iChrono, new DateTimeParser[] {parser, null}));
    }

    private DateTimeFormatterBuilder append0(Object element) {
        iFormatter = null;
        // Add the element as both a printer and parser.
        iElementPairs.add(element);
        iElementPairs.add(element);
        return this;
    }

    private DateTimeFormatterBuilder append0(DateTimePrinter printer,
                                             DateTimeParser parser)
    {
        iFormatter = null;
        iElementPairs.add(printer);
        iElementPairs.add(parser);
        return this;
    }

    /**
     * Instructs the printer to emit a specific character, and the parser to
     * expect it. The parser is case-insensitive.
     */
    public DateTimeFormatterBuilder appendLiteral(char c) {
        return append0(new CharacterLiteral(iChrono, c));
    }

    /**
     * Instructs the printer to emit specific text, and the parser to expect
     * it. The parser is case-insensitive.
     */
    public DateTimeFormatterBuilder appendLiteral(String text) {
        return append0(new StringLiteral(iChrono, text));
    }

    /**
     * Instructs the printer to emit a field value as a decimal number, and the
     * parser to expect an unsigned decimal number.
     *
     * @param field field should operate in UTC or be time zone agnostic
     * @param minDigits minumum number of digits to <i>print</i>
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendNumeric(DateTimeField field,
                                                  int minDigits, int maxDigits)
    {
        if (maxDigits < minDigits) {
            maxDigits = minDigits;
        }
        if (minDigits < 0 || maxDigits <= 0) {
            throw new IllegalArgumentException();
        }
        if (minDigits <= 1) {
            return append0(new UnpaddedNumber(iChrono, field, maxDigits, false));
        } else {
            return append0(new PaddedNumber(iChrono, field, maxDigits, false, minDigits));
        }
    }

    /**
     * Instructs the printer to emit a field value as a decimal number, and the
     * parser to expect a signed decimal number.
     *
     * @param field field should operate in UTC or be time zone agnostic
     * @param minDigits minumum number of digits to <i>print</i>
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendSignedNumeric(DateTimeField field,
                                                        int minDigits, int maxDigits)
    {
        if (maxDigits < minDigits) {
            maxDigits = minDigits;
        }
        if (minDigits < 0 || maxDigits <= 0) {
            throw new IllegalArgumentException();
        }
        if (minDigits <= 1) {
            return append0(new UnpaddedNumber(iChrono, field, maxDigits, true));
        } else {
            return append0(new PaddedNumber(iChrono, field, maxDigits, true, minDigits));
        }
    }

    /**
     * Instructs the printer to emit a field value as text, and the
     * parser to expect text.
     *
     * @param field field should operate in UTC or be time zone agnostic
     */
    public DateTimeFormatterBuilder appendText(DateTimeField field) {
        return append0(new TextField(iChrono, field, iLocale, false));
    }

    /**
     * Instructs the printer to emit a field value as short text, and the
     * parser to expect text.
     *
     * @param field field should operate in UTC or be time zone agnostic
     */
    public DateTimeFormatterBuilder appendShortText(DateTimeField field) {
        return append0(new TextField(iChrono, field, iLocale, true));
    }

    /**
     * Instructs the printer to emit a remainder of time as a decimal fraction,
     * sans decimal point. For example, if the range is specified as 60000
     * (milliseconds in one minute) and the time is 12:30:45, the value printed
     * is 75. A decimal point is implied, so the fraction is 0.75, or three-quarters
     * of a minute.
     *
     * @param minDigits minumum number of digits to print.
     * @param maxDigits maximum number of digits to print or parse.
     * @param rangeInMillis range of values in fraction
     */
    public DateTimeFormatterBuilder appendFraction(int minDigits, int maxDigits,
                                                   int rangeInMillis)
    {
        if (maxDigits < minDigits) {
            maxDigits = minDigits;
        }
        if (minDigits < 0 || maxDigits <= 0) {
            throw new IllegalArgumentException();
        }
        return append0(new Fraction(iChrono, minDigits, maxDigits, rangeInMillis));
    }

    /**
     * @param minDigits minumum number of digits to print
     * @param maxDigits maximum number of digits to print or parse
     */
    public DateTimeFormatterBuilder appendFractionOfSecond(int minDigits, int maxDigits) {
        return appendFraction(minDigits, maxDigits, DateTimeConstants.MILLIS_PER_SECOND);
    }

    /**
     * @param minDigits minumum number of digits to print
     * @param maxDigits maximum number of digits to print or parse
     */
    public DateTimeFormatterBuilder appendFractionOfMinute(int minDigits, int maxDigits) {
        return appendFraction(minDigits, maxDigits, DateTimeConstants.MILLIS_PER_MINUTE);
    }

    /**
     * @param minDigits minumum number of digits to print
     * @param maxDigits maximum number of digits to print or parse
     */
    public DateTimeFormatterBuilder appendFractionOfHour(int minDigits, int maxDigits) {
        return appendFraction(minDigits, maxDigits, DateTimeConstants.MILLIS_PER_HOUR);
    }

    /**
     * @param minDigits minumum number of digits to print
     * @param maxDigits maximum number of digits to print or parse
     */
    public DateTimeFormatterBuilder appendFractionOfDay(int minDigits, int maxDigits) {
        return appendFraction
            (minDigits, maxDigits, DateTimeConstants.MILLIS_PER_DAY);
    }

    /**
     * Instructs the printer to emit a numeric millisOfSecond field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendMillisOfSecond(int minDigits) {
        return appendNumeric(iChronoUTC.millisOfSecond(), minDigits, 3);
    }

    /**
     * Instructs the printer to emit a numeric millisOfDay field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendMillisOfDay(int minDigits) {
        return appendNumeric(iChronoUTC.millisOfDay(), minDigits, 8);
    }

    /**
     * Instructs the printer to emit a numeric secondOfMinute field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendSecondOfMinute(int minDigits) {
        return appendNumeric(iChronoUTC.secondOfMinute(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric secondOfDay field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendSecondOfDay(int minDigits) {
        return appendNumeric(iChronoUTC.secondOfDay(), minDigits, 5);
    }

    /**
     * Instructs the printer to emit a numeric minuteOfHour field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendMinuteOfHour(int minDigits) {
        return appendNumeric(iChronoUTC.minuteOfHour(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric minuteOfDay field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendMinuteOfDay(int minDigits) {
        return appendNumeric(iChronoUTC.minuteOfDay(), minDigits, 4);
    }

    /**
     * Instructs the printer to emit a numeric hourOfDay field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendHourOfDay(int minDigits) {
        return appendNumeric(iChronoUTC.hourOfDay(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric clockhourOfDay field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendClockhourOfDay(int minDigits) {
        return appendNumeric(iChronoUTC.clockhourOfDay(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric hourOfHalfday field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendHourOfHalfday(int minDigits) {
        return appendNumeric(iChronoUTC.hourOfHalfday(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric clockhourOfHalfday field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendClockhourOfHalfday(int minDigits) {
        return appendNumeric(iChronoUTC.clockhourOfHalfday(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric dayOfWeek field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendDayOfWeek(int minDigits) {
        return appendNumeric(iChronoUTC.dayOfWeek(), minDigits, 1);
    }

    /**
     * Instructs the printer to emit a numeric dayOfMonth field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendDayOfMonth(int minDigits) {
        return appendNumeric(iChronoUTC.dayOfMonth(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric dayOfYear field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendDayOfYear(int minDigits) {
        return appendNumeric(iChronoUTC.dayOfYear(), minDigits, 3);
    }

    /**
     * Instructs the printer to emit a numeric weekOfWeekyear field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendWeekOfWeekyear(int minDigits) {
        return appendNumeric(iChronoUTC.weekOfWeekyear(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric weekyear field.
     *
     * @param minDigits minumum number of digits to <i>print</i>
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendWeekyear(int minDigits, int maxDigits) {
        return appendNumeric
            (iChronoUTC.weekyear(), minDigits, maxDigits);
    }

    /**
     * Instructs the printer to emit a numeric monthOfYear field.
     *
     * @param minDigits minumum number of digits to print
     */
    public DateTimeFormatterBuilder appendMonthOfYear(int minDigits) {
        return appendNumeric(iChronoUTC.monthOfYear(), minDigits, 2);
    }

    /**
     * Instructs the printer to emit a numeric year field.
     *
     * @param minDigits minumum number of digits to <i>print</i>
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendYear(int minDigits, int maxDigits) {
        return appendSignedNumeric(iChronoUTC.year(), minDigits, maxDigits);
    }

    /**
     * Instructs the printer to emit a numeric yearOfEra field.
     *
     * @param minDigits minumum number of digits to <i>print</i>
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendYearOfEra(int minDigits, int maxDigits) {
        return appendNumeric(iChronoUTC.yearOfEra(), minDigits, maxDigits);
    }

    /**
     * Instructs the printer to emit a numeric year of century field.
     *
     * @param minDigits minumum number of digits to print
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendYearOfCentury(int minDigits, int maxDigits) {
        return appendNumeric(iChronoUTC.yearOfCentury(), minDigits, maxDigits);
    }

    /**
     * Instructs the printer to emit a numeric century of era field.
     *
     * @param minDigits minumum number of digits to print
     * @param maxDigits maximum number of digits to <i>parse</i>, or the estimated
     * maximum number of digits to print
     */
    public DateTimeFormatterBuilder appendCenturyOfEra(int minDigits, int maxDigits) {
        return appendSignedNumeric(iChronoUTC.centuryOfEra(), minDigits, maxDigits);
    }

    /**
     * Instructs the printer to emit a locale-specific AM/PM text, and the
     * parser to expect it. The parser is case-insensitive.
     */
    public DateTimeFormatterBuilder appendHalfdayOfDayText() {
        return appendText(iChronoUTC.halfdayOfDay());
    }

    /**
     * Instructs the printer to emit a locale-specific dayOfWeek text. The
     * parser will accept a long or short dayOfWeek text, case-insensitive.
     */
    public DateTimeFormatterBuilder appendDayOfWeekText() {
        return appendText(iChronoUTC.dayOfWeek());
    }

    /**
     * Instructs the printer to emit a short locale-specific dayOfWeek
     * text. The parser will accept a long or short dayOfWeek text,
     * case-insensitive.
     */
    public DateTimeFormatterBuilder appendDayOfWeekShortText() {
        return appendShortText(iChronoUTC.dayOfWeek());
    }

    /**
     * Instructs the printer to emit a short locale-specific monthOfYear
     * text. The parser will accept a long or short monthOfYear text,
     * case-insensitive.
     */
    public DateTimeFormatterBuilder appendMonthOfYearText() { 
        return appendText(iChronoUTC.monthOfYear());
    }

    /**
     * Instructs the printer to emit a locale-specific monthOfYear text. The
     * parser will accept a long or short monthOfYear text, case-insensitive.
     */
    public DateTimeFormatterBuilder appendMonthOfYearShortText() {
        return appendShortText(iChronoUTC.monthOfYear());
    }

    /**
     * Instructs the printer to emit a locale-specific era text (BC/AD), and
     * the parser to expect it. The parser is case-insensitive.
     */
    public DateTimeFormatterBuilder appendEraText() {
        return appendText(iChronoUTC.era());
    }

    /**
     * Instructs the printer to emit a locale-specific time zone name. A
     * parser cannot be created from this builder if a time zone name is
     * appended.
     */
    public DateTimeFormatterBuilder appendTimeZoneName() {
        return append0(new TimeZonePrinter(iChrono, iLocale, false), null);
    }

    /**
     * Instructs the printer to emit a short locale-specific time zone
     * name. A parser cannot be created from this builder if time zone
     * name is appended.
     */
    public DateTimeFormatterBuilder appendTimeZoneShortName() {
        return append0(new TimeZonePrinter(iChrono, iLocale, true), null);
    }

    /**
     * Instructs the printer to emit text and numbers to display time zone
     * offset from UTC. A parser will use the parsed time zone offset to adjust
     * the datetime.
     *
     * @param zeroOffsetText Text to use if time zone offset is zero. If
     * null, offset is always shown.
     * @param showSeparators If true, prints ':' separator before minute and
     * second field and prints '.' separator before fraction field.
     * @param minFields minimum number of fields to print, stopping when no
     * more precision is required. 1=hours, 2=minutes, 3=seconds, 4=fraction
     * @param maxFields maximum number of fields to print
     */
    public DateTimeFormatterBuilder appendTimeZoneOffset(String zeroOffsetText,
                                                         boolean showSeparators,
                                                         int minFields, int maxFields)
    {
        return append0(new TimeZoneOffsetFormatter
                       (iChrono, zeroOffsetText, showSeparators, minFields, maxFields));
    }

    /**
     * The pattern syntax is compatible with java.text.SimpleDateFormat, but a
     * few more symbols are also supported.
     * <p>
     * To specify the time format use a <em>time pattern</em> string.
     * In this pattern, all ASCII letters are reserved as pattern letters,
     * which are defined as the following:
     * <blockquote>
     * <pre>
     * Symbol  Meaning                      Presentation  Examples
     * ------  -------                      ------------  -------
     * G       era                          text          AD
     * C       century of era (&gt;=0)         number        20
     * Y       year of era (&gt;=0)            year          1996
     *
     * x       weekyear                     year          1996
     * w       week of weekyear             number        27
     * e       day of week                  number        2
     * E       day of week                  text          Tuesday; Tue
     *
     * y       year                         year          1996
     * D       day of year                  number        189
     * M       month of year                month         July; Jul; 07
     * d       day of month                 number        10
     *
     * a       halfday of day               text          PM
     * K       hour of halfday (0~11)       number        0
     * h       clockhour of halfday (1~12)  number        12
     *
     * H       hour of day (0~23)           number        0
     * k       clockhour of day (1~24)      number        24
     * m       minute of hour               number        30
     * s       second of minute             number        55
     * S       fraction of second           number        978
     *
     * z       time zone                    text          Pacific Standard Time; PST
     * Z       RFC 822 time zone            text          -0800; -08:00
     *
     * '       escape for text              delimiter
     * ''      single quote                 literal       '
     * </pre>
     * </blockquote>
     * The count of pattern letters determine the format.
     * <p>
     * <strong>Text</strong>: If the number of pattern letters is 4 or more,
     * the full form is used; otherwise a short or abbreviated form is used if
     * available.
     * <p>
     * <strong>Number</strong>: The minimum number of digits. Shorter numbers
     * are zero-padded to this amount.
     * <p>
     * <strong>Year</strong>: Numeric presentation for year and weekyear fields
     * are handled specially. For example, if the count of 'y' is 2, the year
     * will be displayed as the zero-based year of the century, which is two
     * digits.
     * <p>
     * <strong>Month</strong>: 3 or over, use text, otherwise use number.
     * <p>
     * Any characters in the pattern that are not in the ranges of ['a'..'z']
     * and ['A'..'Z'] will be treated as quoted text. For instance, characters
     * like ':', '.', ' ', '#' and '@' will appear in the resulting time text
     * even they are not embraced within single quotes.
     */
    public DateTimeFormatterBuilder appendPattern(String pattern)
        throws IllegalArgumentException
    {
        int length = pattern.length();
        int[] indexRef = new int[1];

        for (int i=0; i<length; i++) {
            indexRef[0] = i;
            String token = parseToken(pattern, indexRef);
            i = indexRef[0];

            int tokenLen = token.length();
            if (tokenLen == 0) {
                break;
            }
            char c = token.charAt(0);

            switch (c) {
            case 'G': // era designator (text)
                appendEraText();
                break;
            case 'C': // century of era (number)
                appendCenturyOfEra(tokenLen, tokenLen);
                break;
            case 'x': // weekyear (number)
            case 'y': // year (number)
            case 'Y': // year of era (number)
                if (tokenLen == 2) {
                    // Use a new RemainderDateTimeField to ensure that the year
                    // of century is zero-based.
                    DateTimeField field;
                    switch (c) {
                    case 'x':
                        field = new RemainderDateTimeField("weekyearOfCentury", iChronoUTC.weekyear(), 100);
                        break;
                    case 'y': default:
                        field = new RemainderDateTimeField("yearOfCentury", iChronoUTC.year(), 100);
                        break;
                    case 'Y':
                        field = new RemainderDateTimeField("yearOfCentury", iChronoUTC.yearOfEra(), 100);
                        break;
                    }
                    appendNumeric(field, 2, 2);
                } else {
                    // Try to support long year values.
                    int maxDigits = 9;

                    // Peek ahead to next token.
                    if (i + 1 < length) {
                        indexRef[0]++;
                        if (isNumericToken(parseToken(pattern, indexRef))) {
                            // If next token is a number, cannot support long years.
                            maxDigits = tokenLen;
                        }
                        indexRef[0]--;
                    }

                    switch (c) {
                    case 'x':
                        appendWeekyear(tokenLen, maxDigits);
                        break;
                    case 'y':
                        appendYear(tokenLen, maxDigits);
                        break;
                    case 'Y':
                        appendYearOfEra(tokenLen, maxDigits);
                        break;
                    }
                }
                break;
            case 'M': // month of year (text and number)
                if (tokenLen >= 3) {
                    if (tokenLen >= 4) {
                        appendMonthOfYearText();
                    } else {
                        appendMonthOfYearShortText();
                    }
                } else {
                    appendMonthOfYear(tokenLen);
                }
                break;
            case 'd': // day of month (number)
                appendDayOfMonth(tokenLen);
                break;
            case 'h': // hour of day (number, 1..12)
                appendClockhourOfHalfday(tokenLen);
                break;
            case 'H': // hour of day (number, 0..23)
                appendHourOfDay(tokenLen);
                break;
            case 'm': // minute of hour (number)
                appendMinuteOfHour(tokenLen);
                break;
            case 's': // second of minute (number)
                appendSecondOfMinute(tokenLen);
                break;
            case 'S': // fraction of second (number)
                appendFractionOfSecond(tokenLen, tokenLen);
                break;
            case 'e': // day of week (number)
                appendDayOfWeek(tokenLen);
                break;
            case 'E': // dayOfWeek (text)
                if (tokenLen >= 4) {
                    appendDayOfWeekText();
                } else {
                    appendDayOfWeekShortText();
                }
                break;
            case 'D': // day of year (number)
                appendDayOfYear(tokenLen);
                break;
            case 'w': // week of weekyear (number)
                appendWeekOfWeekyear(tokenLen);
                break;
            case 'a': // am/pm marker (text)
                appendHalfdayOfDayText();
                break;
            case 'k': // hour of day (1..24)
                appendClockhourOfDay(tokenLen);
                break;
            case 'K': // hour of day (0..11)
                appendClockhourOfHalfday(tokenLen);
                break;
            case 'z': // time zone (text)
                if (tokenLen >= 4) {
                    appendTimeZoneName();
                } else {
                    appendTimeZoneShortName();
                }
                break;
            case 'Z': // RFC 822 time zone
                if (tokenLen >= 4) {
                    appendTimeZoneOffset(null, true, 2, 2);
                } else {
                    appendTimeZoneOffset(null, false, 2, 2);
                }
                break;
            case '\'': // literal text
                String sub = token.substring(1);
                if (sub.length() == 1) {
                    appendLiteral(sub.charAt(0));
                } else {
                    // Create copy of sub since otherwise the temporary quoted
                    // string would still be referenced internally.
                    appendLiteral(new String(sub));
                }
                break;
            default:
                throw new IllegalArgumentException
                    ("Illegal pattern component: " + token);
            }
        }

        return this;
    }

    // Returns true if token should be parsed as a numeric field.
    private boolean isNumericToken(String token) {
        int tokenLen = token.length();
        if (tokenLen > 0) {
            char c = token.charAt(0);
            switch (c) {
            case 'c': // century (number)
            case 'C': // century of era (number)
            case 'x': // weekyear (number)
            case 'y': // year (number)
            case 'Y': // year of era (number)
            case 'd': // day of month (number)
            case 'h': // hour of day (number, 1..12)
            case 'H': // hour of day (number, 0..23)
            case 'm': // minute of hour (number)
            case 's': // second of minute (number)
            case 'S': // fraction of second (number)
            case 'e': // day of week (number)
            case 'D': // day of year (number)
            case 'F': // day of week in month (number)
            case 'w': // week of year (number)
            case 'W': // week of month (number)
            case 'k': // hour of day (1..24)
            case 'K': // hour of day (0..11)
                return true;
            case 'M': // month of year (text and number)
                if (tokenLen <= 2) {
                    return true;
                }
            }
        }
            
        return false;
    }

    private Object getFormatter() {
        Object f = iFormatter;

        if (f == null) {
            if (iElementPairs.size() == 2) {
                Object printer = iElementPairs.get(0);
                Object parser = iElementPairs.get(1);

                if (printer != null) {
                    if (printer == parser || parser == null) {
                        f = printer;
                    }
                } else {
                    f = parser;
                }
            }

            if (f == null) {
                f = new Composite(iChrono, iElementPairs);
            }

            iFormatter = f;
        }

        return f;
    }

    private boolean isPrinter(Object f) {
        if (f instanceof DateTimePrinter) {
            if (f instanceof Composite) {
                return ((Composite)f).isPrinter();
            }
            return true;
        }
        return false;
    }

    private boolean isParser(Object f) {
        if (f instanceof DateTimeParser) {
            if (f instanceof Composite) {
                return ((Composite)f).isParser();
            }
            return true;
        }
        return false;
    }

    private boolean isFormatter(Object f) {
        if (f instanceof DateTimeFormatter) {
            if (f instanceof Composite) {
                return ((Composite)f).isPrinter()
                    && ((Composite)f).isParser();
            }
            return true;
        }
        return false;
    }

    private static abstract class AbstractFormatter extends AbstractDateTimeFormatter {
        protected final Chronology iChrono;

        AbstractFormatter(Chronology chrono) {
            iChrono = chrono;
        }

        public Chronology getChronology() {
            return iChrono;
        }

        protected final DateTimeZone getDateTimeZone() {
            DateTimeZone zone = iChrono.getDateTimeZone();
            return zone == null ? DateTimeZone.UTC : zone;
        }
    }

    private static class CharacterLiteral extends AbstractFormatter
        implements DateTimeFormatter
    {
        private final char iValue;

        CharacterLiteral(Chronology chrono, char value) {
            super(chrono);
            iValue = value;
        }

        public int estimatePrintedLength() {
            return 1;
        }

        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            buf.append(iValue);
        }

        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            out.write(iValue);
        }

        public String print(long millisUTC, DateTimeZone zone, long millisLocal) {
            return String.valueOf(iValue);
        }

        public int estimateParsedLength() {
            return 1;
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            if (position >= text.length()) {
                return ~position;
            }

            char a = text.charAt(position);
            char b = iValue;

            if (a != b) {
                a = Character.toUpperCase(a);
                b = Character.toUpperCase(b);
                if (a != b) {
                    a = Character.toLowerCase(a);
                    b = Character.toLowerCase(b);
                    if (a != b) {
                        return ~position;
                    }
                }
            }

            return position + 1;
        }
    }

    private static class StringLiteral extends AbstractFormatter
        implements DateTimeFormatter
    {
        private final String iValue;

        StringLiteral(Chronology chrono, String value) {
            super(chrono);
            iValue = value;
        }

        public int estimatePrintedLength() {
            return iValue.length();
        }

        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            buf.append(iValue);
        }

        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            out.write(iValue);
        }

        public String print(long millisUTC, DateTimeZone zone, long millisLocal) {
            return iValue;
        }

        public int estimateParsedLength() {
            return iValue.length();
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            if (text.regionMatches(true, position, iValue, 0, iValue.length())) {
                return position + iValue.length();
            }
            return ~position;
        }
    }

    private abstract static class NumberFormatter extends AbstractFormatter
        implements DateTimeFormatter
    {
        protected final DateTimeField iField;
        protected final int iMaxParsedDigits;
        protected final boolean iSigned;

        NumberFormatter(Chronology chrono,
                        DateTimeField field, int maxParsedDigits,
                        boolean signed) {
            super(chrono);
            iField = field;
            iMaxParsedDigits = maxParsedDigits;
            iSigned = signed;
        }

        public int estimateParsedLength() {
            return iMaxParsedDigits;
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            int limit = Math.min(iMaxParsedDigits, text.length() - position);

            boolean negative = false;
            int length = 0;
            while (length < limit) {
                char c = text.charAt(position + length);
                if (length == 0 && (c == '-' || c == '+') && iSigned) {
                    negative = c == '-';
                    if (negative) {
                        length++;
                    } else {
                        // Skip the '+' for parseInt to succeed.
                        position++;
                    }
                    // Expand the limit to disregard the sign character.
                    limit = Math.min(limit + 1, text.length() - position);
                    continue;
                }
                if (c < '0' || c > '9') {
                    break;
                }
                length++;
            }

            if (length == 0) {
                return ~position;
            }

            int value;
            if (length == 3 && negative) {
                value = -FormatUtils.parseTwoDigits(text, position + 1);
            } else if (length == 2) {
                if (negative) {
                    value = text.charAt(position + 1) - '0';
                    value = -value;
                } else {
                    value = FormatUtils.parseTwoDigits(text, position);
                }
            } else if (length == 1 && !negative) {
                value = text.charAt(position) - '0';
            } else {
                String sub = text.substring(position, position + length);
                try {
                    value = Integer.parseInt(sub);
                } catch (NumberFormatException e) {
                    return ~position;
                }
            }

            bucket.saveField(iField, value);

            return position + length;
        }
    }

    private static class UnpaddedNumber extends NumberFormatter {
        UnpaddedNumber(Chronology chrono,
                       DateTimeField field, int maxParsedDigits,
                       boolean signed)
        {
            super(chrono, field, maxParsedDigits, signed);
        }

        public int estimatePrintedLength() {
            return iMaxParsedDigits;
        }

        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            FormatUtils.appendUnpaddedInteger(buf, iField.get(millisLocal));
        }

        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            FormatUtils.writeUnpaddedInteger(out, iField.get(millisLocal));
        }
    }

    private static class PaddedNumber extends NumberFormatter {
        private final int iMinPrintedDigits;

        PaddedNumber(Chronology chrono,
                     DateTimeField field, int maxParsedDigits,
                     boolean signed, int minPrintedDigits)
        {
            super(chrono, field, maxParsedDigits, signed);
            iMinPrintedDigits = minPrintedDigits;
        }

        public int estimatePrintedLength() {
            return iMaxParsedDigits;
        }

        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            FormatUtils.appendPaddedInteger
                (buf, iField.get(millisLocal), iMinPrintedDigits);
        }

        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            FormatUtils.writePaddedInteger
                (out, iField.get(millisLocal), iMinPrintedDigits);
        }
    }

    private static class TextField extends AbstractFormatter
        implements DateTimeFormatter
    {
        private final DateTimeField iField;
        private final Locale iLocale;
        private final boolean iShort;

        TextField(Chronology chrono,
                  DateTimeField field, Locale locale, boolean isShort) {
            super(chrono);
            iField = field;
            iLocale = locale;
            iShort = isShort;
        }

        public int estimatePrintedLength() {
            if (iShort) {
                return iField.getMaximumShortTextLength(iLocale);
            } else {
                return iField.getMaximumTextLength(iLocale);
            }
        }

        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            buf.append(print(millisUTC, zone, millisLocal));
        }
    
        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            out.write(print(millisUTC, zone, millisLocal));
        }

        public final String print(long millisUTC, DateTimeZone zone, long millisLocal) {
            if (iShort) {
                return iField.getAsShortText(millisLocal, iLocale);
            } else {
                return iField.getAsText(millisLocal, iLocale);
            }
        }

        public int estimateParsedLength() {
            return estimatePrintedLength();
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            int limit = text.length();
            int i = position;
            for (; i<limit; i++) {
                char c = text.charAt(i);
                if (c < 'A') {
                    break;
                }
                if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || Character.isLetter(c)) {
                    continue;
                }
                break;
            }

            if (i == position) {
                return ~position;
            }

            bucket.saveField(iField, text.substring(position, i), iLocale);

            return i;
        }
    }

    private static class Fraction extends AbstractFormatter
        implements DateTimeFormatter
    {
        private final int iMinDigits;
        private final int iMaxDigits;
        private final int iRange;

        private final long iScaler;

        private transient DateTimeField iField;

        Fraction(Chronology chrono,
                 int minDigits, int maxDigits, int rangeInMillis) {
            super(chrono);

            // Limit the precision requirements.
            if (maxDigits > 18) {
                maxDigits = 18;
            }

            iMinDigits = minDigits;
            iRange = rangeInMillis;

            long scaler;
            while (true) {
                switch (maxDigits) {
                default: scaler = 1L; break;
                case 1:  scaler = 10L; break;
                case 2:  scaler = 100L; break;
                case 3:  scaler = 1000L; break;
                case 4:  scaler = 10000L; break;
                case 5:  scaler = 100000L; break;
                case 6:  scaler = 1000000L; break;
                case 7:  scaler = 10000000L; break;
                case 8:  scaler = 100000000L; break;
                case 9:  scaler = 1000000000L; break;
                case 10: scaler = 10000000000L; break;
                case 11: scaler = 100000000000L; break;
                case 12: scaler = 1000000000000L; break;
                case 13: scaler = 10000000000000L; break;
                case 14: scaler = 100000000000000L; break;
                case 15: scaler = 1000000000000000L; break;
                case 16: scaler = 10000000000000000L; break;
                case 17: scaler = 100000000000000000L; break;
                case 18: scaler = 1000000000000000000L; break;
                }
                if (((rangeInMillis * scaler) / scaler) == rangeInMillis) {
                    break;
                }
                // Overflowed: scale down.
                maxDigits--;
            }

            iMaxDigits = maxDigits;
            iScaler = scaler;
        }

        public int estimatePrintedLength() {
            return iMaxDigits;
        }

        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            try {
                printTo(buf, null, millisLocal);
            } catch (IOException e) {
                // Not gonna happen.
            }
        }

        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            printTo(null, out, millisLocal);
        }

        private void printTo(StringBuffer buf, Writer out, long millis)
            throws IOException
        {
            long fraction;
            if (millis >= 0) {
                fraction = millis % iRange;
            } else {
                fraction = iRange - 1 + (millis + 1) % iRange;
            }

            int minDigits = iMinDigits;

            if (fraction == 0) {
                if (buf != null) {
                    while (--minDigits >= 0) {
                        buf.append('0');
                    }
                } else {
                    while (--minDigits >= 0) {
                        out.write('0');
                    }
                }
                return;
            }

            String str;
            long scaled = fraction * iScaler / iRange;
            if ((scaled & 0x7fffffff) == scaled) {
                str = Integer.toString((int)scaled);
            } else {
                str = Long.toString(scaled);
            }

            int length = str.length();
            int digits = iMaxDigits;

            while (length < digits) {
                if (buf != null) {
                    buf.append('0');
                } else {
                    out.write('0');
                }
                minDigits--;
                digits--;
            }

            if (minDigits < digits) {
                // Chop off as many trailing zero digits as necessary.
                while (minDigits < digits) {
                    if (length <= 1 || str.charAt(length - 1) != '0') {
                        break;
                    }
                    digits--;
                    length--;
                }
                if (length < str.length()) {
                    if (buf != null) {
                        for (int i=0; i<length; i++) {
                            buf.append(str.charAt(i));
                        }
                    } else {
                        for (int i=0; i<length; i++) {
                            out.write(str.charAt(i));
                        }
                    }
                    return;
                }
            }

            if (buf != null) {
                buf.append(str);
            } else {
                out.write(str);
            }
        }

        public int estimateParsedLength() {
            return iMaxDigits;
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            int limit = Math.min(iMaxDigits, text.length() - position);

            long value = 0;
            long n = iRange;
            int length = 0;
            while (length < limit) {
                char c = text.charAt(position + length);
                if (c < '0' || c > '9') {
                    break;
                }
                length++;
                if (c != '0') {
                    value += (c - '0') * n / 10;
                }
                n /= 10;
            }

            if (length == 0) {
                return ~position;
            }

            if (value > Integer.MAX_VALUE) {
                return ~position;
            }

            if (iField == null) {
                iField = new FractionalDateTimeField("", 1, iRange);
            }

            bucket.saveField(iField, (int)value);

            return position + length;
        }
    }

    private static class TimeZoneOffsetFormatter extends AbstractFormatter
        implements DateTimeFormatter
    {
        private final String iZeroOffsetText;
        private final boolean iShowSeparators;
        private final int iMinFields;
        private final int iMaxFields;

        TimeZoneOffsetFormatter(Chronology chrono,
                                String zeroOffsetText,
                                boolean showSeparators,
                                int minFields, int maxFields)
        {
            super(chrono);
            iZeroOffsetText = zeroOffsetText;
            iShowSeparators = showSeparators;
            if (minFields <= 0 || maxFields < minFields) {
                throw new IllegalArgumentException();
            }
            if (minFields > 4) {
                minFields = 4;
                maxFields = 4;
            }
            iMinFields = minFields;
            iMaxFields = maxFields;
        }
            
        public int estimatePrintedLength() {
            int est = 1 + iMinFields << 1;
            if (iShowSeparators) {
                est += iMinFields - 1;
            }
            if (iZeroOffsetText != null && iZeroOffsetText.length() > est) {
                est = iZeroOffsetText.length();
            }
            return est;
        }
        
        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            int offset = (int)(millisLocal - millisUTC);

            if (offset == 0 && iZeroOffsetText != null) {
                buf.append(iZeroOffsetText);
                return;
            }
            if (offset >= 0) {
                buf.append('+');
            } else {
                buf.append('-');
                offset = -offset;
            }

            int hours = offset / DateTimeConstants.MILLIS_PER_HOUR;
            FormatUtils.appendPaddedInteger(buf, hours, 2);
            if (iMaxFields == 1) {
                return;
            }
            offset -= hours * (int)DateTimeConstants.MILLIS_PER_HOUR;
            if (offset == 0 && iMinFields <= 1) {
                return;
            }

            int minutes = offset / DateTimeConstants.MILLIS_PER_MINUTE;
            if (iShowSeparators) {
                buf.append(':');
            }
            FormatUtils.appendPaddedInteger(buf, minutes, 2);
            if (iMaxFields == 2) {
                return;
            }
            offset -= minutes * DateTimeConstants.MILLIS_PER_MINUTE;
            if (offset == 0 && iMinFields <= 2) {
                return;
            }

            int seconds = offset / DateTimeConstants.MILLIS_PER_SECOND;
            if (iShowSeparators) {
                buf.append(':');
            }
            FormatUtils.appendPaddedInteger(buf, seconds, 2);
            if (iMaxFields == 3) {
                return;
            }
            offset -= seconds * DateTimeConstants.MILLIS_PER_SECOND;
            if (offset == 0 && iMinFields <= 3) {
                return;
            }

            if (iShowSeparators) {
                buf.append('.');
            }
            FormatUtils.appendPaddedInteger(buf, offset, 3);
        }
        
        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            int offset = (int)(millisLocal - millisUTC);

            if (offset == 0 && iZeroOffsetText != null) {
                out.write(iZeroOffsetText);
                return;
            }
            if (offset >= 0) {
                out.write('+');
            } else {
                out.write('-');
                offset = -offset;
            }

            int hours = offset / DateTimeConstants.MILLIS_PER_HOUR;
            FormatUtils.writePaddedInteger(out, hours, 2);
            if (iMaxFields == 1) {
                return;
            }
            offset -= hours * (int)DateTimeConstants.MILLIS_PER_HOUR;
            if (offset == 0 && iMinFields == 1) {
                return;
            }

            int minutes = offset / DateTimeConstants.MILLIS_PER_MINUTE;
            if (iShowSeparators) {
                out.write(':');
            }
            FormatUtils.writePaddedInteger(out, minutes, 2);
            if (iMaxFields == 2) {
                return;
            }
            offset -= minutes * DateTimeConstants.MILLIS_PER_MINUTE;
            if (offset == 0 && iMinFields == 2) {
                return;
            }

            int seconds = offset / DateTimeConstants.MILLIS_PER_SECOND;
            if (iShowSeparators) {
                out.write(':');
            }
            FormatUtils.writePaddedInteger(out, seconds, 2);
            if (iMaxFields == 3) {
                return;
            }
            offset -= seconds * DateTimeConstants.MILLIS_PER_SECOND;
            if (offset == 0 && iMinFields == 3) {
                return;
            }

            if (iShowSeparators) {
                out.write('.');
            }
            FormatUtils.writePaddedInteger(out, offset, 3);
        }

        public int estimateParsedLength() {
            return estimatePrintedLength();
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            if (iZeroOffsetText != null) {
                if (text.regionMatches(true, position, iZeroOffsetText, 0,
                                       iZeroOffsetText.length())) {
                    bucket.setOffset(0);
                    return position + iZeroOffsetText.length();
                }
            }

            // Format to expect is sign character followed by at least one digit.

            int limit = text.length() - position;
            if (limit <= 1) {
                return ~position;
            }

            boolean negative;
            char c = text.charAt(position);
            if (c == '-') {
                negative = true;
            } else if (c == '+') {
                negative = false;
            } else {
                return ~position;
            }

            limit--;
            position++;

            // Format following sign is one of:
            //
            // hh
            // hhmm
            // hhmmss
            // hhmmssSSS
            // hh:mm
            // hh:mm:ss
            // hh:mm:ss.SSS

            // First parse hours.

            if (digitCount(text, position, 2) < 2) {
                // Need two digits for hour.
                return ~position;
            }

            int offset;

            int hours = FormatUtils.parseTwoDigits(text, position);
            if (hours > 23) {
                return ~position;
            }
            offset = hours * DateTimeConstants.MILLIS_PER_HOUR;
            limit -= 2;
            position += 2;

            parse: {
                // Need to decide now if separators are expected or parsing
                // stops at hour field.

                if (limit <= 0) {
                    break parse;
                }

                boolean expectSeparators;
                c = text.charAt(position);
                if (c == ':') {
                    expectSeparators = true;
                    limit--;
                    position++;
                } else if (c >= '0' && c <= '9') {
                    expectSeparators = false;
                } else {
                    break parse;
                }

                // Proceed to parse minutes.

                int count = digitCount(text, position, 2);
                if (count == 0 && !expectSeparators) {
                    break parse;
                } else if (count < 2) {
                    // Need two digits for minute.
                    return ~position;
                }

                int minutes = FormatUtils.parseTwoDigits(text, position);
                if (minutes > 59) {
                    return ~position;
                }
                offset += minutes * DateTimeConstants.MILLIS_PER_MINUTE;
                limit -= 2;
                position += 2;

                // Proceed to parse seconds.

                if (limit <= 0) {
                    break parse;
                }

                if (expectSeparators) {
                    if (text.charAt(position) != ':') {
                        break parse;
                    }
                    limit--;
                    position++;
                }

                count = digitCount(text, position, 2);
                if (count == 0 && !expectSeparators) {
                    break parse;
                } else if (count < 2) {
                    // Need two digits for second.
                    return ~position;
                }

                int seconds = FormatUtils.parseTwoDigits(text, position);
                if (seconds > 59) {
                    return ~position;
                }
                offset += seconds * DateTimeConstants.MILLIS_PER_SECOND;
                limit -= 2;
                position += 2;

                // Proceed to parse fraction of second.

                if (limit <= 0) {
                    break parse;
                }

                if (expectSeparators) {
                    if (text.charAt(position) != '.') {
                        break parse;
                    }
                    limit--;
                    position++;
                }
                
                count = digitCount(text, position, 3);
                if (count == 0 && !expectSeparators) {
                    break parse;
                } else if (count < 1) {
                    // Need at least one digit for fraction of second.
                    return ~position;
                }

                offset += (text.charAt(position++) - '0') * 100;
                if (count > 1) {
                    offset += (text.charAt(position++) - '0') * 10;
                    if (count > 2) {
                        offset += text.charAt(position++) - '0';
                    }
                }
            }

            bucket.setOffset(negative ? -offset : offset);
            return position;
        }

        /**
         * Returns actual amount of digits to parse, but no more than original
         * 'amount' parameter.
         */
        private int digitCount(String text, int position, int amount) {
            int limit = Math.min(text.length() - position, amount);
            amount = 0;
            for (; limit > 0; limit--) {
                char c = text.charAt(position + amount);
                if (c < '0' || c > '9') {
                    break;
                }
                amount++;
            }
            return amount;
        }
    }

    private static class TimeZonePrinter extends AbstractFormatter
        implements DateTimePrinter 
    {
        private final Locale iLocale;
        private final boolean iShortFormat;

        TimeZonePrinter(Chronology chrono, Locale locale, boolean shortFormat) {
            super(chrono);
            iLocale = locale;
            iShortFormat = shortFormat;
        }

        public int estimatePrintedLength() {
            return iShortFormat ? 4 : 20;
        }
        
        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            if (zone == null) {
                zone = getDateTimeZone();
            }
            if (iShortFormat) {
                buf.append(zone.getShortName(millisUTC, this.iLocale));
            } else {
                buf.append(zone.getName(millisUTC, this.iLocale));
            }
        }
        
        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            if (zone == null) {
                zone = getDateTimeZone();
            }
            if (iShortFormat) {
                out.write(zone.getShortName(millisUTC, this.iLocale));
            } else {
                out.write(zone.getName(millisUTC, this.iLocale));
            }
        }

        public String print(long millisUTC, DateTimeZone zone, long millisLocal) {
            if (zone == null) {
                zone = getDateTimeZone();
            }
            if (iShortFormat) {
                return zone.getShortName(millisUTC, this.iLocale);
            } else {
                return zone.getName(millisUTC, this.iLocale);
            }
        }
    }

    private static final class Composite extends AbstractFormatter
        implements DateTimeFormatter
    {
        private final DateTimePrinter[] iPrinters;
        private final DateTimeParser[] iParsers;

        private final int iPrintedLengthEstimate;
        private final int iParsedLengthEstimate;

        Composite(Chronology chrono, ArrayList elementPairs) {
            super(chrono);

            int len = elementPairs.size() / 2;

            boolean isPrinter = true;
            boolean isParser = true;

            int printEst = 0;
            int parseEst = 0;

            DateTimePrinter[] printers = new DateTimePrinter[len];
            DateTimeParser[] parsers = new DateTimeParser[len];
            for (int i=0; i<len; i++) {
                Object element = elementPairs.get(i * 2);
                if (element == null || !(element instanceof DateTimePrinter)) {
                    isPrinter = false;
                } else {
                    DateTimePrinter printer = (DateTimePrinter)element;
                    printEst += printer.estimatePrintedLength();
                    printers[i] = printer;
                }

                element = elementPairs.get(i * 2 + 1);
                if (element == null || !(element instanceof DateTimeParser)) {
                    isParser = false;
                } else {
                    DateTimeParser parser = (DateTimeParser)element;
                    parseEst += parser.estimateParsedLength();
                    parsers[i] = parser;
                }
            }

            if (!isPrinter) {
                printers = null;
            }
            if (!isParser) {
                parsers = null;
            }

            iPrinters = printers;
            iParsers = parsers;
            iPrintedLengthEstimate = printEst;
            iParsedLengthEstimate = parseEst;
        }

        public int estimatePrintedLength() {
            return iPrintedLengthEstimate;
        }
    
        public void printTo(StringBuffer buf, long millisUTC,
                            DateTimeZone zone, long millisLocal) {
            DateTimePrinter[] elements = iPrinters;

            if (elements == null) {
                throw new UnsupportedOperationException();
            }

            int len = elements.length;
            for (int i=0; i<len; i++) {
                elements[i].printTo(buf, millisUTC, zone, millisLocal);
            }
        }

        public void printTo(Writer out, long millisUTC,
                            DateTimeZone zone, long millisLocal) throws IOException {
            DateTimePrinter[] elements = iPrinters;

            if (elements == null) {
                throw new UnsupportedOperationException();
            }

            int len = elements.length;
            for (int i=0; i<len; i++) {
                elements[i].printTo(out, millisUTC, zone, millisLocal);
            }
        }

        public int estimateParsedLength() {
            return iParsedLengthEstimate;
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            DateTimeParser[] elements = iParsers;

            if (elements == null) {
                throw new UnsupportedOperationException();
            }

            int len = elements.length;
            for (int i=0; i<len && position >= 0; i++) {
                position = elements[i].parseInto(bucket, text, position);
            }
            return position;
        }

        boolean isPrinter() {
            return iPrinters != null;
        }

        boolean isParser() {
            return iParsers != null;
        }
    }

    private static final class MatchingParser extends AbstractFormatter
        implements DateTimeParser
    {
        private final DateTimeParser[] iParsers;
        private final int iParsedLengthEstimate;

        MatchingParser(Chronology chrono, DateTimeParser[] parsers) {
            super(chrono);
            iParsers = parsers;
            int est = 0;
            for (int i=parsers.length; --i>=0 ;) {
                DateTimeParser parser = parsers[i];
                if (parser != null) {
                    int len = parser.estimateParsedLength();
                    if (len > est) {
                        len = est;
                    }
                }
            }
            iParsedLengthEstimate = est;
        }

        public int estimateParsedLength() {
            return iParsedLengthEstimate;
        }

        public int parseInto(DateTimeParserBucket bucket, String text, int position) {
            DateTimeParser[] parsers = iParsers;
            int length = parsers.length;

            Object state = bucket.saveState();
            
            int bestInvalidPos = position;
            int bestInvalidParser = 0;
            int bestValidPos = position;
            int bestValidParser = 0;

            for (int i=0; i<length; i++) {
                if (i != 0) {
                    bucket.undoChanges(state);
                }

                DateTimeParser parser = parsers[i];
                if (parser == null) {
                    // The empty parser wins only if nothing is better.
                    if (bestValidPos > position) {
                        break;
                    }
                    return position;
                }

                int parsePos = parser.parseInto(bucket, text, position);
                if (parsePos >= position) {
                    if (parsePos >= text.length()) {
                        return parsePos;
                    }
                    if (parsePos > bestValidPos) {
                        bestValidPos = parsePos;
                        bestValidParser = i;
                    }
                } else {
                    parsePos = ~parsePos;
                    if (parsePos > bestInvalidPos) {
                        bestInvalidPos = parsePos;
                        bestInvalidParser = i;
                    }
                }
            }

            if (bestValidPos > position) {
                if (bestValidParser == length - 1) {
                    // The best valid parser was the last one, so the bucket is
                    // already in the best state.
                    return bestValidPos;
                }
                bucket.undoChanges(state);
                // Call best valid parser again to restore bucket state.
                return parsers[bestValidParser].parseInto(bucket, text, position);
            }

            if (bestInvalidParser == length - 1) {
                // The best invalid parser was the last one, so the bucket is
                // already in the best state.
                return ~bestInvalidPos;
            }

            bucket.undoChanges(state);
            // Call best invalid parser again to restore bucket state.
            return parsers[bestInvalidParser].parseInto(bucket, text, position);
        }
    }
}
