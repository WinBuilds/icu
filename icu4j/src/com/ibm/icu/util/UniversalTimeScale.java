/*
 **************************************************************************
 * Copyright (C) 2004, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                           *
 **************************************************************************
 *
 */

package com.ibm.icu.util;

import com.ibm.icu.math.BigDecimal;
import com.ibm.icu.text.MessageFormat;
import java.lang.IllegalArgumentException;

/** 
 * There are quite a few different conventions for binary datetime, depending on different
 * platforms and protocols. Some of these have severe drawbacks. For example, people using
 * Unix time (seconds since Jan 1, 1970) think that they are safe until near the year 2038.
 * But cases can and do arise where arithmetic manipulations causes serious problems. Consider
 * the computation of the average of two datetimes, for example: if one calculates them with
 * <code>averageTime = (time1 + time2)/2</code>, there will be overflow even with dates
 * around the present. Moreover, even if these problems don't occur, there is the issue of
 * conversion back and forth between different systems.
 *
 * <p>
 * Binary datetimes differ in a number of ways: the datatype, the unit,
 * and the epoch (origin). We'll refer to these as time scales. For example:
 *
 * <table border="1" cellspacing="0" cellpadding="4">
 *  <caption>
 *    <h3>Table 1: Binary Time Scales</h3>
 *
 *  </caption>
 *  <tr>
 *    <th align="left">Source</th>
 *    <th align="left">Datatype</th>
 *    <th align="left">Unit</th>
 *    <th align="left">Epoch</th>
 *  </tr>
 *
 *  <tr>
 *    <td>JAVA_TIME</td>
 *    <td>long</td>
 *    <td>milliseconds</td>
 *    <td>Jan 1, 1970</td>
 *  </tr>
 *  <tr>
 *
 *    <td>UNIX_TIME</td>
 *    <td>int or long</td>
 *    <td>seconds</td>
 *    <td>Jan 1, 1970</td>
 *  </tr>
 *  <tr>
 *    <td>ICU4C</td>
 *
 *    <td>double</td>
 *    <td>milliseconds</td>
 *    <td>Jan 1, 1970</td>
 *  </tr>
 *  <tr>
 *    <td>WINDOWS_FILE_TIME</td>
 *    <td>long</td>
 *
 *    <td>ticks (100 nanoseconds)</td>
 *    <td>Jan 1, 1601</td>
 *  </tr>
 *  <tr>
 *    <td>WINDOWS_DATE_TIME</td>
 *    <td>long</td>
 *    <td>ticks (100 nanoseconds)</td>
 *
 *    <td>Jan 1, 0001</td>
 *  </tr>
 *  <tr>
 *    <td>MAC_OLD_TIME</td>
 *    <td>int</td>
 *    <td>seconds</td>
 *    <td>Jan 1, 1904</td>
 *
 *  </tr>
 *  <tr>
 *    <td>MAC_TIME</td>
 *    <td>double</td>
 *    <td>seconds</td>
 *    <td>Jan 1, 2001</td>
 *  </tr>
 *
 *  <tr>
 *    <td>EXCEL_TIME</td>
 *    <td>?</td>
 *    <td>days</td>
 *    <td>Dec 31, 1899</td>
 *  </tr>
 *  <tr>
 *
 *    <td>DB2_TIME</td>
 *    <td>?</td>
 *    <td>days</td>
 *    <td>Dec 31, 1899</td>
 *  </tr>
 * </table>
 *
 * <p>
 * All of the epochs start at 00:00 am (the earliest possible time on the day in question),
 * and are assumed to be UTC.
 *
 * <p>
 * The ranges for different datatypes are given in the following table (all values in years).
 * The range of years includes the entire range expressible with positive and negative
 * values of the datatype. The range of years for double is the range that would be allowed
 * without losing precision to the corresponding unit.
 *
 * <table border="1" cellspacing="0" cellpadding="4">
 *  <tr>
 *    <th align="left">Units</th>
 *    <th align="left">long</th>
 *    <th align="left">double</th>
 *    <th align="left">int</th>
 *  </tr>
 *
 *  <tr>
 *    <td>1 sec</td>
 *    <td align="right">5.84542�10��</td>
 *    <td align="right">285,420,920.94</td>
 *    <td align="right">136.10</td>
 *  </tr>
 *  <tr>
 *
 *    <td>1 millisecond</td>
 *    <td align="right">584,542,046.09</td>
 *    <td align="right">285,420.92</td>
 *    <td align="right">0.14</td>
 *  </tr>
 *  <tr>
 *    <td>1 microsecond</td>
 *
 *    <td align="right">584,542.05</td>
 *    <td align="right">285.42</td>
 *    <td align="right">0.00</td>
 *  </tr>
 *  <tr>
 *    <td>100 nanoseconds (tick)</td>
 *    <td align="right">58,454.20</td>
 *    <td align="right">28.54</td>
 *    <td align="right">0.00</td>
 *  </tr>
 *  <tr>
 *    <td>1 nanosecond</td>
 *    <td align="right">584.5420461</td>
 *    <td align="right">0.2854</td>
 *    <td align="right">0.00</td>
 *  </tr>
 * </table>
 *
 * <p>
 * This class implements a universal time scale which can be used as a 'pivot',
 * and provide conversion functions to and from all other major time scales.
 * This datetimes to be converted to the pivot time, safely manipulated,
 * and converted back to any other datetime time scale.
 *
 *<p>
 * So what to use for this pivot? Java time has plenty of range, but cannot represent
 * Windows datetimes without severe loss of precision. ICU4C time addresses this by using a
 * <code>double</code> that is otherwise equivalent to the Java time. However, there are disadvantages
 * with <code>doubles</code>. They provide for much more graceful degradation in arithmetic operations.
 * But they only have 53 bits of accuracy, which means that they will lose precision when
 * converting back and forth to ticks. What would really be nice would be a
 * <code>long double</code> (80 bits -- 64 bit mantissa), but that is not supported on most systems.
 *
 *<p>
 * The Unix extended time uses a structure with two components: time in seconds and a
 * fractional field (microseconds). However, this is clumsy, slow, and
 * prone to error (you always have to keep track of overflow and underflow in the
 * fractional field). <code>BigDecimal</code> would allow for arbitrary precision and arbitrary range,
 * but we would not want to use this as the normal type, because it is slow and does not
 * have a fixed size.
 *
 *<p>
 * Because of these issues, we ended up concluding that the Windows datetime would be the
 * best pivot. However, we use the full range allowed by the datatype, allowing for
 * datetimes back to 29,000 BC and up to 29,000 AD. This time scale is very fine grained,
 * does not lose precision, and covers a range that will meet almost all requirements.
 * It will not handle the range that Java times would, but frankly, being able to handle dates
 * before 29,000 BC or after 29,000 AD is of very limited interest. However, for those cases,
 * we also allow conversion to an optional <code>BigDecimal</code> format that would have arbitrary
 * precision and range.
 *
 */

