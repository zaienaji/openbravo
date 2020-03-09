package org.wirabumi.gen.oez.event;

import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.wirabumi.gen.oez.oez_documentrouting;
public class DocumentActionList implements FieldProvider {
  private Hashtable<String, String> data = new Hashtable<String, String>();

  @Override
  public String getField(String fieldName) {
    return getData(fieldName);
  }

  public void setData(String name, String value) {
    if (name == null)
      return;
    if (this.data == null)
      this.data = new Hashtable<String, String>();
    if (value == null || value.equals(""))
      this.data.remove(name.toUpperCase());
    else
      this.data.put(name.toUpperCase(), value);
  }

  public String getData(String name) {
    return data.get(name.toUpperCase());
  }

  /**
   * @param referenceID
   * @param rolesID
   * @param windowID
   * @param tabID
   * @param docStatus
   * @return
   */
  public static DocumentActionList[] actionData(String referenceID, String rolesID,
      String windowID, String tabID, String docStatus) {
    Role role = OBDal.getInstance().get(Role.class, rolesID);
    Window window = OBDal.getInstance().get(Window.class, windowID);
    Tab tab = OBDal.getInstance().get(Tab.class, tabID);
VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
    final OBCriteria<oez_documentrouting> RoutingAction = OBDal.getInstance().createCriteria(
        oez_documentrouting.class);
    RoutingAction.add(Restrictions.eq(oez_documentrouting.PROPERTY_ROLE, role));
    RoutingAction.add(Restrictions.eq(oez_documentrouting.PROPERTY_WINDOW, window));
    RoutingAction.add(Restrictions.eq(oez_documentrouting.PROPERTY_TAB, tab));
    RoutingAction.add(Restrictions.eq(oez_documentrouting.PROPERTY_DOCUMENTSTATUS, docStatus));
    List<oez_documentrouting> RouteAction = RoutingAction.list();

    Vector<Object> vector = new Vector<Object>(0);
    String actionName = "";
	OBContext.setAdminMode(true);
    for (oez_documentrouting oez : RouteAction) {
      DocumentActionList objDocRouting = new DocumentActionList();
      String Key = oez.getDocumentAction();
 	// actionName = DocumentHandler.listName(referenceID, Key);
      Reference reference = OBDal.getInstance().get(Reference.class, referenceID);
      String lang = vars.getLanguage();
      String RefName = reference.getName();
      actionName = Utility.getListValueName(RefName, Key, lang);       
	objDocRouting.setData("ID", Key);
      objDocRouting.setData("NAME", actionName);
      vector.addElement(objDocRouting);
    }
    DocumentActionList[] objDocRoutings = new DocumentActionList[vector.size()];
    vector.copyInto(objDocRoutings);
	 OBContext.restorePreviousMode();   
 return objDocRoutings;
  }

}
