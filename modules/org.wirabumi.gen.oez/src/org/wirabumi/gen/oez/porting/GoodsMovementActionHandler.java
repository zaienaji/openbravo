package org.wirabumi.gen.oez.porting;

import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.model.ad.ui.Tab;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class GoodsMovementActionHandler extends DocumentRoutingHandlerAction {
	private final String reactiveStatus="RE";
	private final String inoutProcessID="122";

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		if (doc_status_to.equalsIgnoreCase(reactiveStatus))
			throw new OBException("@ActionNotAllowedHere@"); //shipment inout tidak boleh di reactive
		
		for (String goodsMovementID : recordId){
			doExecuteProcedureCall(goodsMovementID, inoutProcessID);
			
		}

	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// TODO semengtara null dulu
		return null;
	}
	
}
