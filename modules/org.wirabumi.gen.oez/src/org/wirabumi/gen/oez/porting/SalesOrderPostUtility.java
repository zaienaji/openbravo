package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.ClientInformation;
import org.openbravo.model.ad.ui.Message;
import org.openbravo.model.ad.utility.Tree;
import org.openbravo.model.ad.utility.TreeNode;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.OrganizationType;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductUOM;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.common.uom.UOMConversion;
import org.openbravo.model.financialmgmt.cashmgmt.CashBook;
import org.openbravo.model.financialmgmt.cashmgmt.CashJournal;
import org.openbravo.model.financialmgmt.cashmgmt.CashJournalLine;
import org.openbravo.model.financialmgmt.payment.DebtPayment;
import org.openbravo.model.financialmgmt.payment.DebtPaymentV;
import org.openbravo.model.financialmgmt.payment.Settlement;
import org.openbravo.model.materialmgmt.onhandquantity.Reservation;
import org.openbravo.model.materialmgmt.onhandquantity.ReservationStock;
import org.openbravo.model.materialmgmt.onhandquantity.StoragePending;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.procurement.POInvoiceMatch;
import org.openbravo.service.db.DalConnectionProvider;

public class SalesOrderPostUtility {
	
	public boolean ad_org_isinnaturaltree(Organization orgid, Organization orgparent ,Client client){
		BigDecimal param = new BigDecimal(1);
		
		BigDecimal orgInclude = ad_isorgincluded(orgid, orgparent, client);
		BigDecimal orgIncludeParent = ad_isorgincluded(orgparent, orgid, client);
		if((orgInclude.longValue() != param.negate().longValue()) || (orgIncludeParent.longValue() != param.negate().longValue())){
			return true;
		}else{
			return false;
		}
	}
	
