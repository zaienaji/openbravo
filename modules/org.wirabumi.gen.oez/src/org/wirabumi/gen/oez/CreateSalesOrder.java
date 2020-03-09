package org.wirabumi.gen.oez;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

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
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceLine;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CreateSalesOrder extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    // TODO Auto-generated method stub
    final OBError msg = new OBError();
    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
    String pesan = "";

    try {
      // get parameter
      Invoice inv = OBDal.getInstance().get(Invoice.class, bundle.getParams().get("C_Invoice_ID"));
      Client klien = OBDal.getInstance().get(Client.class, bundle.getContext().getClient());
      Organization org = OBDal.getInstance().get(Organization.class,
          bundle.getParams().get("adOrgId"));
      BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class,
          bundle.getParams().get("cBpartnerId"));
      OBCriteria<Location> LOC = OBDal.getInstance().createCriteria(Location.class);
      LOC.add(Restrictions.eq(Location.PROPERTY_BUSINESSPARTNER, bp));
      Location loc = LOC.list().get(0);
      PriceList price = OBDal.getInstance().get(PriceList.class,
          bundle.getParams().get("mPricelistId"));
      DocumentType docTrx = OBDal.getInstance().get(DocumentType.class,
          bundle.getParams().get("cDoctypeId"));
      Date orderDate = sdf.parse(bundle.getParams().get("dateordered").toString());
      Date promiseDate = sdf.parse(bundle.getParams().get("datepromised").toString());
      PaymentTerm payTerm = OBDal.getInstance().get(PaymentTerm.class,
          bundle.getParams().get("cPaymenttermId"));
      FIN_PaymentMethod payMethod = OBDal.getInstance().get(FIN_PaymentMethod.class,
          bundle.getParams().get("finPaymentmethodId"));
      Warehouse gudang = OBDal.getInstance().get(Warehouse.class,
          bundle.getParams().get("mWarehouseId"));
      User user = OBDal.getInstance().get(User.class, bundle.getContext().getUser());

      // get line
      OBQuery<InvoiceLine> IL = OBDal.getInstance().createQuery(InvoiceLine.class,
          "where invoice.id='" + inv.getId() + "'");

      // create order
      Order ord = OBProvider.getInstance().get(Order.class);
      // cek currency
      if (inv.getPriceList().getCurrency().getId() == price.getCurrency().getId()) {
        HttpServletRequest request = RequestContext.get().getRequest();
        VariablesSecureApp vars = new VariablesSecureApp(request);

        DocumentType dT = OBDal.getInstance().get(DocumentType.class, "0");
        String docNo = Utility.getDocumentNo(bundle.getConnection(), vars, "143", "C_Order",
            docTrx.getId(), "0", true, true);
        pesan += docNo;
        // System.out.println("Doc No: " + docNo);
        // save order header
        ord.setClient(klien);
        ord.setOrganization(org);
        ord.setCreatedBy(user);
        ord.setUpdatedBy(user);
        ord.setSalesTransaction(true);
        ord.setDocumentNo(docNo);
        ord.setDocumentStatus("DR");
        ord.setDocumentAction("CO");
        ord.setDocumentType(dT);
        ord.setTransactionDocument(docTrx);
        ord.setDelivered(false);
        ord.setReinvoice(false);
        ord.setPrint(false);
        ord.setSelected(false);
        ord.setOrderDate(orderDate);
        ord.setScheduledDeliveryDate(promiseDate);
        ord.setAccountingDate(promiseDate);
        ord.setBusinessPartner(bp);
        ord.setInvoiceAddress(loc);
        ord.setPartnerAddress(loc);
        ord.setPrintDiscount(false);
        ord.setCurrency(price.getCurrency());
        ord.setFormOfPayment("P");
        ord.setPaymentTerms(payTerm);
        ord.setInvoiceTerms("D");
        ord.setDeliveryTerms("A");
        ord.setFreightCostRule("I");
        ord.setFreightAmount(new BigDecimal(0));
        ord.setDeliveryMethod("P");
        ord.setChargeAmount(new BigDecimal(0));
        ord.setPriority("5");
        ord.setSummedLineAmount(new BigDecimal(0));
        ord.setGrandTotalAmount(new BigDecimal(0));
        ord.setWarehouse(gudang);
        ord.setPriceList(price);
        ord.setUserContact(user);
        ord.setCopyFrom(false);
        ord.setGenerateTemplate(false);
        ord.setCopyFromPO(false);
        ord.setPaymentMethod(payMethod);
        ord.setPickFromShipment(false);
        ord.setReceiveMaterials(false);
        ord.setCreateInvoice(false);
        ord.setAddOrphanLine(false);
        ord.setCalculatePromotions(false);
        ord.setCreateOrder(false);
        OBDal.getInstance().save(ord);
        // System.out.println("Order : " + ord);
        // save lines
        for (InvoiceLine iLine : IL.list()) {
          OrderLine oline = OBProvider.getInstance().get(OrderLine.class);
          oline.setOrganization(org);
          oline.setCreatedBy(user);
          oline.setUpdatedBy(user);
          oline.setLineNo(Long.parseLong("10"));
          oline.setSalesOrder(ord);
          oline.setBusinessPartner(bp);
          oline.setPartnerAddress(loc);
          oline.setOrderDate(orderDate);
          oline.setScheduledDeliveryDate(promiseDate);
          oline.setProduct(iLine.getProduct());
          oline.setWarehouse(gudang);
          oline.setDirectShipment(false);
          oline.setUOM(iLine.getUOM());
          oline.setOrderedQuantity(iLine.getInvoicedQuantity());
          oline.setReservedQuantity(new BigDecimal(0));
          oline.setDeliveredQuantity(new BigDecimal(0));
          oline.setInvoicedQuantity(new BigDecimal(0));
          oline.setCurrency(ord.getCurrency());
          oline.setListPrice(iLine.getListPrice());
          oline.setUnitPrice(iLine.getUnitPrice());
          oline.setPriceLimit(iLine.getPriceLimit());
          oline.setLineNetAmount(iLine.getLineNetAmount());
          oline.setDiscount(new BigDecimal(0));
          oline.setFreightAmount(new BigDecimal(0));
          oline.setChargeAmount(iLine.getChargeAmount());
          oline.setTax(iLine.getTax());
          oline.setDescriptionOnly(false);
          oline.setStandardPrice(iLine.getStandardPrice());
          oline.setCancelPriceAdjustment(false);
          oline.setEditLineAmount(false);
          oline.setTaxableAmount(iLine.getTaxableAmount());
          oline.setGrossUnitPrice(iLine.getGrossUnitPrice());
          oline.setLineGrossAmount(new BigDecimal(0));
          oline.setBaseGrossUnitPrice(iLine.getBaseGrossUnitPrice());
          oline.setManageReservation(false);
          oline.setManagePrereservation(false);
          oline.setExplode(false);
          OBDal.getInstance().save(oline);
          // System.out.println("Order Line : " + oline);
        }
        OBDal.getInstance().commitAndClose();
        // OBDal.getInstance().rollbackAndClose();
        msg.setType("Success");
        msg.setTitle("Create Sales Order");
        msg.setMessage("Sales order has been created sucessfull with document number " + pesan
            + ".");
        bundle.setResult(msg);
      } else {
        OBDal.getInstance().rollbackAndClose();
        msg.setType("Error");
        msg.setTitle("Create Sales Order");
        msg.setMessage("Currency difference between invoice and price list parameter.");
        bundle.setResult(msg);
        return;
      }
    } catch (Exception e) {
      // TODO: handle exception
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
      msg.setType("Error");
      msg.setTitle("Create Sales Order");
      msg.setMessage(e.getMessage());
      bundle.setResult(msg);
    }

  }

}
