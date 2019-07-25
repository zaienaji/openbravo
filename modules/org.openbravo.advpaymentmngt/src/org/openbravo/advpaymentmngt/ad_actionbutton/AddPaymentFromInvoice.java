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
 * All portions are Copyright (C) 2010-2014 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s): Enterprise Intelligence Systems (http://www.eintel.com.au).
 *************************************************************************
 */
package org.openbravo.advpaymentmngt.ad_actionbutton;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.advpaymentmngt.dao.AdvPaymentMngtDao;
import org.openbravo.advpaymentmngt.process.FIN_AddPayment;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.filter.IsIDFilter;
import org.openbravo.base.filter.RequestFilter;
import org.openbravo.base.filter.ValueListFilter;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.DateTimeData;
import org.openbravo.erpCommon.utility.FieldProviderFactory;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.ConversionRateDoc;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.financialmgmt.accounting.FIN_FinancialAccountAccounting;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.model.financialmgmt.payment.FinAccPaymentMethod;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.service.db.CallStoredProcedure;
import org.openbravo.xmlEngine.XmlDocument;

public class AddPaymentFromInvoice extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;
  private AdvPaymentMngtDao dao;
  private static final RequestFilter filterYesNo = new ValueListFilter("Y", "N", "");

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    VariablesSecureApp vars = new VariablesSecureApp(request);
    int conversionRatePrecision = FIN_Utility.getConversionRatePrecision(vars);

    if (vars.commandIn("DEFAULT")) {
      String strBPfromInvoiceId = vars.getRequestGlobalVariable("inpcBpartnerId", "");
      BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class, strBPfromInvoiceId);
      String strBPfromInvoice = bp == null ? "" : bp.getIdentifier();

      String strCurrencyId = vars.getRequestGlobalVariable("inpcCurrencyId", "");
      String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      String strWindowId = vars.getGlobalVariable("inpwindowId", "");
      String strTabId = vars.getGlobalVariable("inpTabId", "");
      String strInvoiceId = vars.getGlobalVariable("inpcInvoiceId", "");
      boolean isReceipt = vars.getGlobalVariable("inpissotrx", "Y").equals("Y");

      printPage(response, vars, strBPfromInvoice, strBPfromInvoiceId, strCurrencyId, strInvoiceId,
          strOrgId, strWindowId, strTabId, isReceipt, conversionRatePrecision);

    } else if (vars.commandIn("GRIDLIST")) {
      String strBPfromInvoiceId = vars.getRequestGlobalVariable("inpBusinessPartnerId", "");
      String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      String strInvoiceId = vars.getRequestGlobalVariable("inpcInvoiceId", "");
      String strExpectedDateFrom = vars.getStringParameter("inpExpectedDateFrom", "");
      String strExpectedDateTo = vars.getStringParameter("inpExpectedDateTo", "");
      String strDocumentType = vars.getStringParameter("inpDocumentType", "");
      String strSelectedPaymentDetails = vars.getInStringParameter("inpScheduledPaymentDetailId",
          IsIDFilter.instance);
      Boolean showAlternativePM = "Y"
          .equals(vars.getStringParameter("inpAlternativePaymentMethod", filterYesNo));
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");

      printGrid(response, vars, strBPfromInvoiceId, strCurrencyId, strInvoiceId, strOrgId,
          strExpectedDateFrom, strExpectedDateTo, strDocumentType, strSelectedPaymentDetails,
          isReceipt, showAlternativePM);

    } else if (vars.commandIn("PAYMENTMETHOD")) {
      String strFinancialAccountId = vars.getRequestGlobalVariable("inpFinancialAccount", "");
      String strPaymentMethodId = vars.getRequestGlobalVariable("inpPaymentMethod", "");
      String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      refreshPaymentMethodCombo(response, strPaymentMethodId, strFinancialAccountId, strOrgId,
          isReceipt);

    } else if (vars.commandIn("FINANCIALACCOUNT")) {
      String strFinancialAccountId = vars.getRequestGlobalVariable("inpFinancialAccount", "");
      String strPaymentMethodId = vars.getRequiredStringParameter("inpPaymentMethod");
      String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      String strPaymentDate = vars.getRequestGlobalVariable("inpPaymentDate", "");
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      String strInvoiceId = vars.getRequestGlobalVariable("inpcInvoiceId", "");
      refreshFinancialAccountCombo(response, vars, strPaymentMethodId, strFinancialAccountId,
          strOrgId, strCurrencyId, isReceipt, strPaymentDate, conversionRatePrecision,
          strInvoiceId);
    } else if (vars.commandIn("FILLFINANCIALACCOUNT")) {
      String strFinancialAccountId = vars.getRequestGlobalVariable("inpFinancialAccount", "");
      String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      String strPaymentDate = vars.getRequestGlobalVariable("inpPaymentDate", "");
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      String strInvoiceId = vars.getRequestGlobalVariable("inpcInvoiceId", "");
      refreshFinancialAccountCombo(response, vars, "", strFinancialAccountId, strOrgId,
          strCurrencyId, isReceipt, strPaymentDate, conversionRatePrecision, strInvoiceId);

    } else if (vars.commandIn("FILLPAYMENTMETHOD")) {
      String strPaymentMethodId = vars.getRequiredStringParameter("inpPaymentMethod");
      String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      refreshPaymentMethodCombo(response, strPaymentMethodId, "", strOrgId, isReceipt);

    } else if (vars.commandIn("EXCHANGERATE")) {
      final String strCurrencyId = vars.getRequestGlobalVariable("inpCurrencyId", "");
      final String strFinancialAccountCurrencyId = vars
          .getRequestGlobalVariable("inpFinancialAccountCurrencyId", "");
      final String strOrgId = vars.getRequestGlobalVariable("inpadOrgId", "");
      final String strPaymentDate = vars.getRequestGlobalVariable("inpPaymentDate", "");
      Organization org = OBDal.getInstance().get(Organization.class, strOrgId);
      String strInvoiceId = vars.getGlobalVariable("inpcInvoiceId", "");
      refreshExchangeRate(response, vars, strCurrencyId, strFinancialAccountCurrencyId,
          strPaymentDate, org, conversionRatePrecision, strInvoiceId);

    } else if (vars.commandIn("BPARTNERBLOCK")) {
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      String strReceivedFromId = vars.getRequiredStringParameter("inpBusinessPartnerId");
      BusinessPartner businessPartner = OBDal.getInstance()
          .get(BusinessPartner.class, strReceivedFromId);
      if (FIN_Utility.isBlockedBusinessPartner(businessPartner.getId(), isReceipt, 4)) {
        businessPartnerBlocked(response, vars, businessPartner.getIdentifier());
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
      String strReceivedFromId = vars.getRequiredStringParameter("inpBusinessPartnerId");
      String strPaymentMethodId = vars.getRequiredStringParameter("inpPaymentMethod");
      String strFinancialAccountId = vars.getRequiredStringParameter("inpFinancialAccount");
      String strPaymentAmount = vars.getRequiredNumericParameter("inpActualPayment");
      String strPaymentDate = vars.getRequiredStringParameter("inpPaymentDate");
      String strSelectedScheduledPaymentDetailIds = vars
          .getRequiredInParameter("inpScheduledPaymentDetailId", IsIDFilter.instance);
      String strOrgId = vars.getRequiredStringParameter("inpadOrgId");
      String strDifferenceAction = "";
      String strDifference = vars.getNumericParameter("inpDifference", "0");
      boolean isUseCredit = vars.getStringParameter("inpUseCredit").equals("Y");
      BigDecimal refundAmount = BigDecimal.ZERO;
      if (!strDifference.equals("0")) {
        refundAmount = new BigDecimal(strDifference);
        strDifferenceAction = vars.getStringParameter("inpDifferenceAction", "");
      }
      String strInvoiceId = vars.getRequestGlobalVariable("inpcInvoiceId", "");
      String strTabId = vars.getRequiredStringParameter("inpTabId");
      String strReferenceNo = vars.getStringParameter("inpReferenceNo", "");
      String paymentCurrencyId = vars.getRequiredStringParameter("inpCurrencyId");
      BigDecimal exchangeRate = new BigDecimal(
          vars.getRequiredNumericParameter("inpExchangeRate", "1"));
      BigDecimal convertedAmount = new BigDecimal(
          vars.getRequiredNumericParameter("inpActualConverted", strPaymentAmount));
      BusinessPartner businessPartner = OBDal.getInstance()
          .get(BusinessPartner.class, strReceivedFromId);
      PriceList priceList = isReceipt ? businessPartner.getPriceList()
          : businessPartner.getPurchasePricelist();
      FIN_FinancialAccount finAccount = OBDal.getInstance()
          .get(FIN_FinancialAccount.class, strFinancialAccountId);
      FIN_PaymentMethod finPaymentMethod = OBDal.getInstance()
          .get(FIN_PaymentMethod.class, strPaymentMethodId);
      boolean paymentDocumentEnabled = getDocumentConfirmation(this, finAccount, finPaymentMethod,
          isReceipt, strPaymentAmount, true);
      OBError message = null;
      // FIXME: added to access the FIN_PaymentSchedule and FIN_PaymentScheduleDetail tables to be
      // removed when new security implementation is done
      OBContext.setAdminMode();
      try {
        if (isUseCredit && !paymentCurrencyId
            .equals((priceList != null) ? priceList.getCurrency().getId() : "")) {
          String errorMsg = String.format(
              Utility.parseTranslation(this, vars, vars.getLanguage(), "@APRM_CreditCurrency@"),
              priceList != null ? priceList.getCurrency().getISOCode()
                  : Utility.parseTranslation(this, vars, vars.getLanguage(),
                      "@APRM_CreditNoPricelistCurrency@"));
          message = new OBError();
          message.setType("Error");
          message.setTitle("Error");
          message.setMessage(errorMsg);
        } else {

          List<FIN_PaymentScheduleDetail> selectedPaymentDetails = FIN_Utility.getOBObjectList(
              FIN_PaymentScheduleDetail.class, strSelectedScheduledPaymentDetailIds);
          HashMap<String, BigDecimal> selectedPaymentDetailAmounts = FIN_AddPayment
              .getSelectedPaymentDetailsAndAmount(vars, selectedPaymentDetails);

          final List<Object> parameters = new ArrayList<Object>();
          parameters.add(vars.getClient());
          parameters.add(strOrgId);
          parameters.add((isReceipt ? "ARR" : "APP"));
          // parameters.add(null);
          String strDocTypeId = (String) CallStoredProcedure.getInstance()
              .call("AD_GET_DOCTYPE", parameters, null);
          boolean documentEnabled = true;
          String strDocBaseType = parameters.get(2).toString();
          boolean orgLegalWithAccounting = FIN_Utility.periodControlOpened(Invoice.TABLE_NAME,
              strInvoiceId, Invoice.TABLE_NAME + "_ID", "LE");

          if ((strAction.equals("PRD") || strAction.equals("PPW")
              || FIN_Utility.isAutomaticDepositWithdrawn(finAccount, finPaymentMethod, isReceipt))
              && new BigDecimal(strPaymentAmount).compareTo(BigDecimal.ZERO) != 0) {
            documentEnabled = paymentDocumentEnabled || getDocumentConfirmation(this, finAccount,
                finPaymentMethod, isReceipt, strPaymentAmount, false);
          } else {
            documentEnabled = paymentDocumentEnabled;
          }

          if (documentEnabled && !FIN_Utility.isPeriodOpen(vars.getClient(), strDocBaseType,
              strOrgId, strPaymentDate) && orgLegalWithAccounting) {
            final OBError myMessage = Utility.translateError(this, vars, vars.getLanguage(),
                Utility.messageBD(this, "PeriodNotAvailable", vars.getLanguage()));
            vars.setMessage(strTabId, myMessage);
            printPageClosePopUp(response, vars);
            return;
          }

          if (strPaymentDocumentNo.startsWith("<")) {
            // get DocumentNo
            strPaymentDocumentNo = Utility.getDocumentNo(this, vars, "AddPaymentFromInvoice",
                "FIN_Payment", strDocTypeId, strDocTypeId, false, true);
          }

          FIN_Payment payment = FIN_AddPayment.savePayment(null, isReceipt,
              dao.getObject(DocumentType.class, strDocTypeId), strPaymentDocumentNo,
              dao.getObject(BusinessPartner.class, strReceivedFromId),
              dao.getObject(FIN_PaymentMethod.class, strPaymentMethodId),
              dao.getObject(FIN_FinancialAccount.class, strFinancialAccountId), strPaymentAmount,
              FIN_Utility.getDate(strPaymentDate), dao.getObject(Organization.class, strOrgId),
              strReferenceNo, selectedPaymentDetails, selectedPaymentDetailAmounts,
              strDifferenceAction.equals("writeoff"), strDifferenceAction.equals("refund"),
              dao.getObject(Currency.class, paymentCurrencyId), exchangeRate, convertedAmount);

          if (strAction.equals("PRP") || strAction.equals("PPP") || strAction.equals("PRD")
              || strAction.equals("PPW")) {

            message = FIN_AddPayment.processPayment(vars, this,
                (strAction.equals("PRP") || strAction.equals("PPP")) ? "P" : "D", payment);
            String strNewPaymentMessage = Utility.parseTranslation(this, vars, vars.getLanguage(),
                "@PaymentCreated@" + " " + payment.getDocumentNo()) + ".";
            if (!"Error".equalsIgnoreCase(message.getType())) {
              message.setMessage(strNewPaymentMessage + " " + message.getMessage());
            }
            if (strDifferenceAction.equals("refund")) {
              Boolean newPayment = !payment.getFINPaymentDetailList().isEmpty();
              FIN_Payment refundPayment = FIN_AddPayment.createRefundPayment(this, vars, payment,
                  refundAmount.negate(), exchangeRate);
              OBError auxMessage = FIN_AddPayment.processPayment(vars, this,
                  (strAction.equals("PRP") || strAction.equals("PPP")) ? "P" : "D", refundPayment);
              if (newPayment && !"Error".equalsIgnoreCase(auxMessage.getType())) {
                final String strNewRefundPaymentMessage = Utility.parseTranslation(this, vars,
                    vars.getLanguage(),
                    "@APRM_RefundPayment@" + ": " + refundPayment.getDocumentNo()) + ".";
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
        }
      } catch (Exception ex) {
        OBDal.getInstance().rollbackAndClose();
        message = Utility.translateError(this, vars, vars.getLanguage(), ex.getMessage());
        log4j.error(ex);
        bdErrorGeneralPopUp(request, response, "Error", message.getMessage());
        return;
      } finally {
        OBContext.restorePreviousMode();
      }

      String strWindowPath = Utility.getTabURL(strTabId, "R", true);
      if (strWindowPath.equals("")) {
        strWindowPath = strDefaultServlet;
      }

      vars.setMessage(strTabId, message);
      printPageClosePopUp(response, vars, strWindowPath);
    } else if (vars.commandIn("PROPERPROCESSOPTIONS")) {
      String strPaymentMethodId = vars.getRequiredStringParameter("inpPaymentMethod");
      String strFinancialAccountId = vars.getRequiredStringParameter("inpFinancialAccount");
      String strOrgId = vars.getRequiredStringParameter("inpadOrgId");
      boolean isReceipt = vars.getRequiredStringParameter("isReceipt").equals("Y");
      refreshProcessOptions(response, vars, strPaymentMethodId, strFinancialAccountId, strOrgId,
          isReceipt);
    }

  }

  public boolean getDocumentConfirmation(ConnectionProvider conn, FIN_FinancialAccount finAccount,
      FIN_PaymentMethod finPaymentMethod, boolean isReceipt, String strPaymentAmount,
      boolean isPayment) {
    // Checks if this step is configured to generate accounting for the selected financial account
    boolean confirmation = false;
    OBContext.setAdminMode();
    try {
      OBCriteria<FinAccPaymentMethod> obCriteria = OBDal.getInstance()
          .createCriteria(FinAccPaymentMethod.class);
      obCriteria.add(Restrictions.eq(FinAccPaymentMethod.PROPERTY_ACCOUNT, finAccount));
      obCriteria.add(Restrictions.eq(FinAccPaymentMethod.PROPERTY_PAYMENTMETHOD, finPaymentMethod));
      obCriteria.setFilterOnReadableClients(false);
      obCriteria.setFilterOnReadableOrganization(false);
      List<FinAccPaymentMethod> lines = obCriteria.list();
      List<FIN_FinancialAccountAccounting> accounts = finAccount.getFINFinancialAccountAcctList();
      String uponUse = "";
      if (isPayment) {
        if (isReceipt) {
          uponUse = lines.get(0).getUponReceiptUse();
        } else {
          uponUse = lines.get(0).getUponPaymentUse();
        }
      } else {
        if (isReceipt) {
          uponUse = lines.get(0).getUponDepositUse();
        } else {
          uponUse = lines.get(0).getUponWithdrawalUse();
        }
      }
      for (FIN_FinancialAccountAccounting account : accounts) {
        if (confirmation) {
          return confirmation;
        }
        if (isReceipt) {
          if (("INT").equals(uponUse) && account.getInTransitPaymentAccountIN() != null) {
            confirmation = true;
          } else if (("DEP").equals(uponUse) && account.getDepositAccount() != null) {
            confirmation = true;
          } else if (("CLE").equals(uponUse) && account.getClearedPaymentAccount() != null) {
            confirmation = true;
          }
        } else {
          if (("INT").equals(uponUse) && account.getFINOutIntransitAcct() != null) {
            confirmation = true;
          } else if (("WIT").equals(uponUse) && account.getWithdrawalAccount() != null) {
            confirmation = true;
          } else if (("CLE").equals(uponUse) && account.getClearedPaymentAccountOUT() != null) {
            confirmation = true;
          }
        }
        // For payments with Amount ZERO always create an entry as no transaction will be created
        if (isPayment) {
          BigDecimal amount = new BigDecimal(strPaymentAmount);
          if (amount.compareTo(BigDecimal.ZERO) == 0) {
            confirmation = true;
          }
        }
      }
    } catch (Exception e) {
      return confirmation;
    } finally {
      OBContext.restorePreviousMode();
    }
    return confirmation;
  }

  private void printPage(HttpServletResponse response, VariablesSecureApp vars,
      String strBPfromInvoice, String strBPfromInvoiceId, String strCurrencyId, String strInvoiceId,
      String strOrgId, String strWindowId, String strTabId, boolean isReceipt,
      int conversionRatePrecision) throws IOException, ServletException {

    log4j.debug("Output: Add Payment button pressed on Sales Invoice window");
    dao = new AdvPaymentMngtDao();
    BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class, strBPfromInvoiceId);

    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/advpaymentmngt/ad_actionbutton/AddPaymentFromInvoice")
        .createXmlDocument();

    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("theme", vars.getTheme());

    if (isReceipt) {
      xmlDocument.setParameter("title",
          Utility.messageBD(this, "APRM_AddPaymentIn", vars.getLanguage()));
    } else {
      xmlDocument.setParameter("title",
          Utility.messageBD(this, "APRM_AddPaymentOut", vars.getLanguage()));
    }
    xmlDocument.setParameter("dateDisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("paymentDate", DateTimeData.today(this));
    xmlDocument.setParameter("businessPartner", strBPfromInvoice);
    xmlDocument.setParameter("businessPartnerId", strBPfromInvoiceId);
    xmlDocument.setParameter("windowId", strWindowId);
    xmlDocument.setParameter("tabId", strTabId);
    xmlDocument.setParameter("orgId", strOrgId);
    xmlDocument.setParameter("invoiceId", strInvoiceId);
    xmlDocument.setParameter("isReceipt", (isReceipt ? "Y" : "N"));

    try {
      OBContext.setAdminMode(true);
      xmlDocument.setParameter("credit", dao
          .getCustomerCredit(bp, isReceipt, OBDal.getInstance().get(Organization.class, strOrgId))
          .toString());
    } finally {
      OBContext.restorePreviousMode();
    }

    // get DocumentNo
    final List<Object> parameters = new ArrayList<Object>();
    parameters.add(vars.getClient());
    parameters.add(strOrgId);
    parameters.add((isReceipt ? "ARR" : "APP"));
    // parameters.add(null);
    String strDocTypeId = (String) CallStoredProcedure.getInstance()
        .call("AD_GET_DOCTYPE", parameters, null);
    String strDocNo = Utility.getDocumentNo(this, vars, "AddPaymentFromInvoice", "FIN_Payment",
        strDocTypeId, strDocTypeId, false, false);
    xmlDocument.setParameter("documentNumber", "<" + strDocNo + ">");
    Invoice inv = OBDal.getInstance().get(Invoice.class, strInvoiceId);
    String strPaymentMethodId = inv.getPaymentMethod().getId();

    FIN_FinancialAccount account = isReceipt ? bp.getAccount() : bp.getPOFinancialAccount();
    if (account == null) {
      log4j.info("No default info for the selected business partner");
      account = dao.getDefaultFinancialAccountFor(strOrgId);
    }

    String strFinancialAccountId = account != null ? account.getId() : "";
    xmlDocument.setParameter("customerBalance", bp.getCreditUsed().toString());

    // Payment Method combobox
    String paymentMethodComboHtml = FIN_Utility.getPaymentMethodList(strPaymentMethodId, "",
        strOrgId, true, true, isReceipt);
    xmlDocument.setParameter("sectionDetailPaymentMethod", paymentMethodComboHtml);

    // Financial Account combobox
    List<FIN_FinancialAccount> financialAccounts = dao.getFilteredFinancialAccounts(
        strPaymentMethodId, strOrgId, strCurrencyId,
        isReceipt ? AdvPaymentMngtDao.PaymentDirection.IN : AdvPaymentMngtDao.PaymentDirection.OUT);

    if (financialAccounts.size() == 0) {
      final OBError myMessage = Utility.translateError(this, vars, vars.getLanguage(),
          Utility.messageBD(this, "APRM_NoFinancialAccountDefined", vars.getLanguage()));
      vars.setMessage(strTabId, myMessage);
      printPageClosePopUp(response, vars);
    }

    String finAccountComboHtml = FIN_Utility.getOptionsList(financialAccounts,
        strFinancialAccountId, true);
    if (financialAccounts.size() > 0 && account == null) {
      strFinancialAccountId = financialAccounts.get(0).getId();
    }
    xmlDocument.setParameter("sectionDetailFinancialAccount", finAccountComboHtml);

    final String strtypewriteoff;
    final String strAmountwriteoff;
    if (account != null) {
      if (!financialAccounts.contains(account)) {
        strFinancialAccountId = financialAccounts.get(0).getId();
        if (financialAccounts.size() > 0 && financialAccounts.get(0).getWriteofflimit() != null) {
          strtypewriteoff = financialAccounts.get(0).getTypewriteoff();
          strAmountwriteoff = financialAccounts.get(0).getWriteofflimit().toString();
          xmlDocument.setParameter("strtypewriteoff", strtypewriteoff);
          xmlDocument.setParameter("strAmountwriteoff", strAmountwriteoff);
        }

      } else {
        if (account.getWriteofflimit() != null) {
          strtypewriteoff = account.getTypewriteoff();
          strAmountwriteoff = account.getWriteofflimit().toString();
          xmlDocument.setParameter("strtypewriteoff", strtypewriteoff);
          xmlDocument.setParameter("strAmountwriteoff", strAmountwriteoff);
        }
      }
    } else {
      if (financialAccounts.size() > 0 && financialAccounts.get(0).getWriteofflimit() != null) {
        strtypewriteoff = financialAccounts.get(0).getTypewriteoff();
        strAmountwriteoff = financialAccounts.get(0).getWriteofflimit().toString();
        xmlDocument.setParameter("strtypewriteoff", strtypewriteoff);
        xmlDocument.setParameter("strAmountwriteoff", strAmountwriteoff);
      }
    }

    // Currency
    xmlDocument.setParameter("CurrencyId", strCurrencyId);
    final Currency paymentCurrency = dao.getObject(Currency.class, strCurrencyId);

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "TABLEDIR", "C_Currency_ID",
          "", "", Utility.getContext(this, vars, "#AccessibleOrgTree", "AddPaymentFromInvoice"),
          Utility.getContext(this, vars, "#User_Client", "AddPaymentFromInvoice"), 0);
      Utility.fillSQLParameters(this, vars, null, comboTableData, "AddPaymentFromInvoice",
          strCurrencyId);
      xmlDocument.setData("reportC_Currency_ID", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    try {
      OBContext.setAdminMode(true);

      final Currency financialAccountCurrency = dao
          .getFinancialAccountCurrency(strFinancialAccountId);
      if (financialAccountCurrency != null) {
        xmlDocument.setParameter("financialAccountCurrencyId", financialAccountCurrency.getId());
        xmlDocument.setParameter("financialAccountCurrencyPrecision",
            financialAccountCurrency.getStandardPrecision().toString());
      }
      BigDecimal exchangeRate = findExchangeRate(vars, paymentCurrency, financialAccountCurrency,
          new Date(), OBDal.getInstance().get(Organization.class, strOrgId),
          conversionRatePrecision, inv);

      if (exchangeRate == null) {
        final OBError myMessage = Utility.translateError(this, vars, vars.getLanguage(),
            Utility.messageBD(this, "NoCurrencyConversion", vars.getLanguage()));
        vars.setMessage(strTabId, myMessage);
        printPageClosePopUp(response, vars);
        return;
      }

      xmlDocument.setParameter("exchangeRate", exchangeRate.toPlainString());

    } finally {
      OBContext.restorePreviousMode();
    }

    boolean forcedFinancialAccountTransaction = false;
    forcedFinancialAccountTransaction = isForcedFinancialAccountTransaction(isReceipt,
        strFinancialAccountId, strPaymentMethodId);

    // Action Regarding Document
    xmlDocument.setParameter("ActionDocument", (isReceipt ? "PRP" : "PPP"));
    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "LIST", "",
          (isReceipt ? "F903F726B41A49D3860243101CEEBA25" : "F15C13A199A748F1B0B00E985A64C036"),
          forcedFinancialAccountTransaction ? "29010995FD39439D97A5C0CE8CE27D70" : "",
          Utility.getContext(this, vars, "#AccessibleOrgTree", "AddPaymentFromInvoice"),
          Utility.getContext(this, vars, "#User_Client", "AddPaymentFromInvoice"), 0);
      Utility.fillSQLParameters(this, vars, null, comboTableData, "AddPaymentFromInvoice", "");
      xmlDocument.setData("reportActionDocument", "liststructure", comboTableData.select(false));
      comboTableData = null;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
    // Not allow to change exchange rate and amount
    final String strNotAllowExchange = Utility.getContext(this, vars, "NotAllowChangeExchange",
        strWindowId);
    xmlDocument.setParameter("strNotAllowExchange", strNotAllowExchange);

    // Not allow to write off
    final String strWriteOffLimit = Utility.getContext(this, vars, "WriteOffLimitPreference",
        strWindowId);
    xmlDocument.setParameter("strWriteOffLimit", strWriteOffLimit);

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private void printGrid(HttpServletResponse response, VariablesSecureApp vars,
      String strBusinessPartnerId, String strCurrencyId, String strInvoiceId, String strOrgId,
      String strExpectedDateFrom, String strExpectedDateTo, String strDocumentType,
      String strSelectedPaymentDetails, boolean isReceipt, boolean showAlternativePM)
      throws IOException, ServletException {

    log4j.debug("Output: Grid with pending payments");
    dao = new AdvPaymentMngtDao();
    String[] discard = { "businessPartnerName" };

    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/advpaymentmngt/ad_actionbutton/AddPaymentGrid", discard)
        .createXmlDocument();

    Invoice inv = dao.getObject(Invoice.class, strInvoiceId);

    // Pending Payments from invoice
    final List<FIN_PaymentScheduleDetail> invoiceScheduledPaymentDetails = dao
        .getInvoicePendingScheduledPaymentDetails(inv);
    // selected scheduled payments list
    final List<FIN_PaymentScheduleDetail> selectedScheduledPaymentDetails = FIN_AddPayment
        .getSelectedPaymentDetails(invoiceScheduledPaymentDetails, strSelectedPaymentDetails);

    // filtered scheduled payments list
    final List<FIN_PaymentScheduleDetail> filteredScheduledPaymentDetails = dao
        .getFilteredScheduledPaymentDetails(dao.getObject(Organization.class, strOrgId),
            dao.getObject(BusinessPartner.class, strBusinessPartnerId),
            dao.getObject(Currency.class, strCurrencyId), null, null,
            FIN_Utility.getDate(strExpectedDateFrom),
            FIN_Utility.getDate(DateTimeData.nDaysAfter(this, strExpectedDateTo, "1")), null, null,
            strDocumentType, "", showAlternativePM ? null : inv.getPaymentMethod(),
            selectedScheduledPaymentDetails, isReceipt);

    final FieldProvider[] data = FIN_AddPayment.getShownScheduledPaymentDetails(vars,
        selectedScheduledPaymentDetails, filteredScheduledPaymentDetails, false, null);
    xmlDocument.setData("structure", (data == null) ? set() : data);

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private void refreshPaymentMethodCombo(HttpServletResponse response, String srtPaymentMethod,
      String strFinancialAccountId, String strOrgId, boolean isReceipt)
      throws IOException, ServletException {
    log4j.debug("Callout: Financial Account has changed to" + strFinancialAccountId);

    String paymentMethodComboHtml = FIN_Utility.getPaymentMethodList(srtPaymentMethod,
        strFinancialAccountId, strOrgId, true, true, isReceipt);

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(paymentMethodComboHtml.replaceAll("\"", "\\'"));

    out.close();
  }

  private void refreshFinancialAccountCombo(HttpServletResponse response, VariablesSecureApp vars,
      String strPaymentMethodId, String strFinancialAccountId, String strOrgId,
      String strCurrencyId, boolean isReceipt, String paymentDate, int conversionRatePrecision,
      String strInvoiceId) throws IOException, ServletException {
    log4j.debug("Callout: Payment Method has changed to " + strPaymentMethodId);

    dao = new AdvPaymentMngtDao();

    String finAccountComboHtml = FIN_Utility.getFinancialAccountList(strPaymentMethodId,
        strFinancialAccountId, strOrgId, true, strCurrencyId, isReceipt);

    final Currency financialAccountCurrency = dao
        .getFinancialAccountCurrency(strFinancialAccountId);
    final Currency paymentCurrency = dao.getObject(Currency.class, strCurrencyId);
    final String formatOutput = vars.getSessionValue("#FormatOutput|generalQtyRelation",
        "#,##0.######");

    Invoice inv = OBDal.getInstance().get(Invoice.class, strInvoiceId);
    BigDecimal exchangeRate = findExchangeRate(vars, paymentCurrency, financialAccountCurrency,
        FIN_Utility.getDate(paymentDate), OBDal.getInstance().get(Organization.class, strOrgId),
        conversionRatePrecision, inv);

    FIN_FinancialAccount financialAccount = dao.getObject(FIN_FinancialAccount.class,
        strFinancialAccountId);

    JSONObject msg = new JSONObject();
    try {
      if (financialAccount != null && financialAccount.getWriteofflimit() != null) {
        msg.put("twriteoff", financialAccount.getTypewriteoff());
        msg.put("awriteoff", financialAccount.getWriteofflimit().toString());
      }
      msg.put("combo", finAccountComboHtml);
      if (financialAccountCurrency != null) {
        msg.put("financialAccountCurrencyId", financialAccountCurrency.getId());
        msg.put("financialAccountCurrencyPrecision",
            financialAccountCurrency.getStandardPrecision());
      }
      msg.put("exchangeRate", exchangeRate == null ? "" : exchangeRate);
      msg.put("formatOutput", formatOutput);
    } catch (JSONException e) {
      log4j.error("JSON object error" + msg.toString());
    }

    response.setContentType("application/json; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(msg.toString());
    out.close();

  }

  private void refreshExchangeRate(HttpServletResponse response, VariablesSecureApp vars,
      String strCurrencyId, String strFinancialAccountCurrencyId, String strPaymentDate,
      Organization organization, int conversionRatePrecision, String strInvoiceId)
      throws IOException, ServletException {

    dao = new AdvPaymentMngtDao();

    final Currency financialAccountCurrency = dao.getObject(Currency.class,
        strFinancialAccountCurrencyId);
    final Currency paymentCurrency = dao.getObject(Currency.class, strCurrencyId);
    final String formatOutput = vars.getSessionValue("#FormatOutput|generalQtyRelation",
        "#,##0.######");

    Invoice inv = OBDal.getInstance().get(Invoice.class, strInvoiceId);
    BigDecimal exchangeRate = findExchangeRate(vars, paymentCurrency, financialAccountCurrency,
        FIN_Utility.getDate(strPaymentDate), organization, conversionRatePrecision, inv);

    JSONObject msg = new JSONObject();
    try {
      msg.put("exchangeRate", exchangeRate == null ? "" : exchangeRate);
      msg.put("formatOutput", formatOutput);
    } catch (JSONException e) {
      log4j.error("JSON object error" + msg.toString());
    }
    response.setContentType("application/json; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(msg.toString());
    out.close();
  }

  private void refreshProcessOptions(HttpServletResponse response, VariablesSecureApp vars,
      String strPaymentMethod, String strFinancialAccountId, String strOrgId, boolean isReceipt)
      throws IOException, ServletException {
    log4j.debug("Callout: Financial Account has changed to" + strFinancialAccountId);

    FIN_PaymentMethod paymentMethod = OBDal.getInstance()
        .get(FIN_PaymentMethod.class, strPaymentMethod);
    FIN_FinancialAccount financialAccount = OBDal.getInstance()
        .get(FIN_FinancialAccount.class, strFinancialAccountId);
    FinAccPaymentMethod finAccPaymentMethod = null;

    for (FinAccPaymentMethod finAccPaymentMethodItem : financialAccount
        .getFinancialMgmtFinAccPaymentMethodList()) {
      if (finAccPaymentMethodItem.getPaymentMethod()
          .getId()
          .equalsIgnoreCase(paymentMethod.getId())) {
        finAccPaymentMethod = finAccPaymentMethodItem;
      }
    }
    String processOprtionsComboHtml = null;

    boolean forcedFinancialAccountTransaction = isReceipt ? finAccPaymentMethod.isAutomaticDeposit()
        : finAccPaymentMethod.isAutomaticWithdrawn();

    try {
      ComboTableData comboTableData = new ComboTableData(vars, this, "LIST", "",
          (isReceipt ? "F903F726B41A49D3860243101CEEBA25" : "F15C13A199A748F1B0B00E985A64C036"),
          forcedFinancialAccountTransaction ? "29010995FD39439D97A5C0CE8CE27D70" : "",
          Utility.getContext(this, vars, "#AccessibleOrgTree", "AddPaymentFromInvoice"),
          Utility.getContext(this, vars, "#User_Client", "AddPaymentFromInvoice"), 0);
      Utility.fillSQLParameters(this, vars, null, comboTableData, "AddPaymentFromInvoice", "");
      FieldProvider[] properOptions = comboTableData.select(false);
      processOprtionsComboHtml = FIN_Utility.getOptionsListFromFieldProvider(properOptions, null,
          true);
    } catch (Exception e) {

    }

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(processOprtionsComboHtml.replaceAll("\"", "\\'"));

    out.close();
  }

  private BigDecimal findExchangeRate(VariablesSecureApp vars, Currency paymentCurrency,
      Currency financialAccountCurrency, Date paymentDate, Organization organization,
      int conversionRatePrecision, Invoice invoice) {

    BigDecimal exchangeRate = BigDecimal.ONE;

    ConversionRateDoc conversionRateDoc = null;

    if (financialAccountCurrency != null && !financialAccountCurrency.equals(paymentCurrency)) {
      if (checkDocumentConversionRate(invoice.getId())) {
        conversionRateDoc = FIN_Utility.getConversionRateDoc(paymentCurrency,
            financialAccountCurrency, invoice.getId(), invoice.getEntity());
        if (conversionRateDoc == null) {
          exchangeRate = null;
        } else {
          exchangeRate = conversionRateDoc.getRate()
              .setScale(conversionRatePrecision, RoundingMode.HALF_UP);
        }

      }
      if (conversionRateDoc == null) {
        final ConversionRate conversionRate = FIN_Utility.getConversionRate(paymentCurrency,
            financialAccountCurrency, paymentDate, organization);
        if (conversionRate == null) {
          exchangeRate = null;
        } else {
          exchangeRate = conversionRate.getMultipleRateBy()
              .setScale(conversionRatePrecision, RoundingMode.HALF_UP);
        }
      }
    }
    return exchangeRate;
  }

  private boolean checkDocumentConversionRate(String documentId) {
    OBCriteria<ConversionRateDoc> conversionRateDocCriteria = OBDal.getInstance()
        .createCriteria(ConversionRateDoc.class);
    conversionRateDocCriteria.add(Restrictions.eq(ConversionRateDoc.PROPERTY_INVOICE,
        OBDal.getInstance().get(Invoice.class, documentId)));
    if (conversionRateDocCriteria.count() > 0) {
      return true;
    } else {
      return false;
    }
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

  @Override
  public String getServletInfo() {
    return "Servlet that presents the payment proposal";
    // end of getServletInfo() method
  }

  /**
   * Returns the boolean value based on the transaction account has a automatic deposit or automatic
   * withdrawn value.
   * 
   * @param isReceipt
   *          . Indicates the transaction is belongs to purchase or sales.
   * @param strFinAccId
   *          . Indicates the financial account id for the transaction.
   * @param strPmtMethodId
   *          . Indicates the payment method id for the transaction. @return. Returns boolean value
   *          based on the automatic deposit or automatic withdrawn value.
   */
  private Boolean isForcedFinancialAccountTransaction(boolean isReceipt, String strFinAccId,
      String strPmtMethodId) {
    FIN_FinancialAccount finAcc = new AdvPaymentMngtDao().getObject(FIN_FinancialAccount.class,
        strFinAccId);
    FIN_PaymentMethod finPmtMethod = new AdvPaymentMngtDao().getObject(FIN_PaymentMethod.class,
        strPmtMethodId);
    OBCriteria<FinAccPaymentMethod> psdFilter = OBDal.getInstance()
        .createCriteria(FinAccPaymentMethod.class);
    psdFilter.add(Restrictions.eq(FinAccPaymentMethod.PROPERTY_ACCOUNT, finAcc));
    psdFilter.add(Restrictions.eq(FinAccPaymentMethod.PROPERTY_PAYMENTMETHOD, finPmtMethod));
    for (FinAccPaymentMethod paymentMethod : psdFilter.list()) {
      return isReceipt ? paymentMethod.isAutomaticDeposit() : paymentMethod.isAutomaticWithdrawn();
    }
    return false;
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
      log4j.error("AddPaymentFromInvoice - Callback", e);
    }

  }
}
