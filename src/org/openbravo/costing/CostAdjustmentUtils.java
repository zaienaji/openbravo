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
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.costing.CostingAlgorithm.CostDimension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.cost.CostAdjustment;
import org.openbravo.model.materialmgmt.cost.CostAdjustmentLine;
import org.openbravo.model.materialmgmt.cost.Costing;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.cost.TransactionCost;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.TransactionLast;

public class CostAdjustmentUtils {
  private static final Logger log4j = LogManager.getLogger();
  public static final String strCategoryCostAdj = "CAD";
  public static final String strTableCostAdj = "M_CostAdjustment";
  public static final String propADListPriority = org.openbravo.model.ad.domain.List.PROPERTY_SEQUENCENUMBER;
  public static final String propADListReference = org.openbravo.model.ad.domain.List.PROPERTY_REFERENCE;
  public static final String propADListValue = org.openbravo.model.ad.domain.List.PROPERTY_SEARCHKEY;
  public static final String MovementTypeRefID = "189";
  public static final String ENABLE_AUTO_PRICE_CORRECTION_PREF = "enableAutomaticPriceCorrectionTrxs";
  public static final String ENABLE_NEGATIVE_STOCK_CORRECTION_PREF = "enableNegativeStockCorrections";

  /**
   * Returns a new header for a Cost Adjustment
   * 
   * @param org
   *          organization set in record
   * 
   * @param sourceProcess
   *          the process that origin the Cost Adjustment: - MCC: Manual Cost Correction - IAU:
   *          Inventory Amount Update - PDC: Price Difference Correction - LC: Landed Cost - BDT:
   *          Backdated Transaction
   */
  public static CostAdjustment insertCostAdjustmentHeader(Organization org, String sourceProcess) {

    final DocumentType docType = FIN_Utility.getDocumentType(org, strCategoryCostAdj);
    final String docNo = FIN_Utility.getDocumentNo(docType, strTableCostAdj);
    final Organization orgLegal = OBContext.getOBContext()
        .getOrganizationStructureProvider(OBContext.getOBContext().getCurrentClient().getId())
        .getLegalEntity(org);

    CostAdjustment costAdjustment = OBProvider.getInstance().get(CostAdjustment.class);
    costAdjustment.setOrganization(orgLegal);
    costAdjustment.setDocumentType(docType);
    costAdjustment.setDocumentNo(docNo);
    costAdjustment.setReferenceDate(new Date());
    costAdjustment.setSourceProcess(sourceProcess);
    costAdjustment.setProcessed(Boolean.FALSE);
    OBDal.getInstance().save(costAdjustment);

    return costAdjustment;
  }

  /**
   * Creates a new Cost Adjustment Line and returns it.
   * 
   * @param transaction
   *          transaction to apply the cost adjustment
   * 
   * @param costAdjustmentHeader
   *          header of line
   * 
   * @param costAdjusted
   *          amount to adjust in the cost
   * 
   * @param isSource
   */
  @Deprecated
  public static CostAdjustmentLine insertCostAdjustmentLine(MaterialTransaction transaction,
      CostAdjustment costAdjustmentHeader, BigDecimal costAdjusted, boolean isSource,
      Date accountingDate) {
    Long lineNo = getNewLineNo(costAdjustmentHeader);
    return insertCostAdjustmentLine(transaction, costAdjustmentHeader, costAdjusted, isSource,
        accountingDate, lineNo);
  }

  @Deprecated
  public static CostAdjustmentLine insertCostAdjustmentLine(MaterialTransaction transaction,
      CostAdjustment costAdjustmentHeader, BigDecimal costAdjusted, boolean isSource,
      Date accountingDate, Currency currency) {
    Long lineNo = getNewLineNo(costAdjustmentHeader);
    return insertCostAdjustmentLine(transaction, costAdjustmentHeader, costAdjusted, isSource,
        accountingDate, lineNo, currency);
  }

  @Deprecated
  public static CostAdjustmentLine insertCostAdjustmentLine(MaterialTransaction transaction,
      CostAdjustment costAdjustmentHeader, BigDecimal costAdjusted, boolean isSource,
      Date accountingDate, Long lineNo) {
    return insertCostAdjustmentLine(transaction, costAdjustmentHeader, costAdjusted, isSource,
        accountingDate, lineNo, null);
  }

  @Deprecated
  public static CostAdjustmentLine insertCostAdjustmentLine(MaterialTransaction transaction,
      CostAdjustment costAdjustmentHeader, BigDecimal costAdjusted, boolean isSource,
      Date accountingDate, Long lineNo, Currency currency) {
    final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(
        transaction, costAdjusted, costAdjustmentHeader, currency);
    lineParameters.setSource(isSource);
    return insertCostAdjustmentLine(lineParameters, accountingDate, lineNo);
  }

  /**
   * Creates a new Adjustment Line and returns it
   * 
   * @param lineParameters
   *          Object that contains most of the information needed to created the Adjustment Line
   * @param accountingDate
   *          The Date for which this document will be posted
   * @return An Adjustment Line created based on the given parameters
   */
  public static CostAdjustmentLine insertCostAdjustmentLine(
      final CostAdjustmentLineParameters lineParameters, final Date accountingDate) {
    final Long lineNo = getNewLineNo(lineParameters.getCostAdjustmentHeader());
    return insertCostAdjustmentLine(lineParameters, accountingDate, lineNo);
  }

