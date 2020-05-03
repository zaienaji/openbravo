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
 * All portions are Copyright (C) 2014-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.costing;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.materialmgmt.cost.CostAdjustment;
import org.openbravo.model.materialmgmt.cost.CostAdjustmentLine;
import org.openbravo.model.materialmgmt.cost.TransactionCost;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;

public class CostAdjustmentProcess {
  private static final Logger log = LogManager.getLogger();
  @Inject
  @Any
  private Instance<CostingAlgorithmAdjustmentImp> costAdjustmentAlgorithms;
  @Inject
  @Any
  private Instance<CostAdjusmentProcessCheck> costAdjustmentProcessChecks;

  /**
   * Method to process a cost adjustment.
   * 
   * @param _costAdjustment
   *          the cost adjustment to be processed.
   * @return the message to be shown to the user properly formatted and translated to the user
   *         language.
   * @throws OBException
   *           when there is an error that prevents the cost adjustment to be processed.
   */
  private JSONObject processCostAdjustment(CostAdjustment _costAdjustment) throws OBException {
    CostAdjustment costAdjustment = _costAdjustment;
    JSONObject message = new JSONObject();
    OBContext.setAdminMode(false);
    try {
      message.put("severity", "success");
      message.put("title", "");
      message.put("text", OBMessageUtils.messageBD("Success"));
      doChecks(costAdjustment.getId(), message);
      initializeLines(costAdjustment);
      calculateAdjustmentAmount(costAdjustment.getId());
      doPostProcessChecks(costAdjustment.getId(), message);

      costAdjustment = OBDal.getInstance().get(CostAdjustment.class, costAdjustment.getId());
      costAdjustment.setProcessed(true);
      costAdjustment.setDocumentStatus("CO");
      OBDal.getInstance().save(costAdjustment);
    } catch (JSONException ignore) {
    } finally {
      OBContext.restorePreviousMode();
    }

    return message;
  }

  private void doChecks(String strCostAdjId, JSONObject message) {
    CostAdjustment costAdjustment = OBDal.getInstance().get(CostAdjustment.class, strCostAdjId);

    // check if there is period closed between reference date and max transaction date
    StringBuffer query = new StringBuffer();
    query.append(" select min(" + CostAdjustmentLine.PROPERTY_ACCOUNTINGDATE + ") as mindate");
    query.append(" from " + CostAdjustmentLine.ENTITY_NAME);
    query.append(" where " + CostAdjustmentLine.PROPERTY_COSTADJUSTMENT + " = :ca");
    query.append("   and " + CostAdjustmentLine.PROPERTY_ISSOURCE + " = true");

    Query<Date> qryMinDate = OBDal.getInstance()
        .getSession()
        .createQuery(query.toString(), Date.class);
    qryMinDate.setParameter("ca", costAdjustment);
    Date minDate = qryMinDate.uniqueResult();
    try {
      Date maxDate = CostingUtils.getMaxTransactionDate(costAdjustment.getOrganization());
      Period periodClosed = CostingUtils.periodClosed(costAdjustment.getOrganization(), minDate,
          maxDate, "CAD");
      if (periodClosed != null) {
        String errorMsg = OBMessageUtils.getI18NMessage("DocumentTypePeriodClosed",
            new String[] { "CAD", periodClosed.getIdentifier() });
        throw new OBException(errorMsg);
      }
    } catch (ServletException e) {
      throw new OBException(e.getMessage());
    }

    // Check that there are not permanently adjusted transactions in the sources.
    checkPermanentelyAdjustedTrx(strCostAdjId);

    // Execute checks added implementing costAdjustmentProcess interface.
    for (CostAdjusmentProcessCheck checksInstance : costAdjustmentProcessChecks) {
      checksInstance.doCheck(costAdjustment, message);
    }
  }

  private void doPostProcessChecks(String strCostAdjId, JSONObject message) {
    // Check there are not permanently adjusted transactions in the cost adjustment
    checkPermanentelyAdjustedTrx(strCostAdjId);

    // Execute checks added implementing costAdjustmentProcess interface.
    CostAdjustment costAdjustment = OBDal.getInstance().get(CostAdjustment.class, strCostAdjId);
    for (CostAdjusmentProcessCheck checksInstance : costAdjustmentProcessChecks) {
      checksInstance.doPostProcessCheck(costAdjustment, message);
    }
  }

  /**
   * Permanently adjusted Transactions with and adjustment amount different than zero should not be
   * adjusted. The only exception are the Opening Inventories. Due to backdated Transactions that
   * can modify the stock or the stock valuation, it is necessary to adjust this Opening Inventories
   * to fix rounding issues.
   */

