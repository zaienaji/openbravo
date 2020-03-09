package org.wirabumi.gen.oez.event;

import java.util.List;

import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.db.CallProcess;

public abstract class DocumentRoutingHandlerAction  {
	protected Logger log4j = Logger.getLogger(this.getClass());
	protected String adWindowId;
	protected String adTabId;
	protected String doc_status_to;
	protected String adRoleId;
	protected VariablesSecureApp vars;
	protected List<String> recordIdList;
	public String getMovementType(){return null;};
	public abstract void doRouting(String adWindowId, String adTabId,String doc_status_to,VariablesSecureApp vars, List<String> recordId);
	public Boolean updateDocumentStatus(Entity entity,  List<String> RecordId, String document_status_to,String column) {	      
		try {
			for (int i=0;i<RecordId.size();i++) {
		        BaseOBObject obObject = OBDal.getInstance().get(entity.toString(), RecordId.get(i));
		        if (document_status_to.equals("RE")) {			        	
		          obObject.set(column, "DR");
		        } else {
		          obObject.set(column, document_status_to);
		        }
		        OBDal.getInstance().save(obObject);
		      }
		      OBDal.getInstance().commitAndClose();	
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
    return true;
  }
	
  public abstract String getCoDocumentNo(String recordID, Tab tab);
  
  public OBError doExecuteProcedureCall(String recordID, String processID){
	    OBError oberror = new OBError();
	    oberror.setType("Success");
	    oberror.setTitle("Success");
		OBContext.setAdminMode();
		final Process process = OBDal.getInstance().get(Process.class, processID);
		log4j.debug("execute procedure call "+process.getName());
		final ProcessInstance pInstance = CallProcess.getInstance().call(process, recordID, null);
		long result = pInstance.getResult();
		if (result==0){
			String errormessage = pInstance.getErrorMsg();
			log4j.debug("error message "+errormessage);
			oberror.setType("Error");
			oberror.setTitle("Error");
			oberror.setMessage(errormessage);
		}
		
		OBContext.restorePreviousMode();
		return oberror;
	}
  
}