  /**
   * 
   * Creates a new Adjustment Line and returns it
   * 
   * @param lineParameters
   *          Object that contains most of the information needed to created the Adjustment Line
   * @param accountingDate
   *          The Date for which this document will be posted
   * @param lineNo
   *          Number to position the line within the Cost Adjustment Document
   * @return An Adjustment Line created based on the given parameters
   */
  public static CostAdjustmentLine insertCostAdjustmentLine(
      final CostAdjustmentLineParameters lineParameters, final Date accountingDate,
      final Long lineNo) {
    final Long stdPrecission = lineParameters.getTransaction().getCurrency().getStandardPrecision();

    CostAdjustmentLine costAdjustmentLine = getExistingCostAdjustmentLine(lineParameters,
        accountingDate);
    if (costAdjustmentLine == null) {
      costAdjustmentLine = OBProvider.getInstance().get(CostAdjustmentLine.class);
      costAdjustmentLine
          .setOrganization(lineParameters.getCostAdjustmentHeader().getOrganization());
      costAdjustmentLine.setCostAdjustment(lineParameters.getCostAdjustmentHeader());
      costAdjustmentLine.setCurrency(lineParameters.getCurrency());
      costAdjustmentLine.setInventoryTransaction(lineParameters.getTransaction());
      costAdjustmentLine.setSource(lineParameters.isSource());
      costAdjustmentLine.setAccountingDate(accountingDate);
      costAdjustmentLine.setLineNo(lineNo);
      costAdjustmentLine.setUnitCost(lineParameters.isUnitCost());
      costAdjustmentLine.setNegativeStockCorrection(lineParameters.isNegativeCorrection());
      costAdjustmentLine.setBackdatedTrx(lineParameters.isBackdatedTransaction());
      costAdjustmentLine.setNeedsPosting(lineParameters.isNeedPosting());
      costAdjustmentLine
          .setRelatedTransactionAdjusted(lineParameters.isRelatedTransactionAdjusted());
    }
    if (lineParameters.getAdjustmentAmount() == null) {
      costAdjustmentLine.setAdjustmentAmount(null);
    } else {
      BigDecimal previouslyAdjustedAmount = costAdjustmentLine.getAdjustmentAmount() == null
          ? BigDecimal.ZERO
          : costAdjustmentLine.getAdjustmentAmount();
      costAdjustmentLine.setAdjustmentAmount(lineParameters.getAdjustmentAmount()
          .add(previouslyAdjustedAmount)
          .setScale(stdPrecission.intValue(), RoundingMode.HALF_UP));
    }

    OBDal.getInstance().save(costAdjustmentLine);

    return costAdjustmentLine;
  }

  private static CostAdjustmentLine getExistingCostAdjustmentLine(
      CostAdjustmentLineParameters lineParameters, Date accountingDate) {
    StringBuilder hql = new StringBuilder("");
    hql.append(" costAdjustment.id = :costAdjustmentId ");
    hql.append(" and inventoryTransaction.id = :transactionId ");
    hql.append(" and isRelatedTransactionAdjusted = false ");
    hql.append(" and currency.id = :currencyId ");
    hql.append(" and isSource = :isSource ");
    hql.append(" and accountingDate = :accountingDate");
    hql.append(" and unitCost = :isUnitCost ");
    hql.append(" and isBackdatedTrx = :isBackdatedTrx");
    hql.append(" and isNegativeStockCorrection = :isNegativeCorrection");

    OBQuery<CostAdjustmentLine> obc = OBDal.getInstance()
        .createQuery(CostAdjustmentLine.class, hql.toString());
    obc.setNamedParameter("costAdjustmentId", lineParameters.getCostAdjustmentHeader().getId());
    obc.setNamedParameter("transactionId", lineParameters.getTransaction().getId());
    obc.setNamedParameter("currencyId", lineParameters.getCurrency().getId());
    obc.setNamedParameter("isSource", lineParameters.isSource());
    obc.setNamedParameter("accountingDate", accountingDate);
    obc.setNamedParameter("isUnitCost", lineParameters.isUnitCost());
    obc.setNamedParameter("isBackdatedTrx", lineParameters.isBackdatedTransaction());
    obc.setNamedParameter("isNegativeCorrection", lineParameters.isNegativeCorrection());

    obc.setMaxResult(1);

    CostAdjustmentLine costAdjustmentLine = (CostAdjustmentLine) obc.uniqueResult();
    return costAdjustmentLine;
  }

  public static boolean isNeededBackdatedCostAdjustment(MaterialTransaction transaction,
      boolean includeWarehouseDimension, Date startingDate) {
    TransactionLast lastTransaction = CostAdjustmentUtils.getLastTransaction(transaction,
        includeWarehouseDimension);
    if (lastTransaction == null) {
      lastTransaction = CostAdjustmentUtils.insertLastTransaction(transaction,
          includeWarehouseDimension);
    }
    if (lastTransaction != null && CostAdjustmentUtils.compareToLastTransaction(transaction,
        lastTransaction, startingDate) < 0) {
      return true;
    } else {
      return false;
    }
  }

