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
import java.util.Set;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.Query;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.costing.CostingAlgorithm.CostDimension;
import org.openbravo.costing.CostingServer.TrxType;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.materialmgmt.cost.CostAdjustmentLine;
import org.openbravo.model.materialmgmt.cost.InvAmtUpdLnInventories;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;

@ComponentProvider.Qualifier("org.openbravo.costing.StandardAlgorithm")
public class StandardCostAdjustment extends CostingAlgorithmAdjustmentImp {

  @Override
  protected void getRelatedTransactionsByAlgorithm() {
    ScrollableResults trxs;
    MaterialTransaction transaction = getTransaction();

    // Case Inventory Amount Update backdated (modifying the cost in the past)
    if (trxType == TrxType.InventoryOpening) {
      // Search transactions with movement/process date after backdated Inventory Amount Update and
      // before next Inventory Amount Update and create an adjustment line for each transaction
      trxs = getLaterTransactions(transaction);
    }

    // Case transaction backdated (modifying the stock in the past)
    else {
      // Search opening inventories with movement date after backdated transaction and create an
      // adjustment line for any of opening transactions of each Inventory Amount Update
      trxs = getLaterOpeningTransactions(transaction);
    }

    int i = 0;
    try {
      while (trxs.next()) {
        MaterialTransaction trx = OBDal.getInstance().get(MaterialTransaction.class, trxs.get()[0]);
        BigDecimal adjAmount;

        if (trxType == TrxType.InventoryOpening) {
          BigDecimal cost = transaction.getPhysicalInventoryLine().getCost();
          adjAmount = trx.getMovementQuantity().abs().multiply(cost).subtract(trx.getTotalCost());
        } else {
          adjAmount = transaction.getMovementQuantity();
        }

        final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(trx,
            adjAmount, getCostAdj());
        lineParameters.setRelatedTransactionAdjusted(true);
        insertCostAdjustmentLine(lineParameters);

        i++;
        if (i % 100 == 0) {
          OBDal.getInstance().flush();
          OBDal.getInstance().getSession().clear();
        }
      }
    } finally {
      trxs.close();
    }
  }

  @Override
  protected BigDecimal getOutgoingBackdatedTrxAdjAmt(CostAdjustmentLine costAdjLine) {
    // Calculate the standard cost on the transaction's movement date and adjust the cost if needed.
    MaterialTransaction trx = costAdjLine.getInventoryTransaction();

    Date trxDate = CostAdjustmentUtils.getLastTrxDateOfMvmntDate(trx.getMovementDate(),
        trx.getProduct(), getCostOrg(), getCostDimensions());
    if (trxDate == null) {
      trxDate = trx.getTransactionProcessDate();
    }

    BigDecimal cost = CostingUtils.getStandardCost(trx.getProduct(), getCostOrg(), trxDate,
        getCostDimensions(), getCostCurrency());

    BigDecimal expectedCostAmt = trx.getMovementQuantity().abs().multiply(cost);
    BigDecimal currentCost = trx.getTransactionCost();
    return expectedCostAmt.subtract(currentCost);
  }

  @Override
  protected void calculateNegativeStockCorrectionAdjustmentAmount(CostAdjustmentLine costAdjLine) {
    // Do nothing
  }

  @Override
  protected void addCostDependingTrx(CostAdjustmentLine costAdjLine) {
    // Do nothing.
    // All transactions are calculated using the current standard cost so there is no need to
    // specifically search for dependent transactions.
  }

  /**
   * Returns transactions with movement/process date after trx and before next Inventory Amount
   * Update
   */
  private ScrollableResults getLaterTransactions(MaterialTransaction trx) {

    final OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId());
    Set<String> orgs = osp.getChildTree(strCostOrgId, true);
    HashMap<CostDimension, BaseOBObject> costDimensions = getCostDimensions();
    if (trx.getProduct().isProduction()) {
      orgs = osp.getChildTree("0", false);
      costDimensions = CostingUtils.getEmptyDimensions();
    }
    final Warehouse warehouse = (Warehouse) costDimensions.get(CostDimension.Warehouse);