  private void checkPermanentelyAdjustedTrx(String strCostAdjId) throws OBException {
    StringBuffer where = new StringBuffer();
    where.append(" as cal");
    where.append(" join cal." + CostAdjustmentLine.PROPERTY_COSTADJUSTMENT + " as ca");
    where.append(" join cal." + CostAdjustmentLine.PROPERTY_INVENTORYTRANSACTION + " as trx");
    where.append(" left join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    where.append(" left join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    where.append(" where ca." + CostAdjustment.PROPERTY_ID + " = :strCostAdjId");
    where.append(" and coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE + ", 'N') <> 'O'");
    where.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTPERMANENT + " = true");
    where.append(" and cal." + CostAdjustmentLine.PROPERTY_ADJUSTMENTAMOUNT + " <> 0");
    where.append(" and cal." + CostAdjustmentLine.PROPERTY_UNITCOST + " = true");
    where.append(" order by cal." + CostAdjustmentLine.PROPERTY_LINENO);

    OBQuery<CostAdjustmentLine> qry = OBDal.getInstance()
        .createQuery(CostAdjustmentLine.class, where.toString());
    qry.setNamedParameter("strCostAdjId", strCostAdjId);

    ScrollableResults lines = qry.scroll(ScrollMode.FORWARD_ONLY);
    long count = 1L;
    try {
      String strLines = "";
      while (lines.next()) {
        CostAdjustmentLine line = (CostAdjustmentLine) lines.get()[0];
        strLines += line.getLineNo() + ", ";

        if (count == 10) {
          break;
        }
        count++;
      }
      if (!strLines.isEmpty()) {
        strLines = strLines.substring(0, strLines.length() - 2);
        String errorMessage = OBMessageUtils.messageBD("CostAdjustmentWithPermanentLines");
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("lines", strLines);
        throw new OBException(OBMessageUtils.parseTranslation(errorMessage, map));
      }
      OBDal.getInstance().flush();
      OBDal.getInstance().getSession().clear();
    } finally {
      lines.close();
    }
  }

  private void initializeLines(CostAdjustment costAdjustment) {
    // initialize is related transaction adjusted flag to false
    StringBuilder updateQuery = new StringBuilder("update ");
    updateQuery.append(CostAdjustmentLine.ENTITY_NAME);
    updateQuery.append(" set ");
    updateQuery.append(CostAdjustmentLine.PROPERTY_ISRELATEDTRANSACTIONADJUSTED);
    updateQuery.append(" = false where ");
    updateQuery.append(CostAdjustmentLine.PROPERTY_COSTADJUSTMENT);
    updateQuery.append(".id = :adjustmentId and ");
    updateQuery.append(CostAdjustmentLine.PROPERTY_ISRELATEDTRANSACTIONADJUSTED);
    updateQuery.append(" = true ");
    @SuppressWarnings("rawtypes")
    Query adjustmentLineQuery = OBDal.getInstance()
        .getSession()
        .createQuery(updateQuery.toString());
    adjustmentLineQuery.setParameter("adjustmentId", costAdjustment.getId());
    adjustmentLineQuery.executeUpdate();
  }

  private void calculateAdjustmentAmount(String strCostAdjustmentId) {
    CostAdjustmentLine line = getNextLine(strCostAdjustmentId);
    while (line != null) {
      MaterialTransaction trx = line.getInventoryTransaction();
      log.debug("Start processing line: {}, transaction: {}", line.getLineNo(),
          trx.getIdentifier());
      if (trx.getCostingAlgorithm() == null) {
        log.error("Transaction is cost calculated with legacy cost engine.");
        throw new OBException(OBMessageUtils.messageBD("NotAdjustLegacyEngineTrx"));
      }
      final String strCostAdjLineId = line.getId();

      // Add transactions that depend on the transaction being adjusted.
      CostingAlgorithmAdjustmentImp costAdjImp = getAlgorithmAdjustmentImp(
          trx.getCostingAlgorithm().getJavaClassName());

      log.debug("costing algorithm imp loaded {}", costAdjImp.getClass().getName());
      costAdjImp.init(line);
      costAdjImp.searchRelatedTransactionCosts(null);
      // Reload cost adjustment object in case the costing algorithm has cleared the session.
      line = OBDal.getInstance().get(CostAdjustmentLine.class, strCostAdjLineId);
      line.setRelatedTransactionAdjusted(true);
      generateTransactionCosts(line);
      OBDal.getInstance().flush();
      OBDal.getInstance().getSession().clear();
      line = getNextLine(strCostAdjustmentId);
    }
  }

