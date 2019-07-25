/*
 *************************************************************************
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
 * All portions are Copyright (C) 2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.advpaymentmngt.actionHandler;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.domain.Preference;

public class MatchStatementOnLoadGetPreferenceActionHandler extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject jsonResponse = new JSONObject();
    try {
      OBContext.setAdminMode(true);
      StringBuffer whereClause = new StringBuffer();
      whereClause.append(" as p ");
      whereClause.append(" where p.userContact.id = :userId");
      whereClause.append("   and p.attribute = 'APRM_NoPersistInfoMessageInMatching' ");
      OBQuery<Preference> query = OBDal.getInstance()
          .createQuery(Preference.class, whereClause.toString());
      query.setNamedParameter("userId", OBContext.getOBContext().getUser().getId());
      for (Preference preference : query.list()) {
        jsonResponse.put("preference", preference.getSearchKey());
        return jsonResponse;
      }
    } catch (JSONException e) {
      log.error("Preference could not be loaded", e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return jsonResponse;
  }
}