public final class UniversalTimeScale
{
    /**
     * Used in the JDK. Data is a <code>long</code>. Value
     * is milliseconds since January 1, 1970.
     *
     * @draft ICU 3.2
     */
    public static final int JAVA_TIME = 0;

    /**
     * Used in Unix systems. Data is an <code>int> or a <code>long</code>. Value
     * is seconds since January 1, 1970.
     *
     * @draft ICU 3.2
     */
    public static final int UNIX_TIME = 1;

    /**
     * Used in the ICU4C. Data is a <code>double</code>. Value
     * is milliseconds since January 1, 1970.
     *
     * @draft ICU 3.2
     */
    public static final int ICU4C_TIME = 2;

    /**
     * Used in Windows for file times. Data is a <code>long</code>. Value
     * is ticks (1 tick == 100 nanoseconds) since January 1, 1601.
     *
     * @draft ICU 3.2
     */
    public static final int WINDOWS_FILE_TIME = 3;

    /**
     * Used in Windows for date time (?). Data is a <code>long</code>. Value
     * is ticks (1 tick == 100 nanoseconds) since January 1, 0001.
     *
     * @draft ICU 3.2
     */
    public static final int WINDOWS_DATE_TIME = 4;

    /**
     * Used in older Macintosh systems. Data is an <code>int</code>. Value
     * is seconds since January 1, 1904.
     *
     * @draft ICU 3.2
     */
    public static final int MAC_OLD_TIME = 5;

