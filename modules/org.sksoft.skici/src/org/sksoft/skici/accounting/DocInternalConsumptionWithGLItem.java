package org.sksoft.skici.accounting;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.NoSuchElementException;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.costing.CostingStatus;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.ad_forms.Account;
import org.openbravo.erpCommon.ad_forms.AcctSchema;
import org.openbravo.erpCommon.ad_forms.DocInternalConsumption;
import org.openbravo.erpCommon.ad_forms.DocInternalConsumptionTemplate;
import org.openbravo.erpCommon.ad_forms.DocLine;
import org.openbravo.erpCommon.ad_forms.Fact;
import org.openbravo.erpCommon.ad_forms.FactLine;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductAccounts;
import org.openbravo.model.financialmgmt.accounting.Costcenter;
import org.openbravo.model.financialmgmt.accounting.coa.AccountingCombination;
import org.openbravo.model.financialmgmt.gl.GLItem;
import org.openbravo.model.financialmgmt.gl.GLItemAccounts;
import org.openbravo.model.marketing.Campaign;
import org.openbravo.model.materialmgmt.cost.ABCActivity;
import org.openbravo.model.materialmgmt.transaction.InternalConsumption;
import org.openbravo.model.materialmgmt.transaction.InternalConsumptionLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.project.Project;

public class DocInternalConsumptionWithGLItem extends DocInternalConsumptionTemplate {

  private static final Logger logger = LogManager.getLogger();

  @Override
  public Fact createFact(DocInternalConsumption docInternalConsumption, AcctSchema as,
      ConnectionProvider conn, Connection con, VariablesSecureApp vars) throws ServletException {

    validateCostingAlgorithm();
    
    InternalConsumption internalConsumption = OBDal.getInstance()
        .get(InternalConsumption.class, docInternalConsumption.Record_ID);
    
    updateInternalConsumptionHeaderAttribute(docInternalConsumption, internalConsumption);
    Currency transactioncurrency = internalConsumption.getClient().getCurrency();
    
    logger.debug("CreateFact header for internal consumption id "+internalConsumption.getId()+
        " currency "+transactioncurrency.getISOCode());
    Fact fact = new Fact(docInternalConsumption, as, Fact.POST_Actual);
    String Fact_Acct_Group_ID = SequenceIdData.getUUID();

    logger.debug("CreateFact - before loop");
    int seqno = 0;
    for (InternalConsumptionLine internalConsumptionLine : internalConsumption
        .getMaterialMgmtInternalConsumptionLineList()) {

      DocLine docLine = createDocLine(internalConsumptionLine, as);

      BigDecimal transactionCost = getInternalConsumptionLineTransactionCost(
          internalConsumptionLine);
      logger.debug("material transaction cost: "+transactionCost);

      DebitCreditAccountPair accountPair = getInternalConsumptionLineAccountPairByAccountingSchema(
          internalConsumptionLine, as);

      logger.debug("CreateFact - before DR - Costs: " + transactionCost);
      Account debitAccount = new Account(conn, accountPair.getDebitAccount().getId());
      createFactLine(fact, debitAccount, docLine, transactioncurrency, transactionCost.toString(), "", 
          Fact_Acct_Group_ID, seqno, docInternalConsumption.DocumentType, conn);

      logger.debug("CreateFact - before CR");
      Account creditAccount = new Account(conn, accountPair.getCreditAccount().getId());
      createFactLine(fact, creditAccount, docLine, transactioncurrency, "", transactionCost.toString(), 
          Fact_Acct_Group_ID, seqno, docInternalConsumption.DocumentType, conn);

    }

    logger.debug("CreateFact - after loop");
    return fact;
  }

  private void validateCostingAlgorithm() {

    boolean isCostingAlgorithmMigratedToNewFramwork = CostingStatus.getInstance().isMigrated();
    if (!isCostingAlgorithmMigratedToNewFramwork) {
      logger.debug("costing algorithm is not migrated yet");
      throw new IllegalStateException("costing algorithm is not migrated yet");
    }
    
    logger.debug("costing algorithm migrated");

  }

