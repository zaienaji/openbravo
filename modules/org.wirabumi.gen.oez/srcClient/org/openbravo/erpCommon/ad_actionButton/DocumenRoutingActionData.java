//Replaced Generated SQL
package org.openbravo.erpCommon.ad_actionButton;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.data.FieldProvider;
import org.openbravo.data.UtilSql;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.erpCommon.utility.Utility;

class DocumenRoutingActionData implements FieldProvider {
  static Logger log4j = Logger.getLogger(DocumenRoutingActionData.class);
  private String InitRecordNumber = "0";
  public String docaction;
  private static HttpServletRequest request = RequestContext.get().getRequest();
  private static VariablesSecureApp vars = new VariablesSecureApp(request);

  public String getInitRecordNumber() {
    return InitRecordNumber;
  }

  public String getField(String fieldName) {
    if (fieldName.equalsIgnoreCase("docaction"))
      return docaction;
    else {
      log4j.debug("Field does not exist: " + fieldName);
      return null;
    }
  }

  /**
   * Validate Action With Transaction amount and Overdue Date
   * 
   * @param connectionProvider
   * @param adRole
   * @param adWindow
   * @param adTab
   * @param docStatus
   * @param transactionAmt
   * @param overdueDate
   * @return
   * @throws ServletException
   * @throws PropertyException
   */
  public static DocumenRoutingActionData[] selectorderdocaction(
      ConnectionProvider connectionProvider, String adRole, String adWindow, String adTab,
      String docStatus, String transactionAmt, String overdueDate) throws ServletException,
      PropertyException {
    return selectorderdocaction(connectionProvider, adRole, adWindow, adTab, docStatus,
        transactionAmt, overdueDate, 0, 0);
  }

  /**
   * Validate Action With Transaction
   * 
   * @param connectionProvider
   * @param adRole
   * @param adWindow
   * @param adTab
   * @param docStatus
   * @param transactionAmt
   * @return
   * @throws ServletException
   * @throws PropertyException
   */
  public static DocumenRoutingActionData[] selectorderdocaction(
      ConnectionProvider connectionProvider, String adRole, String adWindow, String adTab,
      String docStatus, String transactionAmt) throws ServletException, PropertyException {
    return selectorderdocaction(connectionProvider, adRole, adWindow, adTab, docStatus,
        transactionAmt, 0, 0);
  }