    /**
     * Used in the JDK. Data is a <code>double</code>. Value
     * is milliseconds since January 1, 2001.
     *
     * @draft ICU 3.2
     */
    public static final int MAC_TIME = 6;

    /**
     * Used in Excel. Data is a <code>?unknown?</code>. Value
     * is days since December 31, 1899.
     *
     * @draft ICU 3.2
     */
    public static final int EXCEL_TIME = 7;

    /**
     * Used in DB2. Data is a <code>?unknown?</code>. Value
     * is days since December 31, 1899.
     *
     * @draft ICU 3.2
     */
    public static final int DB2_TIME = 8;
    
    /**
     * This is the first unused time scale value.
     *
     * @draft ICU 3.2
     */
    public static final int MAX_SCALE = 9;
    
    /**
     * The constant used to select the units vale
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    
    /**
     * The constant used to select the units vale
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int UNITS_VALUE = 0;
    
    /**
     * The constant used to select the epoch offset value
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int EPOCH_OFFSET_VALUE = 1;
    
    /**
     * The constant used to select the minimum from value
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int FROM_MIN_VALUE = 2;
    
    /**
     * The constant used to select the maximum from value
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int FROM_MAX_VALUE = 3;
    
    /**
     * The constant used to select the minimum to value
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int TO_MIN_VALUE = 4;
    
    /**
     * The constant used to select the maximum to value
     * for a time scale.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int TO_MAX_VALUE = 5;
    
    /**
     * The constant used to select the epoch plus one value
     * for a time scale.
     * 
     * NOTE: This is an internal value. DO NOT USE IT. May not
     * actually be equal to the epoch offset value plus one.
     * 
     * @see getTimeScaleValue
     *
     * @draft ICU 3.2
     */
    public static final int EPOCH_OFFSET_PLUS_1_VALUE = 6;
    
    /**
     * The constant used to select the epoch offset minus one value
     * for a time scale.
     * 
     * NOTE: This is an internal value. DO NOT USE IT. May not
     * actually be equal to the epoch offset value minus one.
     * 
     * @see getTimeScaleValue
     *
     * @internal
     */
    public static final int EPOCH_OFFSET_MINUS_1_VALUE = 7;
    
    /**
     * The constant used to select the units round value
     * for a time scale.
     * 
     * NOTE: This is an internal value. DO NOT USE IT.
     * 
     * @see getTimeScaleValue
     *
     * @internal
     */
    public static final int UNITS_ROUND_VALUE = 8;
    
    /**
     * The constant used to select the minimum safe rounding value
     * for a time scale.
     * 
     * NOTE: This is an internal value. DO NOT USE IT.
     * 
     * @see getTimeScaleValue
     *
     * @internal
     */
    public static final int MIN_ROUND_VALUE = 9;
    
    /**
     * The constant used to select the maximum safe rounding value
     * for a time scale.
     * 
     * NOTE: This is an internal value. DO NOT USE IT.
     * 
     * @see getTimeScaleValue
     *
     * @internal
     */
    public static final int MAX_ROUND_VALUE = 10;
    
    /**
     * The number of time scale values.
     * 
     * NOTE: This is an internal value. DO NOT USE IT.
     * 
     * @see getTimeScaleValue
     *
     * @internal
     */
    public static final int MAX_SCALE_VALUE = 11;
    
    private static final long ticks        = 1;
    private static final long microseconds = ticks * 10;
    private static final long milliseconds = microseconds * 1000;
    private static final long seconds      = milliseconds * 1000;
    private static final long minutes      = seconds * 60;
    private static final long hours        = minutes * 60;
    private static final long days         = hours * 24;
    
