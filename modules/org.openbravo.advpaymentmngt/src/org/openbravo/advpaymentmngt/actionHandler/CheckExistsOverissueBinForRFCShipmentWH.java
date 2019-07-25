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
 * All portions are Copyright (C) 2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.advpaymentmngt.actionHandler;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.common.enterprise.Locator;

public class CheckExistsOverissueBinForRFCShipmentWH extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    JSONObject result = new JSONObject();
    JSONObject errorMessage = new JSONObject();
    try {
      final JSONObject jsonData = new JSONObject(data);
      final String warehouseId = jsonData.getString("warehouseId");
      Locator overIssueBin = getOverissueBinForWarehouse(warehouseId);
      result.put("overissueBin", overIssueBin != null ? overIssueBin.getId() : "");
      result.put("storageBin$_identifier",
          overIssueBin != null ? overIssueBin.getIdentifier() : "");
    } catch (Exception e) {
      log.error("Error parsing JSON Object.", e);
      try {
        errorMessage = new JSONObject();
        errorMessage.put("severity", "error");
        errorMessage.put("title", "Error");
        errorMessage.put("text", "");
        result.put("message", errorMessage);
        result.put("overissueBin", "");
        result.put("storageBin$_identifier", "");
      } catch (Exception e2) {
        log.error("Message could not be built", e2);
      }
    }
    return result;
  }

  private Locator getOverissueBinForWarehouse(String warehouseId) {
    StringBuilder sbHQL = new StringBuilder(" as sb");
    sbHQL.append(" where sb.warehouse.id = :warehouseId");
    sbHQL.append(" and sb.inventoryStatus.overissue = true");
    OBQuery<Locator> sbQuery = OBDal.getInstance().createQuery(Locator.class, sbHQL.toString());
    sbQuery.setNamedParameter("warehouseId", warehouseId);
    sbQuery.setMaxResult(1);
    return sbQuery.uniqueResult();
  }
}
