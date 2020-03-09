package org.wirabumi.gen.oez.utility;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateIntervalUtility {
  /**
   * GET TIME FROM TIMESTAMP * @param paramDate Date to parse with String HH::mm:ss
   * 
   * @return String formated HH::mm:ss
   */
  public static String getTime(Date paramDate) {
    String Time = "";
    if (paramDate != null) {
      SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm:ss.SSS");
      try {
        Time = timeformat.format(paramDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return Time;
  }

  /**
   * GET DATE FROM TIMESTAMP
   * 
   * @param paramDate
   *          Date To Parse
   * @param format
   * @return String With formated text ex: dd-MM-yyyy
   */
  public static String getDate(Date paramDate, String format) {
    String date = "";
    if (paramDate != null) {
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      try {
        date = dateFormat.format(paramDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return date;
  }

  /**
   * Set Time On the Date to 00:00:00.0
   * 
   * @param paramsDate
   *          Date To clear the time
   * @return Date with time is 00:00:00
   */
  public static Date clearTime(Date paramsDate) {
    Date zeroTime = null;
    try {
      Calendar endToday = Calendar.getInstance();
      endToday.setTime(paramsDate);
	  endToday.set(Calendar.HOUR_OF_DAY, 0);
	  endToday.set(Calendar.HOUR, 0);
	  endToday.set(Calendar.MINUTE, 0);
	  endToday.set(Calendar.SECOND, 0);
	  endToday.set(Calendar.MILLISECOND, 0);
	  endToday.set(Calendar.AM_PM, 0);
      zeroTime = endToday.getTime();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return zeroTime;
  }

  /**
   * Adjust Time Or Date From Starting georgian Date 01-01-1970 00:00:00
   * 
   * @param adjust
   *          Adjust Time In Mili Second Day is 1*25*60*60*1000
   * @return
   */
  public static Date resetDateAdjust(int adjust) {
    Date toPass = null;
    try {
      Calendar calx = Calendar.getInstance();
      calx.clear(Calendar.YEAR);
      calx.clear(Calendar.MONTH);
      calx.clear(Calendar.DAY_OF_MONTH);
      calx.clear(Calendar.DAY_OF_WEEK);
      calx.clear(Calendar.DAY_OF_WEEK_IN_MONTH);
      calx.clear(Calendar.DAY_OF_YEAR);
      calx.clear(Calendar.WEEK_OF_MONTH);
      calx.clear(Calendar.WEEK_OF_YEAR);
      calx.clear(Calendar.HOUR_OF_DAY);
      calx.clear(Calendar.AM_PM);
      calx.clear(Calendar.HOUR);
      calx.clear(Calendar.MINUTE);
      calx.clear(Calendar.SECOND);
      calx.clear(Calendar.MILLISECOND);
      calx.add(Calendar.MILLISECOND, adjust);
      toPass = calx.getTime();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return toPass;
  }

  /**
   * Adjust Date By Days
   * 
   * @param paramDates
   *          Date To adjust
   * @param adjustMent
   *          int day too adjusted can -1 or 1 or other
   * @return date adjusted
   */

  public static Date adjustDate(Date paramDates, int adjustMent) {
    Date date = null;
    try {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(paramDates);
      calendar.add(Calendar.DATE, adjustMent);
      date = calendar.getTime();
    } catch (Exception e) {
    }
    return date;
  }

  /**
   * Get Second From hour Duration
   * 
   * @param hour
   *          Input Double Hour
   * @return Second from hour
   */
  public static int getHourSecond(Double hour) {
    int second = 0;
    second = (int) (hour * 3600);
    return second;
  }

  /**
   * Adjusting Time By Second
   * 
   * @param paramDates
   *          date adjust target
   * @param adjustMent
   *          second to be added to param dates
   * @return date adjust by second
   */

  public static Date adjustSecond(Date paramDates, int adjustMent) {
    Date date = null;
    try {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(paramDates);
      calendar.add(Calendar.SECOND, adjustMent);
      date = calendar.getTime();
    } catch (Exception e) {
    }
    return date;
  }

  public static double getYear(Date startDate, Date endDate) {
    int _Year = 0;
    try {
      Calendar calStart = Calendar.getInstance();
      calStart.setTime(startDate);
      int startMonth = calStart.get(Calendar.MONTH);
      int startYear = calStart.get(Calendar.YEAR);

      Calendar calEnd = Calendar.getInstance();
      calEnd.setTime(endDate);
      int endMonth = calEnd.get(Calendar.MONTH);
      int endYear = calEnd.get(Calendar.YEAR);

      int intervalMonth = 0;
      if (startMonth < endMonth) {
        // intervalMonth=(endMonth + 12)-startMonth;
        _Year = endYear - startYear;
      } else {
        // intervalMonth = startMonth - endMonth;
        _Year = (endYear - 1) - startYear;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return _Year;
  }

  public static double getMonth(Date startDate, Date endDate) {
    int _Month = 0;
    try {
      Calendar calStart = Calendar.getInstance();
      calStart.setTime(startDate);
      int startMonth = calStart.get(Calendar.MONTH);
      int startYear = calStart.get(Calendar.YEAR);

      Calendar calEnd = Calendar.getInstance();
      calEnd.setTime(endDate);
      int endMonth = calEnd.get(Calendar.MONTH);
      int endYear = calEnd.get(Calendar.YEAR);

      int intervalMonth = 0;
      if (startMonth < endMonth) {
        _Month = endMonth - startMonth;
        // _Year = (endYear-1)-startYear;
      } else {
        _Month = (endMonth + 12) - startMonth;
        // _Year = endYear-startYear;
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return _Month;

  }

  /**
   * Gets the Interval Day Betwen Two Date.
   * 
   * @param startDate
   *          Formated String Starting Date dd-MM-yyyy HH:mm:ss
   * @param endDate
   *          Formated String Ending Date To Compare dd-MM-yyyy HH:mm:ss
   * @return Date Interval With returner value : Day , Hour , Minutes , Second
   */
  public static double getDay(Date startDate, Date endDate) {
    double _Day = 0;
    try {
      // in milliseconds
      double diff = endDate.getTime() - startDate.getTime();

      _Day = diff / (24 * 60 * 60 * 1000);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return _Day;

  }

  /**
   * Gets the Interval Hour Betwen Two Date.
   * 
   * @param startDate
   *          Formated String Starting Date dd-MM-yyyy HH:mm:ss
   * @param endDate
   *          Formated String Ending Date To Compare dd-MM-yyyy HH:mm:ss
   * @return Date Interval With returner value : Day , Hour , Minutes , Second
   */
  public static double getHour(Date startDate, Date endDate) {
    double _Hours = 0;
    try {
      long diff = endDate.getTime() - startDate.getTime();
      _Hours = diff / (60 * 60 * 1000) % 24;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return _Hours;
  }

  /**
   * Gets the Interval Minutes Between Two Date.
   * 
   * @param startDate
   *          Formated String Starting Date
   * @param endDate
   *          Formated String Ending Date To Compare dd-MM-yyyy HH:mm:ss
   * @return Date Interval With returner value : Day , Hour , Minutes , Second
   */
  public static double getMinutes(Date startDate, Date endDate) {
    double _Minutes = 0;
    try {
      // in milliseconds
      double diff = endDate.getTime() - startDate.getTime();
      _Minutes = diff / (60 * 1000) % 60;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return _Minutes;
  }

  /**
   * Gets the Interval second Betwen Two Date.
   * 
   * @param startDate
   *          Formated String Starting Date dd-MM-yyyy HH:mm:ss
   * @param endDate
   *          Formated String Ending Date To Compare dd-MM-yyyy HH:mm:ss
   * @return Date Interval With returner value : Day , Hour , Minutes , Second
   */
  public static double getSecond(Date startDate, Date endDate) {
    double _Second = 0;
    try {
      double diff = endDate.getTime() - startDate.getTime();
      _Second = diff / 1000 % 60;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return _Second;
  }
}
