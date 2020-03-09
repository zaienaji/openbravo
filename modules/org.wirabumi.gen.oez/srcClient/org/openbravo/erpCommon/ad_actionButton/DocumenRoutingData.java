//Sqlc generated V1.O00-1
package org.openbravo.erpCommon.ad_actionButton;

import java.sql.*;

import org.apache.log4j.Logger;

import javax.servlet.ServletException;

import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.data.UtilSql;

class DocumenRoutingData implements FieldProvider {
static Logger log4j = Logger.getLogger(DocumenRoutingData.class);
  private String InitRecordNumber="0";
  public String count;

  public String getInitRecordNumber() {
    return InitRecordNumber;
  }

  public String getField(String fieldName) {
    if (fieldName.equalsIgnoreCase("count"))
      return count;
   else {
     log4j.debug("Field does not exist: " + fieldName);
     return null;
   }
 }

  public static String select(ConnectionProvider connectionProvider, String adRole, String adWindow, String adTab)    throws ServletException {
    String strSql = "";
    strSql = strSql + 
      "      SELECT count(oez_documentrouting_id)" +
      "      FROM oez_documentrouting" +
      "      WHERE ad_role_id = ? " +
      "	  and ad_window_id = ?" +
      "	  and ad_tab_id = ?";

    ResultSet result;
    String strReturn = null;
    PreparedStatement st = null;

    int iParameter = 0;
    try {
    st = connectionProvider.getPreparedStatement(strSql);
      iParameter++; UtilSql.setValue(st, iParameter, 12, null, adRole);
      iParameter++; UtilSql.setValue(st, iParameter, 12, null, adWindow);
      iParameter++; UtilSql.setValue(st, iParameter, 12, null, adTab);

      result = st.executeQuery();
      if(result.next()) {
        strReturn = UtilSql.getValue(result, "count");
      }
      result.close();
    } catch(SQLException e){
      log4j.error("SQL error in query: " + strSql + "Exception:"+ e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@" + e.getMessage());
    } catch(Exception ex){
      log4j.error("Exception in query: " + strSql + "Exception:"+ ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch(Exception ignore){
        ignore.printStackTrace();
      }
    }
    return(strReturn);
  }
}
