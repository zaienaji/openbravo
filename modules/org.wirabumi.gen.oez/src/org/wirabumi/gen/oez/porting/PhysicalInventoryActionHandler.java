package org.wirabumi.gen.oez.porting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openbravo.advpaymentmngt.process.FIN_AddPaymentFromJournal;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.financialmgmt.gl.GLJournal;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class PhysicalInventoryActionHandler extends DocumentRoutingHandlerAction {
	
	private final String processID="107"; //process ID untuk M_Inventory Post
	private final String completeStatus="CO";
	private final String reactiveStatus="RE";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		
		if (doc_status_to.equalsIgnoreCase(completeStatus)){
			for (String inventoryID : recordId){
				ProcessBundle bundle = new ProcessBundle(processID, vars);
				ConnectionProvider conn = new DalConnectionProvider();
			    bundle.setConnection(conn);
			    Map<String, Object> params = new HashMap<String, Object>();
			    params.put("M_Inventory_ID", inventoryID);
			    bundle.setParams(params);
			    InventoryCountProcess processPhysicalInventory = new InventoryCountProcess();
			    try {
					processPhysicalInventory.execute(bundle);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else if (doc_status_to.equalsIgnoreCase(reactiveStatus)){
			throw new OBException("@ActionNotAllowedHere@");
		}
			
		
		
	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// irrelevant, do nothing
		return null;
	}

}
