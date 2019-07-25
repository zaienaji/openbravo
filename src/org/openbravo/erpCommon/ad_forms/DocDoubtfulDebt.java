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
 * All portions are Copyright (C) 2013-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.erpCommon.ad_forms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.List;

import javax.servlet.ServletException;

import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.FieldProviderFactory;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.AcctSchemaTableDocType;
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchemaTable;
import org.openbravo.model.financialmgmt.payment.DoubtfulDebt;

public class DocDoubtfulDebt extends AcctServer {
  private String SeqNo = "0";

  public DocDoubtfulDebt() {
  }

  public DocDoubtfulDebt(String AD_Client_ID, String AD_Org_ID,
      ConnectionProvider connectionProvider) {
    super(AD_Client_ID, AD_Org_ID, connectionProvider);
  }

  @Override
  public void loadObjectFieldProvider(ConnectionProvider conn, String p_AD_Client_ID, String Id)
      throws ServletException {
    DoubtfulDebt dd = OBDal.getInstance().get(DoubtfulDebt.class, Id);
    FieldProviderFactory[] data = new FieldProviderFactory[1];
    data[0] = new FieldProviderFactory(null);
    FieldProviderFactory.setField(data[0], "AD_Client_ID", dd.getClient().getId());
    FieldProviderFactory.setField(data[0], "AD_Org_ID", dd.getOrganization().getId());
    FieldProviderFactory.setField(data[0], "DocumentNo", dd.getDocumentNo());
    String strAcctDate = OBDateUtils.formatDate(dd.getAccountingDate());
    FieldProviderFactory.setField(data[0], "DateAcct", strAcctDate);
    FieldProviderFactory.setField(data[0], "DateDoc", strAcctDate);
    FieldProviderFactory.setField(data[0], "C_BPartner_ID", dd.getBusinessPartner().getId());
    FieldProviderFactory.setField(data[0], "C_DocType_ID", dd.getDocumentType().getId());
    FieldProviderFactory.setField(data[0], "C_Currency_ID", dd.getCurrency().getId());
    FieldProviderFactory.setField(data[0], "Description", dd.getDescription());
    FieldProviderFactory.setField(data[0], "Amount", dd.getAmount().toString());
    FieldProviderFactory.setField(data[0], "Posted", dd.getPosted());
    FieldProviderFactory.setField(data[0], "Processed", dd.isProcessed() ? "Y" : "N");
    FieldProviderFactory.setField(data[0], "Processing", dd.isProcessNow() ? "Y" : "N");
    // Accounting dimensions
    FieldProviderFactory.setField(data[0], "cProjectId",
        dd.getProject() != null ? dd.getProject().getId() : "");
    FieldProviderFactory.setField(data[0], "cCampaignId",
        dd.getSalesCampaign() != null ? dd.getSalesCampaign().getId() : "");
    FieldProviderFactory.setField(data[0], "cActivityId",
        dd.getActivity() != null ? dd.getActivity().getId() : "");
    FieldProviderFactory.setField(data[0], "user1Id",
        dd.getStDimension() != null ? dd.getStDimension().getId() : "");
    FieldProviderFactory.setField(data[0], "user2Id",
        dd.getNdDimension() != null ? dd.getNdDimension().getId() : "");
    FieldProviderFactory.setField(data[0], "cCostcenterId",
        dd.getCostCenter() != null ? dd.getCostCenter().getId() : "");
    FieldProviderFactory.setField(data[0], "aAssetId",
        dd.getAsset() != null ? dd.getAsset().getId() : "");

    setObjectFieldProvider(data);

  }

  @Override
  public boolean loadDocumentDetails(FieldProvider[] data, ConnectionProvider conn) {
    loadDocumentType();
    Amounts[AMTTYPE_Gross] = data[0].getField("Amount");
    // p_lines = loadLines();
    return true;
  }

  @Override
  public BigDecimal getBalance() {
    return BigDecimal.ZERO;
  }

