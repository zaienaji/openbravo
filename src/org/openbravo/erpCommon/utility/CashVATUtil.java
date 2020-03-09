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
 * All portions are Copyright (C) 2013-2019 Openbravo SLU
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.erpCommon.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBDao;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.ad_forms.AcctSchema;
import org.openbravo.erpCommon.ad_forms.AcctServer;
import org.openbravo.erpCommon.ad_forms.DocLineCashVATReady_PaymentTransactionReconciliation;
import org.openbravo.erpCommon.ad_forms.DocTax;
import org.openbravo.erpCommon.ad_forms.Fact;
import org.openbravo.erpCommon.ad_forms.FactLine;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceTax;
import org.openbravo.model.common.invoice.InvoiceTaxCashVAT;
import org.openbravo.model.common.invoice.InvoiceTaxCashVAT_V;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentDetail;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentSchedule;
import org.openbravo.model.financialmgmt.tax.TaxRate;

public class CashVATUtil {

  private static Logger log4j = LogManager.getLogger();

  public static final BigDecimal _100 = new BigDecimal("100");
  /* Defaulted a big value to avoid precision issues */
  private static final int CASHVAT_PERCENTAGE_PRECISION = 15;

  /**
   * Returns the associated legal entity Cash VAT configuration. Useful for sales flows
   * 
   * @param strOrgId
   *          organization id
   * @return "Y", "N" or null if not found
   */
  public static String getOrganizationIsCashVAT(final String strOrgId) {
    try {
      OBContext.setAdminMode(true);
      final Organization org = OBDal.getInstance().get(Organization.class, strOrgId);
      final Organization legalEntity = OBContext.getOBContext()
          .getOrganizationStructureProvider(org.getClient().getId())
          .getLegalEntity(org);
      if (legalEntity != null && legalEntity.getOrganizationInformationList() != null
          && !legalEntity.getOrganizationInformationList().isEmpty()) {
        return legalEntity.getOrganizationInformationList().get(0).isCashVAT() ? "Y" : "N";
      }
    } catch (final Exception e) {
      log4j.error("Error getting organization'" + strOrgId + "' cash vat. Returning null", e);
    } finally {
      OBContext.restorePreviousMode();
    }

    return null;
  }

  /**
   * Returns the associated legal entity Double Cash Criteria configuration. Useful for purchase
   * flows
   * 
   * @param strOrgId
   *          organization id
   * @return "Y", "N" or null if not found
   */
  public static String getOrganizationIsDoubleCash(final String strOrgId) {
    try {
      OBContext.setAdminMode(true);
      final Organization org = OBDal.getInstance().get(Organization.class, strOrgId);
      final Organization legalEntity = OBContext.getOBContext()
          .getOrganizationStructureProvider(org.getClient().getId())
          .getLegalEntity(org);
      if (legalEntity != null && legalEntity.getOrganizationInformationList() != null
          && !legalEntity.getOrganizationInformationList().isEmpty()) {
        return legalEntity.getOrganizationInformationList().get(0).isDoubleCash() ? "Y" : "N";
      }
    } catch (final Exception e) {
      log4j.error("Error getting organization'" + strOrgId + "' double cash. Returning null", e);
    } finally {
      OBContext.restorePreviousMode();
    }

    return null;
  }

  /**
   * Returns the Cash VAT configuration for the given Vendor (Business Partner)
   * 
   * @param strBPId
   *          Vendor (c_bpartner_id)
   * @return "Y", "N" or null if not found
   */
  public static String getBusinessPartnerIsCashVAT(final String strBPId) {
    try {
      OBContext.setAdminMode(true);
      final BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class, strBPId);
      if (bp != null) {
        return bp.isCashVAT() ? "Y" : "N";
      }
    } catch (final Exception e) {
      log4j.error("Error getting business partner'" + strBPId + "' cash vat. Returning null", e);
    } finally {
      OBContext.restorePreviousMode();
    }