  private void updateInternalConsumptionHeaderAttribute(
      DocInternalConsumption docInternalConsumption, InternalConsumption internalConsumption) {

    DocumentType documentType = internalConsumption.getSkiciDoctype();
    
    docInternalConsumption.C_DocType_ID = documentType.getId();
    docInternalConsumption.DocumentType = documentType.getDocumentCategory();
    docInternalConsumption.GL_Category_ID = documentType.getGLCategory().getId();
    
    logger.debug(String.format("document type %s doctype id %s gl category id %s ", 
        docInternalConsumption.DocumentType, docInternalConsumption.C_DocType_ID, docInternalConsumption.GL_Category_ID));

  }

  private FactLine createFactLine(Fact accountingFact, Account account, DocLine docLine,
      Currency currency, String debitAmount, String creditAmount, String factGroup, int seqno,
      String docBaseType, ConnectionProvider conn) {

    FactLine factLine = accountingFact.createLine(docLine, account, currency.getId(), debitAmount,
        creditAmount, factGroup, String.valueOf(nextSeqNo(seqno)), docBaseType, conn);
    if (factLine != null) {
      factLine.setM_Locator_ID(docLine.m_C_LocFrom_ID);
      factLine.setLocationFromLocator(docLine.m_C_LocFrom_ID, true, conn);
    }

    return factLine;
  }

  private BigDecimal getInternalConsumptionLineTransactionCost(
      InternalConsumptionLine internalConsumptionLine) {
    List<MaterialTransaction> internalConsumptionMaterialTransactions = internalConsumptionLine
        .getMaterialMgmtMaterialTransactionList();

    // TODO we consider each internal consumption line linked to one and only one material
    // transaction, hence next possible material transaction ignored
    if (internalConsumptionMaterialTransactions.size() > 1)
      throw new IllegalStateException("there are more than 1 row of material transaction linked to internal consumption line id "
          + internalConsumptionLine.getId());

    MaterialTransaction materialTransction = internalConsumptionMaterialTransactions.get(0);
    logger.debug("material transaction id "+materialTransction.getId());
    if (!materialTransction.isProcessed())
      throw new IllegalStateException(
          "material transaction is not calculated, id: " + materialTransction.getId());
    
    boolean isReturnDocument = internalConsumptionLine.getInternalConsumption().getSkiciDoctype().isReturn();
    if (isReturnDocument)
    	return materialTransction.getTransactionCost().negate();

    return materialTransction.getTransactionCost();
  }

  private DebitCreditAccountPair getInternalConsumptionLineAccountPairByAccountingSchema(
      InternalConsumptionLine internalConsumptionLine, AcctSchema as) {

    AccountingCombination debitAccount = null;
    AccountingCombination creditAccount = null;
    Product consumedProduct = internalConsumptionLine.getProduct();

    for (ProductAccounts productAccount : consumedProduct.getProductAccountsList()) {
      if (!productAccount.getAccountingSchema().getId().equals(as.getC_AcctSchema_ID()))
        continue;

      debitAccount = productAccount.getProductCOGS();
      creditAccount = productAccount.getFixedAsset();

      break;
    }
    
    if (debitAccount == null)
      throw new NoSuchElementException(
          "can not find product inventory asset account for product id: "
              + consumedProduct.getId() +" accounting schema id "+as.m_C_AcctSchema_ID);
    
    if (creditAccount == null)
      throw new NoSuchElementException(
          "can not find product inventory asset account for product id: "
              + consumedProduct.getId() +" accounting schema id "+as.m_C_AcctSchema_ID);
    
    logger.debug(String.format("account pair from product account => debit %s credit %s ", debitAccount.getCombination(), creditAccount.getCombination()));
    
    GLItem expenseGlItem = internalConsumptionLine.getSkiciGlitem();
    if (expenseGlItem != null) {
      for (GLItemAccounts glItemAccount : expenseGlItem.getFinancialMgmtGLItemAccountsList()) {
        if (!glItemAccount.getAccountingSchema().getId().equals(as.getC_AcctSchema_ID()))
          continue;

        // alter expense account into gl item
        debitAccount = glItemAccount.getGlitemDebitAcct();
        if (debitAccount == null)
          throw new NoSuchElementException(
              "can not find debit account for GL Item id: " + expenseGlItem.getId());
        
        logger.debug("account pair altered using gl item "+debitAccount.getCombination());
        break;
      }
    }

    DebitCreditAccountPair accountPair = new DebitCreditAccountPair(debitAccount, creditAccount);
    return accountPair;
  }

