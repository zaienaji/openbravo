package org.wirabumi.gen.oez;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CreatePurchaseOrder extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    // TODO Auto-generated method stub
    final OBError msg = new OBError();
    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
    String pesan = "";

    Order salesOrder = OBDal.getInstance().get(Order.class, bundle.getParams().get("C_Order_ID"));
    List<Object> param = new ArrayList<Object>();
    param.add(salesOrder);
    Client klien = OBDal.getInstance().get(Client.class, bundle.getContext().getClient());
    Organization org = OBDal.getInstance().get(Organization.class,
        bundle.getParams().get("adOrgId"));
    BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class,
        bundle.getParams().get("cBpartnerId"));
    OBCriteria<Location> loc = OBDal.getInstance().createCriteria(Location.class);
    loc.add(Restrictions.eq(Location.PROPERTY_BUSINESSPARTNER, bp));
    Location lokasi = loc.list().get(0);
    PriceList priceList = OBDal.getInstance().get(PriceList.class,
        bundle.getParams().get("mPricelistId"));
    DocumentType docType = OBDal.getInstance().get(DocumentType.class, "0");
    DocumentType docTrx = OBDal.getInstance().get(DocumentType.class,
        bundle.getParams().get("cDoctypeId"));
    Date orderDate = sdf.parse(bundle.getParams().get("dateordered").toString());
    Date promisedDate = sdf.parse(bundle.getParams().get("datepromised").toString());
    Warehouse gudang = OBDal.getInstance().get(Warehouse.class,
        bundle.getParams().get("mWarehouseId"));
    PaymentTerm payTerm = OBDal.getInstance().get(PaymentTerm.class,
        bundle.getParams().get("cPaymenttermId"));
    FIN_PaymentMethod payMethod = OBDal.getInstance().get(FIN_PaymentMethod.class,
        bundle.getParams().get("finPaymentmethodId"));
    User user = OBDal.getInstance().get(User.class, bundle.getContext().getUser());
    try {
      OBQuery<OrderLine> salesOrderLine = OBDal.getInstance().createQuery(OrderLine.class,
          "where salesOrder=?", param);
      // create purchase order header
      VariablesSecureApp vars = new VariablesSecureApp(RequestContext.get().getRequest());
      Order purchaseOrder = OBProvider.getInstance().get(Order.class);
      purchaseOrder.setClient(klien);
      purchaseOrder.setOrganization(org);
      purchaseOrder.setSalesTransaction(false);
      purchaseOrder.setCreatedBy(user);
      purchaseOrder.setUpdatedBy(user);
      String docNo = Utility.getDocumentNo(bundle.getConnection(), vars, "181", "C_Order",
          docTrx.getId(), "0", true, true);
      pesan = docNo;
      purchaseOrder.setDocumentNo(docNo);
      purchaseOrder.setDocumentStatus("DR");
      purchaseOrder.setDocumentAction("CO");
      purchaseOrder.setProcessed(false);
      purchaseOrder.setProcessNow(false);
      purchaseOrder.setDocumentType(docType);
      purchaseOrder.setTransactionDocument(docTrx);
      purchaseOrder.setDelivered(false);
      purchaseOrder.setReinvoice(false);
      purchaseOrder.setPrint(false);
      purchaseOrder.setSelected(false);
      purchaseOrder.setOrderDate(orderDate);
      purchaseOrder.setScheduledDeliveryDate(promisedDate);
      purchaseOrder.setAccountingDate(salesOrder.getAccountingDate());
      purchaseOrder.setBusinessPartner(bp);
      purchaseOrder.setInvoiceAddress(lokasi);
      purchaseOrder.setPartnerAddress(lokasi);
      purchaseOrder.setPrintDiscount(false);
      purchaseOrder.setCurrency(salesOrder.getCurrency());
      purchaseOrder.setPaymentTerms(payTerm);
      purchaseOrder.setPaymentMethod(payMethod);
      purchaseOrder.setInvoiceTerms("I");
      purchaseOrder.setDeliveryTerms(salesOrder.getDeliveryTerms());
      purchaseOrder.setFreightCostRule(salesOrder.getFreightCostRule());
      purchaseOrder.setFreightAmount(salesOrder.getFreightAmount());
      purchaseOrder.setDeliveryMethod(salesOrder.getDeliveryMethod());
      purchaseOrder.setShippingCompany(salesOrder.getShippingCompany());
      purchaseOrder.setCharge(salesOrder.getCharge());
      purchaseOrder.setChargeAmount(salesOrder.getChargeAmount());
      purchaseOrder.setPriority(salesOrder.getPriority());
      purchaseOrder.setSummedLineAmount(salesOrder.getSummedLineAmount());
      purchaseOrder.setGrandTotalAmount(salesOrder.getGrandTotalAmount());
      purchaseOrder.setWarehouse(gudang);
      purchaseOrder.setPriceList(priceList);
      purchaseOrder.setPriceIncludesTax(false);
      purchaseOrder.setPosted("N");
      purchaseOrder.setCopyFrom(false);
      purchaseOrder.setSelfService(false);
      purchaseOrder.setGenerateTemplate(false);
      purchaseOrder.setCopyFromPO(false);
      purchaseOrder.setPaymentMethod(payMethod);
      purchaseOrder.setPickFromShipment(false);
      purchaseOrder.setReceiveMaterials(false);
      purchaseOrder.setCreateInvoice(false);
      purchaseOrder.setAddOrphanLine(false);
      purchaseOrder.setCalculatePromotions(false);
      purchaseOrder.setCreateOrder(false);
      purchaseOrder.setCreatePOLines(false);
      purchaseOrder.setCashVAT(false);
      purchaseOrder.setOezReviewso(false);
      OBDal.getInstance().save(purchaseOrder);
      // create purchase order lines
      for (OrderLine SOLine : salesOrderLine.list()) {
        OrderLine POLine = OBProvider.getInstance().get(OrderLine.class);
        POLine.setClient(purchaseOrder.getClient());
        POLine.setOrganization(purchaseOrder.getOrganization());
        POLine.setCreatedBy(user);
        POLine.setUpdatedBy(user);
        POLine.setSalesOrder(purchaseOrder);
        POLine.setLineNo(SOLine.getLineNo());
        POLine.setBusinessPartner(bp);
        POLine.setPartnerAddress(lokasi);
        POLine.setOrderDate(orderDate);
        POLine.setScheduledDeliveryDate(promisedDate);
        POLine.setProduct(SOLine.getProduct());
        POLine.setWarehouse(gudang);
        POLine.setDirectShipment(false);
        POLine.setUOM(SOLine.getUOM());
        BigDecimal qtyOrder = new BigDecimal(SOLine.getOrderedQuantity().doubleValue());
        POLine.setOrderedQuantity(qtyOrder);
        POLine.setReservedQuantity(SOLine.getReservedQuantity());
        POLine.setDeliveredQuantity(SOLine.getDeliveredQuantity());
        POLine.setInvoicedQuantity(SOLine.getInvoicedQuantity());
        POLine.setCurrency(SOLine.getCurrency());
        POLine.setListPrice(SOLine.getListPrice());
        POLine.setUnitPrice(SOLine.getUnitPrice());
        POLine.setPriceLimit(SOLine.getPriceLimit());
        POLine.setLineNetAmount(SOLine.getLineNetAmount());
        POLine.setDiscount(SOLine.getDiscount());
        POLine.setFreightAmount(SOLine.getFreightAmount());
        POLine.setChargeAmount(SOLine.getChargeAmount());
        POLine.setTax(SOLine.getTax());
        POLine.setDescriptionOnly(false);
        POLine.setStandardPrice(SOLine.getStandardPrice());
        POLine.setCancelPriceAdjustment(false);
        POLine.setEditLineAmount(false);
        POLine.setTaxableAmount(SOLine.getTaxableAmount());
        POLine.setGrossUnitPrice(SOLine.getGrossUnitPrice());
        POLine.setLineGrossAmount(SOLine.getLineGrossAmount());
        POLine.setGrossUnitPrice(SOLine.getGrossUnitPrice());
        POLine.setManageReservation(false);
        POLine.setManagePrereservation(false);
        POLine.setExplode(false);
        OBDal.getInstance().save(POLine);
      }
      // OBDal.getInstance().rollbackAndClose();
      OBDal.getInstance().commitAndClose();
      msg.setType("Success");
      msg.setTitle("Create Purchase Order");
      msg.setMessage("Purchase order has been created sucessfull with document number " + pesan
          + ".");
      bundle.setResult(msg);
    } catch (Exception e) {
      // TODO: handle exception
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
      msg.setType("Error");
      msg.setTitle("Create Purchase Order");
      msg.setMessage(e.getMessage());
      bundle.setResult(msg);
    }
  }

}
