package org.wirabumi.gen.oez.ad_process;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.scheduling.ProcessBundle;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class PhisycalInventoryHandler extends DocumentRoutingHandlerAction {
	final String processID = "107"; 
	
	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List recordId) {
		
		Process process = OBDal.getInstance().get(Process.class, processID);
		try {
			ProcessBundle bundle = new ProcessBundle(processID, vars);
			for(int i = 0; i < recordId.size(); i++){
				String id = (String) recordId.get(i);
				InventoryCount invent = OBDal.getInstance().get(InventoryCount.class, id);
				
				if(doc_status_to.equals("CO")){
					Map<String, Object> param = new HashMap<String, Object>();
					param.put("M_Inventory_ID", invent.getId());
					
					bundle.setParams(param);
					
					InventoryCountProcess classProcess = new InventoryCountProcess();
					classProcess.execute(bundle);	
				}
				
				if(doc_status_to.equals("RE")){
						invent.setOezDocstatus("DR");
						OBDal.getInstance().save(invent);
				}
				OBDal.getInstance().commitAndClose();
			}	
		} catch (Exception e) {
			e.printStackTrace();
			OBDal.getInstance().rollbackAndClose();
			throw new OBException(e.getMessage());
		}
	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
