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
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.advpaymentmngt.ad_actionbutton;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.openbravo.advpaymentmngt.ProcessInvoiceHook;
import org.openbravo.advpaymentmngt.dao.AdvPaymentMngtDao;
import org.openbravo.advpaymentmngt.process.FIN_AddPayment;
import org.openbravo.advpaymentmngt.process.FIN_PaymentProcess;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.filter.IsIDFilter;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBDao;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.data.FieldProvider;
import org.openbravo.erpCommon.ad_actionButton.ActionButtonUtility;
import org.openbravo.erpCommon.ad_forms.AcctServer;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.DateTimeData;
import org.openbravo.erpCommon.utility.FieldProviderFactory;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.ConversionRateDoc;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.ReversedInvoice;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentDetail;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentDetailV;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentSchedule;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.model.financialmgmt.payment.FinAccPaymentMethod;
import org.openbravo.service.db.CallProcess;
import org.openbravo.xmlEngine.XmlDocument;

public class ProcessInvoice extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;

  private List<FIN_Payment> creditPayments = new ArrayList<FIN_Payment>();
  private final AdvPaymentMngtDao dao = new AdvPaymentMngtDao();
  private static final String Purchase_Invoice_Window = "183";

  @Inject
  @Any
  private Instance<ProcessInvoiceHook> hooks;

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    VariablesSecureApp vars = new VariablesSecureApp(request);

    if (vars.commandIn("DEFAULT")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessInvoice|Window_ID",
          IsIDFilter.instance);
      final String strTabId = vars.getGlobalVariable("inpTabId", "ProcessInvoice|Tab_ID",
          IsIDFilter.instance);

      final String strC_Invoice_ID = vars.getGlobalVariable("inpcInvoiceId",
          strWindowId + "|C_Invoice_ID", "", IsIDFilter.instance);

      final String strdocaction = vars.getStringParameter("inpdocaction");
      final String strProcessing = vars.getStringParameter("inpprocessing", "Y");
      final String strOrg = vars.getRequestGlobalVariable("inpadOrgId", "ProcessInvoice|Org_ID",
          IsIDFilter.instance);
      final String strClient = vars.getStringParameter("inpadClientId", IsIDFilter.instance);

      final String strdocstatus = vars.getRequiredStringParameter("inpdocstatus");
      final String stradTableId = "318";
      final int accesslevel = 1;

      if ((org.openbravo.erpCommon.utility.WindowAccessData.hasReadOnlyAccess(this, vars.getRole(),
          strTabId))
          || !(Utility.isElementInList(
              Utility.getContext(this, vars, "#User_Client", strWindowId, accesslevel), strClient)
              && Utility.isElementInList(
                  Utility.getContext(this, vars, "#User_Org", strWindowId, accesslevel), strOrg))) {
        OBError myError = Utility.translateError(this, vars, vars.getLanguage(),
            Utility.messageBD(this, "NoWriteAccess", vars.getLanguage()));
        vars.setMessage(strTabId, myError);
        printPageClosePopUp(response, vars);
      } else {
        printPageDocAction(response, vars, strC_Invoice_ID, strdocaction, strProcessing,
            strdocstatus, stradTableId, strWindowId);
      }
    } else if (vars.commandIn("SAVE_BUTTONDocAction111")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessInvoice|Window_ID",
          IsIDFilter.instance);
      final String strTabId = vars.getGlobalVariable("inpTabId", "ProcessInvoice|Tab_ID",
          IsIDFilter.instance);
      final String strC_Invoice_ID = vars.getGlobalVariable("inpKey", strWindowId + "|C_Invoice_ID",
          "");
      final String strdocaction = vars.getStringParameter("inpdocaction");
      final String strVoidInvoiceDate = vars.getStringParameter("inpVoidedDocumentDate");
      final String strVoidInvoiceAcctDate = vars.getStringParameter("inpVoidedDocumentAcctDate");
      final String strOrg = vars.getGlobalVariable("inpadOrgId", "ProcessInvoice|Org_ID",
          IsIDFilter.instance);

      OBError myMessage = null;
      try {

        Invoice invoice = dao.getObject(Invoice.class, strC_Invoice_ID);
        invoice.setDocumentAction(strdocaction);
        OBDal.getInstance().save(invoice);
        OBDal.getInstance().flush();

        OBError msg = null;
        for (ProcessInvoiceHook hook : hooks) {
          msg = hook.preProcess(invoice, strdocaction);
          if (msg != null && "Error".equals(msg.getType())) {
            vars.setMessage(strTabId, msg);
            String strWindowPath = Utility.getTabURL(strTabId, "R", true);
            if (strWindowPath.equals("")) {
              strWindowPath = strDefaultServlet;
            }
            printPageClosePopUp(response, vars, strWindowPath);
            return;
          }
        }
        // check BP currency
        if ("CO".equals(strdocaction)) {
          // check BP currency
          if (invoice.getBusinessPartner().getCurrency() == null) {
            String errorMSG = Utility.messageBD(this, "InitBPCurrencyLnk", vars.getLanguage(),
                false);
            msg = new OBError();
            msg.setType("Error");
            msg.setTitle(Utility.messageBD(this, "Error", vars.getLanguage()));
            msg.setMessage(String.format(errorMSG, invoice.getBusinessPartner().getId(),
                invoice.getBusinessPartner().getName()));

            vars.setMessage(strTabId, msg);
            printPageClosePopUp(response, vars, Utility.getTabURL(strTabId, "R", true));
            return;
          }
        }

        OBContext.setAdminMode(true);
        Process process = null;
        try {
          process = dao.getObject(Process.class, "111");
        } finally {
          OBContext.restorePreviousMode();
        }

        Date voidDate = null;
        Date voidAcctDate = null;
        Map<String, String> parameters = null;
        if (!strVoidInvoiceDate.isEmpty() && !strVoidInvoiceAcctDate.isEmpty()) {
          try {
            voidDate = OBDateUtils.getDate(strVoidInvoiceDate);
            voidAcctDate = OBDateUtils.getDate(strVoidInvoiceAcctDate);
          } catch (ParseException pe) {
            voidDate = new Date();
            voidAcctDate = new Date();
            log4j.error("Not possible to parse the following date: " + strVoidInvoiceDate, pe);
            log4j.error("Not possible to parse the following date: " + strVoidInvoiceAcctDate, pe);
          }
          parameters = new HashMap<String, String>();
          parameters.put("voidedDocumentDate", OBDateUtils.formatDate(voidDate, "yyyy-MM-dd"));
          parameters.put("voidedDocumentAcctDate",
              OBDateUtils.formatDate(voidAcctDate, "yyyy-MM-dd"));

        }

        // In case of void a non paid invoice, create a dummy payment related to it with zero amount
        FIN_Payment dummyPayment = null;
        if ("RC".equals(strdocaction) && !invoice.isPaymentComplete()
            && invoice.getTotalPaid().compareTo(BigDecimal.ZERO) == 0) {
          try {
            OBContext.setAdminMode(true);
            final boolean isSOTrx = invoice.isSalesTransaction();
            final DocumentType docType = FIN_Utility.getDocumentType(invoice.getOrganization(),
                isSOTrx ? AcctServer.DOCTYPE_ARReceipt : AcctServer.DOCTYPE_APPayment);
            final String strPaymentDocumentNo = FIN_Utility.getDocumentNo(docType,
                docType.getTable() != null ? docType.getTable().getDBTableName() : "");

            // Get default Financial Account as it is done in Add Payment
            FIN_FinancialAccount bpFinAccount = null;
            if (isSOTrx && invoice.getBusinessPartner().getAccount() != null
                && FIN_Utility.getFinancialAccountPaymentMethod(invoice.getPaymentMethod().getId(),
                    invoice.getBusinessPartner().getAccount().getId(), isSOTrx,
                    invoice.getCurrency().getId(), invoice.getOrganization().getId()) != null) {
              bpFinAccount = invoice.getBusinessPartner().getAccount();
            } else if (!isSOTrx && invoice.getBusinessPartner().getPOFinancialAccount() != null
                && FIN_Utility.getFinancialAccountPaymentMethod(invoice.getPaymentMethod().getId(),
                    invoice.getBusinessPartner().getPOFinancialAccount().getId(), isSOTrx,
                    invoice.getCurrency().getId(), invoice.getOrganization().getId()) != null) {
              bpFinAccount = invoice.getBusinessPartner().getPOFinancialAccount();
            } else {
              FinAccPaymentMethod fpm = FIN_Utility.getFinancialAccountPaymentMethod(
                  invoice.getPaymentMethod().getId(), null, isSOTrx, invoice.getCurrency().getId(),
                  invoice.getOrganization().getId());
              if (fpm != null) {
                bpFinAccount = fpm.getAccount();
              }
            }

            // If no Financial Account exists, show an Error
            if (bpFinAccount == null) {
              msg = new OBError();
              msg.setType("Error");
              msg.setTitle(Utility.messageBD(this, "Error", vars.getLanguage()));
              msg.setMessage(OBMessageUtils.messageBD("APRM_NoFinancialAccountAvailable"));
              vars.setMessage(strTabId, msg);
              printPageClosePopUp(response, vars, Utility.getTabURL(strTabId, "R", true));
              return;
            }

            // If Invoice has a awaiting execution payment related, show an Error
            StringBuilder fpHQLQuery = new StringBuilder(" as fp");
            fpHQLQuery.append(" join fp.fINPaymentDetailList fpd");
            fpHQLQuery.append(" join fpd.fINPaymentScheduleDetailList fpsd");
            fpHQLQuery.append(" join fpsd.invoicePaymentSchedule fps");
            fpHQLQuery.append(" where fps.invoice.id = :invoiceId");
            fpHQLQuery.append(" and fp.status in ('RPAE', 'RPAP')");
            OBQuery<FIN_Payment> paymentQuery = OBDal.getInstance()
                .createQuery(FIN_Payment.class, fpHQLQuery.toString());
            paymentQuery.setNamedParameter("invoiceId", invoice.getId());
            paymentQuery.setMaxResult(1);
            if (paymentQuery.uniqueResult() != null) {
              msg = new OBError();
              msg.setType("Error");
              msg.setTitle(Utility.messageBD(this, "Error", vars.getLanguage()));
              msg.setMessage(
                  OBMessageUtils.messageBD("APRM_InvoiceAwaitingExcutionPaymentRelated"));
              vars.setMessage(strTabId, msg);
              printPageClosePopUp(response, vars, Utility.getTabURL(strTabId, "R", true));
              return;
            }

            // Reversed invoice's date: voidDate in Purchase Invoice, new Date() in Sales Invoice
            Date reversedDate = voidDate != null ? voidDate : new Date();

            // Calculate Conversion Rate
            BigDecimal rate = null;
            if (!StringUtils.equals(invoice.getCurrency().getId(),
                bpFinAccount.getCurrency().getId())) {
              final ConversionRate conversionRate = FinancialUtils.getConversionRate(reversedDate,
                  invoice.getCurrency(), bpFinAccount.getCurrency(), invoice.getOrganization(),
                  invoice.getClient());
              if (conversionRate != null) {
                rate = conversionRate.getMultipleRateBy();
              }
            }

            // Create dummy payment
            dummyPayment = dao.getNewPayment(isSOTrx, invoice.getOrganization(), docType,
                strPaymentDocumentNo, invoice.getBusinessPartner(), invoice.getPaymentMethod(),
                bpFinAccount, "0", reversedDate, invoice.getDocumentNo(), invoice.getCurrency(),
                rate, null);
            OBDal.getInstance().save(dummyPayment);

            List<FIN_PaymentDetail> paymentDetails = new ArrayList<FIN_PaymentDetail>();
            List<FIN_PaymentScheduleDetail> paymentScheduleDetails = dao
                .getInvoicePendingScheduledPaymentDetails(invoice);
            for (FIN_PaymentScheduleDetail psd : paymentScheduleDetails) {
              FIN_PaymentDetail pd = OBProvider.getInstance().get(FIN_PaymentDetail.class);
              pd.setOrganization(psd.getOrganization());
              pd.setFinPayment(dummyPayment);
              pd.setAmount(psd.getAmount());
              pd.setRefund(false);
              OBDal.getInstance().save(pd);

              paymentDetails.add(pd);
              psd.setPaymentDetails(pd);
              pd.getFINPaymentScheduleDetailList().add(psd);
              OBDal.getInstance().save(psd);
            }
            dummyPayment.setFINPaymentDetailList(paymentDetails);

            // Copy exchange rate from invoice
            for (ConversionRateDoc conversionRateDoc : invoice.getCurrencyConversionRateDocList()) {
              ConversionRateDoc newConversionRateDoc = OBProvider.getInstance()
                  .get(ConversionRateDoc.class);
              newConversionRateDoc.setClient(conversionRateDoc.getClient());
              newConversionRateDoc.setOrganization(conversionRateDoc.getOrganization());
              newConversionRateDoc.setCurrency(conversionRateDoc.getCurrency());
              newConversionRateDoc.setToCurrency(conversionRateDoc.getToCurrency());
              newConversionRateDoc.setRate(conversionRateDoc.getRate());
              newConversionRateDoc.setForeignAmount(BigDecimal.ZERO);
              newConversionRateDoc.setPayment(dummyPayment);
              dummyPayment.getCurrencyConversionRateDocList().add(newConversionRateDoc);
              OBDal.getInstance().save(newConversionRateDoc);
            }

            OBDal.getInstance().save(dummyPayment);
          } catch (final Exception e) {
            log4j.error(
                "Exception while creating dummy payment for the invoice: " + strC_Invoice_ID, e);
          } finally {
            OBContext.restorePreviousMode();
          }
        }

        boolean voidingPrepaidInvoice = "RC".equals(strdocaction)
            && invoice.getPrepaymentamt().compareTo(BigDecimal.ZERO) != 0;

        final ProcessInstance pinstance = CallProcess.getInstance()
            .call(process, strC_Invoice_ID, parameters);

        OBDal.getInstance().getSession().refresh(invoice);
        invoice.setAPRMProcessinvoice(invoice.getDocumentAction());

        if ("RC".equals(strdocaction) && pinstance.getResult() != 0L) {
          try {
            OBContext.setAdminMode(true);

            // Get reversed payment
            OBCriteria<ReversedInvoice> revInvoiceCriteria = OBDal.getInstance()
                .createCriteria(ReversedInvoice.class);
            revInvoiceCriteria
                .add(Restrictions.eq(ReversedInvoice.PROPERTY_REVERSEDINVOICE, invoice));
            revInvoiceCriteria.setMaxResults(1);
            ReversedInvoice revInvoice = (ReversedInvoice) revInvoiceCriteria.uniqueResult();

            boolean processPayment = false;

            if (voidingPrepaidInvoice) {
              processPayment = true;

              StringBuilder orderPaymentHQLQuery = new StringBuilder(" as fp");
              orderPaymentHQLQuery.append(" join fp.fINPaymentDetailList fpd");
              orderPaymentHQLQuery.append(" join fpd.fINPaymentScheduleDetailList fpsd");
              orderPaymentHQLQuery.append(" join fpsd.invoicePaymentSchedule fps");
              orderPaymentHQLQuery.append(" where fps.invoice.id = :invoiceId");
              OBQuery<FIN_Payment> paymentQuery = OBDal.getInstance()
                  .createQuery(FIN_Payment.class, orderPaymentHQLQuery.toString());
              paymentQuery.setNamedParameter("invoiceId", invoice.getId());
              paymentQuery.setMaxResult(1);
              FIN_Payment orderPayment = (FIN_Payment) paymentQuery.uniqueResult();

              final DocumentType docType = FIN_Utility.getDocumentType(invoice.getOrganization(),
                  orderPayment.isReceipt() ? AcctServer.DOCTYPE_ARReceipt
                      : AcctServer.DOCTYPE_APPayment);
              final String strPaymentDocumentNo = FIN_Utility.getDocumentNo(docType,
                  docType.getTable() != null ? docType.getTable().getDBTableName() : "");

              // Creating a dummy payment
              dummyPayment = dao.getNewPayment(orderPayment.isReceipt(), invoice.getOrganization(),
                  orderPayment.getDocumentType(), strPaymentDocumentNo,
                  invoice.getBusinessPartner(), invoice.getPaymentMethod(),
                  orderPayment.getAccount(), "0", voidDate != null ? voidDate : new Date(),
                  invoice.getDocumentNo(), invoice.getCurrency(),
                  orderPayment.getFinancialTransactionConvertRate(), null);
              OBDal.getInstance().save(dummyPayment);

              invoice.setOutstandingAmount(BigDecimal.ZERO);

              StringBuilder psdHQLQuery = new StringBuilder(" as fpsd");
              psdHQLQuery.append(" join fpsd.invoicePaymentSchedule fps");
              psdHQLQuery.append(" where fps.invoice.id = :invoiceId");
              psdHQLQuery.append(" or fps.invoice.id = :revInvoiceId");
              OBQuery<FIN_PaymentScheduleDetail> psdQuery = OBDal.getInstance()
                  .createQuery(FIN_PaymentScheduleDetail.class, psdHQLQuery.toString());
              psdQuery.setNamedParameter("invoiceId", invoice.getId());
              psdQuery.setNamedParameter("revInvoiceId", revInvoice.getInvoice().getId());

              // Updating dummy payment lines with invoice and reverse invoice
              for (FIN_PaymentScheduleDetail fpsd : psdQuery.list()) {

                // Invoice payment detail associated to the order
                FIN_PaymentDetail invoiceFPDOrder = fpsd.getPaymentDetails();
                FIN_PaymentSchedule orderPaymentSchedule = fpsd.getOrderPaymentSchedule();

                // Create a payment detail
                FIN_PaymentDetail pd = OBProvider.getInstance().get(FIN_PaymentDetail.class);
                pd.setOrganization(dummyPayment.getOrganization());
                pd.setFinPayment(dummyPayment);
                pd.setAmount(fpsd.getAmount());
                pd.setRefund(false);
                OBDal.getInstance().save(pd);

                // Remove the reference to the order payment schedule
                fpsd.setOrderPaymentSchedule(null);
                fpsd.setPaymentDetails(pd);

                pd.getFINPaymentScheduleDetailList().add(fpsd);
                OBDal.getInstance().save(fpsd);

                dummyPayment.getFINPaymentDetailList().add(pd);

                if (invoiceFPDOrder != null) {
                  StringBuilder orderPSDHQLQuery = new StringBuilder();
                  orderPSDHQLQuery.append(" as fpsd");
                  orderPSDHQLQuery.append(" join fpsd.paymentDetails fpd");
                  orderPSDHQLQuery.append(" where fpd.finPayment.id = :paymentId");
                  orderPSDHQLQuery.append(" and fpsd.id <> :invoicePSDId");
                  orderPSDHQLQuery.append(" and fpsd.invoicePaymentSchedule is null");
                  OBQuery<FIN_PaymentScheduleDetail> orderPSDQuery = OBDal.getInstance()
                      .createQuery(FIN_PaymentScheduleDetail.class, orderPSDHQLQuery.toString());
                  orderPSDQuery.setNamedParameter("paymentId",
                      invoiceFPDOrder.getFinPayment().getId());
                  orderPSDQuery.setNamedParameter("invoicePSDId", fpsd.getId());
                  orderPSDQuery.setMaxResult(1);
                  FIN_PaymentScheduleDetail orderPSD = orderPSDQuery.uniqueResult();

                  if (orderPSD == null) {
                    // Order with no payment schedule detail, create a new one
                    orderPSD = OBProvider.getInstance().get(FIN_PaymentScheduleDetail.class);
                    orderPSD.setOrganization(fpsd.getOrganization());
                    orderPSD.setAmount(fpsd.getAmount());
                    orderPSD.setBusinessPartner(fpsd.getBusinessPartner());
                    orderPSD.setPaymentDetails(invoiceFPDOrder);
                    orderPSD.setOrderPaymentSchedule(orderPaymentSchedule);
                  } else {
                    // Update order received amount
                    orderPSD.setAmount(orderPSD.getAmount().add(fpsd.getAmount()));
                  }
                  OBDal.getInstance().save(orderPSD);

                  // Update invoice payment schedule
                  FIN_PaymentSchedule ps = fpsd.getInvoicePaymentSchedule();
                  ps.setPaidAmount(BigDecimal.ZERO);
                  ps.setOutstandingAmount(fpsd.getAmount());
                  OBDal.getInstance().save(ps);

                  // Update invoice outstanding amount
                  invoice
                      .setOutstandingAmount(invoice.getOutstandingAmount().add(fpsd.getAmount()));
                }
              }
              OBDal.getInstance().save(dummyPayment);

              revInvoice.getInvoice().setPrepaymentamt(BigDecimal.ZERO);

              invoice.setTotalPaid(BigDecimal.ZERO);
              invoice.setPrepaymentamt(BigDecimal.ZERO);

              OBDal.getInstance().save(invoice);
              OBDal.getInstance().save(revInvoice.getInvoice());
            } else if (revInvoice != null && dummyPayment != null) {
              processPayment = true;

              List<FIN_PaymentDetail> paymentDetails = new ArrayList<FIN_PaymentDetail>();
              List<FIN_PaymentScheduleDetail> paymentScheduleDetails = dao
                  .getInvoicePendingScheduledPaymentDetails(revInvoice.getInvoice());
              for (FIN_PaymentScheduleDetail psd : paymentScheduleDetails) {
                FIN_PaymentDetail pd = OBProvider.getInstance().get(FIN_PaymentDetail.class);
                pd.setOrganization(psd.getOrganization());
                pd.setFinPayment(dummyPayment);
                pd.setAmount(psd.getAmount());
                pd.setRefund(false);
                OBDal.getInstance().save(pd);

                paymentDetails.add(pd);
                psd.setPaymentDetails(pd);
                pd.getFINPaymentScheduleDetailList().add(psd);
                OBDal.getInstance().save(psd);
              }
              dummyPayment.getFINPaymentDetailList().addAll(paymentDetails);
              OBDal.getInstance().save(dummyPayment);
            }

            if (processPayment) {
              // Process dummy payment related with both actual invoice and reversed invoice
              OBError message = FIN_AddPayment.processPayment(vars, this, "P", dummyPayment);
              if ("Error".equals(message.getType())) {
                message.setMessage(
                    OBMessageUtils.messageBD("PaymentError") + " " + message.getMessage());
                vars.setMessage(strTabId, message);
                String strWindowPath = Utility.getTabURL(strTabId, "R", true);
                if (strWindowPath.equals("")) {
                  strWindowPath = strDefaultServlet;
                }
                printPageClosePopUp(response, vars, strWindowPath);
                return;
              }
            }
          } catch (final Exception e) {
            log4j.error(
                "Exception while creating dummy payment for the invoice: " + strC_Invoice_ID, e);
          } finally {
            OBContext.restorePreviousMode();
          }
        }

        // Remove invoice's used credit description
        if ("RE".equals(strdocaction) && pinstance.getResult() != 0L) {
          final String invDesc = invoice.getDescription();
          if (invDesc != null) {
            final String creditMsg = Utility.messageBD(this, "APRM_InvoiceDescUsedCredit",
                vars.getLanguage());
            if (creditMsg != null) {
              final StringBuffer newDesc = new StringBuffer();
              for (final String line : invDesc.split("\n")) {
                if (!line.startsWith(creditMsg.substring(0, creditMsg.lastIndexOf("%s")))) {
                  newDesc.append(line);
                  if (!"".equals(line)) {
                    newDesc.append("\n");
                  }
                }
              }
              invoice.setDescription(newDesc.toString());
            }
          }
        }
        OBDal.getInstance().save(invoice);
        OBDal.getInstance().flush();

        OBContext.setAdminMode();
        try {
          // on error close popup and rollback
          if (pinstance.getResult() == 0L) {
            OBDal.getInstance().rollbackAndClose();
            myMessage = Utility.translateError(this, vars, vars.getLanguage(),
                pinstance.getErrorMsg().replaceFirst("@ERROR=", ""));
            log4j.debug(myMessage.getMessage());
            vars.setMessage(strTabId, myMessage);

            String strWindowPath = Utility.getTabURL(strTabId, "R", true);
            if (strWindowPath.equals("")) {
              strWindowPath = strDefaultServlet;
            }
            printPageClosePopUp(response, vars, strWindowPath);

            return;
          }
        } finally {
          OBContext.restorePreviousMode();
        }

        for (ProcessInvoiceHook hook : hooks) {
          msg = hook.postProcess(invoice, strdocaction);
          if (msg != null && "Error".equals(msg.getType())) {
            vars.setMessage(strTabId, msg);
            String strWindowPath = Utility.getTabURL(strTabId, "R", true);
            if (strWindowPath.equals("")) {
              strWindowPath = strDefaultServlet;
            }
            printPageClosePopUp(response, vars, strWindowPath);
            OBDal.getInstance().rollbackAndClose();
            return;
          }
        }

        OBDal.getInstance().commitAndClose();
        final PInstanceProcessData[] pinstanceData = PInstanceProcessData.select(this,
            pinstance.getId());
        myMessage = Utility.getProcessInstanceMessage(this, vars, pinstanceData);
        log4j.debug(myMessage.getMessage());
        vars.setMessage(strTabId, myMessage);

        OBContext.setAdminMode();
        try {
          if (!"CO".equals(strdocaction)) {
            String strWindowPath = Utility.getTabURL(strTabId, "R", true);
            if (strWindowPath.equals("")) {
              strWindowPath = strDefaultServlet;
            }
            printPageClosePopUp(response, vars, strWindowPath);
            return;
          }
        } finally {
          OBContext.restorePreviousMode();
        }

        if ("CO".equals(strdocaction)) {
          // Need to refresh the invoice again from the db
          invoice = dao.getObject(Invoice.class, strC_Invoice_ID);
          OBContext.setAdminMode(false);
          String invoiceDocCategory = "";
          try {
            invoiceDocCategory = invoice.getDocumentType().getDocumentCategory();

            /*
             * Print a grid popup in case of credit payment
             */
            // If the invoice grand total is ZERO or already has payments (due to
            // payment method automation) or the business partner does not have a default financial
            // account defined or invoice's payment method is not inside BP's financial
            // account or the business partner's currency is not equal to the invoice's currency do
            // not cancel credit
            if (BigDecimal.ZERO.compareTo(invoice.getGrandTotalAmount()) != 0
                && isPaymentMethodConfigured(invoice) && !isInvoiceWithPayments(invoice)
                && (AcctServer.DOCTYPE_ARInvoice.equals(invoiceDocCategory)
                    || AcctServer.DOCTYPE_APInvoice.equals(invoiceDocCategory))
                && (invoice.getBusinessPartner().getCurrency() != null
                    && StringUtils.equals(invoice.getCurrency().getId(),
                        invoice.getBusinessPartner().getCurrency().getId()))) {
              creditPayments = dao.getCustomerPaymentsWithCredit(invoice.getOrganization(),
                  invoice.getBusinessPartner(), invoice.isSalesTransaction(),
                  invoice.getCurrency());
              if (creditPayments != null && !creditPayments.isEmpty()) {
                printPageCreditPaymentGrid(response, vars, strC_Invoice_ID, strdocaction, strTabId,
                    strC_Invoice_ID, strdocaction, strWindowId, strTabId, invoice.getInvoiceDate(),
                    strOrg);
              }
            }
          } finally {
            OBContext.restorePreviousMode();
          }

          executePayments(response, vars, strWindowId, strTabId, strC_Invoice_ID, strOrg);
        }

      } catch (ServletException ex) {
        myMessage = Utility.translateError(this, vars, vars.getLanguage(), ex.getMessage());
        if (!myMessage.isConnectionAvailable()) {
          bdErrorConnection(response);
          return;
        } else {
          vars.setMessage(strTabId, myMessage);
        }
      }

    } else if (vars.commandIn("GRIDLIST")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessInvoice|Window_ID",
          IsIDFilter.instance);
      final String strC_Invoice_ID = vars.getGlobalVariable("inpKey", strWindowId + "|C_Invoice_ID",
          "", IsIDFilter.instance);

      printGrid(response, vars, strC_Invoice_ID);
    } else if (vars.commandIn("USECREDITPAYMENTS") || vars.commandIn("CANCEL_USECREDITPAYMENTS")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessInvoice|Window_ID",
          IsIDFilter.instance);
      final String strTabId = vars.getGlobalVariable("inpTabId", "ProcessInvoice|Tab_ID",
          IsIDFilter.instance);
      final String strC_Invoice_ID = vars.getGlobalVariable("inpKey", strWindowId + "|C_Invoice_ID",
          "");
      final String strPaymentDate = vars.getRequiredStringParameter("inpPaymentDate");
      final String strOrg = vars.getGlobalVariable("inpadOrgId", "ProcessInvoice|Org_ID",
          IsIDFilter.instance);

      final String strCreditPaymentIds;
      if (vars.commandIn("CANCEL_USECREDITPAYMENTS")) {
        strCreditPaymentIds = null;
      } else {
        strCreditPaymentIds = vars.getInParameter("inpCreditPaymentId", IsIDFilter.instance);
      }

      /*
       * Use credit logic
       */
      if (strCreditPaymentIds != null && !strCreditPaymentIds.isEmpty()) {
        List<FIN_Payment> selectedCreditPayment = FIN_Utility.getOBObjectList(FIN_Payment.class,
            strCreditPaymentIds);
        HashMap<String, BigDecimal> selectedCreditPaymentAmounts = FIN_AddPayment
            .getSelectedBaseOBObjectAmount(vars, selectedCreditPayment, "inpPaymentAmount");
        try {
          OBContext.setAdminMode(true);
          final Invoice invoice = OBDal.getInstance().get(Invoice.class, strC_Invoice_ID);

          final StringBuffer creditPaymentsIdentifiers = new StringBuffer();
          BigDecimal totalUsedCreditAmt = BigDecimal.ZERO;
          for (final FIN_Payment creditPayment : selectedCreditPayment) {
            final BigDecimal usedCreditAmt = selectedCreditPaymentAmounts
                .get(creditPayment.getId());
            // Set Used Credit = Amount + Previous used credit introduced by the user
            creditPayment.setUsedCredit(usedCreditAmt.add(creditPayment.getUsedCredit()));
            final StringBuffer description = new StringBuffer();
            if (creditPayment.getDescription() != null
                && !creditPayment.getDescription().equals("")) {
              description.append(creditPayment.getDescription()).append("\n");
            }
            description.append(String.format(
                Utility.messageBD(this, "APRM_CreditUsedinInvoice", vars.getLanguage()),
                invoice.getDocumentNo()));
            String truncateDescription = (description.length() > 255)
                ? description.substring(0, 251).concat("...").toString()
                : description.toString();
            creditPayment.setDescription(truncateDescription);
            totalUsedCreditAmt = totalUsedCreditAmt.add(usedCreditAmt);
            creditPaymentsIdentifiers.append(creditPayment.getDocumentNo());
            creditPaymentsIdentifiers.append(", ");
          }
          creditPaymentsIdentifiers.delete(creditPaymentsIdentifiers.length() - 2,
              creditPaymentsIdentifiers.length());
          creditPaymentsIdentifiers.append("\n");

          final List<FIN_PaymentScheduleDetail> paymentScheduleDetails = new ArrayList<FIN_PaymentScheduleDetail>();
          final HashMap<String, BigDecimal> paymentScheduleDetailsAmounts = new HashMap<String, BigDecimal>();
          BigDecimal allocatedAmt = BigDecimal.ZERO;
          for (final FIN_PaymentScheduleDetail paymentScheduleDetail : dao
              .getInvoicePendingScheduledPaymentDetails(invoice)) {
            if (totalUsedCreditAmt.compareTo(allocatedAmt) > 0) {
              final BigDecimal pendingToAllocate = totalUsedCreditAmt.subtract(allocatedAmt);
              paymentScheduleDetails.add(paymentScheduleDetail);

              final BigDecimal psdAmt = paymentScheduleDetail.getAmount();
              if (psdAmt.compareTo(pendingToAllocate) <= 0) {
                paymentScheduleDetailsAmounts.put(paymentScheduleDetail.getId(), psdAmt);
                allocatedAmt = allocatedAmt.add(psdAmt);
              } else {
                paymentScheduleDetailsAmounts.put(paymentScheduleDetail.getId(), pendingToAllocate);
                allocatedAmt = allocatedAmt.add(pendingToAllocate);
              }
            }
          }

          // Create new Payment
          final boolean isSalesTransaction = invoice.isSalesTransaction();
          final DocumentType docType = FIN_Utility.getDocumentType(invoice.getOrganization(),
              isSalesTransaction ? AcctServer.DOCTYPE_ARReceipt : AcctServer.DOCTYPE_APPayment);
          final String strPaymentDocumentNo = FIN_Utility.getDocumentNo(docType,
              docType.getTable() != null ? docType.getTable().getDBTableName() : "");
          final FIN_FinancialAccount bpFinAccount = isSalesTransaction
              ? invoice.getBusinessPartner().getAccount()
              : invoice.getBusinessPartner().getPOFinancialAccount();
          // Calculate Conversion Rate
          final ConversionRate conversionRate = StringUtils.equals(invoice.getCurrency().getId(),
              bpFinAccount.getCurrency().getId())
                  ? null
                  : FinancialUtils.getConversionRate(FIN_Utility.getDate(strPaymentDate),
                      invoice.getCurrency(), bpFinAccount.getCurrency(), invoice.getOrganization(),
                      invoice.getClient());
          final FIN_Payment newPayment = FIN_AddPayment.savePayment(null, isSalesTransaction,
              docType, strPaymentDocumentNo, invoice.getBusinessPartner(),
              invoice.getPaymentMethod(), bpFinAccount, "0", FIN_Utility.getDate(strPaymentDate),
              invoice.getOrganization(), invoice.getDocumentNo(), paymentScheduleDetails,
              paymentScheduleDetailsAmounts, false, false, invoice.getCurrency(),
              conversionRate != null ? conversionRate.getMultipleRateBy() : null, null);
          newPayment.setAmount(BigDecimal.ZERO);
          newPayment.setGeneratedCredit(BigDecimal.ZERO);
          newPayment.setUsedCredit(totalUsedCreditAmt);

          // Link new Payment with the credit payments used
          for (final FIN_Payment creditPayment : selectedCreditPayment) {
            final BigDecimal usedCreditAmt = selectedCreditPaymentAmounts
                .get(creditPayment.getId());
            FIN_PaymentProcess.linkCreditPayment(newPayment, usedCreditAmt, creditPayment);
          }

          // Process the new payment
          OBError message = FIN_AddPayment.processPayment(vars, this, "P", newPayment);
          if ("Success".equals(message.getType())) {
            // Update Invoice's description
            final StringBuffer invDesc = new StringBuffer();
            if (invoice.getDescription() != null) {
              invDesc.append(invoice.getDescription());
              invDesc.append("\n");
            }
            invDesc.append(String.format(
                Utility.messageBD(this, "APRM_InvoiceDescUsedCredit", vars.getLanguage()),
                creditPaymentsIdentifiers.toString()));
            invoice.setDescription(invDesc.toString());
          } else {
            message
                .setMessage(OBMessageUtils.messageBD("PaymentError") + " " + message.getMessage());
            vars.setMessage(strTabId, message);
          }

        } catch (final Exception e) {
          log4j.error("Exception while canceling the credit in the invoice: " + strC_Invoice_ID, e);
        } finally {
          OBContext.restorePreviousMode();
        }
      }
      executePayments(response, vars, strWindowId, strTabId, strC_Invoice_ID, strOrg);
    }
  }

  private void executePayments(HttpServletResponse response, VariablesSecureApp vars,
      final String strWindowId, final String strTabId, final String strC_Invoice_ID,
      final String strOrg) throws IOException, ServletException {
    OBError myMessage = new OBError();

    List<FIN_Payment> payments = null;
    try {
      OBContext.setAdminMode(true);
      payments = dao.getPendingExecutionPayments(strC_Invoice_ID);
    } finally {
      OBContext.restorePreviousMode();
    }
    if (payments != null && payments.size() > 0) {
      vars.setSessionValue("ExecutePayments|Window_ID", strWindowId);
      vars.setSessionValue("ExecutePayments|Tab_ID", strTabId);
      vars.setSessionValue("ExecutePayments|Org_ID", strOrg);
      vars.setSessionValue("ExecutePayments|payments", Utility.getInStrList(payments));
      if (myMessage != null) {
        vars.setMessage("ExecutePayments|message", myMessage);
      }
      response.sendRedirect(
          strDireccion + "/org.openbravo.advpaymentmngt.ad_actionbutton/ExecutePayments.html");
    } else {
      String strWindowPath = Utility.getTabURL(strTabId, "R", true);
      if (strWindowPath.equals("")) {
        strWindowPath = strDefaultServlet;
      }
      printPageClosePopUp(response, vars, strWindowPath);
    }

    vars.removeSessionValue("ProcessInvoice|Window_ID");
    vars.removeSessionValue("ProcessInvoice|Tab_ID");
    vars.removeSessionValue("ProcessInvoice|Org_ID");
  }

  void printPageDocAction(HttpServletResponse response, VariablesSecureApp vars,
      String strC_Invoice_ID, String strdocaction, String strProcessing, String strdocstatus,
      String stradTableId, String strWindowId) throws IOException, ServletException {
    log4j.debug("Output: Button process 111");
    String[] discard = { "newDiscard" };
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_actionButton/DocAction", discard)
        .createXmlDocument();
    xmlDocument.setParameter("key", strC_Invoice_ID);
    xmlDocument.setParameter("processing", strProcessing);
    xmlDocument.setParameter("form", "ProcessInvoice.html");
    xmlDocument.setParameter("window", strWindowId);
    xmlDocument.setParameter("css", vars.getTheme());
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("dateDisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("processId", "111");
    xmlDocument.setParameter("cancel", Utility.messageBD(this, "Cancel", vars.getLanguage()));
    xmlDocument.setParameter("ok", Utility.messageBD(this, "OK", vars.getLanguage()));

    OBError myMessage = vars.getMessage("111");
    vars.removeMessage("111");
    if (myMessage != null) {
      xmlDocument.setParameter("messageType", myMessage.getType());
      xmlDocument.setParameter("messageTitle", myMessage.getTitle());
      xmlDocument.setParameter("messageMessage", myMessage.getMessage());
    }

    xmlDocument.setParameter("docstatus", strdocstatus);
    if (strWindowId.equals(Purchase_Invoice_Window)) {
      // VOID action: Reverse sales/purchase invoice by default takes today as document date and
      // accounting date.
      xmlDocument.setParameter("voidedDocumentDate", DateTimeData.today(this));
      xmlDocument.setParameter("voidedDocumentAcctDate", DateTimeData.today(this));
      Invoice invoice = (Invoice) OBDal.getInstance()
          .getProxy(Invoice.ENTITY_NAME, strC_Invoice_ID);
      xmlDocument.setParameter("documentDate", OBDateUtils.formatDate(invoice.getInvoiceDate()));
      xmlDocument.setParameter("documentAcctDate",
          OBDateUtils.formatDate(invoice.getAccountingDate()));
    }
    xmlDocument.setParameter("adTableId", stradTableId);
    xmlDocument.setParameter("processId", "111");
    xmlDocument.setParameter("processDescription", "Process Invoice");
    xmlDocument.setParameter("docaction", (strdocaction.equals("--") ? "CL" : strdocaction));
    FieldProvider[] dataDocAction = ActionButtonUtility.docAction(this, vars, strdocaction, "135",
        strdocstatus, strProcessing, stradTableId);
    xmlDocument.setData("reportdocaction", "liststructure", dataDocAction);
    StringBuffer dact = new StringBuffer();
    if (dataDocAction != null) {
      dact.append("var arrDocAction = new Array(\n");
      for (int i = 0; i < dataDocAction.length; i++) {
        dact.append("new Array(\"" + dataDocAction[i].getField("id") + "\", \""
            + dataDocAction[i].getField("name") + "\", \""
            + dataDocAction[i].getField("description") + "\")\n");
        if (i < dataDocAction.length - 1) {
          dact.append(",\n");
        }
      }
      dact.append(");");
    } else {
      dact.append("var arrDocAction = null");
    }
    xmlDocument.setParameter("array", dact.toString());

    out.println(xmlDocument.print());
    out.close();

  }

  void printPageCreditPaymentGrid(HttpServletResponse response, VariablesSecureApp vars,
      String strC_Invoice_ID, String strdocaction, String strProcessing, String strdocstatus,
      String stradTableId, String strWindowId, String strTabId, Date invoiceDate, String strOrg)
      throws IOException, ServletException {
    log4j.debug("Output: Credit Payment Grid popup");
    String[] discard = { "" };
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_actionButton/CreditPaymentGrid", discard)
        .createXmlDocument();
    xmlDocument.setParameter("css", vars.getTheme());
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("window", strWindowId);
    xmlDocument.setParameter("tab", strTabId);
    xmlDocument.setParameter("adOrgId", strOrg);

    xmlDocument.setParameter("messageType", "SUCCESS");
    xmlDocument.setParameter("messageTitle",
        Utility.messageBD(this, "InvoiceComplete", vars.getLanguage()));

    xmlDocument.setParameter("invoiceGrossAmt",
        dao.getObject(Invoice.class, strC_Invoice_ID).getGrandTotalAmount().toString());

    OBError myMessage = vars.getMessage("ProcessInvoice|CreditPaymentGrid");
    vars.removeMessage("ProcessInvoice|CreditPaymentGrid");
    if (myMessage != null) {
      xmlDocument.setParameter("messageType", myMessage.getType());
      xmlDocument.setParameter("messageTitle", myMessage.getTitle());
      xmlDocument.setParameter("messageMessage", myMessage.getMessage());
    }

    xmlDocument.setParameter("dateDisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("paymentDate",
        Utility.formatDate(invoiceDate, vars.getJavaDateFormat()));

    out.println(xmlDocument.print());
    out.close();

  }

  private void printGrid(HttpServletResponse response, VariablesSecureApp vars, String invoiceId)
      throws IOException, ServletException {
    log4j.debug("Output: Grid with credit payments");

    final Invoice invoice = dao.getObject(Invoice.class, invoiceId);

    String[] discard = {};
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_actionButton/AddCreditPaymentGrid", discard)
        .createXmlDocument();

    xmlDocument.setData("structure", getCreditPayments(invoice));

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private FieldProvider[] getCreditPayments(Invoice invoice) {
    FieldProvider[] data = FieldProviderFactory.getFieldProviderArray(creditPayments);
    String dateFormat = OBPropertiesProvider.getInstance()
        .getOpenbravoProperties()
        .getProperty("dateFormat.java");
    SimpleDateFormat dateFormater = new SimpleDateFormat(dateFormat);

    BigDecimal pendingToPay = invoice.getGrandTotalAmount();
    try {
      OBContext.setAdminMode(true);
      for (int i = 0; i < data.length; i++) {
        FieldProviderFactory.setField(data[i], "finCreditPaymentId", creditPayments.get(i).getId());
        FieldProviderFactory.setField(data[i], "documentNo", creditPayments.get(i).getDocumentNo());
        FieldProviderFactory.setField(data[i], "paymentDescription",
            creditPayments.get(i).getDescription());
        if (creditPayments.get(i).getPaymentDate() != null) {
          FieldProviderFactory.setField(data[i], "documentDate",
              dateFormater.format(creditPayments.get(i).getPaymentDate()).toString());
        }

        final BigDecimal outStandingAmt = creditPayments.get(i)
            .getGeneratedCredit()
            .subtract(creditPayments.get(i).getUsedCredit());
        FieldProviderFactory.setField(data[i], "outstandingAmount", outStandingAmt.toString());

        FieldProviderFactory.setField(data[i], "paymentAmount",
            pendingToPay.compareTo(outStandingAmt) > 0 ? outStandingAmt.toString()
                : (pendingToPay.compareTo(BigDecimal.ZERO) > 0 ? pendingToPay.toString() : ""));
        pendingToPay = pendingToPay.subtract(outStandingAmt);

        FieldProviderFactory.setField(data[i], "finSelectedCreditPaymentId",
            "".equals(data[i].getField("paymentAmount")) ? "" : creditPayments.get(i).getId());
        FieldProviderFactory.setField(data[i], "rownum", String.valueOf(i));
      }
    } finally {
      OBContext.restorePreviousMode();
    }

    return data;
  }

  private boolean isInvoiceWithPayments(Invoice invoice) {
    for (FIN_PaymentSchedule ps : OBDao
        .getFilteredCriteria(FIN_PaymentSchedule.class,
            Restrictions.eq(FIN_PaymentSchedule.PROPERTY_INVOICE, invoice))
        .list()) {
      for (FIN_PaymentDetailV pdv : OBDao
          .getFilteredCriteria(FIN_PaymentDetailV.class,
              Restrictions.eq(FIN_PaymentDetailV.PROPERTY_INVOICEPAYMENTPLAN, ps))
          .list()) {
        if (pdv.getPayment() != null && !"RPVOID".equals(pdv.getPayment().getStatus())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if the invoice business partner has defined a default financial account and if the
   * payment method selected in the invoice belongs to the default financial account.
   * 
   * @param invoice
   *          Invoice.
   * @return True if the invoice business partner has defined a default financial account and the
   *         payment method selected in the invoice belongs to the default financial account. False
   *         in other cases.
   */
  private boolean isPaymentMethodConfigured(Invoice invoice) {
    final FIN_FinancialAccount bpFinAccount = invoice.isSalesTransaction()
        ? invoice.getBusinessPartner().getAccount()
        : invoice.getBusinessPartner().getPOFinancialAccount();
    if (bpFinAccount != null) {
      for (final FinAccPaymentMethod bpFinAccPaymentMethod : bpFinAccount
          .getFinancialMgmtFinAccPaymentMethodList()) {
        if (bpFinAccPaymentMethod.getPaymentMethod().equals(invoice.getPaymentMethod())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getServletInfo() {
    return "Servlet to Process Invoice";
  }
}