    /**
     * This class holds the data that describes a particular
     * time scale.
     *
     * @draft ICU 3.2
     */
    private static final class TimeScaleData
    {
        TimeScaleData(long theUnits, long theEpochOffset,
                       long theToMin, long theToMax,
                       long theFromMin, long theFromMax)
        {
            units      = theUnits;
            unitsRound = theUnits / 2;
            
            minRound = Long.MIN_VALUE + unitsRound;
            maxRound = Long.MAX_VALUE - unitsRound;
                        
            epochOffset   = theEpochOffset / theUnits;
            
            if (theUnits == 1) {
                epochOffsetP1 = epochOffsetM1 = epochOffset;
            } else {
                epochOffsetP1 = epochOffset + 1;
                epochOffsetM1 = epochOffset - 1;
            }
            
            toMin = theToMin;
            toMax = theToMax;
            
            fromMin = theFromMin;
            fromMax = theFromMax;
        }
        
        /**
         * The units of the time scale, expressed in ticks.
         * 
         * @draft ICU 3.2
         */
        public long units;
        
        /**
         * The distance from the Universal Time Scale's epoch to the
         * time scale's epoch expressed in the time scale's units.
         * 
         * @draft ICU 3.2
         */
        public long epochOffset;
        
        /**
         * The minimum time scale value that can be conveted
         * to the Universal Time Scale without underflowing.
         * 
         * @draft ICU 3.2
         */
        public long fromMin;
        
        /**
         * The maximum time scale value that can be conveted
         * to the Universal Time Scale without overflowing.
         * 
         * @draft ICU 3.2
         */
        public long fromMax;
        
        /**
         * The minimum Universal Time Scale value that can
         * be converted to the time scale without underflowing.
         * 
         * @draft ICU 3.2
         */
        public long toMin;
        
        /**
         * The maximum Universal Time Scale value that can
         * be converted to the time scale without overflowing.
         * 
         * @draft ICU 3.2
         */
        public long toMax;
        
        long epochOffsetP1;
        long epochOffsetM1;
        long unitsRound;
        long minRound;
        long maxRound;
    }
    
    private static final TimeScaleData[] timeScaleTable = {
            new TimeScaleData(milliseconds, 621357696000000000L, -9223372036854774999L, 9223372036854774999L, -984472973285477L,         860201434085477L), // JAVA_TIME
            new TimeScaleData(seconds,      621357696000000000L, -9223372036854775808L, 9223372036854775807L, -984472973285L,               860201434085L), // UNIX_TIME
            new TimeScaleData(milliseconds, 621357696000000000L, -9223372036854774999L, 9223372036854774999L, -984472973285477L,         860201434085477L), // ICU4C_TIME
            new TimeScaleData(ticks,        504912960000000000L, -8718459076854775808L, 9223372036854775807L, -9223372036854775808L, 8718459076854775807L), // WINDOWS_FILE_TIME
            new TimeScaleData(ticks,        000000000000000000L, -9223372036854775808L, 9223372036854775807L, -9223372036854775808L, 9223372036854775807L), // WINDOWS_DATE_TIME
            new TimeScaleData(seconds,      600529248000000000L, -9223372036854775808L, 9223372036854775807L, -982390128485L,               862284278885L), // MAC_OLD_TIME
            new TimeScaleData(seconds,      631140768000000000L, -9223372036854775808L, 9223372036854775807L, -985451280485L,               859223126885L), // MAC_TIME
            new TimeScaleData(days,         599266944000000000L, -9223372036854775808L, 9223372036854775807L, -11368795L,                        9981603L), // EXCEL_TIME
            new TimeScaleData(days,         599266944000000000L, -9223372036854775808L, 9223372036854775807L, -11368795L,                        9981603L)  // DB2_TIME
    };
    
    /**
     * Convert a <code>double</code> datetime from the given time scale to the universal time scale.
     *
     * @param otherTime The <code>double</code> datetime
     * @param timeScale The time scale to convert from
     * 
     * @return The datetime converted to the universal time scale
     *
     * @draft ICU 3.2
     */
    public static long from(double otherTime, int timeScale)
    {
        TimeScaleData data = fromRangeCheck(otherTime, timeScale);
        
        return ((long)otherTime + data.epochOffset) * data.units;
    }

