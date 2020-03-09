package org.wirabumi.gen.oez.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

public class DocRoutingToolbarHandler extends BaseActionHandler{
	public static final String MODE_CHECK_WINDOW="checkWindowType";
	public static final String MODE_POPUP_PARAMETER="OpenPopupParamater";
	public static final String REFERENCE_ID="135";
	private JSONObject checkWindowType(String adTabId) throws JSONException{
		JSONObject hsl = new JSONObject();
		Tab tab=null;
		Window window=null;
		int result=-1;
		String msg="";			
		String tabid=adTabId;
		if(!tabid.isEmpty() && tabid!=""){
			tab=OBDal.getInstance().get(Tab.class,tabid);
			if(tab!=null){
				window=tab.getWindow();
				if(window==null){
					result=0;
					msg="Window from tab_id ("+tabid+") is null";	
				}else{
					result=1;
					msg="";
				}
			}else{
				msg="Tab with tab_id("+tabid+") not found.";	
			}				
		}else{
			msg="Tab_id is null.";
		}
		hsl.put("enable", result);
		hsl.put("message", msg);
		return hsl;
	}
	private JSONObject getComboBoxAction(String adTabId, String doc_status_from) throws JSONException{		
		JSONObject response = new JSONObject();		
		String defaultValue=null;
		List<String> list=new ArrayList<String>(); 
				//DocumentRoutingServer.getParameterAction(adTabId, doc_status_from);
		JSONObject valueMap = new JSONObject();		
	    for (int i=0;i<list.size();i++) {
	      String key = list.get(i);
	      String Name = key;
	      valueMap.put(key, Name);
	    }		
		response.put("valueMap", valueMap);
	    response.put("defaultValue", defaultValue);
		return response;
	}
	@Override
	protected JSONObject execute(Map<String, Object> parameters, String content) {
		JSONObject hsl = new JSONObject();
		OBContext.setAdminMode();
		try {
			JSONObject isi = new JSONObject(content);
			JSONArray tm1= new JSONArray(isi.getString("action").toString());			
			String mode=tm1.getString(0);
			JSONArray tm2= new JSONArray(isi.getString("tabid").toString());
			JSONArray tm3= new JSONArray(isi.getString("docStatusFrom").toString());
			String doc_status_from=tm3.getString(0);
			String adTabId=tm2.getString(0);
		
			if(mode.compareToIgnoreCase(MODE_CHECK_WINDOW)==0){
				hsl=checkWindowType(adTabId);
			}else if(mode.compareToIgnoreCase(MODE_POPUP_PARAMETER)==0){
				hsl.put("actionComboBox", getComboBoxAction(adTabId, doc_status_from)) ;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		OBContext.restorePreviousMode();
		return hsl;
	}
	
}
