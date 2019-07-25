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
 * All portions are Copyright (C) 2012-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.costing.CostingAlgorithm.CostDimension;
import org.openbravo.costing.CostingServer.TrxType;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.materialmgmt.cost.Costing;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.cost.InvAmtUpdLnInventories;
import org.openbravo.model.materialmgmt.cost.TransactionCost;
import org.openbravo.model.materialmgmt.onhandquantity.InventoryStatus;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.pricing.pricelist.ProductPrice;
import org.openbravo.service.db.DalConnectionProvider;

public class CostingUtils {
  private static Logger log4j = LogManager.getLogger();
  public static final String propADListPriority = org.openbravo.model.ad.domain.List.PROPERTY_SEQUENCENUMBER;
  public static final String propADListValue = org.openbravo.model.ad.domain.List.PROPERTY_SEARCHKEY;
  public static final String propADListReference = org.openbravo.model.ad.domain.List.PROPERTY_REFERENCE;
  public static final String MovementTypeRefID = "189";

  /**
   * Calls {@link #getTransactionCost(MaterialTransaction, Date, boolean, Currency)} setting the
   * calculateTrx flag to false.
   */
  public static BigDecimal getTransactionCost(MaterialTransaction transaction, Date date,
      Currency currency) {
    return getTransactionCost(transaction, date, false, currency);
  }

