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
 * All portions are Copyright (C) 2016-2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.erpCommon.ad_callouts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.client.kernel.reference.UIDefinition;
import org.openbravo.client.kernel.reference.UIDefinitionController;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.service.json.JsonConstants;

import jdk.nashorn.api.scripting.JSObject;

/**
 * HttpServletCalloutInformationProvider provides the information that is used to populate the
 * messages, comboEntries,etc in the FIC. This information is updated by a HttpServlet Callout.
 * 
 * Evaluates response assuming it is an HTML with a JavaScript script. It assumes JavaScript engine
 * is nashorn.
 * 
 * @author inigo.sanchez
 *
 */

// Since JDK11 Nashorn is deprecated for removal. Suppressing all warnings to prevent removal
// warning when compiling with JDK11+, as we still support JDK8+ we cannot suppress just removal
// warning because when compiling with lower versions an unnecessary suppress warnings warning would
// appear.
@SuppressWarnings({ "all", "removal" })
public class HttpServletCalloutInformationProvider implements CalloutInformationProvider {
  private static final Logger log = LogManager.getLogger();

  private List<JSObject> responseElements;
  private String rawResponse;
  private String calloutName;

  private int current;
  private String currentElementName;

  // @formatter:off
  private static final String JS_EVALUATOR = 
        "(function () {\n"
      + "  %s\n"
      + "  return { calloutName: calloutName, response: respuesta };\n"
      + " }());";
  // @formatter:on

  public HttpServletCalloutInformationProvider(String rawResponse) {
    this.rawResponse = rawResponse;
  }

  @Override
  public String getCurrentElementName() {
    return currentElementName;
  }

  @Override
  public Object getCurrentElementValue(Object element) {
    return getValue(element, 1);
  }

  private Object getValue(Object element, int position) {
    return ((JSObject) element).getSlot(position);
  }

  @Override
  public Object getNextElement() {
    if (current >= responseElements.size()) {
      return null;
    }
    JSObject element = responseElements.get(current);
    current++;
    currentElementName = (String) element.getSlot(0);

    return element;
  }

  @Override
  public Boolean isComboData(Object element) {
    if (!(element instanceof JSObject)) {
      return false;
    }

    JSObject e = (JSObject) element;
    if (!e.isArray() || !e.hasSlot(1)) {
      return false;
    }

    Object ae = e.getSlot(1);
    return ae instanceof JSObject && ((JSObject) ae).isArray();
  }

  @Override
  public void manageComboData(Map<String, JSONObject> columnValues, List<String> dynamicCols,
      List<String> changedCols, RequestContext request, Object element, Column col, String colIdent)
      throws JSONException {
    JSObject subelements = (JSObject) getCurrentElementValue(element);

    JSONObject jsonobject = new JSONObject();
    ArrayList<JSONObject> comboEntries = new ArrayList<>();
    // If column is not mandatory, we add an initial blank element
    if (!col.isMandatory()) {
      JSONObject entry = new JSONObject();
      entry.put(JsonConstants.ID, (String) null);
      entry.put(JsonConstants.IDENTIFIER, (String) null);
      comboEntries.add(entry);
    }

    for (int i = 0; subelements.hasSlot(i); i++) {
      JSObject subelement = (JSObject) getValue(subelements, i);
      if (subelement == null || !subelement.hasSlot(2) || getValue(subelement, 2) == null) {
        continue;
      }

      JSONObject entry = new JSONObject();
      entry.put(JsonConstants.ID, getElementName(subelement));
      entry.put(JsonConstants.IDENTIFIER, getCurrentElementValue(subelement));
      comboEntries.add(entry);

      // If the column is mandatory, we choose the first value as selected
      // In any case, we select the one which is marked as selected "true"
      boolean selected = (i == 0 && col.isMandatory())
          || Boolean.parseBoolean(getValue(subelement, 2).toString());

      if (selected) {
        UIDefinition uiDef = UIDefinitionController.getInstance().getUIDefinition(col.getId());
        String newValue = getElementName(subelement).toString();

        jsonobject.put(CalloutConstants.VALUE, newValue);
        jsonobject.put(CalloutConstants.CLASSIC_VALUE, uiDef.convertToClassicString(newValue));
        if (request != null) {
          request.setRequestParameter(colIdent, uiDef.convertToClassicString(newValue));
        }
        log.debug("Column: {} Value: {}", col.getDBColumnName(), newValue);
      }
    }

    // If the callout returns a combo, we in any case set the new value with what
    // the callout returned
    columnValues.put(colIdent, jsonobject);

    if (dynamicCols.contains(colIdent)) {
      changedCols.add(col.getDBColumnName());
    }
    jsonobject.put(CalloutConstants.ENTRIES, new JSONArray(comboEntries));
  }

  private Object getElementName(Object element) {
    JSObject e = (JSObject) element;
    return e.getSlot(0);
  }

  /** Parses HTML response to extract callout values from its script */
  public void parseResponse() {
    String initS = "id=\"paramArray\">";
    String resp = rawResponse.substring(rawResponse.indexOf(initS) + initS.length());
    resp = resp.substring(0, resp.indexOf("</SCRIPT")).trim();
    if (!resp.contains("new Array(") && !resp.contains("[[")) {
      log.error("Couldn't evaluate callout response {}", rawResponse);
      return;
    }
    try {
      JSObject o = (JSObject) OBScriptEngine.getInstance().eval(String.format(JS_EVALUATOR, resp));
      calloutName = (String) o.getMember("calloutName");
      JSObject response = (JSObject) o.getMember("response");
      responseElements = new ArrayList<>();
      for (int i = 0; response.hasSlot(i); i++) {
        responseElements.add((JSObject) response.getSlot(i));
      }
    } catch (Exception e) {
      log.error("Couldn't parse callout response. The parsed response was: " + resp, e);
    }
  }

  public String getCalloutName() {
    return calloutName;
  }
}