    return null;
  }

  /**
   * It returns true if Organization's and Business Partner's countries are different, which implies
   * a non Cash VAT document.
   * 
   * If Organization has no address, it tries to get its legal entity address instead.
   * 
   * @param strOrgId
   *          Organization Id
   * @param strBPartnerLocationId
   *          Business Partner Location Id from the document (invoice, order, etc.)
   * @return It returns true if Organization's and Business Partner's countries are different
   */
  public static boolean isForcedNonCashVAT(final String strOrgId,
      final String strBPartnerLocationId) {
    String orgCountryId = null;
    String bpCountryId = null;
    try {
      OBContext.setAdminMode(true);
      final Organization org = OBDal.getInstance().get(Organization.class, strOrgId);
      try {
        orgCountryId = org.getOrganizationInformationList()
            .get(0)
            .getLocationAddress()
            .getCountry()
            .getId();
      } catch (Exception noOrgLocation) {
        final Organization legalEntity = OBContext.getOBContext()
            .getOrganizationStructureProvider(org.getClient().getId())
            .getLegalEntity(org);
        legalEntity.getOrganizationInformationList()
            .get(0)
            .getLocationAddress()
            .getCountry()
            .getId();
      }
      bpCountryId = OBDal.getInstance()
          .get(Location.class, strBPartnerLocationId)
          .getLocationAddress()
          .getCountry()
          .getId();
    } catch (Exception noLocationInOrgOrBP) {
      // OrgCountryId or bpCountryId not yet set. Continue with the flow
    } finally {
      OBContext.restorePreviousMode();
    }

    return (orgCountryId != null && bpCountryId != null && !orgCountryId.equals(bpCountryId));
  }

  /**
   * Returns "Y" if the combination of these parameters represents a Cash VAT transaction.
   * 
   * It first checks whether the organization's and business partner's location country is
   * different. In this case it returns "N", because Cash VAT transactions are only valid within the
   * same country. <br>
   * 
   * For sales flow, it checks whether the organization is declared as Cash VAT. In this case it
   * returns "Y". <br>
   * 
   * For purchase flow, it first checks if the organization is declared as Cash VAT and Double cash
   * criteria. In this case it returns "Y"; otherwise it returns the business partner Cash VAT
   * configuration
   * 
   * @param strIsSOTrx
   *          is Sales transaction ("Y" or "N")
   * @param strOrgId
   *          organization ID
   * @param strBPartnerId
   *          business partner ID
   * @param strBPartnerLocationId
   *          business partner location ID (from the document: order, invoice, etc.)
   * @return "Y" for Cash VAT transactions, otherwise returns "N"
   */
  public static String isCashVAT(final String strIsSOTrx, final String strOrgId,
      final String strBPartnerId, final String strBPartnerLocationId) {
    final boolean isForcedNonCashVAT = isForcedNonCashVAT(strOrgId, strBPartnerLocationId);

    if (isForcedNonCashVAT) {
      // The Organization's and Business Partner's countries are different. It implies Cash VAT = N
      return "N";
    } else {
      final String orgCashVAT = CashVATUtil.getOrganizationIsCashVAT(strOrgId);
      if (StringUtils.equals("Y", strIsSOTrx)) {
        // Sales flow only (from the organization)
        return orgCashVAT;
      } else {
        // Purchase flow
        final String orgDoubleCash = CashVATUtil.getOrganizationIsDoubleCash(strOrgId);
        if (StringUtils.equals("Y", orgCashVAT) && StringUtils.equals("Y", orgDoubleCash)) {
          // from Organization Double Cash Criteria
          return "Y";
        } else {
          // from Business Partner
          return getBusinessPartnerIsCashVAT(strBPartnerId);
        }
      }
    }
  }

  /**
   * Creates the records into the Cash VAT management table (InvoiceTaxCashVAT), calculating the
   * percentage paid/collected tax amount and taxable amount. Only for cash vat tax rates.
   * 
   * If the invoice has been already settled in a Manual Cash VAT Settlement, we don't create a new
   * Cash VAT management record
   * 
   */
  public static void createInvoiceTaxCashVAT(final FIN_PaymentDetail paymentDetail,
      final FIN_PaymentSchedule paymentSchedule, final BigDecimal amount) {
    try {
      OBContext.setAdminMode(true);
      final Invoice invoice = paymentSchedule.getInvoice();
      if (invoice != null && invoice.isCashVAT()) {
        // A previous cash vat line with this payment detail means we are reactivating the payment.
        // In this case we delete the line
        final List<InvoiceTaxCashVAT> previousITCashVATs = getInvoiceTaxCashVAT(paymentDetail);
        if (previousITCashVATs != null && !previousITCashVATs.isEmpty()) {
          for (InvoiceTaxCashVAT previousITCV : previousITCashVATs) {
            OBDal.getInstance().remove(previousITCV);
          }
        } else if (!hasManualCashVATSettlement(invoice)) {
          final boolean calculateAmountsBasedOnPercentage;
          BigDecimal percentage = null; /* Calculate it later on */
          final BigDecimal outstandingAmt = invoice.getOutstandingAmount();
          if (outstandingAmt.compareTo(amount) == 0) {
            // We are fully paying the invoice. We need to subtract amounts instead of calculating
            // them on the fly
            calculateAmountsBasedOnPercentage = false;
          } else {
            // Calculate amounts based on the paid percentage
            calculateAmountsBasedOnPercentage = true;
            final boolean isReversal = invoice.getDocumentType().isReversal();
            final BigDecimal grandTotalAmt = isReversal ? invoice.getGrandTotalAmount().negate()
                : invoice.getGrandTotalAmount();
            percentage = amount.multiply(_100)
                .divide(grandTotalAmt, CASHVAT_PERCENTAGE_PRECISION, RoundingMode.HALF_UP);
          }

          for (final InvoiceTax invoiceTax : invoice.getInvoiceTaxList()) {
            if (invoiceTax.getTax().isCashVAT()) {
              final InvoiceTaxCashVAT iTCashVAT = OBProvider.getInstance()
                  .get(InvoiceTaxCashVAT.class);
              iTCashVAT.setOrganization(invoiceTax.getOrganization());
              iTCashVAT.setInvoiceTax(invoiceTax);
              iTCashVAT.setFINPaymentDetail(paymentDetail);
              final BigDecimal taxAmount;
              final BigDecimal taxableAmount;
              if (calculateAmountsBasedOnPercentage) {
                taxAmount = calculatePercentageAmount(percentage, invoiceTax.getTaxAmount(),
                    invoice.getCurrency());
                taxableAmount = calculatePercentageAmount(percentage, invoiceTax.getTaxableAmount(),
                    invoice.getCurrency());
              } else {
                final Map<String, BigDecimal> outstandingAmounts = getTotalOutstandingCashVATAmount(
                    invoiceTax.getId());
                percentage = outstandingAmounts.get("percentage");
                taxAmount = outstandingAmounts.get("taxAmt");
                taxableAmount = outstandingAmounts.get("taxableAmt");
              }
              iTCashVAT.setPercentage(percentage);
              iTCashVAT.setTaxAmount(taxAmount);
              iTCashVAT.setTaxableAmount(taxableAmount);
              invoiceTax.getInvoiceTaxCashVATList().add(iTCashVAT);
              OBDal.getInstance().save(invoiceTax);
              OBDal.getInstance().save(iTCashVAT);
            }
          }
        }
        OBDal.getInstance().flush();
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Returns true if the invoice has a manual cash vat settlement associated
   */
  public static boolean hasManualCashVATSettlement(final Invoice invoice) {
    try {
      OBContext.setAdminMode(true);

      final StringBuffer hql = new StringBuffer();
      hql.append(" select itcv.id ");
      hql.append(" from InvoiceTaxCashVAT itcv ");
      hql.append(" inner join itcv.invoiceTax it ");
      hql.append(" where it.invoice.id = :invoiceId ");
      hql.append(" and itcv.isManualSettlement = true ");

      final Session session = OBDal.getInstance().getSession();
      final Query<String> query = session.createQuery(hql.toString(), String.class);
      query.setParameter("invoiceId", invoice.getId());
      query.setMaxResults(1);

      return !query.list().isEmpty();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Gets the InvoiceTaxCashVAT records linked to the payment detail
   */
  public static List<InvoiceTaxCashVAT> getInvoiceTaxCashVAT(
      final FIN_PaymentDetail paymentDetail) {
    try {
      OBContext.setAdminMode(true);
      return OBDao
          .getFilteredCriteria(InvoiceTaxCashVAT.class,
              Restrictions.eq(InvoiceTaxCashVAT.PROPERTY_FINPAYMENTDETAIL, paymentDetail))
          .list();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Returns the outstanding percentage, tax amount and taxable amount by subtracting the total
   * percentage (100%), total tax amount and total taxable amount with the amounts registered into
   * the InvoiceTaxCashVAT.
   * 
   * Use this method when the invoice is fully paid to avoid rounding issues with on the fly
   * calculations based on the percentage of the invoice that has been paid/collected
   * 
   */
  public static Map<String, BigDecimal> getTotalOutstandingCashVATAmount(
      final String cInvoiceTaxID) {
    try {
      OBContext.setAdminMode(true);
      final StringBuffer hql = new StringBuffer();
      hql.append(" select 100 - sum(coalesce(itcv." + InvoiceTaxCashVAT_V.PROPERTY_PERCENTAGE
          + ", 0)) as percentage, ");
      hql.append("  max(it." + InvoiceTax.PROPERTY_TAXABLEAMOUNT + ") - sum(coalesce(itcv."
          + InvoiceTaxCashVAT_V.PROPERTY_TAXABLEAMOUNT + ", 0)) as taxableAmt, ");
      hql.append("  max(it." + InvoiceTax.PROPERTY_TAXAMOUNT + ") - sum(coalesce(itcv."
          + InvoiceTaxCashVAT_V.PROPERTY_TAXAMOUNT + ", 0)) as taxAmt ");

      hql.append(" from " + InvoiceTaxCashVAT_V.ENTITY_NAME + " as itcv ");
      hql.append(" right outer join itcv." + InvoiceTaxCashVAT_V.PROPERTY_INVOICETAX + " as it ");
      hql.append(" where it." + InvoiceTax.PROPERTY_ID + "= :cInvoiceTaxID ");
      hql.append(" and coalesce(itcv." + InvoiceTaxCashVAT_V.PROPERTY_CANCELED + ", 'N') = 'N' ");

      final Session session = OBDal.getInstance().getSession();
      final Query<Object[]> query = session.createQuery(hql.toString(), Object[].class);
      query.setParameter("cInvoiceTaxID", cInvoiceTaxID);

      final Map<String, BigDecimal> result = new HashMap<String, BigDecimal>();

      final Object[] o = query.uniqueResult();
      result.put("percentage", (BigDecimal) o[0]);
      result.put("taxableAmt", (BigDecimal) o[1]);
      result.put("taxAmt", (BigDecimal) o[2]);
      return result;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Returns the total percentage that should be applied to the cash vat regime that comes from a
   * prepayment, i.e. from an (partially or totally) paid/collected order and from a payment created
   * at invoicing time (see {@link FIN_PaymentDetail#PROPERTY_ISPAIDATINVOICING}). This percentage
   * must be directly registered into the final tax account instead of the transitory tax account as
   * usual, because this part of the invoice has been paid from the order
   */
  public static BigDecimal calculatePrepaidPercentageForCashVATTax(final String cTaxID,
      final String cInvoiceId) {
    try {
      OBContext.setAdminMode(true);
      final StringBuffer hql = new StringBuffer();
      hql.append(" select coalesce(sum(" + InvoiceTaxCashVAT_V.PROPERTY_PERCENTAGE + "), 0) ");
      hql.append(" from " + InvoiceTaxCashVAT_V.ENTITY_NAME);
      hql.append(
          " where " + InvoiceTaxCashVAT_V.PROPERTY_TAX + "." + TaxRate.PROPERTY_ID + " = :taxId ");
      hql.append(" and " + InvoiceTaxCashVAT_V.PROPERTY_INVOICE + "." + Invoice.PROPERTY_ID
          + " = :invoiceId ");
      hql.append(" and " + InvoiceTaxCashVAT_V.PROPERTY_CANCELED + " = false ");
      hql.append(" and (" + InvoiceTaxCashVAT_V.PROPERTY_ISPREPAYMENT + " = true ");
      hql.append("      or " + InvoiceTaxCashVAT_V.PROPERTY_ISPAIDATINVOICING + " = true) ");
      hql.append(" group by " + InvoiceTaxCashVAT_V.PROPERTY_TAX + "." + TaxRate.PROPERTY_ID + ", "
          + InvoiceTaxCashVAT_V.PROPERTY_INVOICE + "." + Invoice.PROPERTY_ID);

      final Session session = OBDal.getInstance().getSession();
      final Query<BigDecimal> query = session.createQuery(hql.toString(), BigDecimal.class);
      query.setParameter("taxId", cTaxID);
      query.setParameter("invoiceId", cInvoiceId);
      final BigDecimal percentage = query.uniqueResult();
      return percentage == null ? BigDecimal.ZERO : percentage;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Generic method to calculate the percentage of an amount (totalAmt) with the currency's standard
   * precision
   * 
   * @param percentage
   *          percentage to apply for the totalAmt
   * @param totalAmt
   *          total amount (represents 100%)
   * @param cCurrencyId
   *          currency ID
   * @return percentage * totalAmt / 100, rounded to the currency's standard precision
   */
  public static BigDecimal calculatePercentageAmount(final BigDecimal percentage,
      final BigDecimal totalAmt, final String cCurrencyId) {
    try {
      OBContext.setAdminMode(true);
      final Currency currency = OBDal.getInstance().get(Currency.class, cCurrencyId);
      return calculatePercentageAmount(percentage, totalAmt, currency);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Generic method to calculate the percentage of an amount (totalAmt) with the currency's standard
   * precision
   * 
   * @param percentage
   *          percentage to apply for the totalAmt
   * @param totalAmt
   *          total amount (represents 100%)
   * @param currency
   *          currency
   * @return percentage * totalAmt / 100, rounded to the currency's standard precision
   */
  public static BigDecimal calculatePercentageAmount(final BigDecimal percentage,
      final BigDecimal totalAmt, final Currency currency) {
    try {
      OBContext.setAdminMode(true);
      if (currency != null) {
        int precission = currency.getStandardPrecision().intValue();
        return percentage.multiply(totalAmt).divide(_100, precission, RoundingMode.HALF_UP);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    throw new RuntimeException("CashVATUtil.calculatePercentageAmount(), wrong parameters :"
        + percentage + ", " + totalAmt + ", " + currency);
  }

  /**
   * @deprecated use
   *             {@link #createFactCashVAT(AcctSchema, ConnectionProvider, Fact, String, DocLineCashVATReady_PaymentTransactionReconciliation, Invoice, String, String)}
   *             instead
   */
  @Deprecated
  public static String createFactCashVAT(AcctSchema as, ConnectionProvider conn, Fact fact,
      String Fact_Acct_Group_ID, DocLineCashVATReady_PaymentTransactionReconciliation line,
      Invoice invoice, final String documentType, final String cCurrencyID, final String SeqNo) {
    return createFactCashVAT(as, conn, fact, Fact_Acct_Group_ID, line, invoice, documentType,
        SeqNo);
  }

  /**
   * Create the accounting fact lines related to Cash VAT for payments, transactions and
   * reconciliations that come from a cash VAT invoice
   * 
   */
  public static String createFactCashVAT(AcctSchema as, ConnectionProvider conn, Fact fact,
      String Fact_Acct_Group_ID, DocLineCashVATReady_PaymentTransactionReconciliation line,
      Invoice invoice, final String documentType, final String SeqNo) {
    try {
      if (invoice.isCashVAT() && !line.getInvoiceTaxCashVAT_V_IDs().isEmpty()) {
        FactLine factLine2 = null;
        for (final String itcvId : line.getInvoiceTaxCashVAT_V_IDs()) {
          InvoiceTaxCashVAT_V itcv = OBDal.getInstance().get(InvoiceTaxCashVAT_V.class, itcvId);
          final TaxRate tax = itcv.getInvoiceTax().getTax();
          Invoice inv = itcv.getInvoiceTax().getInvoice();
          if (tax.isCashVAT() && StringUtils.equals(inv.getId(), invoice.getId())) {
            final BigDecimal taxAmt = itcv.getTaxAmount();
            if (taxAmt.compareTo(BigDecimal.ZERO) != 0) {
              String dateFormatString = OBPropertiesProvider.getInstance()
                  .getOpenbravoProperties()
                  .getProperty("dateFormat.java");
              SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString);
              BigDecimal taxAmtConverted = fact.getM_doc()
                  .convertAmount(taxAmt, invoice.isSalesTransaction(),
                      dateFormat.format(invoice.getAccountingDate()), AcctServer.TABLEID_Invoice,
                      invoice.getId(), invoice.getCurrency().getId(), as.m_C_Currency_ID, line, as,
                      fact, Fact_Acct_Group_ID, nextSeqNo(SeqNo), conn);
              if (taxAmtConverted.compareTo(BigDecimal.ZERO) != 0) {
                String taxAmountConverted = taxAmtConverted.toString();
                final DocTax m_tax = new DocTax(tax.getId(), tax.getName(),
                    tax.getRate().toString(), itcv.getInvoiceTax().getTaxableAmount().toString(),
                    itcv.getTaxAmount().toString(), tax.isNotTaxdeductable(),
                    tax.isTaxdeductable());
                final String invoicedocumentType = invoice.getDocumentType().getDocumentCategory();
                final boolean isReversal = invoice.getDocumentType().isReversal();
                // ARI, ARF, ARI_RM
                if (invoicedocumentType.equals(AcctServer.DOCTYPE_ARInvoice)
                    || invoicedocumentType.equals(AcctServer.DOCTYPE_ARProForma)
                    || invoicedocumentType.equals(AcctServer.DOCTYPE_RMSalesInvoice)) {
                  if (isReversal) {
                    final FactLine factLine1 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxDue_Trans, as, conn),
                        invoice.getCurrency().getId(), "", taxAmountConverted, Fact_Acct_Group_ID,
                        nextSeqNo(SeqNo), documentType, conn);
                    factLine2 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxDue, as, conn),
                        invoice.getCurrency().getId(), taxAmt.toString(), "", Fact_Acct_Group_ID,
                        nextSeqNo(factLine1.m_SeqNo), documentType, conn);
                  } else {
                    final FactLine factLine1 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxDue_Trans, as, conn),
                        invoice.getCurrency().getId(), taxAmountConverted, "", Fact_Acct_Group_ID,
                        nextSeqNo(SeqNo), documentType, conn);
                    factLine2 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxDue, as, conn),
                        invoice.getCurrency().getId(), "", taxAmt.toString(), Fact_Acct_Group_ID,
                        nextSeqNo(factLine1.m_SeqNo), documentType, conn);
                  }
                } // ARC
                else if (invoicedocumentType.equals(AcctServer.DOCTYPE_ARCredit)) {
                  final FactLine factLine1 = fact.createLine(line,
                      m_tax.getAccount(DocTax.ACCTTYPE_TaxDue_Trans, as, conn),
                      invoice.getCurrency().getId(), "", taxAmountConverted, Fact_Acct_Group_ID,
                      nextSeqNo(SeqNo), documentType, conn);
                  factLine2 = fact.createLine(line,
                      m_tax.getAccount(DocTax.ACCTTYPE_TaxDue, as, conn),
                      invoice.getCurrency().getId(), taxAmt.toString(), "", Fact_Acct_Group_ID,
                      nextSeqNo(factLine1.m_SeqNo), documentType, conn);
                }
                // API
                else if (invoicedocumentType.equals(AcctServer.DOCTYPE_APInvoice)) {
                  if (isReversal) {
                    final FactLine factLine1 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxCredit_Trans, as, conn),
                        invoice.getCurrency().getId(), taxAmountConverted, "", Fact_Acct_Group_ID,
                        nextSeqNo(SeqNo), documentType, conn);
                    factLine2 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxCredit, as, conn),
                        invoice.getCurrency().getId(), "", taxAmt.toString(), Fact_Acct_Group_ID,
                        nextSeqNo(factLine1.m_SeqNo), documentType, conn);
                  } else {
                    final FactLine factLine1 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxCredit_Trans, as, conn),
                        invoice.getCurrency().getId(), "", taxAmountConverted, Fact_Acct_Group_ID,
                        nextSeqNo(SeqNo), documentType, conn);
                    factLine2 = fact.createLine(line,
                        m_tax.getAccount(DocTax.ACCTTYPE_TaxCredit, as, conn),
                        invoice.getCurrency().getId(), taxAmt.toString(), "", Fact_Acct_Group_ID,
                        nextSeqNo(factLine1.m_SeqNo), documentType, conn);
                  }
                }
                // APC
                else if (invoicedocumentType.equals(AcctServer.DOCTYPE_APCredit)) {
                  final FactLine factLine1 = fact.createLine(line,
                      m_tax.getAccount(DocTax.ACCTTYPE_TaxCredit_Trans, as, conn),
                      invoice.getCurrency().getId(), taxAmountConverted, "", Fact_Acct_Group_ID,
                      nextSeqNo(SeqNo), documentType, conn);
                  factLine2 = fact.createLine(line,
                      m_tax.getAccount(DocTax.ACCTTYPE_TaxCredit, as, conn),
                      invoice.getCurrency().getId(), "", taxAmt.toString(), Fact_Acct_Group_ID,
                      nextSeqNo(factLine1.m_SeqNo), documentType, conn);
                }
              }
            }
          }
        }
        if (factLine2 != null) {
          return factLine2.m_SeqNo;
        }
      }
    } catch (ServletException e) {
      log4j.error("Error ocurring posting cashVAT", e);
    }
    return SeqNo;
  }

  private static String nextSeqNo(String oldSeqNo) {
    BigDecimal seqNo = new BigDecimal(oldSeqNo);
    return (seqNo.add(new BigDecimal("10"))).toString();

  }
}