    /**
     * Convert a <code>long</code> datetime from the given time scale to the universal time scale.
     *
     * @param otherTime The <code>long</code> datetime
     * @param timeScale The time scale to convert from
     * 
     * @return The datetime converted to the universal time scale
     *
     * @draft ICU 3.2
     */
    public static long from(long otherTime, int timeScale)
    {
        TimeScaleData data = fromRangeCheck(otherTime, timeScale);
                
        return (otherTime + data.epochOffset) * data.units;
    }

    /**
     * Convert a <code>double</code> datetime from the given time scale to the universal time scale.
     * All calculations are done using <code>BigDecimal</code> to guarantee that the value
     * does not go out of range.
     *
     * @param otherTime The <code>double</code> datetime
     * @param timeScale The time scale to convert from
     * 
     * @return The datetime converted to the universal time scale
     *
     * @draft ICU 3.2
     */
    public static BigDecimal bigDecimalFrom(double otherTime, int timeScale)
    {
        TimeScaleData data     = getTimeScaleData(timeScale);
        BigDecimal other       = new BigDecimal(otherTime);
        BigDecimal units       = new BigDecimal(data.units);
        BigDecimal epochOffset = new BigDecimal(data.epochOffset);
        
        return other.add(epochOffset).multiply(units);
    }

    /**
     * Convert a <code>long</code> datetime from the given time scale to the universal time scale.
     * All calculations are done using <code>BigDecimal</code> to guarantee that the value
     * does not go out of range.
     *
     * @param otherTime The <code>long</code> datetime
     * @param timeScale The time scale to convert from
     * 
     * @return The datetime converted to the universal time scale
     *
     * @draft ICU 3.2
     */
    public static BigDecimal bigDecimalFrom(long otherTime, int timeScale)
    {
        TimeScaleData data     = getTimeScaleData(timeScale);
        BigDecimal other       = new BigDecimal(otherTime);
        BigDecimal units       = new BigDecimal(data.units);
        BigDecimal epochOffset = new BigDecimal(data.epochOffset);
        
        return other.add(epochOffset).multiply(units);
    }

    /**
     * Convert a <code>BigDecimal</code> datetime from the given time scale to the universal time scale.
     * All calculations are done using <code>BigDecimal</code> to guarantee that the value
     * does not go out of range.
     *
     * @param otherTime The <code>BigDecimal</code> datetime
     * @param timeScale The time scale to convert from
     * 
     * @return The datetime converted to the universal time scale
     *
     * @draft ICU 3.2
     */
    public static BigDecimal bigDecimalFrom(BigDecimal otherTime, int timeScale)
    {
        TimeScaleData data = getTimeScaleData(timeScale);
        
        BigDecimal units = new BigDecimal(data.units);
        BigDecimal epochOffset = new BigDecimal(data.epochOffset);
        
        return otherTime.add(epochOffset).multiply(units);
    }

