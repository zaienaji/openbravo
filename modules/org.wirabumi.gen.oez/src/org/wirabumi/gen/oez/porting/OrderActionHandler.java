package org.wirabumi.gen.oez.porting;

import java.sql.SQLException;
import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.common.order.Order;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;
import org.openbravo.model.ad.ui.Tab;



public class OrderActionHandler extends DocumentRoutingHandlerAction {
	private final String reactiveStatus="RE";
	private final String completeStatus="CO";
	private final String draftStatus="DR";
	private final String closedStatus="CL";
	private final String voidStatus="VO";
	private final String orderProcessID="104";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		
		final ConnectionProvider conn = new DalConnectionProvider();
		
		//berlaku untuk doComplete, doReactive, doClose, dan doVoid
		
		for (String orderID : recordId){
			Order order = OBDal.getInstance().get(Order.class, orderID);
			String docstatus=order.getDocumentStatus();
			String docaction=order.getDocumentAction();
			if (doc_status_to.equalsIgnoreCase(completeStatus)){
				
				//cek apakah dari CL atau VO, jika ya, maka exception
				if (order.getDocumentStatus().equalsIgnoreCase(closedStatus)||
						order.getDocumentStatus().equalsIgnoreCase(voidStatus))
					throw new OBException("@ActionNotAllowedHere@");
				
				//ubdah dulu docstatus menjadi DR, baru di complete
				order.setDocumentStatus(draftStatus);
				order.setDocumentAction(doc_status_to);
				
			} else if (doc_status_to.equalsIgnoreCase(reactiveStatus) || 
					doc_status_to.equalsIgnoreCase(voidStatus) ||
					doc_status_to.equalsIgnoreCase(closedStatus)){
				
				//cek apakah doc status adalah CO, jika tidak maka exception
				if (!order.getDocumentStatus().equalsIgnoreCase(completeStatus))
					throw new OBException("@ActionNotAllowedHere@");
				order.setDocumentAction(doc_status_to);
				
			} 
			
			OBDal.getInstance().save(order);
			try {
				OBDal.getInstance().getConnection().commit();
			} catch (SQLException e) {
				e.printStackTrace();
				throw new OBException(e.getMessage());
			}
			
			try{
				OBError oberror = doExecuteProcedureCall(orderID, orderProcessID);
				if (oberror.getType().equalsIgnoreCase("Error")){
					String message = oberror.getMessage();
					message = message.substring(8, message.length()-1);
					String convertedMessage = Utility.messageBD(conn, message, vars.getLanguage());
					throw new OBException(convertedMessage);
				}
					
			}
			catch (OBException e){
				//exception happen, rollback doc status
				order.setDocumentStatus(docstatus);
				order.setDocumentAction(docaction);
				OBDal.getInstance().save(order);
				try {
					OBDal.getInstance().getConnection().commit();
				} catch (SQLException e2) {
					e.printStackTrace();
					throw new OBException(e.getMessage());
				}
				
				//throw chain exception
				e.printStackTrace();
				throw new OBException(e.getMessage());
			}
			
			OBDal.getInstance().refresh(order);
			docstatus=order.getDocumentStatus();
			boolean processed = order.isProcessed();
			if (processed && !docstatus.equalsIgnoreCase(completeStatus) && doc_status_to.equalsIgnoreCase(completeStatus)){
				//terproses tapi doc status masih draft
				//maka ubah docstatus menjadi complete
				order.setDocumentStatus(doc_status_to);
				OBDal.getInstance().save(order);
				try {
					OBDal.getInstance().getConnection().commit();
				} catch (SQLException e) {
					e.printStackTrace();
					throw new OBException(e.getMessage());
				}
			}
			
		}

	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// TODO sementara null dulu
		return null;
	}
	
	@Override
	public Boolean updateDocumentStatus(Entity entity,  List<String> RecordId, String document_status_to,String column){
		if (document_status_to.equalsIgnoreCase(completeStatus)||
				document_status_to.equalsIgnoreCase(reactiveStatus)||
				document_status_to.equalsIgnoreCase(closedStatus)||
				document_status_to.equalsIgnoreCase(voidStatus))
			return true;
		else
			return super.updateDocumentStatus(entity, RecordId, document_status_to, column);
		
	}
}


