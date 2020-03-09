package org.wirabumi.gen.oez.event;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.db.DbUtility;

public class DocumentRoutingHandler extends BaseActionHandler {
	  final static Logger log4jDocRouting = Logger.getLogger(DocumentRoutingHandler.class);
	  final String referenceId = "135"; //id untuk ad_reference All_Document Action

	  ConnectionProvider connectionProvider = new DalConnectionProvider();

	  @Override
	  protected JSONObject execute(Map<String, Object> parameters, String content) {
	    JSONObject response = new JSONObject();
	    HttpServletRequest request = RequestContext.get().getRequest();
	    VariablesSecureApp vars = new VariablesSecureApp(request);
	    try {
	      final JSONObject jsonData = new JSONObject(content);
	      final String action = jsonData.getString("action");
	      final String adTabId = jsonData.getString("adTabId");
	      final String windowId = jsonData.getString("windowId");
	      String doc_status_from = jsonData.getString("doc_status_from");
	      final JSONArray RecordList = jsonData.getJSONArray("recordIdList");
	      List<String> recordIdList=DocumentRoutingHandlerServer.parseJSON(RecordList);
	      
	      // cek apakah dalam record yang dicentang ada perbedaan docSatus, jika Ya maka exception
	      OBContext.setAdminMode();
	      doc_status_from=getCurrentStatus(windowId, adTabId, referenceId, recordIdList);
	      if(doc_status_from==null || doc_status_from.equals("")){
	    	  String message = Utility.parseTranslation(connectionProvider, vars, vars.getLanguage(),
	                  "@OEZ_MultipleDocStatus@");
	              throw new Exception(message);
	      }
	      
	      //set docAction, jika tidak terdefinisi di role | document routing, maka exception karena dianggap tidak memiliki otorisasi
	      String doc_status_to=null;
	      if(!action.equals("OpenPopupParamater")){
	    	  doc_status_to=action;
	      }
	      if (action.equals("OpenPopupParamater")) {
	    	  JSONObject comboItem=getActionComboBox(windowId, adTabId,referenceId,  recordIdList, vars,doc_status_from);
    		  if (comboItem!=null) {
	            response.put("actionComboBox", comboItem);
	          } else {
	        	  String message = Utility.parseTranslation(connectionProvider, vars, vars.getLanguage(),
	                      "@oez_documentapprovaldenied@ ");
	            throw new Exception(message);
	          }
	      } else {
	    	  DocumentRoutingHandlerServer.doRouting(windowId, adTabId, recordIdList, doc_status_to, doc_status_from, vars);
	    	  String actionName = DocumentRoutingHandlerServer.getDocumentStatusName(referenceId, action);
		      String message = Utility.parseTranslation(connectionProvider, vars, vars.getLanguage(),RecordList.length() + " @RowsUpdated@ " + actionName);
		      JSONObject Message = new JSONObject();
		      Message.put("severity", "success");
		      Message.put("text", message);
		      response.put("message", Message);
		      return response;
	      }
	      OBContext.restorePreviousMode();
	    } catch (Exception e) {
	      OBDal.getInstance().rollbackAndClose();
	      log4jDocRouting.error("Document Action Failed: " + e.getMessage(), e);
	      Throwable ex = DbUtility.getUnderlyingSQLException(e);
	      String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
	      try {
	        JSONObject errorMessage = new JSONObject();
	        errorMessage.put("severity", "error");
	        errorMessage.put("text", message);
	        response.put("message", errorMessage);
	      } catch (JSONException ignore) {
	    	  e.printStackTrace();
	      }
	    }
	    return response;
	  }

	  private static String getCurrentStatus(String windowID, String tabID, String referenceID, List<String> recordId) throws Exception{
		  	log4jDocRouting.debug("start getCurrentStatus");
		  	Tab tab=OBDal.getInstance().get(Tab.class, tabID);
		    Entity entityActive = ModelProvider.getInstance().getEntityByTableId(tab.getTable().getId());
	        Property docStatusProp=null;
	        String column="documentStatus";
	        log4jDocRouting.debug("get value from documentStatus column");
	        docStatusProp = entityActive.getProperty(column, false);
	        if(docStatusProp==null)
	        {
	        	log4jDocRouting.debug("get value from oezDocStatus column");
	        	column="oezDocstatus";
	        	docStatusProp=entityActive.getProperty(column, false);	
	        }	        
	        if(docStatusProp==null){
	        	log4jDocRouting.debug("get value from user defined document status column");
	        	Table table = tab.getTable();
				String columnStatus = table.getOezDocstatuscolumn();
				docStatusProp = entityActive.getProperty(columnStatus, false);
				if(docStatusProp!=null){
					column=columnStatus;
				}else{
					throw new Exception("Column documentStatus or oezDocstatus not found");	
				}
	        	
	        }
	        BaseOBObject obObject = OBDal.getInstance().get(entityActive.toString(), recordId.get(0));	         
	        String currentStatus= (String)obObject.get(column);
	        if(recordId.size()>1){
	        	for(int i=1;i<recordId.size();i++){
		        	BaseOBObject ob= OBDal.getInstance().get(entityActive.toString(), recordId.get(i));
		        	String tm= (String)ob.get(column);
		        	if(!tm.equalsIgnoreCase(currentStatus)){
		        		return null;
		        	}
			        
		        }	
	        }
	        
	        return currentStatus;
	  }
	  private static JSONObject getActionComboBox(String windowID, String tabID, String referenceID, List<String> recordId, 
			  VariablesSecureApp vars,String currentStatus) throws Exception {
		    OBContext.setAdminMode();
		    String defaultValue = null;
		    JSONObject response = null;
		    String roleID = vars.getRole();		    
		    DocumentActionList[] docHandlerAcct = DocumentRoutingHandlerServer.actionData(referenceID, roleID,windowID, tabID, currentStatus);		    
		    if(docHandlerAcct.length>0){
		    	JSONObject valueMap = new JSONObject();
		    	response=new JSONObject();
		    	for (DocumentActionList DocRouteAction : docHandlerAcct) {
			      String key = DocRouteAction.getField("ID");
			      String Name = DocRouteAction.getField("NAME");
			      valueMap.put(key, Name);
			    }
			    response.put("valueMap", valueMap);
			    response.put("defaultValue", defaultValue);
		    }		    
		    OBContext.restorePreviousMode();
		    return response;
		  }
	  
	}
