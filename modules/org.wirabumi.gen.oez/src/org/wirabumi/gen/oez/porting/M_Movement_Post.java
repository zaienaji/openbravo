package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.materialmgmt.onhandquantity.ReservationStock;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.model.materialmgmt.transaction.InternalMovement;
import org.openbravo.model.materialmgmt.transaction.InternalMovementLine;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalConnectionProvider;

public class M_Movement_Post {

	private static ProcessLogger logger;
	public static void post(String record_id, String ad_user_id){
		ConnectionProvider conn=new DalConnectionProvider();
		logger=new ProcessLogger(conn);
		recordValidation(record_id,ad_user_id,conn);
	
		
	}
	private static void accountingStep(InternalMovement internalMovement,Connection conDal,String v_user_id){
		/**
	      * Accounting first step
	      */
		addLog("Accounting first step");
		OBCriteria<InternalMovementLine> cIml=OBDal.getInstance().createCriteria(InternalMovementLine.class);
		cIml.add(Restrictions.eq(InternalMovementLine.PROPERTY_MOVEMENT, internalMovement));
		cIml.addOrderBy(InternalMovementLine.PROPERTY_LINENO, false);
		//OBQuery<InternalMovementLine> lineQuery=OBDal.getInstance().createQuery(InternalMovementLine.class, "" ," order line");
		for(InternalMovementLine line:cIml.list()){
			addLog("Transaction for line "+line.getLineNo());
			if(line.getMovementQuantity().equals(BigDecimal.ZERO)){
				addLog("Goods movements with zero qty - "+line.getLineNo());
				restoreProcessStatus(internalMovement, true);
				throw new OBException("Goods movements with zero qty - "+line.getLineNo());
			}
			if(line.getStockReservation()!=null){
				StringBuilder hql1=new StringBuilder();
				hql1.append("SELECT COALESCE(SUM(m.quantity - COALESCE(m.released,0)), 0) FROM MaterialMgmtReservationStock m");
				hql1.append(" WHERE m.reservation.id = ?");
				hql1.append(" AND m.storageBin.id = ?");
				hql1.append(" AND COALESCE(m.attributeSetValue.id, '0') = COALESCE(?, '0')");
				final Query query = OBDal.getInstance().getSession().createQuery(hql1.toString());
				query.setParameter(0, line.getStockReservation().getId());
				query.setParameter(1, line.getStorageBin().getId());
				query.setParameter(2, line.getAttributeSetValue().getId());
				final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
				if (result.next()) {					
					Long v_reservedqty= (Long) result.get()[0];
					if(v_reservedqty.longValue()>line.getMovementQuantity().longValue()){
						/*						  
						 *-- There is not enough stock reserved in given storage bin and attributes
			              -- Try to reallocate no allocated reserved stock.
			              -- An error means that there is not available stock to assign to the given reservation because:
			              -- 1) there is not enough on hand stock
			              -- 2) there are other reservations using that stock and cannot be reallocated to use a different stock
			              -- 3) the given reservation has some allocated stock with different storage bin or attributes
						 */
						final String sql1="SELECT *  FROM M_RESERVATION_REALLOCATE(?,?,?,?,?)";
						try {
							PreparedStatement ps1;
							ps1 = conDal.prepareStatement(sql1);
							ps1.setString(1, line.getStockReservation().getId());
							ps1.setString(2, line.getStorageBin().getId());
							ps1.setString(3, line.getAttributeSetValue().getId());
							ps1.setLong(4, line.getMovementQuantity().longValue());
							ps1.setString(5, v_user_id);
							ResultSet rs2=ps1.executeQuery();							
							if(rs2.next()){
								Long v_res_result=rs2.getLong(0);
								String v_res_msg=rs2.getString(1);
								int val= v_res_result.intValue();
								if(val==0 || val==2){
									addLog("line id : "+line.getId()+" "+v_res_msg);
									restoreProcessStatus(internalMovement, true);
									throw new OBException("line id : "+line.getId()+" "+v_res_msg);		
								}
							}else{
								addLog("Error executing query : "+sql1);
								throw new OBException("Error executing query : "+sql1);
							}
						} catch (SQLException e) {
							e.printStackTrace();
							addLog("Error executing query : "+e.getMessage());
							restoreProcessStatus(internalMovement, true);
							throw new OBException("Error executing query : "+e.getMessage());
						}
					}
					/*
					 * -- If there is enough stock reserved release the stock and reserve it in the destination storage bin
					 */
					BigDecimal reservedqty=line.getMovementQuantity();	
					OBCriteria<StorageDetail> cds=OBDal.getInstance().createCriteria(StorageDetail.class);
					cds.add(Restrictions.eq(StorageDetail.PROPERTY_PRODUCT, line.getProduct()));
					cds.add(Restrictions.eq(StorageDetail.PROPERTY_STORAGEBIN, line.getStorageBin()));
					cds.add(Restrictions.eq(StorageDetail.PROPERTY_ATTRIBUTESETVALUE, line.getAttributeSetValue()));
					cds.add(Restrictions.eq(StorageDetail.PROPERTY_UOM, null));					
					if(cds.list().size()>0){
						StorageDetail v_storage_detail=cds.list().get(0);
						StringBuilder hql2=new StringBuilder(); 
						hql2.append("SELECT m.id, (m.quantity - COALESCE(m.released,0)) as reservedqty, m.allocated");
						hql2.append(" FROM MaterialMgmtReservationStock m");
						hql2.append(" WHERE m.id = ? AND m.storageBin.id = ?");
						hql2.append(" AND COALESCE(?, '0') = COALESCE(null, '0') ORDER BY CASE m.allocated WHEN 'Y' THEN 0 ELSE 1 END");
						final Query query2 = OBDal.getInstance().getSession().createQuery(hql2.toString());
						query2.setParameter(0, line.getStockReservation().getId());
						query2.setParameter(1, line.getStorageBin().getId());
						query2.setParameter(2, line.getAttributeSetValue().getId());
						final ScrollableResults result2 = query2.scroll(ScrollMode.FORWARD_ONLY);
						while (result2.next()) {							
							String m_reservationstock_id=result2.getString(0);
							Long reservedqty1=result2.getLong(1);								
							String allocated=result2.getString(2);
							BigDecimal v_qtytorelease=reservedqty.min(new BigDecimal(reservedqty1));
							ReservationStock reservationStock=OBDal.getInstance().get(ReservationStock.class, m_reservationstock_id);
							BigDecimal newQty=reservationStock.getQuantity().subtract(v_qtytorelease);
							reservationStock.setQuantity(newQty);
							OBDal.getInstance().save(reservationStock);
							final String sql1="SELECT * FROM M_RESERVE_STOCK_MANUAL(?,'SD',?,?,?,?)";
							PreparedStatement ps1;
								try {
									ps1 = conDal.prepareStatement(sql1);
									ps1.setString(1, line.getStockReservation().getId());
									ps1.setString(2, v_storage_detail.getId());
									ps1.setBigDecimal(3, v_qtytorelease);
									ps1.setString(4, v_user_id);
									ps1.setString(5, allocated);							
									ResultSet rs3=ps1.executeQuery();								
									if(rs3.next()){
										String v_res_stock_id=rs3.getString(0);
									}
									v_reservedqty=v_reservedqty-v_qtytorelease.longValue();
									if(v_reservedqty<=0){
										return;
									}
								} catch (SQLException e) {						
									e.printStackTrace();
									addLog("SQL Error : "+e.getMessage());
									restoreProcessStatus(internalMovement, true);
									throw new OBException("SQL Error : "+e.getMessage());								
								}
						}// end while loop
						String sql2="DELETE FROM m_reservation_stock WHERE quantity = 0 AND m_reservation_id = ?";
						try {
							PreparedStatement ps2=conDal.prepareStatement(sql2);
							ps2.setString(1, line.getStockReservation().getId());
							if(!ps2.execute()){
								addLog("Error execute query : "+sql2);
								restoreProcessStatus(internalMovement, true);
								throw new OBException("Error execute query : "+sql2);
							}
						} catch (SQLException e) {
							e.printStackTrace();
							addLog("Error execute query : "+sql2);
							restoreProcessStatus(internalMovement, true);
							throw new OBException("Error execute query : "+sql2);
							
						}
					}else{
						addLog("StorageDetail not found.");
						restoreProcessStatus(internalMovement, true);
						throw new OBException("StorageDetail not found.");
					}
				}else{
					addLog("Query has no result : "+hql1.toString());
					restoreProcessStatus(internalMovement, true);
					throw new OBException("Query has no result : "+hql1.toString());
				}
			}// end if
			//  COALESCE(Cur_MoveLine.M_AttributeSetInstance_ID, '0')
			AttributeSetInstance attributeSet=null;
			if(line.getAttributeSetValue()==null){
				attributeSet=OBDal.getInstance().get(AttributeSetInstance.class, "0");				
			}else{
				attributeSet=line.getAttributeSetValue();
			}
			BigDecimal orderQty=line.getOrderQuantity();
			// from 
			MaterialTransaction mtFrom=OBProvider.getInstance().get(MaterialTransaction.class);
			mtFrom.setActive(true);
			mtFrom.setMovementType("M-");
			mtFrom.setStorageBin(line.getStorageBin());
			mtFrom.setProduct(line.getProduct());
			//mtFrom.setAttributeSetValue(line.getAttributeSetValue());			
			mtFrom.setMovementDate(internalMovement.getMovementDate());
			mtFrom.setAttributeSetValue(attributeSet);
			mtFrom.setMovementQuantity((line.getMovementQuantity().compareTo(BigDecimal.ZERO)==0?BigDecimal.ZERO:line.getMovementQuantity().negate()));
			mtFrom.setMovementLine(line);
			mtFrom.setUOM(line.getUOM());
			
			if(orderQty==null){
				mtFrom.setOrderQuantity(null);
				mtFrom.setOrderUOM(null);				
			}else{
				mtFrom.setOrderQuantity((orderQty.compareTo(BigDecimal.ZERO)==0?BigDecimal.ZERO:orderQty.negate()));				
				mtFrom.setOrderUOM(line.getOrderUOM());
			}
			OBDal.getInstance().save(mtFrom);
			/*
			 * M_Transaction_ID, AD_Client_ID, AD_Org_ID, IsActive,
              Created, CreatedBy, Updated, UpdatedBy,
              MovementType, M_Locator_ID, M_Product_ID, M_AttributeSetInstance_ID,
              MovementDate, MovementQty, M_MovementLine_ID, M_Product_UOM_ID,
              QuantityOrder, C_UOM_ID
              ------------------------
			 * get_uuid(), Cur_MoveLine.AD_Client_ID, Cur_MoveLine.AD_Org_ID, 'Y',
              TO_DATE(NOW()), v_p_User, TO_DATE(NOW()), v_p_User,
              'M-', Cur_MoveLine.M_Locator_ID, Cur_MoveLine.M_Product_ID, COALESCE(Cur_MoveLine.M_AttributeSetInstance_ID, '0'),
              v_MoveDate, (Cur_MoveLine.MovementQty * -1), Cur_MoveLine.M_MovementLine_ID, Cur_MoveLine.M_Product_UOM_ID,
              (Cur_MoveLine.QuantityOrder * -1), Cur_MoveLine.C_UOM_ID
			 */
			// to
			MaterialTransaction mtTo=OBProvider.getInstance().get(MaterialTransaction.class);
			mtTo.setActive(true);
			mtTo.setMovementType("M+");
			mtTo.setStorageBin(line.getStorageBin());
			mtTo.setProduct(line.getProduct());		
			mtTo.setMovementDate(internalMovement.getMovementDate());	
			mtTo.setAttributeSetValue(attributeSet);
			mtTo.setMovementQuantity(line.getMovementQuantity());
			mtTo.setMovementLine(line);
			if(orderQty==null){
				mtTo.setOrderQuantity(null);
				mtTo.setOrderUOM(null);
			}else{
				mtTo.setOrderQuantity(orderQty);
				mtTo.setOrderUOM(line.getOrderUOM());
			}
			mtTo.setUOM(line.getUOM());
			OBDal.getInstance().save(mtTo);
			/*
			 * (
              M_Transaction_ID, AD_Client_ID, AD_Org_ID, IsActive,
              Created, CreatedBy, Updated, UpdatedBy,
              MovementType, M_Locator_ID, M_Product_ID, M_AttributeSetInstance_ID,
              MovementDate, MovementQty, M_MovementLine_ID, M_Product_UOM_ID,
              QuantityOrder, C_UOM_ID
            )
            VALUES
            (
              get_uuid(), Cur_MoveLine.AD_Client_ID, Cur_MoveLine.AD_Org_ID, 'Y',
              TO_DATE(NOW()), v_p_User, TO_DATE(NOW()), v_p_User,
              'M+', Cur_MoveLine.M_LocatorTo_ID, Cur_MoveLine.M_Product_ID, COALESCE(Cur_MoveLine.M_AttributeSetInstance_ID, '0'),
              v_MoveDate, Cur_MoveLine.MovementQty, Cur_MoveLine.M_MovementLine_ID, Cur_MoveLine.M_Product_UOM_ID,
              Cur_MoveLine.QuantityOrder, Cur_MoveLine.C_UOM_ID
            )
			 */
			
		}// end for
		// update header status
		restoreProcessStatus(internalMovement);
		OBDal.getInstance().commitAndClose();		
	}
	private static void restoreProcessStatus(InternalMovement internalMovement){
		restoreProcessStatus(internalMovement,false);
	}
	private static void restoreProcessStatus(InternalMovement internalMovement,boolean commitClose){
		addLog("UnLockingMovement");
		internalMovement.setProcessed(true);
		internalMovement.setProcessNow(false);
		OBDal.getInstance().save(internalMovement);		
		if(commitClose==true){
			OBDal.getInstance().commitAndClose();
		}
		addLog("Finished");
	}
	 public static String ad_get_org_le_bu(String organizationHeader, String type) {
		    String orgHeaderId;
		    boolean isLegalenty = false;
		    boolean isBusinessUnit = false;
		    orgHeaderId = organizationHeader;
		    ConnectionProvider conn = new DalConnectionProvider();
		    Statement st;

		    if (type.endsWith("")) {
		      if (isLegalenty == false && isBusinessUnit == false) {
		        String sqlCekLebu = "SELECT hh.parent_id, ad_orgtype.isbusinessunit, ad_orgtype.islegalentity "
		            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
		            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
		            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
		            + "AND hh.ad_tree_id = pp.ad_tree_id "
		            + "WHERE hh.node_id='"
		            + orgHeaderId
		            + "' AND ad_org.isready='Y' "
		            + "AND EXISTS (SELECT 1 FROM ad_tree "
		            + "WHERE ad_tree.treetype='OO' "
		            + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
		            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

		        try {
		          st = conn.getStatement();
		          ResultSet rsCekLebu = st.executeQuery(sqlCekLebu);
		          while (rsCekLebu.next()) {
		            orgHeaderId = rsCekLebu.getString("parent_id");
		            String bussinesunit = rsCekLebu.getString("isbusinessunit");
		            String legalenty = rsCekLebu.getString("islegalentity");

		            if (legalenty.endsWith("Y")) {
		              isLegalenty = true;
		            } else {
		              isLegalenty = false;
		            }

		            if (bussinesunit.endsWith("Y")) {
		              isBusinessUnit = true;
		            } else {
		              isBusinessUnit = false;
		            }
		          }
		        } catch (Exception e) {
		          e.printStackTrace();
		          throw new OBException(e.getMessage());
		        }
		      }
		    } else if (type.endsWith("LE")) {
		      if (isLegalenty == false) {
		        String sqlCekLe = "SELECT hh.parent_id, ad_orgtype.islegalentity "
		            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
		            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
		            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
		            + "AND hh.ad_tree_id = pp.ad_tree_id " + "WHERE hh.node_id='" + orgHeaderId
		            + "' AND ad_org.isready='Y' " + "AND EXISTS (SELECT 1 FROM ad_tree "
		            + "WHERE ad_tree.treetype='OO' " + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
		            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

		        try {
		          st = conn.getStatement();
		          ResultSet rsCekLe = st.executeQuery(sqlCekLe);
		          while (rsCekLe.next()) {
		            orgHeaderId = rsCekLe.getString("parent_id");
		            String legalenty = rsCekLe.getString("islegalentity");

		            if (legalenty.endsWith("Y")) {
		              isLegalenty = true;
		            } else {
		              isLegalenty = false;
		            }
		          }
		        } catch (Exception e) {
		          e.printStackTrace();

		          throw new OBException(e.getMessage());
		        }
		      }
		    } else if (type.endsWith("BU")) {
		      if (isBusinessUnit == false) {
		        String sqlCekBu = "SELECT hh.parent_id, ad_orgtype.isbusinessunit "
		            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
		            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
		            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
		            + "AND hh.ad_tree_id = pp.ad_tree_id " + "WHERE hh.node_id='" + orgHeaderId
		            + "' AND ad_org.isready='Y' " + "AND EXISTS (SELECT 1 FROM ad_tree "
		            + "WHERE ad_tree.treetype='OO' " + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
		            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

		        try {
		          st = conn.getStatement();
		          ResultSet rsCekBu = st.executeQuery(sqlCekBu);
		          while (rsCekBu.next()) {
		            orgHeaderId = rsCekBu.getString("parent_id");
		            String businessunit = rsCekBu.getString("isbusinessunit");

		            if (businessunit.endsWith("Y")) {
		              isBusinessUnit = true;
		            } else {
		              isBusinessUnit = false;
		            }
		          }
		        } catch (Exception e) {
		          e.printStackTrace();
		          throw new OBException(e.getMessage());
		        }
		      }
		    }
		    return orgHeaderId;
		  }
	public static String ad_get_doc_le_bu(String headerTable, String documentId, String headerColumnName,
		      String type) {

		    String organizationHeader = "";
		    boolean isBussinesUnit;
		    boolean isLegalEntity;

		    String hql = "SELECT a.organization.id, a.organization.organizationType.businessUnit, a.organization.organizationType.legalEntity "
		        + "FROM " + headerTable + " a  WHERE a." + headerColumnName + " = '" + documentId + "'";
		    Query query = OBDal.getInstance().getSession().createQuery(hql);

		    ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
		    while (rs.next()) {
		      isBussinesUnit = (Boolean) rs.get()[1];
		      isLegalEntity = (Boolean) rs.get()[2];
		      if ((isBussinesUnit == true && (type.endsWith("BU") || type.endsWith("")))
		          || (isLegalEntity == true && (type.endsWith("LE") || type.endsWith("")))) {
		        organizationHeader = (String) rs.get()[0];
		      } else {
		        organizationHeader = (String) rs.get()[0];
		        organizationHeader = ad_get_org_le_bu(organizationHeader, "");
		      }
		    }
		    return organizationHeader;
		  }
	public static int ad_org_chk_documents(String HeaderTable, String LinesTable, String DocumentId,
		      String HeaderColumnName, String LinesColumnName) {

		    String OrganizationHeaderId;
		    String lineOrganization = "";
		    int isInclude = 0;
		    OrganizationHeaderId = ad_get_doc_le_bu(HeaderTable, DocumentId, HeaderColumnName, "");

		    String hql = "SELECT DISTINCT(a.organization) FROM " + LinesTable + " a " + "WHERE a."
		        + LinesColumnName + "." + HeaderColumnName + " = '" + DocumentId + "' "
		        + "AND a.organization.id='" + OrganizationHeaderId + "'";
		    Query query = OBDal.getInstance().getSession().createQuery(hql);

		    ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
		    while (rs.next()) {
		      Organization organization = (Organization) rs.get()[0];
		      boolean isLegalentity = organization.getOrganizationType().isLegalEntity();
		      boolean isBusinessUnit = organization.getOrganizationType().isBusinessUnit();
		      lineOrganization = organization.getId();

		      if (isLegalentity == false && isBusinessUnit == false) {
		        String sql = "SELECT hh.parent_id, ad_orgtype.isbusinessunit, ad_orgtype.islegalentity "
		            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
		            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
		            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
		            + "AND hh.ad_tree_id = pp.ad_tree_id " + "WHERE hh.node_id='" + lineOrganization
		            + "' AND ad_org.isready='Y' " + "AND EXISTS (SELECT 1 FROM ad_tree "
		            + "WHERE ad_tree.treetype='OO' " + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
		            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

		        ConnectionProvider conn = new DalConnectionProvider();
		        try {
		          Statement st = conn.getStatement();
		          ResultSet rs2 = st.executeQuery(sql);

		          while (rs2.next()) {
		            lineOrganization = rs2.getString("parent_id");
		            String bussinesunit = rs2.getString("isbusinessunit");
		            String legalenty = rs2.getString("islegalentity");

		            if (legalenty.endsWith("Y")) {
		              isLegalentity = true;
		            } else {
		              isLegalentity = false;
		            }

		            if (bussinesunit.endsWith("Y")) {
		              isBusinessUnit = true;
		            } else {
		              isBusinessUnit = false;
		            }
		          }

		        } catch (Exception e) {
		          e.printStackTrace();
		          throw new OBException(e.getMessage());
		        }
		      }
		      if (!lineOrganization.endsWith(OrganizationHeaderId)) {
			      isInclude = -1;
			  }
		      if(isInclude==-1){
		    	  return isInclude;	  
		      }
		    }	    
		    return isInclude;
		  }
	private static void addLog(String msg){
		logger.logln("m_movement_post - "+msg+".");
	}
	private static boolean  recordValidation(String record_id, String ad_user_id, ConnectionProvider conn) {		
		addLog("validation record");
		String v_Record_ID=record_id;
		String v_p_User=ad_user_id;
		String v_MoveDate;		
		String v_Org_ID;
		String v_Client_ID;
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
		
		InternalMovement internalMovement=OBDal.getInstance().get(InternalMovement.class, v_Record_ID);
		if(internalMovement!=null){
			if(internalMovement.isProcessed()){
				addLog(v_Record_ID+" has been processed.");
				throw new OBException("record has been processed.");	
			}
			if(internalMovement.isProcessNow()){
				addLog(v_Record_ID+" is still processing.");
				throw new OBException("record is processing.");
			}
			addLog("CheckingRestrictions");
			StringBuilder hql1=new StringBuilder();
			hql1.append("select count(*), max(m.lineNo) from  MaterialMgmtInternalMovementLine m");
			hql1.append(" where m.product.attributeSet is not null");
			hql1.append(" and (m.product.attributeSetValue is null or m.product.attributeSetValue<> 'F')");
			hql1.append(" and (SELECT requireAtLeastOneValue FROM AttributeSet a WHERE a.id=m.product.attributeSet.id) = 'Y' AND COALESCE(m.attributeSetValue.id, '0') = '0'");
			hql1.append(" and m.movement.id=?");
			final Query query = OBDal.getInstance().getSession().createQuery(hql1.toString());
			query.setParameter(0, v_Record_ID);
			final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
			if (result.next()) {
				Long count = (Long) result.get()[0];
				String lineNo= (String) result.get()[1];
					if(count.longValue()!=0){
						addLog(lineNo+" have product without attributeSet.");
						throw new OBException(lineNo +" have product without attributeSet.");
					}
					StringBuilder hql2=new StringBuilder();
					hql2.append("SELECT count(m), max(m.product.name)");
					hql2.append(" FROM MaterialMgmtInternalMovementLine m JOIN m.product p");
					hql2.append(" WHERE m.movement.id = ?");
					hql2.append(" AND COALESCE(p.isGeneric, 'N') = 'Y'");
					Query query2=OBDal.getInstance().getSession().createQuery(hql1.toString());
					query2.setParameter(0, v_Record_ID);
					final ScrollableResults result2 = query2.scroll(ScrollMode.FORWARD_ONLY);
					if (result2.next()) {
						Long count2= (Long) result.get()[0];
						String productName= (String) result.get()[1];
						if(count2.longValue()>0){
							addLog("Cannot use generic product '"+productName+"'.");
							throw new OBException("Cannot use generic product '"+productName+"'.");
						}
						// -- Check the header belongs to a organization where transactions are posible and ready to use --
						if(internalMovement.getOrganization().isReady()==false){
							addLog("Org header not ready.");
							throw new OBException("Org header not ready.");
						}
						if(internalMovement.getOrganization().getOrganizationType().isTransactionsAllowed()==false){
							addLog("Org header not transaction allowed.");
							throw new OBException("Org header not transaction allowed.");
						}
						Connection conDal=OBDal.getInstance().getConnection();												
//						try {
//							String sql1="select AD_ORG_CHK_DOCUMENTS('M_MOVEMENT', 'M_MOVEMENTLINE', ?, 'M_MOVEMENT_ID', 'M_MOVEMENT_ID') " +
//									"as v_is_included from dual";
//							final PreparedStatement ps = conDal.prepareStatement(sql1);
//							ps.setString(1, v_Record_ID);
//							ResultSet rsDoc =ps.executeQuery(); 
//							 if(rsDoc.next()) {
//								 int v_is_included = rsDoc.getInt("v_is_included");
//								 if(v_is_included==-1){
//									 addLog("Lines and Header different LE or BU.");
//									throw new OBException("Lines and Header different LE or BU.");
//								 }								 
//							 }else{
//								addLog("Lines and Header different LE or BU.");
//								throw new OBException("Lines and Header different LE or BU.");
//							 }
							
							 int v_is_included = ad_org_chk_documents("MaterialMgmtInternalMovement", "MaterialMgmtInternalMovementLine", v_Record_ID, "id", "movement");
							 if(v_is_included==-1){
								 addLog("Lines and Header different LE or BU.");
								throw new OBException("Lines and Header different LE or BU.");
							 }
							//-- Check the period control is opened (only if it is legal entity with accounting) --
						    // -- Gets the BU or LE of the document --
//							final String sql2="select AD_GET_DOC_LE_BU('M_MOVEMENT', ?, 'M_MOVEMENT_ID', 'LE') as v_org_bule_id from dual";							 
//							final PreparedStatement ps2=conDal.prepareStatement(sql2); 
//							ps2.setString(1, v_Record_ID);
//							ResultSet rs2=ps2.executeQuery();
//							String v_org_bule_id=null;
//							if(rs2.next()){
//								v_org_bule_id=rs2.getString("v_org_bule_id");
//							}else{
//								addLog("org_bule_id not found.");
//								throw new OBException("org_bule_id not found.");
//							}
							String v_org_bule_id=ad_get_doc_le_bu("MaterialMgmtInternalMovement", v_Record_ID, "id", "LE");
							if(v_org_bule_id!=null){
								Organization organization=OBDal.getInstance().get(Organization.class, v_org_bule_id);
								if(organization.getOrganizationType().isLegalEntityWithAccounting()){
									v_MoveDate=sdf.format(internalMovement.getMovementDate());
									v_Org_ID=internalMovement.getOrganization().getId();
									v_Client_ID = internalMovement.getClient().getId();									
//									final String sql3="SELECT C_CHK_OPEN_PERIOD(?, '"+v_MoveDate+"', 'MMM', NULL) as v_available_period FROM DUAL";
//									PreparedStatement ps3;
//									try {
//										ps3 = conDal.prepareStatement(sql3);
//										ps3.setString(1, v_Org_ID);
//										//ps3.setString(2, v_MoveDate);									
//										ResultSet rs3=ps3.executeQuery();
//										if(rs3.next()){
//											Long v_available_period=rs3.getLong("v_available_period");
//											if(v_available_period.longValue()!=1){
//												addLog("Period not available.");
//												throw new OBException("Period not available.");	
//											}
//										}else{
//											addLog("Period not available.");
//											throw new OBException("Period not available.");
//										}
//									} catch (SQLException e) {
//										// TODO Auto-generated catch block
//										e.printStackTrace();
//									}
//									
									int v_available_period=c_chk_open_period(v_Org_ID, internalMovement.getMovementDate(), "MMM", null);
									if(v_available_period!=1){
										addLog("Period not available.");
										throw new OBException("Period not available.");	
									}
								}								
								addLog("LockingMovement");
								//UPDATE M_Movement  SET Processing='Y',Updated=TO_DATE(NOW()),UpdatedBy=v_p_User  WHERE M_Movement_ID=v_Record_ID;
								internalMovement.setProcessNow(true);
								internalMovement.setUpdated(new Date());
								User user=OBDal.getInstance().get(User.class, v_p_User);
								internalMovement.setUpdatedBy(user);
								OBDal.getInstance().save(internalMovement);
								accountingStep(internalMovement,conDal,v_p_User);
								return true;
							}else{
								addLog("org_bule_id not found.");
								throw new OBException("org_bule_id not found.");
							}
//						} catch (SQLException e) {
//							addLog("Error executing query : "+e.getMessage());
//							throw new OBException("Error executing query : "+e.getMessage());
//						}
					}else{
						addLog("Error executing query : "+hql2.toString());
						throw new OBException("Error executing query : "+hql2.toString());
					}
			}else{
				addLog("Error executing query : "+hql1.toString());
				throw new OBException("Error executing query : "+hql1.toString());
			}
		}else{
			throw new OBException("record id not found");
		}
	}
	public static String ad_org_getperiodcontrolallow(String OrganizationId) {

	    String parentId = "";
	    String nodeId = "";
	    Organization organization;
	    boolean isPeriodConttrolAllowed = false;

	    organization = OBDal.getInstance().get(Organization.class, OrganizationId);
	    isPeriodConttrolAllowed = organization.isAllowPeriodControl();

	    if (isPeriodConttrolAllowed == true) {
	      parentId = organization.getId();
	    } else {
	    	nodeId=OrganizationId;
	      while ((parentId.compareToIgnoreCase("0")!=0 && nodeId.compareToIgnoreCase("0")!=0)) {
			String sql=	"SELECT parent_id FROM ad_treenode t WHERE node_id=? AND " +
					"EXISTS (SELECT 1 FROM ad_tree, ad_org WHERE ad_tree.ad_client_id = ad_org.ad_client_id " +
					"AND ad_tree.ad_client_id=t.ad_client_id AND ad_tree.ad_table_id='155' " +
					"AND t.ad_tree_id=ad_tree.ad_tree_id)";
	        ConnectionProvider conn = new DalConnectionProvider();
	        try {	          
	          PreparedStatement ps=conn.getPreparedStatement(sql);
	          ps.setString(1, nodeId);
	          ResultSet rs2 = ps.executeQuery();
	          while (rs2.next()) {
	            parentId = rs2.getString("parent_id");
	            organization = OBDal.getInstance().get(Organization.class, parentId);
	            isPeriodConttrolAllowed = organization.isAllowPeriodControl();
	            if(isPeriodConttrolAllowed){
	            	return parentId;
	            }
	            nodeId = parentId;
	          }
	        } catch (Exception e) {
	          throw new OBException(e.getMessage());
	        }
	      }
	    }
	    return parentId;
	  }
	public static int c_chk_open_period(String adOrgId, Date ProductionDate, String docType, String docTypeId) {
	    ConnectionProvider conn = new DalConnectionProvider();
	    int availablePeriod = 0;
	    String organizationPeriodeAllow = ad_org_getperiodcontrolallow(adOrgId);

	    if (!(docTypeId == "" || docTypeId==null)) {

	      String sql = "SELECT COUNT(p.c_period_id) AS periodeActive FROM c_period p WHERE '"
	          + ProductionDate + "' >= p.startdate AND '" + ProductionDate + "' <=p.enddate+1 "
	          + "AND EXISTS (SELECT 1 FROM c_periodcontrol pc WHERE pc.c_period_id = p.c_period_id "
	          + "AND pc.docbasetype=(SELECT docbasetype FROM c_doctype WHERE c_doctype_id='"
	          + docTypeId + "') " + "AND pc.ad_org_id= '" + organizationPeriodeAllow
	          + "' AND pc.periodstatus='O')";

	      try {
	        Statement st = conn.getStatement();
	        ResultSet rs = st.executeQuery(sql);
	        while (rs.next()) {
	          availablePeriod = rs.getInt("periodeActive");
	        }
	      } catch (Exception e) {
	        e.printStackTrace();
	        throw new OBException(e.getMessage());
	      }
	    } else if (docType.compareToIgnoreCase("")!=0) {
	      String sql = "SELECT COUNT(p.c_period_id) AS periodeActive FROM c_period p WHERE '"
	          + ProductionDate + "' >= p.startdate AND '" + ProductionDate + "' <=p.enddate+1 "
	          + "AND EXISTS (SELECT 1 FROM c_periodcontrol pc WHERE pc.c_period_id = p.c_period_id "
	          + "AND pc.docbasetype='" + docType + "' " + "AND pc.ad_org_id= '"
	          + organizationPeriodeAllow + "' AND pc.periodstatus='O')";

	      try {
	        Statement st = conn.getStatement();
	        ResultSet rs = st.executeQuery(sql);
	        while (rs.next()) {
	          availablePeriod = rs.getInt("periodeActive");
	        }
	      } catch (Exception e) {
	        e.printStackTrace();
	        throw new OBException(e.getMessage());
	      }
	    } else {
	      availablePeriod = 0;
	    }

	    return availablePeriod;
	  }
}
