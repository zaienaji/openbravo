/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2001-2011 Openbravo SLU 
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.erpCommon.ad_reports;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.filter.IsIDFilter;
import org.openbravo.base.filter.IsPositiveIntFilter;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.costing.CostingStatus;
import org.openbravo.erpCommon.businessUtility.Tree;
import org.openbravo.erpCommon.businessUtility.TreeData;
import org.openbravo.erpCommon.businessUtility.WindowTabs;
import org.openbravo.erpCommon.info.SelectorUtilityData;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.DateTimeData;
import org.openbravo.erpCommon.utility.LeftTabsBar;
import org.openbravo.erpCommon.utility.NavigationBar;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.ToolBar;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.utils.Replace;
import org.openbravo.xmlEngine.XmlDocument;

public class ReportInvoiceCustomerDimensionalAnalysesJR_legacy extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    VariablesSecureApp vars = new VariablesSecureApp(request);

    // Get user Client's base currency
    String strUserCurrencyId = Utility.stringBaseCurrencyId(this, vars.getClient());
    if (vars.commandIn("DEFAULT", "DEFAULT_COMPARATIVE")) {
      String strDateFrom = vars.getGlobalVariable("inpDateFrom",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateFrom", "");
      String strDateTo = vars.getGlobalVariable("inpDateTo",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateTo", "");
      String strDateFromRef = vars.getGlobalVariable("inpDateFromRef",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateFromRef", "");
      String strDateToRef = vars.getGlobalVariable("inpDateToRef",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateToRef", "");
      String strPartnerGroup = vars.getGlobalVariable("inpPartnerGroup",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partnerGroup", "");
      String strcBpartnerId = vars.getInGlobalVariable("inpcBPartnerId_IN",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partner", "", IsIDFilter.instance);
      String strProductCategory = vars.getGlobalVariable("inpProductCategory",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|productCategory", "");
      String strmProductId = vars.getInGlobalVariable("inpmProductId_IN",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|product", "", IsIDFilter.instance);
      // ad_ref_list.value for refercence_id 800087
      String strNotShown = vars.getInGlobalVariable("inpNotShown",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|notShown", "",
          IsPositiveIntFilter.instance);
      String strShown = vars.getInGlobalVariable("inpShown",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|shown", "",
          IsPositiveIntFilter.instance);
      String strOrg = vars.getGlobalVariable("inpOrg",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|org", "");
      String strsalesrepId = vars.getGlobalVariable("inpSalesrepId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|salesrep", "");
      String strcProjectId = vars.getGlobalVariable("inpcProjectId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|project", "");
      String strProducttype = vars.getGlobalVariable("inpProducttype",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|producttype", "");
      String strOrder = vars.getGlobalVariable("inpOrder",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|order", "Normal");
      String strMayor = vars.getNumericGlobalVariable("inpMayor",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|mayor", "");
      String strMenor = vars.getNumericGlobalVariable("inpMenor",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|menor", "");
      String strPartnerSalesRepId = vars.getGlobalVariable("inpPartnerSalesrepId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partnersalesrep", "");
      String strCurrencyId = vars.getGlobalVariable("inpCurrencyId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|currency", strUserCurrencyId);
      String strComparative = "";
      if (vars.commandIn("DEFAULT_COMPARATIVE")) {
        strComparative = vars.getRequestGlobalVariable("inpComparative",
            "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|comparative");
      } else {
        strComparative = vars.getGlobalVariable("inpComparative",
            "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|comparative", "N");
      }
      printPageDataSheet(request, response, vars, strComparative, strDateFrom, strDateTo,
          strPartnerGroup, strcBpartnerId, strProductCategory, strmProductId, strNotShown, strShown,
          strDateFromRef, strDateToRef, strOrg, strsalesrepId, strcProjectId, strProducttype,
          strOrder, strMayor, strMenor, strPartnerSalesRepId, strCurrencyId);
    } else if (vars.commandIn("EDIT_HTML", "EDIT_HTML_COMPARATIVE", "EDIT_PDF")) {
      String strDateFrom = vars.getRequestGlobalVariable("inpDateFrom",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateFrom");
      String strDateTo = vars.getRequestGlobalVariable("inpDateTo",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateTo");
      String strDateFromRef = vars.getRequestGlobalVariable("inpDateFromRef",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateFromRef");
      String strDateToRef = vars.getRequestGlobalVariable("inpDateToRef",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateToRef");
      String strPartnerGroup = vars.getRequestGlobalVariable("inpPartnerGroup",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partnerGroup");
      String strcBpartnerId = vars.getRequestInGlobalVariable("inpcBPartnerId_IN",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partner", IsIDFilter.instance);
      String strProductCategory = vars.getRequestGlobalVariable("inpProductCategory",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|productCategory");
      String strmProductId = vars.getRequestInGlobalVariable("inpmProductId_IN",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|product", IsIDFilter.instance);
      // ad_ref_list.value for refercence_id 800087
      String strNotShown = vars.getInStringParameter("inpNotShown", IsPositiveIntFilter.instance);
      String strShown = vars.getInStringParameter("inpShown", IsPositiveIntFilter.instance);
      String strOrg = vars.getRequestGlobalVariable("inpOrg",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|org");
      String strsalesrepId = vars.getRequestGlobalVariable("inpSalesrepId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|salesrep");
      String strcProjectId = vars.getRequestGlobalVariable("inpcProjectId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|project");
      String strProducttype = vars.getRequestGlobalVariable("inpProducttype",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|producttype");
      String strOrder = vars.getRequestGlobalVariable("inpOrder",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|order");
      String strMayor = vars.getNumericParameter("inpMayor", "");
      String strMenor = vars.getNumericParameter("inpMenor", "");
      String strComparative = vars.getStringParameter("inpComparative", "N");
      String strPartnerSalesrepId = vars.getRequestGlobalVariable("inpPartnerSalesrepId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partnersalesrep");
      String strCurrencyId = vars.getGlobalVariable("inpCurrencyId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|currency", strUserCurrencyId);
      printPageHtml(request, response, vars, strComparative, strDateFrom, strDateTo,
          strPartnerGroup, strcBpartnerId, strProductCategory, strmProductId, strNotShown, strShown,
          strDateFromRef, strDateToRef, strOrg, strsalesrepId, strcProjectId, strProducttype,
          strOrder, strMayor, strMenor, strPartnerSalesrepId, strCurrencyId,
          vars.commandIn("EDIT_PDF") ? "pdf" : "html");
    } else if (vars.commandIn("EDIT_PDF", "EDIT_PDF_COMPARATIVE")) {
      String strDateFrom = vars.getRequestGlobalVariable("inpDateFrom",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateFrom");
      String strDateTo = vars.getRequestGlobalVariable("inpDateTo",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateTo");
      String strDateFromRef = vars.getRequestGlobalVariable("inpDateFromRef",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateFromRef");
      String strDateToRef = vars.getRequestGlobalVariable("inpDateToRef",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|dateToRef");
      String strPartnerGroup = vars.getRequestGlobalVariable("inpPartnerGroup",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partnerGroup");
      String strcBpartnerId = vars.getRequestInGlobalVariable("inpcBPartnerId_IN",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partner", IsIDFilter.instance);
      String strProductCategory = vars.getRequestGlobalVariable("inpProductCategory",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|productCategory");
      String strmProductId = vars.getRequestInGlobalVariable("inpmProductId_IN",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|product", IsIDFilter.instance);
      // ad_ref_list.value for refercence_id 800087
      String strNotShown = vars.getInStringParameter("inpNotShown", IsPositiveIntFilter.instance);
      String strShown = vars.getInStringParameter("inpShown", IsPositiveIntFilter.instance);
      String strOrg = vars.getRequestGlobalVariable("inpOrg",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|org");
      String strsalesrepId = vars.getRequestGlobalVariable("inpSalesrepId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|salesrep");
      String strcProjectId = vars.getRequestGlobalVariable("inpcProjectId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|project");
      String strProducttype = vars.getRequestGlobalVariable("inpProducttype",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|producttype");
      String strOrder = vars.getRequestGlobalVariable("inpOrder",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|order");
      String strMayor = vars.getNumericParameter("inpMayor", "");
      String strMenor = vars.getNumericParameter("inpMenor", "");
      String strComparative = vars.getStringParameter("inpComparative", "N");
      String strPartnerSalesrepId = vars.getRequestGlobalVariable("inpPartnerSalesrepId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|partnersalesrep");
      String strCurrencyId = vars.getGlobalVariable("inpCurrencyId",
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy|currency", strUserCurrencyId);
      printPageHtml(request, response, vars, strComparative, strDateFrom, strDateTo,
          strPartnerGroup, strcBpartnerId, strProductCategory, strmProductId, strNotShown, strShown,
          strDateFromRef, strDateToRef, strOrg, strsalesrepId, strcProjectId, strProducttype,
          strOrder, strMayor, strMenor, strPartnerSalesrepId, strCurrencyId, "pdf");
    } else {
      pageErrorPopUp(response);
    }
  }

  private void printPageDataSheet(HttpServletRequest request, HttpServletResponse response,
      VariablesSecureApp vars, String strComparative, String strDateFrom, String strDateTo,
      String strPartnerGroup, String strcBpartnerId, String strProductCategory,
      String strmProductId, String strNotShown, String strShown, String strDateFromRef,
      String strDateToRef, String strOrg, String strsalesrepId, String strcProjectId,
      String strProducttype, String strOrder, String strMayor, String strMenor,
      String strPartnerSalesrepId, String strCurrencyId) throws IOException, ServletException {
    if (log4j.isDebugEnabled()) {
      log4j.debug("Output: dataSheet");
    }
    String discard[] = { "selEliminarHeader1" };
    if (strComparative.equals("Y")) {
      discard[0] = "selEliminarHeader2";
    }
    XmlDocument xmlDocument = null;
    xmlDocument = xmlEngine.readXmlTemplate(
        "org/openbravo/erpCommon/ad_reports/ReportInvoiceCustomerDimensionalAnalysesJRFilter_legacy",
        discard).createXmlDocument();

    ToolBar toolbar = new ToolBar(this, vars.getLanguage(),
        "ReportInvoiceCustomerDimensionalAnalysesJR_legacy", false, "", "", "", false, "ad_reports",
        strReplaceWith, false, true);
    toolbar.prepareSimpleToolBarTemplate();
    xmlDocument.setParameter("toolbar", toolbar.toString());
    try {
      WindowTabs tabs = new WindowTabs(this, vars,
          "org.openbravo.erpCommon.ad_reports.ReportInvoiceCustomerDimensionalAnalysesJR_legacy");
      xmlDocument.setParameter("parentTabContainer", tabs.parentTabs());
      xmlDocument.setParameter("mainTabContainer", tabs.mainTabs());
      xmlDocument.setParameter("childTabContainer", tabs.childTabs());
      xmlDocument.setParameter("theme", vars.getTheme());
      NavigationBar nav = new NavigationBar(this, vars.getLanguage(),
          "ReportInvoiceCustomerDimensionalAnalysesJRFilter_legacy.html", classInfo.id,
          classInfo.type, strReplaceWith, tabs.breadcrumb());
      xmlDocument.setParameter("navigationBar", nav.toString());
      LeftTabsBar lBar = new LeftTabsBar(this, vars.getLanguage(),
          "ReportInvoiceCustomerDimensionalAnalysesJRFilter_legacy.html", strReplaceWith);
      xmlDocument.setParameter("leftTabs", lBar.manualTemplate());
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    {
      OBError myMessage = vars.getMessage("ReportInvoiceCustomerDimensionalAnalysesJR_legacy");
      vars.removeMessage("ReportInvoiceCustomerDimensionalAnalysesJR_legacy");
      if (myMessage != null) {
        xmlDocument.setParameter("messageType", myMessage.getType());
        xmlDocument.setParameter("messageTitle", myMessage.getTitle());
        xmlDocument.setParameter("messageMessage", myMessage.getMessage());
      }
      if (CostingStatus.getInstance().isMigrated()) {
        advise(request, response, "ERROR",
            Utility.messageBD(this, "NotUsingOldCost", vars.getLanguage()), "");
        return;
      }

    }

    xmlDocument.setParameter("calendar", vars.getLanguage().substring(0, 2));
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("dateFrom", strDateFrom);
    xmlDocument.setParameter("dateFromdisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateFromsaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateTo", strDateTo);
    xmlDocument.setParameter("dateTodisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateTosaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateFromRef", strDateFromRef);
    xmlDocument.setParameter("dateFromRefdisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateFromRefsaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateToRef", strDateToRef);
    xmlDocument.setParameter("dateToRefdisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateToRefsaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    /*
     * xmlDocument.setParameter("paramBPartnerId", strcBpartnerId);
     * xmlDocument.setParameter("bPartnerDescription",
     * ReportInvoiceCustomerDimensionalAnalysesJRData.selectBpartner(this, strcBpartnerId));
     * xmlDocument.setParameter("mProduct", strmProductId);
     * xmlDocument.setParameter("productDescription",
     * ReportInvoiceCustomerDimensionalAnalysesJRData.selectMproduct(this, strmProductId));
     */
    xmlDocument.setParameter("cBpGroupId", strPartnerGroup);
    xmlDocument.setParameter("mProductCategoryId", strProductCategory);
    xmlDocument.setParameter("adOrgId", strOrg);
    xmlDocument.setParameter("salesRepId", strsalesrepId);
    xmlDocument.setParameter("normal", strOrder);
    xmlDocument.setParameter("amountasc", strOrder);
    xmlDocument.setParameter("amountdesc", strOrder);
    xmlDocument.setParameter("mayor", strMayor);
    xmlDocument.setParameter("menor", strMenor);
    xmlDocument.setParameter("comparative", strComparative);
    xmlDocument.setParameter("cProjectId", strcProjectId);
    xmlDocument.setParameter("producttype", strProducttype);
    xmlDocument.setParameter("partnerSalesRepId", strPartnerSalesrepId);
    xmlDocument.setParameter("projectName",
        ReportInvoiceCustomerDimensionalAnalysesJRData.selectProject(this, strcProjectId));
    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLEDIR", "C_BP_Group_ID",
          "", "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData,
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy", strPartnerGroup);
      xmlDocument.setData("reportC_BP_GROUPID", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLEDIR",
          "M_Product_Category_ID", "", "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData,
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy", strProductCategory);
      xmlDocument.setData("reportM_PRODUCT_CATEGORYID", "liststructure",
          comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLEDIR", "AD_Org_ID", "",
          "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData,
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy", strOrg);
      xmlDocument.setData("reportAD_ORGID", "liststructure", comboTableData.select(false));
      comboTableData = null;

    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    xmlDocument.setData("reportCBPartnerId_IN", "liststructure",
        SelectorUtilityData.selectBpartner(this,
            Utility.getContext(this, vars, "#AccessibleOrgTree", ""),
            Utility.getContext(this, vars, "#User_Client", ""), strcBpartnerId));
    xmlDocument.setData("reportMProductId_IN", "liststructure",
        SelectorUtilityData.selectMproduct(this,
            Utility.getContext(this, vars, "#AccessibleOrgTree", ""),
            Utility.getContext(this, vars, "#User_Client", ""), strmProductId));

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "LIST", "",
          "M_Product_ProductType", "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData,
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy", "");
      xmlDocument.setData("reportProductType", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLE", "SalesRep_ID",
          "AD_User SalesRep", "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData, "ReportSalesDimensionalAnalyzeJR",
          strsalesrepId);
      xmlDocument.setData("reportSalesRep_ID", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLE", "",
          "C_BPartner SalesRep", "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData,
          "ReportInvoiceCustomerDimensionalAnalysesJR", strPartnerSalesrepId);
      xmlDocument.setData("reportPartnerSalesRep_ID", "liststructure",
          comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    xmlDocument.setParameter("ccurrencyid", strCurrencyId);
    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLEDIR", "C_Currency_ID",
          "", "",
          Utility.getContext(this, vars, "#AccessibleOrgTree",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          Utility.getContext(this, vars, "#User_Client",
              "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
          0);
      Utility.fillSQLParameters(this, vars, null, comboTableData,
          "ReportInvoiceCustomerDimensionalAnalysesJR_legacy", strCurrencyId);
      xmlDocument.setData("reportC_Currency_ID", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    if (vars.getLanguage().equals("en_US")) {
      xmlDocument.setData("structure1",
          ReportInvoiceCustomerDimensionalAnalysesJRData.selectNotShown(this, strShown));
      xmlDocument.setData("structure2",
          strShown.equals("") ? new ReportInvoiceCustomerDimensionalAnalysesJRData[0]
              : ReportInvoiceCustomerDimensionalAnalysesJRData.selectShown(this, strShown));
    } else {
      xmlDocument.setData("structure1", ReportInvoiceCustomerDimensionalAnalysesJRData
          .selectNotShownTrl(this, vars.getLanguage(), strShown));
      xmlDocument.setData("structure2",
          strShown.equals("") ? new ReportInvoiceCustomerDimensionalAnalysesJRData[0]
              : ReportInvoiceCustomerDimensionalAnalysesJRData.selectShownTrl(this,
                  vars.getLanguage(), strShown));
    }

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private void printPageHtml(HttpServletRequest request, HttpServletResponse response,
      VariablesSecureApp vars, String strComparative, String strDateFrom, String strDateTo,
      String strPartnerGroup, String strcBpartnerId, String strProductCategory,
      String strmProductId, String strNotShown, String strShown, String strDateFromRef,
      String strDateToRef, String strOrg, String strsalesrepId, String strcProjectId,
      String strProducttype, String strOrder, String strMayor, String strMenor,
      String strPartnerSalesrepId, String strCurrencyId, String strOutput)
      throws IOException, ServletException {
    String localStrShown = strShown;
    String localStrOrg = strOrg;
    if (log4j.isDebugEnabled()) {
      log4j.debug("Output: print html");
    }
    String strOrderby = "";
    String[] discard = { "", "", "", "", "", "", "", "", "", "" };
    String[] discard1 = { "selEliminarBody1", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard", "discard", "discard", "discard",
        "discard", "discard", "discard", "discard", "discard" };
    if (localStrOrg.equals("")) {
      localStrOrg = vars.getOrg();
    }
    if (strComparative.equals("Y")) {
      discard1[0] = "selEliminarBody2";
    }
    String strTitle = "";
    strTitle = Utility.messageBD(this, "From", vars.getLanguage()) + " " + strDateFrom + " "
        + Utility.messageBD(this, "to", vars.getLanguage()) + " " + strDateTo;
    if (!strPartnerGroup.equals("")) {
      strTitle = strTitle + ", " + Utility.messageBD(this, "ForBPartnerGroup", vars.getLanguage())
          + " " + ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectBpgroup(this,
              strPartnerGroup);
    }

    if (!strProductCategory.equals("")) {
      strTitle = strTitle + ", " + Utility.messageBD(this, "ProductCategory", vars.getLanguage())
          + " " + ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectProductCategory(this,
              strProductCategory);
    }
    if (!strcProjectId.equals("")) {
      strTitle = strTitle + ", " + Utility.messageBD(this, "Project", vars.getLanguage()) + " "
          + ReportInvoiceCustomerDimensionalAnalysesJRData.selectProject(this, strcProjectId);
    }
    if (!strProducttype.equals("")) {
      strTitle = strTitle + ", " + Utility.messageBD(this, "PRODUCTTYPE", vars.getLanguage()) + " "
          + ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectProducttype(this, "270",
              vars.getLanguage(), strProducttype);
    }
    if (!strsalesrepId.equals("")) {
      strTitle = strTitle + ", " + Utility.messageBD(this, "TheClientSalesRep", vars.getLanguage())
          + " " + ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectSalesrep(this,
              strsalesrepId);
    }
    if (!strPartnerSalesrepId.equals("")) {
      strTitle = strTitle + " " + Utility.messageBD(this, "And", vars.getLanguage()) + " "
          + Utility.messageBD(this, "TheClientSalesRep", vars.getLanguage()) + " "
          + ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectSalesrep(this,
              strPartnerSalesrepId);
    }

    ReportInvoiceCustomerDimensionalAnalysesJRLegacyData[] data = null;
    String[] strShownArray = { "", "", "", "", "", "", "", "", "", "" };
    if (localStrShown.startsWith("(")) {
      localStrShown = localStrShown.substring(1, localStrShown.length() - 1);
    }
    if (!localStrShown.equals("")) {
      localStrShown = Replace.replace(localStrShown, "'", "");
      localStrShown = Replace.replace(localStrShown, " ", "");
      StringTokenizer st = new StringTokenizer(localStrShown, ",", false);
      int intContador = 0;
      while (st.hasMoreTokens()) {
        strShownArray[intContador] = st.nextToken();
        intContador++;
      }

    }

    ReportInvoiceCustomerDimensionalAnalysesJRLegacyData[] dimensionLabel = null;
    if (vars.getLanguage().equals("en_US")) {
      dimensionLabel = ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectNotShown(this,
          "");
    } else {
      dimensionLabel = ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectNotShownTrl(this,
          vars.getLanguage(), "");
    }

    // Checking report limit first
    StringBuffer levelsconcat = new StringBuffer();
    levelsconcat.append("''");
    String[] strLevelLabel = { "", "", "", "", "", "", "", "", "", "" };
    String[] strTextShow = { "", "", "", "", "", "", "", "", "", "" };
    int intOrder = 0;
    int intProductLevel = 11;
    int intAuxDiscard = -1;
    for (int i = 0; i < 10; i++) {
      if (strShownArray[i].equals("1")) {
        strTextShow[i] = "C_BP_GROUP.NAME";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[0].name;
        levelsconcat.append(" || ");
        levelsconcat.append("C_BP_GROUP.C_BP_GROUP_ID");
      } else if (strShownArray[i].equals("2")) {
        strTextShow[i] = "AD_COLUMN_IDENTIFIER(to_char('C_Bpartner'), to_char( C_BPARTNER.C_BPARTNER_ID), to_char( '"
            + vars.getLanguage() + "'))";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[1].name;
        levelsconcat.append(" || ");
        levelsconcat.append("C_BPARTNER.C_BPARTNER_ID");
      } else if (strShownArray[i].equals("3")) {
        strTextShow[i] = "M_PRODUCT_CATEGORY.NAME";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[2].name;
        levelsconcat.append(" || ");
        levelsconcat.append("M_PRODUCT_CATEGORY.M_PRODUCT_CATEGORY_ID");
      } else if (strShownArray[i].equals("4")) {
        strTextShow[i] = "AD_COLUMN_IDENTIFIER(to_char('M_Product'), to_char( M_PRODUCT.M_PRODUCT_ID), to_char( '"
            + vars.getLanguage()
            + "'))|| CASE WHEN uomsymbol IS NULL THEN '' ELSE to_char(' ('||uomsymbol||')') END";
        intAuxDiscard = i;
        intOrder++;
        intProductLevel = i + 1;
        strLevelLabel[i] = dimensionLabel[3].name;
        levelsconcat.append(" || ");
        levelsconcat.append("M_PRODUCT.M_PRODUCT_ID");
      } else if (strShownArray[i].equals("5")) {
        strTextShow[i] = "C_INVOICE.DOCUMENTNO";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[4].name;
        levelsconcat.append(" || ");
        levelsconcat.append("C_INVOICE.C_INVOICE_ID");
      } else if (strShownArray[i].equals("6")) {
        strTextShow[i] = "AD_USER.FIRSTNAME||' '||' '||AD_USER.LASTNAME";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[5].name;
        levelsconcat.append(" || ");
        levelsconcat.append("AD_USER.AD_USER_ID");
      } else if (strShownArray[i].equals("8")) {
        strTextShow[i] = "AD_ORG.NAME";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[6].name;
        levelsconcat.append(" || ");
        levelsconcat.append("AD_ORG.AD_ORG_ID");
      } else if (strShownArray[i].equals("9")) {
        strTextShow[i] = "CASE WHEN AD_USER.AD_USER_ID IS NOT NULL THEN AD_COLUMN_IDENTIFIER(to_char('Ad_User'), to_char( AD_USER.AD_USER_ID), to_char( '"
            + vars.getLanguage() + "')) ELSE '' END";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[7].name;
        levelsconcat.append(" || ");
        levelsconcat.append("AD_USER.AD_USER_ID");
      } else if (strShownArray[i].equals("10")) {
        strTextShow[i] = "C_PROJECT.NAME";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[8].name;
        levelsconcat.append(" || ");
        levelsconcat.append("C_PROJECT.C_PROJECT_ID");
      } else if (strShownArray[i].equals("11")) {
        strTextShow[i] = "AD_COLUMN_IDENTIFIER(to_char('C_Bpartner_Location'), to_char( M_INOUT.C_BPARTNER_LOCATION_ID), to_char( '"
            + vars.getLanguage() + "'))";
        intOrder++;
        strLevelLabel[i] = dimensionLabel[9].name;
        levelsconcat.append(" || ");
        levelsconcat.append("M_INOUT.C_BPARTNER_LOCATION_ID");
      } else {
        strTextShow[i] = "''";
        discard[i] = "display:none;";
      }
    }
    if (intOrder != 0 || intAuxDiscard != -1) {
      int k = 1;
      if (intOrder == 1) {
        strOrderby = " ORDER BY  NIVEL" + k + ",";
      } else {
        strOrderby = " ORDER BY ";
      }
      while (k < intOrder) {
        strOrderby = strOrderby + "NIVEL" + k + ",";
        k++;
      }
      if (k == 1) {
        if (strOrder.equals("Normal")) {
          strOrderby = " ORDER BY NIVEL" + k;
        } else if (strOrder.equals("Amountasc")) {
          strOrderby = " ORDER BY CONVAMOUNT ASC";
        } else if (strOrder.equals("Amountdesc")) {
          strOrderby = " ORDER BY CONVAMOUNT DESC";
        } else {
          strOrderby = "1";
        }
      } else {
        if (strOrder.equals("Normal")) {
          strOrderby += "NIVEL" + k;
        } else if (strOrder.equals("Amountasc")) {
          strOrderby += "CONVAMOUNT ASC";
        } else if (strOrder.equals("Amountdesc")) {
          strOrderby += "CONVAMOUNT DESC";
        } else {
          strOrderby = "1";
        }
      }

    } else {
      strOrderby = " ORDER BY 1";
    }
    String strHaving = "";
    if (!strMayor.equals("") && !strMenor.equals("")) {
      strHaving = " HAVING (SUM(CONVAMOUNT) >= " + strMayor + " AND SUM(CONVAMOUNT) <= " + strMenor
          + ")";
    } else if (!strMayor.equals("") && strMenor.equals("")) {
      strHaving = " HAVING (SUM(CONVAMOUNT) >= " + strMayor + ")";
    } else if (strMayor.equals("") && !strMenor.equals("")) {
      strHaving = " HAVING (SUM(CONVAMOUNT) <= " + strMenor + ")";
    }
    strOrderby = strHaving + strOrderby;

    int limit = 0;
    int mycount = 0;
    try {
      limit = Integer.parseInt(Utility.getPreference(vars, "ReportsLimit", ""));
      if (limit > 0) {
        mycount = Integer.parseInt((strComparative.equals("Y"))
            ? ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectCount(this,
                levelsconcat.toString(),
                Tree.getMembers(this, TreeData.getTreeOrg(this, vars.getClient()), localStrOrg),
                Utility.getContext(this, vars, "#User_Client",
                    "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
                strPartnerGroup, strcBpartnerId, strProductCategory, strmProductId, strsalesrepId,
                strPartnerSalesrepId, strcProjectId, strProducttype, strDateFrom,
                DateTimeData.nDaysAfter(this, strDateTo, "1"), strDateFromRef,
                DateTimeData.nDaysAfter(this, strDateToRef, "1"))
            : ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectNoComparativeCount(this,
                levelsconcat.toString(),
                Tree.getMembers(this, TreeData.getTreeOrg(this, vars.getClient()), localStrOrg),
                Utility.getContext(this, vars, "#User_Client",
                    "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
                strPartnerGroup, strcBpartnerId, strProductCategory, strmProductId, strsalesrepId,
                strPartnerSalesrepId, strcProjectId, strProducttype, strDateFrom,
                DateTimeData.nDaysAfter(this, strDateTo, "1")));
      }
    } catch (NumberFormatException e) {
    }

    if (limit > 0 && mycount > limit) {
      String msgbody = Utility.messageBD(this, "ReportsLimitBody", vars.getLanguage());
      msgbody = msgbody.replace("@rows@", Integer.toString(mycount));
      msgbody = msgbody.replace("@limit@", Integer.toString(limit));
      advisePopUp(request, response, "ERROR",
          Utility.messageBD(this, "ReportsLimitHeader", vars.getLanguage()), msgbody);
    } else {
      // Checks if there is a conversion rate for each of the transactions
      // of
      // the report
      String strConvRateErrorMsg = "";
      OBError myMessage = null;
      myMessage = new OBError();
      String strBaseCurrencyId = Utility.stringBaseCurrencyId(this, vars.getClient());
      if (strComparative.equals("Y")) {
        try {
          data = ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.select(this, strCurrencyId,
              strBaseCurrencyId, strTextShow[0], strTextShow[1], strTextShow[2], strTextShow[3],
              strTextShow[4], strTextShow[5], strTextShow[6], strTextShow[7], strTextShow[8],
              strTextShow[9],
              Tree.getMembers(this, TreeData.getTreeOrg(this, vars.getClient()), localStrOrg),
              Utility.getContext(this, vars, "#User_Client",
                  "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
              strDateFrom, DateTimeData.nDaysAfter(this, strDateTo, "1"), strPartnerGroup,
              strcBpartnerId, strProductCategory, strmProductId, strsalesrepId,
              strPartnerSalesrepId, strcProjectId, strProducttype, strDateFromRef,
              DateTimeData.nDaysAfter(this, strDateToRef, "1"), strOrderby);
        } catch (ServletException ex) {
          myMessage = Utility.translateError(this, vars, vars.getLanguage(), ex.getMessage());
        }
      } else {
        try {
          data = ReportInvoiceCustomerDimensionalAnalysesJRLegacyData.selectNoComparative(this,
              strCurrencyId, strBaseCurrencyId, strTextShow[0], strTextShow[1], strTextShow[2],
              strTextShow[3], strTextShow[4], strTextShow[5], strTextShow[6], strTextShow[7],
              strTextShow[8], strTextShow[9],
              Tree.getMembers(this, TreeData.getTreeOrg(this, vars.getClient()), localStrOrg),
              Utility.getContext(this, vars, "#User_Client",
                  "ReportInvoiceCustomerDimensionalAnalysesJR_legacy"),
              strDateFrom, DateTimeData.nDaysAfter(this, strDateTo, "1"), strPartnerGroup,
              strcBpartnerId, strProductCategory, strmProductId, strsalesrepId,
              strPartnerSalesrepId, strcProjectId, strProducttype, strOrderby);
        } catch (ServletException ex) {
          myMessage = Utility.translateError(this, vars, vars.getLanguage(), ex.getMessage());
        }
      }
      strConvRateErrorMsg = myMessage.getMessage();
      // If a conversion rate is missing for a certain transaction, an
      // error
      // message window pops-up.
      if (!strConvRateErrorMsg.equals("") && strConvRateErrorMsg != null) {
        advisePopUp(request, response, "ERROR",
            Utility.messageBD(this, "NoConversionRateHeader", vars.getLanguage()),
            strConvRateErrorMsg);
      } else { // Otherwise, the report is launched
        String strReportPath;
        if (strComparative.equals("Y")) {
          strReportPath = "@basedesign@/org/openbravo/erpCommon/ad_reports/ReportInvoiceCustomerDimensionalAnalysesComparativeJR_legacy.jrxml";
        } else {
          strReportPath = "@basedesign@/org/openbravo/erpCommon/ad_reports/ReportInvoiceCustomerDimensionalAnalysesNoComparativeJR_legacy.jrxml";
        }
        if (data == null || data.length == 0) {
          advisePopUp(request, response, "WARNING",
              Utility.messageBD(this, "ProcessStatus-W", vars.getLanguage()),
              Utility.messageBD(this, "NoDataFound", vars.getLanguage()));
        } else {

          HashMap<String, Object> parameters = new HashMap<String, Object>();
          parameters.put("LEVEL1_LABEL", strLevelLabel[0]);
          parameters.put("LEVEL2_LABEL", strLevelLabel[1]);
          parameters.put("LEVEL3_LABEL", strLevelLabel[2]);
          parameters.put("LEVEL4_LABEL", strLevelLabel[3]);
          parameters.put("LEVEL5_LABEL", strLevelLabel[4]);
          parameters.put("LEVEL6_LABEL", strLevelLabel[5]);
          parameters.put("LEVEL7_LABEL", strLevelLabel[6]);
          parameters.put("LEVEL8_LABEL", strLevelLabel[7]);
          parameters.put("LEVEL9_LABEL", strLevelLabel[8]);
          parameters.put("LEVEL10_LABEL", strLevelLabel[9]);
          parameters.put("DIMENSIONS", intOrder);
          parameters.put("REPORT_SUBTITLE", strTitle);
          parameters.put("PRODUCT_LEVEL", intProductLevel);
          renderJR(vars, response, strReportPath, strOutput, parameters, data, null);
        }
      }
    }
  }

  @Override
  public String getServletInfo() {
    return "Servlet ReportInvoiceCustomerDimensionalAnalysesJR_legacy. This Servlet was made by Jon Alegría";
  } // end of getServletInfo() method
}
