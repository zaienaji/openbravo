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
 * All portions are Copyright (C) 2016 Openbravo SLU 
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
import org.mozilla.javascript.NativeArray;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.client.kernel.reference.UIDefinition;
import org.openbravo.client.kernel.reference.UIDefinitionController;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.service.json.JsonConstants;

/**
 * HttpServletCalloutInformationProvider provides the information that is used to populate the
 * messages, comboEntries,etc in the FIC. This information is updated by a HttpServlet Callout.
 * 
 * @author inigo.sanchez
 *
 */
public class HttpServletCalloutInformationProvider implements CalloutInformationProvider {

  private ArrayList<NativeArray> calloutResult;
  private int current;
  private String currentElementName;

  private final Logger log = LogManager.getLogger();

  public HttpServletCalloutInformationProvider(ArrayList<NativeArray> calloutResult) {
    this.calloutResult = calloutResult;
    this.current = 0;
    this.currentElementName = "";
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
    NativeArray nativeArrayElement = (NativeArray) element;
    return nativeArrayElement.get(position, null);
  }

  @Override
  public Object getNextElement() {
    NativeArray element = null;
    if (current < calloutResult.size()) {
      element = (NativeArray) calloutResult.get(current);
      current++;
      // Update current element name
      currentElementName = (String) element.get(0, null);
    }
    return element;
  }

  @Override
  public Boolean isComboData(Object element) {
    if (element instanceof NativeArray) {
      NativeArray nativeArrayElement = (NativeArray) element;
      return nativeArrayElement.get(1, null) instanceof NativeArray;
    }
    return false;
  }

  @Override
  public void manageComboData(Map<String, JSONObject> columnValues, List<String> dynamicCols,
      List<String> changedCols, RequestContext request, Object element, Column col, String colIdent)
      throws JSONException {
    NativeArray subelements = (NativeArray) this.getCurrentElementValue(element);
    JSONObject jsonobject = new JSONObject();
    ArrayList<JSONObject> comboEntries = new ArrayList<JSONObject>();
    // If column is not mandatory, we add an initial blank element
    if (!col.isMandatory()) {
      JSONObject entry = new JSONObject();
      entry.put(JsonConstants.ID, (String) null);
      entry.put(JsonConstants.IDENTIFIER, (String) null);
      comboEntries.add(entry);
    }
    for (int j = 0; j < subelements.getLength(); j++) {
      NativeArray subelement = (NativeArray) getValue(subelements, j);
      if (subelement != null && getValue(subelement, 2) != null) {
        JSONObject entry = new JSONObject();
        entry.put(JsonConstants.ID, this.getElementName(subelement));
        entry.put(JsonConstants.IDENTIFIER, this.getCurrentElementValue(subelement));
        comboEntries.add(entry);
        if ((j == 0 && col.isMandatory())
            || getValue(subelement, 2).toString().equalsIgnoreCase("True")) {
          // If the column is mandatory, we choose the first value as selected
          // In any case, we select the one which is marked as selected "true"
          UIDefinition uiDef = UIDefinitionController.getInstance().getUIDefinition(col.getId());
          String newValue = this.getElementName(subelement).toString();

          jsonobject.put(CalloutConstants.VALUE, newValue);
          jsonobject.put(CalloutConstants.CLASSIC_VALUE, uiDef.convertToClassicString(newValue));
          request.setRequestParameter(colIdent, uiDef.convertToClassicString(newValue));
          log.debug("Column: {} Value: {}", col.getDBColumnName(), newValue);
        }
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
    NativeArray nativeArrayElement = (NativeArray) element;
    return nativeArrayElement.get(0, null);
  }
}
