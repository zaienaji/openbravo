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
 * All portions are Copyright (C) 2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.advpaymentmngt.process;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.financialmgmt.accounting.AccountingFact;
import org.openbravo.model.financialmgmt.accounting.coa.AccountingCombination;
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchema;
import org.openbravo.model.financialmgmt.payment.FIN_FinaccTransaction;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.model.financialmgmt.payment.FinAccPaymentMethod;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

public class RecordID2Filling extends DalBaseProcess {

  private static final Object TRANSACTION_TABLE_ID = "4D8C3B3C31D1410DA046140C9F024D17";
  private ProcessLogger logger;

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    logger = bundle.getLogger();
    final StringBuilder hqlInvoices = new StringBuilder();
    Set<AcctSchema> schemas = getSchemas();
    Set<String> bpAccounts = new HashSet<String>();
    Set<String> faAccounts = new HashSet<String>();
    for (AcctSchema acctSchema : schemas) {
      bpAccounts.addAll(getBPAccountList(true, acctSchema.getId()));
      bpAccounts.addAll(getBPAccountList(false, acctSchema.getId()));
      faAccounts.addAll(getFAAccountList(true, acctSchema.getId()));
      faAccounts.addAll(getFAAccountList(false, acctSchema.getId()));
    }

    hqlInvoices.append(" update FinancialMgmtAccountingFact as f set f.recordID2 = f.lineID ");
    hqlInvoices.append(" where f.table.id = '318' ");
    hqlInvoices
        .append("   and exists (select 1 from FIN_Payment_Schedule as ps where ps.id = f.lineID)");
    hqlInvoices.append("   and f.account.id in :accounts");
    hqlInvoices.append("   and f.recordID2 is null");
    int numberInvoices = OBDal.getInstance()
        .getSession()
        .createQuery(hqlInvoices.toString())
        .setParameterList("accounts", bpAccounts)
        .executeUpdate();
    logger.logln("Number of invoice entries updated: " + numberInvoices);
    OBDal.getInstance().flush();

    final StringBuilder hqlPayments = new StringBuilder();
    hqlPayments.append(" update FinancialMgmtAccountingFact as f set f.recordID2 = ("
        + "select case when psd.invoicePaymentSchedule is null then psd.orderPaymentSchedule else psd.invoicePaymentSchedule end "
        + "from FIN_Payment_ScheduleDetail as psd join psd.paymentDetails as pd"
        + " where pd.id = f.lineID)");
    hqlPayments.append("where f.table.id = 'D1A97202E832470285C9B1EB026D54E2' ");
    hqlPayments.append(" and f.account.id in :accounts");
    hqlPayments.append(" and f.recordID2 is null");
    int numberPayments = OBDal.getInstance()
        .getSession()
        .createQuery(hqlPayments.toString())
        .setParameterList("accounts", bpAccounts)
        .executeUpdate();

    logger.logln("Number of payment entries updated: " + numberPayments);
    OBDal.getInstance().flush();

    // Updates in transit accounts (record_id2)
    final StringBuilder hqlPaymentsInTransit = new StringBuilder();
    hqlPaymentsInTransit
        .append(" update FinancialMgmtAccountingFact as f set f.recordID2 = f.recordID");
    hqlPaymentsInTransit.append(" where f.lineID is null ");
    hqlPaymentsInTransit.append(" and f.recordID2 is null");
    hqlPaymentsInTransit
        .append(" and exists (select 1 from FIN_Payment as p where p.id = f.recordID "
            + "and not exists( select 1 from FIN_Payment_Credit as pc where pc.creditPaymentUsed = p))");
    int numberPaymentsInTransit = OBDal.getInstance()
        .getSession()
        .createQuery(hqlPaymentsInTransit.toString())
        .executeUpdate();

    logger.logln("Number of payment entries updated (In Transit): " + numberPaymentsInTransit);
    OBDal.getInstance().flush();

    final StringBuilder hqlTrxRec = new StringBuilder();
    hqlTrxRec.append(" select f from FinancialMgmtAccountingFact as f ");
    hqlTrxRec.append(" where f.recordID2 is null ");
    hqlTrxRec
        .append("   and exists (select 1 from FIN_Finacc_Transaction as t where t.id = f.lineID)");
    hqlTrxRec.append("   and account.id in :accounts");
    Query<AccountingFact> query = OBDal.getInstance()
        .getSession()
        .createQuery(hqlTrxRec.toString(), AccountingFact.class);

