package org.wirabumi.gen.oez.utility;

import java.util.Date;

import org.jfree.data.time.DateRange;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.service.db.DalConnectionProvider;

public class DateTimeUtility {
  public static boolean isOverlap(Date dateFrom1, Date dateTo1, Date dateFrom2, Date dateTo2,
      boolean allowIntersect) {
    boolean overlap = true;
    String language = OBContext.getOBContext().getLanguage().getLanguage();
    ConnectionProvider conn = new DalConnectionProvider(false);
    try {
      DateRange interval = new DateRange(dateFrom1, dateTo1);
      DateRange interval2 = new DateRange(dateFrom2, dateTo2);
      overlap = interval.intersects(interval2);
      if (allowIntersect && overlap) {
        if (dateFrom1.before(dateFrom2) && dateTo1.after(dateFrom2) && !dateTo1.after(dateTo2)) {
          overlap = false;
        } else if (dateFrom1.before(dateTo2) && dateTo1.after(dateTo2)
            && !dateFrom1.before(dateFrom2)) {
          overlap = false;
        }
      } else if (!allowIntersect && overlap) {
        return true;
      }
    } catch (Exception e) {
      throw new OBException(Utility.messageBD(conn,
          "Jam Yang Anda Masukan Salah Jam Keluar Lebih Kecil Dari Jam Masuk ", language));
    }
    return overlap;
  }

  // TODO belum selesai
  public static DateRange validRange(Date dateFrom1, Date dateTo1, Date dateFrom2, Date dateTo2) {
    DateRange validRange = null;
    try {
      DateRange interval = new DateRange(dateFrom1, dateTo1);
      DateRange interval2 = new DateRange(dateFrom2, dateTo2);
      boolean isOVerlap = interval.intersects(interval2);
      if (isOVerlap) {
        if (dateFrom1.before(dateFrom2) && dateTo1.after(dateFrom2)) {
          validRange = new DateRange(dateFrom1, dateFrom2);
        } else if (dateFrom1.before(dateTo2) && dateTo1.after(dateTo2)) {
          validRange = new DateRange(dateTo2, dateTo1);
        }
      } else {
        validRange = interval;
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return validRange;
  }
}
