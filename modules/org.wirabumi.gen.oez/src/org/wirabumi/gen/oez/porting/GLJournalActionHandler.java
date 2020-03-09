package org.wirabumi.gen.oez.porting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openbravo.advpaymentmngt.process.FIN_AddPaymentFromJournal;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.financialmgmt.gl.GLJournal;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class GLJournalActionHandler extends DocumentRoutingHandlerAction {
	
	private final String processID="5BE14AA10165490A9ADEFB7532F7FA94"; //process ID untuk FIN_AddPaymentFromJournal
	private final String completeStatus="CO";
	private final String reactiveStatus="RE";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		
		if (doc_status_to.equalsIgnoreCase(completeStatus)){
			for (String gljournalID : recordId){
				ProcessBundle bundle = new ProcessBundle(processID, vars);
				ConnectionProvider conn = new DalConnectionProvider();
			    bundle.setConnection(conn);
			    Map<String, Object> params = new HashMap<String, Object>();
			    params.put("GL_Journal_ID", gljournalID);
			    bundle.setParams(params);
			    FIN_AddPaymentFromJournal processJournal = new FIN_AddPaymentFromJournal();
			    try {
					processJournal.execute(bundle);
					OBError oberror = (OBError) bundle.getResult();
					if (oberror.getType().equalsIgnoreCase("Error"))
						throw new OBException(oberror.getMessage());
				} catch (Exception e) {
					throw new OBException(e.getMessage());
				} 
			}
		} else if (doc_status_to.equalsIgnoreCase(reactiveStatus)){
			for (String gljournalID : recordId){
				GLJournal gljournal = OBDal.getInstance().get(GLJournal.class, gljournalID);
				if (gljournal.getPosted().equalsIgnoreCase("Y"))
					throw new OBException("@PostedDocument@");
				gljournal.setProcessed(false);
				OBDal.getInstance().save(gljournal);
			}
			OBDal.getInstance().commitAndClose();
		}
			
		
		
	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// irrelevant, do nothing
		return null;
	}

}