    query.setParameterList("accounts", bpAccounts);
    int i = 0;
    int j = 0;
    query.setFetchSize(1000);
    final ScrollableResults scroller = query.scroll(ScrollMode.FORWARD_ONLY);
    try {
      while (scroller.next()) {
        OBContext.setAdminMode(false);
        final AccountingFact accountingEntry = (AccountingFact) scroller.get()[0];
        try {
          FIN_FinaccTransaction trx = OBDal.getInstance()
              .get(FIN_FinaccTransaction.class, accountingEntry.getLineID());
          if (trx != null && trx.getFinPayment() != null) {
            if (trx.getFinPayment().getFINPaymentDetailList().size() == 1 && (trx.getFinPayment()
                .getFINPaymentDetailList()
                .get(0)
                .getFINPaymentScheduleDetailList()
                .get(0)
                .getInvoicePaymentSchedule() != null
                || trx.getFinPayment()
                    .getFINPaymentDetailList()
                    .get(0)
                    .getFINPaymentScheduleDetailList()
                    .get(0)
                    .getOrderPaymentSchedule() != null)) {
              accountingEntry.setRecordID2(trx.getFinPayment()
                  .getFINPaymentDetailList()
                  .get(0)
                  .getFINPaymentScheduleDetailList()
                  .get(0)
                  .getInvoicePaymentSchedule() == null
                      ? trx.getFinPayment()
                          .getFINPaymentDetailList()
                          .get(0)
                          .getFINPaymentScheduleDetailList()
                          .get(0)
                          .getOrderPaymentSchedule()
                          .getId()
                      : trx.getFinPayment()
                          .getFINPaymentDetailList()
                          .get(0)
                          .getFINPaymentScheduleDetailList()
                          .get(0)
                          .getInvoicePaymentSchedule()
                          .getId());
              OBDal.getInstance().save(accountingEntry);
              j++;
            } else {
              FIN_PaymentScheduleDetail psd = getOrderedPSDs(trx.getFinPayment())
                  .get(getAccountingEntryPosition(accountingEntry, bpAccounts));
              accountingEntry.setRecordID2(
                  psd.getPaymentDetails().isPrepayment() ? psd.getOrderPaymentSchedule().getId()
                      : psd.getInvoicePaymentSchedule().getId());
              OBDal.getInstance().save(accountingEntry);
              j++;
            }
          }
          // clear the session every 100 records
          if ((i % 100) == 0 && i != 0) {
            logger.logln(String.valueOf(i + 1) + " - " + String.valueOf(j));
            logger.logln(String.valueOf(i + 1) + " - " + String.valueOf(j));
            OBDal.getInstance().flush();
            OBDal.getInstance().getSession().clear();
          }
          i++;
        } catch (Exception e) {
          logger.logln("Entry not updated: " + accountingEntry.getId());
          continue;
        }
      }
      OBDal.getInstance().flush();
      OBDal.getInstance().getSession().clear();
    } finally {
      OBContext.restorePreviousMode();
      scroller.close();
    }
    // Update Transactions and Reconciliations
    final StringBuilder hqlTrxRecInTransit = new StringBuilder();
    hqlTrxRecInTransit.append(" select f from FinancialMgmtAccountingFact as f ");
    hqlTrxRecInTransit.append(" where f.recordID2 is null ");
    hqlTrxRecInTransit.append(
        "   and (exists (select 1 from FIN_Finacc_Transaction as t where t.id = f.lineID) or (f.lineID is null and f.table.id = '"
            + TRANSACTION_TABLE_ID + "'))");
    hqlTrxRecInTransit.append("   and account.id in :accounts");
    Query<AccountingFact> queryInTransit = OBDal.getInstance()
        .getSession()
        .createQuery(hqlTrxRecInTransit.toString(), AccountingFact.class);