    // Get the movement date of the first Inventory Amount Update after trx
    StringBuffer dateWhere = new StringBuffer();
    dateWhere.append(" select trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " as trxdate");
    dateWhere.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    dateWhere.append(" join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    dateWhere.append(" join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    dateWhere.append(" where trx." + MaterialTransaction.PROPERTY_CLIENT + " = :client");
    dateWhere.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    dateWhere.append(" and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    dateWhere.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    dateWhere.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :date");
    dateWhere.append(
        " and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :startdate");
    dateWhere.append(" and i." + InventoryCount.PROPERTY_INVENTORYTYPE + " = 'O'");
    if (warehouse != null) {
      dateWhere.append(" and i." + InventoryCount.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    dateWhere.append(" order by trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE);

    Query<Date> dateQry = OBDal.getInstance()
        .getSession()
        .createQuery(dateWhere.toString(), Date.class);
    dateQry.setParameter("client", trx.getClient());
    dateQry.setParameterList("orgs", orgs);
    dateQry.setParameter("product", trx.getProduct());
    dateQry.setParameter("date", trx.getMovementDate());
    dateQry.setParameter("startdate", startingDate);
    if (warehouse != null) {
      dateQry.setParameter("warehouse", warehouse);
    }
    dateQry.setMaxResults(1);
    Date date = dateQry.uniqueResult();

    // Get transactions with movement/process date after trx and before next Inventory Amount Update
    // (include closing inventory lines and exclude opening inventory lines of it)
    StringBuffer where = new StringBuffer();
    where.append(" select trx." + MaterialTransaction.PROPERTY_ID + " as trxid");
    where.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    where.append(" join trx. " + MaterialTransaction.PROPERTY_STORAGEBIN + " as l");
    where.append(" left join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    where.append(" left join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    where.append(" left join i."
        + InventoryCount.PROPERTY_INVENTORYAMOUNTUPDATELINEINVENTORIESCLOSEINVENTORYLIST
        + " as iaui");
    where.append(" where trx." + MaterialTransaction.PROPERTY_CLIENT + " = :client");
    where.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    where.append(" and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    where.append(" and coalesce(iaui." + InvAmtUpdLnInventories.PROPERTY_CAINVENTORYAMTLINE
        + ", '0') <> :iaul");
    where.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    where.append(
        " and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :startdate");
    where.append(" and coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE + ", 'N') <> 'O'");
    if (warehouse != null) {
      where.append(" and l." + Locator.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    if (areBackdatedTrxFixed) {
      where.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :dateFrom");
      if (date != null) {
        where.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " <= :dateTo");
      }
      where.append(" order by trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE);
    } else {
      where.append(" and case when coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE
          + ", 'N') <> 'N' then trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " else trx."
          + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " end > :dateFrom");
      if (date != null) {
        where.append(" and case when coalesce(i." + InventoryCount.PROPERTY_INVENTORYTYPE
            + ", 'N') <> 'N' then trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " else trx."
            + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " end <= :dateTo");
      }
      where.append(" order by trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
    }

    Query<String> qry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), String.class);
    qry.setParameter("client", trx.getClient());
    qry.setParameterList("orgs", orgs);
    qry.setParameter("product", trx.getProduct());
    qry.setParameter("iaul",
        trx.getPhysicalInventoryLine()
            .getPhysInventory()
            .getInventoryAmountUpdateLineInventoriesInitInventoryList()
            .get(0)
            .getCaInventoryamtline());
    qry.setParameter("startdate", startingDate);
    if (warehouse != null) {
      qry.setParameter("warehouse", warehouse);
    }
    qry.setParameter("dateFrom", trx.getMovementDate());
    if (date != null) {
      qry.setParameter("dateTo", date);
    }
    return qry.scroll(ScrollMode.FORWARD_ONLY);
  }

  /**
   * Returns opening physical inventory transactions created by a Inventory Amount Update and
   * created after trx
   */
  private ScrollableResults getLaterOpeningTransactions(MaterialTransaction trx) {

    final OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId());
    Set<String> orgs = osp.getChildTree(strCostOrgId, true);
    HashMap<CostDimension, BaseOBObject> costDimensions = getCostDimensions();
    if (trx.getProduct().isProduction()) {
      orgs = osp.getChildTree("0", false);
      costDimensions = CostingUtils.getEmptyDimensions();
    }
    final Warehouse warehouse = (Warehouse) costDimensions.get(CostDimension.Warehouse);

    StringBuffer where = new StringBuffer();
    where.append(" select min(trx." + MaterialTransaction.PROPERTY_ID + ") as trxid");
    where.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    where.append(" join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    where.append(" join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    where.append(
        " join i." + InventoryCount.PROPERTY_INVENTORYAMOUNTUPDATELINEINVENTORIESINITINVENTORYLIST
            + " as iaui");
    where.append(" where trx." + MaterialTransaction.PROPERTY_CLIENT + " = :client");
    where.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    where.append(" and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    where.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    where.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :date");
    where.append(
        " and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :startdate");
    where.append(" and i." + InventoryCount.PROPERTY_INVENTORYTYPE + " = 'O'");
    if (warehouse != null) {
      where.append(" and iaui." + InvAmtUpdLnInventories.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    where.append(" group by iaui." + InvAmtUpdLnInventories.PROPERTY_CAINVENTORYAMTLINE);
    if (warehouse != null) {
      where.append(" , iaui." + InvAmtUpdLnInventories.PROPERTY_WAREHOUSE);
    }
    where.append(" order by min(trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + ")");

    Query<String> qry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), String.class);
    qry.setParameter("client", trx.getClient());
    qry.setParameterList("orgs", orgs);
    qry.setParameter("product", trx.getProduct());
    qry.setParameter("date", trx.getMovementDate());
    qry.setParameter("startdate", startingDate);
    if (warehouse != null) {
      qry.setParameter("warehouse", warehouse);
    }
    return qry.scroll(ScrollMode.FORWARD_ONLY);
  }
}
