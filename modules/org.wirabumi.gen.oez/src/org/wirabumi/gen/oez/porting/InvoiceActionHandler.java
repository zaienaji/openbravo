package org.wirabumi.gen.oez.porting;

import java.sql.SQLException;
import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.invoice.Invoice;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;;

public class InvoiceActionHandler extends DocumentRoutingHandlerAction {
	private final String reactiveStatus="RE";
	private final String completeStatus="CO";
	private final String draftStatus="DR";
	private final String closedStatus="CL";
	private final String voidStatus="VO";
	private final String invoiceProcessID="111";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		//berlaku untuk doComplete, doReactive, doClose, dan doVoid
		for (String invoiceID : recordId){
			Invoice invoice = OBDal.getInstance().get(Invoice.class, invoiceID);
			String docstatus = invoice.getDocumentStatus();
			
			if (doc_status_to.equalsIgnoreCase(completeStatus)){
				
				//cek apakah dari CL atau VO, jika ya, maka exception
				if (invoice.getDocumentStatus().equalsIgnoreCase(closedStatus)||
						invoice.getDocumentStatus().equalsIgnoreCase(voidStatus))
					throw new OBException("@ActionNotAllowedHere@");
				
				//ubdah dulu docstatus menjadi DR, baru di complete
				invoice.setDocumentStatus(draftStatus);
				invoice.setDocumentAction(doc_status_to);
				
			} else if (doc_status_to.equalsIgnoreCase(reactiveStatus) || 
					doc_status_to.equalsIgnoreCase(voidStatus) ||
					doc_status_to.equalsIgnoreCase(closedStatus)){
				
				//cek apakah doc status adalah CO, jika tidak maka exception
				if (!invoice.getDocumentStatus().equalsIgnoreCase(completeStatus))
					throw new OBException("@ActionNotAllowedHere@");
				invoice.setDocumentAction(doc_status_to);
				if (doc_status_to.equalsIgnoreCase(voidStatus))
					invoice.setDocumentAction("RC"); //void dalam document routing adalah VO, tapi dalam invoice adalah RC
				
			} 
			
			OBDal.getInstance().save(invoice);
			try {
				OBDal.getInstance().getConnection().commit();
			} catch (SQLException e) {
				e.printStackTrace();
				throw new OBException(e.getMessage());
			}
			
			OBError hasil = doExecuteProcedureCall(invoiceID, invoiceProcessID);
			if (hasil.getType().equalsIgnoreCase("Error")){
				//restore to previous doc status
				invoice.setDocumentStatus(docstatus);
				OBDal.getInstance().save(invoice);
				try {
					OBDal.getInstance().getConnection().commit();
				} catch (SQLException e) {
					e.printStackTrace();
					throw new OBException(e.getMessage());
				}
				throw new OBException(hasil.getMessage());
			}
		}
		
	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// TODO Auto-generated method stub
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
