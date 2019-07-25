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
 * All portions are Copyright (C) 2018-2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.common.actionhandler.createlinesfromprocess;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.json.JsonUtils;

class CreateLinesFromUtil {
  private static final Logger log = LogManager.getLogger();

  public static final String MESSAGE = "message";
  private static final String MESSAGE_SEVERITY = "severity";
  private static final String MESSAGE_TEXT = "text";
  private static final String MESSAGE_TITLE = "title";
  private static final String MESSAGE_SUCCESS = "success";
  private static final String MESSAGE_ERROR = "error";

  static JSONObject getSuccessMessage() throws JSONException {
    JSONObject errorMessage = new JSONObject();
    errorMessage.put(MESSAGE_SEVERITY, MESSAGE_SUCCESS);
    errorMessage.put(MESSAGE_TITLE, "Success");
    errorMessage.put(MESSAGE_TEXT, OBMessageUtils.messageBD(MESSAGE_SUCCESS));
    return errorMessage;
  }

  static JSONObject getErrorMessage(final Exception e) throws JSONException {
    Throwable ex = DbUtility.getUnderlyingSQLException(e);
    String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
    JSONObject errorMessage = new JSONObject();
    errorMessage.put(MESSAGE_SEVERITY, MESSAGE_ERROR);
    errorMessage.put(MESSAGE_TITLE, "Error");
    errorMessage.put(MESSAGE_TEXT, message);
    return errorMessage;
  }

  static boolean isOrderLine(BaseOBObject line) {
    return line instanceof OrderLine;
  }

  static boolean isInOutLine(BaseOBObject line) {
    return line instanceof ShipmentInOutLine;
  }

  static Invoice getCurrentInvoice(JSONObject jsonRequest) {
    try {
      final String invoiceId = jsonRequest.getString("inpcInvoiceId");
      return OBDal.getInstance().get(Invoice.class, invoiceId);
    } catch (JSONException e) {
      log.error("Error getting the invoice.", e);
      throw new OBException(e);
    }
  }

  static JSONArray getSelectedLines(final JSONObject jsonRequest) throws JSONException {
    return jsonRequest.getJSONObject("_params").getJSONObject("grid").getJSONArray("_selection");
  }

  static BigDecimal getOrderedQuantity(JSONObject selectedPEValuesInLine) {
    try {
      return new BigDecimal(selectedPEValuesInLine.getString(
          selectedPEValuesInLine.has("orderedQuantity") ? "orderedQuantity" : "movementQuantity"));
    } catch (JSONException e) {
      log.error("Error getting the Ordered Quantity.", e);
      throw new OBException(e);
    }
  }

  static BigDecimal getOperativeQuantity(JSONObject selectedPEValuesInLine) {
    try {
      return hasNotEmptyValue(selectedPEValuesInLine, "operativeQuantity")
          ? new BigDecimal(selectedPEValuesInLine.getString("operativeQuantity"))
          : null;
    } catch (JSONException e) {
      log.error("Error getting the Operative Quantity.", e);
      throw new OBException(e);
    }
  }

  static BigDecimal getOrderQuantity(JSONObject selectedPEValuesInLine) {
    try {
      return hasNotEmptyValue(selectedPEValuesInLine, "orderQuantity")
          ? new BigDecimal(selectedPEValuesInLine.getString("orderQuantity"))
          : null;
    } catch (JSONException e) {
      log.error("Error getting the Order Quantity.", e);
      throw new OBException(e);
    }
  }

  static ShipmentInOutLine getShipmentInOutLine(JSONObject selectedPEValuesInLine) {
    ShipmentInOutLine inOutLine = null;
    try {
      if (hasNotEmptyValue(selectedPEValuesInLine, "shipmentInOutLine")) {
        inOutLine = OBDal.getInstance()
            .get(ShipmentInOutLine.class, selectedPEValuesInLine.getString("shipmentInOutLine"));
      }
    } catch (JSONException e) {
      log.error("Error getting the Shipment/Receipt.", e);
      throw new OBException(e);
    }
    return inOutLine;
  }

