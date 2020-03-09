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
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceLine;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CreateCustomerReimbursement extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    final OBError msg = new OBError();
    String dateFormat = bundle.getContext().getJavaDateFormat();
	SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
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
      DocumentType docTrxInvoice = OBDal.getInstance().get(DocumentType.class,
          bundle.getParams().get("cDoctypeinvoiceId"));
      DocumentType docTrxShipment = OBDal.getInstance().get(DocumentType.class,
              bundle.getParams().get("cDoctypeshipmentId"));
      Date invoiceDate = sdf.parse(bundle.getParams().get("dateordered").toString());
      PaymentTerm payTerm = OBDal.getInstance().get(PaymentTerm.class,
          bundle.getParams().get("cPaymenttermId"));
      FIN_PaymentMethod payMethod = OBDal.getInstance().get(FIN_PaymentMethod.class,
          bundle.getParams().get("finPaymentmethodId"));
      Warehouse gudang = OBDal.getInstance().get(Warehouse.class,
          bundle.getParams().get("mWarehouseId"));
      User user = OBDal.getInstance().get(User.class, bundle.getContext().getUser());

      // get purchase invoice line to be copied as reimbursement
      OBQuery<InvoiceLine> IL = OBDal.getInstance().createQuery(InvoiceLine.class,
          "where invoice.id='" + inv.getId() + "'");
      
      // make sure currency in selected price list match with currency in purchase invoice
      if (inv.getPriceList().getCurrency().getId() == price.getCurrency().getId()) {
        HttpServletRequest request = RequestContext.get().getRequest();
        VariablesSecureApp vars = new VariablesSecureApp(request);

        //get document type for "New"
        DocumentType dT = OBDal.getInstance().get(DocumentType.class, "0");
        
        //get document no
        String docNoInvoice = Utility.getDocumentNo(bundle.getConnection(), vars, "167", "C_Invoice",
            docTrxInvoice.getId(), "0", true, true);
        pesan += "Sales Invoice No: "+docNoInvoice;
        String docNoShipment = Utility.getDocumentNo(bundle.getConnection(), vars, "169", "M_InOut",
                docTrxShipment.getId(), "0", true, true);
        pesan += ". Goods Shipment No: "+docNoShipment;
        
        // create reimbursement invoice header
        Invoice invoice = OBProvider.getInstance().get(Invoice.class);
        invoice.setClient(klien);
        invoice.setOrganization(org);
        invoice.setCreatedBy(user);
        invoice.setUpdatedBy(user);
        invoice.setSalesTransaction(true);
        invoice.setDocumentNo(docNoInvoice);
        invoice.setDocumentStatus("DR");
        invoice.setDocumentAction("CO");
        invoice.setDocumentType(dT);
        invoice.setTransactionDocument(docTrxInvoice);
        invoice.setPrint(false);
        invoice.setPrintDiscount(false);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setAccountingDate(invoiceDate);
        invoice.setBusinessPartner(bp);
        invoice.setPartnerAddress(loc);
        invoice.setCurrency(price.getCurrency());
        invoice.setFormOfPayment("P");
        invoice.setPaymentTerms(payTerm);
        invoice.setChargeAmount(new BigDecimal(0));
        invoice.setSummedLineAmount(new BigDecimal(0));
        invoice.setGrandTotalAmount(new BigDecimal(0));
        invoice.setPriceList(price);
        invoice.setUserContact(user);
        invoice.setCopyFrom(false);
        invoice.setPaymentMethod(payMethod);
        invoice.setCalculatePromotions(false);
        invoice.setProject(inv.getProject());
        invoice.setTaxDate(invoiceDate);
        OBDal.getInstance().save(invoice);
        
        // create reimbursement shipment header
        ShipmentInOut shipment = OBProvider.getInstance().get(ShipmentInOut.class);
        shipment.setOrganization(org);
        shipment.setSalesTransaction(true);
        shipment.setDocumentNo(docNoShipment);
        shipment.setDocumentAction("CO");
        shipment.setDocumentStatus("DR");
        shipment.setProcessNow(false);
        shipment.setProcessed(false);
        shipment.setDocumentType(docTrxShipment);
        shipment.setPrint(false);
        shipment.setMovementType("C-");
        shipment.setMovementDate(invoiceDate);
        shipment.setAccountingDate(invoiceDate);
        shipment.setBusinessPartner(bp);
        shipment.setPartnerAddress(loc);
        shipment.setWarehouse(gudang);
        shipment.setDeliveryTerms("A");
        shipment.setFreightCostRule("I");
        shipment.setDeliveryMethod("P");
        shipment.setPriority("5");
        shipment.setProject(inv.getProject());
        shipment.setLogistic(false);
        OBDal.getInstance().save(shipment);
        OBDal.getInstance().commitAndClose();
        
        // create reimbursement invoice and shipment lines
        Long lineNo=new Long(10);
        for (InvoiceLine iLine : IL.list()) {
          
          //create reimbursement goods shipment lines
          ShipmentInOutLine shipmentLine = OBProvider.getInstance().get(ShipmentInOutLine.class);
          shipmentLine.setOrganization(org);
          shipmentLine.setLineNo(lineNo);
          shipmentLine.setShipmentReceipt(shipment);
          if (iLine.getGoodsShipmentLine()!=null){
        	  shipmentLine.setStorageBin(iLine.getGoodsShipmentLine().getStorageBin());
        	  shipmentLine.setAttributeSetValue(iLine.getGoodsShipmentLine().getAttributeSetValue());
          } else {
        	  OBCriteria<Locator> locator = OBDal.getInstance().createCriteria(Locator.class);
        	  locator.add(Restrictions.eq(Locator.PROPERTY_WAREHOUSE, gudang));
        	  shipmentLine.setStorageBin(locator.list().get(0));
          }
          shipmentLine.setProduct(iLine.getProduct());
          shipmentLine.setUOM(iLine.getUOM());
          shipmentLine.setMovementQuantity(iLine.getInvoicedQuantity());
          shipmentLine.setProject(iLine.getProject());
          OBDal.getInstance().save(shipmentLine);
          OBDal.getInstance().commitAndClose();
          
          //create reimbursement sales invoice line
          InvoiceLine invoiceLine = OBProvider.getInstance().get(InvoiceLine.class);
          invoiceLine.setOrganization(org);
          invoiceLine.setLineNo(lineNo);
          invoiceLine.setInvoice(invoice);
          invoiceLine.setGoodsShipmentLine(shipmentLine);
          invoiceLine.setFinancialInvoiceLine(false);
          invoiceLine.setProduct(iLine.getProduct());
          invoiceLine.setInvoicedQuantity(iLine.getInvoicedQuantity());
          invoiceLine.setUOM(iLine.getUOM());
          invoiceLine.setListPrice(iLine.getListPrice());
          invoiceLine.setUnitPrice(iLine.getUnitPrice());
          invoiceLine.setPriceLimit(iLine.getPriceLimit());
          invoiceLine.setLineNetAmount(iLine.getLineNetAmount());
          invoiceLine.setTax(iLine.getTax());
          invoiceLine.setEditLineAmount(false);
          invoiceLine.setTaxableAmount(iLine.getTaxableAmount());
          invoiceLine.setGrossUnitPrice(iLine.getGrossUnitPrice());
          invoiceLine.setBusinessPartner(bp);
          invoiceLine.setChargeAmount(iLine.getChargeAmount());
          invoiceLine.setDescriptionOnly(false);
          invoiceLine.setStandardPrice(iLine.getStandardPrice());
          invoiceLine.setBaseGrossUnitPrice(iLine.getBaseGrossUnitPrice());
          invoiceLine.setExplode(false);
          OBDal.getInstance().save(invoiceLine);
          
        }
        
        //SO created successfully
        OBDal.getInstance().commitAndClose();
        msg.setType("Success");
        msg.setTitle("Customer Reimbursment");
        msg.setMessage("Customer reimbursement has been created sucessfull." + pesan
            + ".");
        bundle.setResult(msg);
      } else {
    	//error handling for different currency
        OBDal.getInstance().rollbackAndClose();
        msg.setType("Error");
        msg.setTitle("Create Sales Order");
        msg.setMessage("Currency difference between invoice and price list parameter.");
        bundle.setResult(msg);
        return;
      }
    } catch (Exception e) {
      //error handling for unknown exception
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
      msg.setType("Error");
      msg.setTitle("Create Sales Order");
      msg.setMessage(e.getMessage());
      bundle.setResult(msg);
    }

  }

}

