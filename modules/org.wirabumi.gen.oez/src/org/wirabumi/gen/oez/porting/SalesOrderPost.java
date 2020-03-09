package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.List;
import java.sql.ResultSet;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.ClientInformation;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.OrgWarehouse;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.OrganizationType;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.order.OrderLineAccountingDimension;
import org.openbravo.model.common.order.OrderTax;
import org.openbravo.model.common.order.RejectReason;
import org.openbravo.model.common.plm.ApprovedVendor;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductUOM;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.financialmgmt.cashmgmt.BankStatement;
import org.openbravo.model.financialmgmt.cashmgmt.CashJournal;
import org.openbravo.model.financialmgmt.cashmgmt.CashJournalLine;
import org.openbravo.model.financialmgmt.payment.DebtPayment;
import org.openbravo.model.financialmgmt.payment.Settlement;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.procurement.POInvoiceMatch;
import org.openbravo.service.db.DalConnectionProvider;

public class SalesOrderPost{
	Logger log4j = Logger.getLogger(this.getClass());
	Order order = null;
	Date date = new Date();
	ConnectionProvider conn = new DalConnectionProvider();
	SalesOrderPostUtility Utility = new SalesOrderPostUtility();
	String msg = "";
	
	public void SalesOrder(Client client, User user,Organization organization, Order recordid, String docAction){
		order = recordid;
		try {
			boolean isSOTrx = order.isSalesTransaction();
			
			/**************************************************
			 * Untuk syntax dibawah ini kalo pake function postgresql tidak error
			 * jika pake function java ini error
			 * ini hanya berlaku pada Purchase Order
			 **********************************************/
			
			if(!isSOTrx){
				boolean cekAcctAmt = cekAcctAmt(order);
				// remark by asrofie
				/*
				if(cekAcctAmt){
					msg = Utility.Error("QuantitiesNotMatch");
					throw new OBException(msg);
				}*/
			}
			BusinessPartner businessPartner = order.getBusinessPartner();
			DocumentType docType = order.getDocumentType();
			DocumentType transDoc = order.getTransactionDocument();
			String invoiceRule = order.getInvoiceTerms();
			boolean isCasVat = order.isCashVAT();
			String docStatus = order.getDocumentStatus();

			/*----------------get Module DocaAction --------------------*/ 
			String v_moduledocation = Utility.get_moduledocaction(docAction, null);
			// revisi asrofie
			if(v_moduledocation==null){
				v_moduledocation="";
			}
			// --------------
			if(!v_moduledocation.equals("0")){
			    //docaction bukan bawaan openbravo, berarti dari document routing, maka docaction akan menjadi docstatus, dan return.
			    //document routing hanya menjegal docstatus tidak menjadi complete, tetapi dibelokkan dulu
				order.setDocumentStatus(docAction);
				order.setDocumentAction("CO");
				
				OBDal.getInstance().save(order);
				return;
			}else{
				String baru = Utility.get_moduledocaction("", order);;
				if(baru != null){
					v_moduledocation=baru;		
				}
				if(!v_moduledocation.equals("0")){
					//jika docstatus tidak bawaan openbravo, jika docaction=CO maka ubah docstatus=DR, 
					//jika docaction=RC/VO maka raise exception action not allowed
					if(docAction.equals("RC") || docAction.equals("VO") || docAction.equals("CL")){
						msg = "Action not allowed since document status not COMPLETE";
						throw new OBException(msg);
					}else{
						if(docAction.equals("CO")){
							order.setDocumentStatus("DR");
							OBDal.getInstance().save(order);
						}else{
							msg = "Action not allowed since document status is not compatible" ;
							throw new OBException(msg);
						}
					}
				}
			}
			
			//get DocSubTypeSO
			String DocSubTypeSO = docType.getSOSubType();
			String DocSubTypeSOTarget = transDoc.getSOSubType();
			if(DocSubTypeSOTarget == null){
				DocSubTypeSOTarget = "";
			}
			boolean v_isreturndoctype = transDoc.isReturn();
			Warehouse warehouse = order.getWarehouse();
			
			//Cek warehouse apakah mempunyai organisation
			OBCriteria<OrgWarehouse> orgWarehouse = OBDal.getInstance().createCriteria(OrgWarehouse.class);
			orgWarehouse.add(Restrictions.eq(OrgWarehouse.PROPERTY_ORGANIZATION, organization));
			orgWarehouse.add(Restrictions.eq(OrgWarehouse.PROPERTY_WAREHOUSE, warehouse));
			
			int countOrgWarehouse = orgWarehouse.list().size(); 
			
			if(countOrgWarehouse == 0 && isSOTrx){
				msg = Utility.Error("WrongWarehouse");
				throw new OBException(msg);
			}
			
			//cek organization WareHouse ada di tree
			if((Utility.ad_org_isinnaturaltree(warehouse.getOrganization(), organization, client))==false && isSOTrx==false){
				msg = Utility.Error("WrongWarehouse");
				throw new OBException(msg);
			}
			
			//get v_bpartner_blocked, v_orderBlocking, 
			boolean v_bpartner_blocked = true;
			boolean v_orderBlocking = true;
			if(!isSOTrx){
				v_bpartner_blocked = businessPartner.isVendorBlocking();
				v_orderBlocking = businessPartner.isPurchaseOrder();
			}else{
				v_bpartner_blocked = businessPartner.isCustomerBlocking();
				v_orderBlocking = businessPartner.isSalesOrder();				
			}
			
			if(docAction.equals("CO") && v_bpartner_blocked == true && v_orderBlocking == true && v_isreturndoctype == false){
				msg = Utility.Error("ThebusinessPartner");
				String msg2 = Utility.Error("BusinessPartnerBlocked");
				throw new OBException(msg+" "+businessPartner.getName()+" "+msg2);
			}
			
			//Cek Order Memiliki Line
			OBCriteria<OrderLine> orderLine = OBDal.getInstance().createCriteria(OrderLine.class);
			orderLine.add(Restrictions.eq(OrderLine.PROPERTY_SALESORDER, order));
			
			for(OrderLine orL : orderLine.list()){
				//cek Product Generic produk
				Product productID = orL.getProduct();
				if(productID.isGeneric()){
					msg = Utility.Error("CannotUseGenericProduct");
					throw new OBException(msg+":"+productID.getName());						
				}
				
				//cek org line header matched dengan org header
				BigDecimal orgMatch = Utility.ad_isorgincluded(orL.getOrganization(), organization, client);
				String sql = "as o ,OrderLine as oL where o = oL.salesOrder and o.id='"+ order.getId() +"' and "+ orgMatch +" = -1";
				OBQuery<Order> orderOrg = OBDal.getInstance().createQuery(Order.class, sql);
				
				if(orderOrg.list().size() > 0){
					msg = Utility.Error("NotCorrectOrgLines");
					throw new OBException(msg);
				}
			}
			
			if(docAction.equals("CO") || docAction.equals("PR")){
				//cek header punya line atau tidak
				if(orderLine.list().size() <= 0){
					msg = Utility.Error("OrderWithoutLines");
					throw new OBException(msg);
				}
				//check cas flag pada 
				OBCriteria<OrderTax> orderTax = OBDal.getInstance().createCriteria(OrderTax.class);
				orderTax.add(Restrictions.eq(OrderTax.PROPERTY_SALESORDER, order));
				
				int v_countCashVat = 0;
				for(OrderTax oT : orderTax.list()){
					TaxRate tax = oT.getTax();
					if(tax.isCashVAT() != isCasVat && tax.getRate() != new BigDecimal(0)  && tax.isWithholdingTax() == false){
						v_countCashVat = v_countCashVat + 1;
					}
				}
				if(v_countCashVat > 0){
					msg = Utility.Error("CashVATNotMatch");
					throw new OBException(msg);
				}	
			}
			
			if(docStatus.equals("WP") && !docAction.equals("CL")){
				msg = Utility.Error("WaitingPayment");
				throw new OBException(msg);
			}
			
			//cek SOSubTypeTarget <> payment 
			if(DocSubTypeSOTarget.equals("PR") && !(invoiceRule.equals("I"))){
				msg = Utility.Error("PrepayMustImmediate");
				throw new OBException(msg);
			}
			
			//cek docBaseType matceh to IsSotrx
			int v_count_docbasetype = Utility.cekDocBaseType(order, docType, organization, docType.getOrganization(), client);
			if(v_count_docbasetype == 0){
				msg = Utility.Error("NotCorrectOrgDoctypeOrder");
				throw new OBException(msg);
			}
			
			//Cek Org Header Ready to Use
			OBQuery<OrganizationType> orgType = OBDal.getInstance().createQuery(OrganizationType.class, 
					"as ot, Order as o, Organization as og where og.organizationType.id = ot.id " +
					"and og.id = o.organization.id " +
					"and o.id = '"+ order.getId() +"'");
			
			for(OrganizationType oT : orgType.list()){
				if(!organization.isReady()){
					msg = Utility.Error("OrgHeaderNotReady");
					throw new OBException(msg);
				}
				if(!oT.isTransactionsAllowed()){
					msg = Utility.Error("OrgHeaderNotTransAllowed");
					throw new OBException(msg);
				}
			}
			
			//cek organization line match dengan header
			int v_is_included = Utility.ad_org_chk_documents("Order", "OrderLine", order.getId(), "salesOrder");
			if(v_is_included == new BigDecimal(1).negate().intValue()){
				msg = Utility.Error("LinesAndHeaderDifferentLEorBU");
				throw new OBException(msg);	
			}

			//cek apakah business partner match dengan organization
			OBCriteria<ClientInformation> clientInfo = OBDal.getInstance().createCriteria(ClientInformation.class);
			clientInfo.add(Restrictions.eq(ClientInformation.PROPERTY_CHECKORDERORGANIZATION, true));
			
			if(clientInfo.list().size()>0){
				BigDecimal orgBusinesPartner = Utility.ad_isorgincluded(order.getOrganization(), 
						businessPartner.getOrganization(), businessPartner.getClient());
				
				OBQuery<BusinessPartner> bp = OBDal.getInstance().createQuery(BusinessPartner.class, 
						"as bp where EXISTS " +
						"(select 1 from Order as c where c.businessPartner=bp and c.id='"+order.getId()+"') " +
								"AND "+orgBusinesPartner+" = -1");
				if(bp.list().size()>0){
					msg = Utility.Error("NotCorrectOrgBpartnerOrder");
					throw new OBException(msg);	
					
				}
			}
			// -- revisi by asrofie
			if(DocSubTypeSO!=null){
				if(DocSubTypeSO.equals("RM") || v_isreturndoctype==true){
					/*------------- Return Material --------------*/
					for(OrderLine orL : orderLine.list()){
						if(orL.getOrderedQuantity().longValue() > 0 && orL.getOrderDiscount() == null){
							msg = Utility.Error("ReturnMaterialOrderType");
							throw new OBException(msg);
						}
					}	
				}	
			}
		} catch (Exception e) {
			throw new OBException(e.getMessage());
		}
	}
	
