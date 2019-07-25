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
 * All portions are Copyright (C) 2012-2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import org.openbravo.base.exception.OBException;
import org.openbravo.costing.CostingServer.TrxType;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.Costing;

public class StandardAlgorithm extends CostingAlgorithm {

  @Override
  public BigDecimal getTransactionCost() {
    switch (trxType) {
      case InventoryOpening:
        BigDecimal unitCost = transaction.getPhysicalInventoryLine().getCost();
        if (unitCost != null && unitCost.signum() != 0) {
          return getOpeningInventoryCost();
        }
      default:
    }
    return getOutgoingTransactionCost();
  }

  @Override
  protected BigDecimal getOutgoingTransactionCost() {
    Date date;
    if (costingRule.isBackdatedTransactionsFixed() || trxType == TrxType.InventoryOpening
        || trxType == TrxType.InventoryClosing) {
      date = transaction.getMovementDate();
    } else {
      date = transaction.getTransactionProcessDate();
    }
    BigDecimal standardCost = CostingUtils.getStandardCost(transaction.getProduct(), costOrg, date,
        costDimensions, costCurrency);
    return standardCost.multiply(transaction.getMovementQuantity().abs());
  }

  /**
   * Opening Inventories with a cost defined are calculated using that unit cost. A new Standard
   * Cost is also entered.
   * 
   * @return The cost of the transaction based on the unit cost defined in the inventory line.
   */
  private BigDecimal getOpeningInventoryCost() {
    BigDecimal unitCost = transaction.getPhysicalInventoryLine()
        .getCost()
        .setScale(costCurrency.getCostingPrecision().intValue(), RoundingMode.HALF_UP);
    Costing stdCost = CostingUtils.getStandardCostDefinition(transaction.getProduct(), costOrg,
        transaction.getMovementDate(), costDimensions);

    if (stdCost == null) {
      // If no standard cost is found throw an exception.
      throw new OBException("@NoStandardCostDefined@ @Organization@:" + costOrg.getName()
          + ", @Product@: " + transaction.getProduct().getName() + ", @Date@: "
          + OBDateUtils.formatDate(transaction.getMovementDate()));
    }
    BigDecimal currentCost = stdCost.getCost();
    if (stdCost.getCurrency().getId().equals(costCurrency.getId())) {
      currentCost = FinancialUtils.getConvertedAmount(currentCost, stdCost.getCurrency(),
          costCurrency, transaction.getMovementDate(), costOrg, "C");
    }
    if (currentCost.compareTo(unitCost) == 0) {
      // Unit cost and current cost don't change return regular outgoing cost and do not create a
      // new costing entry.
      return getOutgoingTransactionCost();
    }
    BigDecimal trxCost = unitCost.multiply(transaction.getMovementQuantity().abs());
    insertCost(stdCost, unitCost);

    return trxCost;
  }

  private void insertCost(Costing currentCosting, BigDecimal newCost) {
    Costing costing = (Costing) DalUtil.copy(currentCosting, false);
    costing.setCost(newCost);
    costing.setStartingDate(transaction.getMovementDate());
    costing.setCurrency(costCurrency);
    costing.setInventoryTransaction(transaction);
    if (transaction.getProduct().isProduction()) {
      costing.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
    } else {
      costing.setOrganization(costOrg);
    }
    costing.setCostType("STA");
    costing.setManual(false);
    costing.setPermanent(true);
    OBDal.getInstance().save(costing);

    currentCosting.setEndingDate(transaction.getMovementDate());
    OBDal.getInstance().save(currentCosting);
  }

}
