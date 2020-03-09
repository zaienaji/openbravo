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
 * All portions are Copyright (C) 2001-2020 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.erpCommon.ad_reports;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.businessUtility.AccountTree;
import org.openbravo.erpCommon.businessUtility.AccountTreeData;
import org.openbravo.erpCommon.businessUtility.AccountingSchemaMiscData;
import org.openbravo.erpCommon.businessUtility.Tree;
import org.openbravo.erpCommon.businessUtility.WindowTabs;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.DateTimeData;
import org.openbravo.erpCommon.utility.FieldProviderFactory;
import org.openbravo.erpCommon.utility.LeftTabsBar;
import org.openbravo.erpCommon.utility.NavigationBar;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBLedgerUtils;
import org.openbravo.erpCommon.utility.ToolBar;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.erpCommon.utility.WindowTreeData;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.financialmgmt.accounting.OrganizationClosing;
import org.openbravo.model.financialmgmt.calendar.Calendar;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.xmlEngine.XmlDocument;

public class GeneralAccountingReports extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;
  private static final String C_ELEMENT_VALUE_TABLE_ID = "188";

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    VariablesSecureApp vars = new VariablesSecureApp(request);

    if (vars.commandIn("DEFAULT")) {
      String strDateFrom = vars.getGlobalVariable("inpDateFrom",
          "GeneralAccountingReports|dateFrom", "");
      String strDateTo = vars.getGlobalVariable("inpDateTo", "GeneralAccountingReports|dateTo", "");
      String strDateFromRef = vars.getGlobalVariable("inpDateFromRef",
          "GeneralAccountingReports|dateFromRef", "");
      String strDateToRef = vars.getGlobalVariable("inpDateToRef",
          "GeneralAccountingReports|dateToRef", "");
      String strAsDateTo = vars.getGlobalVariable("inpAsDateTo",
          "GeneralAccountingReports|asDateTo", "");
      String strAsDateToRef = vars.getGlobalVariable("inpAsDateToRef",
          "GeneralAccountingReports|asDateToRef", "");
      String strPageNo = vars.getGlobalVariable("inpPageNo", "GeneralAccountingReports|PageNo",
          "1");
      String strElementValue = vars.getGlobalVariable("inpcElementvalueId",
          "GeneralAccountingReports|C_ElementValue_ID", "");
      String strConImporte = vars.getGlobalVariable("inpConImporte",
          "GeneralAccountingReports|conImporte", "N");
      String strConCodigo = vars.getGlobalVariable("inpConCodigo",
          "GeneralAccountingReports|conCodigo", "N");
      /* Improved Balance Sheet */
      String strCompareTo = vars.getGlobalVariable("inpCompareTo",
          "GeneralAccountingReports|compareTo", "Y");

      String strLevel = vars.getGlobalVariable("inpLevel", "GeneralAccountingReports|level", "");
      printPageDataSheet(response, vars, "", "", strDateFrom, strDateTo, strPageNo, strDateFromRef,
          strDateToRef, strAsDateTo, strAsDateToRef, strElementValue, strConImporte, "", strLevel,
          strConCodigo, "", strCompareTo);
    } else if (vars.commandIn("FIND")) {
      String strcAcctSchemaId = vars.getStringParameter("inpcAcctSchemaId", "");
      String strAgno = vars.getRequiredGlobalVariable("inpAgno", "GeneralAccountingReports|agno");
      /* Improved Balance Sheet */
      String strAgnoRef = vars.getRequestGlobalVariable("inpAgnoRef",
          "GeneralAccountingReports|agnoRef");
      String strDateFrom = vars.getRequestGlobalVariable("inpDateFrom",
          "GeneralAccountingReports|dateFrom");
      String strDateTo = vars.getRequestGlobalVariable("inpDateTo",
          "GeneralAccountingReports|dateTo");
      String strDateFromRef = vars.getRequestGlobalVariable("inpDateFromRef",
          "GeneralAccountingReports|dateFromRef");
      String strDateToRef = vars.getRequestGlobalVariable("inpDateToRef",
          "GeneralAccountingReports|dateToRef");
      String strPageNo = vars.getRequestGlobalVariable("inpPageNo",
          "GeneralAccountingReports|PageNo");
      String strAsDateTo = vars.getRequestGlobalVariable("inpAsDateTo",
          "GeneralAccountingReports|asDateTo");
      String strAsDateToRef = vars.getRequestGlobalVariable("inpAsDateToRef",
          "GeneralAccountingReports|asDateToRef");
      String strElementValue = vars.getRequiredGlobalVariable("inpcElementvalueId",
          "GeneralAccountingReports|C_ElementValue_ID");
      String strConImporte = vars.getRequestGlobalVariable("inpConImporte",
          "GeneralAccountingReports|conImporte");
      String strConCodigo = vars.getRequestGlobalVariable("inpConCodigo",
          "GeneralAccountingReports|conCodigo");
      /* Improved Balance Sheet */
      String strCompareTo = vars.getRequestGlobalVariable("inpCompareTo",
          "GeneralAccountingReports|compareTo");
      String strOrg = vars.getRequestGlobalVariable("inpOrganizacion",
          "GeneralAccountingReports|organizacion");
      String strLevel = vars.getRequestGlobalVariable("inpLevel", "GeneralAccountingReports|level");
      printPagePDF(request, response, vars, strAgno, strAgnoRef, strDateFrom, strDateTo,
          strDateFromRef, strDateToRef, strAsDateTo, strAsDateToRef, strElementValue, strConImporte,
          strOrg, strLevel, strConCodigo, strcAcctSchemaId, strPageNo, strCompareTo);
    } else if (vars.commandIn("LEDGER")) {
      String strOrg = vars.getStringParameter("inpOrganizacion");
      if (StringUtils.isEmpty(strOrg)) {
        strOrg = vars.getOrg();
      }
      String strcAcctSchemaId = OBLedgerUtils.getOrgLedger(strOrg);
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter out = response.getWriter();
      out.print(StringEscapeUtils.escapeHtml(strcAcctSchemaId));
      out.close();
    } else if (vars.commandIn("CMBORG")) {
      String strAccSchema = vars.getStringParameter("inpcAcctSchemaId");
      String strAcctRpt = vars.getStringParameter("inpcElementvalueId", "");
      if (StringUtils.isNotEmpty(strAcctRpt)) {
        strAcctRpt = strAcctRpt.substring(1);
      }
      String strOrg = vars.getStringParameter("inpOrganizacion", "");
      String strOrgList = "";
      List<String> orgList = getRoleOrganizationList(OBContext.getOBContext().getRole().getId());
      int i = 0;
      for (String org : orgList) {
        if (i == 0) {
          strOrgList += "'" + org + "'";
        } else {
          strOrgList += ",'" + org + "'";
        }
        i++;
      }
      ConnectionProvider readOnlyCP = DalConnectionProvider.getReadOnlyConnectionProvider();
      GeneralAccountingReportsData[] data = GeneralAccountingReportsData
          .selectOrgsDouble(readOnlyCP, vars.getClient(), strOrgList, strAccSchema, strAcctRpt);
      String combobox = getJSONComboBox(data, strOrg, false);
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter out = response.getWriter();
      out.println("objson = " + combobox);
      out.close();
    } else if (vars.commandIn("CMBYEAR")) {
      String strOrg = vars.getStringParameter("inpOrganizacion", "");
      String strAgno = vars.getStringParameter("inpAgno", "");
      ConnectionProvider readOnlyCP = DalConnectionProvider.getReadOnlyConnectionProvider();
      GeneralAccountingReportsData[] data = GeneralAccountingReportsData
          .selectYearsDouble(readOnlyCP, vars.getUserClient(), strOrg);
      String combobox = getJSONComboBox(data, strAgno, false);
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter out = response.getWriter();
      out.println("objson = " + combobox);
      out.close();
    } else {
      pageError(response);
    }
  }

  private String getJSONComboBox(GeneralAccountingReportsData[] data, String selectedValue,
      boolean isMandatory) {
    JSONObject json = new JSONObject();
    JSONArray select = new JSONArray();
    Map<String, String> attr = null;
    Set<String> orgs = new HashSet<String>();
    try {
      int i = 0;
      if (!isMandatory) {
        attr = new HashMap<String, String>();
        attr.put("value", "");
        attr.put("selected", "false");
        attr.put("text", "");
        select.put(i, attr);
        json.put("optionlist", select);
        i++;
      }
      for (GeneralAccountingReportsData obj : data) {
        if (!orgs.contains(obj.id)) {
          orgs.add(obj.id);
          attr = new HashMap<String, String>();
          attr.put("value", obj.id);
          attr.put("selected", StringUtils.equals(obj.name, selectedValue) ? "true" : "false");
          attr.put("text", obj.name);
          select.put(i, attr);
          json.put("optionlist", select);
          i++;
        }
      }
      json.put("ismandatory", String.valueOf(isMandatory));
    } catch (JSONException e) {
      log4j.error("Error creating JSON object for representing combo option", e);
    }
    return json.toString();
  }

  private void printPagePDF(HttpServletRequest request, HttpServletResponse response,
      VariablesSecureApp vars, String strYearId, String strYearRefId, String strDateFrom,
      String strDateTo, String strDateFromRef, String strDateToRef, String strAsDateTo,
      String strAsDateToRef, String strElementValue, String strConImporte, String strOrg,
      String strLevel, String strConCodigo, String strcAcctSchemaId, String strPageNo,
      String strCompareTo) throws IOException, ServletException {
    String localStrElementValue = strElementValue;
    String localStrDateToRef = strDateToRef;
    String localStrDateFrom = strDateFrom;
    String localStrDateFromRef = strDateFromRef;
    String localStrDateTo = strDateTo;
    if (log4j.isDebugEnabled()) {
      log4j.debug("Output: pdf");
    }

    String strCalculateOpening = localStrElementValue.substring(0, 1);
    localStrElementValue = localStrElementValue.substring(1, localStrElementValue.length());
    ConnectionProvider readOnlyCP = DalConnectionProvider.getReadOnlyConnectionProvider();
    GeneralAccountingReportsData[] strGroups = GeneralAccountingReportsData.selectGroups(readOnlyCP,
        localStrElementValue);

    try {
      strGroups[strGroups.length - 1].pagebreak = "";

      String[][] strElementValueDes = new String[strGroups.length][];
      if (log4j.isDebugEnabled()) {
        log4j.debug(
            "strElementValue:" + localStrElementValue + " - strGroups.length:" + strGroups.length);
      }
      for (int i = 0; i < strGroups.length; i++) {
        GeneralAccountingReportsData[] strElements = GeneralAccountingReportsData
            .selectElements(readOnlyCP, strGroups[i].id);
        strElementValueDes[i] = new String[strElements.length];
        if (log4j.isDebugEnabled()) {
          log4j.debug("strElements.length:" + strElements.length);
        }
        for (int j = 0; j < strElements.length; j++) {
          strElementValueDes[i][j] = strElements[j].id;
        }
      }
      String strTreeOrg = GeneralAccountingReportsData.treeOrg(readOnlyCP, vars.getClient());
      AccountTree[] acct = new AccountTree[strGroups.length];

      AccountTreeData[][] elements = new AccountTreeData[strGroups.length][];

      WindowTreeData[] dataTree = WindowTreeData.selectTreeIDWithTableId(readOnlyCP,
          Utility.stringList(vars.getClient()), C_ELEMENT_VALUE_TABLE_ID);
      String TreeID = "";
      if (dataTree != null && dataTree.length != 0) {
        TreeID = dataTree[0].id;
      }
      OBContext.setAdminMode(false);
      try {
        String openingEntryOwner = "";
        String openingEntryOwnerRef = "";
        // For each year, the initial and closing date is obtained
        Year year = OBDal.getReadOnlyInstance().get(Year.class, strYearId);
        Year yearRef = OBDal.getReadOnlyInstance().get(Year.class, strYearRefId);
        HashMap<String, Date> startingEndingDate = getStartingEndingDate(year);
        /* Improved Balance Sheet */
        String yrRef = "";
        HashMap<String, Date> startingEndingDateRef = null;
        if (StringUtils.equals(strCompareTo, "Y")) {
          yrRef = yearRef.getFiscalYear();
          startingEndingDateRef = getStartingEndingDate(yearRef);
        }
        // Years to be included as no closing is present
        String strYearsToClose = "";
        String strYearsToCloseRef = "";
        if (StringUtils.equals(strCalculateOpening, "Y")) {
          strCalculateOpening = "N";
          localStrDateTo = strAsDateTo;
          localStrDateToRef = strAsDateToRef;
          localStrDateFrom = "";
          localStrDateFromRef = "";
          String[] yearsInfo = getYearsToClose(startingEndingDate.get("startingDate"), strOrg,
              year.getCalendar(), strcAcctSchemaId, false);
          strYearsToClose = yearsInfo[0];
          openingEntryOwner = yearsInfo[1];
          if (strYearsToClose.length() > 0) {
            strCalculateOpening = "Y";
            strYearsToClose = "," + strYearsToClose;
          }
          /* Improved Balance Sheet */
          if (StringUtils.equals(strCompareTo, "Y")) {
            yearsInfo = getYearsToClose(startingEndingDateRef.get("startingDate"), strOrg,
                yearRef.getCalendar(), strcAcctSchemaId, true);
            strYearsToCloseRef = yearsInfo[0];
            openingEntryOwnerRef = yearsInfo[1];
            if (strYearsToCloseRef.length() > 0) {
              strCalculateOpening = "Y";
              strYearsToCloseRef = "," + strYearsToCloseRef;
            }
          }
        }
        // Income summary amount is calculated and included in the balance sheet data
        String strIncomeSummaryAccount = GeneralAccountingReportsData.incomesummary(readOnlyCP,
            strcAcctSchemaId);

        for (int i = 0; i < strGroups.length; i++) {
          // All account tree is obtained
          if (StringUtils.equals(vars.getLanguage(), "en_US")) {
            elements[i] = AccountTreeData.select(readOnlyCP, strConCodigo, TreeID);
          } else {
            elements[i] = AccountTreeData.selectTrl(readOnlyCP, strConCodigo, vars.getLanguage(),
                TreeID);
          }
          // For each account with movements in the year, debit and credit total amounts are
          // calculated according to fact_acct movements.
          AccountTreeData[] accounts = AccountTreeData.selectFactAcct(readOnlyCP,
              Utility.getContext(readOnlyCP, vars, "#AccessibleOrgTree",
                  "GeneralAccountingReports"),
              Utility.getContext(readOnlyCP, vars, "#User_Client", "GeneralAccountingReports"),
              localStrDateFrom, DateTimeData.nDaysAfter(readOnlyCP, localStrDateTo, "1"),
              strcAcctSchemaId, Tree.getMembers(readOnlyCP, strTreeOrg, strOrg),
              "'" + year.getFiscalYear() + "'" + strYearsToClose, openingEntryOwner, strCompareTo,
              localStrDateFromRef, DateTimeData.nDaysAfter(readOnlyCP, localStrDateToRef, "1"),
              "'" + yrRef + "'" + strYearsToCloseRef, openingEntryOwnerRef);
          {
            if (log4j.isDebugEnabled()) {
              log4j.debug("*********** strIncomeSummaryAccount: " + strIncomeSummaryAccount);
            }
            String strISyear = processIncomeSummary(localStrDateFrom,
                DateTimeData.nDaysAfter(readOnlyCP, localStrDateTo, "1"),
                "'" + year.getFiscalYear() + "'" + strYearsToClose, strTreeOrg, strOrg,
                strcAcctSchemaId);
            if (log4j.isDebugEnabled()) {
              log4j.debug("*********** strISyear: " + strISyear);
            }
            /* Improved Balance Sheet */
            String strISyearRef = "0";
            if (StringUtils.equals(strCompareTo, "Y")) {
              strISyearRef = processIncomeSummary(localStrDateFromRef,
                  DateTimeData.nDaysAfter(readOnlyCP, localStrDateToRef, "1"),
                  "'" + yearRef.getFiscalYear() + "'" + strYearsToCloseRef, strTreeOrg, strOrg,
                  strcAcctSchemaId);
              if (log4j.isDebugEnabled()) {
                log4j.debug("*********** strISyearRef: " + strISyearRef);
              }
            }
            accounts = appendRecords(accounts, strIncomeSummaryAccount, strISyear, strISyearRef);

          }
          // Report tree is built with given the account tree, and the amounts obtained from
          // fact_acct
          acct[i] = new AccountTree(vars, readOnlyCP, elements[i], accounts, strElementValueDes[i]);
          if (acct[i] != null) {
            acct[i].filterSVC();
            acct[i].filter(StringUtils.equals(strConImporte, "Y"), strLevel, false);
          } else if (log4j.isDebugEnabled()) {
            log4j.debug("acct null!!!");
          }
        }

        String strReportName = "@basedesign@/org/openbravo/erpCommon/ad_reports/GeneralAccountingReportsPDF.jrxml";
        HashMap<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("group", strGroups);
        parameters.put("agno", year.getFiscalYear());

        parameters.put("agno2", yrRef);
        parameters.put("column", year.getFiscalYear());
        parameters.put("columnRef", yrRef);
        parameters.put("org", OrganizationData.selectOrgName(readOnlyCP, strOrg));
        parameters.put("column1", year.getFiscalYear());
        parameters.put("columnRef1", yrRef);
        parameters.put("companyName",
            GeneralAccountingReportsData.companyName(readOnlyCP, vars.getClient()));
        parameters.put("date", DateTimeData.today(readOnlyCP));
        if (StringUtils.isEmpty(localStrDateFrom)) {
          localStrDateFrom = OBDateUtils.formatDate(startingEndingDate.get("startingDate"));
        }
        if (StringUtils.isEmpty(localStrDateTo)) {
          localStrDateTo = OBDateUtils.formatDate(startingEndingDate.get("endingDate"));
        }
        /* Improved Balance Sheet */
        if (StringUtils.equals(strCompareTo, "Y")) {
          if (StringUtils.isEmpty(localStrDateFromRef)) {
            localStrDateFromRef = OBDateUtils.formatDate(startingEndingDateRef.get("startingDate"));
          }
          if (StringUtils.isEmpty(localStrDateToRef)) {
            localStrDateToRef = OBDateUtils.formatDate(startingEndingDateRef.get("endingDate"));
          }
        }
        parameters.put("period", localStrDateFrom + " - " + localStrDateTo);
        parameters.put("periodRef", localStrDateFromRef + " - " + localStrDateToRef);
        parameters.put("agnoInitial", year.getFiscalYear());
        parameters.put("agnoRef", yrRef);
        parameters.put("compareTo", StringUtils.equals(strCompareTo, "Y") ? "Y" : "N");
        parameters.put("principalTitle",
            StringUtils.equals(strCalculateOpening, "Y")
                ? GeneralAccountingReportsData.rptTitle(readOnlyCP, localStrElementValue)
                    + " (Provisional)"
                : GeneralAccountingReportsData.rptTitle(readOnlyCP, localStrElementValue));

        parameters.put("pageNo", strPageNo);

        AccountTreeData[][] trees = new AccountTreeData[strGroups.length][];
        for (int i = 0; i < strGroups.length; i++) {
          trees[i] = acct[i].getAccounts();
        }

        List<HashMap<String, String>> hashMapList = new ArrayList<HashMap<String, String>>();

        for (int i = 0; i < trees.length; i++) {
          for (int j = 0; j < trees[i].length; j++) {
            HashMap<String, String> hashMap = new HashMap<String, String>();
            hashMap.put("elementLevel", trees[i][j].elementLevel);
            hashMap.put("name", trees[i][j].name);
            hashMap.put("qty", trees[i][j].qty);
            hashMap.put("qtyRef", trees[i][j].qtyRef);
            hashMap.put("groupname", strGroups[i].name);
            hashMap.put("pagebreak", strGroups[i].pagebreak);

            hashMapList.add(hashMap);
          }
        }
        FieldProvider[] data = FieldProviderFactory.getFieldProviderArray(hashMapList);
        renderJR(vars, response, strReportName, "pdf", parameters, data, null);

      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (ArrayIndexOutOfBoundsException e) {
      advisePopUp(request, response, "ERROR",
          Utility.messageBD(readOnlyCP, "ReportWithoutNodes", vars.getLanguage()));
    }
  }

  private String[] getYearsToClose(Date startingDate, String strOrg, Calendar calendar,
      String strcAcctSchemaId, boolean isYearRef) {
    String openingEntryOwner = "";
    ArrayList<Year> previousYears = getOrderedPreviousYears(startingDate, calendar);
    Set<String> notClosedYears = new HashSet<String>();
    for (Year previousYear : previousYears) {
      for (Organization org : getCalendarOwnerOrgs(strOrg)) {
        if (isNotClosed(previousYear, org, strcAcctSchemaId)) {
          notClosedYears.add(previousYear.getFiscalYear());
        } else {
          openingEntryOwner = previousYear.getFiscalYear();
        }
      }
    }
    String[] result = { Utility.getInStrSet(notClosedYears), openingEntryOwner };
    return result;
  }

  private Set<Organization> getCalendarOwnerOrgs(String strOrg) {
    Set<Organization> calendarOwnerOrgs = new HashSet<Organization>();
    Organization organization = OBDal.getReadOnlyInstance().get(Organization.class, strOrg);
    if (organization.isAllowPeriodControl()) {
      calendarOwnerOrgs.add(organization);
    }
    for (String child : new OrganizationStructureProvider().getChildTree(strOrg, false)) {
      calendarOwnerOrgs.addAll(getCalendarOwnerOrgs(child));
    }
    return calendarOwnerOrgs;
  }

  private boolean isNotClosed(Year year, Organization org, String strcAcctSchemaId) {
    OBContext.setAdminMode(false);
    try {
      OBCriteria<OrganizationClosing> obc = OBDal.getReadOnlyInstance()
          .createCriteria(OrganizationClosing.class);
      obc.createAlias(OrganizationClosing.PROPERTY_ORGACCTSCHEMA, "oa");
      obc.add(Restrictions.eq("organization", org));
      obc.add(Restrictions.eq(OrganizationClosing.PROPERTY_YEAR, year));
      obc.add(Restrictions.eq("oa.accountingSchema.id", strcAcctSchemaId));
      obc.add(Restrictions.isNotNull(OrganizationClosing.PROPERTY_CLOSINGFACTACCTGROUP));
      obc.setMaxResults(1);
      return obc.uniqueResult() == null ? true : false;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private ArrayList<Year> getOrderedPreviousYears(Date startingDate, Calendar calendar) {
    final StringBuilder hqlString = new StringBuilder();
    ArrayList<Year> result = new ArrayList<Year>();
    hqlString.append("select y");
    hqlString.append(" from FinancialMgmtYear y, FinancialMgmtPeriod as p");
    hqlString.append(
        " where p.year = y  and p.endingDate < :date and y.calendar = :calendar order by p.startingDate");
    final Session session = OBDal.getReadOnlyInstance().getSession();
    final Query<Year> query = session.createQuery(hqlString.toString(), Year.class);
    query.setParameter("date", startingDate);
    query.setParameter("calendar", calendar);
    for (Year previousYear : query.list()) {
      if (!(result.contains(previousYear))) {
        result.add(previousYear);
      }
    }
    return result;
  }

  private HashMap<String, Date> getStartingEndingDate(Year year) {
    final StringBuilder hqlString = new StringBuilder();
    HashMap<String, Date> result = new HashMap<String, Date>();
    result.put("startingDate", null);
    result.put("endingDate", null);
    hqlString.append("select min(p.startingDate) as startingDate, max(p.endingDate) as endingDate");
    hqlString.append(" from FinancialMgmtPeriod as p");
    hqlString.append(" where p.year = :year");

    final Session session = OBDal.getReadOnlyInstance().getSession();
    final Query<Object[]> query = session.createQuery(hqlString.toString(), Object[].class);
    query.setParameter("year", year);
    for (Object[] values : query.list()) {
      result.put("startingDate", (Date) values[0]);
      result.put("endingDate", (Date) values[1]);
    }
    return result;
  }

  private AccountTreeData[] appendRecords(AccountTreeData[] data, String strIncomeSummary,
      String strISyear, String strISyearRef) throws ServletException {
    if (data == null || StringUtils.isEmpty(strIncomeSummary) || StringUtils.isEmpty(strISyear)
        || StringUtils.isEmpty(strISyearRef)) {
      return data;
    }
    AccountTreeData[] data2 = new AccountTreeData[data.length + 1];
    boolean found = false;
    for (int i = 0; i < data.length; i++) {
      if (StringUtils.equals(data[i].id, strIncomeSummary)) {
        found = true;
        BigDecimal isYear = new BigDecimal(strISyear);
        BigDecimal isYearRef = new BigDecimal(strISyearRef);
        data[i].qty = (new BigDecimal(data[i].qty).subtract(isYear)).toPlainString();
        data[i].qtycredit = (new BigDecimal(data[i].qtycredit).add(isYear)).toPlainString();
        data[i].qtyRef = (new BigDecimal(data[i].qtyRef).subtract(isYearRef)).toPlainString();
        data[i].qtycreditRef = (new BigDecimal(data[i].qtycreditRef).add(isYearRef))
            .toPlainString();
      }
      data2[i] = data[i];
    }
    if (!found) {
      data2[data2.length - 1] = new AccountTreeData();
      data2[data2.length - 1].id = strIncomeSummary;
      data2[data2.length - 1].qty = new BigDecimal(strISyear).negate().toPlainString();
      data2[data2.length - 1].qtycredit = strISyear;
      data2[data2.length - 1].qtyRef = new BigDecimal(strISyearRef).negate().toPlainString();
      data2[data2.length - 1].qtycreditRef = strISyearRef;
    } else {
      return data;
    }
    return data2;
  }

  private String processIncomeSummary(String strDateFrom, String strDateTo, String strAgno,
      String strTreeOrg, String strOrg, String strcAcctSchemaId)
      throws ServletException, IOException {
    ConnectionProvider readOnlyCP = DalConnectionProvider.getReadOnlyConnectionProvider();
    String strISRevenue = GeneralAccountingReportsData.selectPyG(readOnlyCP, "R", strDateFrom,
        strDateTo, strcAcctSchemaId, strAgno, Tree.getMembers(readOnlyCP, strTreeOrg, strOrg));
    String strISExpense = GeneralAccountingReportsData.selectPyG(readOnlyCP, "E", strDateFrom,
        strDateTo, strcAcctSchemaId, strAgno, Tree.getMembers(readOnlyCP, strTreeOrg, strOrg));
    BigDecimal totalRevenue = new BigDecimal(strISRevenue);
    BigDecimal totalExpense = new BigDecimal(strISExpense);
    BigDecimal total = totalRevenue.add(totalExpense);
    if (log4j.isDebugEnabled()) {
      log4j.debug(total.toString());
    }
    return total.toString();
  }

  private void printPageDataSheet(HttpServletResponse response, VariablesSecureApp vars,
      String strAgno, String strAgnoRef, String strDateFrom, String strDateTo, String strPageNo,
      String strDateFromRef, String strDateToRef, String strAsDateTo, String strAsDateToRef,
      String strElementValue, String strConImporte, String strOrg, String strLevel,
      String strConCodigo, String strcAcctSchemaId, String strCompareTo)
      throws IOException, ServletException {
    if (log4j.isDebugEnabled()) {
      log4j.debug("Output: dataSheet");
    }
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_reports/GeneralAccountingReports")
        .createXmlDocument();
    ConnectionProvider readOnlyCP = DalConnectionProvider.getReadOnlyConnectionProvider();
    ToolBar toolbar = new ToolBar(readOnlyCP, vars.getLanguage(), "GeneralAccountingReports", false,
        "", "", "", false, "ad_reports", strReplaceWith, false, true);
    toolbar.prepareSimpleToolBarTemplate();
    xmlDocument.setParameter("toolbar", toolbar.toString());

    try {
      WindowTabs tabs = new WindowTabs(readOnlyCP, vars,
          "org.openbravo.erpCommon.ad_reports.GeneralAccountingReports");
      xmlDocument.setParameter("parentTabContainer", tabs.parentTabs());
      xmlDocument.setParameter("mainTabContainer", tabs.mainTabs());
      xmlDocument.setParameter("childTabContainer", tabs.childTabs());
      xmlDocument.setParameter("theme", vars.getTheme());
      NavigationBar nav = new NavigationBar(readOnlyCP, vars.getLanguage(),
          "GeneralAccountingReports.html", classInfo.id, classInfo.type, strReplaceWith,
          tabs.breadcrumb());
      xmlDocument.setParameter("navigationBar", nav.toString());
      LeftTabsBar lBar = new LeftTabsBar(readOnlyCP, vars.getLanguage(),
          "GeneralAccountingReports.html", strReplaceWith);
      xmlDocument.setParameter("leftTabs", lBar.manualTemplate());
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    {
      OBError myMessage = vars.getMessage("GeneralAccountingReports");
      vars.removeMessage("GeneralAccountingReports");
      if (myMessage != null) {
        xmlDocument.setParameter("messageType", myMessage.getType());
        xmlDocument.setParameter("messageTitle", myMessage.getTitle());
        xmlDocument.setParameter("messageMessage", myMessage.getMessage());
      }
    }

    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("agno", strAgno);
    xmlDocument.setParameter("agnoRef", strAgnoRef);
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
    xmlDocument.setParameter("PageNo", strPageNo);
    xmlDocument.setParameter("dateToRefdisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("dateToRefsaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("asDateTo", strAsDateTo);
    xmlDocument.setParameter("asDateTodisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("asDateTosaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("asDateToRef", strAsDateToRef);
    xmlDocument.setParameter("asDateToRefdisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("asDateToRefsaveFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("conImporte", strConImporte);
    xmlDocument.setParameter("conCodigo", strConCodigo);
    /* Improved Balance Sheet */
    xmlDocument.setParameter("compareTo", strCompareTo);
    xmlDocument.setParameter("C_Org_ID", strOrg);
    xmlDocument.setParameter("C_ElementValue_ID", strElementValue);
    xmlDocument.setParameter("level", strLevel);
    xmlDocument.setParameter("cAcctschemaId", strcAcctSchemaId);
    xmlDocument.setData("reportC_ACCTSCHEMA_ID", "liststructure",
        AccountingSchemaMiscData.selectC_ACCTSCHEMA_ID(readOnlyCP,
            Utility.getContext(readOnlyCP, vars, "#AccessibleOrgTree", "GeneralAccountingReports"),
            Utility.getContext(readOnlyCP, vars, "#User_Client", "GeneralAccountingReports"),
            strcAcctSchemaId));
    try {
      ComboTableData comboTableData = new ComboTableData(vars, readOnlyCP, "LIST", "",
          "C_ElementValue level", "",
          Utility.getContext(readOnlyCP, vars, "#AccessibleOrgTree", "GeneralAccountingReports"),
          Utility.getContext(readOnlyCP, vars, "#User_Client", "GeneralAccountingReports"), 0);
      Utility.fillSQLParameters(readOnlyCP, vars, null, comboTableData, "GeneralAccountingReports",
          "");
      xmlDocument.setData("reportLevel", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    xmlDocument.setParameter("orgs", Utility.arrayDobleEntrada("arrOrgs", new FieldProvider[0]));
    xmlDocument.setParameter("accountingReports",
        Utility.arrayDobleEntrada("arrAccountingReports",
            GeneralAccountingReportsData.selectRptDouble(readOnlyCP, Utility.getContext(readOnlyCP,
                vars, "#AccessibleOrgTree", "GeneralAccountingReports"))));
    xmlDocument.setParameter("years", Utility.arrayDobleEntrada("arrYears", new FieldProvider[0]));
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private List<String> getRoleOrganizationList(String roleId) {
    try {
      OBContext.setAdminMode(false);
      StringBuffer hqlString = new StringBuffer();
      hqlString.append(" select " + RoleOrganization.PROPERTY_ORGANIZATION + ".id");
      hqlString.append(" from " + RoleOrganization.ENTITY_NAME);
      hqlString.append(" where " + RoleOrganization.PROPERTY_ROLE + ".id = :roleId");
      Query<String> query = OBDal.getReadOnlyInstance()
          .getSession()
          .createQuery(hqlString.toString(), String.class);
      query.setParameter("roleId", roleId);
      return query.list();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  @Override
  public String getServletInfo() {
    return "Servlet GeneralAccountingReportsData";
  } // end of getServletInfo() method

}