  public static TransactionLast getLastTransaction(MaterialTransaction trx,
      boolean includeWarehouseDimension) {
    final Organization orgLegal = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId())
        .getLegalEntity(trx.getOrganization());
    OBCriteria<TransactionLast> obc = OBDal.getInstance().createCriteria(TransactionLast.class);
    obc.add(Restrictions.eq(TransactionLast.PROPERTY_PRODUCT, trx.getProduct()));
    obc.add(Restrictions.eq(TransactionLast.PROPERTY_ORGANIZATION, orgLegal));
    if (includeWarehouseDimension) {
      obc.add(
          Restrictions.eq(TransactionLast.PROPERTY_WAREHOUSE, trx.getStorageBin().getWarehouse()));
    }
    obc.setMaxResults(1);
    return (TransactionLast) obc.uniqueResult();
  }

  public static int compareToLastTransaction(MaterialTransaction trx,
      TransactionLast lastTransaction, Date startingDate) {
    MaterialTransaction lastTrx = lastTransaction.getInventoryTransaction();

    // If trx is the same as lastTransaction or is from previous costing rule, return 0
    if (trx.getId().equals(lastTrx.getId())
        || lastTrx.getTransactionProcessDate().compareTo(startingDate) <= 0) {
      return 0;
    }

    int compareMovementDate = DateUtils.truncate(trx.getMovementDate(), Calendar.DATE)
        .compareTo(DateUtils.truncate(lastTrx.getMovementDate(), Calendar.DATE));
    int compareProcessDate = trx.getTransactionProcessDate()
        .compareTo(lastTrx.getTransactionProcessDate());
    Long trxPrio = CostAdjustmentUtils.getTrxTypePrio(trx.getMovementType());
    Long lastPrio = CostAdjustmentUtils.getTrxTypePrio(lastTrx.getMovementType());
    int comparePriority = trxPrio.compareTo(lastPrio);
    int compareQty = trx.getMovementQuantity().compareTo(lastTrx.getMovementQuantity());

    // If trx was processed after lastTrx
    if (compareProcessDate > 0 || (compareProcessDate == 0
        && (comparePriority > 0 || (comparePriority == 0 && compareQty <= 0)))) {
      if (compareMovementDate < 0) {
        // Before
        return -1;
      } else {
        // After
        return 1;
      }
    }

    return 0;
  }

  private static TransactionLast insertLastTransaction(MaterialTransaction trx,
      boolean includeWarehouseDimension) {
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId());
    final Organization orgLegal = osp.getLegalEntity(trx.getOrganization());
    Set<String> orgs = osp.getChildTree(orgLegal.getId(), true);

    StringBuffer where = new StringBuffer();
    where.append(" select trx." + MaterialTransaction.PROPERTY_ID);
    where.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (includeWarehouseDimension) {
      where.append(" join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    where.append(" , " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");
    where.append(" where trxtype." + CostAdjustmentUtils.propADListValue + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    where.append(" and trxtype." + CostAdjustmentUtils.propADListReference + ".id = :refId");
    where.append(" and trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :productId");
    where.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgIds)");
    if (includeWarehouseDimension) {
      where.append(" and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouseId");
    }
    where.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    where.append(" order by trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " desc");
    where.append(" , trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " desc");
    where.append(" , trxtype." + propADListPriority + " desc");
    where.append(" , trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " asc");
    where.append(" , trx." + MaterialTransaction.PROPERTY_ID + " desc");
    Query<String> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), String.class);
    trxQry.setParameter("refId", CostAdjustmentUtils.MovementTypeRefID);
    trxQry.setParameter("productId", trx.getProduct().getId());
    trxQry.setParameterList("orgIds", orgs);
    if (includeWarehouseDimension) {
      trxQry.setParameter("warehouseId", trx.getStorageBin().getWarehouse().getId());
    }
    trxQry.setMaxResults(1);
    String transactionId = trxQry.uniqueResult();

    TransactionLast lastTransaction = null;
    if (transactionId != null) {
      MaterialTransaction transaction = OBDal.getInstance()
          .get(MaterialTransaction.class, transactionId);
      lastTransaction = OBProvider.getInstance().get(TransactionLast.class);
      lastTransaction.setClient(transaction.getClient());
      lastTransaction.setOrganization(orgLegal);
      lastTransaction.setInventoryTransaction(transaction);
      lastTransaction.setProduct(transaction.getProduct());
      if (includeWarehouseDimension) {
        lastTransaction.setWarehouse(transaction.getStorageBin().getWarehouse());
      }
      OBDal.getInstance().save(lastTransaction);
      OBDal.getInstance().flush();
    }

    return lastTransaction;
  }

  private static Long getNewLineNo(CostAdjustment cadj) {
    StringBuffer where = new StringBuffer();
    where.append(" select max(" + CostAdjustmentLine.PROPERTY_LINENO + ")");
    where.append(" from " + CostAdjustmentLine.ENTITY_NAME + " as cal");
    where.append(
        " where cal." + CostAdjustmentLine.PROPERTY_COSTADJUSTMENT + ".id = :costAdjustment");
    Query<Long> calQry = OBDal.getInstance().getSession().createQuery(where.toString(), Long.class);
    calQry.setParameter("costAdjustment", cadj.getId());
    calQry.setMaxResults(1);

    Long lineNo = calQry.uniqueResult();
    if (lineNo != null) {
      return lineNo + 10L;
    }
    return 10L;
  }

  public static BigDecimal getTrxCost(MaterialTransaction trx, boolean justUnitCost,
      Currency currency) {
    if (!trx.isCostCalculated()) {
      // Transaction hasn't been calculated yet.
      log4j.error("  *** No cost found for transaction {} with id {}", trx.getIdentifier(),
          trx.getId());
      throw new OBException("@NoCostFoundForTrxOnDate@ @Transaction@: " + trx.getIdentifier());
    }

    StringBuffer select = new StringBuffer();
    select.append(" select sum(tc." + TransactionCost.PROPERTY_COST + ") as cost");
    select.append(" , tc." + TransactionCost.PROPERTY_CURRENCY + ".id as currency");
    select.append(" , tc." + TransactionCost.PROPERTY_COSTDATE + " as date");
    select.append(" from " + TransactionCost.ENTITY_NAME + " as tc");
    select.append(" where tc." + TransactionCost.PROPERTY_INVENTORYTRANSACTION + ".id = :trxId");
    if (justUnitCost) {
      select.append(" and tc." + TransactionCost.PROPERTY_UNITCOST + " = true");
    }
    select.append(" group by tc." + TransactionCost.PROPERTY_CURRENCY);
    select.append(" , tc." + TransactionCost.PROPERTY_COSTDATE);

    Query<Object[]> qry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Object[].class);
    qry.setParameter("trxId", trx.getId());
    ScrollableResults scroll = qry.scroll(ScrollMode.FORWARD_ONLY);

    BigDecimal cost = BigDecimal.ZERO;
    try {
      while (scroll.next()) {
        Object[] resultSet = scroll.get();
        BigDecimal costAmt = (BigDecimal) resultSet[0];
        String origCurId = (String) resultSet[1];

        if (StringUtils.equals(origCurId, currency.getId())) {
          cost = cost.add(costAmt);
        } else {
          Currency origCur = OBDal.getInstance().get(Currency.class, origCurId);
          Date convDate = (Date) resultSet[2];
          cost = cost.add(FinancialUtils.getConvertedAmount(costAmt, origCur, currency, convDate,
              trx.getOrganization(), FinancialUtils.PRECISION_COSTING));
        }
      }
    } finally {
      scroll.close();
    }
    return cost;
  }

  /**
   * Calculates the stock of the product on the given date and for the given cost dimensions. It
   * only takes transactions that have its cost calculated.
   */
  public static BigDecimal getStockOnMovementDate(Product product, Organization org, Date _date,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean backdatedTransactionsFixed) {
    // Get child tree of organizations.
    Date date = _date;
    Set<String> orgs = OBContext.getOBContext()
        .getOrganizationStructureProvider()
        .getChildTree(org.getId(), true);

    StringBuffer subSelect = new StringBuffer();
    subSelect.append(" select min(case when coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE
        + ", 'N') <> 'N' then trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " else trx."
        + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " end)");
    subSelect.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      subSelect.append("   join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    subSelect.append(
        "   left join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    subSelect.append("   left join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    subSelect.append(" where trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :product");
    subSelect.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :date");
    // Include only transactions that have its cost calculated
    subSelect.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      subSelect.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    subSelect.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");

    Query<Date> trxsubQry = OBDal.getInstance()
        .getSession()
        .createQuery(subSelect.toString(), Date.class);
    trxsubQry.setParameter("date", date);
    trxsubQry.setParameter("product", product.getId());
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxsubQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    trxsubQry.setParameterList("orgs", orgs);
    Date trxprocessDate = trxsubQry.uniqueResult();

    StringBuffer select = new StringBuffer();
    select
        .append(" select sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") as stock");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("   join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }

    Date backdatedTrxFrom = null;
    if (backdatedTransactionsFixed) {
      CostingRule costRule = CostingUtils.getCostDimensionRule(org, date);
      backdatedTrxFrom = CostingUtils.getCostingRuleFixBackdatedFrom(costRule);
    }

    if (trxprocessDate != null
        && (!backdatedTransactionsFixed || trxprocessDate.before(backdatedTrxFrom))) {
      date = trxprocessDate;
      select.append(
          " left join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
      select.append(" left join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
      select.append(" where case when coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE
          + ", 'N') <> 'N' then trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " else trx."
          + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " end < :date");
    } else {
      select.append(" where trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " <= :date");
    }

    select.append(" and trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :product");
    // Include only transactions that have its cost calculated
    select.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    Query<BigDecimal> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), BigDecimal.class);
    trxQry.setParameter("product", product.getId());
    trxQry.setParameter("date", date);
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    trxQry.setParameterList("orgs", orgs);
    BigDecimal stock = trxQry.uniqueResult();
    return (stock != null) ? stock : BigDecimal.ZERO;
  }

  /**
   * Calculates the stock of the product on the given date and for the given cost dimensions. It
   * only takes transactions that have its cost calculated.
   */
  public static BigDecimal getStockOnTransactionDate(Organization costorg, MaterialTransaction trx,
      HashMap<CostDimension, BaseOBObject> _costDimensions, boolean isManufacturingProduct,
      boolean areBackdatedTrxFixed, Currency currency) {
    Date date = areBackdatedTrxFixed ? trx.getMovementDate() : trx.getTransactionProcessDate();
    Costing costing = AverageAlgorithm.getLastCumulatedCosting(date, trx.getProduct(),
        _costDimensions, costorg);
    return getStockOnTransactionDate(costorg, trx, _costDimensions, isManufacturingProduct,
        areBackdatedTrxFixed, currency, costing);
  }

  /**
   * Calculates the stock of the product on the given date and for the given cost dimensions. It
   * only takes transactions that have its cost calculated.
   */
  public static BigDecimal getStockOnTransactionDate(Organization costorg, MaterialTransaction trx,
      HashMap<CostDimension, BaseOBObject> _costDimensions, boolean isManufacturingProduct,
      boolean areBackdatedTrxFixed, Currency currency, Costing costing) {

    // Get child tree of organizations.
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId());
    Set<String> orgs = osp.getChildTree(costorg.getId(), true);
    HashMap<CostDimension, BaseOBObject> costDimensions = _costDimensions;
    if (isManufacturingProduct) {
      orgs = osp.getChildTree("0", false);
      costDimensions = CostingUtils.getEmptyDimensions();
    }
    CostingRule costingRule = CostingUtils.getCostDimensionRule(costorg,
        trx.getTransactionProcessDate());

    BigDecimal cumulatedStock = null;
    int costingPrecision = currency.getCostingPrecision().intValue();
    MaterialTransaction ctrx = costing != null ? costing.getInventoryTransaction() : null;
    boolean existsCumulatedStockOnTrxDate = ctrx != null
        && costing.getTotalMovementQuantity() != null;

    // Backdated transactions can't use cumulated values
    if (existsCumulatedStockOnTrxDate && costingRule.isBackdatedTransactionsFixed()) {
      Date trxMovementDate = DateUtils.truncate(trx.getMovementDate(), Calendar.DATE);
      Date ctrxMovementDate = DateUtils.truncate(ctrx.getMovementDate(), Calendar.DATE);
      if (trxMovementDate.compareTo(ctrxMovementDate) < 0
          || (trxMovementDate.compareTo(ctrxMovementDate) == 0 && trx.getTransactionProcessDate()
              .compareTo(ctrx.getTransactionProcessDate()) <= 0)) {
        existsCumulatedStockOnTrxDate = false;
      }
    }

    if (existsCumulatedStockOnTrxDate) {
      cumulatedStock = costing.getTotalMovementQuantity();
      if (StringUtils.equals(ctrx.getId(), trx.getId())) {
        return cumulatedStock.setScale(costingPrecision, RoundingMode.HALF_UP);
      }
    }

    StringBuffer select = new StringBuffer();
    select
        .append(" select sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") as stock");

    select.append("\n from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("\n join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    select.append("\n , " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");

    select.append("\n where trxtype." + propADListReference + ".id = :refid");
    select.append(
        "  and trxtype." + propADListValue + " = trx." + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    select.append("   and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    // Include only transactions that have its cost calculated. Should be all.
    select.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");

    if (existsCumulatedStockOnTrxDate) {
      if (costingRule.isBackdatedTransactionsFixed()) {
        select.append(" and (trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :cmvtdate");
        select.append(" or (trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :cmvtdate");
      }
      select.append(
          " and (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :ctrxdate");
      select.append(
          " or (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :ctrxdate");
      // If the costing Transaction is an M- exclude the M+ Transactions with same movementDate and
      // TrxProcessDate due to how data is going to be ordered in further queries using the priority
      if (costing.getInventoryTransaction().getMovementType().equals("M-")) {
        select.append(
            " and (( trx." + MaterialTransaction.PROPERTY_MOVEMENTTYPE + " <> 'M+' and trxtype."
                + CostAdjustmentUtils.propADListPriority + " > :ctrxtypeprio)");
      } else {
        select
            .append(" and (trxtype." + CostAdjustmentUtils.propADListPriority + " > :ctrxtypeprio");
      }
      select.append(" or (trxtype." + CostAdjustmentUtils.propADListPriority + " = :ctrxtypeprio");
      select.append(" and (trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " < :ctrxqty");
      select.append(" or (trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :ctrxqty");
      select.append(" and trx." + MaterialTransaction.PROPERTY_ID + " > :ctrxid");
      select.append(" ))))))");
      if (costingRule.isBackdatedTransactionsFixed()) {
        select.append(" ))");
      }
    }

    select.append("  and ( ");

    if (costingRule.isBackdatedTransactionsFixed()) {
      select.append("  (");
      select.append("   trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :fixbdt");
      select.append("   and  (");
    }

    // If there are more than one trx on the same trx process date filter out those types with less
    // priority and / or higher quantity.
    select.append("    trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :trxdate");
    select.append("    or (");
    select
        .append("     trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :trxdate");
    select.append("     and (");
    select.append("      trxtype." + propADListPriority + " < :trxtypeprio");
    select.append("      or (");
    select.append("       trxtype." + propADListPriority + " = :trxtypeprio");
    select.append("       and trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " > :trxqty");
    select.append("        or (");
    select.append("         trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :trxqty");
    select.append("         and trx." + MaterialTransaction.PROPERTY_ID + " <= :trxid");
    select.append("   ))))");

    if (costingRule.isBackdatedTransactionsFixed()) {
      select.append(" )) or (");
      select.append("  trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " >= :fixbdt");
      select.append("  and (");
      select.append("   trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " < :mvtdate");
      select.append("   or (");
      select.append("    trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :mvtdate");
      // If there are more than one trx on the same trx process date filter out those types with
      // less priority and / or higher quantity.
      select.append("    and (");
      select.append(
          "     trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :trxdate");
      select.append("     or (");
      select.append(
          "      trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :trxdate");
      select.append("      and (");
      select.append("       trxtype." + propADListPriority + " < :trxtypeprio");
      select.append("       or (");
      select.append("        trxtype." + propADListPriority + " = :trxtypeprio");
      select.append(
          "        and trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " > :trxqty");
      select.append("        or (");
      select.append("         trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :trxqty");
      select.append("         and trx." + MaterialTransaction.PROPERTY_ID + " <= :trxid");
      select.append("    ))))");
      select.append("   ))))");
    }

    select.append("  )");

    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }

    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");

    Query<BigDecimal> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), BigDecimal.class);
    trxQry.setParameter("refid", MovementTypeRefID);
    trxQry.setParameter("product", trx.getProduct());
    trxQry.setParameter("trxdate", trx.getTransactionProcessDate());
    trxQry.setParameter("trxtypeprio", getTrxTypePrio(trx.getMovementType()));
    trxQry.setParameter("trxqty", trx.getMovementQuantity());
    trxQry.setParameter("trxid", trx.getId());
    trxQry.setParameterList("orgs", orgs);

    if (existsCumulatedStockOnTrxDate) {
      if (costingRule.isBackdatedTransactionsFixed()) {
        trxQry.setParameter("cmvtdate", ctrx.getMovementDate());
      }
      trxQry.setParameter("ctrxdate", ctrx.getTransactionProcessDate());
      trxQry.setParameter("ctrxtypeprio", getTrxTypePrio(ctrx.getMovementType()));
      trxQry.setParameter("ctrxqty", ctrx.getMovementQuantity());
      trxQry.setParameter("ctrxid", ctrx.getId());
    }

    if (costingRule.isBackdatedTransactionsFixed()) {
      trxQry.setParameter("mvtdate", trx.getMovementDate());
      trxQry.setParameter("fixbdt", CostingUtils.getCostingRuleFixBackdatedFrom(costingRule));
    }

    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }

    BigDecimal stock = trxQry.uniqueResult();
    if (stock == null) {
      stock = BigDecimal.ZERO;
    }
    if (existsCumulatedStockOnTrxDate) {
      stock = stock.add(cumulatedStock);
    }
    return stock.setScale(costingPrecision, RoundingMode.HALF_UP);
  }

  /**
   * Calculates the value of the stock of the product on the given date, for the given cost
   * dimensions and for the given currency. It only takes transactions that have its cost
   * calculated.
   */
  public static BigDecimal getValuedStockOnMovementDate(Product product, Organization org,
      Date _date, HashMap<CostDimension, BaseOBObject> _costDimensions, Currency currency,
      boolean backdatedTransactionsFixed) {
    return getValuedStockOnMovementDateByAttrAndLocator(product, org, _date, _costDimensions, null,
        null, currency, backdatedTransactionsFixed);
  }

  /**
   * Calculates the value of the stock of the product on the given date, for the given cost
   * dimensions and for the given currency. It only takes transactions that have its cost
   * calculated.
   */
  public static BigDecimal getValuedStockOnMovementDateByAttrAndLocator(Product product,
      Organization org, Date _date, HashMap<CostDimension, BaseOBObject> _costDimensions,
      Locator locator, AttributeSetInstance asi, Currency currency,
      boolean backdatedTransactionsFixed) {
    Date date = _date;
    HashMap<CostDimension, BaseOBObject> costDimensions = _costDimensions;

    // Get child tree of organizations.
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(org.getClient().getId());
    Set<String> orgs = osp.getChildTree(org.getId(), true);
    if (product.isProduction()) {
      orgs = osp.getChildTree("0", false);
      costDimensions = CostingUtils.getEmptyDimensions();
    }

    StringBuffer subSelect = new StringBuffer();
    subSelect.append(" select min(case when coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE
        + ", 'N') <> 'N' then trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " else trx."
        + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " end)");
    subSelect.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      subSelect.append("   join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    subSelect.append(
        "   left join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    subSelect.append("   left join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    subSelect.append(" where trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :product");
    subSelect.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :date");
    // Include only transactions that have its cost calculated
    subSelect.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      subSelect.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    subSelect.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");

    Query<Date> trxsubQry = OBDal.getInstance()
        .getSession()
        .createQuery(subSelect.toString(), Date.class);
    trxsubQry.setParameter("date", date);
    trxsubQry.setParameter("product", product.getId());
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxsubQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    trxsubQry.setParameterList("orgs", orgs);
    Date trxprocessDate = trxsubQry.uniqueResult();

    StringBuffer select = new StringBuffer();
    select.append(" select sum(case");
    select.append("     when trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY
        + " < 0 then -tc." + TransactionCost.PROPERTY_COST);
    select.append("     else tc." + TransactionCost.PROPERTY_COST + " end ) as cost");
    select.append(" , tc." + TransactionCost.PROPERTY_CURRENCY + ".id as currency");
    select.append(" , tc." + TransactionCost.PROPERTY_ACCOUNTINGDATE + " as mdate");
    select.append(" , sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") as stock");

    select.append(" from " + TransactionCost.ENTITY_NAME + " as tc");
    select.append("  join tc." + TransactionCost.PROPERTY_INVENTORYTRANSACTION + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("  join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }

    Date backdatedTrxFrom = null;
    if (backdatedTransactionsFixed) {
      CostingRule costRule = CostingUtils.getCostDimensionRule(org, date);
      backdatedTrxFrom = CostingUtils.getCostingRuleFixBackdatedFrom(costRule);
    }

    if (trxprocessDate != null
        && (!backdatedTransactionsFixed || trxprocessDate.before(backdatedTrxFrom))) {
      date = trxprocessDate;
      select.append(
          " left join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
      select.append(" left join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
      select.append(" where case when coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE
          + ", 'N') <> 'N' then trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " else trx."
          + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " end < :date");
    } else {
      select.append(" where trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " <= :date");
    }

    select.append(" and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    // Include only transactions that have its cost calculated
    select.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");

    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    if (locator != null) {
      select.append("   and trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " = :locator");
    }
    if (asi != null) {
      select.append("   and trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + " = :asi");
    }
    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");

    select.append(" group by tc." + TransactionCost.PROPERTY_CURRENCY);
    select.append("   , tc." + TransactionCost.PROPERTY_ACCOUNTINGDATE);

    Query<Object[]> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Object[].class);
    trxQry.setParameter("product", product);
    trxQry.setParameter("date", date);
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    if (locator != null) {
      trxQry.setParameter("locator", locator);
    }
    if (asi != null) {
      trxQry.setParameter("asi", asi);
    }
    trxQry.setParameterList("orgs", orgs);

    ScrollableResults scroll = trxQry.scroll(ScrollMode.FORWARD_ONLY);
    BigDecimal sum = BigDecimal.ZERO;
    try {
      while (scroll.next()) {
        Object[] resultSet = scroll.get();
        BigDecimal origAmt = (BigDecimal) resultSet[0];
        String origCurId = (String) resultSet[1];

        if (StringUtils.equals(origCurId, currency.getId())) {
          sum = sum.add(origAmt);
        } else {
          Currency origCur = OBDal.getInstance().get(Currency.class, origCurId);
          Date convDate = (Date) resultSet[2];
          sum = sum.add(FinancialUtils.getConvertedAmount(origAmt, origCur, currency, convDate, org,
              FinancialUtils.PRECISION_COSTING));
        }
      }
    } finally {
      scroll.close();
    }
    return sum;
  }

  /**
   * Calculates the value of the stock of the product on the given date, for the given cost
   * dimensions and for the given currency. It only takes transactions that have its cost
   * calculated.
   */
  public static BigDecimal getValuedStockOnTransactionDate(Organization costorg,
      MaterialTransaction trx, HashMap<CostDimension, BaseOBObject> _costDimensions,
      boolean isManufacturingProduct, boolean areBackdatedTrxFixed, Currency currency) {
    Date date = areBackdatedTrxFixed ? trx.getMovementDate() : trx.getTransactionProcessDate();
    Costing costing = AverageAlgorithm.getLastCumulatedCosting(date, trx.getProduct(),
        _costDimensions, costorg);
    return getValuedStockOnTransactionDate(costorg, trx, _costDimensions, isManufacturingProduct,
        areBackdatedTrxFixed, currency, costing);
  }

  /**
   * Calculates the value of the stock of the product on the given date, for the given cost
   * dimensions and for the given currency. It only takes transactions that have its cost
   * calculated.
   */
  public static BigDecimal getValuedStockOnTransactionDate(Organization costorg,
      MaterialTransaction trx, HashMap<CostDimension, BaseOBObject> _costDimensions,
      boolean isManufacturingProduct, boolean areBackdatedTrxFixed, Currency currency,
      Costing costing) {

    // Get child tree of organizations.
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId());
    Set<String> orgs = osp.getChildTree(costorg.getId(), true);
    HashMap<CostDimension, BaseOBObject> costDimensions = _costDimensions;
    if (isManufacturingProduct) {
      orgs = osp.getChildTree("0", false);
      costDimensions = CostingUtils.getEmptyDimensions();
    }
    CostingRule costingRule = CostingUtils.getCostDimensionRule(costorg,
        trx.getTransactionProcessDate());

    BigDecimal cumulatedValuation = null;
    int costingPrecision = currency.getCostingPrecision().intValue();
    MaterialTransaction ctrx = costing != null ? costing.getInventoryTransaction() : null;
    boolean existsCumulatedValuationOnTrxDate = ctrx != null
        && costing.getTotalStockValuation() != null;

    // Backdated transactions can't use cumulated values
    if (existsCumulatedValuationOnTrxDate && costingRule.isBackdatedTransactionsFixed()) {
      Date trxMovementDate = DateUtils.truncate(trx.getMovementDate(), Calendar.DATE);
      Date ctrxMovementDate = DateUtils.truncate(ctrx.getMovementDate(), Calendar.DATE);
      if (trxMovementDate.compareTo(ctrxMovementDate) < 0
          || (trxMovementDate.compareTo(ctrxMovementDate) == 0 && trx.getTransactionProcessDate()
              .compareTo(ctrx.getTransactionProcessDate()) <= 0)) {
        existsCumulatedValuationOnTrxDate = false;
      }
    }

    if (existsCumulatedValuationOnTrxDate) {
      cumulatedValuation = costing.getTotalStockValuation();
      if (!StringUtils.equals(costing.getCurrency().getId(), currency.getId())) {
        cumulatedValuation = FinancialUtils.getConvertedAmount(cumulatedValuation,
            costing.getCurrency(), currency, ctrx.getTransactionProcessDate(), costorg,
            FinancialUtils.PRECISION_COSTING);
      }
      if (StringUtils.equals(ctrx.getId(), trx.getId())) {
        return cumulatedValuation.setScale(costingPrecision, RoundingMode.HALF_UP);
      }
    }

    StringBuffer select = new StringBuffer();
    select.append(" select sum(case");
    select.append("     when trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY
        + " < 0 then -tc." + TransactionCost.PROPERTY_COST);
    select.append("     else tc." + TransactionCost.PROPERTY_COST + " end ) as cost");
    select.append(" , tc." + TransactionCost.PROPERTY_CURRENCY + ".id as currency");
    select.append(" , tc." + TransactionCost.PROPERTY_ACCOUNTINGDATE + " as mdate");
    select.append(" , sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") as stock");

    select.append("\n from " + TransactionCost.ENTITY_NAME + " as tc");
    select.append("\n  join tc." + TransactionCost.PROPERTY_INVENTORYTRANSACTION + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("\n  join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    select.append("\n , " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");

    select.append("\n where trxtype." + propADListReference + ".id = :refid");
    select.append(
        "  and trxtype." + propADListValue + " = trx." + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    select.append("  and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    // Include only transactions that have its cost calculated
    select.append("  and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");

    if (existsCumulatedValuationOnTrxDate) {
      if (costingRule.isBackdatedTransactionsFixed()) {
        select.append(" and (trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :cmvtdate");
        select.append(" or (trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :cmvtdate");
      }
      select.append(
          " and (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :ctrxdate");
      select.append(
          " or (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :ctrxdate");
      // If the costing Transaction is an M- exclude the M+ Transactions with same movementDate and
      // TrxProcessDate due to how data is going to be ordered in further queries using the priority
      if (costing.getInventoryTransaction().getMovementType().equals("M-")) {
        select.append(
            " and (( trx." + MaterialTransaction.PROPERTY_MOVEMENTTYPE + " <> 'M+' and trxtype."
                + CostAdjustmentUtils.propADListPriority + " > :ctrxtypeprio)");
      } else {
        select
            .append(" and (trxtype." + CostAdjustmentUtils.propADListPriority + " > :ctrxtypeprio");
      }
      select.append(" or (trxtype." + CostAdjustmentUtils.propADListPriority + " = :ctrxtypeprio");
      select.append(" and (trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " < :ctrxqty");
      select.append(" or (trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :ctrxqty");
      select.append(" and trx." + MaterialTransaction.PROPERTY_ID + " > :ctrxid");
      select.append(" ))))))");
      if (costingRule.isBackdatedTransactionsFixed()) {
        select.append(" ))");
      }
    }

    select.append("  and (");

    if (costingRule.isBackdatedTransactionsFixed()) {
      select
          .append("   ( trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :fixbdt");
      select.append(" and (");
    }

    // If there are more than one trx on the same trx process date filter out those types with less
    // priority and / or higher quantity.
    select.append("  trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :trxdate");
    select.append("  or (");
    select.append("   trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :trxdate");
    select.append("   and (");
    select.append("    trxtype." + propADListPriority + " < :trxtypeprio");
    select.append("    or (");
    select.append("     trxtype." + propADListPriority + " = :trxtypeprio");
    select.append("     and trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " > :trxqty");
    select.append("        or (");
    select.append("         trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :trxqty");
    select.append("         and trx." + MaterialTransaction.PROPERTY_ID + " <= :trxid");
    select.append(" ))))");

    if (costingRule.isBackdatedTransactionsFixed()) {
      select.append(" )) or (");
      select
          .append("   trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " >= :fixbdt");
      select.append("   and (trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " < :mvtdate");
      select.append("   or (");
      select.append("    trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :mvtdate");
      // If there are more than one trx on the same trx process date filter out those types with
      // less priority and / or higher quantity.
      select.append(" and (");
      select.append("  trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :trxdate");
      select.append("  or (");
      select
          .append("   trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :trxdate");
      select.append("   and (");
      select.append("    trxtype." + propADListPriority + " < :trxtypeprio");
      select.append("    or (");
      select.append("     trxtype." + propADListPriority + " = :trxtypeprio");
      select.append("     and trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " > :trxqty");
      select.append("        or (");
      select.append("         trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :trxqty");
      select.append("         and trx." + MaterialTransaction.PROPERTY_ID + " <= :trxid");
      select.append("  ))))))");
      select.append(" ))");
    }

    select.append(" )");

    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }

    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");

    select.append(" group by tc." + TransactionCost.PROPERTY_CURRENCY);
    select.append("   , tc." + TransactionCost.PROPERTY_ACCOUNTINGDATE);

    Query<Object[]> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Object[].class);
    trxQry.setParameter("refid", MovementTypeRefID);
    trxQry.setParameter("product", trx.getProduct());
    trxQry.setParameter("trxdate", trx.getTransactionProcessDate());
    trxQry.setParameter("trxtypeprio", getTrxTypePrio(trx.getMovementType()));
    trxQry.setParameter("trxqty", trx.getMovementQuantity());
    trxQry.setParameter("trxid", trx.getId());
    trxQry.setParameterList("orgs", orgs);

    if (existsCumulatedValuationOnTrxDate) {
      if (costingRule.isBackdatedTransactionsFixed()) {
        trxQry.setParameter("cmvtdate", ctrx.getMovementDate());
      }
      trxQry.setParameter("ctrxdate", ctrx.getTransactionProcessDate());
      trxQry.setParameter("ctrxtypeprio", getTrxTypePrio(ctrx.getMovementType()));
      trxQry.setParameter("ctrxqty", ctrx.getMovementQuantity());
      trxQry.setParameter("ctrxid", ctrx.getId());
    }

    if (costingRule.isBackdatedTransactionsFixed()) {
      trxQry.setParameter("mvtdate", trx.getMovementDate());
      trxQry.setParameter("fixbdt", CostingUtils.getCostingRuleFixBackdatedFrom(costingRule));
    }

    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }

    ScrollableResults scroll = trxQry.scroll(ScrollMode.FORWARD_ONLY);
    BigDecimal sum = BigDecimal.ZERO;
    try {
      while (scroll.next()) {
        Object[] resultSet = scroll.get();
        BigDecimal origAmt = (BigDecimal) resultSet[0];
        String origCurId = (String) resultSet[1];

        if (StringUtils.equals(origCurId, currency.getId())) {
          sum = sum.add(origAmt);
        } else {
          Currency origCur = OBDal.getInstance().get(Currency.class, origCurId);
          Date convDate = (Date) resultSet[2];
          sum = sum.add(FinancialUtils.getConvertedAmount(origAmt, origCur, currency, convDate,
              costorg, FinancialUtils.PRECISION_COSTING));
        }
      }
    } finally {
      scroll.close();
    }

    if (existsCumulatedValuationOnTrxDate) {
      sum = sum.add(cumulatedValuation);
    }
    return sum.setScale(costingPrecision, RoundingMode.HALF_UP);
  }

  /**
   * Returns the last transaction process date of a non backdated transactions for the given
   * movement date or previous date.
   */
  public static Date getLastTrxDateOfMvmntDate(Date refDate, Product product, Organization org,
      HashMap<CostDimension, BaseOBObject> costDimensions) {
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(org.getClient().getId());
    Set<String> orgs = osp.getChildTree(org.getId(), true);
    Warehouse wh = (Warehouse) costDimensions.get(CostDimension.Warehouse);

    // Calculate the transaction process date of the first transaction with a movement date
    // after the given date. Any transaction with a transaction process date after this min date on
    // the given date or before is a backdated transaction.
    StringBuffer select = new StringBuffer();
    select.append(
        " select min(trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + ") as date");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (wh != null) {
      select.append("    join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as loc");
    }
    select.append(" where trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    select.append("   and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    select.append("   and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :mvntdate");
    if (wh != null) {
      select.append("   and loc." + Locator.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    Query<Date> qryMinDate = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Date.class);
    qryMinDate.setParameterList("orgs", orgs);
    qryMinDate.setParameter("product", product);
    qryMinDate.setParameter("mvntdate", refDate);
    if (wh != null) {
      qryMinDate.setParameter("warehouse", wh);
    }
    Date minNextDate = qryMinDate.uniqueResult();
    if (minNextDate == null) {
      return null;
    }

    // Get the last transaction process date of transactions with movement date equal or before the
    // given date and a transaction process date before the previously calculated min date.
    select = new StringBuffer();
    select.append(
        " select max(trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + ") as date");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (wh != null) {
      select.append("    join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as loc");
    }
    select.append(" where trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    select.append("   and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    select.append("   and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " <= :mvntdate");
    select.append(
        "   and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :trxdate");
    if (wh != null) {
      select.append("   and loc." + Locator.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    Query<Date> qryMaxDate = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Date.class);
    qryMaxDate.setParameterList("orgs", orgs);
    qryMaxDate.setParameter("product", product);
    qryMaxDate.setParameter("mvntdate", refDate);
    qryMaxDate.setParameter("trxdate", minNextDate);
    if (wh != null) {
      qryMaxDate.setParameter("warehouse", wh);
    }

    return qryMaxDate.uniqueResult();
  }

  /**
   * Returns the priority of the given movementType.
   */
  public static long getTrxTypePrio(String mvmntType) {
    OBCriteria<org.openbravo.model.ad.domain.List> crList = OBDal.getInstance()
        .createCriteria(org.openbravo.model.ad.domain.List.class);
    crList.createAlias(propADListReference, "ref");
    crList.add(Restrictions.eq("ref.id", MovementTypeRefID));
    crList.add(Restrictions.eq(propADListValue, mvmntType));
    return ((org.openbravo.model.ad.domain.List) crList.uniqueResult()).getSequenceNumber();
  }
}