  @Override
  public Fact createFact(AcctSchema as, ConnectionProvider conn, Connection con,
      VariablesSecureApp vars) throws ServletException {
    String strClassname = "";
    final StringBuilder whereClause = new StringBuilder();
    final Fact fact = new Fact(this, as, Fact.POST_Actual);
    String Fact_Acct_Group_ID = SequenceIdData.getUUID();

    try {
      OBContext.setAdminMode(false);
      whereClause.append(" as astdt ");
      whereClause.append(" where astdt.acctschemaTable.accountingSchema.id = :acctSchemaID");
      whereClause.append(" and astdt.acctschemaTable.table.id = :tableID");
      whereClause.append(" and astdt.documentCategory = :documentType");

      final OBQuery<AcctSchemaTableDocType> obqParameters = OBDal.getInstance()
          .createQuery(AcctSchemaTableDocType.class, whereClause.toString());
      obqParameters.setNamedParameter("acctSchemaID", as.m_C_AcctSchema_ID);
      obqParameters.setNamedParameter("tableID", AD_Table_ID);
      obqParameters.setNamedParameter("documentType", DocumentType);
      final List<AcctSchemaTableDocType> acctSchemaTableDocTypes = obqParameters.list();

      if (acctSchemaTableDocTypes != null && acctSchemaTableDocTypes.size() > 0
          && acctSchemaTableDocTypes.get(0).getCreatefactTemplate() != null) {
        strClassname = acctSchemaTableDocTypes.get(0).getCreatefactTemplate().getClassname();
      }

      if (strClassname.equals("")) {
        final StringBuilder whereClause2 = new StringBuilder();
        whereClause2.append(" as ast ");
        whereClause2.append(" where ast.accountingSchema.id = :acctSchemaID");
        whereClause2.append(" and ast.table.id = :tableID");

        final OBQuery<AcctSchemaTable> obqParameters2 = OBDal.getInstance()
            .createQuery(AcctSchemaTable.class, whereClause2.toString());
        obqParameters2.setNamedParameter("acctSchemaID", as.m_C_AcctSchema_ID);
        obqParameters2.setNamedParameter("tableID", AD_Table_ID);
        final List<AcctSchemaTable> acctSchemaTables = obqParameters2.list();
        if (acctSchemaTables != null && acctSchemaTables.size() > 0
            && acctSchemaTables.get(0).getCreatefactTemplate() != null) {
          strClassname = acctSchemaTables.get(0).getCreatefactTemplate().getClassname();
        }
      }
      if (!strClassname.equals("")) {
        try {
          DocDoubtfulDebtTemplate newTemplate = (DocDoubtfulDebtTemplate) Class
              .forName(strClassname)
              .getDeclaredConstructor()
              .newInstance();
          return newTemplate.createFact(this, as, conn, con, vars);
        } catch (Exception e) {
          log4j.error("Error while creating new instance for DocUnbilledRevenueTemplate - ", e);
        }
      }
      DoubtfulDebt dd = OBDal.getInstance().get(DoubtfulDebt.class, Record_ID);
      BigDecimal bpAmountConverted = convertAmount(new BigDecimal(Amounts[AMTTYPE_Gross]),
          !dd.getFINPaymentSchedule().getInvoice().isSalesTransaction(), DateAcct, TABLEID_Invoice,
          dd.getFINPaymentSchedule().getInvoice().getId(), C_Currency_ID, as.m_C_Currency_ID, null,
          as, fact, Fact_Acct_Group_ID, nextSeqNo(SeqNo), conn, false);
      // Doubtful debt recognition
      fact.createLine(null, getAccountBPartner(C_BPartner_ID, as, true, false, true, conn),
          this.C_Currency_ID, bpAmountConverted.toString(), "", Fact_Acct_Group_ID,
          nextSeqNo(SeqNo), DocumentType, conn);
      fact.createLine(null, getAccountBPartner(C_BPartner_ID, as, true, false, false, conn),
          this.C_Currency_ID, "", bpAmountConverted.toString(), Fact_Acct_Group_ID,
          nextSeqNo(SeqNo), DocumentType, conn);
      // Provision
      Fact_Acct_Group_ID = SequenceIdData.getUUID();

      // Assign expense to the dimensions of the invoice lines
      BigDecimal assignedAmount = BigDecimal.ZERO;
      DocDoubtfulDebtData[] data = DocDoubtfulDebtData.select(conn,
          dd.getFINPaymentSchedule().getInvoice().getId());
      Currency currency = OBDal.getInstance().get(Currency.class, C_Currency_ID);
      for (int i = 0; i < data.length; i++) {
        BigDecimal lineAmount = bpAmountConverted.multiply(new BigDecimal(data[i].percentage))
            .setScale(currency.getStandardPrecision().intValue(), RoundingMode.HALF_UP);
        if (i == data.length - 1) {
          lineAmount = bpAmountConverted.subtract(assignedAmount);
        }
        DocLine line = new DocLine(DocumentType, Record_ID, "");
        line.m_A_Asset_ID = data[i].aAssetId;
        line.m_M_Product_ID = data[i].mProductId;
        line.m_C_Project_ID = data[i].cProjectId;
        line.m_C_BPartner_ID = data[i].cBpartnerId;
        line.m_C_Costcenter_ID = data[i].cCostcenterId;
        line.m_C_Campaign_ID = data[i].cCampaignId;
        line.m_C_Activity_ID = data[i].cActivityId;
        line.m_C_Glitem_ID = data[i].mCGlitemId;
        line.m_User1_ID = data[i].user1id;
        line.m_User2_ID = data[i].user2id;
        line.m_AD_Org_ID = data[i].adOrgId;
        fact.createLine(line, getAccountBPartnerBadDebt(C_BPartner_ID, true, as, conn),
            this.C_Currency_ID, lineAmount.toString(), "", Fact_Acct_Group_ID, nextSeqNo(SeqNo),
            DocumentType, conn);
        assignedAmount = assignedAmount.add(lineAmount);
      }
      fact.createLine(null, getAccountBPartnerAllowanceForDoubtfulDebt(C_BPartner_ID, as, conn),
          this.C_Currency_ID, "", bpAmountConverted.toString(), Fact_Acct_Group_ID,
          nextSeqNo(SeqNo), DocumentType, conn);
    } finally {
      OBContext.restorePreviousMode();
    }

    SeqNo = "0";

    return fact;
  }

  @Override
  public boolean getDocumentConfirmation(ConnectionProvider conn, String strRecordId) {
    return true;
  }

  public String nextSeqNo(String oldSeqNo) {
    BigDecimal seqNo = new BigDecimal(oldSeqNo);
    SeqNo = (seqNo.add(new BigDecimal("10"))).toString();
    return SeqNo;
  }
}
