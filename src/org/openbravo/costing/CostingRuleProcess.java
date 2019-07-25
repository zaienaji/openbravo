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
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductUOM;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.cost.CostingRuleInit;
import org.openbravo.model.materialmgmt.cost.TransactionCost;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.materialmgmt.transaction.TransactionLast;
import org.openbravo.scheduling.Process;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DbUtility;

public class CostingRuleProcess implements Process {
  private ProcessLogger logger;
  private static final Logger log4j = LogManager.getLogger();

  @Override
  public void execute(ProcessBundle bundle) throws Exception {
    long start = System.currentTimeMillis();
    log4j.debug("Starting CostingRuleProcess at: " + new Date());
    logger = bundle.getLogger();
    OBError msg = new OBError();
    msg.setType("Success");
    msg.setTitle(OBMessageUtils.messageBD("Success"));
    try {
      OBContext.setAdminMode(false);
      final String ruleId = (String) bundle.getParams().get("M_Costing_Rule_ID");
      CostingRule rule = OBDal.getInstance().get(CostingRule.class, ruleId);

      // Checks
      if (rule.getOrganization().getCurrency() == null) {
        throw new OBException("@NoCurrencyInCostingRuleOrg@");
      }
      migrationCheck();
      if (rule.isBackdatedTransactionsFixed()) {
        CostAdjustmentProcess
            .doGetAlgorithmAdjustmentImp(rule.getCostingAlgorithm().getJavaClassName());
      }

      OrganizationStructureProvider osp = OBContext.getOBContext()
          .getOrganizationStructureProvider(rule.getClient().getId());
      final Set<String> childOrgs = osp.getChildTree(rule.getOrganization().getId(), true);
      final Set<String> naturalOrgs = osp.getNaturalTree(rule.getOrganization().getId());

      CostingRule prevCostingRule = getPreviousRule(rule);
      boolean existsPreviousRule = prevCostingRule != null;
      boolean existsTransactions = existsTransactions(naturalOrgs, childOrgs);
      if (existsPreviousRule) {
        // Product with costing rule. All trx must be calculated.
        checkAllTrxCalculated(naturalOrgs, childOrgs);
      } else if (existsTransactions) {
        // Product configured to have cost not calculated cannot have transactions with cost
        // calculated.
        checkNoTrxWithCostCalculated(naturalOrgs, childOrgs);
        if (rule.getStartingDate() != null) {
          // First rule of an instance that does not need migration. Old transactions costs are not
          // calculated. They are initialized with ZERO cost.
          initializeOldTrx(childOrgs, rule.getStartingDate());
        }
      }
      // Inventories are only needed:
      // - if the costing rule is updating a previous rule
      // - or legacy cost was never used and the first validated rule has a starting date different
      // than null. If the date is old enough that there are not prior transactions no inventories
      // are created.
      if (existsPreviousRule || rule.getStartingDate() != null) {
        Date startingDate = rule.getStartingDate();
        if (existsPreviousRule) {
          // Set valid from date
          startingDate = DateUtils.truncate(new Date(), Calendar.SECOND);
          rule.setStartingDate(startingDate);
          log4j.debug("Setting starting date " + startingDate);
          prevCostingRule.setEndingDate(startingDate);
          OBDal.getInstance().save(prevCostingRule);
          OBDal.getInstance().flush();
        }
        if (rule.getFixbackdatedfrom() == null && rule.isBackdatedTransactionsFixed()) {
          rule.setFixbackdatedfrom(startingDate);
        }
        createCostingRuleInits(ruleId, childOrgs, startingDate);

        // Update cost of inventories and process starting physical inventories.
        updateInventoriesCostAndProcessInitInventories(ruleId, startingDate, existsPreviousRule);

        // Delete M_Transaction_Last
        if (existsPreviousRule) {
          deleteLastTransaction();
        }
      }

      if (rule.getStartingDate() != null && rule.getFixbackdatedfrom() != null
          && rule.isBackdatedTransactionsFixed()
          && rule.getFixbackdatedfrom().before(rule.getStartingDate())) {
        throw new OBException("@FixBackdateFromBeforeStartingDate@");
      }

      // Reload rule after possible session clear.
      rule = OBDal.getInstance().get(CostingRule.class, ruleId);
      rule.setValidated(true);
      CostingStatus.getInstance().setMigrated();
      OBDal.getInstance().save(rule);
    } catch (final OBException e) {
      OBDal.getInstance().rollbackAndClose();
      String resultMsg = OBMessageUtils.parseTranslation(e.getMessage());
      logger.log(resultMsg);
      log4j.error(e);
      msg.setType("Error");
      msg.setTitle(OBMessageUtils.messageBD("Error"));
      msg.setMessage(resultMsg);
      bundle.setResult(msg);

    } catch (final Exception e) {
      OBDal.getInstance().rollbackAndClose();
      String message = DbUtility.getUnderlyingSQLException(e).getMessage();
      logger.log(message);
      log4j.error(message, e);
      msg.setType("Error");
      msg.setTitle(OBMessageUtils.messageBD("Error"));
      msg.setMessage(message);
      bundle.setResult(msg);
    } finally {
      OBContext.restorePreviousMode();
    }
    bundle.setResult(msg);
    long end = System.currentTimeMillis();
    log4j.debug(
        "Ending CostingRuleProcess at: " + new Date() + ". Duration: " + (end - start) + " ms.");
  }

