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
 * All portions are Copyright (C) 2014-2020 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.costing;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductUOM;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.cost.InvAmtUpdLnInventories;
import org.openbravo.model.materialmgmt.cost.InventoryAmountUpdate;
import org.openbravo.model.materialmgmt.cost.InventoryAmountUpdateLine;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.service.db.DbUtility;

public class InventoryAmountUpdateProcess extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    JSONObject result = new JSONObject();
    JSONObject errorMessage = new JSONObject();
    OBContext.setAdminMode(true);

    try {
      final JSONObject jsonData = new JSONObject(data);

      String orgId = jsonData.getString("inpadOrgId");
      String invAmtUpdId = jsonData.getString("M_Ca_Inventoryamt_ID");
      InventoryAmountUpdate invAmtUpd = OBDal.getInstance()
          .get(InventoryAmountUpdate.class, invAmtUpdId);
      final OBCriteria<InventoryAmountUpdateLine> qLines = OBDal.getInstance()
          .createCriteria(InventoryAmountUpdateLine.class);
      qLines.add(Restrictions.eq(InventoryAmountUpdateLine.PROPERTY_CAINVENTORYAMT, invAmtUpd));

      ScrollableResults scrollLines = qLines.scroll(ScrollMode.FORWARD_ONLY);
      try {
        int cnt = 0;
        while (scrollLines.next()) {
          final InventoryAmountUpdateLine line = (InventoryAmountUpdateLine) scrollLines.get()[0];
          String lineId = line.getId();
          CostingRule rule = CostingUtils.getCostDimensionRule(
              OBDal.getInstance().get(Organization.class, orgId), line.getReferenceDate());
          String ruleId = rule.getId();
          OrganizationStructureProvider osp = OBContext.getOBContext()
              .getOrganizationStructureProvider(rule.getClient().getId());
          final Set<String> childOrgs = osp.getChildTree(rule.getOrganization().getId(), true);
          if (!rule.isWarehouseDimension()) {
            createInventories(lineId, null, ruleId, childOrgs, line.getReferenceDate());
          } else {
            createInventories(lineId, line.getWarehouse(), ruleId, childOrgs,
                line.getReferenceDate());
          }

          if ((cnt++ % 10) == 0) {
            OBDal.getInstance().flush();
            // clear session after each line iteration because the number of objects read in memory
            // is big
            OBDal.getInstance().getSession().clear();
          }
        }
        invAmtUpd = OBDal.getInstance().get(InventoryAmountUpdate.class, invAmtUpdId);
        invAmtUpd.setProcessed(true);
        OBDal.getInstance().save(invAmtUpd);
        OBDal.getInstance().flush();

        try {
          // to ensure that the closed inventory is created before opening inventory
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          log.error("Error waiting between processing close an open inventories", e);
        }

        StringBuffer where = new StringBuffer();
        where.append(" as inv");
        where.append(" where exists (");
        where.append("      select 1 from " + InvAmtUpdLnInventories.ENTITY_NAME + " invAmtUpd");
        where.append("      where invAmtUpd." + InvAmtUpdLnInventories.PROPERTY_CAINVENTORYAMTLINE
            + "." + InventoryAmountUpdateLine.PROPERTY_CAINVENTORYAMT + ".id =:invAmtUpdId");
        where.append(
            "        and invAmtUpd." + InvAmtUpdLnInventories.PROPERTY_INITINVENTORY + "= inv)");
        OBQuery<InventoryCount> qry = OBDal.getInstance()
            .createQuery(InventoryCount.class, where.toString());
        qry.setNamedParameter("invAmtUpdId", invAmtUpdId);

        ScrollableResults invLines = qry.scroll(ScrollMode.FORWARD_ONLY);
        try {
          while (invLines.next()) {
            final InventoryCount inventory = (InventoryCount) invLines.get()[0];
            new InventoryCountProcess().processInventory(inventory, false, true);
          }
        } finally {
          invLines.close();
        }

      } finally {
        scrollLines.close();
      }

      errorMessage.put("severity", "success");
      errorMessage.put("text", OBMessageUtils.messageBD("Success"));
      result.put("message", errorMessage);
    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      log.error(e.getMessage(), e);
      try {
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        errorMessage = new JSONObject();
        errorMessage.put("severity", "error");
        errorMessage.put("title", OBMessageUtils.messageBD("Error"));
        errorMessage.put("text", message);
        result.put("message", errorMessage);
      } catch (Exception ignore) {
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return result;
  }

  protected void createInventories(String lineId, Warehouse warehouse, String ruleId,
      Set<String> childOrgs, Date date) {

    CostingRule costRule = OBDal.getInstance().get(CostingRule.class, ruleId);
    InventoryAmountUpdateLine line = OBDal.getInstance()
        .get(InventoryAmountUpdateLine.class, lineId);
    ScrollableResults stockLines = getStockLines(childOrgs, date, line.getProduct(), warehouse,
        costRule.isBackdatedTransactionsFixed());
    // The key of the Map is the concatenation of orgId and warehouseId
    Map<String, String> inventories = new HashMap<String, String>();
    Map<String, Long> maxLineNumbers = new HashMap<String, Long>();
    InventoryCountLine closingInventoryLine = null;
    InventoryCountLine openInventoryLine = null;
    int i = 1;
    try {
      while (stockLines.next()) {
        Object[] stockLine = stockLines.get();
        String attrSetInsId = (String) stockLine[0];
        String uomId = (String) stockLine[1];
        String orderUOMId = (String) stockLine[2];
        String locatorId = (String) stockLine[3];
        String warehouseId = (String) stockLine[4];
        BigDecimal qty = (BigDecimal) stockLine[5];
        BigDecimal orderQty = (BigDecimal) stockLine[6];
        //
        String invId = inventories.get(warehouseId);
        InvAmtUpdLnInventories inv = null;
        if (invId == null) {
          inv = createInventorieLine(line, warehouseId, date);

          inventories.put(warehouseId, inv.getId());
        } else {
          inv = OBDal.getInstance().get(InvAmtUpdLnInventories.class, invId);
        }
        Long lineNo = (maxLineNumbers.get(inv.getId()) == null ? 0L
            : maxLineNumbers.get(inv.getId())) + 10L;
        maxLineNumbers.put(inv.getId(), lineNo);

        if (BigDecimal.ZERO.compareTo(qty) < 0) {
          // Do not insert negative values in Inventory lines, instead reverse the Quantity Count
          // and the Book Quantity. For example:
          // Instead of CountQty=0 and BookQty=-5 insert CountQty=5 and BookQty=0
          // By doing so the difference between both quantities remains the same and no negative
          // values have been inserted.

          openInventoryLine = insertInventoryLine(inv.getInitInventory(), line.getProduct().getId(),
              attrSetInsId, uomId, orderUOMId, locatorId, qty, BigDecimal.ZERO, orderQty,
              BigDecimal.ZERO, lineNo, null, line.getUnitCost());
          insertInventoryLine(inv.getCloseInventory(), line.getProduct().getId(), attrSetInsId,
              uomId, orderUOMId, locatorId, BigDecimal.ZERO, qty, BigDecimal.ZERO, orderQty, lineNo,
              openInventoryLine, null);

        } else {
          openInventoryLine = insertInventoryLine(inv.getInitInventory(), line.getProduct().getId(),
              attrSetInsId, uomId, orderUOMId, locatorId, BigDecimal.ZERO, qty.negate(),
              BigDecimal.ZERO, orderQty == null ? null : orderQty, lineNo, closingInventoryLine,
              line.getUnitCost());
          insertInventoryLine(inv.getCloseInventory(), line.getProduct().getId(), attrSetInsId,
              uomId, orderUOMId, locatorId, qty == null ? null : qty.negate(), BigDecimal.ZERO,
              orderQty == null ? null : orderQty, BigDecimal.ZERO, lineNo, openInventoryLine, null);

        }

        if ((i % 100) == 0) {
          OBDal.getInstance().flush();
          OBDal.getInstance().getSession().clear();
          // Reload line after clear session.
          line = OBDal.getInstance().get(InventoryAmountUpdateLine.class, lineId);
        }
        i++;
      }
    } finally {
      stockLines.close();
    }
    // Process closing physical inventories.
    for (InvAmtUpdLnInventories inv : line.getInventoryAmountUpdateLineInventoriesList()) {
      new InventoryCountProcess().processInventory(inv.getCloseInventory(), false);
    }
  }

  private ScrollableResults getStockLines(Set<String> childOrgs, Date date, Product product,
      Warehouse warehouse, boolean backdatedTransactionsFixed) {
    Date localDate = date;
    StringBuffer select = new StringBuffer();
    StringBuffer subSelect = new StringBuffer();

    select.append("select trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_UOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ORDERUOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_STORAGEBIN + ".id");
    select.append(", loc." + Locator.PROPERTY_WAREHOUSE + ".id");
    select.append(", sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ")");
    select.append(", sum(trx." + MaterialTransaction.PROPERTY_ORDERQUANTITY + ")");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    select.append("    join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as loc");
    select.append(" where trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    if (localDate != null) {
      if (backdatedTransactionsFixed) {
        select.append("   and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " <= :date");
      } else {
        subSelect
            .append("select min(trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + ")");
        subSelect.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
        if (warehouse != null) {
          subSelect
              .append("   join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
        }
        subSelect.append(" where trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :product");
        subSelect.append(" and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :date");
        // Include only transactions that have its cost calculated
        subSelect.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
        if (warehouse != null) {
          subSelect.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
        }
        subSelect
            .append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");

        Query<Date> trxsubQry = OBDal.getInstance()
            .getSession()
            .createQuery(subSelect.toString(), Date.class);
        trxsubQry.setParameter("date", localDate);
        trxsubQry.setParameter("product", product.getId());
        if (warehouse != null) {
          trxsubQry.setParameter("warehouse", warehouse.getId());
        }
        trxsubQry.setParameterList("orgs", childOrgs);
        Date trxprocessDate = trxsubQry.uniqueResult();
        if (trxprocessDate != null) {
          localDate = trxprocessDate;
          select.append(
              "   and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :date");
        } else {
          select.append("   and trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " <= :date");
        }
      }
    }
    if (warehouse != null) {
      select.append("   and loc." + Locator.PROPERTY_WAREHOUSE + " = :warehouse");
    }
    select.append("   and trx." + MaterialTransaction.PROPERTY_PRODUCT + " = :product");
    select.append(" group by trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_UOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ORDERUOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_STORAGEBIN + ".id");
    select.append(", loc." + Locator.PROPERTY_WAREHOUSE + ".id");
    select.append(" having ");
    select.append(" sum(trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ") <> 0");
    select.append(" order by loc." + Locator.PROPERTY_WAREHOUSE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_STORAGEBIN + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_UOM + ".id");
    select.append(", trx." + MaterialTransaction.PROPERTY_ORDERUOM + ".id");

    Query<Object[]> stockLinesQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Object[].class);
    stockLinesQry.setParameterList("orgs", childOrgs);
    if (localDate != null) {
      stockLinesQry.setParameter("date", localDate);
    }
    if (warehouse != null) {
      stockLinesQry.setParameter("warehouse", warehouse);
    }
    stockLinesQry.setParameter("product", product);
    stockLinesQry.setFetchSize(1000);
    ScrollableResults stockLines = stockLinesQry.scroll(ScrollMode.FORWARD_ONLY);
    return stockLines;
  }

  private InvAmtUpdLnInventories createInventorieLine(InventoryAmountUpdateLine invLine,
      String warehouseId, Date date) {
    Date localDate = date;
    if (localDate == null) {
      localDate = new Date();
    }
    Client client = (Client) OBDal.getInstance()
        .getProxy(Client.ENTITY_NAME, invLine.getClient().getId());
    String orgId = invLine.getOrganization().getId();
    Warehouse warehouse = (Warehouse) OBDal.getInstance()
        .getProxy(Warehouse.ENTITY_NAME, warehouseId);
    InvAmtUpdLnInventories inv = OBProvider.getInstance().get(InvAmtUpdLnInventories.class);
    inv.setClient(client);
    inv.setOrganization(
        (Organization) OBDal.getInstance().getProxy(Organization.ENTITY_NAME, orgId));
    inv.setWarehouse(warehouse);

    inv.setCaInventoryamtline(invLine);
    List<InvAmtUpdLnInventories> invList = invLine.getInventoryAmountUpdateLineInventoriesList();
    invList.add(inv);
    invLine.setInventoryAmountUpdateLineInventoriesList(invList);

    final InventoryCount closeInv = OBProvider.getInstance().get(InventoryCount.class);
    final Organization invOrg = getOrganizationForCloseAndOpenInventories(orgId, warehouse);
    closeInv.setClient(client);
    closeInv.setName(OBMessageUtils.messageBD("InvAmtUpdCloseInventory"));
    closeInv.setWarehouse(warehouse);
    closeInv.setOrganization(invOrg);
    closeInv.setMovementDate(localDate);
    closeInv.setInventoryType("C");
    inv.setCloseInventory(closeInv);

    final InventoryCount initInv = OBProvider.getInstance().get(InventoryCount.class);
    initInv.setClient(client);
    initInv.setName(OBMessageUtils.messageBD("InvAmtUpdInitInventory"));
    initInv.setWarehouse(warehouse);
    initInv.setOrganization(invOrg);
    initInv.setMovementDate(localDate);
    initInv.setInventoryType("O");
    inv.setInitInventory(initInv);
    OBDal.getInstance().save(invLine);
    OBDal.getInstance().save(closeInv);
    OBDal.getInstance().save(initInv);

    OBDal.getInstance().flush();

    return inv;
  }

  private InventoryCountLine insertInventoryLine(InventoryCount inventory, String productId,
      String attrSetInsId, String uomId, String orderUOMId, String locatorId, BigDecimal qtyCount,
      BigDecimal qtyBook, BigDecimal orderQtyCount, BigDecimal orderQtyBook, Long lineNo,
      InventoryCountLine relatedInventoryLine, BigDecimal cost) {
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
    if (cost != null) {
      icl.setCost(cost);
    }
    List<InventoryCountLine> invLines = inventory.getMaterialMgmtInventoryCountLineList();
    invLines.add(icl);
    inventory.setMaterialMgmtInventoryCountLineList(invLines);
    OBDal.getInstance().save(inventory);
    OBDal.getInstance().flush();
    return icl;
  }

  private Organization getOrganizationForCloseAndOpenInventories(final String inventoryLineOrgId,
      final Warehouse warehouse) {
    Organization invOrg = getTransactionAllowedOrg(warehouse.getOrganization());
    if (invOrg == null) {
      return (Organization) OBDal.getInstance()
          .getProxy(Organization.ENTITY_NAME, inventoryLineOrgId);
    }
    return invOrg;
  }

  private Organization getTransactionAllowedOrg(final Organization org) {
    if (org.getOrganizationType().isTransactionsAllowed()) {
      return org;
    } else {
      final Organization parentOrg = OBContext.getOBContext()
          .getOrganizationStructureProvider()
          .getParentOrg(org);
      if (parentOrg != null && !isStarOrganization(parentOrg)) {
        return getTransactionAllowedOrg(parentOrg);
      } else {
        return null;
      }
    }
  }

  private boolean isStarOrganization(final Organization parentOrg) {
    return StringUtils.equals(parentOrg.getId(), "0");
  }
}
