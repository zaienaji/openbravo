package org.wirabumi.gen.oez.porting;

import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.materialmgmt.transaction.ProductionTransaction;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;;

public class ProductionActionHandler extends DocumentRoutingHandlerAction {
	private final String processID="137";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		//berlaku untuk doComplete, doReactive, doClose, dan doVoid
		for (String productionID : recordId){
			doExecuteProcedureCall(productionID, processID);
			
		}
		
	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	
}
