package org.wirabumi.gen.oez.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.wirabumi.gen.oez.oez_documentrouting;
import org.wirabumi.gen.oez.oez_documentroutingaction;

public class DocumentRoutingHandlerServer {
	public static List<String> parseJSON(JSONArray idJSON) throws JSONException {
		List<String> ids = new ArrayList<String>();
		for (int i = 0; i < idJSON.length(); i++) {
			ids.add(idJSON.getString(i));
		}
		return ids;
	}

	public static String getDocumentStatusName(String referenceID, String key) {

		String actionName = "";
		try {
			OBContext.setAdminMode(true);
			Reference reference = OBDal.getInstance().get(Reference.class,
					referenceID);
			OBCriteria<org.openbravo.model.ad.domain.List> listData = OBDal
					.getInstance().createCriteria(
							org.openbravo.model.ad.domain.List.class);
			listData.add(Restrictions.eq(
					org.openbravo.model.ad.domain.List.PROPERTY_REFERENCE,
					reference));
			listData.add(Restrictions.eq(
					org.openbravo.model.ad.domain.List.PROPERTY_SEARCHKEY, key));
			List<org.openbravo.model.ad.domain.List> theList = listData.list();
			for (org.openbravo.model.ad.domain.List lists : theList) {
				actionName = lists.getName();
			}
		} finally {
			OBContext.restorePreviousMode();
		}
		return actionName;
	}

	public static void doRouting(String windowID, String tabID, List<String> recordId,
			String doc_status_to, String doc_status_from,
			VariablesSecureApp vars) {
		Tab tab = OBDal.getInstance().get(Tab.class, tabID);
		Window window = tab.getWindow();
		String tableId = tab.getTable().getId();
		Entity entityActive = ModelProvider.getInstance().getEntityByTableId(
				tableId);

		//by default kolom yang menyimpan docstatus adalah documentStatus, jika tidak ada ambil milik OEZ yaitu oezDocstatus, jika tetap tidak ada maka exception
		String column = "documentStatus";
		Property docStatusProp = entityActive.getProperty(column, false);

		if (docStatusProp == null) {
			column = "oezDocstatus";
			docStatusProp = entityActive.getProperty(column, false);
			if (docStatusProp == null) {
				Table table = tab.getTable();
				String columnStatus = table.getOezDocstatuscolumn();
				docStatusProp = entityActive.getProperty(columnStatus, false);
				if (docStatusProp != null) {
					column = columnStatus;
				} else {
					throw new OBException(
							"Column documentStatus or oezDocstatus not found");
				}

			}
		}
		
		//instantiate document routing java class di window document routing configuration, jika tidak ada maka instantiate DefaultRoutingHandlerAction
		DocumentRoutingHandlerAction callHandler = null;
		try {
			callHandler = DocumentRoutingHandlerServer.getClassRoutingHandler(
					windowID, tabID, doc_status_to, doc_status_from);
			if (callHandler == null) {
				callHandler = new DefaultRoutingHandlerAction();
			}
		} catch (InstantiationException e) {
			throw new OBException(e.getMessage());
		} catch (IllegalAccessException e) {
			throw new OBException(e.getMessage());
		} catch (ClassNotFoundException e) {
			throw new OBException(e.getMessage());
		}
		
		// cek apakah table berhubungan dengan inventory, apabila tidak berhubungan maka tidak ada validasi pada stock reservation
		// untuk menentukan apakah table berhubungan dengan inventory, kolom em_sr_isinventory harus dicentang. 
        // temporary disabled due to thightly coupled with stock reservation module
        /*
		String isInventoryColumn = "srIsinventory";
		Table table = tab.getTable();
        OBContext.setAdminMode();
		Boolean isInventoryProp = (Boolean) table.get(isInventoryColumn);
		if (isInventoryProp != null) {
			if (isInventoryProp.booleanValue()) {
				StockReservationHandling.prosesValidation(tab.getTable(),
						entityActive, windowID, doc_status_to,
						callHandler.getMovementType(), recordId);
			}
		}
		OBContext.restorePreviousMode();
        */
		
		callHandler.doRouting(windowID, tabID, doc_status_to, vars, recordId);
		callHandler.updateDocumentStatus(entityActive, recordId, doc_status_to,
				column);
		setCoDocumentNo(recordId, entityActive, callHandler, tab, window);

	}

