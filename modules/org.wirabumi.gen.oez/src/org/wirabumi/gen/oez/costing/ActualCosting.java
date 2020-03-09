package org.wirabumi.gen.oez.costing;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.costing.CostingAlgorithm;
import org.openbravo.costing.CostingServer.TrxType;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.cost.Costing;

public class ActualCosting extends CostingAlgorithm {
  private Logger log4j = Logger.getLogger(ActualCosting.class);

  public BigDecimal getTransactionCost() {

    BigDecimal trxCost = null;
    AttributeSetInstance attribute = this.transaction.getAttributeSetValue();

    switch (trxType) {
    case ShipmentVoid:
    case ShipmentNegative:
      this.transaction.setCostingStatus("NC");
      OBDal.getInstance().save(this.transaction);
      trxCost = getShipmentVoidTransactionCost();
      if (trxCost != null) {
        UpdateShipmentVoidCost(attribute, this.transaction.getProduct());
      }
      break;
    case ShipmentReturn:
    case ReceiptReturn:
    case InventoryIncrease:
    case InventoryDecrease:
    case IntMovementFrom:
    case IntMovementTo:
    case InternalCons:
    case InternalConsNegative:
    case InternalConsVoid:
    case BOMPart:
    case BOMProduct:
    case ManufacturingConsumed:
    case ManufacturingProduced:
    case Unknown:
      this.transaction.setCostingStatus("P");
      OBDal.getInstance().save(this.transaction);
      return null;
    case ReceiptVoid:
    case Shipment:
      this.transaction.setCostingStatus("NC");
      OBDal.getInstance().save(this.transaction);
      trxCost = getOutgoingTransactionCost();
      break;
    case ReceiptNegative:
      trxType = TrxType.Receipt;
      this.transaction.setCostingStatus("NC");
      OBDal.getInstance().save(this.transaction);
      trxCost = super.getTransactionCost();
      break;
    case Receipt:
      this.transaction.setCostingStatus("NC");
      OBDal.getInstance().save(this.transaction);
      trxCost = super.getTransactionCost();
      if (trxCost != null) {
        insertCost(trxCost, attribute, false);
      }
      break;

    default:
      break;
    }
    return trxCost;
  }

  private BigDecimal getShipmentVoidTransactionCost() {
    BigDecimal outgoingTransactionCost;
    if (!trxType.toString().equalsIgnoreCase("ShipmentVoid")) {
      this.transaction.setCostingStatus("P");
      OBDal.getInstance().save(this.transaction);
      return null;
    }

    // lookup for fully matched record using attribute set instance, and available qty > 0
    String sqlQuery = "select id from MaterialMgmtCosting" + " where product.id = ?"
        + " and  (oezAttributesetinstance.id = ? or oezSerno = ?)";
    final Query query = OBDal.getInstance().getSession().createQuery(sqlQuery);
    query.setParameter(0, this.transaction.getProduct().getId());
    query.setParameter(1, this.transaction.getAttributeSetValue().getId());
    OBContext.setAdminMode();
    query.setParameter(2, this.transaction.getAttributeSetValue().getSerialNo());
    OBContext.restorePreviousMode();
    final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
    while (result.next()) {
      String costingId = (String) result.get()[0];
      Costing productCost = OBDal.getInstance().get(Costing.class, costingId);
      outgoingTransactionCost = productCost.getCost();
      productCost.setOezUsedqty(productCost.getOezUsedqty().add(new BigDecimal(1)));
      OBDal.getInstance().save(productCost);
      return outgoingTransactionCost;
    }
    // can not find costing with same serial number with any available qty, throw exception
    long a = new BigDecimal(0).longValue();
    if (log4j.isDebugEnabled()) {
      log4j.error("can not find valid costing for product "
          + this.transaction.getProduct().getName() + " with attributesetinstance ID "
          + this.transaction.getAttributeSetValue().getId() + " transaction ID "
          + this.transaction.getId());
    }
    this.transaction.setCostingStatus("P");
    OBDal.getInstance().save(this.transaction);
    return null;
  }

  private void UpdateShipmentVoidCost(AttributeSetInstance attribute, Product product) {
    OBCriteria<Costing> costingCriteria = OBDal.getInstance().createCriteria(Costing.class);
    costingCriteria.add(Restrictions.eq(Costing.PROPERTY_OEZATTRIBUTESETINSTANCE, attribute));
    costingCriteria.add(Restrictions.eq(Costing.PROPERTY_PRODUCT, product));
    List<Costing> costingList = costingCriteria.list();
    if (costingList != null) {
      Costing cost = costingList.get(0);
      cost.setOezUsedqty(cost.getOezUsedqty().subtract(this.transaction.getMovementQuantity()));
      OBDal.getInstance().save(cost);
    }
  }