	public BigDecimal ad_isorgincluded(Organization orgid, Organization orgparent ,Client client){
		BigDecimal hasil = new BigDecimal(1); 
		BigDecimal level = new BigDecimal(1); 
		try {
			OBCriteria<ClientInformation> clientInfo = OBDal.getInstance().createCriteria(ClientInformation.class);
			Tree treeOrg = null;
			for(ClientInformation ci : clientInfo.list()){
				treeOrg = ci.getPrimaryTreeOrganization();
			}
			
			String v_parent = orgid.getId();
			String v_node = "";
			while(v_parent != null){
				OBCriteria<TreeNode> treeNode = OBDal.getInstance().createCriteria(TreeNode.class);
				treeNode.add(Restrictions.eq(TreeNode.PROPERTY_TREE, treeOrg));
				treeNode.add(Restrictions.eq(TreeNode.PROPERTY_NODE, v_parent));
				
				for(TreeNode tN : treeNode.list()){
					v_parent = tN.getReportSet();
					v_node = tN.getNode();
				}
				if(v_node.equals(orgparent.getId())){
					return level;
				}
				level = level.add(new BigDecimal(1));
			}
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return hasil.negate();	
	}
	
	public int cekDocBaseType(Order orderID, DocumentType docTypeID,
			Organization orgOrder, Organization orgDoc, Client clientOrder){
		int hasil = 0;
		
		
		BigDecimal isOrgInclude = ad_isorgincluded(orgOrder, orgDoc, clientOrder);
		
		String sql = "as c, DocumentType as dt where  dt.documentCategory in('SOO','POO') " +
				"and dt.salesTransaction=c.salesTransaction and c.transactionDocument=dt and "+ isOrgInclude +" <> -1 " +
				"and c.id='"+ orderID.getId() +"'";
		
		OBQuery<Order> order = OBDal.getInstance().createQuery(Order.class, sql);
		
		hasil = order.list().size();
		return hasil;
	}
	
	public int ad_org_chk_documents(String p_header_table , String p_lines_table, String p_document_id, 
			String p_lines_column_id){
		int hasil = 0;
		BigDecimal angka = new BigDecimal(1);
		try {
			String v_org_header_id = ad_get_doc_le_bu(p_header_table, p_document_id, null);
			
			String sql = "Select DISTINCT(ol.organization.id) from "+p_header_table+" as o, "+p_lines_table+" as ol " +
					"where o = ol."+p_lines_column_id+" " +
					"and ol.organization<>'"+v_org_header_id+"' " +
					"and ol."+p_lines_column_id+".id='"+p_document_id+"'";
			
			Query query = OBDal.getInstance().getSession().createQuery(sql);
			ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
			while(rs.next()){
				boolean s = true;
				String orgID = rs.getString(0);
				String v_org_line_id = "";
				OBQuery<Organization> organization = OBDal.getInstance().createQuery(Organization.class, 
						"as o, OrganizationType as ot where o.organizationType = ot and o.id='"+orgID+"'");
				for(Organization org : organization.list()){
					OrganizationType orgType = org.getOrganizationType();
					boolean v_isbusinessunit = orgType.isBusinessUnit();
					boolean v_islegalentity = orgType.isLegalEntity();
					while(!v_isbusinessunit && !v_islegalentity){
						String sqlquery = "select hh.reportSet, ot.businessUnit, ot.legalEntity from OrganizationType "+ 
								"as ot, Organization as o,  ADTreeNode as pp, ADTreeNode as hh " +
								"where pp.node = hh.reportSet " +
								"and hh.tree = pp.tree " +
								"and pp.node = o.id " +
								"and hh.node='"+org.getId()+"' " +
								"and o.organizationType = ot " +
								"and o.ready ='Y' " +
								"and EXISTS (select 1 from ADTree at where at.typeArea='OO' and hh.tree = at and hh.client=at.client)";
						Query q = OBDal.getInstance().getSession().createQuery(sqlquery);
						ScrollableResults result = q.scroll(ScrollMode.FORWARD_ONLY);
						while(result.next()){
							v_org_line_id = result.getString(0); 
							v_isbusinessunit = result.getBoolean(1);
							v_islegalentity = result.getBoolean(2);
						}
					}
					if(!v_org_line_id.equals(v_org_header_id)){
						hasil = angka.negate().intValue();
						return hasil;
					}
				}
			}
		} catch (Exception e) {
			throw new OBException(e.getMessage());
		}
		return hasil;
	}
	
	public String ad_get_doc_le_bu(String p_header_table, String p_document_id, String p_type){
		String hasil = "";
		
		//get paramaeter
		try {
			String v_org_header_id = "";
			boolean v_isbusinessunit = false;
			boolean v_islegalentity = false;
			
			String sql = "Select og.id, ot.businessUnit, ot.legalEntity from "+ p_header_table +" as o, Organization og, OrganizationType ot " +
					"where o.id='"+p_document_id+"' and og.organizationType = ot and o.organization = og";
			Query query = OBDal.getInstance().getSession().createQuery(sql);
			ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
			
			while(rs.next()){
				v_org_header_id = rs.getString(0);
				v_isbusinessunit = rs.getBoolean(1);
				v_islegalentity = rs.getBoolean(2);
			}
			String typeBU = "";
			String typeLE = "";
			
			if(p_type == null){
				typeBU = "BU";
				typeLE = "LE";
			}
			
			if((v_isbusinessunit && typeBU.equals("BU")) || (v_islegalentity && typeLE.equals("LE"))){
				hasil = v_org_header_id;
				return hasil;
			}
			
			hasil = ad_get_org_le_bu(v_org_header_id, p_type);	
		} catch (Exception e) {
			e.printStackTrace();		
		}
		return hasil;
	}
	
	private String ad_get_org_le_bu(String p_org_id, String p_type){
		String hasil = p_org_id;
		boolean v_islegalentity = false;
		boolean v_isbusinessunit = false;
		
		String sql = "as o " +
				"join o.organizationType as ot " +
				"where o.id='"+ p_org_id +"'";
		OBQuery<Organization> organization = OBDal.getInstance().createQuery(Organization.class, sql);
		
		for(Organization org : organization.list()){
			OrganizationType orgType = org.getOrganizationType();
			v_islegalentity = orgType.isLegalEntity();
			v_isbusinessunit = orgType.isBusinessUnit();
		}
		
		try {
			if(p_type==null){
				while(!v_islegalentity && !v_isbusinessunit){
					String sqlquery = "select hh.reportSet, ot.businessUnit, ot.legalEntity from OrganizationType "+ 
							"as ot, Organization as o,  ADTreeNode as pp, ADTreeNode as hh " +
							"where pp.node = hh.reportSet " +
							"and hh.tree = pp.tree " +
							"and pp.node = o.id " +
							"and hh.node='"+hasil+"' " +
							"and o.organizationType = ot " +
							"and o.ready ='Y' " +
							"and EXISTS (select 1 from ADTree at where at.typeArea='OO' and hh.tree = at and hh.client=at.client)";
					Query q = OBDal.getInstance().getSession().createQuery(sqlquery);
					ScrollableResults result = q.scroll(ScrollMode.FORWARD_ONLY);
					while(result.next()){
						hasil = result.getString(0); 
						v_isbusinessunit = result.getBoolean(1);
						v_islegalentity = result.getBoolean(2);
					}
				}
			}else if(p_type.equals("LE")){
				while(!v_islegalentity){
					String sqlquery = "select hh.reportSet, ot.businessUnit, ot.legalEntity from OrganizationType "+ 
							"as ot, Organization as o,  ADTreeNode as pp, ADTreeNode as hh " +
							"where pp.node = hh.reportSet " +
							"and hh.tree = pp.tree " +
							"and pp.node = o.id " +
							"and hh.node='"+hasil+"' " +
							"and o.organizationType = ot " +
							"and o.ready ='Y' " +
							"and EXISTS (select 1 from ADTree at where at.typeArea='OO' and hh.tree = at and hh.client=at.client)";
					Query q = OBDal.getInstance().getSession().createQuery(sqlquery);
					ScrollableResults result = q.scroll(ScrollMode.FORWARD_ONLY);
					while(result.next()){
						hasil = result.getString(0); 
						v_isbusinessunit = result.getBoolean(1);
						v_islegalentity = result.getBoolean(2);
					}
				}				
			}else if(p_type.equals("BU")){
				while(!hasil.equals("0") && !v_isbusinessunit){
					String sqlquery = "select hh.reportSet, ot.businessUnit, ot.legalEntity from OrganizationType "+ 
							"as ot, Organization as o,  ADTreeNode as pp, ADTreeNode as hh " +
							"where pp.node = hh.reportSet " +
							"and hh.tree = pp.tree " +
							"and pp.node = o.id " +
							"and hh.node='"+hasil+"' " +
							"and o.organizationType = ot " +
							"and o.ready ='Y' " +
							"and EXISTS (select 1 from ADTree at where at.typeArea='OO' and hh.tree = at and hh.client=at.client)";
					Query q = OBDal.getInstance().getSession().createQuery(sqlquery);
					ScrollableResults result = q.scroll(ScrollMode.FORWARD_ONLY);
					while(result.next()){
						hasil = result.getString(0); 
						v_isbusinessunit = result.getBoolean(1);
						v_islegalentity = result.getBoolean(2);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hasil;
	}
	
	public String get_moduledocaction(String DocAction, Order orderID){
		String hasil = null;
		String sql = "";
		
		if(orderID == null){
			sql = "select l.module.id from ADList as l where l.reference.id = 'FF80818130217A35013021A672400035' " +
					"and l.searchKey='"+DocAction+"'";
		}else{
			sql = "select al.module.id from ADList al, Order i " +
					"where al.searchKey=i.documentStatus " +
					"and al.reference.id = 'FF80818130217A35013021A672400035' " +
					"and i.id='"+orderID.getId()+"'";
		}
		
		Query query = OBDal.getInstance().getSession().createQuery(sql);
		ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
		
		while(rs.next()){
			hasil = rs.getString(0);
		}
		
		return hasil;
	}
	
	public String c_debt_payment_status(Settlement settlementCancelled, boolean p_cancel_processed, 
			boolean p_generate_processed, boolean p_ispaid, boolean p_isvalid){
		String hasil = "";
		
		if(!p_isvalid){
			hasil = "I";
		}else if(p_cancel_processed && !p_ispaid){
			hasil = "C";
		}else if(p_cancel_processed || (settlementCancelled != null && p_generate_processed && p_ispaid)){
			hasil = "A";
		}else{
			hasil = "P";
		}
		
		return hasil;
	}
	
	public BigDecimal c_uom_convert(BigDecimal p_qty, String p_uomfrom_id, String p_uomto_id, boolean p_stdprecision) throws Exception{
		BigDecimal hasil = new BigDecimal(0);
		BigDecimal v_rate = new BigDecimal(0);
		try {
			if(p_uomfrom_id.equals(p_uomto_id) || p_uomfrom_id == null || 
					p_uomto_id == null || p_qty == null || p_qty == new BigDecimal(0)){
				return p_qty;
			}
			
			OBCriteria<UOMConversion> uomConversion = OBDal.getInstance().createCriteria(UOMConversion.class);
			uomConversion.add(Restrictions.eq(UOMConversion.PROPERTY_UOM, p_uomfrom_id));
			uomConversion.add(Restrictions.eq(UOMConversion.PROPERTY_TOUOM, p_uomto_id));
			
			for(UOMConversion uc : uomConversion.list()){
				v_rate = uc.getMultipleRateBy();
				hasil = v_rate.multiply(p_qty);
			}
			
			if(hasil == null){
				for(UOMConversion uc : uomConversion.list()){
					v_rate = uc.getMultipleRateBy();
					hasil = v_rate.multiply(p_qty);
				}
			}
			if(hasil != null){
				UOM uom = OBDal.getInstance().get(UOM.class, p_uomto_id);
				
				if(p_stdprecision){
					int StdPrecision = uom.getStandardPrecision().intValue();
					hasil = hasil.setScale(StdPrecision);
				}else{
					int CostingPrecision = uom.getCostingPrecision().intValue();
					hasil = hasil.setScale(CostingPrecision);
				}
			}	
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hasil;
	}
	public void m_update_storage_pending(Client client, Organization org,
			User user, Product productID, Warehouse warehouseID,
			AttributeSetInstance attributeSetValueID, UOM uOMID, ProductUOM productUOM,
			BigDecimal v_QtySO, BigDecimal v_QtyOrderSO, BigDecimal v_QtyPO,
			BigDecimal v_QtyOrderPO) {
		try {
			String Attributeset = "";
			String orderUomID = "";
			long v_count = 0;
			
			if(attributeSetValueID == null){
				Attributeset="0";
				attributeSetValueID = OBDal.getInstance().get(AttributeSetInstance.class, Attributeset);
			}
			if(productUOM == null){
				orderUomID = "-1";
			}else{
				orderUomID = productUOM.getId();
			}
			
			String sql = "as sp where sp.product.id='"+productID.getId()+"' and sp.warehouse.id='"+warehouseID.getId()+"' " +
					"and sp.uOM.id='"+uOMID.getId()+"'" +
							"and COALESCE(sp.orderUOM.id, '-1')='"+orderUomID+"' " +
									"and COALESCE(sp.attributeSetValue.id, '0') = '"+attributeSetValueID.getId()+"'";
			OBQuery<StoragePending>  storagePending = OBDal.getInstance().createQuery(StoragePending.class, sql);
			
			v_count = storagePending.list().size();
			
			if(productUOM != null){
				if(v_QtyOrderSO == null){
					v_QtyOrderSO=new BigDecimal(0);
				}
				if(v_QtyOrderPO == null){
					v_QtyOrderPO = new BigDecimal(0);
				}
			}
			if(v_count==0){
				if(v_QtySO == null){
					v_QtySO = new BigDecimal(0);
				}
				if(v_QtyPO == null){
					v_QtyPO = new BigDecimal(0);
				}
				if(v_QtyOrderSO==null){
					v_QtyOrderSO = new BigDecimal(0);
				}
				StoragePending sp = OBProvider.getInstance().get(StoragePending.class);
				sp.setProduct(productID);
				sp.setWarehouse(warehouseID);
				sp.setAttributeSetValue(attributeSetValueID);
				sp.setOrderUOM(productUOM);
				sp.setUOM(uOMID);
				sp.setReservedQuantity(v_QtySO);
				sp.setReservedQuantityOrder(v_QtyOrderSO);
				sp.setOrderedQuantity(v_QtyPO);
				sp.setOrderedQuantityOrder(v_QtyOrderPO);
				
				OBDal.getInstance().save(sp);
			}else{
				if(v_QtySO == null){
					v_QtySO = new BigDecimal(0);
				}
				if(v_QtyPO == null){
					v_QtyPO = new BigDecimal(0);
				}
				
				for(StoragePending sp : storagePending.list()){
					OBContext.setAdminMode();
					BigDecimal ReservedQuantityOrder = sp.getReservedQuantityOrder();
					BigDecimal OrderedQuantityOrder = sp.getReservedQuantityOrder();
					OBContext.restorePreviousMode();
					
					if(ReservedQuantityOrder==null && v_QtyOrderPO==null){
						sp.setReservedQuantityOrder(null);
					}else{
						sp.setReservedQuantityOrder(ReservedQuantityOrder.add(v_QtyOrderPO));
					}
					if(OrderedQuantityOrder==null && v_QtyOrderPO==null){
						sp.setOrderedQuantityOrder(null);
					}else{
						sp.setOrderedQuantityOrder(OrderedQuantityOrder.add(v_QtyOrderPO));
					}
					
					sp.setOrderedQuantity(sp.getOrderedQuantity().add(v_QtyPO));
					sp.setClient(client);
					sp.setOrganization(org);
					sp.setUpdated(new Date());
					sp.setUpdatedBy(user);
					
					OBDal.getInstance().save(sp);
					
				}
			}
		} catch (Exception e) {
			OBDal.getInstance().rollbackAndClose();
			throw new OBException(e.getMessage());
		}

	}
	
	public void CREATE_RESERVE_FROM_SOL(OrderLine orderline , boolean process_reserve , String msg){
		Order order = orderline.getSalesOrder();
		boolean isSotrx = order.isSalesTransaction();
		BigDecimal qtyOrdered = orderline.getOrderedQuantity();
		BigDecimal qtyDelivered = orderline.getDeliveredQuantity();
		AttributeSetInstance attributeSetvalue = orderline.getAttributeSetValue();
		Product product = orderline.getProduct();
		UOM uom = orderline.getUOM();
		
		if(!isSotrx){
			msg = Error("cannotReservePurchaseOrder");
			throw new OBException(msg);
		}
		if(qtyOrdered.longValue() < 0){
			msg = Error("cannotReserveNegativeOrders");
			throw new OBException(msg);			
		}
		if(qtyOrdered.subtract(qtyDelivered).longValue() <= 0){
			msg = Error("cannotReserveDeliveredSalesOrderLine");
			throw new OBException(msg);
		}
		
		Reservation reservation = OBProvider.getInstance().get(Reservation.class);
		reservation.setSalesOrderLine(orderline);
		reservation.setProduct(product);
		reservation.setUOM(uom);
		reservation.setQuantity(qtyOrdered);
		reservation.setReservedQty(new BigDecimal(0));
		reservation.setReleased(qtyDelivered);
		reservation.setRESStatus("DR");
		reservation.setRESProcess("CO");
		reservation.setAttributeSetValue(attributeSetvalue);
		
		OBDal.getInstance().save(reservation);
		
		
	}
	
	public void StockReservation(boolean isSOTrx, Order order, String msg, User user) throws Exception{
		BigDecimal v_qtyaux = null;
		BigDecimal reservedqty = new BigDecimal(0);
		BigDecimal v_pendingtounreserve = new BigDecimal(0);
		BigDecimal v_reservedqty = new BigDecimal(0);
		BigDecimal v_quantity = new BigDecimal(0);
		BigDecimal qtyordered = new BigDecimal(0);
		String v_res_status = "";
		
		OBCriteria<Preference> preference = OBDal.getInstance().createCriteria(Preference.class);
		preference.add(Restrictions.eq(Preference.PROPERTY_PROPERTY, "StockReservations"));
		if(preference.list().size()>0){
			if(preference.list().size()>1){
				// membuat function
//				v_dummy := AD_GET_PREFERENCE_VALUE('StockReservations', 'Y', v_client_id, v_org_id, NULL, NULL, NULL);
			}
			if(isSOTrx){
				String sql = "as r " +
						"right join r.salesOrderLine as ol " +
						"join ol.product as p " +
						"where ol.salesOrder.id='"+order.getId()+"' " +
						"and ((ol.orderedQuantity > 0 and p.stocked='Y' and p.productType='I' ) or ( r is not null ))";
				OBQuery<Reservation> reservation = OBDal.getInstance().createQuery(Reservation.class, sql);
				if(reservation.list().size() > 0){
					return;
				}
				for(Reservation rs : reservation.list()){
					OrderLine orl = rs.getSalesOrderLine();
					qtyordered = orl.getOrderedQuantity();
					
					orl.setReservationStatus("NR");
//					OBDal.getInstance().save(orl);
					
					OBCriteria<Reservation> reserv = OBDal.getInstance().createCriteria(Reservation.class);
					reserv.add(Restrictions.eq(Reservation.PROPERTY_SALESORDERLINE, orl));
					
					Reservation reservID = null;
					
					for(Reservation rt : reserv.list()){
						reservID = rt;
						break;
					}
					
					if(reserv.list().size()>1){
						msg = Error("SOLineWithMoreThanOneOpenReservation");
						throw new OBException(msg);
					}else if(reserv.list().size()==1){
						OBQuery<ReservationStock> reservationStock = OBDal.getInstance().createQuery(ReservationStock.class, 
								"as rs right join rs.reservation r where r.id='"+reservID.getId()+"'");
						BigDecimal v_allocated = new BigDecimal(0);
						//SUM
						for(ReservationStock rsStock : reservationStock.list()){
							BigDecimal Released = rsStock.getReleased();
							BigDecimal quantity = rsStock.getQuantity();
							BigDecimal allocated = new BigDecimal(0);
							if(Released==null){
								Released = new BigDecimal(0);
							}
							if(rsStock.isAllocated()){
								allocated = quantity.subtract(Released);
							}
							v_allocated = v_allocated.add(allocated);
							if(v_allocated == null){
								v_allocated = new BigDecimal(0);
							}
						}
						for(ReservationStock rsStock : reservationStock.list()){
							reservID = rsStock.getReservation();
							v_reservedqty = reservID.getReservedQty();
							v_quantity = reservID.getQuantity();
							v_res_status = reservID.getRESStatus();
							if(v_quantity != qtyordered){
								if(qtyordered.longValue() < v_allocated.longValue()){
									msg = Error("ThereIsMoreAllocatedQtyThanOrdered");
									throw new OBException(msg);
								}
								if(qtyordered.longValue() < reservID.getReleased().longValue()){
									msg = Error("CannotOrderLessThanReleasedQty");
									throw new OBException(msg);
								}
								if(qtyordered.longValue() < v_reservedqty.longValue()){
									v_pendingtounreserve = reservID.getReservedQty().subtract(qtyordered);
									

									v_pendingtounreserve = reservID.getReservedQty().subtract(qtyordered);
									String sqlQuery = "select rs, quantity - COALESCE(released, 0) " +
											"from MaterialMgmtReservationStock rs " +
											"where rs.reservation='"+reservID.getId()+"' " +
											"ORDER BY COALESCE(released, 0), quantity - COALESCE(released, 0)";
									Query q = OBDal.getInstance().getSession().createQuery(sqlQuery);
									ScrollableResults result = q.scroll(ScrollMode.FORWARD_ONLY);
									
									while(result.next()){
										reservID = (Reservation) result.get()[0];
										reservedqty = result.getBigDecimal(1);
									}
									
									// update Stock Reservation Stock
									if(v_pendingtounreserve.longValue() > reservedqty.longValue()){
										v_qtyaux = reservedqty;
									}else{
										v_qtyaux = v_pendingtounreserve;
									}
									
									rsStock.setQuantity(v_quantity.subtract(v_qtyaux));
									rsStock.setUpdated(new Date());
									rsStock.setUpdatedBy(user);
									OBDal.getInstance().save(rsStock);
									
									
									v_reservedqty = v_reservedqty.subtract(v_qtyaux);
									v_pendingtounreserve = v_pendingtounreserve.subtract(v_qtyaux);
									
									if(v_pendingtounreserve.equals(new BigDecimal(0))){
										break;
									}
									if(v_pendingtounreserve.longValue() > 0){
										msg = Error("CouldNotUnreserveNeededQty");
										throw new OBException(msg);
									}
									
				                    //Delete reservation lines with quantity zero.
									
								}	
								reservID.setQuantity(qtyordered);
								reservID.setUpdated(new Date());
								reservID.setUpdatedBy(user);
								OBDal.getInstance().save(reservID);
							}
							
							if(v_res_status != "DR"){
								String res_status = "PR";
								if(qtyordered.equals(v_reservedqty)){
									res_status= "CR";
								}
								orl.setReservationStatus(res_status);
								OBDal.getInstance().save(orl);
							}
						}
					}else if(orl.getCreateReservation().equals("CRP")){
						CREATE_RESERVE_FROM_SOL(orl, true, msg);
					}else if(orl.getCreateReservation().equals("CR")){
						CREATE_RESERVE_FROM_SOL(orl, false, msg);
					}
				}
				OBQuery<OrderLine> orderline=OBDal.getInstance().createQuery(OrderLine.class, "as ol " +
						"join ol.product as p " +
						"where ol.salesOrder.id='"+order.getId()+"' " +
								"and ol.orderedQuantity>0 and p.stocked='Y' and p.productType='I'");
				int v_linecount=0, v_creservedcount=0, v_preservedcount=0;
				String ResStatus = "";
				
				v_linecount = orderline.list().size();
				for(OrderLine orl : orderline.list()){
					ResStatus = orl.getReservationStatus();
					if(ResStatus == null){
						ResStatus = "";		
					}
					if(ResStatus.equals("CR")){
						v_creservedcount = 1;
					}
					if(ResStatus.equals("PR")){
						v_preservedcount = 1;
					}
				}
				if(v_linecount == v_creservedcount){
					ResStatus = "CR";
				}
				if(v_creservedcount + v_preservedcount > 0){
					ResStatus = "PR";
				}
				if(ResStatus != ""){
					order.setReservationStatus(ResStatus);
					OBDal.getInstance().save(order);	
				}
			}
		}
	}
	
	public void createDebtPayment(Order order, String DocSubTypeSO, Organization org, String msg, Date date){
		BigDecimal totalCash = new BigDecimal(0), grandtotal = new BigDecimal(0);
		Warehouse warehouseID = null;
		String paymentRule = "", documentNo = "", warehouseName = "", cashLineId= null, isoCode = "";
		CashBook cashBookID = null;
		Currency currencyID = null;
		CashJournal cashID = null;
		long line = 0;
		
		OBCriteria<DebtPayment> debtPayment = OBDal.getInstance().createCriteria(DebtPayment.class);
		debtPayment.add(Restrictions.eq(DebtPayment.PROPERTY_SALESORDER, order));
		
		for(DebtPayment dp : debtPayment.list()){
			dp.setValid(true);
			OBDal.getInstance().save(dp);
		}
		
		String sql = "select o.formOfPayment," +
				"length(o.documentNo || ' - ' || bp.name || ' - ' || o.grandTotalAmount), " +
				"substr(o.documentNo || ' - ' || bp.name || ' - ' || o.grandTotalAmount,1,197)||'...' , " +
				"o.documentNo || ' - ' || bp.name || ' - ' || o.grandTotalAmount, " +
				"o.grandTotalAmount, o.warehouse " +
				"from Order as o, BusinessPartner as bp where o.businessPartner=bp and o.id='"+order.getId()+"'";
		Query q = OBDal.getInstance().getSession().createQuery(sql);
		ScrollableResults rs = q.scroll(ScrollMode.FORWARD_ONLY);
		while(rs.next()){
			paymentRule = (String) rs.get()[0];
			int length = (Integer) rs.get()[1];
			String substr = (String) rs.get()[2];
			String docElse = (String) rs.get()[3];
			if(length > 200){
				documentNo = substr;
			}else{
				documentNo = docElse;
			}
			grandtotal = rs.getBigDecimal(4);
			warehouseID = (Warehouse) rs.get()[5];
		}
		
		OBQuery<Warehouse> warehouse = OBDal.getInstance().createQuery(Warehouse.class, "as w where w.id='"+warehouseID.getId()+"'");
		for(Warehouse wh : warehouse.list()){
			warehouseName = wh.getName();
		}
		
		if(paymentRule.equals("C") && ((DocSubTypeSO.equals("WI") || DocSubTypeSO.equals("WR")) || DocSubTypeSO!=null)){
			OBCriteria<CashJournalLine> cashLine = OBDal.getInstance().createCriteria(CashJournalLine.class);
			cashLine.add(Restrictions.eq(CashJournalLine.PROPERTY_SALESORDER, order));
			
			for(CashJournalLine cj : cashLine.list()){
				cashLineId = cj.getId();
			}
			
			if(cashLineId==null || cashLineId.equals("0")){
				//------------ Create CashLine ---------------
				sql = "as cb, Currency as c " +
						"where cb.currency=c and cb.active=true " +
						"and cb.organization='"+org+"' " +
						"order by cb.default desc";
				OBQuery<CashBook> cashBook = OBDal.getInstance().createQuery(CashBook.class, sql);
				
				for(CashBook cb : cashBook.list()){
					if(cashBookID == null){
						cashBookID = cb;
						currencyID = cb.getCurrency();
						isoCode = currencyID.getISOCode();
					}else if(cb.getName().equalsIgnoreCase(warehouseName)){
						cashBookID = cb;
						currencyID = cb.getCurrency();
						isoCode = currencyID.getISOCode();
					}
				}
				if(cashBookID == null){
					msg = Error("CashBookPRSCnotfoundOrg");
					throw new OBException(msg);
				}
				
				//------------ Cek Cash ------------------
				OBCriteria<CashJournal> cashJournal = OBDal.getInstance().createCriteria(CashJournal.class);
				cashJournal.add(Restrictions.eq(CashJournal.PROPERTY_CASHBOOK, cashBookID));
				cashJournal.add(Restrictions.eq(CashJournal.PROPERTY_TRANSACTIONDATE, date));
				cashJournal.add(Restrictions.eq(CashJournal.PROPERTY_PROCESSED, false));
				for(CashJournal cj : cashJournal.list()){
					cashID = cj;
				}
				
				//------------ Create Cash ---------------
				if(cashID == null){
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM--dd");
					sdf.format(date);
					String Name = sdf.toString() + isoCode;
					
					CashJournal cash = OBProvider.getInstance().get(CashJournal.class);
					cash.setCashbook(cashBookID);
					cash.setName(Name);
					cash.setTransactionDate(date);
					cash.setAccountingDate(date);
					cash.setBeginningBalance(new BigDecimal(0));
					cash.setEndingBalance(new BigDecimal(0));
					cash.setStatementDifference(new BigDecimal(0));
					cash.setProcessNow(false);
					cash.setProcessed("N");
					cash.setPosted("N");
					
					OBDal.getInstance().save(cash);
					cashID=cash;
				}
				
				//------------ Create Debt Payment ----------------
				OBCriteria<DebtPaymentV> debtPaymentV = OBDal.getInstance().createCriteria(DebtPaymentV.class);
				debtPaymentV.add(Restrictions.eq(DebtPaymentV.PROPERTY_SALESORDER, order));
				
				if(debtPaymentV.list().size()>0){
					ConnectionProvider conn = new DalConnectionProvider();
					String sqlQuery = "SELECT COALESCE(SUM(C_Currency_Round(C_Currency_Convert((Amount + WriteOffAmt), C_Currency_ID, " +
							"'"+currencyID.getId()+"', '"+date+"', NULL, '"+order.getClient().getId()+"', '"+org.getId()+"'), " +
									"'"+order.getCurrency().getId()+"', NULL)), 0) FROM C_DEBT_PAYMENT_V dp WHERE C_Order_ID='"+order.getId()+"'";
					try {
						PreparedStatement st = conn.getPreparedStatement(sql);
						ResultSet result = st.executeQuery();
						while(result.next()){
							int total = result.getInt(0);
							totalCash = new BigDecimal(total);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				DebtPayment debtPay = OBProvider.getInstance().get(DebtPayment.class);
				debtPay.setReceipt(order.isSalesTransaction());
				debtPay.setSettlementCancelled(null);
				debtPay.setSettlementGenerate(null);
				debtPay.setDescription("");
				debtPay.setSalesOrder(order);
				debtPay.setBusinessPartner(order.getBusinessPartner());
				debtPay.setCashJournalLine(null);
				debtPay.setBankAccount(null);
				debtPay.setCashbook(cashBookID);
				debtPay.setFormOfPayment("C");
				debtPay.setPaymentComplete(false);
				debtPay.setAmount(grandtotal.subtract(totalCash));
				debtPay.setWriteoffAmount(new BigDecimal(0));
				debtPay.setDueDate(date);
				debtPay.setManual(false);
				debtPay.setValid(true);
				debtPay.setBankStatementLine(null);
				debtPay.setChangeSettlementCancel(false);
				debtPay.setCancelProcessed(false);
				debtPay.setGenerateProcessed(false);
				debtPay.setProject(order.getProject());
				debtPay.setAutomaticGenerated(true);
				debtPay.setInitialStatus("DE");
				
				OBDal.getInstance().save(debtPay);
				
				//--------- Create CashLine ----------
				OBCriteria<CashJournalLine> cashJournalLine= OBDal.getInstance().createCriteria(CashJournalLine.class);
				cashJournalLine.add(Restrictions.eq(CashJournalLine.PROPERTY_CASHJOURNAL, cashID));
				
				for(CashJournalLine cl : cashJournalLine.list()){
					line = cl.getLineNo();
				}
				line = line + 10;
				
				BigDecimal angka = new BigDecimal(1);
				if(!order.isSalesTransaction()){
					angka = angka.negate();
				}
				
				CashJournalLine cashL = OBProvider.getInstance().get(CashJournalLine.class);
				cashL.setCashJournal(cashID);
				cashL.setPayment(debtPay);
				cashL.setLineNo(line);
				cashL.setDescription(documentNo);
				cashL.setAmount(grandtotal.subtract(totalCash).multiply(angka));
				cashL.setCashType("P");
				cashL.setDiscountAmount(new BigDecimal(0));
				cashL.setWriteoffAmount(new BigDecimal(0));
				cashL.setGenerated(true);
				
				OBDal.getInstance().save(cashL);
				
				OBDal.getInstance().commitAndClose();
			}
		}
	}
	
	public void m_inout_cancel(Order order){
		OBCriteria<ShipmentInOut> shipmentInOut = OBDal.getInstance().createCriteria(ShipmentInOut.class);
		shipmentInOut.add(Restrictions.eq(ShipmentInOut.PROPERTY_SALESORDER, order));
		shipmentInOut.add(Restrictions.ne(ShipmentInOut.PROPERTY_DOCUMENTSTATUS, "RE"));
		shipmentInOut.add(Restrictions.ne(ShipmentInOut.PROPERTY_DOCUMENTSTATUS, "VO"));
		
		for(ShipmentInOut inOut : shipmentInOut.list()){
			inOut.setDocumentAction("RC");
			inOut.setProcessed(false);
			inOut.setUpdated(new Date());
			
			OBDal.getInstance().save(inOut);
		}
	}
	
	public void c_invoice_cancel(Order order, ConnectionProvider conn){
		conn = new DalConnectionProvider();
		
		OBCriteria<Invoice> invoice = OBDal.getInstance().createCriteria(Invoice.class);
		invoice.add(Restrictions.eq(Invoice.PROPERTY_SALESORDER, order));
		invoice.add(Restrictions.eq(Invoice.PROPERTY_DOCUMENTSTATUS, "CO"));
		
		for(Invoice in : invoice.list()){
			in.setDocumentAction("RC");
			in.setProcessed(false);
			in.setUpdated(new Date());
			
			OBDal.getInstance().save(in);
			
			String sql = "select C_INVOICE_POST(NULL, '"+order.getId()+"') from dual";
			PreparedStatement st;
			try {
				st = conn.getPreparedStatement(sql);
				ResultSet result = st.executeQuery();
			} catch (Exception e) {
				throw new OBException(e.getMessage());
			}
		}
	}
	
	public String Error(String value){
		String hasilMessage = "";
		
		OBCriteria<Message> message= OBDal.getInstance().createCriteria(Message.class);
		message.add(Restrictions.eq(Message.PROPERTY_SEARCHKEY, value));
		
		for(Message msg : message.list()){
			hasilMessage = msg.getMessageText();
		}
		
		return hasilMessage;
	}
}
