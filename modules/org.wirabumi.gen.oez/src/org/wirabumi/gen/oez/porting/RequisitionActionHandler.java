package org.wirabumi.gen.oez.porting;

import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.procurement.Requisition;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class RequisitionActionHandler extends DocumentRoutingHandlerAction {
	private final String reactiveStatus="RE";
	private final String completeStatus="CO";
	private final String draftStatus="DR";
	private final String closedStatus="CL";
	private final String voidStatus="VO";
	private final String processID="1004400003";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		if (doc_status_to.equalsIgnoreCase(voidStatus))
			throw new OBException("@ActionNotAllowedHere@"); //requisition tidak boleh di void
		
		for (String requisitionID : recordId){
			if (doc_status_to.equalsIgnoreCase(completeStatus)){
				Requisition requisition = OBDal.getInstance().get(Requisition.class, requisitionID);
				//cek apakah dari CL atau VO, jika ya, maka exception
				if (requisition.getDocumentStatus().equalsIgnoreCase(closedStatus)||
						requisition.getDocumentStatus().equalsIgnoreCase(voidStatus))
					throw new OBException("@ActionNotAllowedHere@");
				
				//ubdah dulu docstatus menjadi DR, baru di complete
				requisition.setDocumentStatus(draftStatus);
				requisition.setDocumentAction(doc_status_to);
				OBDal.getInstance().save(requisition);
				OBDal.getInstance().flush();
				
			} else if (doc_status_to.equalsIgnoreCase(reactiveStatus)){
				//cek apakah doc status adalah CO, jika tidak maka exception
				Requisition requisition = OBDal.getInstance().get(Requisition.class, requisitionID);
				if (!requisition.getDocumentStatus().equalsIgnoreCase(completeStatus))
					throw new OBException("@ActionNotAllowedHere@");
				requisition.setDocumentAction(doc_status_to);
				OBDal.getInstance().save(requisition);
				OBDal.getInstance().flush();
				
			} 
			doExecuteProcedureCall(requisitionID, processID);
			
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
				document_status_to.equalsIgnoreCase(closedStatus))
			return true;
		else
			return super.updateDocumentStatus(entity, RecordId, document_status_to, column);
		
	}

}