  private void migrationCheck() {
    if (!CostingStatus.getInstance().isMigrated()) {
      throw new OBException("@CostMigrationNotDone@");
    }
  }

  private CostingRule getPreviousRule(CostingRule rule) {
    StringBuffer where = new StringBuffer();
    where.append(" as cr");
    where.append(" where cr." + CostingRule.PROPERTY_ORGANIZATION + " = :ruleOrg");
    where.append("   and cr." + CostingRule.PROPERTY_VALIDATED + " = true");
    where.append("   order by cr." + CostingRule.PROPERTY_STARTINGDATE + " desc");

    OBQuery<CostingRule> crQry = OBDal.getInstance()
        .createQuery(CostingRule.class, where.toString());
    crQry.setFilterOnReadableOrganization(false);
    crQry.setNamedParameter("ruleOrg", rule.getOrganization());
    crQry.setMaxResult(1);
    return crQry.uniqueResult();
  }

  private boolean existsTransactions(Set<String> naturalOrgs, Set<String> childOrgs) {
    StringBuffer where = new StringBuffer();
    where.append(" as p");
    where.append(" where p." + Product.PROPERTY_PRODUCTTYPE + " = 'I'");
    where.append("   and p." + Product.PROPERTY_STOCKED + " = true");
    where.append("   and p." + Product.PROPERTY_ORGANIZATION + ".id in (:porgs)");
    where.append("   and exists (select 1 from " + MaterialTransaction.ENTITY_NAME);
    where.append("     where " + MaterialTransaction.PROPERTY_PRODUCT + " = p");
    where
        .append("      and " + MaterialTransaction.PROPERTY_ORGANIZATION + " .id in (:childOrgs))");

    OBQuery<Product> pQry = OBDal.getInstance().createQuery(Product.class, where.toString());
    pQry.setFilterOnReadableOrganization(false);
    pQry.setNamedParameter("porgs", naturalOrgs);
    pQry.setNamedParameter("childOrgs", childOrgs);
    pQry.setMaxResult(1);
    return pQry.uniqueResult() != null;
  }

  private void checkAllTrxCalculated(Set<String> naturalOrgs, Set<String> childOrgs) {
    StringBuffer where = new StringBuffer();
    where.append(" as p");
    where.append(" where p." + Product.PROPERTY_PRODUCTTYPE + " = 'I'");
    where.append("  and p." + Product.PROPERTY_STOCKED + " = true");
    where.append("  and p." + Product.PROPERTY_ORGANIZATION + ".id in (:porgs)");
    where.append("  and exists (select 1 from " + MaterialTransaction.ENTITY_NAME + " as trx ");
    where.append("   where trx." + MaterialTransaction.PROPERTY_PRODUCT + " = p");
    where.append(
        "     and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:childOrgs)");
    where.append("     and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = false");
    where.append("   )");
    OBQuery<Product> pQry = OBDal.getInstance().createQuery(Product.class, where.toString());
    pQry.setFilterOnReadableOrganization(false);
    pQry.setNamedParameter("porgs", naturalOrgs);
    pQry.setNamedParameter("childOrgs", childOrgs);
    pQry.setMaxResult(1);
    if (pQry.uniqueResult() != null) {
      throw new OBException("@TrxWithCostNoCalculated@");
    }
  }

