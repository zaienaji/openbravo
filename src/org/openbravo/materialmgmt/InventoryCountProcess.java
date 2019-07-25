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
 * All portions are Copyright (C) 2012-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.materialmgmt;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.query.Query;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.materialmgmt.hook.InventoryCountCheckHook;
import org.openbravo.materialmgmt.hook.InventoryCountProcessHook;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.financialmgmt.calendar.PeriodControl;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.scheduling.Process;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalConnectionProvider;

public class InventoryCountProcess implements Process {
  private static final Logger log4j = LogManager.getLogger();

  @Inject
  @Any
  private Instance<InventoryCountCheckHook> inventoryCountChecks;

  @Inject
  @Any
  private Instance<InventoryCountProcessHook> inventoryCountProcesses;

  @Override
  public void execute(ProcessBundle bundle) throws Exception {
    OBError msg = new OBError();
    msg.setType("Success");
    msg.setTitle(OBMessageUtils.messageBD("Success"));

    try {
      // retrieve standard params
      final String recordID = (String) bundle.getParams().get("M_Inventory_ID");
      final InventoryCount inventory = OBDal.getInstance().get(InventoryCount.class, recordID);

      // lock inventory
      if (inventory.isProcessNow()) {
        throw new OBException(OBMessageUtils.parseTranslation("@OtherProcessActive@"));
      }
      inventory.setProcessNow(true);
      OBDal.getInstance().save(inventory);
      if (SessionHandler.isSessionHandlerPresent()) {
        SessionHandler.getInstance().commitAndStart();
      }

      OBContext.setAdminMode(false);
      try {
        msg = processInventory(inventory);
      } finally {
        OBContext.restorePreviousMode();
      }

      inventory.setProcessNow(false);

      OBDal.getInstance().save(inventory);
      OBDal.getInstance().flush();

      bundle.setResult(msg);

    } catch (GenericJDBCException ge) {
      log4j.error("Exception processing physical inventory", ge);
      msg.setType("Error");
      msg.setTitle(OBMessageUtils.messageBD(bundle.getConnection(), "Error",
          bundle.getContext().getLanguage()));
      msg.setMessage(ge.getSQLException().getMessage().split("\n")[0]);
      bundle.setResult(msg);
      OBDal.getInstance().rollbackAndClose();
      final String recordID = (String) bundle.getParams().get("M_Inventory_ID");
      final InventoryCount inventory = OBDal.getInstance().get(InventoryCount.class, recordID);
      inventory.setProcessNow(false);
      OBDal.getInstance().save(inventory);
    } catch (final Exception e) {
      log4j.error("Exception processing physical inventory", e);
      msg.setType("Error");
      msg.setTitle(OBMessageUtils.messageBD(bundle.getConnection(), "Error",
          bundle.getContext().getLanguage()));
      msg.setMessage(FIN_Utility.getExceptionMessage(e));
      bundle.setResult(msg);
      OBDal.getInstance().rollbackAndClose();
      final String recordID = (String) bundle.getParams().get("M_Inventory_ID");
      final InventoryCount inventory = OBDal.getInstance().get(InventoryCount.class, recordID);
      inventory.setProcessNow(false);
      OBDal.getInstance().save(inventory);
    }

  }

  public OBError processInventory(InventoryCount inventory) throws OBException {
    return processInventory(inventory, true);
  }

  public OBError processInventory(InventoryCount inventory, boolean checkReservationQty)
      throws OBException {
    return processInventory(inventory, checkReservationQty, false);
  }

