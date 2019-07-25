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
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.enterprise.context.Dependent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.costing.CostingAlgorithm.CostDimension;
import org.openbravo.costing.CostingServer.TrxType;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.cost.CostAdjustment;
import org.openbravo.model.materialmgmt.cost.CostAdjustmentLine;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.transaction.InternalConsumptionLine;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ProductionLine;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;

@Dependent
public abstract class CostingAlgorithmAdjustmentImp {
  private static final Logger log4j = LogManager.getLogger();
  protected String strCostAdjLineId;
  protected String strCostAdjId;
  protected String strTransactionId;
  protected String strCostOrgId;
  protected String strCostCurrencyId;
  protected int costCurPrecission;
  protected int stdCurPrecission;
  protected TrxType trxType;
  protected String strCostingRuleId;
  protected Date startingDate;
  protected String strClientId;
  protected boolean isManufacturingProduct;
  protected boolean areBackdatedTrxFixed;
  protected boolean checkNegativeStockCorrection;
  protected Long nextLineNo;
  protected HashMap<CostDimension, String> costDimensionIds = new HashMap<CostDimension, String>();

  /**
   * Initializes class variables to perform the cost adjustment process. Variables are stored by the
   * ids instead of the BaseOBObject to be safe of session clearing.
   * 
   * @param costAdjLine
   *          The Cost Adjustment Line that it is processed.
   */
  protected void init(CostAdjustmentLine costAdjLine) {
    strCostAdjLineId = costAdjLine.getId();
    strCostAdjId = costAdjLine.getCostAdjustment().getId();
    MaterialTransaction transaction = costAdjLine.getInventoryTransaction();
    strTransactionId = transaction.getId();
    isManufacturingProduct = transaction.getProduct().isProduction();
    CostingServer costingServer = new CostingServer(transaction);
    strCostOrgId = costingServer.getOrganization().getId();
    strCostCurrencyId = transaction.getCurrency().getId();
    costCurPrecission = transaction.getCurrency().getCostingPrecision().intValue();
    stdCurPrecission = transaction.getCurrency().getStandardPrecision().intValue();
    trxType = CostingServer.TrxType.getTrxType(transaction);
    CostingRule costingRule = costingServer.getCostingRule();
    strCostingRuleId = costingRule.getId();
    startingDate = CostingUtils.getCostingRuleStartingDate(costingRule);
    strClientId = costingRule.getClient().getId();
    areBackdatedTrxFixed = costingRule.isBackdatedTransactionsFixed()
        && !transaction.getTransactionProcessDate()
            .before(CostingUtils.getCostingRuleFixBackdatedFrom(costingRule));

    HashMap<CostDimension, BaseOBObject> costDimensions = CostingUtils.getEmptyDimensions();
    // Production products cannot be calculated by warehouse dimension.
    if (costingRule.isWarehouseDimension()) {
      costDimensions.put(CostDimension.Warehouse, transaction.getStorageBin().getWarehouse());
    }
    for (CostDimension costDimension : costDimensions.keySet()) {
      String value = null;
      if (costDimensions.get(costDimension) != null) {
        value = (String) costDimensions.get(costDimension).getId();
      }
      costDimensionIds.put(costDimension, value);
    }
    try {
      checkNegativeStockCorrection = Preferences
          .getPreferenceValue(CostAdjustmentUtils.ENABLE_NEGATIVE_STOCK_CORRECTION_PREF, true,
              OBContext.getOBContext().getCurrentClient(),
              OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
              OBContext.getOBContext().getRole(), null)
          .equals(Preferences.YES);
    } catch (PropertyException e1) {
      checkNegativeStockCorrection = false;
    }
  }

  /**
   * Process to include in the Cost Adjustment the required lines of transactions whose cost needs
   * to be adjusted as a consequence of other lines already included.
   */
  protected void searchRelatedTransactionCosts(CostAdjustmentLine _costAdjLine) {
    boolean searchRelatedTransactions = true;
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
      searchRelatedTransactions = false;
    } else {
      costAdjLine = getCostAdjLine();
    }

    // Backdated transactions are inserted with a null adjustment amount, in case we are not
    // adjusting a Inventory Amount Update transaction.
    if (costAdjLine.isBackdatedTrx() && costAdjLine.getAdjustmentAmount() == null) {
      calculateBackdatedTrxAdjustment(costAdjLine);
    }