	private boolean cekAcctAmt(Order orderID){
		//membandingkan acctAmount <> LineNetAmt 
		OBCriteria<OrderLine> orderLine = OBDal.getInstance().createCriteria(OrderLine.class);
		orderLine.add(Restrictions.eq(OrderLine.PROPERTY_SALESORDER, orderID));
		
		for(OrderLine orL : orderLine.list()){
			//mengambil LineNetAmount
			BigDecimal lineNetAmount = orL.getLineNetAmount();
			
			//mengambil acctAmount
			BigDecimal acctAmount = new BigDecimal(0);
			OBCriteria<OrderLineAccountingDimension> orderAccDimension = 
					OBDal.getInstance().createCriteria(OrderLineAccountingDimension.class);
			orderAccDimension.add(Restrictions.eq(OrderLineAccountingDimension.PROPERTY_SALESORDERLINE, orL));
			
			for(OrderLineAccountingDimension acctDimensi : orderAccDimension.list()){
				acctAmount.add(acctDimensi.getAmount());
			}
			
			if(lineNetAmount.longValue() != acctAmount.longValue()){
				return true;
			}
		}
		return false;
	}
	
	public void Complete(Client client, Organization org, User user,
			Order orderID, String docAction, VariablesSecureApp vars) throws Exception{
		
		order = orderID;
		Date orderedDate = order.getOrderDate();
		String docStatus = orderID.getDocumentStatus();
		boolean isSOTrx = order.isSalesTransaction();
		DocumentType docType = order.getDocumentType();
		DocumentType transDoc = order.getTransactionDocument();
		String DocSubTypeSO = docType.getSOSubType();
		if(DocSubTypeSO == null){
			DocSubTypeSO = "";
		}
		
		//Docaction CO
		//update doctype based on c_doctypetarget_id
		if(docType != transDoc){
			if(docStatus.equals("DR") || docType.getId() == "0"){
				//update to target Document Type
				try {
					while(docType != transDoc){
						order.setDocumentType(transDoc);
						order.setUpdated(date);
						order.setUpdatedBy(user);
						OBDal.getInstance().save(order);
						docType = transDoc;
						DocSubTypeSO = docType.getSOSubType();
					}
				} catch (Exception e) {
					order.setDocumentNo(order.getDocumentNo()+".");
					OBDal.getInstance().save(order);
				}
			}else{
				if(DocSubTypeSO.equals("ON") || DocSubTypeSO.equals("OB") || docStatus.equals("IP")){
					try {
						while(docType != transDoc){
							order.setDocumentType(transDoc);
							order.setUpdated(date);
							order.setUpdatedBy(user);
							OBDal.getInstance().save(order);
							docType = transDoc;
						}
					} catch (Exception e) {
						order.setDocumentNo(order.getDocumentNo()+".");
						OBDal.getInstance().save(order);
					}
				}else{
					//kembali ke record awal
					order.setTransactionDocument(docType);
					OBDal.getInstance().save(order);
					msg = Utility.Error("CannotChangeDocType");
					throw new OBException(msg);
				}
			}
		}
		
		
		/*------------- BOM ----------------*/
		
		/**********************************************************
		 * select ol from OrderLine ol, Product p 
		 * where ol.product = p and ol.explode='N' 
		 * and p.billOfMaterials='Y' and p.stocked='N' 
		 * and ol.salesOrder.id='EBE841A61C1E4E57A5E56D712E9F9FBF' 
		 * order by ol.lineNo
		 * ******************************************************** */
		
		
		//Always check and (un) Reserve Inventory
		if(!docAction.equals("CL")){
			boolean directShipment = false;
			BigDecimal angka = new BigDecimal(0);
			BigDecimal orderQuantity =angka, reservedQuantity = angka, deliveredQuantity = angka, orderedQuantity = angka, qty = angka;
			BigDecimal v_QtySO = angka, v_QtyOrderSO = angka, v_QtyPO = angka, v_QtyOrderPO = angka;
			 
			String sql = "select l.warehouse, l.product, l.attributeSetValue, " +
					"l.directShipment, l.orderedQuantity, l.reservedQuantity, l.deliveredQuantity, " +
					"l.orderQuantity, l.scheduledDeliveryDate, l.uOM, l.orderUOM.id, l.id " +
					"from OrderLine l, Product p " +
					"where l.salesOrder='"+order.getId()+"' and l.product = p and p.stocked='Y' and p.productType='I'";
			Query q = OBDal.getInstance().getSession().createQuery(sql);
			ScrollableResults rs = q.scroll(ScrollMode.FORWARD_ONLY);
			
			while(rs.next()){
				String id = rs.getString(11);
				directShipment = rs.getBoolean(3);
				orderedQuantity = rs.getBigDecimal(4);
				reservedQuantity = rs.getBigDecimal(5);
				deliveredQuantity = rs.getBigDecimal(6);
				orderQuantity = rs.getBigDecimal(7);
				if(directShipment){
					qty = new BigDecimal(0).subtract(reservedQuantity).subtract(deliveredQuantity);
				}else{
					qty = orderedQuantity.subtract(reservedQuantity).subtract(deliveredQuantity);
				}
				if(qty.equals(new BigDecimal(0))){
					return;
				}
				Warehouse warehouse = (Warehouse) rs.get(0);
				Product product = (Product) rs.get(1);
				AttributeSetInstance attributeSetValueID = (AttributeSetInstance) rs.get(2);
				UOM uom = (UOM) rs.get(9);
				ProductUOM orderUOMID = (ProductUOM) rs.get(10);
				
				if(DocSubTypeSO == null){
					v_QtySO=angka;
					v_QtyOrderSO=null;
					v_QtyPO=deliveredQuantity.subtract(qty);
					v_QtyOrderPO=null;
					if(deliveredQuantity.equals(new BigDecimal(0))){
						v_QtyOrderPO = orderQuantity.negate();
					}else if(orderUOMID != null){
						v_QtyOrderPO = Utility.c_uom_convert(v_QtyPO, uom.getId(), orderUOMID.getId(), true);
						v_QtyOrderPO.negate();
					}
				}else{
					v_QtySO=reservedQuantity;
					v_QtySO.negate();
					if(reservedQuantity.equals(qty)){
						v_QtyOrderSO = orderQuantity;
					}else if(orderUOMID != null){
						v_QtyOrderSO = Utility.c_uom_convert(v_QtyPO, uom.getId(), orderUOMID.getId(), true);
					}
					v_QtyPO=angka;
					v_QtyOrderPO=null;
				}
				
				if(!(docStatus.equals("IP") || docAction.equals("CO")) && !((DocSubTypeSO == null)? "":DocSubTypeSO).equals("OB")){
					Utility.m_update_storage_pending(client, org, user, product, warehouse, attributeSetValueID, uom, orderUOMID, 
							v_QtySO, v_QtyOrderSO, v_QtyPO, v_QtyOrderPO);
				}
				if(DocSubTypeSO != null){
					OrderLine orL = OBDal.getInstance().get(OrderLine.class, id);
					orL.setReservedQuantity(orL.getReservedQuantity().add(v_QtySO));
					
					OBDal.getInstance().save(orL);
				}
			}
		}
		// revisi by asrofie
		if(DocSubTypeSO==null){
			DocSubTypeSO="";
		}
		// ------------------
		//realese Stock Reservation
		Utility.StockReservation(isSOTrx, order, msg ,user);
		
		if(DocSubTypeSO.equals("PR")){
			String sql = "SELECT p_invoice_id FROM C_Invoice_Create(NULL, '"+order.getId()+"')";
			try {
				PreparedStatement st = conn.getPreparedStatement(sql);
				ResultSet result = st.executeQuery();
				
				if(result.next()){
					//--------------Update Order----------
					order.setDocumentStatus("WP");
					order.setDocumentAction("--");
					order.setProcessed(true);
					order.setUpdated(date);
					order.setUpdatedBy(user);
					OBDal.getInstance().save(order);
					//-------------Create Invoice---------
					String invoiceID = result.getString(0);
					sql = "SELECT * FROM C_INVOICE_POST(NULL, '"+invoiceID+"')";
					st = conn.getPreparedStatement(sql);
					result = st.executeQuery();
					if(result.next()){
						return;
					}
				}else{
					msg = Utility.Error("PreInvoiceCreateFailed");
					throw new OBException(msg);
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				OBDal.getInstance().rollbackAndClose();
			}
		}
		if(DocSubTypeSO.equals("WI") || DocSubTypeSO.equals("WP") || DocSubTypeSO.equals("WR")){
			ConnectionProvider conn = new DalConnectionProvider();
			String sql = "SELECT p_inout_id FROM M_Inout_Create(NULL, '"+order.getId()+"', NULL, 'Y')";
			try {
				PreparedStatement st = conn.getPreparedStatement(sql);
				ResultSet result = st.executeQuery();
				if(result.next()){
					//---------Create Shipment----------
					String inoutId = result.getString(1);
					if(inoutId != null){
						ShipmentInOut inOut = OBDal.getInstance().get(ShipmentInOut.class, inoutId);	
					}
				}else{
					msg = Utility.Error("InOutCreateFailed");
					throw new OBException(msg);
				}
				if(DocSubTypeSO.equals("WI") || DocSubTypeSO.equals("WR")){
					//----------Create Invoice----------
					sql = "SELECT p_invoice_id FROM C_Invoice_Create(NULL, '"+order.getId()+"')";
					st = conn.getPreparedStatement(sql);
					result = st.executeQuery();
					if(result.next()){
						String invoiceID = result.getString(1);
						if(invoiceID != null){
							Invoice invoice = OBDal.getInstance().get(Invoice.class, invoiceID);	
						}
					}else{
						msg = Utility.Error("InvoiceCreateFailed");
						throw new OBException(msg);
					}
				}
				//---------- Update Order -----------------
				order.setDocumentStatus("CO");
				order.setDocumentAction("--");
				order.setDelivered(true);
				order.setReinvoice(true);
				order.setProcessed(true);
				OBDal.getInstance().save(order);
			} catch (Exception e) {
				// TODO: handle exception
				OBDal.getInstance().rollbackAndClose();
				throw new OBException(e.getMessage());
			}
		}
		
		//----------Update Order dengan Kriteria(Complete, Void, Close)------------
		if((docAction.equals("CO")||docAction.equals("CL")||docAction.equals("VO")) && !DocSubTypeSO.equals("OB")){
			order.setDocumentStatus("CO");
			order.setDocumentAction("--");
			order.setProcessed(true);
			OBDal.getInstance().save(order);
		}
		
		//----------Update Order "Under Evaluation" -------------
		if(docAction.equals("CO") && DocSubTypeSO.equals("OB")){
			order.setDocumentStatus("UE");
			order.setDocumentAction("--");
			order.setProcessed(true);
			OBDal.getInstance().save(order);
		}
		
		//------------ Create Debt Payment ------------------
		Utility.createDebtPayment(order, DocSubTypeSO, org, msg, orderedDate);
		
		OBDal.getInstance().commitAndClose();
	}
	
	public void Close(Order orderID, String docAction, Client client, Organization org, User user) throws Exception{
		/*--------- Edit Posted menjadi false agar bisa mengganti record menjadi Close -------------*/
		order = orderID;
		order.setProcessed(false);
		OBDal.getInstance().save(order);
		OBDal.getInstance().commitAndClose();
		OBDal.getInstance().getSession();
		
		BigDecimal angka = new BigDecimal(0);
		BigDecimal v_qtyOrdered = angka, lineNetAmount = angka, lineGrossAmount = angka, v_quantityOrder=angka;
		BigDecimal qtyInvoiced = angka, qtyDelivered = angka, qtyOrdered = angka, quantityOrder = angka,
				priceActual = angka, grossUnitPrice = angka;
		Product product = null; Warehouse warehouse = null; UOM uom = null; Currency currency = null;
		AttributeSetInstance attributeSetInstance = null; ProductUOM productUom = null;
		try {
			if(docAction.equals("CL")){
				if(order.isSalesTransaction()){
					OBCriteria<OrderLine> orderLineC = OBDal.getInstance().createCriteria(OrderLine.class);
					orderLineC.add(Restrictions.eq(OrderLine.PROPERTY_SALESORDER, order));
					
					for(OrderLine olC : orderLineC.list()){
						qtyDelivered = olC.getDeliveredQuantity();
						qtyInvoiced = olC.getInvoicedQuantity();
						if( qtyInvoiced.equals(new BigDecimal(0))){
							qtyOrdered = qtyDelivered;
						}else{
							if(qtyDelivered.equals(new BigDecimal(0))){
								qtyOrdered = qtyInvoiced;
							}else{
								if(qtyDelivered.longValue() < 0 && qtyInvoiced.longValue() < 0){
									if(qtyDelivered.longValue() < qtyInvoiced.longValue()){
										qtyOrdered = qtyDelivered;		
									}else{
										qtyOrdered = qtyInvoiced;
									}
								}
							}
						}
						String sql = "as ol where ol.salesOrder.id='"+order.getId()+"' and orderedQuantity <> "+qtyOrdered+"";
						OBQuery<OrderLine> orderLineQ = OBDal.getInstance().createQuery(OrderLine.class, sql);
						for(OrderLine ol : orderLineQ.list()){
							qtyOrdered = ol.getOrderedQuantity();
							if(ol.getOrderQuantity() != null){
								quantityOrder = ol.getOrderQuantity();
							}
							priceActual = ol.getUnitPrice();
							grossUnitPrice = ol.getGrossUnitPrice();
							lineNetAmount = ol.getLineNetAmount();
							product = ol.getProduct();
							warehouse = ol.getWarehouse();
							
							uom = ol.getUOM();
							productUom = ol.getOrderUOM();
							currency = ol.getCurrency();
							
							/*---------------- get Quantity Order --------------*/
							if(qtyDelivered.longValue() < 0){
								if(qtyDelivered.longValue() < qtyInvoiced.longValue()){
									v_qtyOrdered = qtyDelivered;
								}else{
									v_qtyOrdered = qtyInvoiced;
								}
							}else{
								if(qtyDelivered.longValue() > qtyInvoiced.longValue()){
									v_qtyOrdered = qtyDelivered;
								}else{
									v_qtyOrdered = qtyInvoiced;
								}
							}
							
							int stdPreci = currency.getStandardPrecision().intValue();
							/*------------ Round LineNetAmount -------------*/
							lineNetAmount = quantityOrder.multiply(priceActual);
							lineNetAmount = lineNetAmount.setScale(stdPreci, RoundingMode.FLOOR); 
							/*------------ Round LineNetGrossAmount -----------*/
							lineGrossAmount = quantityOrder.multiply(grossUnitPrice);
							lineGrossAmount = lineGrossAmount.setScale(stdPreci, RoundingMode.FLOOR);
							
							/*--------------- Call M_Update_Sotorage_Pending -----------------*/
							if(productUom == null){
								v_quantityOrder = quantityOrder;
							}else{
								UOM v_ProductUOM = productUom.getUOM();
								v_quantityOrder = Utility.c_uom_convert(v_qtyOrdered, uom.getId(), v_ProductUOM.getId(), true);
							}
							
							if(!qtyOrdered.equals(v_qtyOrdered)){
								BigDecimal v_QtySO = qtyOrdered.subtract(v_qtyOrdered).negate();
								BigDecimal v_QtyOrderSO = quantityOrder.subtract(v_quantityOrder).negate();
								
								Utility.m_update_storage_pending(client, org, user, product, warehouse, 
										attributeSetInstance, uom, 
										productUom, v_QtySO, v_QtyOrderSO, new BigDecimal(0), null);
							}
							
							/*-------- Update Orderline -----------*/
							if(productUom == null){
								v_quantityOrder = null;
							}
							ol.setOrderedQuantity(v_qtyOrdered);
							ol.setLineNetAmount(lineNetAmount);
							ol.setLineGrossAmount(lineGrossAmount);
							ol.setOrderQuantity(v_quantityOrder);
							ol.setUpdated(date);
							OBDal.getInstance().save(ol);
						}
					}
				}else{
					String sql = "as po join po.salesOrderLine as ol where ol.salesOrder.id='"+order.getId()+"'";
					OBQuery<POInvoiceMatch> invoiceMatch = OBDal.getInstance().createQuery(POInvoiceMatch.class, sql);
					/*--------- Get qtyDelivered, qtyInvoiceLIne M_MATCHPO ----------------*/
					for(POInvoiceMatch im : invoiceMatch.list()){
						if(im.getGoodsShipmentLine() == null){
							qtyDelivered = qtyDelivered.add(im.getQuantity());
						}
						if(im.getInvoiceLine() == null){
							qtyInvoiced = qtyInvoiced.add(im.getQuantity());
						}
					}
					
					if(qtyDelivered.equals(new BigDecimal(0))){
						qtyOrdered = qtyInvoiced;
					}else{
						if(qtyInvoiced.equals(new BigDecimal(0))){
							qtyOrdered = qtyDelivered;
						}else{
							if(qtyDelivered.longValue() < 0 && qtyInvoiced.longValue() < 0){
								if(qtyDelivered.longValue() < qtyInvoiced.longValue()){
									qtyOrdered = qtyDelivered;		
								}else{
									qtyOrdered = qtyInvoiced;
								}
							}else{
								if(qtyDelivered.longValue() > qtyInvoiced.longValue()){
									qtyOrdered = qtyDelivered;		
								}else{
									qtyOrdered = qtyInvoiced;
								}
							}
						}
					}
					/*------------- Get QtyOrdered, LineNetAmount, LineGrossUnit dari OrderLine --------------------*/
					sql = "as ol where ol.salesOrder.id='"+order.getId()+"' and orderedQuantity <> "+qtyOrdered+"";
					OBQuery<OrderLine> orderLineQ = OBDal.getInstance().createQuery(OrderLine.class, sql);
					for(OrderLine ol : orderLineQ.list()){
						qtyOrdered = ol.getOrderedQuantity();
						quantityOrder = ol.getOrderQuantity();
						priceActual = ol.getUnitPrice();
						grossUnitPrice = ol.getGrossUnitPrice();
						lineNetAmount = ol.getLineNetAmount();
						product = ol.getProduct();
						warehouse = ol.getWarehouse();
						attributeSetInstance = ol.getAttributeSetValue();
						uom = ol.getUOM();
						productUom = ol.getOrderUOM();
						currency = ol.getCurrency();
						
						/*---------------- get Quantity Order --------------*/
						if(qtyDelivered.longValue() < 0){
							if(qtyDelivered.longValue() < qtyInvoiced.longValue()){
								v_qtyOrdered = qtyDelivered;
							}else{
								v_qtyOrdered = qtyInvoiced;
							}
						}else{
							if(qtyDelivered.longValue() > qtyInvoiced.longValue()){
								v_qtyOrdered = qtyDelivered;
							}else{
								v_qtyOrdered = qtyInvoiced;
							}
						}

						if(quantityOrder != null){
							int stdPreci = currency.getStandardPrecision().intValue();
							/*------------ Round LineNetAmount -------------*/
							lineNetAmount = quantityOrder.multiply(priceActual);
							lineNetAmount = lineNetAmount.setScale(stdPreci, RoundingMode.FLOOR);
							/*------------ Round LineNetGrossAmount -----------*/
							lineGrossAmount = quantityOrder.multiply(grossUnitPrice);
							lineGrossAmount = lineGrossAmount.setScale(stdPreci, RoundingMode.FLOOR);
						}
						
						/*--------------- Call M_Update_Sotorage_Pending -----------------*/
						if(productUom == null){
							v_quantityOrder = quantityOrder;
						}else{
							UOM v_ProductUOM = productUom.getUOM();
							v_quantityOrder = Utility.c_uom_convert(v_qtyOrdered, uom.getId(), v_ProductUOM.getId(), true);
						}
						
						if(!qtyOrdered.equals(v_qtyOrdered)){
							BigDecimal v_QtyPO = qtyOrdered.subtract(v_qtyOrdered).negate();
							BigDecimal v_QtyOrderPO = null;
							if(quantityOrder != null){
								v_QtyOrderPO = quantityOrder.subtract(v_quantityOrder).negate();	
							}
							
							Utility.m_update_storage_pending(client, org, user, product, warehouse, 
									attributeSetInstance, uom, 
									productUom, new BigDecimal(0), null ,v_QtyPO, v_QtyOrderPO);
						}
						
						/*-------- Update Orderline -----------*/
						if(productUom == null){
							v_quantityOrder = null;
						}
						ol.setOrderedQuantity(v_qtyOrdered);
						ol.setLineNetAmount(lineNetAmount);
						ol.setLineGrossAmount(lineGrossAmount);
						ol.setOrderQuantity(v_quantityOrder);
						ol.setUpdated(date);
						OBDal.getInstance().save(ol);
					}
					
					boolean v_isreturndoctype = order.getTransactionDocument().isReturn();
					if(!order.isSalesTransaction() && !v_isreturndoctype){
						OBCriteria<OrderLine> orderLineC = OBDal.getInstance().createCriteria(OrderLine.class);
						orderLineC.add(Restrictions.eq(OrderLine.PROPERTY_SALESORDER, order));
						for(OrderLine ol : orderLineC.list()){
							BusinessPartner bp = order.getBusinessPartner();
							product = ol.getProduct();
							OBCriteria<ApprovedVendor> appVendor = OBDal.getInstance().createCriteria(ApprovedVendor.class);
							appVendor.add(Restrictions.eq(ApprovedVendor.PROPERTY_BUSINESSPARTNER, bp));
							appVendor.add(Restrictions.eq(ApprovedVendor.PROPERTY_PRODUCT, product));
							
							/*--------- Mencari ApprovedVendor yang Matched dengan Orderline -------------*/
							for(ApprovedVendor av : appVendor.list()){
								BigDecimal Isorgincluded = Utility.ad_isorgincluded(ol.getOrganization(), av.getOrganization(), ol.getClient());
								sql = "as av where av.id='"+av.getId()+"' and -1 <> "+Isorgincluded+"";
								OBQuery<ApprovedVendor> approvedVendor = OBDal.getInstance().createQuery(ApprovedVendor.class, sql);
								for(ApprovedVendor apv : approvedVendor.list()){
									apv.setLastPurchasePrice(ol.getUnitPrice());
									
									OBDal.getInstance().save(apv);
								}
							}
						}
					}
				}
				/*---------- mengembalikan posted menjadi true -------------------*/
				order.setProcessed(true);
				OBDal.getInstance().save(order);
				OBDal.getInstance().commitAndClose();
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new OBException(e.getMessage());
		}
	}
	
	public void Unlock(Order orderID, User user){
		order = orderID;
		
		order.setProcessNow(false);
		order.setDocumentAction("--");
		order.setUpdated(date);
		order.setUpdatedBy(user);
		OBDal.getInstance().save(order);
	}
	
	public void Void(Order orderID, User user){
		order = orderID;
		ProductUOM orderUOM = null;
		BigDecimal quantityOrder = new BigDecimal(0);
		
		OBCriteria<OrderLine> orderLine = OBDal.getInstance().createCriteria(OrderLine.class);
		orderLine.add(Restrictions.eq(OrderLine.PROPERTY_SALESORDER, order));
		orderLine.add(Restrictions.ne(OrderLine.PROPERTY_ORDEREDQUANTITY, new BigDecimal(0)));
		
		for(OrderLine orl : orderLine.list()){
			orderUOM = orl.getOrderUOM();
			if(orderUOM == null){
				quantityOrder = null;
			}
			
			orl.setOrderedQuantity(new BigDecimal(0));
			orl.setOrderQuantity(quantityOrder);
			orl.setLineNetAmount(new BigDecimal(0));
			orl.setUpdated(date);
			orl.setUpdatedBy(user);
			OBDal.getInstance().save(orl);
		}
	}

	public void Reject(Order orderID, User user){
		order = orderID;
		
		RejectReason rejectReason = order.getRejectReason();
		if(rejectReason == null){
			msg = Utility.Error("NoRejectReason");
			throw new OBException(msg);
		}
		
		/*---------- Update Inventory Reservation -----------*/
		OBCriteria<OrderLine> orderLine = OBDal.getInstance().createCriteria(OrderLine.class);
		orderLine.add(Restrictions.eq(OrderLine.PROPERTY_SALESORDER, order));
		for(OrderLine ol : orderLine.list()){
			ol.setReservedQuantity(new BigDecimal(0));
			ol.setUpdated(date);
			ol.setUpdatedBy(user);
			
			OBDal.getInstance().save(ol);
		}
		
		order.setDocumentStatus("CJ");
		order.setDocumentAction("--");
		order.setProcessed(true);
		order.setUpdated(date);
		order.setUpdatedBy(user);
		
		OBDal.getInstance().save(order);
		
		OBDal.getInstance().commitAndClose();
	}
	
	public void Reactive(Order orderID, String docAction, Client client, User user, Organization org) throws Exception{
		BigDecimal angka =  new BigDecimal(0);
		String processed = "";
		order = orderID;
		String docStatus = order.getDocumentStatus();
		DocumentType docType = order.getDocumentType();
		String DocSubTypeSO = docType.getSOSubType();
		if(DocSubTypeSO == null){
			DocSubTypeSO = "";
		}
		BigDecimal v_QtySO = angka, v_QtyOrderSO = angka, v_QtyPO = angka, v_QtyOrderPO = angka;
		
		if(docAction.equals("RE")){
			if(DocSubTypeSO.equals("WR") || DocSubTypeSO.equals("WI")){
				msg = Utility.Error("ActionNotSupported");
				throw new OBException(msg);						
			}
			int v_aux = 0;
			OBCriteria<DebtPayment> debtPayment = OBDal.getInstance().createCriteria(DebtPayment.class);
			debtPayment.add(Restrictions.eq(DebtPayment.PROPERTY_SALESORDER, order));
			
			Settlement settlementCancelled= null; 
			boolean cancelProcessed = false;
			boolean generateProcessed = false;
			boolean paymentComplete = false;
			boolean valid = false;
			String idDebt = "";
			
			for(DebtPayment dp : debtPayment.list()){
				settlementCancelled = dp.getSettlementCancelled();
				cancelProcessed = dp.isCancelProcessed();
				generateProcessed = dp.isGenerateProcessed();
				paymentComplete = dp.isPaymentComplete();
				valid = dp.isValid();
			}
			
			if(debtPayment.list().size()>0){
				String debtpaymentstatus = Utility.c_debt_payment_status(settlementCancelled, cancelProcessed, 
						generateProcessed, paymentComplete, valid);
				String sql = "select max(id), count(dp), dp.settlementCancelled.id, dp.cancelProcessed, dp.generateProcessed, " +
						"dp.paymentComplete, dp.valid from FinancialMgmtDebtPayment dp " +
						"where dp.salesOrder='"+order+"'" +
								"and "+debtpaymentstatus+" != 'P'";
				Query query = OBDal.getInstance().getSession().createQuery(sql);
				ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
				while(rs.next()){
					idDebt = rs.getString(0);
					v_aux = rs.getInteger(1);
				}
			}
			//Statement ini masih belum diajalankan karena masih belum ada kasusnya
			if(v_aux !=0){
				DebtPayment dp = OBDal.getInstance().get(DebtPayment.class, idDebt);
				if(dp.getBankStatementLine() != null){
					BankStatement bankState = dp.getBankStatementLine().getBankStatement();
					String name = bankState.getName();
					Date date = bankState.getTransactionDate();
					msg = Utility.Error("ManagedDebtPaymentOrderBank");
					throw new OBException(msg + " " + name + "From " + date);
				}
				if(dp.getCashJournalLine() != null){
					CashJournal cash = dp.getCashJournalLine().getCashJournal();
					String name = cash.getName();
					Date date = cash.getTransactionDate();
					msg = Utility.Error("ManagedDebtPaymentOrderCash");
					throw new OBException(msg+ " " + name + "From " + date);					
				}
				if(dp.isCancelProcessed() && dp.isPaymentComplete()){
					String documentNo = settlementCancelled.getDocumentNo();
					Date date = settlementCancelled.getTransactionDate();
					msg = Utility.Error("ManagedDebtPaymentOrderCancel");
					throw new OBException(msg + " " + documentNo + "From " + date);
				}
			}
			
			//cek DocSubTypeSO in(Pos Order, On Credit Order, Warehouse Order)
			if(DocSubTypeSO.equals("WI") || DocSubTypeSO.equals("WP") || DocSubTypeSO.equals("WR")){
				Utility.m_inout_cancel(order);
				if(!DocSubTypeSO.equals("WP")){
					Utility.c_invoice_cancel(order, conn);
				}
			}
			/*-------- Update Order ----------*/
			order.setDocumentStatus("IP");
			order.setDocumentAction("CO");
			order.setProcessed(false);
			order.setProcessNow(false);
			order.setUpdated(date);
			order.setUpdatedBy(user);
			
			OBDal.getInstance().save(order);
			
			/*--------- Update ShipmentInOutLine ---------*/
			String sql ="as iol " +
					"where salesOrderLine.id in " +
					"(select ol from OrderLine ol where orderDiscount != null and salesOrder.id='"+order.getId()+"') " +
					"and (select distinct io.documentStatus from MaterialMgmtShipmentInOutLine iol " +
					"join iol.shipmentReceipt io " +
					"join iol.salesOrderLine ol " +
					"where ol.orderDiscount != null and ol.salesOrder.id = '"+order.getId()+"') = 'VO'";
			OBQuery<ShipmentInOutLine> inOutLine = OBDal.getInstance().createQuery(ShipmentInOutLine.class, sql);
			
			for(ShipmentInOutLine iol : inOutLine.list()){
				iol.setSalesOrderLine(null);
				OBDal.getInstance().save(iol);
			}
			
			/*----------- Delete Orderline with Discount Not null --------------*/
			sql = "as ol where ol.salesOrder.id='"+order.getId()+"' and ol.orderDiscount != null";
			OBQuery<OrderLine> orderLine = OBDal.getInstance().createQuery(OrderLine.class, sql);
			
			for(OrderLine ol : orderLine.list()){
				OBDal.getInstance().remove(ol);
			}
			
			/*------------- Update Debt Payment -------------*/
			OBCriteria<DebtPayment> debtPay = OBDal.getInstance().createCriteria(DebtPayment.class);
			debtPay.add(Restrictions.eq(DebtPayment.PROPERTY_SALESORDER, order));
			debtPay.add(Restrictions.eq(DebtPayment.PROPERTY_ISAUTOMATICGENERATED, false));
			
			for(DebtPayment dp : debtPay.list()){
				dp.setValid(false);
				dp.setUpdated(date);
				dp.setUpdatedBy(user);
				
				OBDal.getInstance().save(dp);
			}
			
			/*--------------- Delete Debt Payment with Is Automatic Generated True --------------*/
			debtPay.add(Restrictions.eq(DebtPayment.PROPERTY_ISAUTOMATICGENERATED, true));
			for(DebtPayment dp : debtPay.list()){
				OBDal.getInstance().save(dp);
			}			
			
			/*---------------- Delete CashLine ---------------*/
			sql = "as cl " +
					"where payment in (select dp from FinancialMgmtDebtPayment dp where dp.salesOrder='"+order+"' " +
					"and COALESCE(isAutomaticGenerated, 'Y')='Y')";
			OBQuery<CashJournalLine> cashJournalLine = OBDal.getInstance().createQuery(CashJournalLine.class, sql);
			for(CashJournalLine cashL : cashJournalLine.list()){
				OBDal.getInstance().remove(cashL);
			}
			
			/*-------------- Undo Inventory Reservation --------------*/
			sql="as ol join ol.product p where p.stocked='Y' " +
					"and p.productType='I' and ol.salesOrder.id='"+order.getId()+"'";
//			orderLine.deleteQuery();
			orderLine = OBDal.getInstance().createQuery(OrderLine.class, sql);
			
			for(OrderLine ol : orderLine.list()){
				DocSubTypeSO = docType.getSOSubType();
				if(DocSubTypeSO == null){
					v_QtyOrderSO = null;
					v_QtyPO = ol.getDeliveredQuantity().subtract(ol.getOrderedQuantity());
					v_QtyOrderPO = null;
					if(ol.getDeliveredQuantity() == angka){
						v_QtyOrderPO = ol.getOrderQuantity().negate();
					}else if(ol.getOrderUOM() != null){
						v_QtyOrderPO = Utility.c_uom_convert(v_QtyPO, ol.getUOM().getId(), ol.getOrderUOM().getId(), true);
						v_QtyOrderPO.negate();
					}
				}else{
					v_QtySO = ol.getReservedQuantity().negate();
					if(ol.getReservedQuantity().equals(ol.getOrderedQuantity())){
						v_QtyOrderSO = ol.getOrderQuantity().negate();
					}else if(ol.getOrderUOM() != null){
						v_QtyOrderSO = Utility.c_uom_convert(v_QtySO, ol.getUOM().getId(), ol.getOrderUOM().getId(), true);
						v_QtyOrderSO.negate();
					}
					v_QtyPO = angka;
					v_QtyOrderPO = null;
				}
				if(!(docStatus.equals("IP") && docAction.equals("CO")) || !((DocSubTypeSO == null)? "":DocSubTypeSO).equals("OB")){
					Utility.m_update_storage_pending(client, org, user, ol.getProduct(), ol.getWarehouse(), 
							ol.getAttributeSetValue(), ol.getUOM(), ol.getOrderUOM(), 
							v_QtySO, v_QtyOrderSO, v_QtyPO, v_QtyOrderPO);
				}
			}
			
			/*------------- Update Orderline -------------*/
//			orderLine.deleteQuery();
			sql="as ol where ol.id in " +
					"(select ol.id from OrderLine ol where ol.salesOrder.id='"+order.getId()+"')";
			orderLine = OBDal.getInstance().createQuery(OrderLine.class, sql);
			
			for(OrderLine ol : orderLine.list()){
				ol.setReservedQuantity(new BigDecimal(0));
				ol.setUpdated(date);
				ol.setUpdatedBy(user);
				OBDal.getInstance().save(ol);
			}
			
			/*------------ Manage Stock Reservation ---------------*/
			OBCriteria<Preference> preference = OBDal.getInstance().createCriteria(Preference.class);
			preference.add(Restrictions.eq(Preference.PROPERTY_PROPERTY, "StockReservations"));
			
			if(preference.list().size() > 1){
				//v_dummy := AD_GET_PREFERENCE_VALUE('StockReservations', 'Y', v_client_id, v_org_id, NULL, NULL, NULL);
			}else if(preference.list().size() == 1){
				/*----------- Update ResStatus --------------*/
				for(OrderLine ol : orderLine.list()){
					ol.setReservationStatus(null);
					OBDal.getInstance().save(ol);
				}
				order.setReservationStatus(null);
				OBDal.getInstance().save(order);
			}
			
			sql = "select max(cl) from FinancialMgmtJournalLine cl where cl.salesOrder.id='"+order.getId()+"'";
			Query query = OBDal.getInstance().getSession().createQuery(sql);
			ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
			
			while(rs.next()){
				CashJournalLine cashLine = (CashJournalLine) rs.get()[0];
				if(cashLine != null){
					CashJournal cash = cashLine.getCashJournal();
					processed = cash.getProcessed();
					if(processed.equals("N")){
						OBDal.getInstance().remove(cashLine);
					}else{
						msg = Utility.Error("Ordercahslineprocessed");
						throw new OBException(msg + cash.getName() + "From :" + cash.getTransactionDate() +
								" Line :"+ cashLine.getLineNo());
					}
				}
			}
			
			/*-------------- Update Order --------------*/
			order.setDocumentAction("CO");
			order.setProcessNow(false);
			order.setUpdated(date);
			order.setUpdatedBy(user);
			OBDal.getInstance().save(order);
		}
		OBDal.getInstance().commitAndClose();
	}
}