    queryInTransit.setParameterList("accounts", faAccounts);
    i = 0;
    j = 0;
    queryInTransit.setFetchSize(1000);
    final ScrollableResults scrollerInTransit = queryInTransit.scroll(ScrollMode.FORWARD_ONLY);
    try {
      while (scrollerInTransit.next()) {
        OBContext.setAdminMode(false);
        final AccountingFact accountingEntry = (AccountingFact) scrollerInTransit.get()[0];
        try {
          FIN_FinaccTransaction trx = OBDal.getInstance()
              .get(FIN_FinaccTransaction.class,
                  accountingEntry.getLineID() == null ? accountingEntry.getRecordID()
                      : accountingEntry.getLineID());
          if (trx != null && trx.getFinPayment() != null) {
            // logger.logln("Table: " + accountingEntry.getTable().getName());
            // logger.logln("Accounting entry: ");
            // logger.logln(accountingEntry.getAccountingEntryDescription() + " - "
            // + accountingEntry.getDebit().toString() + " - "
            // + accountingEntry.getCredit().toString());
            // logger.logln("Payment Method: " + trx.getFinPayment().getPaymentMethod().getName());
            // logger.logln("Financial Account: " + trx.getAccount().getName());

            Set<String> paymentAccount = getFAAccountList(trx.getFinPayment().isReceipt(),
                accountingEntry.getAccountingSchema().getId(), trx.getAccount().getId(),
                trx.getFinPayment().getPaymentMethod().getId(), "PAY");
            Set<String> transactionAccount = getFAAccountList(trx.getFinPayment().isReceipt(),
                accountingEntry.getAccountingSchema().getId(), trx.getAccount().getId(),
                trx.getFinPayment().getPaymentMethod().getId(), "TRX");

            // logger.logln("Payment event account: " + paymentAccount);
            // logger.logln("Transaction event account: " + transactionAccount);
            if (paymentAccount.contains(accountingEntry.getAccount().getId())) {
              logger.logln("Use: Payment ID= " + trx.getFinPayment().getId());
              accountingEntry.setRecordID2(trx.getFinPayment().getId());
              OBDal.getInstance().save(accountingEntry);
              j++;
            } else if (transactionAccount.contains(accountingEntry.getAccount().getId())) {
              logger.logln("Use: Transaction ID= " + trx.getId());
              accountingEntry.setRecordID2(trx.getId());
              OBDal.getInstance().save(accountingEntry);
              j++;
            }
          }
          // clear the session every 100 records
          if ((i % 100) == 0 && i != 0) {
            logger.logln(String.valueOf(i + 1) + " - " + String.valueOf(j));
            logger.logln(String.valueOf(i + 1) + " - " + String.valueOf(j));
            OBDal.getInstance().flush();
            OBDal.getInstance().getSession().clear();
          }
          i++;
        } catch (Exception e) {
          logger.logln("Entry not updated: " + accountingEntry.getId());
          continue;
        }
      }
      OBDal.getInstance().flush();
      OBDal.getInstance().getSession().clear();
    } finally {
      OBContext.restorePreviousMode();
      scrollerInTransit.close();
    }

