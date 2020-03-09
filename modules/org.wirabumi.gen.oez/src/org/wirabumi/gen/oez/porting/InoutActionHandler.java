package org.wirabumi.gen.oez.porting;

import java.sql.SQLException;
import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class InoutActionHandler extends DocumentRoutingHandlerAction {
	private final String reactiveStatus="RE";
	private final String completeStatus="CO";
	private final String draftStatus="DR";
	private final String closedStatus="CL";
	private final String voidStatus="VO";
	private final String inoutProcessID="109";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		if (doc_status_to.equalsIgnoreCase(reactiveStatus))
			throw new OBException("@ActionNotAllowedHere@"); //shipment inout tidak boleh di reactive
		
		for (String inoutID : recordId){
			ShipmentInOut inout = OBDal.getInstance().get(ShipmentInOut.class, inoutID);
			String docstatus=inout.getDocumentStatus();
			String docaction=inout.getDocumentAction();
			if (doc_status_to.equalsIgnoreCase(completeStatus)){
				
				//cek apakah dari CL atau VO, jika ya, maka exception
				if (inout.getDocumentStatus().equalsIgnoreCase(closedStatus)||
						inout.getDocumentStatus().equalsIgnoreCase(voidStatus))
					throw new OBException("@ActionNotAllowedHere@");
				
				//ubdah dulu docstatus menjadi DR, baru di complete
				inout.setDocumentStatus(draftStatus);
				inout.setDocumentAction(doc_status_to);
				OBDal.getInstance().save(inout);
				try {
					OBDal.getInstance().getConnection().commit();
				} catch (SQLException e) {
					e.printStackTrace();
					throw new OBException(e.getMessage());
				}
				
			} else if (doc_status_to.equalsIgnoreCase(voidStatus)||
					doc_status_to.equalsIgnoreCase(closedStatus)){
				//cek apakah doc status adalah CO, jika tidak maka exception
				if (!inout.getDocumentStatus().equalsIgnoreCase(completeStatus))
					throw new OBException("@ActionNotAllowedHere@");
				inout.setDocumentAction(doc_status_to);
				if (doc_status_to.equalsIgnoreCase(voidStatus))
					inout.setDocumentAction("RC"); //void dalam document routing adalah VO, tapi dalam invoice adalah RC
				OBDal.getInstance().save(inout);
				try {
					OBDal.getInstance().getConnection().commit();
				} catch (SQLException e) {
					e.printStackTrace();
					throw new OBException(e.getMessage());
				}
				
			}
			try{
				OBError oberror = doExecuteProcedureCall(inoutID, inoutProcessID);
				if (oberror.getType().equals("Error"))
					throw new OBException(oberror.getMessage());
			}
			catch (OBException e){
				//exception happen, rollback doc status
				inout.setDocumentStatus(docstatus);
				inout.setDocumentAction(docaction);
				OBDal.getInstance().save(inout);
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
			
			OBDal.getInstance().refresh(inout);
			docstatus=inout.getDocumentStatus();
			boolean processed = inout.isProcessed();
			if (processed && !docstatus.equalsIgnoreCase(completeStatus) && doc_status_to.equalsIgnoreCase(completeStatus)){
				//terproses tapi doc status masih draft
				//maka ubah docstatus menjadi complete
				inout.setDocumentStatus(doc_status_to);
				OBDal.getInstance().save(inout);
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
		// TODO semengtara null dulu
		return null;
	}
	
	@Override
	public Boolean updateDocumentStatus(Entity entity,  List<String> RecordId, String document_status_to,String column){
		if (document_status_to.equalsIgnoreCase(completeStatus)||
				document_status_to.equalsIgnoreCase(closedStatus)||
				document_status_to.equalsIgnoreCase(voidStatus))
			return true;
		else
			return super.updateDocumentStatus(entity, RecordId, document_status_to, column);
		
	}

}