    /**
     * Convert a datetime from the universal time scale to a <code>double</code> in the given
     * time scale.
     * 
     * Since this calculation requires a divide, we must round. The straight forward
     * way to round by adding half of the divisor will push the sum out of range for values
     * within half the divisor of the limits of the precision of a <code>long</code>. To get around this, we do
     * the rounding like this:
     * 
     * <p><code>
     * (universalTime - units + units/2) / units + 1
     * </code>
     * 
     * <p>
     * (i.e. we subtract units first to guarantee that we'll still be in range when we
     * add <code>units/2</code>. We then need to add one to the quotent to make up for the extra subtraction.
     * This simplifies to:
     * 
     * <p><code>
     * (universalTime - units/2) / units - 1
     * </code>
     * 
     * <p>
     * For negative values to round away from zero, we need to flip the signs:
     * 
     * <p><code>
     * (universalTime + units/2) / units + 1
     * </code>
     * 
     * <p>
     * Since we also need to subtract the epochOffset, we fold the <code>+/- 1</code>
     * into the offset value. (i.e. <code>epochOffsetP1</code>, <code>epochOffsetM1</code>.)
     *
     * @param universal The datetime in the universal time scale
     * @param timeScale The time scale to convert to
     * 
     * @return The datetime converted to the given time scale
     *
     * @draft ICU 3.2
     */
    public static double toDouble(long universalTime, int timeScale)
    {
        TimeScaleData data = toRangeCheck(universalTime, timeScale);
        
        if (universalTime < 0) {
            if (universalTime < data.minRound) {
                return (universalTime + data.unitsRound) / data.units - data.epochOffsetP1;
            }
            
            return (universalTime - data.unitsRound) / data.units - data.epochOffset;
        }
        
        if (universalTime > data.maxRound) {
            return (universalTime - data.unitsRound) / data.units - data.epochOffsetM1;
        }
        
        return (universalTime + data.unitsRound) / data.units - data.epochOffset;
    }
    
    /**
     * Convert a datetime from the universal time scale stored as a <code>BigDecimal</code> to a
     * <code>long</code> in the given time scale.
     *
     * Since this calculation requires a divide, we must round. The straight forward
     * way to round by adding half of the divisor will push the sum out of range for values
     * within have the divisor of the limits of the precision of a <code>long</code>. To get around this, we do
     * the rounding like this:
     * 
     * <p><code>
     * (universalTime - units + units/2) / units + 1
     * </code>
     * 
     * <p>
     * (i.e. we subtract units first to guarantee that we'll still be in range when we
     * add <code>units/2</code>. We then need to add one to the quotent to make up for the extra subtraction.
     * This simplifies to:
     * 
     * <p><code>
     * (universalTime - units/2) / units - 1
     * </code>
     * 
     * <p>
     * For negative values to round away from zero, we need to flip the signs:
     * 
     * <p><code>
     * (universalTime + units/2) / units + 1
     * </code>
     * 
     * <p>
     * Since we also need to subtract the epochOffset, we fold the <code>+/- 1</code>
     * into the offset value. (i.e. <code>epochOffsetP1</code>, <code>epochOffsetM1</code>.)
     * 
     * @param universal The datetime in the universal time scale
     * @param timeScale The time scale to convert to
     * 
     * @return The datetime converted to the given time scale
     *
     * @draft ICU 3.2
     */
    public static long toLong(long universalTime, int timeScale)
    {
        TimeScaleData data = toRangeCheck(universalTime, timeScale);
        
        if (universalTime < 0) {
            if (universalTime < data.minRound) {
                return (universalTime + data.unitsRound) / data.units - data.epochOffsetP1;
            }
            
            return (universalTime - data.unitsRound) / data.units - data.epochOffset;
        }
        
        if (universalTime > data.maxRound) {
            return (universalTime - data.unitsRound) / data.units - data.epochOffsetM1;
        }
        
        return (universalTime + data.unitsRound) / data.units - data.epochOffset;
    }
    
    /**
     * Convert a datetime from the universal time scale to a <code>BigDecimal</code> in the given time scale.
     *
     * @param universal The datetime in the universal time scale
     * @param timeScale The time scale to convert to
     * 
     * @return The datetime converted to the given time scale
     *
     * @draft ICU 3.2
     */
    public static BigDecimal toBigDecimal(long universalTime, int timeScale)
    {
        TimeScaleData data     = getTimeScaleData(timeScale);
        BigDecimal universal   = new BigDecimal(universalTime);
        BigDecimal units       = new BigDecimal(data.units);
        BigDecimal epochOffset = new BigDecimal(data.epochOffset);
        
        return universal.divide(units, BigDecimal.ROUND_HALF_UP).subtract(epochOffset);
    }
    
