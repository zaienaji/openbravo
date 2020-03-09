package org.wirabumi.gen.oez.event;

import java.util.List;

import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

public class ActivateDocumentHandlerAction extends DocumentRoutingHandlerAction {
	private final String active="active";
	private final String complete="co";
	private final String reactive="re";

  @Override
  public void doRouting(String adWindowId, String adTabId, String doc_status_to,
      VariablesSecureApp vars, List<String> recordId) {
	  //dari tabId dapatkan tableID
	  Tab tab = OBDal.getInstance().get(Tab.class, adTabId);
	  String tableId = tab.getTable().getId();
	  Entity entity = ModelProvider.getInstance().getEntityByTableId(
				tableId);
    // doc status to=CO maka activate
    if (doc_status_to.equalsIgnoreCase(complete)) {
      for (int i = 0; i < recordId.size(); i++) {
        BaseOBObject objek = OBDal.getInstance().get(entity.toString(), recordId.get(i));
        objek.set(active, true);
        OBDal.getInstance().save(objek);
      }
      try {
        OBDal.getInstance().commitAndClose();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (doc_status_to.equalsIgnoreCase(reactive)) {
        for (int i = 0; i < recordId.size(); i++) {
          BaseOBObject objek = OBDal.getInstance().get(entity.toString(), recordId.get(i));
          objek.set(active, false);
          OBDal.getInstance().save(objek);
        }
        try {
          OBDal.getInstance().commitAndClose();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
  }

  @Override
  public Boolean updateDocumentStatus(Entity entity, List<String> RecordId, String document_status_to,
      String column) {
    return super.updateDocumentStatus(entity, RecordId, document_status_to, column);
  }

  @Override
  public String getCoDocumentNo(String record, Tab tab) {
    // do nothing, cuma activate/deactivate pada kolom isactive
    return null;
  }

}