  /**
   * 
   * @param connectionProvider
   * @param adRole
   * @param adWindow
   * @param adTab
   * @param docStatus
   * @param transactionAmt
   * @param firstRegister
   * @param numberRegisters
   * @return
   * @throws ServletException
   * @throws PropertyException
   */
  public static DocumenRoutingActionData[] selectorderdocaction(
      ConnectionProvider connectionProvider, String adRole, String adWindow, String adTab,
      String docStatus, String transactionAmt, int firstRegister, int numberRegisters)
      throws ServletException, PropertyException {
    String UseRangeOrder = "N";

    String strSql = "";
    strSql = strSql + "      SELECT orderdocumentaction as docaction"
        + "      FROM oez_documentrouting" + "      WHERE ad_role_id = ? "
        + "   and ad_window_id = ?" + "   and ad_tab_id = ?" + "   and orderdocumentstatus = ?";

    UseRangeOrder = Utility.getPreference(vars, "OEZ_USEPROGRESIFORDER_RANGE", adWindow);
    if (UseRangeOrder.equals("Y")) {
      strSql = strSql + "   and (?)::numeric between minamt and maxamt";
    } else {
      strSql = strSql + "   and (?)::numeric <= maxamt";
    }

    ResultSet result;
    Vector<java.lang.Object> vector = new Vector<java.lang.Object>(0);
    PreparedStatement st = null;

    int iParameter = 0;
    try {
      st = connectionProvider.getPreparedStatement(strSql);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adRole);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adWindow);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adTab);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, docStatus);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, transactionAmt);

      result = st.executeQuery();
      long countRecord = 0;
      long countRecordSkip = 1;
      boolean continueResult = true;
      while (countRecordSkip < firstRegister && continueResult) {
        continueResult = result.next();
        countRecordSkip++;
      }
      while (continueResult && result.next()) {
        countRecord++;
        DocumenRoutingActionData objectDocumenRoutingActionData = new DocumenRoutingActionData();
        objectDocumenRoutingActionData.docaction = UtilSql.getValue(result, "docaction");
        objectDocumenRoutingActionData.InitRecordNumber = Integer.toString(firstRegister);
        vector.addElement(objectDocumenRoutingActionData);
        if (countRecord >= numberRegisters && numberRegisters != 0) {
          continueResult = false;
        }
      }
      result.close();
    } catch (SQLException e) {
      log4j.error("SQL error in query: " + strSql + "Exception:" + e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@"
          + e.getMessage());
    } catch (Exception ex) {
      log4j.error("Exception in query: " + strSql + "Exception:" + ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    DocumenRoutingActionData objectDocumenRoutingActionData[] = new DocumenRoutingActionData[vector
        .size()];
    vector.copyInto(objectDocumenRoutingActionData);
    return (objectDocumenRoutingActionData);
  }

  public static DocumenRoutingActionData[] selectorderdocaction(
      ConnectionProvider connectionProvider, String adRole, String adWindow, String adTab,
      String docStatus, String transactionAmt, String overdueDate, int firstRegister,
      int numberRegisters) throws ServletException, PropertyException {
    String strSql = "";
    String UseRangeOrder = "N";
    String UseRangeOverDueDate = "N";
    strSql = strSql + "      SELECT orderdocumentaction as docaction"
        + "      FROM oez_documentrouting" + "      WHERE ad_role_id = ? "
        + "	  and ad_window_id = ?" + "	  and ad_tab_id = ?" + "	  and orderdocumentstatus = ?";
    UseRangeOrder = Utility.getPreference(vars, "OEZ_USEPROGRESIFORDER_RANGE", adWindow);
    if (UseRangeOrder.equals("Y")) {
      strSql = strSql + "   and (?)::numeric between minamt and maxamt";
    } else {
      strSql = strSql + "   and (?)::numeric <= maxamt";
    }

    UseRangeOverDueDate = Utility.getPreference(vars, "OEZ_USEPROGRESIFOVERDUE_RANGE", adWindow);
    if (UseRangeOverDueDate.equals("Y")) {
      strSql = strSql + " and (?)::numeric between minoverdue and maxoverdue";
    } else {
      strSql = strSql + "   and (?)::numeric <= maxoverdue";
    }

    ResultSet result;
    Vector<java.lang.Object> vector = new Vector<java.lang.Object>(0);
    PreparedStatement st = null;

    int iParameter = 0;
    try {
      st = connectionProvider.getPreparedStatement(strSql);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adRole);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adWindow);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adTab);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, docStatus);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, transactionAmt);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, overdueDate);

      result = st.executeQuery();
      long countRecord = 0;
      long countRecordSkip = 1;
      boolean continueResult = true;
      while (countRecordSkip < firstRegister && continueResult) {
        continueResult = result.next();
        countRecordSkip++;
      }
      while (continueResult && result.next()) {
        countRecord++;
        DocumenRoutingActionData objectDocumenRoutingActionData = new DocumenRoutingActionData();
        objectDocumenRoutingActionData.docaction = UtilSql.getValue(result, "docaction");
        objectDocumenRoutingActionData.InitRecordNumber = Integer.toString(firstRegister);
        vector.addElement(objectDocumenRoutingActionData);
        if (countRecord >= numberRegisters && numberRegisters != 0) {
          continueResult = false;
        }
      }
      result.close();
    } catch (SQLException e) {
      log4j.error("SQL error in query: " + strSql + "Exception:" + e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@"
          + e.getMessage());
    } catch (Exception ex) {
      log4j.error("Exception in query: " + strSql + "Exception:" + ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    DocumenRoutingActionData objectDocumenRoutingActionData[] = new DocumenRoutingActionData[vector
        .size()];
    vector.copyInto(objectDocumenRoutingActionData);
    return (objectDocumenRoutingActionData);
  }

  public static DocumenRoutingActionData[] selectdocaction(ConnectionProvider connectionProvider,
      String adRole, String adWindow, String adTab, String docStatus, String transactionAmt)
      throws ServletException {
    return selectdocaction(connectionProvider, adRole, adWindow, adTab, docStatus, transactionAmt,
        0, 0);
  }

  /**
   * 
   * @param connectionProvider
   * @param adRole
   * @param adWindow
   * @param adTab
   * @param docStatus
   * @param transactionAmt
   * @param firstRegister
   * @param numberRegisters
   * @return
   * @throws ServletException
   */
  public static DocumenRoutingActionData[] selectdocaction(ConnectionProvider connectionProvider,
      String adRole, String adWindow, String adTab, String docStatus, String transactionAmt,
      int firstRegister, int numberRegisters) throws ServletException {
    String strSql = "";
    strSql = strSql + "      SELECT docaction" + "      FROM oez_documentrouting"
        + "      WHERE ad_role_id = ? " + "	  and ad_window_id = ?" + "	  and ad_tab_id = ?"
        + "	  and docstatus = ?" + "	  and (?)::numeric between minamt and maxamt";

    ResultSet result;
    Vector<java.lang.Object> vector = new Vector<java.lang.Object>(0);
    PreparedStatement st = null;

    int iParameter = 0;
    try {
      st = connectionProvider.getPreparedStatement(strSql);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adRole);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adWindow);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, adTab);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, docStatus);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, transactionAmt);

      result = st.executeQuery();
      long countRecord = 0;
      long countRecordSkip = 1;
      boolean continueResult = true;
      while (countRecordSkip < firstRegister && continueResult) {
        continueResult = result.next();
        countRecordSkip++;
      }
      while (continueResult && result.next()) {
        countRecord++;
        DocumenRoutingActionData objectDocumenRoutingActionData = new DocumenRoutingActionData();
        objectDocumenRoutingActionData.docaction = UtilSql.getValue(result, "docaction");
        objectDocumenRoutingActionData.InitRecordNumber = Integer.toString(firstRegister);
        vector.addElement(objectDocumenRoutingActionData);
        if (countRecord >= numberRegisters && numberRegisters != 0) {
          continueResult = false;
        }
      }
      result.close();
    } catch (SQLException e) {
      log4j.error("SQL error in query: " + strSql + "Exception:" + e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@"
          + e.getMessage());
    } catch (Exception ex) {
      log4j.error("Exception in query: " + strSql + "Exception:" + ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    DocumenRoutingActionData objectDocumenRoutingActionData[] = new DocumenRoutingActionData[vector
        .size()];
    vector.copyInto(objectDocumenRoutingActionData);
    return (objectDocumenRoutingActionData);
  }

  public static String getOrderAmount(ConnectionProvider connectionProvider, String orderId)
      throws ServletException {
    String strSql = "";
    strSql = strSql
        + "      SELECT c_base_convert(grandtotal, c_order.c_currency_id,c_order.ad_client_id,c_order.dateordered)"
        + "      FROM c_order" + "      WHERE c_order_id = ? ";

    ResultSet result;
    String strReturn = null;
    PreparedStatement st = null;

    int iParameter = 0;
    try {
      st = connectionProvider.getPreparedStatement(strSql);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, orderId);

      result = st.executeQuery();
      if (result.next()) {
        strReturn = UtilSql.getValue(result, "c_base_convert");
      }
      result.close();
    } catch (SQLException e) {
      log4j.error("SQL error in query: " + strSql + "Exception:" + e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@"
          + e.getMessage());
    } catch (Exception ex) {
      log4j.error("Exception in query: " + strSql + "Exception:" + ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    return (strReturn);
  }

  public static String getInvoiceAmount(ConnectionProvider connectionProvider, String invoiceId)
      throws ServletException {
    String strSql = "";
    strSql = strSql
        + "      SELECT c_base_convert(grandtotal, c_invoice.c_currency_id,c_invoice.ad_client_id,c_invoice.dateinvoiced)"
        + "      FROM c_invoice" + "      WHERE c_invoice_id = ? ";

    ResultSet result;
    String strReturn = null;
    PreparedStatement st = null;

    int iParameter = 0;
    try {
      st = connectionProvider.getPreparedStatement(strSql);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, invoiceId);

      result = st.executeQuery();
      if (result.next()) {
        strReturn = UtilSql.getValue(result, "c_base_convert");
      }
      result.close();
    } catch (SQLException e) {
      log4j.error("SQL error in query: " + strSql + "Exception:" + e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@"
          + e.getMessage());
    } catch (Exception ex) {
      log4j.error("Exception in query: " + strSql + "Exception:" + ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    return (strReturn);
  }

  public static String getForceDocumentRouting(ConnectionProvider connectionProvider,
      String clientId) throws ServletException {
    String strSql = "";
    strSql = strSql + "      SELECT em_oez_forcedocumentrouting" + "      FROM ad_client"
        + "      WHERE ad_client_id = ? ";

    ResultSet result;
    String strReturn = null;
    PreparedStatement st = null;

    int iParameter = 0;
    try {
      st = connectionProvider.getPreparedStatement(strSql);
      iParameter++;
      UtilSql.setValue(st, iParameter, 12, null, clientId);

      result = st.executeQuery();
      if (result.next()) {
        strReturn = UtilSql.getValue(result, "em_oez_forcedocumentrouting");
      }
      result.close();
    } catch (SQLException e) {
      log4j.error("SQL error in query: " + strSql + "Exception:" + e);
      throw new ServletException("@CODE=" + Integer.toString(e.getErrorCode()) + "@"
          + e.getMessage());
    } catch (Exception ex) {
      log4j.error("Exception in query: " + strSql + "Exception:" + ex);
      throw new ServletException("@CODE=@" + ex.getMessage());
    } finally {
      try {
        connectionProvider.releasePreparedStatement(st);
      } catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    return (strReturn);
  }
}