    /**
     * Convert a datetime from the universal time scale to a <code>BigDecimal</code> in the given time scale.
     *
     * @param universal The datetime in the universal time scale
     * @param timeScale The time scale to convert to
     * 
     * @return The datetime converted to the given time scale
     *
     * @draft ICU 3.2
     */
    public static BigDecimal toBigDecimal(BigDecimal universalTime, int timeScale)
    {
        TimeScaleData data     = getTimeScaleData(timeScale);
        BigDecimal units       = new BigDecimal(data.units);
        BigDecimal epochOffset = new BigDecimal(data.epochOffset);
        
        return universalTime.divide(units, BigDecimal.ROUND_HALF_UP).subtract(epochOffset);
    }
    
    /**
     * Return the <code>TimeScaleData</code> object for the given time
     * scale.
     * 
     * @param scale - the time scale
     * 
     * @return the <code>TimeScaleData</code> object for the given time scale
     * 
     * @internal
     */
    private static TimeScaleData getTimeScaleData(int scale)
    {
        if (scale < 0 || scale >= MAX_SCALE) {
            throw new IllegalArgumentException("scale out of range: " + scale);
        }
        
        return timeScaleTable[scale];
    }
    
    /**
     * Get a value associated with a particular time scale.
     * 
     * @param scale - the time scale
     * @param value - a constant representing the value to get
     * 
     * @return - the value.
     * 
     * @draft ICU 3.2
     */
    public static long getTimeScaleValue(int scale, int value)
    {
        TimeScaleData data = getTimeScaleData(scale);
        
        switch (value)
        {
        case UNITS_VALUE:
            return data.units;
            
        case EPOCH_OFFSET_VALUE:
            return data.epochOffset;
        
        case FROM_MIN_VALUE:
            return data.fromMin;
            
        case FROM_MAX_VALUE:
            return data.fromMax;
            
        case TO_MIN_VALUE:
            return data.toMin;
            
        case TO_MAX_VALUE:
            return data.toMax;
            
        case EPOCH_OFFSET_PLUS_1_VALUE:
            return data.epochOffsetP1;
            
        case EPOCH_OFFSET_MINUS_1_VALUE:
            return data.epochOffsetM1;
            
        case UNITS_ROUND_VALUE:
            return data.unitsRound;
        
        case MIN_ROUND_VALUE:
            return data.minRound;
            
        case MAX_ROUND_VALUE:
            return data.maxRound;
            
        default:
            throw new IllegalArgumentException("value out of range: " + value);
        }
    }
    
    private static TimeScaleData toRangeCheck(long universalTime, int scale)
    {
        TimeScaleData data = getTimeScaleData(scale);
          
        if (universalTime >= data.toMin && universalTime <= data.toMax) {
            return data;
        }
        
        throw new IllegalArgumentException("universalTime out of range:" + universalTime);
    }
    
    private static TimeScaleData fromRangeCheck(long otherTime, int scale)
    {
        TimeScaleData data = getTimeScaleData(scale);
          
        if (otherTime >= data.fromMin && otherTime <= data.fromMax) {
            return data;
        }
        
        throw new IllegalArgumentException("otherTime out of range:" + otherTime);
    }
    
    private static TimeScaleData fromRangeCheck(double otherTime, int scale)
    {
        TimeScaleData data = getTimeScaleData(scale);
          
        if (otherTime >= data.fromMin && otherTime <= data.fromMax) {
            return data;
        }
        
        throw new IllegalArgumentException("otherTime out of range:" + otherTime);
    }
    
    /**
     * Convert a time in the Universal Time Scale into another time
     * scale. The division used to do the conversion rounds down.
     * 
     * NOTE: This is an internal routine used by the tool that
     * generates the to and from limits. Use it at your own risk.
     * 
     * @param universalTime the time in the Universal Time scale
     * @param timeScale the time scale to convert to
     * @return the time in the given time scale
     * 
     * @internal
     */
    public static BigDecimal toBigDecimalTrunc(BigDecimal universalTime, int timeScale)
    {
        TimeScaleData data = getTimeScaleData(timeScale);
        BigDecimal units = new BigDecimal(data.units);
        BigDecimal epochOffset = new BigDecimal(data.epochOffset);
        
        return universalTime.divide(units, BigDecimal.ROUND_DOWN).subtract(epochOffset);
    }
}