  // already implemented transaction type: Shipment
  @Override
  public BigDecimal getOutgoingTransactionCost() {
    BigDecimal outgoingTransactionCost;
    if (!trxType.toString().equalsIgnoreCase("shipment")
        && !trxType.toString().equalsIgnoreCase("ReceiptVoid")) {
      this.transaction.setCostingStatus("P");
      OBDal.getInstance().save(this.transaction);
      return null;
    }

    // lookup for fully matched record using attribute set instance, and available qty > 0
    String sqlQuery = "select id from MaterialMgmtCosting" + " where product.id = ?"
        + " and  (oezAttributesetinstance.id = ? or oezSerno = ?)" + " and quantity > oezUsedqty";
    final Query query = OBDal.getInstance().getSession().createQuery(sqlQuery);
    query.setParameter(0, this.transaction.getProduct().getId());
    query.setParameter(1, this.transaction.getAttributeSetValue().getId());
    OBContext.setAdminMode();
    query.setParameter(2, this.transaction.getAttributeSetValue().getSerialNo());
    OBContext.restorePreviousMode();
    final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
    while (result.next()) {
      String costingId = (String) result.get()[0];
      Costing productCost = OBDal.getInstance().get(Costing.class, costingId);
      outgoingTransactionCost = productCost.getCost();
      productCost.setOezUsedqty(productCost.getOezUsedqty().add(new BigDecimal(1)));
      OBDal.getInstance().save(productCost);
      return outgoingTransactionCost;
    }
    // can not find costing with same serial number with any available qty, throw exception
    log4j.error("can not find valid costing for product " + this.transaction.getProduct().getName()
        + " with attributesetinstance ID " + this.transaction.getAttributeSetValue().getId()
        + " transaction ID " + this.transaction.getId());
    this.transaction.setCostingStatus("P");
    OBDal.getInstance().save(this.transaction);
    return null;
  }

  /**
   * In case the Default Cost is used it prioritizes the existence of an average cost.
   */
  @Override
  public BigDecimal getDefaultCost() {
    if (getProductCost() != null) {
      return getOutgoingTransactionCost();
    }
    return super.getDefaultCost();
  }

  private void insertCost(BigDecimal trxCost, AttributeSetInstance attribute,
      boolean productionTransaction) {
    Costing cost = OBProvider.getInstance().get(Costing.class);
    BigDecimal movementQty = this.transaction.getMovementQuantity();
    if (movementQty.compareTo(new BigDecimal(0)) == 0) {
      // movementqty=0, need not insert cost
      return;
    }
    cost.setProduct(this.transaction.getProduct());
    cost.setStartingDate(this.transaction.getMovementDate());
    cost.setEndingDate(getLastDate());
    cost.setManual(false);
    cost.setGoodsShipmentLine(this.transaction.getGoodsShipmentLine());
    cost.setQuantity(this.transaction.getMovementQuantity());
    cost.setPrice(trxCost.divide(movementQty));
    cost.setTotalMovementQuantity(this.transaction.getMovementQuantity());
    cost.setCostType("OEZ_ACTUALCOST");
    cost.setPermanent(true);
    cost.setCost(trxCost.divide(movementQty));
    cost.setProduction(productionTransaction);
    cost.setWarehouse(this.transaction.getStorageBin().getWarehouse());
    cost.setInventoryTransaction(this.transaction);
    cost.setCurrency(this.transaction.getCurrency());
    OBContext.setAdminMode();
    cost.setOezAttributesetinstance(this.transaction.getAttributeSetValue());
    cost.setOezSerno(this.transaction.getAttributeSetValue().getSerialNo());
    OBContext.restorePreviousMode();
    cost.setOezUsedqty(new BigDecimal(0));

    OBDal.getInstance().save(cost);

  }

  private Costing getProductCost() {
    Product product = transaction.getProduct();
    Date date = transaction.getTransactionProcessDate();
    StringBuffer where = new StringBuffer();
    where.append(Costing.PROPERTY_PRODUCT + ".id = :product");
    where.append("  and " + Costing.PROPERTY_STARTINGDATE + " <= :startingDate");
    where.append("  and " + Costing.PROPERTY_ENDINGDATE + " > :endingDate");
    where.append("  and " + Costing.PROPERTY_COSTTYPE + " = 'AVA'");
    where.append("  and " + Costing.PROPERTY_COST + " is not null");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      where.append("  and " + Costing.PROPERTY_WAREHOUSE + ".id = :warehouse");
    } else {
      where.append("  and " + Costing.PROPERTY_WAREHOUSE + " is null");
    }
    if (product.isProduction()) {
      where.append("  and " + Costing.PROPERTY_CLIENT + ".id = :client");
    } else {
      where.append("  and " + Costing.PROPERTY_ORGANIZATION + ".id = :org");
    }
    OBQuery<Costing> costQry = OBDal.getInstance().createQuery(Costing.class, where.toString());
    costQry.setFilterOnReadableOrganization(false);
    costQry.setNamedParameter("product", product.getId());
    costQry.setNamedParameter("startingDate", date);
    costQry.setNamedParameter("endingDate", date);
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      costQry.setNamedParameter("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    if (product.isProduction()) {
      costQry.setNamedParameter("client", costOrg.getClient());
    } else {
      costQry.setNamedParameter("org", costOrg);
    }

    List<Costing> costList = costQry.list();
    // If no average cost is found return null.
    if (costList.size() == 0) {
      return null;
    }
    if (costList.size() > 1) {
      log4j.warn("More than one cost found for same date: " + OBDateUtils.formatDate(date)
          + " for product: " + product.getName() + " (" + product.getId() + ")");
    }
    return costList.get(0);
  }

  private Date getLastDate() {
    SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
    try {
      return outputFormat.parse("31-12-9999");
    } catch (ParseException e) {
      // Error parsing the date.
      log4j.error("Error parsing the date.", e);
      return null;
    }
  }

}