    // Update date balancing
    final StringBuilder hqlDateBalanced = new StringBuilder();
    hqlDateBalanced.append(" update FinancialMgmtAccountingFact as f set f.dateBalanced = "
        + "(select max(f2.accountingDate) from FinancialMgmtAccountingFact as f2 "
        + "where f2.recordID2 = f.recordID2 and f2.accountingSchema = f.accountingSchema and f2.account = f.account group by f2.recordID2 having sum(f2.credit-f2.debit)=0) ");
    hqlDateBalanced.append(" where exists (select 1 from FinancialMgmtAccountingFact as f3 "
        + "where f3.recordID2 = f.recordID2 and f3.accountingSchema = f.accountingSchema and f3.account = f.account group by f3.recordID2 having sum(f3.credit-f3.debit)=0)");
    hqlDateBalanced.append(" and f.dateBalanced is null");
    int numberBalanced = OBDal.getInstance()
        .getSession()
        .createQuery(hqlDateBalanced.toString())
        .executeUpdate();
    logger.logln("Number of date balanced entries: " + numberBalanced);
    OBDal.getInstance().flush();
  }

  private Set<AcctSchema> getSchemas() {
    OBCriteria<AcctSchema> obc = OBDal.getInstance().createCriteria(AcctSchema.class);
    return new HashSet<AcctSchema>(obc.list());
  }

  private int getAccountingEntryPosition(AccountingFact accountingEntry, Set<String> accounts) {
    final StringBuilder hqlString = new StringBuilder();
    hqlString.append(" as f");
    hqlString.append(" where f.account.id in :accounts");
    hqlString.append(" and f.recordID = :recordID");
    hqlString.append(" order by abs(f.debit-f.credit), f.creationDate");
    final OBQuery<AccountingFact> query = OBDal.getInstance()
        .createQuery(AccountingFact.class, hqlString.toString());
    query.setNamedParameter("accounts", accounts);
    query.setNamedParameter("recordID", accountingEntry.getRecordID());
    query.setFilterOnReadableClients(false);
    query.setFilterOnReadableOrganization(false);
    int i = 0;
    for (AccountingFact af : query.list()) {
      if (af.getId().equals(accountingEntry.getId())) {
        return i;
      }
      i = i + 1;
    }
    return i;
  }

  private List<FIN_PaymentScheduleDetail> getOrderedPSDs(FIN_Payment finPayment) {
    final StringBuilder hqlString = new StringBuilder();
    hqlString.append(" as psd join psd.paymentDetails as pd");
    hqlString.append(" where pd.finPayment = :payment");
    hqlString.append(" and pd.gLItem is null");
    hqlString.append(" order by abs(psd.amount), psd.creationDate");
    final OBQuery<FIN_PaymentScheduleDetail> query = OBDal.getInstance()
        .createQuery(FIN_PaymentScheduleDetail.class, hqlString.toString());
    query.setNamedParameter("payment", finPayment);
    return query.list();
  }

  private Set<String> getBPAccountList(boolean isReceipt, String acctSchemaId) {
    Set<String> result = new HashSet<>();
    final StringBuilder hqlString = new StringBuilder();
    if (isReceipt) {
      hqlString.append("select distinct ca.customerReceivablesNo, ca.customerPrepayment");
      hqlString.append(" from CustomerAccounts as ca");
      hqlString.append(" where ca.accountingSchema.id = '" + acctSchemaId + "'");
    } else {
      hqlString.append("select distinct va.vendorPrepayment, va.vendorLiability");// va.vendorServiceLiability,
      hqlString.append(" from VendorAccounts as va");
      hqlString.append(" where va.accountingSchema.id = '" + acctSchemaId + "'");
    }
    final Session session = OBDal.getInstance().getSession();
    final Query<Object[]> query = session.createQuery(hqlString.toString(), Object[].class);
    for (Object[] values : query.list()) {
      for (Object value : values) {
        if (value instanceof AccountingCombination) {
          result.add(((AccountingCombination) value).getAccount().getId());
        }
      }
    }
    return result;
  }

  private Set<String> getFAAccountList(boolean isReceipt, String acctSchemaId) {
    Set<String> result = new HashSet<>();
    final StringBuilder hqlString = new StringBuilder();
    if (isReceipt) {
      hqlString.append("select distinct faa.inTransitPaymentAccountIN");
    } else {
      hqlString.append("select distinct faa.fINOutIntransitAcct");
    }
    hqlString.append(" from FIN_Financial_Account_Acct as faa");
    hqlString.append(" where faa.accountingSchema.id = :acctSchemaId");
    final Session session = OBDal.getInstance().getSession();
    final Query<AccountingCombination> query = session.createQuery(hqlString.toString(),
        AccountingCombination.class);
    query.setParameter("acctSchemaId", acctSchemaId);
    for (AccountingCombination value : query.list()) {
      result.add(value.getAccount().getId());
    }
    final StringBuilder hqlString2 = new StringBuilder();
    if (isReceipt) {
      hqlString2.append("select distinct faa.depositAccount");
    } else {
      hqlString2.append("select distinct faa.withdrawalAccount");
    }
    hqlString2.append(" from FIN_Financial_Account_Acct as faa");
    hqlString2.append(" where faa.accountingSchema.id = :acctSchemaId");
    final Query<AccountingCombination> query2 = session.createQuery(hqlString2.toString(),
        AccountingCombination.class);
    query2.setParameter("acctSchemaId", acctSchemaId);
    for (AccountingCombination value : query2.list()) {
      result.add(value.getAccount().getId());
    }
    return result;
  }

  private Set<String> getFAAccountList(boolean isReceipt, String acctSchemaId,
      String financialAccountId, String paymentMethodId, String table) {
    Set<String> result = new HashSet<>();
    final StringBuilder hqlString = new StringBuilder();
    String use = null;
    if (paymentMethodId != null && !"".equals(paymentMethodId)) {
      FIN_PaymentMethod paymentMethod = OBDal.getInstance()
          .get(FIN_PaymentMethod.class, paymentMethodId);
      use = getUse(paymentMethod, financialAccountId, isReceipt, table);
      if (use == null) {
        return result;
      }
    }
    if (isReceipt) {
      if ("INT".equals(use)) {
        hqlString.append("select distinct faa.inTransitPaymentAccountIN");
      } else if ("DEP".equals(use)) {
        hqlString.append("select distinct faa.depositAccount");
      } else if ("CLE".equals(use)) {
        hqlString.append("select distinct faa.clearedPaymentAccount");
      } else {
        hqlString.append("select distinct faa.inTransitPaymentAccountIN, faa.depositAccount");
      }
    } else {
      if ("INT".equals(use)) {
        hqlString.append("select distinct faa.fINOutIntransitAcct");
      } else if ("WIT".equals(use)) {
        hqlString.append("select distinct faa.withdrawalAccount");
      } else if ("CLE".equals(use)) {
        hqlString.append("select distinct faa.clearedPaymentAccountOUT");
      } else {
        hqlString.append("select distinct faa.inTransitPaymentAccountIN, faa.withdrawalAccount");
      }
    }
    hqlString.append(" from FIN_Financial_Account_Acct as faa");
    hqlString.append(" where faa.accountingSchema.id = :acctSchemaId");
    if (financialAccountId != null && !"".equals(financialAccountId)) {
      hqlString.append(" and faa.account.id = :financialAccountId");
    }
    final Session session = OBDal.getInstance().getSession();
    final Query<Object> query = session.createQuery(hqlString.toString(), Object.class);
    query.setParameter("acctSchemaId", acctSchemaId);
    if (financialAccountId != null && !"".equals(financialAccountId)) {
      query.setParameter("financialAccountId", financialAccountId);
    }
    for (Object resultObject : query.list()) {
      if (resultObject.getClass().isArray()) {
        final Object[] values = (Object[]) resultObject;
        for (Object value : values) {
          if (value instanceof AccountingCombination) {
            result.add(((AccountingCombination) value).getAccount().getId());
          }
        }
      } else {
        if (resultObject instanceof AccountingCombination) {
          result.add(((AccountingCombination) resultObject).getAccount().getId());
        }
      }
    }
    return result;
  }

  private String getUse(FIN_PaymentMethod paymentMethod, String financialAccountId,
      boolean isReceipt, String table) {
    String use = null;
    OBCriteria<FinAccPaymentMethod> obc = OBDal.getInstance()
        .createCriteria(FinAccPaymentMethod.class);
    obc.add(Restrictions.eq(FinAccPaymentMethod.PROPERTY_PAYMENTMETHOD, paymentMethod));
    obc.add(Restrictions.eq(FinAccPaymentMethod.PROPERTY_ACCOUNT,
        OBDal.getInstance().get(FIN_FinancialAccount.class, financialAccountId)));
    obc.setMaxResults(1);
    FinAccPaymentMethod pm = (FinAccPaymentMethod) obc.uniqueResult();
    if (isReceipt) {
      if ("PAY".equals(table)) {
        use = pm.getUponReceiptUse();
      } else if ("TRX".equals(table)) {
        use = pm.getUponDepositUse();
      } else if ("REC".equals(table)) {
        use = pm.getINUponClearingUse();
      }
    } else {
      if ("PAY".equals(table)) {
        use = pm.getUponPaymentUse();
      } else if ("TRX".equals(table)) {
        use = pm.getUponWithdrawalUse();
      } else if ("REC".equals(table)) {
        use = pm.getOUTUponClearingUse();
      }
    }
    return use;
  }
}
