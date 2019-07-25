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
package org.openbravo.service.importprocess;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.service.db.DbUtility;

/**
 * Utility methods used in the import process.
 * 
 * @author mtaal
 */
public class ImportProcessUtils {

  /**
   * Returns true if the import.disable.process property is set to true, false otherwise
   */
  public static boolean isImportProcessDisabled() {
    return OBPropertiesProvider.getInstance().getBooleanProperty("import.disable.process");
  }

  public static List<String> getOrderedTypesOfData() {
    final Reference reference = OBDal.getInstance()
        .get(Reference.class, "11F86B630ECB4A57B28927193F8AB99D");

    List<org.openbravo.model.ad.domain.List> refList = new ArrayList<org.openbravo.model.ad.domain.List>(
        reference.getADListList());
    Collections.sort(refList, new Comparator<org.openbravo.model.ad.domain.List>() {

      @Override
      public int compare(org.openbravo.model.ad.domain.List o1,
          org.openbravo.model.ad.domain.List o2) {
        Long l1 = o1.getSequenceNumber();
        Long l2 = o2.getSequenceNumber();
        if (l1 == null) {
          l1 = 50L;
        }
        if (l2 == null) {
          l2 = 50L;
        }
        return l1.compareTo(l2);
      }
    });

    final List<String> typesOfData = new ArrayList<String>();
    for (org.openbravo.model.ad.domain.List ref : refList) {
      typesOfData.add(ref.getSearchKey());
    }

    return typesOfData;
  }

  // get a property but prevent someone from putting a crazy value in properties
  public static int getCheckIntProperty(Logger log, String property, int defaultValue,
      int minValue) {
    final int value = ImportProcessUtils.getIntOpenbravoProperty(property, defaultValue);
    if (value < minValue) {
      log.warn("Value of property " + property + " is set too low (" + value
          + "), using valid minValue instead " + minValue);
      return minValue;
    }
    return value;
  }

  public static int getIntOpenbravoProperty(String propName, int defaultValue) {
    final String val = OBPropertiesProvider.getInstance()
        .getOpenbravoProperties()
        .getProperty(propName);
    if (val == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException ignore) {
      return defaultValue;
    }
  }

  public static void logError(Logger log, Throwable t) {
    Throwable toReport = DbUtility.getUnderlyingSQLException(t);
    log.error(toReport.getMessage(), toReport);
  }

  public static String getErrorMessage(Throwable e) {
    StringWriter sb = new StringWriter();

    PrintWriter pw = new PrintWriter(sb);

    e.printStackTrace(pw);
    Throwable foundCause = DbUtility.getUnderlyingSQLException(e);
    if (e != foundCause) {
      pw.write("\n >>>> Next Exception:\n");
      foundCause.printStackTrace(pw);
    }

    return sb.toString();
  }

  /**
   * Data send from clients can contain a single data element or be an array. If it is an array then
   * the first entry in the array is used to find the value of the property.
   * 
   * If the property can not be found then null is returned.
   */
  public static String getJSONProperty(JSONObject jsonObject, String property) {
    try {
      if (jsonObject.has(property)) {
        return jsonObject.getString(property);
      }
      if (!jsonObject.has("data")) {
        return null;
      }

      Object jsonData = jsonObject.get("data");
      JSONObject jsonContent = null;
      if (jsonData instanceof JSONObject) {
        jsonContent = (JSONObject) jsonData;
      } else if (jsonData instanceof String) {
        jsonContent = new JSONObject((String) jsonData);
      } else if (jsonData instanceof JSONArray) {
        final JSONArray jsonArray = (JSONArray) jsonData;
        if (jsonArray.length() > 0) {
          jsonContent = jsonArray.getJSONObject(0);
        }
      }
      if (jsonContent != null && jsonContent.has(property)) {
        return jsonContent.getString(property);
      }
      return null;
    } catch (JSONException e) {
      throw new OBException(e);
    }

  }
}