  private CostAdjustmentLine getNextLine(String strCostAdjustmentId) {
    OBCriteria<CostAdjustmentLine> critLines = OBDal.getInstance()
        .createCriteria(CostAdjustmentLine.class);
    critLines.createAlias(CostAdjustmentLine.PROPERTY_INVENTORYTRANSACTION, "trx");
    critLines.createAlias(CostAdjustmentLine.PROPERTY_COSTADJUSTMENT, "ca");
    critLines.add(Restrictions.eq("ca.id", strCostAdjustmentId));
    critLines.add(Restrictions.eq(CostAdjustmentLine.PROPERTY_ISRELATEDTRANSACTIONADJUSTED, false));
    critLines.addOrder(Order.asc("trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE));
    critLines.addOrder(Order.asc("ca." + CostAdjustment.PROPERTY_DOCUMENTNO));
    critLines.addOrder(Order.asc(CostAdjustmentLine.PROPERTY_LINENO));
    critLines.addOrder(Order.asc(CostAdjustmentLine.PROPERTY_ADJUSTMENTAMOUNT));
    critLines.addOrder(Order.asc("trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE));
    critLines.setMaxResults(1);
    return (CostAdjustmentLine) critLines.uniqueResult();
  }

  private void generateTransactionCosts(CostAdjustmentLine costAdjustmentLine) {
    log.debug("Generate transaction costs of line: {}", costAdjustmentLine.getLineNo());
    long t1 = System.currentTimeMillis();
    OBCriteria<CostAdjustmentLine> critLines = OBDal.getInstance()
        .createCriteria(CostAdjustmentLine.class);
    Date referenceDate = costAdjustmentLine.getCostAdjustment().getReferenceDate();
    critLines.add(Restrictions.or(
        Restrictions.eq(CostAdjustmentLine.PROPERTY_PARENTCOSTADJUSTMENTLINE, costAdjustmentLine),
        Restrictions.eq("id", costAdjustmentLine.getId())));
    ScrollableResults lines = critLines.scroll(ScrollMode.FORWARD_ONLY);

    try {
      OBContext.setAdminMode(false);
      while (lines.next()) {
        CostAdjustmentLine line = (CostAdjustmentLine) lines.get(0);
        if (!line.getTransactionCostList().isEmpty()) {
          continue;
        }
        TransactionCost trxCost = OBProvider.getInstance().get(TransactionCost.class);
        // TODO: Review this
        // trxCost.setNewOBObject(true);
        MaterialTransaction trx = line.getInventoryTransaction();
        trxCost.setInventoryTransaction(trx);
        trxCost.setOrganization(trx.getOrganization());
        trxCost.setCostDate(referenceDate);
        trxCost.setCostAdjustmentLine(line);
        trxCost.setUnitCost(line.isUnitCost());
        Date accountingDate = line.getAccountingDate();
        if (accountingDate == null) {
          accountingDate = trx.getMovementDate();
        }
        trxCost.setAccountingDate(accountingDate);
        BigDecimal convertedAmt = line.getAdjustmentAmount();
        if (!line.getCurrency().getId().equals(trx.getCurrency().getId())) {
          convertedAmt = FinancialUtils.getConvertedAmount(convertedAmt, line.getCurrency(),
              trx.getCurrency(), accountingDate, trx.getOrganization(), "C");
        }
        trxCost.setCost(convertedAmt);
        trxCost.setCurrency(trx.getCurrency());
        OBDal.getInstance().getSession().evict(trx);

        OBDal.getInstance().save(trxCost);
      }
    } finally {
      lines.close();
      OBContext.restorePreviousMode();
    }
    OBDal.getInstance().flush();
    OBDal.getInstance().getSession().clear();
    log.debug("Transaction costs created. Time {}", System.currentTimeMillis() - t1);
  }

  private CostingAlgorithmAdjustmentImp getAlgorithmAdjustmentImp(String strJavaClass) {
    CostingAlgorithmAdjustmentImp implementor = null;
    for (CostingAlgorithmAdjustmentImp nextImplementor : costAdjustmentAlgorithms
        .select(new ComponentProvider.Selector(strJavaClass))) {
      if (implementor == null) {
        implementor = nextImplementor;
      } else {
        log.warn(
            "More than one class found implementing cost adjustment for algorithm with java class {}",
            strJavaClass);
      }
    }
    if (implementor == null) {
      throw new OBException(OBMessageUtils.messageBD("CostAlgorithmWithoutAdjustment"));
    }
    return implementor;
  }

  public static JSONObject doProcessCostAdjustment(CostAdjustment costAdjustment)
      throws OBException {
    log.debug("Starting doProcessCostAdjustment() for cost adjustment: {}",
        costAdjustment.getDocumentNo());
    long t1 = System.currentTimeMillis();
    CostAdjustmentProcess cap = WeldUtils
        .getInstanceFromStaticBeanManager(CostAdjustmentProcess.class);
    JSONObject message = cap.processCostAdjustment(costAdjustment);
    long t2 = System.currentTimeMillis();
    log.debug("Ending doProcessCostAdjustment() took: {} ms.", (t2 - t1));
    return message;
  }

  public static CostingAlgorithmAdjustmentImp doGetAlgorithmAdjustmentImp(String strJavaClass) {
    CostAdjustmentProcess cap = WeldUtils
        .getInstanceFromStaticBeanManager(CostAdjustmentProcess.class);
    return cap.getAlgorithmAdjustmentImp(strJavaClass);
  }
}