    // Negative stock correction are inserted with a null adjustment amount, in case we are not
    // adjusting a Inventory Closing transaction.
    if (costAdjLine.isNegativeStockCorrection() && costAdjLine.getAdjustmentAmount() == null) {
      calculateNegativeStockCorrectionAdjustmentAmount(costAdjLine);
    }

    if (costAdjLine.isSource()) {
      addCostDependingTrx(null);
      if (BigDecimal.ZERO.compareTo(costAdjLine.getAdjustmentAmount()) == 0) {
        costAdjLine.setNeedsPosting(Boolean.FALSE);
      }
    }

    if (searchRelatedTransactions) {
      getRelatedTransactionsByAlgorithm();
    }
  }

  protected void addCostDependingTrx(CostAdjustmentLine costAdjLine) {
    // Some transaction costs are directly related to other transaction costs. These relationships
    // must be kept when the original transaction cost is adjusted adjusting as well the dependent
    // transactions.
    TrxType _trxType = trxType;
    if (costAdjLine != null) {
      _trxType = TrxType.getTrxType(costAdjLine.getInventoryTransaction());
    }
    switch (_trxType) {
      case Shipment:
        searchReturnShipments(costAdjLine);
      case Receipt:
        searchVoidInOut(costAdjLine);
        break;
      case IntMovementFrom:
        searchIntMovementTo(costAdjLine);
        break;
      case InternalCons:
        searchVoidInternalConsumption(costAdjLine);
        break;
      case BOMPart:
        searchBOMProducts(costAdjLine);
        break;
      case ManufacturingConsumed:
        searchManufacturingProduced(costAdjLine);
        break;
      case InventoryDecrease:
      case InventoryIncrease:
        searchOpeningInventory(costAdjLine);
      default:
        break;
    }
  }

  protected CostAdjustmentLine insertCostAdjustmentLine(
      CostAdjustmentLineParameters lineParameters) {
    return insertCostAdjustmentLine(lineParameters, null);
  }

  @Deprecated
  protected CostAdjustmentLine insertCostAdjustmentLine(MaterialTransaction trx,
      BigDecimal adjustmentamt, CostAdjustmentLine _parentLine) {
    final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(trx,
        adjustmentamt, getCostAdj());
    return insertCostAdjustmentLine(lineParameters, _parentLine);

  }

  /**
   * Inserts a new cost adjustment line
   *
   * @param trx
   *          Material transaction
   * @param adjustmentamt
   *          Adjustment amount
   * @param _parentLine
   *          Cost Adjustment Line
   * 
   */
  protected CostAdjustmentLine insertCostAdjustmentLine(CostAdjustmentLineParameters lineParameters,
      CostAdjustmentLine _parentLine) {
    Date dateAcct = lineParameters.getTransaction().getMovementDate();

    CostAdjustmentLine parentLine;
    if (_parentLine == null) {
      parentLine = getCostAdjLine();
    } else {
      parentLine = _parentLine;
    }
    Date parentAcctDate = parentLine.getAccountingDate();
    if (parentAcctDate == null) {
      parentAcctDate = parentLine.getInventoryTransaction().getMovementDate();
    }

    if (dateAcct.before(parentAcctDate)) {
      dateAcct = parentAcctDate;
    }

    CostAdjustmentLine newCAL = CostAdjustmentUtils.insertCostAdjustmentLine(lineParameters,
        dateAcct, getNextLineNo());
    if (!newCAL.getId().equals(parentLine.getId())) {
      newCAL.setParentCostAdjustmentLine(parentLine);
      OBDal.getInstance().save(newCAL);
    }

    addCostDependingTrx(newCAL);
    return newCAL;
  }

  private Long getNextLineNo() {
    if (nextLineNo == null) {
      StringBuffer where = new StringBuffer();
      where.append(" select max(" + CostAdjustmentLine.PROPERTY_LINENO + ")");
      where.append(" from " + CostAdjustmentLine.ENTITY_NAME + " as cal");
      where.append(
          " where cal." + CostAdjustmentLine.PROPERTY_COSTADJUSTMENT + ".id = :costAdjustment");
      Query<Long> calQry = OBDal.getInstance()
          .getSession()
          .createQuery(where.toString(), Long.class);
      calQry.setParameter("costAdjustment", strCostAdjId);
      calQry.setMaxResults(1);

      nextLineNo = calQry.uniqueResult();
    }
    nextLineNo += 10L;
    return nextLineNo;
  }

  /**
   * When the cost of a Closing Inventory is adjusted it is needed to adjust with the same amount
   * the related Opening Inventory.
   */
  protected void searchOpeningInventory(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }
    InventoryCountLine invline = costAdjLine.getInventoryTransaction()
        .getPhysicalInventoryLine()
        .getRelatedInventory();
    if (invline == null) {
      return;
    }
    MaterialTransaction deptrx = invline.getMaterialMgmtMaterialTransactionList().get(0);
    if (!deptrx.isCostCalculated() || deptrx.isCostPermanent()) {
      return;
    }

    final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(deptrx,
        costAdjLine.getAdjustmentAmount(), getCostAdj());
    insertCostAdjustmentLine(lineParameters, _costAdjLine);
  }

  protected void searchManufacturingProduced(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }
    ProductionLine pl = costAdjLine.getInventoryTransaction().getProductionLine();

    OBCriteria<ProductionLine> critPL = OBDal.getInstance().createCriteria(ProductionLine.class);
    critPL.createAlias(ProductionLine.PROPERTY_PRODUCT, "pr");
    critPL.add(Restrictions.eq(ProductionLine.PROPERTY_PRODUCTIONPLAN, pl.getProductionPlan()));
    critPL.add(Restrictions.eq(ProductionLine.PROPERTY_PRODUCTIONTYPE, "+"));
    critPL.addOrderBy(ProductionLine.PROPERTY_COMPONENTCOST, true);

    BigDecimal pendingAmt = costAdjLine.getAdjustmentAmount();
    CostAdjustmentLine lastAdjLine = null;
    for (ProductionLine pline : critPL.list()) {
      BigDecimal adjAmt = costAdjLine.getAdjustmentAmount();
      if (pline.getComponentCost() != null) {
        adjAmt = costAdjLine.getAdjustmentAmount().multiply(pline.getComponentCost());
        pendingAmt = pendingAmt.subtract(adjAmt);
      }
      if (!pline.getProduct().isStocked() || !"I".equals(pline.getProduct().getProductType())) {
        continue;
      }
      if (pline.getMaterialMgmtMaterialTransactionList().isEmpty()) {
        log4j.error("Production Line with id {} has no related transaction (M_Transaction).",
            pline.getId());
        continue;
      }
      MaterialTransaction prodtrx = pline.getMaterialMgmtMaterialTransactionList().get(0);
      if (!prodtrx.isCostCalculated() || prodtrx.isCostPermanent()) {
        continue;
      }

      final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(prodtrx,
          adjAmt, getCostAdj());
      CostAdjustmentLine newCAL = insertCostAdjustmentLine(lineParameters, _costAdjLine);

      lastAdjLine = newCAL;
    }
    // If there is more than one P+ product there can be some amount left to assign due to rounding.
    if (pendingAmt.signum() != 0 && lastAdjLine != null) {
      lastAdjLine.setAdjustmentAmount(lastAdjLine.getAdjustmentAmount().add(pendingAmt));
      OBDal.getInstance().save(lastAdjLine);
    }
  }

  protected void searchBOMProducts(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }
    ProductionLine pl = costAdjLine.getInventoryTransaction().getProductionLine();
    OBCriteria<ProductionLine> critBOM = OBDal.getInstance().createCriteria(ProductionLine.class);
    critBOM.createAlias(ProductionLine.PROPERTY_PRODUCT, "pr");
    critBOM.add(Restrictions.eq(ProductionLine.PROPERTY_PRODUCTIONPLAN, pl.getProductionPlan()));
    critBOM.add(Restrictions.gt(ProductionLine.PROPERTY_MOVEMENTQUANTITY, BigDecimal.ZERO));
    critBOM.add(Restrictions.eq("pr." + Product.PROPERTY_STOCKED, true));
    critBOM.add(Restrictions.eq("pr." + Product.PROPERTY_PRODUCTTYPE, "I"));
    for (ProductionLine pline : critBOM.list()) {
      if (pline.getMaterialMgmtMaterialTransactionList().isEmpty()) {
        log4j.error("BOM Produced with id {} has no related transaction (M_Transaction).",
            pline.getId());
        continue;
      }
      MaterialTransaction prodtrx = pline.getMaterialMgmtMaterialTransactionList().get(0);
      if (!prodtrx.isCostCalculated() || prodtrx.isCostPermanent()) {
        continue;
      }
      final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(prodtrx,
          costAdjLine.getAdjustmentAmount(), getCostAdj());
      lineParameters.setUnitCost(true);
      insertCostAdjustmentLine(lineParameters, _costAdjLine);
    }
  }

  protected void searchVoidInternalConsumption(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }

    List<InternalConsumptionLine> intConsVoidedList = costAdjLine.getInventoryTransaction()
        .getInternalConsumptionLine()
        .getMaterialMgmtInternalConsumptionLineVoidedInternalConsumptionLineList();

    if (intConsVoidedList.isEmpty()) {
      return;
    }
    InternalConsumptionLine intCons = intConsVoidedList.get(0);
    MaterialTransaction voidedTrx = intCons.getMaterialMgmtMaterialTransactionList().get(0);
    if (!voidedTrx.isCostCalculated() || voidedTrx.isCostPermanent()) {
      return;
    }
    final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(voidedTrx,
        costAdjLine.getAdjustmentAmount(), getCostAdj());
    insertCostAdjustmentLine(lineParameters, _costAdjLine);
  }

  protected void searchIntMovementTo(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }
    MaterialTransaction transaction = costAdjLine.getInventoryTransaction();
    for (MaterialTransaction movementTransaction : transaction.getMovementLine()
        .getMaterialMgmtMaterialTransactionList()) {
      if (movementTransaction.getId().equals(transaction.getId())) {
        continue;
      }
      if (!movementTransaction.isCostCalculated() || movementTransaction.isCostPermanent()) {
        continue;
      }
      final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(
          movementTransaction, costAdjLine.getAdjustmentAmount(), getCostAdj());
      insertCostAdjustmentLine(lineParameters, _costAdjLine);
    }
  }

  protected void searchVoidInOut(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }
    ShipmentInOutLine voidedinoutline = costAdjLine.getInventoryTransaction()
        .getGoodsShipmentLine()
        .getCanceledInoutLine();
    if (voidedinoutline == null) {
      return;
    }
    for (MaterialTransaction trx : voidedinoutline.getMaterialMgmtMaterialTransactionList()) {
      if (!trx.isCostCalculated() || trx.isCostPermanent()) {
        continue;
      }
      final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(trx,
          costAdjLine.getAdjustmentAmount(), getCostAdj());
      insertCostAdjustmentLine(lineParameters, _costAdjLine);
    }
  }

  protected void searchReturnShipments(CostAdjustmentLine _costAdjLine) {
    CostAdjustmentLine costAdjLine;
    if (_costAdjLine != null) {
      costAdjLine = _costAdjLine;
    } else {
      costAdjLine = getCostAdjLine();
    }
    ShipmentInOutLine inoutline = costAdjLine.getInventoryTransaction().getGoodsShipmentLine();
    BigDecimal costAdjAmt = costAdjLine.getAdjustmentAmount();
    int precission = getCostCurrency().getStandardPrecision().intValue();
    StringBuffer where = new StringBuffer();
    where.append(" as trx");
    where.append(" join trx." + MaterialTransaction.PROPERTY_GOODSSHIPMENTLINE + " as iol");
    where.append(" join iol." + ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT + " as io");
    where.append(" join iol." + ShipmentInOutLine.PROPERTY_SALESORDERLINE + " as ol");
    where.append(" where ol." + OrderLine.PROPERTY_GOODSSHIPMENTLINE + " = :shipment");
    where.append(" and io." + ShipmentInOut.PROPERTY_DOCUMENTSTATUS + " <> 'VO'");
    OBQuery<MaterialTransaction> qryTrx = OBDal.getInstance()
        .createQuery(MaterialTransaction.class, where.toString());
    qryTrx.setFilterOnReadableOrganization(false);
    qryTrx.setNamedParameter("shipment", inoutline);
    ScrollableResults trxs = qryTrx.scroll(ScrollMode.FORWARD_ONLY);
    try {
      int counter = 0;
      while (trxs.next()) {
        counter++;

        MaterialTransaction trx = (MaterialTransaction) trxs.get()[0];
        if (trx.isCostCalculated() && !trx.isCostPermanent()) {
          BigDecimal adjAmt = costAdjAmt.multiply(trx.getMovementQuantity().abs())
              .divide(inoutline.getMovementQuantity().abs(), precission, RoundingMode.HALF_UP);
          final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(trx,
              adjAmt, getCostAdj());
          insertCostAdjustmentLine(lineParameters, _costAdjLine);
        }

        if (counter % 1000 == 0) {
          OBDal.getInstance().flush();
          OBDal.getInstance().getSession().clear();
        }
      }
    } finally {
      trxs.close();
    }
  }

  protected abstract void calculateNegativeStockCorrectionAdjustmentAmount(
      CostAdjustmentLine costAdjLine);

  protected abstract void getRelatedTransactionsByAlgorithm();

  protected void calculateBackdatedTrxAdjustment(CostAdjustmentLine costAdjLine) {
    BigDecimal adjAmt = BigDecimal.ZERO;
    TrxType calTrxType = TrxType.getTrxType(costAdjLine.getInventoryTransaction());

    if (costAdjLine.getInventoryTransaction().isCostPermanent() && costAdjLine.isUnitCost()) {
      costAdjLine.setCurrency(
          (Currency) OBDal.getInstance().getProxy(Currency.ENTITY_NAME, strCostCurrencyId));
      costAdjLine.setAdjustmentAmount(BigDecimal.ZERO);
      OBDal.getInstance().save(costAdjLine);
      return;
    }

    // Incoming transactions does not modify the calculated cost
    switch (calTrxType) {
      case ShipmentVoid:
      case ReceiptVoid:
      case IntMovementTo:
      case InternalConsVoid:
      case BOMProduct:
      case ManufacturingProduced:
        // The cost of these transaction types does not depend on the date it is calculated.
        break;

      case Receipt:
        if (hasOrder(costAdjLine)) {
          // If the receipt has a related order the cost amount does not depend on the date.
          break;
        }
        // Check receipt default on backdated date.
        adjAmt = getDefaultCostDifference(calTrxType, costAdjLine);
        break;
      case ShipmentReturn:
        if (hasReturnedReceipt(costAdjLine)) {
          // If the return receipt has a original receipt the cost amount does not depend on the
          // date.
          break;
        }
      case ShipmentNegative:
        // These transaction types are calculated using the default cost. Check if there is a
        // difference.
        adjAmt = getDefaultCostDifference(calTrxType, costAdjLine);
        break;
      case InventoryIncrease:
      case InventoryOpening:
        if (inventoryHasCost(costAdjLine)) {
          // If the inventory line defines a unit cost it does not depend on the date.
          break;
        }
      case InternalConsNegative:
        // These transaction types are calculated using the default cost. Check if there is a
        // difference.
        adjAmt = getDefaultCostDifference(calTrxType, costAdjLine);
        break;
      case InventoryClosing:
        adjAmt = getInventoryClosingAmt(costAdjLine);
        break;

      case Shipment:
      case ReceiptReturn:
      case ReceiptNegative:
      case InventoryDecrease:
      case IntMovementFrom:
      case InternalCons:
      case BOMPart:
      case ManufacturingConsumed:
        // These transactions are calculated as regular outgoing transactions. The adjustment amount
        // needs to be calculated by the algorithm.
        adjAmt = getOutgoingBackdatedTrxAdjAmt(costAdjLine);
      default:
        break;
    }
    costAdjLine.setCurrency(
        (Currency) OBDal.getInstance().getProxy(Currency.ENTITY_NAME, strCostCurrencyId));
    costAdjLine.setAdjustmentAmount(adjAmt);
    OBDal.getInstance().save(costAdjLine);

  }

  protected abstract BigDecimal getOutgoingBackdatedTrxAdjAmt(CostAdjustmentLine costAdjLine);

  protected BigDecimal getDefaultCostDifference(TrxType calTrxType,
      CostAdjustmentLine costAdjLine) {
    MaterialTransaction trx = costAdjLine.getInventoryTransaction();
    BusinessPartner bp = CostingUtils.getTrxBusinessPartner(trx, calTrxType);
    Organization costOrg = getCostOrg();
    Date trxDate = CostAdjustmentUtils.getLastTrxDateOfMvmntDate(trx.getMovementDate(),
        trx.getProduct(), costOrg, getCostDimensions());
    if (trxDate == null) {
      trxDate = trx.getTransactionProcessDate();
    }

    BigDecimal defaultCost = CostingUtils.getDefaultCost(trx.getProduct(),
        trx.getMovementQuantity(), costOrg, trxDate, trx.getMovementDate(), bp, getCostCurrency(),
        getCostDimensions());
    BigDecimal trxCalculatedCost = CostAdjustmentUtils.getTrxCost(trx, true, getCostCurrency());
    return defaultCost.subtract(trxCalculatedCost);
  }

  private BigDecimal getInventoryClosingAmt(CostAdjustmentLine costAdjLine) {
    MaterialTransaction trx = costAdjLine.getInventoryTransaction();
    // currentBalanceOnDate already includes the cost of the inventory closing. The balance after an
    // inventory closing should be zero, so the adjustment amount should be de current balance
    // negated.
    BigDecimal currentBalanceOnDate = CostAdjustmentUtils
        .getValuedStockOnMovementDateByAttrAndLocator(trx.getProduct(), getCostOrg(),
            trx.getMovementDate(), getCostDimensions(), trx.getStorageBin(),
            trx.getAttributeSetValue(), getCostCurrency(), true);

    return currentBalanceOnDate.negate();
  }

  /**
   * Checks if the goods receipt line of the adjustment line has a related purchase order line.
   * 
   * @param costAdjLine
   *          the adjustment line to check.
   * @return true if there is a related order line.
   */
  private boolean hasOrder(CostAdjustmentLine costAdjLine) {
    return costAdjLine.getInventoryTransaction().getGoodsShipmentLine() != null
        && costAdjLine.getInventoryTransaction().getGoodsShipmentLine().getSalesOrderLine() != null;
  }

  /**
   * Checks if the inventory line has a unit cost defined.
   * 
   * @param costAdjLine
   *          the adjustment line to check.
   * @return true if there is a unit cost.
   */
  private boolean inventoryHasCost(CostAdjustmentLine costAdjLine) {
    return costAdjLine.getInventoryTransaction().getPhysicalInventoryLine() != null
        && costAdjLine.getInventoryTransaction().getPhysicalInventoryLine().getCost() != null;
  }

  /**
   * Checks if the returned receipt line has a related original shipment line.
   * 
   * @param costAdjLine
   *          the adjustment line to check.
   * @return true if there is a original shipment line.
   */
  private boolean hasReturnedReceipt(CostAdjustmentLine costAdjLine) {
    OrderLine shipmentLine = costAdjLine.getInventoryTransaction()
        .getGoodsShipmentLine()
        .getSalesOrderLine();
    return shipmentLine != null && shipmentLine.getGoodsShipmentLine() != null;
  }

  public CostAdjustmentLine getCostAdjLine() {
    return OBDal.getInstance().get(CostAdjustmentLine.class, strCostAdjLineId);
  }

  public CostAdjustment getCostAdj() {
    return OBDal.getInstance().get(CostAdjustment.class, strCostAdjId);
  }

  public MaterialTransaction getTransaction() {
    return OBDal.getInstance().get(MaterialTransaction.class, strTransactionId);
  }

  public Organization getCostOrg() {
    return OBDal.getInstance().get(Organization.class, strCostOrgId);
  }

  public Currency getCostCurrency() {
    return OBDal.getInstance().get(Currency.class, strCostCurrencyId);
  }

  public CostingRule getCostingRule() {
    return OBDal.getInstance().get(CostingRule.class, strCostingRuleId);
  }

  public HashMap<CostDimension, BaseOBObject> getCostDimensions() {
    HashMap<CostDimension, BaseOBObject> costDimensions = new HashMap<CostDimension, BaseOBObject>();
    for (CostDimension costDimension : costDimensionIds.keySet()) {
      switch (costDimension) {
        case Warehouse:
          Warehouse warehouse = null;
          if (costDimensionIds.get(costDimension) != null) {
            warehouse = OBDal.getInstance()
                .get(Warehouse.class, costDimensionIds.get(costDimension));
          }
          costDimensions.put(costDimension, warehouse);
          break;
        default:
          break;
      }
    }

    return costDimensions;
  }
}
