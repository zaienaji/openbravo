/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2010-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s): Enterprise Intelligence Systems (http://www.eintel.com.au).
 *************************************************************************
 */
package org.openbravo.advpaymentmngt.ad_actionbutton;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.advpaymentmngt.dao.AdvPaymentMngtDao;
import org.openbravo.advpaymentmngt.process.FIN_AddPayment;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.filter.IsIDFilter;
import org.openbravo.base.filter.RequestFilter;
import org.openbravo.base.filter.ValueListFilter;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.erpCommon.ad_forms.AcctServer;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.DateTimeData;
import org.openbravo.erpCommon.utility.DimensionDisplayUtility;
import org.openbravo.erpCommon.utility.FieldProviderFactory;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.financialmgmt.accounting.Costcenter;
import org.openbravo.model.financialmgmt.accounting.UserDimension1;
import org.openbravo.model.financialmgmt.accounting.UserDimension2;
import org.openbravo.model.financialmgmt.gl.GLItem;
import org.openbravo.model.financialmgmt.payment.FIN_BankStatementLine;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.model.financialmgmt.payment.FinAccPaymentMethod;
import org.openbravo.model.marketing.Campaign;
import org.openbravo.model.materialmgmt.cost.ABCActivity;
import org.openbravo.model.project.Project;
import org.openbravo.service.db.CallStoredProcedure;
import org.openbravo.xmlEngine.XmlDocument;

public class AddPaymentFromTransaction extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;
  private AdvPaymentMngtDao dao;
  private String exchangeRateFormat = "#,##0.######";

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    VariablesSecureApp vars = new VariablesSecureApp(request);
    int conversionRatePrecision = FIN_Utility.getConversionRatePrecision(vars);

    if (vars.commandIn("DEFAULT")) {
      final RequestFilter docTypeFilter = new ValueListFilter("RCIN", "PDOUT");
      final boolean isReceipt = vars.getRequiredStringParameter("inpDocumentType", docTypeFilter)
          .equals("RCIN");
      final String strFinancialAccountId = vars
          .getRequiredStringParameter("inpFinFinancialAccountId", IsIDFilter.instance);
      String strFinBankStatementLineId = vars.getStringParameter("inpFinBankStatementLineId", "",
          IsIDFilter.instance);
      String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      String strTransactionDate = vars.getStringParameter("inpMainDate", "");
      String strWindowId = vars.getSessionValue("AddTransaction|windowId");

      printPage(response, vars, strFinancialAccountId, isReceipt, strFinBankStatementLineId,
          strTransactionDate, strCurrencyId, conversionRatePrecision, strWindowId);

    } else if (vars.commandIn("GRIDLIST")) {
      final String strBusinessPartnerId = vars.getRequestGlobalVariable("inpcBpartnerId", "");
      final String strFinancialAccountId = vars.getRequiredStringParameter("inpFinancialAccountId",
          IsIDFilter.instance);
      final String strExpectedDateFrom = vars.getStringParameter("inpExpectedDateFrom", "");
      final String strExpectedDateTo = vars.getStringParameter("inpExpectedDateTo", "");
      final String strTransDateFrom = vars.getStringParameter("inpTransDateFrom", "");
      final String strTransDateTo = vars.getStringParameter("inpTransDateTo", "");
      final String strDocumentType = vars.getStringParameter("inpDocumentType", "");
      final String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      final String strDocumentNo = vars.getStringParameter("inpDocumentNo", "");
      final String strSelectedPaymentDetails = vars
          .getInStringParameter("inpScheduledPaymentDetailId", IsIDFilter.instance);
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      final String strAmountFrom = vars.getNumericParameter("inpAmountFrom", "");
      final String strAmountTo = vars.getNumericParameter("inpAmountTo", "");

      printGrid(response, request, vars, strFinancialAccountId, strBusinessPartnerId,
          strExpectedDateFrom, strExpectedDateTo, strTransDateFrom, strTransDateTo, strDocumentType,
          strDocumentNo, strSelectedPaymentDetails, isReceipt, strCurrencyId, strAmountFrom,
          strAmountTo);

    } else if (vars.commandIn("PAYMENTMETHODCOMBO")) {
      final String strBusinessPartnerId = vars.getRequestGlobalVariable("inpcBpartnerId", "");
      final String strFinancialAccountId = vars.getRequiredStringParameter("inpFinancialAccountId",
          IsIDFilter.instance);
      boolean isReceipt = "Y".equals(vars.getRequiredStringParameter("isReceipt"));
      refreshPaymentMethod(response, strBusinessPartnerId, strFinancialAccountId, isReceipt);

    } else if (vars.commandIn("LOADCREDIT")) {
      final String strBusinessPartnerId = vars.getRequiredStringParameter("inpcBpartnerId");
      final boolean isReceipt = "Y".equals(vars.getRequiredStringParameter("isReceipt"));
      final String strOrgId = vars.getRequiredStringParameter("inpadOrgId");
      BigDecimal customerCredit;
      try {
        OBContext.setAdminMode(true);
        customerCredit = dao.getCustomerCredit(
            OBDal.getInstance().get(BusinessPartner.class, strBusinessPartnerId), isReceipt,
            OBDal.getInstance().get(Organization.class, strOrgId));
      } finally {
        OBContext.restorePreviousMode();
      }
      response.setContentType("text/html; charset=UTF-8");
      response.setHeader("Cache-Control", "no-cache");
      PrintWriter out = response.getWriter();
      JSONObject json = new JSONObject();
      try {
        json.put("credit", customerCredit);
      } catch (JSONException e) {
        log4j.error("Error parsing load credit JSON: " + customerCredit, e);
      }
      out.println("data = " + json.toString());
      out.close();

    } else if (vars.commandIn("EXCHANGERATE")) {
      final String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      final String strFinancialAccountCurrencyId = vars
          .getRequestGlobalVariable("inpFinancialAccountCurrencyId", "");
      final String strPaymentDate = vars.getRequestGlobalVariable("inpPaymentDate", "");
      final String strFinancialAccountId = vars.getRequiredStringParameter("inpFinancialAccountId");
      FIN_FinancialAccount fa = OBDal.getInstance()
          .get(FIN_FinancialAccount.class, strFinancialAccountId);
      exchangeRateFormat = vars.getSessionValue("#FormatOutput|generalQtyRelation", "#,##0.######");
      refreshExchangeRate(response, strCurrencyId, strFinancialAccountCurrencyId, strPaymentDate,
          fa.getOrganization(), conversionRatePrecision);
    } else if (vars.commandIn("BPARTNERBLOCK")) {
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      final String strBusinessPartnerId = vars.getRequestGlobalVariable("inpcBpartnerId", "");
      if (!"".equals(strBusinessPartnerId)) {
        BusinessPartner businessPartner = OBDal.getInstance()
            .get(BusinessPartner.class, strBusinessPartnerId);
        if (FIN_Utility.isBlockedBusinessPartner(businessPartner.getId(), isReceipt, 4)) {
          businessPartnerBlocked(response, vars, businessPartner.getIdentifier());
        }
      } else {
        String strSelectedScheduledPaymentDetailIds = vars
            .getInStringParameter("inpScheduledPaymentDetailId", "", null);
        if (!"".equals(strSelectedScheduledPaymentDetailIds)) {
          OBContext.setAdminMode(true);
          try {
            List<FIN_PaymentScheduleDetail> selectedPaymentDetails = FIN_Utility.getOBObjectList(
                FIN_PaymentScheduleDetail.class, strSelectedScheduledPaymentDetailIds);
            for (FIN_PaymentScheduleDetail psd : selectedPaymentDetails) {
              BusinessPartner bPartner;
              if (psd.getInvoicePaymentSchedule() == null) {
                bPartner = psd.getOrderPaymentSchedule().getOrder().getBusinessPartner();
              } else {
                bPartner = psd.getInvoicePaymentSchedule().getInvoice().getBusinessPartner();
              }
              if (FIN_Utility.isBlockedBusinessPartner(bPartner.getId(), isReceipt, 4)) {
                businessPartnerBlocked(response, vars, bPartner.getIdentifier());
              }
            }
          } finally {
            OBContext.restorePreviousMode();
          }
        }
      }
    } else if (vars.commandIn("SAVE") || vars.commandIn("SAVEANDPROCESS")) {
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      String strAction = null;
      if (vars.commandIn("SAVEANDPROCESS")) {
        // The default option is process
        strAction = (isReceipt ? "PRP" : "PPP");
      } else {
        strAction = vars.getRequiredStringParameter("inpActionDocument");
      }
      String strPaymentDocumentNo = vars.getRequiredStringParameter("inpDocNumber");
      String strReceivedFromId = vars.getStringParameter("inpcBpartnerId");
      String strPaymentMethodId = vars.getRequiredStringParameter("inpPaymentMethod");
      String strFinancialAccountId = vars.getRequiredStringParameter("inpFinancialAccountId");
      String strPaymentAmount = vars.getRequiredNumericParameter("inpActualPayment");
      String strPaymentDate = vars.getRequiredStringParameter("inpPaymentDate");
      String strSelectedScheduledPaymentDetailIds = vars
          .getInParameter("inpScheduledPaymentDetailId", IsIDFilter.instance);
      String strAddedGLItems = vars.getStringParameter("inpGLItems");
      JSONArray addedGLITemsArray = null;
      try {
        addedGLITemsArray = new JSONArray(strAddedGLItems);
      } catch (JSONException e) {
        log4j.error("Error parsing received GLItems JSON Array: " + strAddedGLItems, e);
        bdErrorGeneralPopUp(request, response, "Error",
            "Error parsing received GLItems JSON Array: " + strAddedGLItems);
        return;
      }
      String strDifferenceAction = vars.getStringParameter("inpDifferenceAction", "");
      BigDecimal refundAmount = BigDecimal.ZERO;
      if (strDifferenceAction.equals("refund")) {
        refundAmount = new BigDecimal(vars.getRequiredNumericParameter("inpDifference"));
      }
      String strReferenceNo = vars.getStringParameter("inpReferenceNo", "");
      String paymentCurrencyId = vars.getRequiredStringParameter("inpCurrencyId");
      BigDecimal exchangeRate = new BigDecimal(
          vars.getRequiredNumericParameter("inpExchangeRate", "1"));
      BigDecimal convertedAmount = new BigDecimal(
          vars.getRequiredNumericParameter("inpActualConverted", strPaymentAmount));
      OBError message = null;
      // FIXME: added to access the FIN_PaymentSchedule and FIN_PaymentScheduleDetail tables to be
      // removed when new security implementation is done
      OBContext.setAdminMode();
      try {

        List<FIN_PaymentScheduleDetail> selectedPaymentDetails = FIN_Utility
            .getOBObjectList(FIN_PaymentScheduleDetail.class, strSelectedScheduledPaymentDetailIds);
        HashMap<String, BigDecimal> selectedPaymentDetailAmounts = FIN_AddPayment
            .getSelectedPaymentDetailsAndAmount(vars, selectedPaymentDetails);

        // When creating a payment for orders/invoices of different business partners (factoring)
        // the business partner must be empty
        BusinessPartner paymentBusinessPartner = null;
        if (selectedPaymentDetails.size() == 0 && !"".equals(strReceivedFromId)) {
          paymentBusinessPartner = OBDal.getInstance()
              .get(BusinessPartner.class, strReceivedFromId);
        } else {
          paymentBusinessPartner = getMultiBPartner(selectedPaymentDetails);
        }

        final List<Object> parameters = new ArrayList<Object>();
        parameters.add(vars.getClient());
        parameters.add(dao.getObject(FIN_FinancialAccount.class, strFinancialAccountId)
            .getOrganization()
            .getId());
        parameters.add((isReceipt ? "ARR" : "APP"));
        // parameters.add(null);
        String strDocTypeId = (String) CallStoredProcedure.getInstance()
            .call("AD_GET_DOCTYPE", parameters, null);

        if (strPaymentDocumentNo.startsWith("<")) {
          // get DocumentNo
          strPaymentDocumentNo = Utility.getDocumentNo(this, vars, "AddPaymentFromTransaction",
              "FIN_Payment", strDocTypeId, strDocTypeId, false, true);
        }
        final FIN_FinancialAccount finAcc = dao.getObject(FIN_FinancialAccount.class,
            strFinancialAccountId);
        FIN_Payment payment = dao.getNewPayment(isReceipt, finAcc.getOrganization(),
            dao.getObject(DocumentType.class, strDocTypeId), strPaymentDocumentNo,
            paymentBusinessPartner, dao.getObject(FIN_PaymentMethod.class, strPaymentMethodId),
            finAcc, strPaymentAmount, FIN_Utility.getDate(strPaymentDate), strReferenceNo,
            dao.getObject(Currency.class, paymentCurrencyId), exchangeRate, convertedAmount);

        if (addedGLITemsArray != null) {
          for (int i = 0; i < addedGLITemsArray.length(); i++) {
            JSONObject glItem = addedGLITemsArray.getJSONObject(i);
            BigDecimal glItemOutAmt = new BigDecimal(glItem.getString("glitemPaidOutAmt"));
            BigDecimal glItemInAmt = new BigDecimal(glItem.getString("glitemReceivedInAmt"));
            BigDecimal glItemAmt = BigDecimal.ZERO;
            if (isReceipt) {
              glItemAmt = glItemInAmt.subtract(glItemOutAmt);
            } else {
              glItemAmt = glItemOutAmt.subtract(glItemInAmt);
            }
            final String strGLItemId = glItem.getString("glitemId");
            checkID(strGLItemId);

            // Accounting Dimensions
            final String strElement_BP = glItem.getString("cBpartnerDim");
            checkID(strElement_BP);
            final BusinessPartner businessPartner = dao.getObject(BusinessPartner.class,
                strElement_BP);

            final String strElement_PR = glItem.getString("mProductDim");
            checkID(strElement_PR);
            final Product product = dao.getObject(Product.class, strElement_PR);

            final String strElement_PJ = glItem.getString("cProjectDim");
            checkID(strElement_PJ);
            final Project project = dao.getObject(Project.class, strElement_PJ);

            final String strElement_AY = glItem.getString("cActivityDim");
            checkID(strElement_AY);
            final ABCActivity activity = dao.getObject(ABCActivity.class, strElement_AY);

            final String strElement_CC = glItem.getString("cCostcenterDim");
            checkID(strElement_CC);
            final Costcenter costCenter = dao.getObject(Costcenter.class, strElement_CC);

            final String strElement_MC = glItem.getString("cCampaignDim");
            checkID(strElement_MC);
            final Campaign campaign = dao.getObject(Campaign.class, strElement_MC);

            final String strElement_U1 = glItem.getString("user1Dim");
            checkID(strElement_U1);
            final UserDimension1 user1 = dao.getObject(UserDimension1.class, strElement_U1);

            final String strElement_U2 = glItem.getString("user2Dim");
            checkID(strElement_U2);
            final UserDimension2 user2 = dao.getObject(UserDimension2.class, strElement_U2);

            FIN_AddPayment.saveGLItem(payment, glItemAmt, dao.getObject(GLItem.class, strGLItemId),
                businessPartner, product, project, campaign, activity, null, costCenter, user1,
                user2);
          }
        }
        payment = FIN_AddPayment.savePayment(payment, isReceipt, null, null, null, null, null, null,
            null, null, null, selectedPaymentDetails, selectedPaymentDetailAmounts,
            strDifferenceAction.equals("writeoff"), strDifferenceAction.equals("refund"),
            dao.getObject(Currency.class, paymentCurrencyId), exchangeRate, convertedAmount);

        if (strAction.equals("PRP") || strAction.equals("PPP") || strAction.equals("PRD")
            || strAction.equals("PPW")) {
          message = FIN_AddPayment.processPayment(vars, this,
              (strAction.equals("PRP") || strAction.equals("PPP")) ? "P" : "D", payment);
          if (message != null && "Error".equals(message.getType())) {
            throw new OBException();
          }
          // PPW: process made payment and withdrawal
          // PRD: process made payment and deposit
          if ((strAction.equals("PRD") || strAction.equals("PPW"))
              && !"Error".equals(message.getType())) {
            vars.setSessionValue("AddPaymentFromTransaction|closeAutomatically", "Y");
            vars.setSessionValue("AddPaymentFromTransaction|PaymentId", payment.getId());
          }
          if (strDifferenceAction.equals("refund")) {
            Boolean newPayment = !payment.getFINPaymentDetailList().isEmpty();
            FIN_Payment refundPayment = FIN_AddPayment.createRefundPayment(this, vars, payment,
                refundAmount.negate(), exchangeRate);
            OBError auxMessage = FIN_AddPayment.processPayment(vars, this,
                (strAction.equals("PRP") || strAction.equals("PPP")) ? "P" : "D", refundPayment);
            if (newPayment) {
              final String strNewRefundPaymentMessage = Utility.parseTranslation(this, vars,
                  vars.getLanguage(), "@APRM_RefundPayment@" + ": " + refundPayment.getDocumentNo())
                  + ".";
              message.setMessage(strNewRefundPaymentMessage + " " + message.getMessage());
              if (payment.getGeneratedCredit().compareTo(BigDecimal.ZERO) != 0) {
                payment
                    .setDescription(payment.getDescription() + strNewRefundPaymentMessage + "\n");
                OBDal.getInstance().save(payment);
                OBDal.getInstance().flush();
              }
            } else {
              message = auxMessage;
            }
          }
        }

      } catch (Exception ex) {
        String strMessage = FIN_Utility.getExceptionMessage(ex);
        if (message != null && "Error".equals(message.getType())) {
          strMessage = message.getMessage();
        }
        bdErrorGeneralPopUp(request, response, "Error", strMessage);
        OBDal.getInstance().rollbackAndClose();
        return;
      } finally {
        OBContext.restorePreviousMode();
      }

      log4j.debug("Output: PopUp Response");
      final XmlDocument xmlDocument = xmlEngine
          .readXmlTemplate("org/openbravo/base/secureApp/PopUp_Close_Refresh")
          .createXmlDocument();
      xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
      response.setContentType("text/html; charset=UTF-8");
      final PrintWriter out = response.getWriter();
      out.println(xmlDocument.print());
      out.close();
    }

  }

  private void printPage(HttpServletResponse response, VariablesSecureApp vars,
      String strFinancialAccountId, boolean isReceipt, String strFinBankStatementLineId,
      String strTransactionDate, String strCurrencyId, int conversionRatePrecision,
      String strWindowId) throws IOException, ServletException {
    log4j.debug("Output: Add Payment button pressed on Add Transaction popup.");
    dao = new AdvPaymentMngtDao();
    String defaultPaymentMethod = "";

    final FIN_FinancialAccount financialAccount = dao.getObject(FIN_FinancialAccount.class,
        strFinancialAccountId);

    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/advpaymentmngt/ad_actionbutton/AddPaymentFromTransaction")
        .createXmlDocument();

    if (!strFinBankStatementLineId.isEmpty()) {
      FIN_BankStatementLine bsline = dao.getObject(FIN_BankStatementLine.class,
          strFinBankStatementLineId);
      String actualPayment = (isReceipt)
          ? bsline.getCramount().subtract(bsline.getDramount()).toString()
          : bsline.getDramount().subtract(bsline.getCramount()).toString();
      xmlDocument.setParameter("actualPayment", actualPayment);
      xmlDocument.setParameter("origActualPayment", actualPayment);
      if (bsline.getBusinessPartner() == null) {
        OBCriteria<BusinessPartner> obcBP = OBDal.getInstance()
            .createCriteria(BusinessPartner.class);
        obcBP.add(Restrictions.eq(BusinessPartner.PROPERTY_NAME, bsline.getBpartnername()));
        obcBP.setMaxResults(1);
        if (obcBP.list() != null && obcBP.list().size() > 0) {
          xmlDocument.setParameter("businessPartner", obcBP.list().get(0).getId());
          xmlDocument.setParameter("businessPartnerName", obcBP.list().get(0).getName());
          defaultPaymentMethod = (obcBP.list().get(0).getPaymentMethod() != null)
              ? obcBP.list().get(0).getPaymentMethod().getId()
              : "";
        }
      } else {
        xmlDocument.setParameter("businessPartner", bsline.getBusinessPartner().getId());
        xmlDocument.setParameter("businessPartnerName", bsline.getBusinessPartner().getName());
      }
    }
    // Take payment date from the add transaction popup
    xmlDocument.setParameter("paymentDate",
        strTransactionDate.isEmpty() ? DateTimeData.today(this) : strTransactionDate);

    if (isReceipt) {
      xmlDocument.setParameter("title",
          Utility.messageBD(this, "APRM_AddPaymentIn", vars.getLanguage()));
    } else {
      xmlDocument.setParameter("title",
          Utility.messageBD(this, "APRM_AddPaymentOut", vars.getLanguage()));
    }
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("theme", vars.getTheme());
    xmlDocument.setParameter("isReceipt", (isReceipt) ? "Y" : "N");
    xmlDocument.setParameter("isSoTrx", (isReceipt) ? "Y" : "N");
    xmlDocument.setParameter("finBankStatementLineId", strFinBankStatementLineId);
    xmlDocument.setParameter("orgId", financialAccount.getOrganization().getId());
    xmlDocument.setParameter("inheritedActualPayment",
        strFinBankStatementLineId.isEmpty() ? "N" : "Y");
    xmlDocument.setParameter("displayDoubtfulDebt",
        displayDoubtfulDebtAmount(isReceipt) ? "true" : "false");

    // get DocumentNo
    final List<Object> parameters = new ArrayList<Object>();
    parameters.add(vars.getClient());
    parameters.add(financialAccount.getOrganization().getId());
    parameters.add((isReceipt ? "ARR" : "APP"));
    // parameters.add(null);
    String strDocTypeId = (String) CallStoredProcedure.getInstance()
        .call("AD_GET_DOCTYPE", parameters, null);
    String strDocNo = Utility.getDocumentNo(this, vars, "AddPaymentFromTransaction", "FIN_Payment",
        strDocTypeId, strDocTypeId, false, false);
    xmlDocument.setParameter("documentNumber", "<" + strDocNo + ">");
    xmlDocument.setParameter("documentType",
        dao.getObject(DocumentType.class, strDocTypeId).getName());

    Currency paymentCurrency;
    if (strCurrencyId == null || strCurrencyId.isEmpty()) {
      paymentCurrency = financialAccount.getCurrency();
    } else {
      paymentCurrency = dao.getObject(Currency.class, strCurrencyId);
    }

    xmlDocument.setParameter("currencyId", paymentCurrency.getId());
    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLEDIR", "C_Currency_ID",
          "", "", Utility.getContext(this, vars, "#AccessibleOrgTree", "AddPaymentFromTransaction"),
          Utility.getContext(this, vars, "#User_Client", "AddPaymentFromTransaction"), 0);
      Utility.fillSQLParameters(this, vars, null, comboTableData, "AddPaymentFromTransaction",
          strCurrencyId);
      xmlDocument.setData("reportCurrencyId", "liststructure", comboTableData.select(false));
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    xmlDocument.setParameter("financialAccountId", strFinancialAccountId);
    xmlDocument.setParameter("financialAccount", financialAccount.getIdentifier());

    final Currency financialAccountCurrency = dao
        .getFinancialAccountCurrency(strFinancialAccountId);
    if (financialAccountCurrency != null) {
      xmlDocument.setParameter("financialAccountCurrencyId", financialAccountCurrency.getId());
      try {
        OBContext.setAdminMode(true);
        xmlDocument.setParameter("financialAccountCurrencyPrecision",
            financialAccountCurrency.getStandardPrecision().toString());
      } finally {
        OBContext.restorePreviousMode();
      }
    }

    BigDecimal exchangeRate = BigDecimal.ONE;
    if (financialAccountCurrency != null && !financialAccountCurrency.equals(paymentCurrency)) {
      exchangeRate = findExchangeRate(paymentCurrency, financialAccountCurrency, new Date(),
          financialAccount.getOrganization(), conversionRatePrecision);
    }

    xmlDocument.setParameter("exchangeRate",
        exchangeRate == null ? "" : exchangeRate.toPlainString());

    // Payment Method combobox
    String paymentMethodComboHtml = FIN_Utility.getPaymentMethodList(defaultPaymentMethod,
        strFinancialAccountId, financialAccount.getOrganization().getId(), true, true, isReceipt);
    xmlDocument.setParameter("sectionDetailPaymentMethod", paymentMethodComboHtml);

    final List<FinAccPaymentMethod> paymentMethods = financialAccount
        .getFinancialMgmtFinAccPaymentMethodList();
    JSONObject json = new JSONObject();
    try {
      for (FinAccPaymentMethod method : paymentMethods) {
        if (isReceipt) {
          json.put(method.getPaymentMethod().getId(), method.isPayinIsMulticurrency());
        } else {
          json.put(method.getPaymentMethod().getId(), method.isPayoutIsMulticurrency());
        }
      }
    } catch (JSONException e) {
      log4j.error("JSON object error" + json.toString());
    }
    StringBuilder sb = new StringBuilder();
    sb.append("<script language='JavaScript' type='text/javascript'>");
    sb.append("var paymentMethodMulticurrency = ");
    sb.append(json.toString());
    sb.append(";");
    sb.append("</script>");
    xmlDocument.setParameter("sectionDetailPaymentMethodMulticurrency", sb.toString());

    xmlDocument.setParameter("dateDisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));

    // Action Regarding Document
    xmlDocument.setParameter("ActionDocument", (isReceipt ? "PRD" : "PPW"));
    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "LIST", "",
          (isReceipt ? "F903F726B41A49D3860243101CEEBA25" : "F15C13A199A748F1B0B00E985A64C036"), "",
          Utility.getContext(this, vars, "#AccessibleOrgTree", "AddPaymentFromTransaction"),
          Utility.getContext(this, vars, "#User_Client", "AddPaymentFromTransaction"), 0);
      Utility.fillSQLParameters(this, vars, null, comboTableData, "AddPaymentFromTransaction", "");

      xmlDocument.setData("reportActionDocument", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }

    // Accounting Dimensions
    String doctype;
    if (isReceipt) {
      doctype = AcctServer.DOCTYPE_ARReceipt;
    } else {
      doctype = AcctServer.DOCTYPE_APPayment;
    }
    final String strCentrally = Utility.getContext(this, vars,
        DimensionDisplayUtility.IsAcctDimCentrally, strWindowId);
    final String strElement_BP = Utility.getContext(this, vars,
        DimensionDisplayUtility.displayAcctDimensions(strCentrally,
            DimensionDisplayUtility.DIM_BPartner, doctype, DimensionDisplayUtility.DIM_Header),
        strWindowId);
    final String strElement_PR = Utility.getContext(this, vars,
        DimensionDisplayUtility.displayAcctDimensions(strCentrally,
            DimensionDisplayUtility.DIM_Product, doctype, DimensionDisplayUtility.DIM_Header),
        strWindowId);
    final String strElement_PJ = Utility.getContext(this, vars,
        DimensionDisplayUtility.displayAcctDimensions(strCentrally,
            DimensionDisplayUtility.DIM_Project, doctype, DimensionDisplayUtility.DIM_Header),
        strWindowId);
    final String strElement_AY = Utility.getContext(this, vars, "$Element_AY", strWindowId);
    final String strElement_CC = Utility.getContext(this, vars,
        DimensionDisplayUtility.displayAcctDimensions(strCentrally,
            DimensionDisplayUtility.DIM_CostCenter, doctype, DimensionDisplayUtility.DIM_Header),
        strWindowId);
    final String strElement_MC = Utility.getContext(this, vars, "$Element_MC", strWindowId);
    final String strElement_U1 = Utility.getContext(this, vars,
        DimensionDisplayUtility.displayAcctDimensions(strCentrally,
            DimensionDisplayUtility.DIM_User1, doctype, DimensionDisplayUtility.DIM_Header),
        strWindowId);
    final String strElement_U2 = Utility.getContext(this, vars,
        DimensionDisplayUtility.displayAcctDimensions(strCentrally,
            DimensionDisplayUtility.DIM_User2, doctype, DimensionDisplayUtility.DIM_Header),
        strWindowId);
    xmlDocument.setParameter("strElement_BP", strElement_BP);
    xmlDocument.setParameter("strElement_PR", strElement_PR);
    xmlDocument.setParameter("strElement_PJ", strElement_PJ);
    xmlDocument.setParameter("strElement_AY", strElement_AY);
    xmlDocument.setParameter("strElement_CC", strElement_CC);
    xmlDocument.setParameter("strElement_MC", strElement_MC);
    xmlDocument.setParameter("strElement_U1", strElement_U1);
    xmlDocument.setParameter("strElement_U2", strElement_U2);

    // Not allow to change exchange rate and amount
    final String strNotAllowExchange = Utility.getContext(this, vars, "NotAllowChangeExchange",
        strWindowId);
    xmlDocument.setParameter("strNotAllowExchange", strNotAllowExchange);

    if (financialAccount.getWriteofflimit() != null) {
      final String strtypewriteoff;
      final String strAmountwriteoff;

      strtypewriteoff = financialAccount.getTypewriteoff();
      strAmountwriteoff = financialAccount.getWriteofflimit().toString();
      xmlDocument.setParameter("strtypewriteoff", strtypewriteoff);
      xmlDocument.setParameter("strAmountwriteoff", strAmountwriteoff);

      final String strWriteOffLimit = Utility.getContext(this, vars, "WriteOffLimitPreference",
          strWindowId);
      xmlDocument.setParameter("strWriteOffLimit", strWriteOffLimit);
    }

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private void printGrid(HttpServletResponse response, HttpServletRequest request,
      VariablesSecureApp vars, String strFinancialAccountId, String strBusinessPartnerId,
      String strExpectedDateFrom, String strExpectedDateTo, String strTransDateFrom,
      String strTransDateTo, String strDocumentType, String strDocumentNo,
      String strSelectedPaymentDetails, boolean isReceipt, String strCurrencyId,
      String strAmountFrom, String strAmountTo) throws IOException, ServletException {

    log4j.debug("Output: Grid with pending payments");
    String message = "";
    dao = new AdvPaymentMngtDao();
    FIN_FinancialAccount financialAccount = dao.getObject(FIN_FinancialAccount.class,
        strFinancialAccountId);

    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate(
            "org/openbravo/advpaymentmngt/ad_actionbutton/AddPaymentFromTransactionGrid")
        .createXmlDocument();

    // Pending Payments from invoice
    final List<FIN_PaymentScheduleDetail> invoiceScheduledPaymentDetails = new ArrayList<FIN_PaymentScheduleDetail>();
    // selected scheduled payments list
    final List<FIN_PaymentScheduleDetail> selectedScheduledPaymentDetails = FIN_AddPayment
        .getSelectedPaymentDetails(invoiceScheduledPaymentDetails, strSelectedPaymentDetails);

    List<FIN_PaymentScheduleDetail> filteredScheduledPaymentDetails = new ArrayList<FIN_PaymentScheduleDetail>();

    // If business partner and document number are empty search for all filtered scheduled payments
    // list
    if (!"".equals(strBusinessPartnerId) || !"".equals(strDocumentNo)
        || isValidJSDate(strExpectedDateFrom) || isValidJSDate(strExpectedDateTo)
        || isValidJSDate(strTransDateFrom) || isValidJSDate(strTransDateTo)
        || !"".equals(strAmountFrom) || !"".equals(strAmountTo)) {
      Currency paymentCurrency;
      if (strCurrencyId == null || strCurrencyId.isEmpty()) {
        paymentCurrency = financialAccount.getCurrency();
      } else {
        paymentCurrency = dao.getObject(Currency.class, strCurrencyId);
      }

      filteredScheduledPaymentDetails = dao.getFilteredScheduledPaymentDetails(
          financialAccount.getOrganization(),
          dao.getObject(BusinessPartner.class, strBusinessPartnerId), paymentCurrency, null, null,
          FIN_Utility.getDate(strExpectedDateFrom),
          FIN_Utility.getDate(DateTimeData.nDaysAfter(this, strExpectedDateTo, "1")),
          FIN_Utility.getDate(strTransDateFrom),
          FIN_Utility.getDate(DateTimeData.nDaysAfter(this, strTransDateTo, "1")), strDocumentType,
          strDocumentNo, null, selectedScheduledPaymentDetails, isReceipt, strAmountFrom,
          strAmountTo);
    }
    final FieldProvider[] data = FIN_AddPayment.getShownScheduledPaymentDetails(vars,
        selectedScheduledPaymentDetails, filteredScheduledPaymentDetails, false, null, null,
        displayDoubtfulDebtAmount(isReceipt));
    String invoiceIdentifier = "";
    if ((data == null || data.length == 0) && !"".equals(strDocumentNo) && strDocumentNo != null) {
      final OBCriteria<Invoice> obc = OBDal.getInstance().createCriteria(Invoice.class);
      if (isReceipt) {
        obc.add(Restrictions.eq(Invoice.PROPERTY_DOCUMENTNO, strDocumentNo));
      } else {
        obc.add(Restrictions.eq(Invoice.PROPERTY_ORDERREFERENCE, strDocumentNo));
      }
      obc.add(Restrictions.eq(Invoice.PROPERTY_PROCESSED, true));
      obc.add(Restrictions.eq(Invoice.PROPERTY_SALESTRANSACTION, isReceipt));
      if (strBusinessPartnerId != null && !"".equals(strBusinessPartnerId)) {
        obc.add(Restrictions.eq(Invoice.PROPERTY_BUSINESSPARTNER, strBusinessPartnerId));
      }
      obc.add(Restrictions.eq(Invoice.PROPERTY_PAYMENTCOMPLETE, true));
      obc.add(Restrictions.in(Invoice.PROPERTY_ORGANIZATION,
          getOrganizationList(new OrganizationStructureProvider()
              .getChildTree(financialAccount.getOrganization().getId(), true))));
      obc.addOrderBy(Invoice.PROPERTY_INVOICEDATE, false);
      Invoice invoice = (Invoice) obc.uniqueResult();
      if (invoice != null) {
        invoiceIdentifier = invoice.getIdentifier();
        message = "APRM_PaidInvoice";
      }
    }
    xmlDocument.setData("structure", (data == null) ? set() : data);
    try {
      JSONObject json = new JSONObject();
      ArrayList<String> params = new ArrayList<String>();
      params.add(invoiceIdentifier);
      json.put("message", message);
      json.put("params", params);
      json.put("grid", xmlDocument.print());
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter out = response.getWriter();
      out.println("objson = " + json);
      out.close();
    } catch (JSONException e) {
      log4j.error("AddPaymentFromTransaction - CallbackGrid", e);
    }
  }

  /**
   * Returns true in case the provided string is well formed JS-formated date
   */
  private boolean isValidJSDate(String strDate) {
    if ("".equals(strDate)) {
      return false;
    }
    try {
      OBContext.setAdminMode(true);
      String dateFormat = OBPropertiesProvider.getInstance()
          .getOpenbravoProperties()
          .getProperty("dateFormat.java");

      Date date = new SimpleDateFormat(dateFormat).parse(strDate);
      Date year1000 = new SimpleDateFormat("yyyy-MM-dd").parse("999-12-31");
      return date.after(year1000);
    } catch (Exception e) {
      return false;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void refreshPaymentMethod(HttpServletResponse response, String strBusinessPartnerId,
      String strFinancialAccountId, boolean isReceipt) throws IOException, ServletException {
    log4j.debug("Callout: Business Partner has changed to" + strBusinessPartnerId);

    String paymentMethodComboHtml = "";
    FIN_FinancialAccount account = OBDal.getInstance()
        .get(FIN_FinancialAccount.class, strFinancialAccountId);
    BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class, strBusinessPartnerId);
    String paymentMethodId = null;
    if (bp != null) {
      if (isReceipt && bp.getPaymentMethod() != null) {
        paymentMethodId = bp.getPaymentMethod().getId();
      } else if (!isReceipt && bp.getPOPaymentMethod() != null) {
        paymentMethodId = bp.getPOPaymentMethod().getId();
      }
    }
    paymentMethodComboHtml = FIN_Utility.getPaymentMethodList(paymentMethodId,
        strFinancialAccountId, account.getOrganization().getId(), true, true, isReceipt);

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(paymentMethodComboHtml.replaceAll("\"", "\\'"));
    out.close();
  }

  /**
   * Returns the business partner (if is the same for all the elements in the list) or null in other
   * case.
   * 
   * @param paymentScheduleDetailList
   *          List of payment schedule details.
   * @return Business Partner if the payment schedule details belong to the same business partner.
   *         Null if the list of payment schedule details are associated to more than one business
   *         partner.
   * 
   */
  private BusinessPartner getMultiBPartner(
      List<FIN_PaymentScheduleDetail> paymentScheduleDetailList) {
    String previousBPId = null;
    String currentBPId = null;
    for (FIN_PaymentScheduleDetail psd : paymentScheduleDetailList) {
      if (psd.getInvoicePaymentSchedule() != null) { // Invoice
        currentBPId = psd.getInvoicePaymentSchedule().getInvoice().getBusinessPartner().getId();
        if (!currentBPId.equals(previousBPId) && previousBPId != null) {
          return null;
        } else {
          previousBPId = currentBPId;
        }
      }
      if (psd.getOrderPaymentSchedule() != null) { // Order
        currentBPId = psd.getOrderPaymentSchedule().getOrder().getBusinessPartner().getId();
        if (!currentBPId.equals(previousBPId) && previousBPId != null) {
          return null;
        } else {
          previousBPId = currentBPId;
        }
      }
    }
    return currentBPId != null ? OBDal.getInstance().get(BusinessPartner.class, currentBPId) : null;
  }

  private void refreshExchangeRate(HttpServletResponse response, String strCurrencyId,
      String strFinancialAccountCurrencyId, String strPaymentDate, Organization organization,
      int conversionRatePrecision) throws IOException, ServletException {

    dao = new AdvPaymentMngtDao();

    final Currency financialAccountCurrency = dao.getObject(Currency.class,
        strFinancialAccountCurrencyId);
    final Currency paymentCurrency = dao.getObject(Currency.class, strCurrencyId);

    BigDecimal exchangeRate = findExchangeRate(paymentCurrency, financialAccountCurrency,
        FIN_Utility.getDate(strPaymentDate), organization, conversionRatePrecision);

    JSONObject msg = new JSONObject();
    try {
      msg.put("exchangeRate", exchangeRate == null ? "" : exchangeRate);
      msg.put("formatOutput", exchangeRateFormat);
    } catch (JSONException e) {
      log4j.error("JSON object error" + msg.toString());
    }
    response.setContentType("application/json; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(msg.toString());
    out.close();
  }

  private BigDecimal findExchangeRate(Currency paymentCurrency, Currency financialAccountCurrency,
      Date paymentDate, Organization organization, int conversionRatePrecision) {
    BigDecimal exchangeRate = BigDecimal.ONE;
    if (financialAccountCurrency != null && !financialAccountCurrency.equals(paymentCurrency)) {
      final ConversionRate conversionRate = FIN_Utility.getConversionRate(paymentCurrency,
          financialAccountCurrency, paymentDate, organization);
      if (conversionRate == null) {
        exchangeRate = null;
      } else {
        exchangeRate = conversionRate.getMultipleRateBy()
            .setScale(conversionRatePrecision, RoundingMode.HALF_UP);
      }
    }
    return exchangeRate;
  }

  private FieldProvider[] set() throws ServletException {
    HashMap<String, String> empty = new HashMap<String, String>();
    empty.put("finScheduledPaymentId", "");
    empty.put("salesOrderNr", "");
    empty.put("salesInvoiceNr", "");
    empty.put("expectedDate", "");
    empty.put("invoicedAmount", "");
    empty.put("expectedAmount", "");
    empty.put("paymentAmount", "");
    ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
    result.add(empty);
    return FieldProviderFactory.getFieldProviderArray(result);
  }

  private void checkID(final String id) throws ServletException {
    if (!IsIDFilter.instance.accept(id)) {
      log4j.error("Input: " + id + " not accepted by filter: IsIDFilter");
      throw new ServletException("Input: " + id + " is not an accepted input");
    }
  }

  private void businessPartnerBlocked(HttpServletResponse response, VariablesSecureApp vars,
      String strBPartnerName) throws IOException, ServletException {

    try {
      JSONObject json = new JSONObject();
      json.put("text", "SelectedBPartnerBlocked");
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter out = response.getWriter();
      out.println("objson = " + json);
      out.close();
    } catch (JSONException e) {
      log4j.error("AddPaymentFromTransaction - Callback", e);
    }
  }

  @Override
  public String getServletInfo() {
    return "Servlet that presents the payment proposal";
    // end of getServletInfo() method
  }

  private List<Organization> getOrganizationList(Set<String> orgIds) {
    List<Organization> result = new ArrayList<Organization>();
    for (String orgId : orgIds) {
      result.add(OBDal.getInstance().get(Organization.class, orgId));
    }
    return result;
  }

  boolean displayDoubtfulDebtAmount(boolean isReceipt) {
    if (!isReceipt) {
      return false;
    }
    OBContext.setAdminMode();
    try {
      OBCriteria<Preference> obCriteria = OBDal.getInstance().createCriteria(Preference.class);
      obCriteria.add(Restrictions.eq(Preference.PROPERTY_ATTRIBUTE, "Doubtful_Debt_Visibility"));
      obCriteria.add(
          Restrictions.eq(Preference.PROPERTY_CLIENT, OBContext.getOBContext().getCurrentClient()));
      obCriteria.add(Restrictions.in(Preference.PROPERTY_ORGANIZATION + ".id",
          (Object[]) OBContext.getOBContext().getReadableOrganizations()));
      Preference preference = (Preference) obCriteria.uniqueResult();
      if (preference != null) {
        return "Y".equals(preference.getSearchKey());
      } else {
        return false;
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
