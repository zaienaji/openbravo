package org.wirabumi.gen.oez.porting;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;

import org.openbravo.base.exception.OBException;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.service.db.DalConnectionProvider;

public class ChkOpenPeriod {

  ConnectionProvider conn = new DalConnectionProvider();
  int availablePeriod = 0;

  public ChkOpenPeriod(String adOrgId, Date ProductionDate, String docType, String docTypeId) {

    AdOrgGetPeriodControlAllow orgPeriodControlAllow = new AdOrgGetPeriodControlAllow(adOrgId);
    String organizationPeriodeAllow = orgPeriodControlAllow.getOrgPeriodControlAllow();

    if (docTypeId != "") {

      String sql = "SELECT COUNT(p.c_period_id) AS periodeActive FROM c_period p WHERE '"
          + ProductionDate + "' >= p.startdate AND '" + ProductionDate + "' <=p.enddate+1 "
          + "AND EXISTS (SELECT 1 FROM c_periodcontrol pc WHERE pc.c_period_id = p.c_period_id "
          + "AND pc.docbasetype=(SELECT docbasetype FROM c_doctype WHERE c_doctype_id='"
          + docTypeId + "') " + "AND pc.ad_org_id= " + organizationPeriodeAllow
          + " AND pc.periodstatus='O')";

      try {
        Statement st = conn.getStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
          availablePeriod = rs.getInt("availablePeriod");
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new OBException(e.getMessage());
      }
    } else if (docType != "") {
      String sql = "SELECT COUNT(p.c_period_id) AS periodeActive FROM c_period p WHERE '"
          + ProductionDate + "' >= p.startdate AND '" + ProductionDate + "' <=p.enddate+1 "
          + "AND EXISTS (SELECT 1 FROM c_periodcontrol pc WHERE pc.c_period_id = p.c_period_id "
          + "AND pc.docbasetype='" + docType + "' " + "AND pc.ad_org_id= '"
          + organizationPeriodeAllow + "' AND pc.periodstatus='O')";

      try {
        Statement st = conn.getStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
          availablePeriod = rs.getInt("periodeActive");
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new OBException(e.getMessage());
      }
    } else {
      availablePeriod = 0;
    }
  }

  public int getAvailablePeriod() {
    return availablePeriod;
  }
}