  static UOM getAUM(JSONObject selectedPEValuesInLine) {
    try {
      UOM aum = null;
      if (hasNotEmptyValue(selectedPEValuesInLine, "operativeUOM")) {
        aum = OBDal.getInstance().get(UOM.class, selectedPEValuesInLine.getString("operativeUOM"));
        if (aum == null) {
          OBCriteria<UOM> aumCriteria = OBDal.getInstance().createCriteria(UOM.class);
          aumCriteria.add(
              Restrictions.eq(UOM.PROPERTY_NAME, selectedPEValuesInLine.getString("operativeUOM")));
          aum = (UOM) aumCriteria.uniqueResult();
        }
      }
      return aum;
    } catch (JSONException e) {
      log.error("Error getting the Order UOM.", e);
      throw new OBException(e);
    }
  }

  static boolean isOrderLineWithRelatedShipmentReceiptLines(BaseOBObject line,
      JSONObject selectedPEValuesInLine) {
    try {
      return isOrderLine(line)
          && !((OrderLine) line).getMaterialMgmtShipmentInOutLineList().isEmpty()
          && !hasNotEmptyValue(selectedPEValuesInLine, "shipmentInOutLine");
    } catch (JSONException e) {
      log.error("Error getting is an order line and has related shipment/receipt.", e);
      throw new OBException(e);
    }
  }

  static boolean hasRelatedOrderLine(final ShipmentInOutLine inOutLine) {
    return inOutLine.getSalesOrderLine() != null;
  }

  private static boolean hasNotEmptyValue(JSONObject selectedPEValuesInLine, String propertyName)
      throws JSONException {
    return selectedPEValuesInLine.has(propertyName)
        && !JsonUtils.isValueEmpty(selectedPEValuesInLine.getString(propertyName));
  }

  @SuppressWarnings("unchecked")
  static List<InOutLineData> getRelatedNotInvoicedInOutLines(final OrderLine orderLine) {
    Boolean isSalesTransaction = orderLine.getSalesOrder().isSalesTransaction();
    StringBuilder shipmentHQLQuery = new StringBuilder(" SELECT il.id, ");
    if (isSalesTransaction) {
      shipmentHQLQuery.append(" il.movementQuantity,");
      shipmentHQLQuery.append(" il.orderQuantity,");
    } else {
      shipmentHQLQuery.append(" il.movementQuantity - sum(coalesce(mi.quantity,0)),");
      shipmentHQLQuery.append(
          " il.orderQuantity * to_number(c_divide(il.movementQuantity - sum(coalesce((mi.quantity),0)), il.movementQuantity)),");
    }
    shipmentHQLQuery.append(" il.operativeQuantity,");
    shipmentHQLQuery.append(" il.operativeUOM.id,");
    shipmentHQLQuery.append(" il.uOM.id");
    shipmentHQLQuery.append(" FROM MaterialMgmtShipmentInOutLine AS il");
    shipmentHQLQuery.append(" join il.shipmentReceipt sh");
    if (!isSalesTransaction) {
      shipmentHQLQuery.append(" left join il.procurementReceiptInvoiceMatchList mi");
    }
    shipmentHQLQuery.append(" where il.salesOrderLine.id = :orderLineId");
    shipmentHQLQuery.append("  and sh.processed = 'Y'");
    shipmentHQLQuery.append("  and sh.documentStatus in ('CO', 'CL')");
    if (isSalesTransaction) {
      shipmentHQLQuery.append("  and sh.completelyInvoiced = 'N'");
      shipmentHQLQuery.append("  and il.reinvoice = 'N'"); // IsInvoiced='N'
    } else {
      shipmentHQLQuery
          .append(" group by il.id, il.salesOrderLine.id, il.movementQuantity, il.orderQuantity,");
      shipmentHQLQuery
          .append("   il.operativeQuantity, il.operativeUOM.id, il.uOM.id, mi.id, mi.quantity");
      shipmentHQLQuery.append(" having (il.movementQuantity - sum(coalesce(mi.quantity,0)) <> 0)");
    }

    final Session session = OBDal.getInstance().getSession();
    final Query<Object[]> query = session.createQuery(shipmentHQLQuery.toString());
    query.setParameter("orderLineId", orderLine.getId());
    return getInOutLinesInformation(query.getResultList());
  }

  private static List<InOutLineData> getInOutLinesInformation(List<Object[]> inOutLinesData) {
    List<InOutLineData> inOutLines = new ArrayList<>();
    for (Object[] inOutLine : inOutLinesData) {
      inOutLines.add(new InOutLineData(inOutLine));
    }
    return inOutLines;
  }
}