  private int nextSeqNo(int oldSeqNo) {
    logger.debug("DocInternalConsumption - oldSeqNo = " + oldSeqNo);
    int SeqNo = oldSeqNo + 10;
    logger.debug("DocInternalConsumption - nextSeqNo = " + SeqNo);
    return SeqNo;
  }

  private DocLine createDocLine(InternalConsumptionLine internalConsumptionLine,
      AcctSchema accountingSchema) {
    DocLine docLine = new DocLine(
        internalConsumptionLine.getInternalConsumption().getSkiciDoctype().getId(),
        internalConsumptionLine.getInternalConsumption().getId(), internalConsumptionLine.getId());
    loadDocLineAttribute(docLine, internalConsumptionLine, accountingSchema);

    return docLine;
  }

  private void loadDocLineAttribute(DocLine docLine,
      InternalConsumptionLine internalConsumptionLine, AcctSchema accountingSchema) {
    InternalConsumption internalConsumption = internalConsumptionLine.getInternalConsumption();
    docLine.m_AD_Org_ID = internalConsumptionLine.getOrganization().getId();
    docLine.m_AD_OrgTrx_ID = internalConsumptionLine.getOrganization().getId();
    Product product = internalConsumptionLine.getProduct();
    docLine.m_M_Product_ID = product.getId();
    docLine.m_C_UOM_ID = product.getUOM().getId();
    docLine.m_C_Glitem_ID = internalConsumptionLine.getSkiciGlitem().getId();
    docLine.m_qty = internalConsumptionLine.getMovementQuantity().toString();
    Project project = internalConsumption.getProject();
    if (project != null)
      docLine.m_C_Project_ID = project.getId();
    Campaign campaign = internalConsumption.getSalesCampaign();
    if (campaign != null)
      docLine.m_C_Campaign_ID = campaign.getId();
    ABCActivity activity = internalConsumption.getActivity();
    if (activity != null)
      docLine.m_C_Activity_ID = activity.getId();
    docLine.m_C_LocFrom_ID = internalConsumptionLine.getStorageBin().getId();
    Costcenter costcenter = internalConsumption.getCostCenter();
    if (costcenter != null)
      docLine.m_C_Costcenter_ID = costcenter.getId();
    docLine.m_Line = internalConsumptionLine.getLineNo().toString();
    docLine.m_description = internalConsumptionLine.getDescription();
    if (docLine.m_description == null || docLine.m_description.isEmpty())
      docLine.m_description = internalConsumption.getDescription();
    docLine.m_C_Currency_ID = accountingSchema.getC_Currency_ID();
  }

}

class DebitCreditAccountPair {
  private AccountingCombination debitAccount;
  private AccountingCombination creditAccount;

  public DebitCreditAccountPair(AccountingCombination debitAccount,
      AccountingCombination creditAccount) {
    this.debitAccount = debitAccount;
    this.creditAccount = creditAccount;
  }

  public AccountingCombination getDebitAccount() {
    return debitAccount;
  }

  public AccountingCombination getCreditAccount() {
    return creditAccount;
  }

}
