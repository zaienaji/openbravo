/*************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2008-2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************/
package org.openbravo.erpCommon.ad_process;

import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

class ApplyModulesResponse {
  private int state;
  private String statusofstate;
  private List<String> warnings;
  private List<String> errors;
  private String lastmessage;
  private String processFinished;

  public void setState(int state) {
    this.state = state;
  }

  public void setStatusofstate(String statusofstate) {
    this.statusofstate = statusofstate;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  public void setLastmessage(String lastmessage) {
    this.lastmessage = lastmessage;
  }

  public void setProcessFinished(String processFinished) {
    this.processFinished = processFinished;
  }

  String toJSON() {
    JSONObject jsonObject = new JSONObject();
    try {
      JSONObject properties = new JSONObject();
      properties.put("state", state);
      properties.put("statusofstate", statusofstate);
      if (warnings != null && !warnings.isEmpty()) {
        properties.put("warnings", new JSONArray(warnings));
      }
      if (errors != null && !errors.isEmpty()) {
        properties.put("errors", new JSONArray(errors));
      }
      properties.put("lastmessage", lastmessage);
      properties.put("processFinished", processFinished);

      jsonObject.put("Response", properties);
    } catch (JSONException ex) {
    }
    return jsonObject.toString();
  }
}
