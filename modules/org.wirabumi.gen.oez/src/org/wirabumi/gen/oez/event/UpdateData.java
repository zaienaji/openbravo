package org.wirabumi.gen.oez.event;

import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.json.JSONArray;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

public class UpdateData extends BaseActionHandler {

	@Override
	protected JSONObject execute(Map<String, Object> parameters, String content) {
		
		JSONObject result = new JSONObject();
		try {
			JSONObject data = new JSONObject(content);
			JSONArray tabel = new JSONArray();
			JSONArray idRec = new JSONArray(data.getString("idrec"));
			String tabID = data.getString("tab");
			String idTable = OBDal.getInstance().get(Tab.class, tabID).getTable().getId();
			Entity entity = ModelProvider.getInstance().getEntityByTableId(idTable);
			String entityString = data.getString("entity");
			String value = data.getString("value");
			int count = 0;
			for(int i=0;i<idRec.length();i++){
				BaseOBObject Object = OBDal.getInstance().get(entity.toString(), idRec.get(i));
				Object.set(entityString, value);
				tabel.put(Object);
				count++;
			}
			result.put("hsl", tabel);
			result.put("severity", "success");
			result.put("text", count+" Data Updated");
			result.put("title", "Update Data");
			} catch (Exception e) {
			e.printStackTrace();
			}
		return result;
	}

}