  public OBError processInventory(InventoryCount inventory, boolean checkReservationQty,
      boolean checkPermanentCost) throws OBException {
    OBError msg = new OBError();
    msg.setType("Success");
    msg.setTitle(OBMessageUtils.messageBD("Success"));
    runChecks(inventory);

    StringBuffer insert = new StringBuffer();
    insert.append("insert into " + MaterialTransaction.ENTITY_NAME + "(");
    insert.append(" id ");
    insert.append(", " + MaterialTransaction.PROPERTY_ACTIVE);
    insert.append(", " + MaterialTransaction.PROPERTY_CLIENT);
    insert.append(", " + MaterialTransaction.PROPERTY_ORGANIZATION);
    insert.append(", " + MaterialTransaction.PROPERTY_CREATIONDATE);
    insert.append(", " + MaterialTransaction.PROPERTY_CREATEDBY);
    insert.append(", " + MaterialTransaction.PROPERTY_UPDATED);
    insert.append(", " + MaterialTransaction.PROPERTY_UPDATEDBY);
    insert.append(", " + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    insert.append(", " + MaterialTransaction.PROPERTY_CHECKRESERVEDQUANTITY);
    insert.append(", " + MaterialTransaction.PROPERTY_ISCOSTPERMANENT);
    insert.append(", " + MaterialTransaction.PROPERTY_MOVEMENTDATE);
    insert.append(", " + MaterialTransaction.PROPERTY_STORAGEBIN);
    insert.append(", " + MaterialTransaction.PROPERTY_PRODUCT);
    insert.append(", " + MaterialTransaction.PROPERTY_ATTRIBUTESETVALUE);
    insert.append(", " + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY);
    insert.append(", " + MaterialTransaction.PROPERTY_UOM);
    insert.append(", " + MaterialTransaction.PROPERTY_ORDERQUANTITY);
    insert.append(", " + MaterialTransaction.PROPERTY_ORDERUOM);
    insert.append(", " + MaterialTransaction.PROPERTY_PHYSICALINVENTORYLINE);
    insert.append(", " + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
    // select from inventory line
    insert.append(" ) \n select get_uuid() ");
    insert.append(", e." + InventoryCountLine.PROPERTY_ACTIVE);
    insert.append(", e." + InventoryCountLine.PROPERTY_CLIENT);
    insert.append(", e." + InventoryCountLine.PROPERTY_ORGANIZATION);
    insert.append(", now()");
    insert.append(", u");
    insert.append(", now()");
    insert.append(", u");
    insert.append(", 'I+'");
    // We have to set check reservation quantity flag equal to checkReservationQty
    // InventoryCountLine.PROPERTY_ACTIVE-->> Y
    // InventoryCountLine.PROPERTY_PHYSINVENTORY + "." + InventoryCount.PROPERTY_PROCESSED -->> N
    if (checkReservationQty) {
      insert.append(", e." + InventoryCountLine.PROPERTY_ACTIVE);
    } else {
      insert.append(", e." + InventoryCountLine.PROPERTY_PHYSINVENTORY + "."
          + InventoryCount.PROPERTY_PROCESSED);
    }
    // We have to set check permanent cost flag
    // InventoryCountLine.PROPERTY_ACTIVE-->> Y
    // InventoryCountLine.PROPERTY_PHYSINVENTORY + "." + InventoryCount.PROPERTY_PROCESSED -->> N
    if (checkPermanentCost) {
      insert.append(", e." + InventoryCountLine.PROPERTY_ACTIVE);
    } else {
      insert.append(", e." + InventoryCountLine.PROPERTY_PHYSINVENTORY + "."
          + InventoryCount.PROPERTY_PROCESSED);
    }
    insert.append(", e." + InventoryCountLine.PROPERTY_PHYSINVENTORY + "."
        + InventoryCount.PROPERTY_MOVEMENTDATE);
    insert.append(", e." + InventoryCountLine.PROPERTY_STORAGEBIN);
    insert.append(", e." + InventoryCountLine.PROPERTY_PRODUCT);
    insert.append(", asi");
    insert.append(", e." + InventoryCountLine.PROPERTY_QUANTITYCOUNT + " - COALESCE(" + "e."
        + InventoryCountLine.PROPERTY_BOOKQUANTITY + ", 0)");
    insert.append(", e." + InventoryCountLine.PROPERTY_UOM);
    insert.append(", e." + InventoryCountLine.PROPERTY_ORDERQUANTITY + " - COALESCE(" + "e."
        + InventoryCountLine.PROPERTY_QUANTITYORDERBOOK + ", 0)");
    insert.append(", e." + InventoryCountLine.PROPERTY_ORDERUOM);
    insert.append(", e");
    insert.append(", to_timestamp(to_char(:currentDate), to_char('DD-MM-YYYY HH24:MI:SS'))");
    insert.append(" \nfrom " + InventoryCountLine.ENTITY_NAME + " as e");
    insert.append(" , " + User.ENTITY_NAME + " as u");
    insert.append(" , " + AttributeSetInstance.ENTITY_NAME + " as asi");
    insert.append(" , " + Product.ENTITY_NAME + " as p");
    insert.append(" \nwhere e." + InventoryCountLine.PROPERTY_PHYSINVENTORY + ".id = :inv");
    insert.append(" and (e." + InventoryCountLine.PROPERTY_QUANTITYCOUNT + " != e."
        + InventoryCountLine.PROPERTY_BOOKQUANTITY);
    insert.append(" or e." + InventoryCountLine.PROPERTY_ORDERQUANTITY + " != e."
        + InventoryCountLine.PROPERTY_QUANTITYORDERBOOK + ")");
    insert.append(" and u.id = :user");
    insert.append(
        " and asi.id = COALESCE(e." + InventoryCountLine.PROPERTY_ATTRIBUTESETVALUE + ".id , '0')");
    // Non Stockable Products should not generate warehouse transactions
    insert.append(" and e." + InventoryCountLine.PROPERTY_PRODUCT + ".id = p.id and p."
        + Product.PROPERTY_STOCKED + " = 'Y' and p." + Product.PROPERTY_PRODUCTTYPE + " = 'I'");

    @SuppressWarnings("rawtypes")
    Query queryInsert = OBDal.getInstance().getSession().createQuery(insert.toString());
    queryInsert.setParameter("inv", inventory.getId());
    queryInsert.setParameter("user", OBContext.getOBContext().getUser().getId());
    final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    queryInsert.setParameter("currentDate", dateFormatter.format(new Date()));
    // queryInsert.setBoolean("checkReservation", checkReservationQty);
    queryInsert.executeUpdate();

    if (!"C".equals(inventory.getInventoryType()) && !"O".equals(inventory.getInventoryType())) {
      checkStock(inventory);
    }

    try {
      executeHooks(inventoryCountProcesses, inventory);
    } catch (Exception e) {
      OBException obException = new OBException(e.getMessage(), e.getCause());
      throw obException;
    }
    inventory.setProcessed(true);
    OBDal.getInstance().flush();

    // Update inventory date for inventory count lines whose qtycount is equal to book quantity or
    // orderquantity is equals to quantity order book
    updateDateInventory(inventory);

    return msg;
  }

  private void runChecks(InventoryCount inventory) throws OBException {
    try {
      executeHooks(inventoryCountChecks, inventory);
    } catch (Exception genericException) {
      throw new OBException(genericException.getMessage(), genericException.getCause());
    }
    checkInventoryAlreadyProcessed(inventory);
    checkMandatoryAttributesWithoutVavlue(inventory);
    checkDuplicatedProducts(inventory);
    Organization org = inventory.getOrganization();
    checkIfOrganizationIsReady(org);
    checkOrganizationAllowsTransactions(org);
    checkDifferentLegalInLinesAndHeader(inventory, org);
    checkPeriodsNotAvailable(inventory, org);
  }

  private void checkInventoryAlreadyProcessed(InventoryCount inventory) {
    if (inventory.isProcessed()) {
      throw new OBException(OBMessageUtils.parseTranslation("@AlreadyPosted@"));
    }
  }

  private void checkMandatoryAttributesWithoutVavlue(InventoryCount inventory) {
    InventoryCountLine inventoryLine = getLineWithMandatoryAttributeWithoutValue(inventory);
    if (inventoryLine != null) {
      throw new OBException(OBMessageUtils.parseTranslation(
          "@Inline@ " + (inventoryLine).getLineNo() + " @productWithoutAttributeSet@"));
    }
  }

  private InventoryCountLine getLineWithMandatoryAttributeWithoutValue(InventoryCount inventory) {
    StringBuilder where = new StringBuilder();
    where.append(" as icl ");
    where.append("   join icl.product as p ");
    where.append("   join icl.storageBin as sb ");
    where.append("   join p.attributeSet as aset ");
    where.append(" where icl.physInventory.id = :inventory ");
    where.append("   and aset.requireAtLeastOneValue = true ");
    where.append("   and coalesce(p.useAttributeSetValueAs, '-') <> 'F' ");
    where.append("   and coalesce(icl.attributeSetValue, '0') = '0' ");
    // Allow to regularize to 0 any existing Stock without attribute for this Product
    // (this situation can happen when there is a bug in a different part of the code,
    // but the user should be able always to zero this stock)
    where.append("   and (icl.quantityCount <> 0 ");
    where.append("        or (icl.quantityCount = 0 ");
    where.append("            and not exists (select 1 from MaterialMgmtStorageDetail sd ");
    where.append("                                     where sd.storageBin.id = sb.id ");
    where.append("                                     and sd.product.id = p.id ");
    where.append("                                     and sd.attributeSetValue = '0' ");
    where.append("                                     and sd.uOM.id = icl.uOM.id ");
    where.append("                                     and sd.quantityOnHand <> 0 ");
    where.append("                                    and sd.quantityInDraftTransactions <> 0 ) ");
    where.append("                           ) ");
    where.append("           ) ");
    where.append("  order by icl.lineNo ");
    OBQuery<InventoryCountLine> query = OBDal.getInstance()
        .createQuery(InventoryCountLine.class, where.toString());
    query.setNamedParameter("inventory", inventory.getId());
    query.setMaxResult(1);
    return query.uniqueResult();
  }

  private void checkDuplicatedProducts(InventoryCount inventory) {
    List<InventoryCountLine> inventoryLineList = getLinesWithDuplicatedProducts(inventory);
    if (!inventoryLineList.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder("");
      for (InventoryCountLine icl2 : inventoryLineList) {
        errorMessage.append(icl2.getLineNo().toString() + ", ");
      }
      throw new OBException(OBMessageUtils
          .parseTranslation("@Thelines@ " + errorMessage.toString() + "@sameInventorylines@"));
    }
  }

  private List<InventoryCountLine> getLinesWithDuplicatedProducts(InventoryCount inventory) {
    StringBuilder where = new StringBuilder();
    where.append(" as icl");
    where.append(" where icl.physInventory.id = :inventory");
    where.append("   and exists (select 1 ");
    where.append("               from MaterialMgmtInventoryCountLine as icl2");
    where.append("               where icl.physInventory = icl2.physInventory");
    where.append("               and icl.product = icl2.product");
    where.append(
        "                and coalesce(icl.attributeSetValue, '0') = coalesce(icl2.attributeSetValue, '0')");
    where.append("               and coalesce(icl.orderUOM, '0') = coalesce(icl2.orderUOM, '0')");
    where.append("               and coalesce(icl.uOM, '0') = coalesce(icl2.uOM, '0')");
    where.append("               and icl.storageBin = icl2.storageBin");
    where.append("               and icl.lineNo <> icl2.lineNo)");
    where.append(" order by icl.product");
    where.append(", icl.attributeSetValue");
    where.append(", icl.storageBin");
    where.append(", icl.orderUOM");
    where.append(", icl.lineNo");
    OBQuery<InventoryCountLine> query = OBDal.getInstance()
        .createQuery(InventoryCountLine.class, where.toString());
    query.setNamedParameter("inventory", inventory.getId());
    return query.list();
  }

  private void checkIfOrganizationIsReady(Organization org) {
    if (!org.isReady()) {
      throw new OBException(OBMessageUtils.parseTranslation("@OrgHeaderNotReady@"));
    }
  }

  private void checkOrganizationAllowsTransactions(Organization org) {
    if (!org.getOrganizationType().isTransactionsAllowed()) {
      throw new OBException(OBMessageUtils.parseTranslation("@OrgHeaderNotTransAllowed@"));
    }
  }

  private void checkDifferentLegalInLinesAndHeader(InventoryCount inventory, Organization org) {
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(inventory.getClient().getId());
    Organization inventoryLegalOrBusinessUnitOrg = osp.getLegalEntityOrBusinessUnit(org);
    List<InventoryCountLine> inventoryLineList = getLinesWithDifferentOrganizationThanHeader(
        inventory, org);
    if (!inventoryLineList.isEmpty()) {
      for (InventoryCountLine inventoryLine : inventoryLineList) {
        if (!inventoryLegalOrBusinessUnitOrg.getId()
            .equals(osp.getLegalEntityOrBusinessUnit(inventoryLine.getOrganization()).getId())) {
          throw new OBException(OBMessageUtils.parseTranslation("@LinesAndHeaderDifferentLEorBU@"));
        }
      }
    }
  }

  private List<InventoryCountLine> getLinesWithDifferentOrganizationThanHeader(
      InventoryCount inventory, Organization org) {
    OBQuery<InventoryCountLine> query = OBDal.getInstance()
        .createQuery(InventoryCountLine.class,
            InventoryCountLine.PROPERTY_PHYSINVENTORY + ".id = :inventory and "
                + InventoryCountLine.PROPERTY_ORGANIZATION + ".id <> :organization");
    query.setNamedParameter("inventory", inventory.getId());
    query.setNamedParameter("organization", org.getId());
    return query.list();
  }

  private void checkPeriodsNotAvailable(InventoryCount inventory, Organization org) {
    OrganizationStructureProvider osp = OBContext.getOBContext()
        .getOrganizationStructureProvider(inventory.getClient().getId());
    Organization inventoryLegalOrBusinessUnitOrg = osp.getLegalEntityOrBusinessUnit(org);
    if (inventoryLegalOrBusinessUnitOrg.getOrganizationType().isLegalEntityWithAccounting()) {
      StringBuilder where = new StringBuilder();
      where.append(" as pc ");
      where.append("   join pc.period as p");
      where.append(" where p.startingDate <= :dateStarting");
      where.append("   and p.endingDate >= :dateEnding");
      where.append("   and pc.documentCategory = 'MMI' ");
      where.append("   and pc.organization.id = :org");
      where.append("   and pc.periodStatus = 'O'");
      OBQuery<PeriodControl> query = OBDal.getInstance()
          .createQuery(PeriodControl.class, where.toString());
      query.setFilterOnReadableClients(false);
      query.setFilterOnReadableOrganization(false);
      query.setNamedParameter("dateStarting", inventory.getMovementDate());
      query.setNamedParameter("dateEnding",
          DateUtils.truncate(inventory.getMovementDate(), Calendar.DATE));
      query.setNamedParameter("org", osp.getPeriodControlAllowedOrganization(org).getId());
      query.setMaxResult(1);
      if (query.uniqueResult() == null) {
        throw new OBException(OBMessageUtils.parseTranslation("@PeriodNotAvailable@"));
      }
    }
  }

  private void updateDateInventory(InventoryCount inventory) {

    try {
      for (InventoryCountLine invCountLine : inventory.getMaterialMgmtInventoryCountLineList()) {
        if (invCountLine.getQuantityCount().compareTo(invCountLine.getBookQuantity()) == 0
            || (invCountLine.getOrderQuantity() != null
                && invCountLine.getQuantityOrderBook() != null && invCountLine.getOrderQuantity()
                    .compareTo(invCountLine.getQuantityOrderBook()) == 0)) {
          org.openbravo.database.ConnectionProvider cp = new DalConnectionProvider(false);
          CallableStatement updateStockStatement = cp.getConnection()
              .prepareCall("{call M_UPDATE_INVENTORY (?,?,?,?,?,?,?,?,?,?,?,?,?)}");
          // client
          updateStockStatement.setString(1, invCountLine.getClient().getId());
          // org
          updateStockStatement.setString(2, invCountLine.getOrganization().getId());
          // user
          updateStockStatement.setString(3, OBContext.getOBContext().getUser().getId());
          // product
          updateStockStatement.setString(4, invCountLine.getProduct().getId());
          // locator
          updateStockStatement.setString(5, invCountLine.getStorageBin().getId());
          // attributesetinstance
          updateStockStatement.setString(6,
              invCountLine.getAttributeSetValue() != null
                  ? invCountLine.getAttributeSetValue().getId()
                  : null);
          // uom
          updateStockStatement.setString(7, invCountLine.getUOM().getId());
          // product uom
          updateStockStatement.setString(8,
              invCountLine.getOrderUOM() != null ? invCountLine.getOrderUOM().getId() : null);
          // p_qty
          updateStockStatement.setBigDecimal(9, BigDecimal.ZERO);
          // p_qtyorder
          updateStockStatement.setBigDecimal(10, BigDecimal.ZERO);
          // p_dateLastInventory --- **
          updateStockStatement.setDate(11,
              new java.sql.Date(inventory.getMovementDate().getTime()));
          // p_preqty
          updateStockStatement.setBigDecimal(12, BigDecimal.ZERO);
          // p_preqtyorder
          updateStockStatement.setBigDecimal(13, BigDecimal.ZERO);

          updateStockStatement.execute();
        }
      }
      OBDal.getInstance().flush();
    } catch (Exception e) {
      log4j.error("Error in updateDateInventory while Inventory Count Process", e);
      throw new OBException(e.getMessage(), e);
    }
  }

  private void checkStock(InventoryCount inventory) {
    String attribute;
    final StringBuilder hqlString = new StringBuilder();
    hqlString.append("select sd.id ");
    hqlString.append(" from MaterialMgmtInventoryCountLine as icl");
    hqlString.append(" , MaterialMgmtStorageDetail as sd");
    hqlString.append(" , Locator as l");
    hqlString.append(" , MaterialMgmtInventoryStatus as invs");
    hqlString.append(" where icl.physInventory.id = :physInventoryId");
    hqlString.append("   and sd.product = icl.product");
    hqlString.append("   and (sd.quantityOnHand < 0");
    hqlString.append("     or sd.onHandOrderQuanity < 0");
    hqlString.append("     )");
    // Check only negative Stock for the Bins of the Lines of the Physical Inventory
    hqlString.append("   and sd.storageBin.id = icl.storageBin.id");
    hqlString.append("   and l.id = icl.storageBin.id");
    hqlString.append("   and l.inventoryStatus.id = invs.id");
    hqlString.append("   and invs.overissue = false");
    hqlString.append(" order by icl.lineNo");

    final Session session = OBDal.getInstance().getSession();
    final Query<String> query = session.createQuery(hqlString.toString(), String.class);
    query.setParameter("physInventoryId", inventory.getId());
    query.setMaxResults(1);

    if (!query.list().isEmpty()) {
      StorageDetail storageDetail = OBDal.getInstance()
          .get(StorageDetail.class, query.list().get(0).toString());
      attribute = (!storageDetail.getAttributeSetValue().getIdentifier().isEmpty())
          ? " @PCS_ATTRIBUTE@ '" + storageDetail.getAttributeSetValue().getIdentifier() + "', "
          : "";
      throw new OBException(Utility
          .messageBD(new DalConnectionProvider(), "insuffient_stock",
              OBContext.getOBContext().getLanguage().getLanguage())
          .replaceAll("%1", storageDetail.getProduct().getIdentifier())
          .replaceAll("%2", attribute)
          .replaceAll("%3", storageDetail.getUOM().getIdentifier())
          .replaceAll("%4", storageDetail.getStorageBin().getIdentifier()));
    }
  }

  private void executeHooks(Instance<? extends Object> hooks, InventoryCount inventory)
      throws Exception {
    if (hooks != null) {
      for (Iterator<? extends Object> procIter = hooks.iterator(); procIter.hasNext();) {
        Object proc = procIter.next();
        if (proc instanceof InventoryCountProcessHook) {
          ((InventoryCountProcessHook) proc).exec(inventory);
        } else {
          ((InventoryCountCheckHook) proc).exec(inventory);
        }
      }
    }
  }
}
