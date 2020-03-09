package org.wirabumi.gen.oez.event;

import java.util.List;

import org.openbravo.base.model.Entity;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.model.ad.ui.Tab;

public class DefaultRoutingHandlerAction extends DocumentRoutingHandlerAction {

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List<String> recordId) {
		// do nothing, ini default routing handler action
		
	}
	@Override
	public Boolean updateDocumentStatus(Entity entity, List<String> RecordId,
			String document_status_to, String column) {
		return super.updateDocumentStatus(entity, RecordId, document_status_to, column);
	}
	@Override
	public String getCoDocumentNo(String record, Tab tab) {
		// do nothing, ini default routing handler action
		return null;
	}

}