  /**
   * Calculates the total cost amount of a transaction including the cost adjustments done until the
   * given date.
   * 
   * @param transaction
   *          MaterialTransaction to get its cost.
   * @param date
   *          The Date it is desired to know the cost.
   * @param calculateTrx
   *          boolean flag to force the calculation of the transaction cost if it is not calculated.
   * @param currency
   *          The Currency to calculate the amount.
   * @return The total cost amount.
   */
  public static BigDecimal getTransactionCost(MaterialTransaction transaction, Date date,
      boolean calculateTrx, Currency currency) {
    log4j.debug("Get Transaction Cost");
    OBError result = new OBError();
    try {
      OBContext.setAdminMode(true);
      result.setType("Success");
      result.setTitle(OBMessageUtils.messageBD("Success"));
      if (!transaction.isCostCalculated()) {
        // Transaction hasn't been calculated yet.
        if (calculateTrx) {
          log4j.debug(
              "  *** Cost for transaction will be calculated." + transaction.getIdentifier());
          CostingServer transactionCost = new CostingServer(transaction);
          transactionCost.process();
          return transactionCost.getTransactionCost();
        }
        log4j.error("  *** No cost found for transaction " + transaction.getIdentifier()
            + " with id " + transaction.getId() + " on date " + OBDateUtils.formatDate(date));
        throw new OBException("@NoCostFoundForTrxOnDate@ @Transaction@: "
            + transaction.getIdentifier() + " @Date@ " + OBDateUtils.formatDate(date));
      }
      BigDecimal cost = BigDecimal.ZERO;
      for (TransactionCost trxCost : transaction.getTransactionCostList()) {
        if (!trxCost.getCostDate().after(date)) {
          cost = cost.add(FinancialUtils.getConvertedAmount(trxCost.getCost(),
              trxCost.getCurrency(), currency, trxCost.getCostDate(), trxCost.getOrganization(),
              FinancialUtils.PRECISION_COSTING));
        }
      }
      return cost;
    } catch (OBException e) {
      OBDal.getInstance().rollbackAndClose();
      log4j.error(result.getMessage(), e);
      return null;
    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      log4j.error(result.getMessage(), e);
      return null;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  public static BigDecimal getDefaultCost(Product product, BigDecimal qty, Organization org,
      Date costDate, Date movementDate, BusinessPartner bp, Currency currency,
      HashMap<CostDimension, BaseOBObject> costDimensions) {
    Costing stdCost = getStandardCostDefinition(product, org, costDate, costDimensions);
    PriceList pricelist = null;
    if (bp != null) {
      pricelist = bp.getPurchasePricelist();
    }
    ProductPrice pp = FinancialUtils.getProductPrice(product, movementDate, false, pricelist, false,
        false);
    if (stdCost == null && pp == null) {
      throw new OBException(
          "@NoPriceListOrStandardCostForProduct@ @Organization@: " + org.getName() + ", @Product@: "
              + product.getSearchKey() + ", @Date@: " + OBDateUtils.formatDate(costDate));
    } else if (stdCost != null && pp == null) {
      BigDecimal standardCost = getStandardCost(product, org, costDate, costDimensions, currency);
      return qty.abs().multiply(standardCost);
    } else if (stdCost == null && pp != null) {
      BigDecimal cost = pp.getStandardPrice().multiply(qty.abs());
      if (pp.getPriceListVersion().getPriceList().getCurrency().getId().equals(currency.getId())) {
        // no conversion needed
        return cost;
      }
      return FinancialUtils.getConvertedAmount(cost,
          pp.getPriceListVersion().getPriceList().getCurrency(), currency, movementDate, org,
          FinancialUtils.PRECISION_STANDARD);

    } else if (stdCost != null && pp != null
        && stdCost.getStartingDate().before(pp.getPriceListVersion().getValidFromDate())) {
      BigDecimal cost = pp.getStandardPrice().multiply(qty.abs());
      if (pp.getPriceListVersion().getPriceList().getCurrency().getId().equals(currency.getId())) {
        // no conversion needed
        return cost;
      }
      return FinancialUtils.getConvertedAmount(cost,
          pp.getPriceListVersion().getPriceList().getCurrency(), currency, movementDate, org,
          FinancialUtils.PRECISION_STANDARD);
    } else {
      BigDecimal standardCost = getStandardCost(product, org, costDate, costDimensions, currency);
      return qty.abs().multiply(standardCost);
    }
  }

  /**
   * Calls {@link #getStandardCost(Product, Organization, Date, HashMap, boolean, Currency)} setting
   * the recheckWithoutDimensions flag to true.
   */
  public static BigDecimal getStandardCost(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions, Currency convCurrency)
      throws OBException {
    return getStandardCost(product, org, date, costDimensions, true, convCurrency);
  }

  /**
   * Calculates the standard cost of a product on the given date and cost dimensions.
   * 
   * @param product
   *          The Product to get its Standard Cost
   * @param date
   *          The Date to get the Standard Cost
   * @param costDimensions
   *          The cost dimensions to get the Standard Cost if it is defined by some of them.
   * @param recheckWithoutDimensions
   *          boolean flag to force a recall the method to get the Standard Cost at client level if
   *          no cost is found in the given cost dimensions.
   * @param convCurrency
   *          The Currency to calculate the amount.
   * @return the Standard Cost.
   * @throws OBException
   *           when no standard cost is found.
   */
  public static BigDecimal getStandardCost(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean recheckWithoutDimensions,
      Currency convCurrency) throws OBException {
    Costing stdCost = getStandardCostDefinition(product, org, date, costDimensions,
        recheckWithoutDimensions);
    if (stdCost == null) {
      // If no standard cost is found throw an exception.
      throw new OBException("@NoStandardCostDefined@ @Organization@:" + org.getName()
          + ", @Product@: " + product.getName() + ", @Date@: " + OBDateUtils.formatDate(date));
    }
    return FinancialUtils.getConvertedAmount(stdCost.getCost(), stdCost.getCurrency(), convCurrency,
        date, org, FinancialUtils.PRECISION_COSTING);
  }

  /**
   * Calls {@link #hasStandardCostDefinition(Product, Organization, Date, HashMap, boolean)} setting
   * the recheckWithoutDimensions flag to true.
   */
  public static boolean hasStandardCostDefinition(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions) {
    return hasStandardCostDefinition(product, org, date, costDimensions, true);
  }

  /**
   * Check the existence of a standard cost definition of a product on the given date and cost
   * dimensions.
   * 
   * @param product
   *          The Product to get its Standard Cost
   * @param date
   *          The Date to get the Standard Cost
   * @param costDimensions
   *          The cost dimensions to get the Standard Cost if it is defined by some of them.
   * @param recheckWithoutDimensions
   *          boolean flag to force a recall the method to get the Standard Cost at client level if
   *          no cost is found in the given cost dimensions.
   * @return the Standard Cost. Null when no definition is found.
   */
  public static boolean hasStandardCostDefinition(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean recheckWithoutDimensions) {
    return getStandardCostDefinition(product, org, date, costDimensions,
        recheckWithoutDimensions) != null;
  }

  /**
   * Calls {@link #getStandardCostDefinition(Product, Organization, Date, HashMap, boolean)} setting
   * the recheckWithoutDimensions flag to true.
   */
  public static Costing getStandardCostDefinition(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions) {
    return getStandardCostDefinition(product, org, date, costDimensions, true);
  }

  /**
   * Calculates the standard cost definition of a product on the given date and cost dimensions.
   * 
   * @param product
   *          The Product to get its Standard Cost
   * @param date
   *          The Date to get the Standard Cost
   * @param costDimensions
   *          The cost dimensions to get the Standard Cost if it is defined by some of them.
   * @param recheckWithoutDimensions
   *          boolean flag to force a recall the method to get the Standard Cost at client level if
   *          no cost is found in the given cost dimensions.
   * @return the Standard Cost. Null when no definition is found.
   */
  public static Costing getStandardCostDefinition(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean recheckWithoutDimensions) {
    Costing stdCost = getStandardCostDefinition(product, org, date, costDimensions,
        recheckWithoutDimensions, "STA");
    if (stdCost != null) {
      return stdCost;
    } else {
      // If no cost is found, search valid legacy cost
      return getStandardCostDefinition(product, org, date, costDimensions, recheckWithoutDimensions,
          "ST");
    }
  }

  /**
   * Calculates the standard cost definition of a product on the given date and cost dimensions.
   * 
   * @param product
   *          The Product to get its Standard Cost
   * @param date
   *          The Date to get the Standard Cost
   * @param costDimensions
   *          The cost dimensions to get the Standard Cost if it is defined by some of them.
   * @param recheckWithoutDimensions
   *          boolean flag to force a recall the method to get the Standard Cost at client level if
   *          no cost is found in the given cost dimensions.
   * @return the Standard Cost. Null when no definition is found.
   */
  public static Costing getStandardCostDefinition(Product product, Organization org, Date date,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean recheckWithoutDimensions,
      String costtype) {
    // Get cost from M_Costing for given date.
    OBCriteria<Costing> obcCosting = OBDal.getInstance().createCriteria(Costing.class);
    obcCosting.add(Restrictions.eq(Costing.PROPERTY_PRODUCT, product));
    obcCosting.add(Restrictions.le(Costing.PROPERTY_STARTINGDATE, date));
    obcCosting.add(Restrictions.gt(Costing.PROPERTY_ENDINGDATE, date));
    obcCosting.add(Restrictions.eq(Costing.PROPERTY_COSTTYPE, costtype));
    obcCosting.add(Restrictions.isNotNull(Costing.PROPERTY_COST));
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      obcCosting.add(
          Restrictions.eq(Costing.PROPERTY_WAREHOUSE, costDimensions.get(CostDimension.Warehouse)));
    }
    obcCosting.add(Restrictions.eq(Costing.PROPERTY_ORGANIZATION, org));
    obcCosting.setFilterOnReadableOrganization(false);
    obcCosting.setMaxResults(2);

    List<Costing> obcCostingList = obcCosting.list();
    int size = obcCostingList.size();
    if (size != 0) {
      if (size > 1) {
        log4j.warn("More than one cost found for same date: " + OBDateUtils.formatDate(date)
            + " for product: " + product.getName() + " (" + product.getId() + ")");
      }
      return obcCostingList.get(0);
    } else if (recheckWithoutDimensions) {
      return getStandardCostDefinition(product, org, date, getEmptyDimensions(), false);
    }
    return null;
  }

  /**
   * @return The costDimensions HashMap with null values for the dimensions.
   */
  public static HashMap<CostDimension, BaseOBObject> getEmptyDimensions() {
    HashMap<CostDimension, BaseOBObject> costDimensions = new HashMap<CostDimension, BaseOBObject>();
    costDimensions.put(CostDimension.Warehouse, null);
    return costDimensions;
  }

  /**
   * Calculates the stock of the product on the given date and for the given cost dimensions. It
   * only takes transactions that have its cost calculated.
   */
  public static BigDecimal getCurrentStock(MaterialTransaction trx, Organization org,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean areBackdatedTrxFixed,
      Currency currency) {
    Product product = trx.getProduct();
    Date date = areBackdatedTrxFixed ? trx.getMovementDate() : trx.getTransactionProcessDate();
    Costing costing = AverageAlgorithm.getLastCumulatedCosting(date, product, costDimensions, org);
    return getCurrentStock(product, org, trx.getTransactionProcessDate(), costDimensions, currency,
        costing);
  }

  /**
   * Calculates the stock of the product on the given date and for the given cost dimensions. It
   * only takes transactions that have its cost calculated.
   */
  public static BigDecimal getCurrentStock(Product product, Organization costorg, Date dateTo,
      HashMap<CostDimension, BaseOBObject> costDimensions, Currency currency, Costing costing) {
    // Get child tree of organizations.
    Set<String> orgs = OBContext.getOBContext()
        .getOrganizationStructureProvider()
        .getChildTree(costorg.getId(), true);
    CostingRule costingRule = CostingUtils.getCostDimensionRule(costorg, dateTo);

    MaterialTransaction ctrx = costing != null ? costing.getInventoryTransaction() : null;
    boolean existsCumulatedStock = ctrx != null && costing.getTotalMovementQuantity() != null;

    StringBuffer select = new StringBuffer();
    select
        .append(" select sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") as stock");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append(" join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    if (existsCumulatedStock) {
      select.append(", " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");
    }
    select.append(" where trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :product");
    select
        .append(" and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " <= :dateTo");
    if (existsCumulatedStock) {
      select.append(" and trxtype." + CostAdjustmentUtils.propADListValue + " = trx."
          + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
      select.append(" and trxtype." + CostAdjustmentUtils.propADListReference + ".id = :refid");
      if (costingRule.isBackdatedTransactionsFixed()) {
        select.append(
            "   and  trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " >= :fixbdt");
        select.append("  and (");
        select.append("   trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :mvtdate");
        select.append("   or (");
        select.append("    trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :mvtdate");
      }
      select.append(
          " and (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :dateFrom");
      select.append(
          " or (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :dateFrom");
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
    // Include only transactions that have its cost calculated
    select.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append(" and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    if (product.isProduction()) {
      select.append(" and trx." + MaterialTransaction.PROPERTY_CLIENT + ".id = :client");
    } else {
      select.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    }
    Query<BigDecimal> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), BigDecimal.class);
    trxQry.setParameter("product", product.getId());
    trxQry.setParameter("dateTo", dateTo);
    if (existsCumulatedStock) {
      if (costingRule.isBackdatedTransactionsFixed()) {
        trxQry.setParameter("fixbdt", getCostingRuleFixBackdatedFrom(costingRule));
        trxQry.setParameter("mvtdate", costing.getInventoryTransaction().getMovementDate());
      }
      trxQry.setParameter("refid", CostAdjustmentUtils.MovementTypeRefID);
      trxQry.setParameter("dateFrom", costing.getStartingDate());
      trxQry.setParameter("ctrxtypeprio",
          CostAdjustmentUtils.getTrxTypePrio(ctrx.getMovementType()));
      trxQry.setParameter("ctrxqty", ctrx.getMovementQuantity());
      trxQry.setParameter("ctrxid", ctrx.getId());
    }
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    if (product.isProduction()) {
      trxQry.setParameter("client", product.getClient().getId());
    } else {
      trxQry.setParameterList("orgs", orgs);
    }
    BigDecimal stock = trxQry.uniqueResult();
    if (stock == null) {
      stock = BigDecimal.ZERO;
    }
    if (existsCumulatedStock) {
      stock = stock.add(costing.getTotalMovementQuantity());
    }

    int costingPrecision = currency.getCostingPrecision().intValue();
    return stock.setScale(costingPrecision, RoundingMode.HALF_UP);
  }

  /**
   * Calculates the value of the stock of the product on the given date, for the given cost
   * dimensions and for the given currency. It only takes transactions that have its cost
   * calculated.
   */
  public static BigDecimal getCurrentValuedStock(MaterialTransaction trx, Organization org,
      HashMap<CostDimension, BaseOBObject> costDimensions, boolean areBackdatedTrxFixed,
      Currency currency) {
    Product product = trx.getProduct();
    Date date = areBackdatedTrxFixed ? trx.getMovementDate() : trx.getTransactionProcessDate();
    Costing costing = AverageAlgorithm.getLastCumulatedCosting(date, product, costDimensions, org);
    return getCurrentValuedStock(product, org, trx.getTransactionProcessDate(), costDimensions,
        currency, costing);
  }

  /**
   * Calculates the value of the stock of the product on the given date, for the given cost
   * dimensions and for the given currency. It only takes transactions that have its cost
   * calculated.
   */
  public static BigDecimal getCurrentValuedStock(Product product, Organization costorg, Date dateTo,
      HashMap<CostDimension, BaseOBObject> costDimensions, Currency currency, Costing costing) {
    // Get child tree of organizations.
    Set<String> orgs = OBContext.getOBContext()
        .getOrganizationStructureProvider()
        .getChildTree(costorg.getId(), true);
    CostingRule costingRule = CostingUtils.getCostDimensionRule(costorg, dateTo);

    MaterialTransaction ctrx = costing != null ? costing.getInventoryTransaction() : null;
    boolean existsCumulatedValuation = ctrx != null && costing.getTotalStockValuation() != null;

    StringBuffer select = new StringBuffer();
    select.append(" select sum(case");
    select.append(" when trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " < 0 then -tc."
        + TransactionCost.PROPERTY_COST);
    select.append(" else tc." + TransactionCost.PROPERTY_COST + " end ) as cost,");
    select.append(" tc." + TransactionCost.PROPERTY_CURRENCY + ".id as currency,");
    select.append(" tc." + TransactionCost.PROPERTY_ACCOUNTINGDATE + " as mdate");

    select.append(" from " + TransactionCost.ENTITY_NAME + " as tc");
    select.append(" join tc." + TransactionCost.PROPERTY_INVENTORYTRANSACTION + " as trx");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append(" join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    }
    if (existsCumulatedValuation) {
      select.append(", " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");
    }

    select.append(" where trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :product");
    select
        .append(" and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " <= :dateTo");
    if (existsCumulatedValuation) {
      select.append(" and trxtype." + CostAdjustmentUtils.propADListValue + " = trx."
          + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
      select.append(" and trxtype." + CostAdjustmentUtils.propADListReference + ".id = :refid");
      if (costingRule.isBackdatedTransactionsFixed()) {
        select.append(
            "   and  trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " >= :fixbdt");
        select.append("  and (");
        select.append("   trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :mvtdate");
        select.append("   or (");
        select.append("    trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :mvtdate");
      }
      select.append(
          " and (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " > :dateFrom");
      select.append(
          " or (trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :dateFrom");
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
    // Include only transactions that have its cost calculated
    select.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append(" and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    if (product.isProduction()) {
      select.append(" and trx." + MaterialTransaction.PROPERTY_CLIENT + ".id = :client");
    } else {
      select.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    }
    select.append(" group by tc." + TransactionCost.PROPERTY_CURRENCY + ",");
    select.append(" tc." + TransactionCost.PROPERTY_ACCOUNTINGDATE);

    Query<Object[]> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Object[].class);
    trxQry.setParameter("product", product.getId());
    trxQry.setParameter("dateTo", dateTo);
    if (existsCumulatedValuation) {
      if (costingRule.isBackdatedTransactionsFixed()) {
        trxQry.setParameter("fixbdt", getCostingRuleFixBackdatedFrom(costingRule));
        trxQry.setParameter("mvtdate", costing.getInventoryTransaction().getMovementDate());
      }
      trxQry.setParameter("refid", CostAdjustmentUtils.MovementTypeRefID);
      trxQry.setParameter("dateFrom", costing.getStartingDate());
      trxQry.setParameter("ctrxtypeprio",
          CostAdjustmentUtils.getTrxTypePrio(ctrx.getMovementType()));
      trxQry.setParameter("ctrxqty", ctrx.getMovementQuantity());
      trxQry.setParameter("ctrxid", ctrx.getId());
    }
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      trxQry.setParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    if (product.isProduction()) {
      trxQry.setParameter("client", product.getClient().getId());
    } else {
      trxQry.setParameterList("orgs", orgs);
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

    if (existsCumulatedValuation) {
      BigDecimal costingValuedStock = costing.getTotalStockValuation();
      if (!StringUtils.equals(costing.getCurrency().getId(), currency.getId())) {
        costingValuedStock = FinancialUtils.getConvertedAmount(costingValuedStock,
            costing.getCurrency(), currency, costing.getStartingDate(), costorg,
            FinancialUtils.PRECISION_COSTING);
      }
      sum = sum.add(costingValuedStock);
    }

    int costingPrecision = currency.getCostingPrecision().intValue();
    return sum.setScale(costingPrecision, RoundingMode.HALF_UP);
  }

  public static BusinessPartner getTrxBusinessPartner(MaterialTransaction transaction,
      TrxType trxType) {
    switch (trxType) {
      case Receipt:
      case ReceiptNegative:
      case ReceiptReturn:
      case ReceiptVoid:
      case Shipment:
      case ShipmentNegative:
      case ShipmentReturn:
      case ShipmentVoid:
        return transaction.getGoodsShipmentLine().getShipmentReceipt().getBusinessPartner();
      default:
        return null;
    }
  }

  /**
   * Returns the newer order line for the given product, business partner and organization.
   */
  public static OrderLine getOrderLine(Product product, BusinessPartner bp, Organization org) {
    OrganizationStructureProvider osp = OBContext.getOBContext().getOrganizationStructureProvider();

    StringBuffer where = new StringBuffer();
    where.append(" as ol");
    where.append("   join ol." + OrderLine.PROPERTY_SALESORDER + " as o");
    where.append("   join o." + Order.PROPERTY_DOCUMENTTYPE + " as dt");
    where.append(" where o." + Order.PROPERTY_BUSINESSPARTNER + " = :bp");
    where.append("   and ol." + OrderLine.PROPERTY_PRODUCT + " = :product");
    where.append("   and o." + Order.PROPERTY_ORGANIZATION + ".id in :org");
    where.append("   and o." + Order.PROPERTY_DOCUMENTSTATUS + " in ('CO', 'CL')");
    where.append("   and o." + Order.PROPERTY_SALESTRANSACTION + " = false");
    where.append("   and dt." + DocumentType.PROPERTY_RETURN + " = false");
    where.append(" order by o." + Order.PROPERTY_ORDERDATE + " desc");
    OBQuery<OrderLine> olQry = OBDal.getInstance().createQuery(OrderLine.class, where.toString());
    olQry.setFilterOnReadableOrganization(false);
    olQry.setNamedParameter("bp", bp);
    olQry.setNamedParameter("product", product);
    olQry.setNamedParameter("org", osp.getChildTree(org.getId(), true));
    olQry.setMaxResult(1);
    return olQry.uniqueResult();
  }

  public static CostingRule getCostDimensionRule(Organization org, Date date) {
    StringBuffer where = new StringBuffer();
    where.append(CostingRule.PROPERTY_ORGANIZATION + ".id = :organization");
    where.append(" and (" + CostingRule.PROPERTY_STARTINGDATE + " is null ");
    where.append("   or " + CostingRule.PROPERTY_STARTINGDATE + " <= :startdate)");
    where.append(" and (" + CostingRule.PROPERTY_ENDINGDATE + " is null");
    where.append("   or " + CostingRule.PROPERTY_ENDINGDATE + " >= :enddate )");
    where.append(" and " + CostingRule.PROPERTY_VALIDATED + " = true");
    where.append(" order by case when " + CostingRule.PROPERTY_STARTINGDATE
        + " is null then 1 else 0 end, " + CostingRule.PROPERTY_STARTINGDATE + " desc");
    OBQuery<CostingRule> crQry = OBDal.getInstance()
        .createQuery(CostingRule.class, where.toString());
    crQry.setFilterOnReadableOrganization(false);
    crQry.setNamedParameter("organization", org.getId());
    crQry.setNamedParameter("startdate", date);
    crQry.setNamedParameter("enddate", date);
    crQry.setMaxResult(1);
    CostingRule costRule = crQry.uniqueResult();
    if (costRule == null) {
      throw new OBException("@NoCostingRuleFoundForOrganizationAndDate@ @Organization@: "
          + org.getName() + ", @Date@: " + OBDateUtils.formatDate(date));
    }
    return costRule;
  }

  /**
   * Returns the max transaction date with cost calculated
   */
  public static Date getMaxTransactionDate(Organization org) {
    // Get child tree of organizations.
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(org.getClient().getId());
    Set<String> orgs = osp.getChildTree(org.getId(), true);

    StringBuffer select = new StringBuffer();
    select.append(" select max(trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + ") as date");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    select.append(" where trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    select.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    Query<Date> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Date.class);
    trxQry.setParameterList("orgs", orgs);
    Date maxDate = trxQry.uniqueResult();
    if (maxDate != null) {
      return maxDate;
    }
    return null;
  }

  /**
   * Search period control closed between dateFrom and dateTo
   */
  public static Period periodClosed(Organization org, Date dateFrom, Date dateTo, String docType)
      throws ServletException {
    String strDateFormat = OBPropertiesProvider.getInstance()
        .getOpenbravoProperties()
        .getProperty("dateFormat.java");
    final SimpleDateFormat dateFormat = new SimpleDateFormat(strDateFormat);

    String strDateFrom = dateFormat.format(dateFrom);
    String strDateTo = dateFormat.format(dateTo);
    CostingUtilsData[] per = CostingUtilsData.periodClosed(new DalConnectionProvider(false),
        org.getId(), strDateFrom, strDateTo, org.getClient().getId(), docType);
    if (per.length > 0) {
      return OBDal.getInstance().get(Period.class, per[0].period);
    }
    return null;
  }

  /**
   * Returns the Starting Date of a Costing Rule, if is null returns 01/01/1900
   */
  public static Date getCostingRuleStartingDate(CostingRule rule) {
    if (rule.getStartingDate() == null) {
      SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
      try {
        return outputFormat.parse("01-01-1900");
      } catch (ParseException e) {
        // Error parsing the date.
        log4j.error("Error parsing the date.", e);
        return null;
      }
    }
    return rule.getStartingDate();
  }

  /**
   * Returns the Fix Backdated From of a Costing Rule, if is null returns 01/01/1900
   */
  public static Date getCostingRuleFixBackdatedFrom(CostingRule rule) {
    if (rule.getFixbackdatedfrom() == null) {
      SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
      try {
        return outputFormat.parse("01-01-1900");
      } catch (ParseException e) {
        // Error parsing the date.
        log4j.error("Error parsing the date.", e);
        return null;
      }
    }
    return rule.getFixbackdatedfrom();
  }

  /**
   * Throws an OBException when the processId is the CostingBackground and it is being executed by
   * an organization which has a legal entity as an ancestor.
   * 
   * @param processId
   *          This is the process Id being executed. The method only runs the validation when the
   *          process ID is equal to CostingBackground.AD_PROCESS_ID
   * @param scheduledOrg
   *          the organization that runs the process
   */
  public static void checkValidOrganization(final String processId,
      final Organization scheduledOrg) {
    if (StringUtils.equals(processId, CostingBackground.AD_PROCESS_ID)) {
      final Organization legalEntity = OBContext.getOBContext()
          .getOrganizationStructureProvider()
          .getLegalEntity(scheduledOrg);
      if (legalEntity != null && !StringUtils.equals(legalEntity.getId(), scheduledOrg.getId())) {
        throw new OBException(OBMessageUtils.messageBD("CostBackgroundWrongOrganization"));
      }
    }
  }

  /**
   * Check if exists processed transactions for this product
   */
  public static boolean existsProcessedTransactions(Product product,
      HashMap<CostDimension, BaseOBObject> _costDimensions, Organization costorg,
      MaterialTransaction trx, boolean isManufacturingProduct) {

    // Get child tree of organizations.
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(trx.getClient().getId());
    Set<String> orgs = osp.getChildTree(costorg.getId(), true);
    HashMap<CostDimension, BaseOBObject> costDimensions = _costDimensions;
    if (isManufacturingProduct) {
      orgs = osp.getChildTree("0", false);
      costDimensions = CostingUtils.getEmptyDimensions();
    }

    OBCriteria<MaterialTransaction> criteria = OBDal.getInstance()
        .createCriteria(MaterialTransaction.class);
    criteria.createAlias(MaterialTransaction.PROPERTY_STORAGEBIN, "sb");
    criteria.add(Restrictions.eq(MaterialTransaction.PROPERTY_PRODUCT, product));
    criteria.add(Restrictions.eq(MaterialTransaction.PROPERTY_ISPROCESSED, true));
    criteria.add(Restrictions.in(MaterialTransaction.PROPERTY_ORGANIZATION + ".id", orgs));
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      criteria.add(Restrictions.eq("sb." + Locator.PROPERTY_WAREHOUSE + ".id",
          costDimensions.get(CostDimension.Warehouse).getId()));
    }
    criteria.setFilterOnReadableOrganization(false);
    criteria.setMaxResults(1);
    return criteria.uniqueResult() != null;
  }

  /**
   * Check if trx is the last opening one of an Inventory Amount Update
   */
  public static boolean isLastOpeningTransaction(MaterialTransaction trx,
      boolean includeWarehouseDimension) {

    StringBuffer where = new StringBuffer();
    where.append(" select trx." + MaterialTransaction.PROPERTY_ID + " as trxid");
    where.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    where.append(" join trx." + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + " as il");
    where.append(" join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    where.append(
        " join i." + InventoryCount.PROPERTY_INVENTORYAMOUNTUPDATELINEINVENTORIESINITINVENTORYLIST
            + " as iaui");
    where.append(" join iaui." + InvAmtUpdLnInventories.PROPERTY_WAREHOUSE + " as w");
    where.append(" where i." + InventoryCount.PROPERTY_INVENTORYTYPE + " = 'O'");
    where.append(" and iaui." + InvAmtUpdLnInventories.PROPERTY_CAINVENTORYAMTLINE + " = :iaul");
    if (includeWarehouseDimension) {
      where.append(" and iaui." + InvAmtUpdLnInventories.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    where.append(" order by w." + Warehouse.PROPERTY_NAME + " desc");
    where.append(" , il." + InventoryCountLine.PROPERTY_LINENO + " desc");

    Query<String> qry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), String.class);
    OBDal.getInstance().refresh(trx.getPhysicalInventoryLine().getPhysInventory());
    qry.setParameter("iaul",
        trx.getPhysicalInventoryLine()
            .getPhysInventory()
            .getInventoryAmountUpdateLineInventoriesInitInventoryList()
            .get(0)
            .getCaInventoryamtline());
    if (includeWarehouseDimension) {
      qry.setParameter("warehouse", trx.getStorageBin().getWarehouse());
    }
    qry.setMaxResults(1);
    return StringUtils.equals(trx.getId(), qry.uniqueResult());
  }

  @Deprecated
  public static boolean isAllowNegativeStock(Client client) {
    try {
      OBContext.setAdminMode(true);
      return isNegativeStockEnabledForAnyStorageBin(client);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  public static boolean isNegativeStockEnabledForAnyStorageBin(Client client) {
    StringBuilder where = new StringBuilder();
    where.append(" select c");
    where.append(" from " + Client.ENTITY_NAME + " as c");
    where.append(" where exists (select 1");
    where.append("               from " + Locator.ENTITY_NAME + " as l");
    where.append("               join l." + Locator.PROPERTY_INVENTORYSTATUS + " invs");
    where.append("               where invs." + InventoryStatus.PROPERTY_OVERISSUE + " = true");
    where.append("               and l." + Locator.PROPERTY_CLIENT + " = c)");
    where.append(" and c = :client");

    Query<Client> qry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), Client.class);
    qry.setParameter("client", client);
    qry.setMaxResults(1);
    return !qry.list().isEmpty();
  }

  public static boolean isNegativeStockAllowedForShipmentInout(String shipmentInOutId) {
    StringBuilder where = new StringBuilder();
    where.append(" select c");
    where.append(" from " + Client.ENTITY_NAME + " as c");
    where.append(" where exists (select 1");
    where.append("               from " + ShipmentInOutLine.ENTITY_NAME + " as iol");
    where
        .append("               join iol." + ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT + " as io");
    where.append("               join iol." + ShipmentInOutLine.PROPERTY_STORAGEBIN + " as l");
    where.append("               left join l." + Locator.PROPERTY_INVENTORYSTATUS + " invs");
    where.append("               where invs." + InventoryStatus.PROPERTY_OVERISSUE + " = true");
    where.append("               and io." + ShipmentInOut.PROPERTY_ID + " = :shipmentInOutID)");

    Query<Client> qry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), Client.class);
    qry.setParameter("shipmentInOutID", shipmentInOutId);
    qry.setMaxResults(1);
    return !qry.list().isEmpty();
  }
}
