package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
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
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.project.Project;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CreateCustomerReimbursement extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    final OBError msg = new OBError();
    String dateFormat = bundle.getContext().getJavaDateFormat();
	SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
    String pesan = "";
    boolean isFromInvoice;
    Project headerProject= null;

    try {
      // determine which document come from
      String documentID=(String) bundle.getParams().get("C_Invoice_ID");
      if (documentID!=null && !documentID.isEmpty()){
    	  isFromInvoice=true;
      } else{
    	  documentID=(String) bundle.getParams().get("M_InOut_ID");
    	  if (documentID!=null && !documentID.isEmpty()){
    		  isFromInvoice=false;
    	  } else {
    		  throw new OBException("The document neither come from purchase invoice nor goods receipt");
    	  }
      }
    	
      //get baseobobject which generate reimbursement
      ShipmentInOut inout = null;
      Invoice inv = null;
      if (isFromInvoice){
    	  inv=OBDal.getInstance().get(Invoice.class, bundle.getParams().get("C_Invoice_ID"));
    	  inout = inv.getInvoiceLineList().get(0).getGoodsShipmentLine().getShipmentReceipt();
    	  
      } else {
    	  inout=OBDal.getInstance().get(ShipmentInOut.class, bundle.getParams().get("M_InOut_ID"));
    	  headerProject=inout.getProject();
      }
      
      //get param
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
      
      // make sure currency in selected price list match with currency in purchase invoice
      if (isFromInvoice) {
    	  //make sure currency is consistent between invoice and param
    	  if (inv.getPriceList().getCurrency().getId() != price.getCurrency().getId()) {
    		//error handling for different currency, invoice currency != currency from param
    		  OBDal.getInstance().rollbackAndClose();
  	          msg.setType("Error");
  	          msg.setTitle("Create Sales Order");
  	          msg.setMessage("Currency difference between purchase invoice and price list parameter.");
  	          bundle.setResult(msg);
  	          return;  
    	  }
      } else {
    	//make sure currency is consistent between order and param
    	  Order purchaseOrder = inout.getSalesOrder();
    	  if ((purchaseOrder != null) && (purchaseOrder.getPriceList().getCurrency().getId() != price.getCurrency().getId())) {
      		//error handling for different currency, PO currency != currency from param
      		  OBDal.getInstance().rollbackAndClose();
      		  msg.setType("Error");
	          msg.setTitle("Create Sales Order");
	          msg.setMessage("Currency difference between purchase order and price list parameter.");
	          bundle.setResult(msg);
	          return;      
      	  }
      }
      
      //create vars
      HttpServletRequest request = RequestContext.get().getRequest();
      VariablesSecureApp vars = new VariablesSecureApp(request);

      //create document type for generated reimbursement
      DocumentType dT = OBDal.getInstance().get(DocumentType.class, "0");
      
      //crete document no
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
      invoice.setProject(headerProject);
      invoice.setTaxDate(invoiceDate);
      invoice.setOezIsreimbursement(true);
      OBDal.getInstance().save(invoice);
      OBDal.getInstance().flush();
      
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
      shipment.setProject(headerProject);
      shipment.setLogistic(false);
      shipment.setOezIsreimbursement(true);
      shipment.setOezReimbursefrom(inout);
      OBDal.getInstance().save(shipment);
      OBDal.getInstance().flush();
      
      // get purchase invoice line to be copied as reimbursement
      OBQuery<InvoiceLine> IL = null;
      OBQuery<ShipmentInOutLine> IOL = null;
      if (isFromInvoice){
    	  IL=OBDal.getInstance().createQuery(InvoiceLine.class,
    	          "where invoice.id='" + inv.getId() + "'");
      } else {
    	  IOL=OBDal.getInstance().createQuery(ShipmentInOutLine.class,
    	          "where shipmentReceipt.id='" + inout.getId() + "'");
      }
      
      if(isFromInvoice){
    	  for (InvoiceLine iLine : IL.list()) {
    		//create reimbursement goods shipment lines
  	        ShipmentInOutLine shipmentLine = OBProvider.getInstance().get(ShipmentInOutLine.class);
  	        shipmentLine.setOezReimbursefrom(iLine.getGoodsShipmentLine());
  	        shipmentLine.setOrganization(org);
  	        shipmentLine.setLineNo(iLine.getLineNo());
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
  	        OBDal.getInstance().flush();
  	        
  	        //create reimbursement sales invoice line
  	        InvoiceLine invoiceLine = OBProvider.getInstance().get(InvoiceLine.class);
  	        invoiceLine.setOrganization(org);
  	        invoiceLine.setLineNo(iLine.getLineNo());
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
  	        OBDal.getInstance().flush();
    	  }
    	  
      } else {
    	  for(ShipmentInOutLine IOLine : IOL.list()){
    		//create reimbursement goods shipment lines
    		ShipmentInOutLine shipmentLine = OBProvider.getInstance().get(ShipmentInOutLine.class);
    		shipmentLine.setOezReimbursefrom(IOLine);
    		shipmentLine.setOrganization(org);
  	        shipmentLine.setLineNo(IOLine.getLineNo());
  	        shipmentLine.setShipmentReceipt(shipment);
  	        shipmentLine.setStorageBin(IOLine.getStorageBin());
	      	shipmentLine.setAttributeSetValue(IOLine.getAttributeSetValue());
  	        shipmentLine.setProduct(IOLine.getProduct());
  	        shipmentLine.setUOM(IOLine.getUOM());
  	        shipmentLine.setMovementQuantity(IOLine.getMovementQuantity());
  	        shipmentLine.setProject(IOLine.getProject());
  	        OBDal.getInstance().save(shipmentLine);
  	        OBDal.getInstance().flush();
  	        
  	        //create reimbursement sales invoice line
  	        OBQuery<InvoiceLine> invoiceLineList = OBDal.getInstance().createQuery(InvoiceLine.class,
    	          "where goodsShipmentLine.id='" + IOLine.getId() + "'");
  	        
  	        boolean isPurchaseInvoiceLineExists;
  	        InvoiceLine purchaseInvoiceLine=null;
  	        OrderLine purchaseOrderLine=null;
  	        if (invoiceLineList.list().size()>0){
  	        	isPurchaseInvoiceLineExists=true;
  	        	purchaseInvoiceLine = invoiceLineList.list().get(0);
  	        } else {
  	        	isPurchaseInvoiceLineExists=false;
  	        	purchaseOrderLine = IOLine.getSalesOrderLine();
  	        	if (purchaseOrderLine==null){
  	        		throw new OBException("Goods receipt line "+IOLine.getLineNo()+" neither linked to purchase invoice nor purchase order");
  	        	}
  	        }
  	        InvoiceLine invoiceLine = OBProvider.getInstance().get(InvoiceLine.class);
  	        invoiceLine.setOrganization(org);
  	        invoiceLine.setLineNo(IOLine.getLineNo());
  	        invoiceLine.setInvoice(invoice);
  	        invoiceLine.setGoodsShipmentLine(shipmentLine);
  	        invoiceLine.setFinancialInvoiceLine(false);
  	        invoiceLine.setProduct(IOLine.getProduct());
  	        invoiceLine.setInvoicedQuantity(IOLine.getMovementQuantity());
  	        invoiceLine.setUOM(IOLine.getUOM());
  	        if (isPurchaseInvoiceLineExists){
  	        	invoiceLine.setListPrice(purchaseInvoiceLine.getListPrice());
  	  	        invoiceLine.setUnitPrice(purchaseInvoiceLine.getUnitPrice());
  	  	        invoiceLine.setPriceLimit(purchaseInvoiceLine.getPriceLimit());
  	  	        invoiceLine.setLineNetAmount(purchaseInvoiceLine.getLineNetAmount());
  	  	        invoiceLine.setTax(purchaseInvoiceLine.getTax());
  	  	        invoiceLine.setTaxableAmount(purchaseInvoiceLine.getTaxableAmount());
  	            invoiceLine.setGrossUnitPrice(purchaseInvoiceLine.getGrossUnitPrice());
  	            
  	        } else {
  	        	invoiceLine.setListPrice(purchaseOrderLine.getListPrice());
  	  	        invoiceLine.setUnitPrice(purchaseOrderLine.getUnitPrice());
  	  	        invoiceLine.setPriceLimit(purchaseOrderLine.getPriceLimit());
  	  	        invoiceLine.setLineNetAmount(purchaseOrderLine.getLineNetAmount());
  	  	        invoiceLine.setTax(purchaseOrderLine.getTax());
  	  	        invoiceLine.setTaxableAmount(purchaseOrderLine.getTaxableAmount());
	            invoiceLine.setGrossUnitPrice(purchaseOrderLine.getGrossUnitPrice());
	            invoiceLine.setChargeAmount(purchaseOrderLine.getChargeAmount());
    	        invoiceLine.setStandardPrice(purchaseOrderLine.getStandardPrice());
    	        invoiceLine.setBaseGrossUnitPrice(purchaseOrderLine.getBaseGrossUnitPrice());
  	        }
  	        
  	        invoiceLine.setEditLineAmount(false);
  	        invoiceLine.setBusinessPartner(bp);
  	        invoiceLine.setDescriptionOnly(false);
  	        invoiceLine.setExplode(false);
  	        OBDal.getInstance().save(invoiceLine);
  	        OBDal.getInstance().flush();
    	        
    	  }
      }
      
      //set Reimburse to Customer button to Y
      if (isFromInvoice){
    	  inv.setOezCreateSalesorder(true);
      } else {
    	  inout.setOezReimburse(true);
      }
      
      //SO created successfully
      OBDal.getInstance().commitAndClose();
      msg.setType("Success");
      msg.setTitle("Customer Reimbursment");
      msg.setMessage("Customer reimbursement has been created sucessfull." + pesan
          + ".");
      bundle.setResult(msg);
      
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