	public static DocumentRoutingHandlerAction getClassRoutingHandler(
			String adWindowId, String adTabId, String doc_status_to,
			String doc_status_from) throws InstantiationException,
			IllegalAccessException, ClassNotFoundException {
		DocumentRoutingHandlerAction clas = null;
		OBCriteria<oez_documentroutingaction> criteria = OBDal.getInstance()
				.createCriteria(oez_documentroutingaction.class);
		Tab tab = OBDal.getInstance().get(Tab.class, adTabId);
		Window window = OBDal.getInstance().get(Window.class, adWindowId);
		criteria.add(Restrictions.eq(oez_documentroutingaction.PROPERTY_WINDOW,
				window));
		criteria.add(Restrictions.eq(oez_documentroutingaction.PROPERTY_TAB,
				tab));
		if (doc_status_to != null) {
			criteria.add(Restrictions.eq(
					oez_documentroutingaction.PROPERTY_DOCSTATUSTO,
					doc_status_to));
		} else {
			throw new OBException("Document action shouldn't be empty");
		}

		if (criteria.list().size() > 0) {
			String callHandler = criteria.list().get(0)
					.getNavigationBarClassname();
			if (!(callHandler == null || callHandler.isEmpty())) {
				clas = (DocumentRoutingHandlerAction) Class
						.forName(callHandler).newInstance(); //
			}
		}

		return clas;
	}

	public static DocumentActionList[] actionData(String referenceID,
			String rolesID, String windowID, String tabID, String docStatus) {
		Role role = OBDal.getInstance().get(Role.class, rolesID);
		Window window = OBDal.getInstance().get(Window.class, windowID);
		Tab tab = OBDal.getInstance().get(Tab.class, tabID);
		VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
		final OBCriteria<oez_documentrouting> RoutingAction = OBDal
				.getInstance().createCriteria(oez_documentrouting.class);
		RoutingAction.add(Restrictions.eq(oez_documentrouting.PROPERTY_WINDOW,
				window));
		RoutingAction.add(Restrictions
				.eq(oez_documentrouting.PROPERTY_TAB, tab));
		RoutingAction.add(Restrictions.eq(
				oez_documentrouting.PROPERTY_DOCUMENTSTATUS, docStatus));
		RoutingAction.add(Restrictions.eq(oez_documentrouting.PROPERTY_ROLE,
				role));
		List<oez_documentrouting> RouteAction = RoutingAction.list();
		Vector<Object> vector = new Vector<Object>(0);
		String actionName = "";
		OBContext.setAdminMode(true);
		for (oez_documentrouting oez : RouteAction) {
			DocumentActionList objDocRouting = new DocumentActionList();
			String Key = oez.getDocumentAction();
			Reference reference = OBDal.getInstance().get(Reference.class,
					referenceID);
			String lang = vars.getLanguage();
			String RefName = reference.getName();
			actionName = Utility.getListValueName(RefName, Key, lang);
			objDocRouting.setData("ID", Key);
			objDocRouting.setData("NAME", actionName);
			vector.addElement(objDocRouting);
		}
		DocumentActionList[] objDocRoutings = new DocumentActionList[vector
				.size()];
		vector.copyInto(objDocRoutings);
		OBContext.restorePreviousMode();
		return objDocRoutings;

	}

	private static void setCoDocumentNo(List<String> recordId, Entity entityActive,
			DocumentRoutingHandlerAction callHandler, Tab tab, Window window) {
		String column = "oezCodocumentno";
		Property docStatusProp = entityActive.getProperty(column, false);
		if(docStatusProp==null){
			return;
		}
		for (Object recordID : recordId) {			
			String documentNo = callHandler.getCoDocumentNo(
					recordID.toString(), tab);
			
			BaseOBObject obObject = OBDal.getInstance().get(
					entityActive.toString(), recordID.toString());
			obObject.set(docStatusProp.getName(), documentNo);
			OBDal.getInstance().save(obObject);
			OBDal.getInstance().flush();
		}
		OBDal.getInstance().commitAndClose();
	}

}