  private void checkNoTrxWithCostCalculated(Set<String> naturalOrgs, Set<String> childOrgs) {
    StringBuffer where = new StringBuffer();
    where.append(" as p");
    where.append(" where p." + Product.PROPERTY_PRODUCTTYPE + " = 'I'");
    where.append("  and p." + Product.PROPERTY_STOCKED + " = true");
    where.append("  and p." + Product.PROPERTY_ORGANIZATION + ".id in (:porgs)");
    where.append("  and exists (select 1 from " + MaterialTransaction.ENTITY_NAME + " as trx ");
    where.append("   where trx." + MaterialTransaction.PROPERTY_PRODUCT + " = p");
    where.append("     and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    where.append(
        "     and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:childOrgs)");
    where.append("   )");
    OBQuery<Product> pQry = OBDal.getInstance().createQuery(Product.class, where.toString());
    pQry.setFilterOnReadableOrganization(false);
    pQry.setNamedParameter("porgs", naturalOrgs);
    pQry.setNamedParameter("childOrgs", childOrgs);
    pQry.setMaxResult(1);
    if (pQry.uniqueResult() != null) {
      throw new OBException("@ProductsWithTrxCalculated@");
    }
  }

  private void initializeOldTrx(Set<String> childOrgs, Date date) throws SQLException {
    Client client = OBDal.getInstance()
        .get(Client.class, OBContext.getOBContext().getCurrentClient().getId());

    long t1 = System.currentTimeMillis();
    StringBuffer insert = new StringBuffer();
    insert.append(" insert into " + TransactionCost.ENTITY_NAME);
    insert.append(" (" + TransactionCost.PROPERTY_ID);
    insert.append(", " + TransactionCost.PROPERTY_CLIENT);
    insert.append(", " + TransactionCost.PROPERTY_ORGANIZATION);
    insert.append(", " + TransactionCost.PROPERTY_CREATIONDATE);
    insert.append(", " + TransactionCost.PROPERTY_CREATEDBY);
    insert.append(", " + TransactionCost.PROPERTY_UPDATED);
    insert.append(", " + TransactionCost.PROPERTY_UPDATEDBY);
    insert.append(", " + TransactionCost.PROPERTY_ACTIVE);
    insert.append(", " + TransactionCost.PROPERTY_INVENTORYTRANSACTION);
    insert.append(", " + TransactionCost.PROPERTY_COST);
    insert.append(", " + TransactionCost.PROPERTY_COSTDATE);
    insert.append(", " + TransactionCost.PROPERTY_CURRENCY);
    insert.append(", " + TransactionCost.PROPERTY_ACCOUNTINGDATE);
    insert.append(")");
    insert.append(" select get_uuid()");
    insert.append(", t." + MaterialTransaction.PROPERTY_CLIENT);
    insert.append(", t." + MaterialTransaction.PROPERTY_ORGANIZATION);
    insert.append(", now()");
    insert.append(", t." + MaterialTransaction.PROPERTY_CREATEDBY);
    insert.append(", now()");
    insert.append(", t." + MaterialTransaction.PROPERTY_UPDATEDBY);
    insert.append(", t." + MaterialTransaction.PROPERTY_ACTIVE);
    insert.append(", t");
    insert.append(", cast(0 as big_decimal)");
    insert.append(", t." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
    insert.append(", t." + MaterialTransaction.PROPERTY_CLIENT + "." + Client.PROPERTY_CURRENCY);
    insert.append(", coalesce(io." + ShipmentInOut.PROPERTY_ACCOUNTINGDATE + ", t."
        + MaterialTransaction.PROPERTY_MOVEMENTDATE + ")");
    insert.append(" from " + MaterialTransaction.ENTITY_NAME + " as t");
    insert.append(" left join t." + MaterialTransaction.PROPERTY_GOODSSHIPMENTLINE + " as iol");
    insert.append(" left join iol." + ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT + " as io");
    insert.append(" where t." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    insert.append(" and t." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :date");
    insert.append(" and t." + MaterialTransaction.PROPERTY_ISPROCESSED + " = false");
    insert.append(" and t." + MaterialTransaction.PROPERTY_ACTIVE + " = true");
    insert.append(" and t." + MaterialTransaction.PROPERTY_CLIENT + ".id = :client");

    @SuppressWarnings("rawtypes")
    Query queryInsert = OBDal.getInstance().getSession().createQuery(insert.toString());
    queryInsert.setParameterList("orgs", childOrgs);
    queryInsert.setParameter("date", date);
    queryInsert.setParameter("client", client.getId());
    int n1 = queryInsert.executeUpdate();
    log4j.debug("InitializeOldTrx inserted " + n1 + " records. Took: "
        + (System.currentTimeMillis() - t1) + " ms.");

    long t2 = System.currentTimeMillis();
    StringBuffer update = new StringBuffer();
    update.append(" update " + MaterialTransaction.ENTITY_NAME);
    update.append(" set " + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    update.append(", " + MaterialTransaction.PROPERTY_COSTINGSTATUS + " = 'CC'");
    update.append(", " + MaterialTransaction.PROPERTY_TRANSACTIONCOST + " = " + BigDecimal.ZERO);
    update.append(", " + MaterialTransaction.PROPERTY_CURRENCY + " = :currency");
    update.append(", " + MaterialTransaction.PROPERTY_ISPROCESSED + " = true");
    update.append(" where " + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    update.append(" and " + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :date");
    update.append(" and " + MaterialTransaction.PROPERTY_ISPROCESSED + " = false");
    update.append(" and " + MaterialTransaction.PROPERTY_ACTIVE + " = true");
    update.append(" and " + MaterialTransaction.PROPERTY_CLIENT + ".id = :client");

    @SuppressWarnings("rawtypes")
    Query queryUpdate = OBDal.getInstance().getSession().createQuery(update.toString());
    queryUpdate.setParameter("currency", client.getCurrency());
    queryUpdate.setParameterList("orgs", childOrgs);
    queryUpdate.setParameter("date", date);
    queryUpdate.setParameter("client", client.getId());
    int n2 = queryUpdate.executeUpdate();
    log4j.debug("InitializeOldTrx updated " + n2 + " records. Took: "
        + (System.currentTimeMillis() - t2) + " ms.");

    OBDal.getInstance().getSession().flush();
    OBDal.getInstance().getSession().clear();
  }

  @Deprecated
  protected void createCostingRuleInits(CostingRule rule, Set<String> childOrgs) {
    createCostingRuleInits(rule.getId(), childOrgs, null);
  }

  protected void createCostingRuleInits(String ruleId, Set<String> childOrgs, Date date) {
    long t1 = System.currentTimeMillis();
    CostingRule rule = OBDal.getInstance().get(CostingRule.class, ruleId);
    ScrollableResults stockLines = getStockLines(childOrgs, date);
    log4j.debug("GetStockLines took: " + (System.currentTimeMillis() - t1) + " ms.");

    // The key of the Map is the concatenation of orgId and warehouseId
    long t2 = System.currentTimeMillis();
    Map<String, String> initLines = new HashMap<String, String>();
    Map<String, Long> maxLineNumbers = new HashMap<String, Long>();
    InventoryCountLine closingInventoryLine = null;
    InventoryCountLine openInventoryLine = null;
    int i = 0;
    try {
      while (stockLines.next()) {
        long t3 = System.currentTimeMillis();
        Object[] stockLine = stockLines.get();
        String productId = (String) stockLine[0];
        String attrSetInsId = (String) stockLine[1];
        String uomId = (String) stockLine[2];
        String orderUOMId = (String) stockLine[3];
        String locatorId = (String) stockLine[4];
        String warehouseId = (String) stockLine[5];
        BigDecimal qty = (BigDecimal) stockLine[6];
        BigDecimal orderQty = (BigDecimal) stockLine[7];

        String criId = initLines.get(warehouseId);
        CostingRuleInit cri = null;
        if (criId == null) {
          cri = createCostingRuleInitLine(rule, warehouseId, date);

          initLines.put(warehouseId, cri.getId());
        } else {
          cri = OBDal.getInstance().get(CostingRuleInit.class, criId);
        }
        Long lineNo = (maxLineNumbers.get(criId) == null ? 0L : maxLineNumbers.get(criId)) + 10L;
        maxLineNumbers.put(criId, lineNo);

        if (BigDecimal.ZERO.compareTo(qty) < 0) {
          // Do not insert negative values in Inventory lines, instead reverse the Quantity Count
          // and the Book Quantity. For example:
          // Instead of CountQty=0 and BookQty=-5 insert CountQty=5 and BookQty=0
          // By doing so the difference between both quantities remains the same and no negative
          // values have been inserted.

          openInventoryLine = insertInventoryLine(cri.getInitInventory(), productId, attrSetInsId,
              uomId, orderUOMId, locatorId, qty, BigDecimal.ZERO, orderQty, BigDecimal.ZERO, lineNo,
              null);
          insertInventoryLine(cri.getCloseInventory(), productId, attrSetInsId, uomId, orderUOMId,
              locatorId, BigDecimal.ZERO, qty, BigDecimal.ZERO, orderQty, lineNo,
              openInventoryLine);

        } else {
          openInventoryLine = insertInventoryLine(cri.getInitInventory(), productId, attrSetInsId,
              uomId, orderUOMId, locatorId, BigDecimal.ZERO, qty.abs(), BigDecimal.ZERO,
              orderQty == null ? null : orderQty.abs(), lineNo, closingInventoryLine);
          insertInventoryLine(cri.getCloseInventory(), productId, attrSetInsId, uomId, orderUOMId,
              locatorId, qty == null ? null : qty.abs(), BigDecimal.ZERO,
              orderQty == null ? null : orderQty.abs(), BigDecimal.ZERO, lineNo, openInventoryLine);
        }

        i++;
        if ((i % 100) == 0) {
          OBDal.getInstance().flush();
          OBDal.getInstance().getSession().clear();
          // Reload rule after clear session.
          rule = OBDal.getInstance().get(CostingRule.class, ruleId);
        }

        log4j.debug("Create closing/opening inventory line took: "
            + (System.currentTimeMillis() - t3) + " ms.");
      }
    } finally {
      stockLines.close();
    }
    log4j.debug("Create " + i + " closing/opening inventory lines took: "
        + (System.currentTimeMillis() - t2) + " ms.");

    // Process closing physical inventories.
    long t4 = System.currentTimeMillis();
    rule = OBDal.getInstance().get(CostingRule.class, ruleId);
    i = 0;
    for (CostingRuleInit cri : rule.getCostingRuleInitList()) {
      long t5 = System.currentTimeMillis();
      new InventoryCountProcess().processInventory(cri.getCloseInventory(), false);
      log4j.debug(
          "Processing closing inventory took: " + (System.currentTimeMillis() - t5) + " ms.");
      i++;
    }
    log4j.debug("Processing " + i + " closing inventories took: "
        + (System.currentTimeMillis() - t4) + " ms.");

    log4j
        .debug("CreateCostingRuleInits method took: " + (System.currentTimeMillis() - t1) + " ms.");
  }

  private ScrollableResults getStockLines(Set<String> childOrgs, Date date) {
    StringBuffer select = new StringBuffer();
    select.append("select trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_UOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ORDERUOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_STORAGEBIN + ".id");
    select.append(", loc." + Locator.PROPERTY_WAREHOUSE + ".id");
    select.append(", sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ")");
    select.append(", sum(trx." + MaterialTransaction.PROPERTY_ORDERQUANTITY + ")");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    select.append("    join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as loc");
    select.append(" where trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    if (date != null) {
      select
          .append("   and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :date");
    }
    select.append("   and trx." + MaterialTransaction.PROPERTY_PRODUCT + ".productType = 'I'");
    select.append("   and trx." + MaterialTransaction.PROPERTY_PRODUCT + ".stocked = true");
    select.append(" group by trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_UOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ORDERUOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_STORAGEBIN + ".id");
    select.append(", loc." + Locator.PROPERTY_WAREHOUSE + ".id");
    select.append(" having ");
    select.append(" sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") <> 0");
    select.append(" or sum(trx." + MaterialTransaction.PROPERTY_ORDERQUANTITY + ") <> 0");
    select.append(" order by loc." + Locator.PROPERTY_WAREHOUSE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_STORAGEBIN + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_UOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ORDERUOM + ".id");

    @SuppressWarnings("rawtypes")
    Query stockLinesQry = OBDal.getInstance().getSession().createQuery(select.toString());
    stockLinesQry.setParameterList("orgs", childOrgs);
    if (date != null) {
      stockLinesQry.setParameter("date", date);
    }
    stockLinesQry.setFetchSize(1000);
    ScrollableResults stockLines = stockLinesQry.scroll(ScrollMode.FORWARD_ONLY);
    return stockLines;
  }

  private CostingRuleInit createCostingRuleInitLine(CostingRule rule, String warehouseId,
      Date date) {
    Date localDate = date;
    if (localDate == null) {
      localDate = new Date();
    }
    String clientId = rule.getClient().getId();
    String orgId = rule.getOrganization().getId();
    CostingRuleInit cri = OBProvider.getInstance().get(CostingRuleInit.class);
    cri.setClient((Client) OBDal.getInstance().getProxy(Client.ENTITY_NAME, clientId));
    cri.setOrganization(
        (Organization) OBDal.getInstance().getProxy(Organization.ENTITY_NAME, orgId));
    cri.setWarehouse((Warehouse) OBDal.getInstance().getProxy(Warehouse.ENTITY_NAME, warehouseId));
    cri.setCostingRule(rule);
    List<CostingRuleInit> criList = rule.getCostingRuleInitList();
    criList.add(cri);
    rule.setCostingRuleInitList(criList);

    InventoryCount closeInv = OBProvider.getInstance().get(InventoryCount.class);
    closeInv.setClient((Client) OBDal.getInstance().getProxy(Client.ENTITY_NAME, clientId));
    closeInv.setOrganization(
        (Organization) OBDal.getInstance().getProxy(Organization.ENTITY_NAME, orgId));
    closeInv.setName(OBMessageUtils.messageBD("CostCloseInventory"));
    closeInv
        .setWarehouse((Warehouse) OBDal.getInstance().getProxy(Warehouse.ENTITY_NAME, warehouseId));
    closeInv.setMovementDate(localDate);
    closeInv.setInventoryType("C");
    cri.setCloseInventory(closeInv);

    InventoryCount initInv = OBProvider.getInstance().get(InventoryCount.class);
    initInv.setClient((Client) OBDal.getInstance().getProxy(Client.ENTITY_NAME, clientId));
    initInv.setOrganization(
        (Organization) OBDal.getInstance().getProxy(Organization.ENTITY_NAME, orgId));
    initInv.setName(OBMessageUtils.messageBD("CostInitInventory"));
    initInv
        .setWarehouse((Warehouse) OBDal.getInstance().getProxy(Warehouse.ENTITY_NAME, warehouseId));
    initInv.setMovementDate(localDate);
    initInv.setInventoryType("O");
    cri.setInitInventory(initInv);
    OBDal.getInstance().save(rule);
    OBDal.getInstance().save(closeInv);
    OBDal.getInstance().save(initInv);

    OBDal.getInstance().flush();

    return cri;
  }

  private InventoryCountLine insertInventoryLine(InventoryCount inventory, String productId,
      String attrSetInsId, String uomId, String orderUOMId, String locatorId, BigDecimal qtyCount,
      BigDecimal qtyBook, BigDecimal orderQtyCount, BigDecimal orderQtyBook, Long lineNo,
      InventoryCountLine relatedInventoryLine) {
    InventoryCountLine icl = OBProvider.getInstance().get(InventoryCountLine.class);
    icl.setClient(inventory.getClient());
    icl.setOrganization(inventory.getOrganization());
    icl.setPhysInventory(inventory);
    icl.setLineNo(lineNo);
    icl.setStorageBin((Locator) OBDal.getInstance().getProxy(Locator.ENTITY_NAME, locatorId));
    icl.setProduct((Product) OBDal.getInstance().getProxy(Product.ENTITY_NAME, productId));
    icl.setAttributeSetValue((AttributeSetInstance) OBDal.getInstance()
        .getProxy(AttributeSetInstance.ENTITY_NAME, attrSetInsId));
    icl.setQuantityCount(qtyCount);
    icl.setBookQuantity(qtyBook);
    icl.setUOM((UOM) OBDal.getInstance().getProxy(UOM.ENTITY_NAME, uomId));
    if (orderUOMId != null) {
      icl.setOrderQuantity(orderQtyCount);
      icl.setQuantityOrderBook(orderQtyBook);
      icl.setOrderUOM(
          (ProductUOM) OBDal.getInstance().getProxy(ProductUOM.ENTITY_NAME, orderUOMId));
    }
    icl.setRelatedInventory(relatedInventoryLine);
    List<InventoryCountLine> invLines = inventory.getMaterialMgmtInventoryCountLineList();
    invLines.add(icl);
    inventory.setMaterialMgmtInventoryCountLineList(invLines);
    OBDal.getInstance().save(inventory);
    OBDal.getInstance().flush();
    return icl;
  }

  private void updateInventoriesCostAndProcessInitInventories(String ruleId, Date startingDate,
      boolean existsPreviousRule) {
    long t1 = System.currentTimeMillis();
    CostingRule rule = OBDal.getInstance().get(CostingRule.class, ruleId);
    int i = 0;
    for (CostingRuleInit cri : rule.getCostingRuleInitList()) {
      long t2 = System.currentTimeMillis();
      ScrollableResults trxs = getInventoryLineTransactions(cri.getCloseInventory());
      log4j.debug(
          "GetInventoryLineTransactions took: " + (System.currentTimeMillis() - t2) + " ms.");
      long t3 = System.currentTimeMillis();
      int j = 0;
      try {
        while (trxs.next()) {
          long t4 = System.currentTimeMillis();
          MaterialTransaction trx = (MaterialTransaction) trxs.get(0);
          // Remove 1 second from transaction date to ensure that cost is calculated with previous
          // costing rule.
          trx.setTransactionProcessDate(DateUtils.addSeconds(startingDate, -1));
          BigDecimal trxCost = BigDecimal.ZERO;
          BigDecimal cost = null;
          Currency cur = FinancialUtils.getLegalEntityCurrency(trx.getOrganization());
          if (existsPreviousRule) {
            trxCost = CostingUtils.getTransactionCost(trx, startingDate, true, cur);
            if (trx.getMovementQuantity().compareTo(BigDecimal.ZERO) != 0) {
              if (trxCost == null) {
                throw new OBException("@NoCostCalculated@: " + trx.getIdentifier());
              }
              cost = trxCost.divide(trx.getMovementQuantity().abs(),
                  cur.getCostingPrecision().intValue(), RoundingMode.HALF_UP);
              trx = OBDal.getInstance().get(MaterialTransaction.class, trx.getId());
            }
          } else {
            // Insert transaction cost record big ZERO cost.
            cur = trx.getClient().getCurrency();
            TransactionCost transactionCost = OBProvider.getInstance().get(TransactionCost.class);
            transactionCost.setInventoryTransaction(trx);
            transactionCost.setCostDate(trx.getTransactionProcessDate());
            transactionCost.setClient(trx.getClient());
            transactionCost.setOrganization(trx.getOrganization());
            transactionCost.setCost(BigDecimal.ZERO);
            transactionCost.setCurrency(trx.getClient().getCurrency());
            transactionCost.setAccountingDate(trx.getGoodsShipmentLine() != null
                ? trx.getGoodsShipmentLine().getShipmentReceipt().getAccountingDate()
                : trx.getMovementDate());
            List<TransactionCost> trxCosts = trx.getTransactionCostList();
            trxCosts.add(transactionCost);
            trx.setTransactionCostList(trxCosts);
            OBDal.getInstance().save(trx);
          }

          trx.setCostCalculated(true);
          trx.setCostingStatus("CC");
          trx.setCurrency(cur);
          trx.setTransactionCost(trxCost);
          trx.setProcessed(true);
          OBDal.getInstance().save(trx);

          InventoryCountLine initICL = trx.getPhysicalInventoryLine().getRelatedInventory();
          initICL.setCost(cost);
          OBDal.getInstance().save(initICL);

          j++;
          if ((j % 100) == 0) {
            OBDal.getInstance().flush();
            OBDal.getInstance().getSession().clear();
            cri = OBDal.getInstance().get(CostingRuleInit.class, cri.getId());
          }

          log4j.debug(
              "Update inventory line cost took: " + (System.currentTimeMillis() - t4) + " ms.");
        }
      } finally {
        trxs.close();
      }
      OBDal.getInstance().flush();
      log4j.debug("Update " + j + "inventory line costs took: " + (System.currentTimeMillis() - t3)
          + " ms.");

      long t5 = System.currentTimeMillis();
      cri = OBDal.getInstance().get(CostingRuleInit.class, cri.getId());
      new InventoryCountProcess().processInventory(cri.getInitInventory(), false);
      log4j.debug(
          "Processing opening inventory took: " + (System.currentTimeMillis() - t5) + " ms.");
      i++;
    }
    log4j.debug("Processing " + i + " opening inventories took: "
        + (System.currentTimeMillis() - t1) + " ms.");

    if (!existsPreviousRule) {
      long t6 = System.currentTimeMillis();
      updateInitInventoriesTrxDate(startingDate, ruleId);
      log4j.debug(
          "UpdateInitInventoriesTrxDate took: " + (System.currentTimeMillis() - t6) + " ms.");
    }

    log4j.debug("UpdateInventoriesCostAndProcessInitInventories method took: "
        + (System.currentTimeMillis() - t1) + " ms.");
  }

  protected MaterialTransaction getInventoryLineTransaction(InventoryCountLine icl) {
    OBQuery<MaterialTransaction> trxQry = OBDal.getInstance()
        .createQuery(MaterialTransaction.class,
            MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + ".id = :invline");
    trxQry.setFilterOnReadableClients(false);
    trxQry.setFilterOnReadableOrganization(false);
    trxQry.setNamedParameter("invline", icl.getId());
    MaterialTransaction trx = trxQry.uniqueResult();
    return trx;
  }

  protected InventoryCountLine getInitIcl(InventoryCount initInventory, InventoryCountLine icl) {
    StringBuffer where = new StringBuffer();
    where.append(InventoryCountLine.PROPERTY_PHYSINVENTORY + ".id = :inventory");
    where.append(" and " + InventoryCountLine.PROPERTY_PRODUCT + ".id = :product");
    where.append(" and " + InventoryCountLine.PROPERTY_ATTRIBUTESETVALUE + ".id = :asi");
    where.append(" and " + InventoryCountLine.PROPERTY_STORAGEBIN + ".id = :locator");
    if (icl.getOrderUOM() == null) {
      where.append(" and " + InventoryCountLine.PROPERTY_ORDERUOM + " is null");
    } else {
      where.append(" and " + InventoryCountLine.PROPERTY_ORDERUOM + ".id = :orderuom");
    }
    OBQuery<InventoryCountLine> iclQry = OBDal.getInstance()
        .createQuery(InventoryCountLine.class, where.toString());
    iclQry.setFilterOnReadableClients(false);
    iclQry.setFilterOnReadableOrganization(false);
    iclQry.setNamedParameter("inventory", initInventory.getId());
    iclQry.setNamedParameter("product", icl.getProduct().getId());
    iclQry.setNamedParameter("asi", icl.getAttributeSetValue().getId());
    iclQry.setNamedParameter("locator", icl.getStorageBin().getId());
    if (icl.getOrderUOM() != null) {
      iclQry.setNamedParameter("orderuom", icl.getOrderUOM().getId());
    }
    return iclQry.uniqueResult();
  }

  private ScrollableResults getInventoryLineTransactions(InventoryCount inventory) {
    StringBuffer where = new StringBuffer();
    where.append(MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + "."
        + InventoryCountLine.PROPERTY_PHYSINVENTORY + "." + InventoryCount.PROPERTY_ID
        + " = :inventory");
    where.append(" order by " + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " desc");
    where.append(" , " + MaterialTransaction.PROPERTY_ID);
    OBQuery<MaterialTransaction> trxQry = OBDal.getInstance()
        .createQuery(MaterialTransaction.class, where.toString());
    trxQry.setNamedParameter("inventory", inventory.getId());
    return trxQry.scroll(ScrollMode.FORWARD_ONLY);
  }

  private void updateInitInventoriesTrxDate(Date startingDate, String ruleId) {
    StringBuffer update = new StringBuffer();
    update.append(" update " + MaterialTransaction.ENTITY_NAME + " as trx");
    update.append(" set trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :date");
    update.append(" where exists (");
    update.append("    select 1");
    update.append("    from " + InventoryCountLine.ENTITY_NAME + " as il");
    update.append("    join il." + InventoryCountLine.PROPERTY_PHYSINVENTORY + " as i");
    update.append(
        "    join i." + InventoryCount.PROPERTY_COSTINGRULEINITINITINVENTORYLIST + " as cri");
    update.append("    where cri." + CostingRuleInit.PROPERTY_COSTINGRULE + ".id = :cr");
    update.append("    and il." + InventoryCountLine.PROPERTY_ID + " = trx."
        + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE + ".id");
    update.append(" )");

    @SuppressWarnings("rawtypes")
    Query queryUpdate = OBDal.getInstance().getSession().createQuery(update.toString());
    queryUpdate.setParameter("date", startingDate);
    queryUpdate.setParameter("cr", ruleId);
    queryUpdate.executeUpdate();

    OBDal.getInstance().flush();
    OBDal.getInstance().getSession().clear();
  }

  private void deleteLastTransaction() {
    @SuppressWarnings("rawtypes")
    Query queryDelete = OBDal.getInstance()
        .getSession()
        .createQuery("delete from " + TransactionLast.ENTITY_NAME);
    queryDelete.executeUpdate();
  }
}
