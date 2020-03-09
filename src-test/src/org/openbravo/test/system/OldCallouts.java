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
 * All portions are Copyright (C) 2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.test.system;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_callouts.HttpServletCalloutInformationProvider;
import org.openbravo.erpCommon.ad_callouts.SimpleCallout;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.test.base.OBBaseTest;

/**
 * Tests it is possible to correctly parse response of old callouts that do not extend
 * {@link SimpleCallout} but implement Servlet instead.
 */
public class OldCallouts extends OBBaseTest {

  @Test
  public void shouldBeCorrectlyParsed() throws JSONException {
    //@formatter:off
    String calloutRawResponse = "<HTML>\n" + 
        "<HEAD>\n" + 
        "<META http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"></META>\n" + 
        "<TITLE></TITLE>\n" + 
        "<LINK rel=\"shortcut icon\" href=\"web/images/favicon.ico\" type=\"image/x-icon\"></LINK>\n" + 
        "<SCRIPT language=\"JavaScript\" type=\"text/javascript\" src=\"web/js/error.js\"></SCRIPT>\n" + 
        "<SCRIPT language=\"JavaScript\" type=\"text/javascript\" src=\"web/js/callOut.js\"></SCRIPT>\n" + 
        "<SCRIPT language=\"JavaScript\" type=\"text/javascript\" id=\"paramArray\">var calloutName='SL_Order_DocType';\n" + 
        "\n" + 
        "var respuesta = new Array(new Array(\"inpordertype\", \"SO\")\n" + 
        ", new Array(\"inpdocumentno\", \"<1000257>\")\n" + 
        ", new Array(\"inpinvoicerule\", new Array(new Array(\"D\", \"After Delivery\", \"true\"),\n" + 
        "new Array(\"O\", \"After Order Delivered\", \"false\"),\n" + 
        "new Array(\"S\", \"Customer Schedule After Delivery\", \"false\"),\n" + 
        "new Array(\"N\", \"Do Not Invoice\", \"false\"),\n" + 
        "new Array(\"I\", \"Immediate\", \"false\"))), new Array(\"inppaymentrule\", \"P\")\n" + 
        ", new Array(\"EXECUTE\", \"displayLogic();\")\n" + 
        ");</SCRIPT></HEAD>\n" + 
        "<BODY bgcolor=\"#FFFFFF\" onload=\"setgWaitingCallOut(false, '');returnResponse(respuesta, calloutName, 'appFrame');\" id=\"paramFrameName\">Call out response page</BODY></HTML>";
    //@formatter:on

    HttpServletCalloutInformationProvider calloutExecutor = new HttpServletCalloutInformationProvider(
        calloutRawResponse);
    calloutExecutor.parseResponse();
    assertThat("callout name", calloutExecutor.getCalloutName(), is("SL_Order_DocType"));

    Object element = calloutExecutor.getNextElement();
    String elementName = calloutExecutor.getCurrentElementName();
    assertThat("Element name", elementName, is("inpordertype"));
    assertThat(elementName + " value", calloutExecutor.getCurrentElementValue(element), is("SO"));

    element = calloutExecutor.getNextElement();
    elementName = calloutExecutor.getCurrentElementName();
    assertThat("Element name", elementName, is("inpdocumentno"));
    assertThat(elementName + " value", calloutExecutor.getCurrentElementValue(element),
        is("<1000257>"));

    element = calloutExecutor.getNextElement();
    elementName = calloutExecutor.getCurrentElementName();
    assertThat("Element name", elementName, is("inpinvoicerule"));

    Map<String, JSONObject> columnValues = new HashMap<>();
    List<String> dynamicCols = new ArrayList<>();

    List<String> changedCols = new ArrayList<>();

    OBContext.setAdminMode();
    try {
      Column col = OBDal.getInstance().get(Column.class, "4019");

      calloutExecutor.manageComboData(columnValues, dynamicCols, changedCols, null, element, col,
          "myColumn");
    } finally {
      OBContext.restorePreviousMode();
    }

    JSONObject colValue = columnValues.get("myColumn");
    assertThat("combo value", colValue.getString("value"), is("D"));
    JSONArray entries = colValue.getJSONArray("entries");
    assertThat("combo elements", entries.length(), is(5));

    JSONObject firstComboElement = entries.getJSONObject(0);
    assertThat("first combo entry value", firstComboElement.getString("id"), is("D"));
    assertThat("first combo entry identifier", firstComboElement.getString("_identifier"),
        is("After Delivery"));
  }
}
