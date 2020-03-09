package org.wirabumi.gen.oez.porting;

import java.util.List;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.order.Order;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class SalesDocumentHandler extends DocumentRoutingHandlerAction {

	@Override
	public void doRouting(String adWindowId, String adTabId,
			String doc_status_to, VariablesSecureApp vars, List recordId) {
		SalesOrderPost salesOrder = new SalesOrderPost();
		try {
			Client client = OBContext.getOBContext().getCurrentClient();
			User user = OBContext.getOBContext().getUser();
			String orgID = vars.getOrg();
			Organization organization = OBDal.getInstance().get(Organization.class, orgID);
			
			if(!(doc_status_to.equals("RE") || doc_status_to.equals("XL") || doc_status_to.equals("--"))){
				initial_validation(client, user, organization, recordId, doc_status_to);
			}
			
			for(int i=0; i < recordId.size(); i=i+1){
				String id = (String) recordId.get(i);
				Order orderID = OBDal.getInstance().get(Order.class, id);
				
				if(doc_status_to.equals("CO")){
					salesOrder.Complete(client, organization, user, orderID, doc_status_to, vars);
				}else if(doc_status_to.equals("CL")){
					salesOrder.Close(orderID, doc_status_to, client, organization, user);
				}else if(doc_status_to.equals("RE")){
					//masih prosess
					salesOrder.Reactive(orderID, doc_status_to, client, user, organization);
				}else if(doc_status_to.equals("VO")){
					salesOrder.Void(orderID, user);
				}else if(doc_status_to.equals("RJ")){
					salesOrder.Reject(orderID, user);
				}else if(doc_status_to.equals("XL")){
					salesOrder.Unlock(orderID, user);
				}else if(doc_status_to.equals("--")){
					orderID.setDocumentStatus("IP");
				}
			}
		} catch (Exception e) {
			OBDal.getInstance().rollbackAndClose();
			throw new OBException(e.getMessage());
		}
	}
	
	private void initial_validation(Client client, User user, Organization organization, List recordId, 
			String doc_status_to){
		SalesOrderPost salesOrder = new SalesOrderPost();
		for(int i=0; i < recordId.size(); i=i+1){
			String id = (String) recordId.get(i);
			Order order = OBDal.getInstance().get(Order.class, id);
			salesOrder.SalesOrder(client, user, organization, order, doc_status_to);	
		}
	}

	@Override
	public String getCoDocumentNo(String recordID, Tab tab) {
		// TODO Auto-generated method stub
		return null;
	}
}
