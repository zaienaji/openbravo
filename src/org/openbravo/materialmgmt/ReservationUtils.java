/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2012-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.materialmgmt;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.materialmgmt.onhandquantity.Reservation;
import org.openbravo.model.materialmgmt.onhandquantity.ReservationStock;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.service.db.CallProcess;
import org.openbravo.service.db.DalConnectionProvider;

public class ReservationUtils {
  String returnValue;
  String exito;

  public static Reservation createReserveFromSalesOrderLine(OrderLine soLine, boolean doProcess)
      throws OBException {
    if (!soLine.getSalesOrder().isSalesTransaction()) {
      throw new OBException(OBMessageUtils.messageBD("cannotReservePurchaseOrder", false));
    }
    if (soLine.getOrderedQuantity()
        .subtract(soLine.getDeliveredQuantity())
        .compareTo(BigDecimal.ZERO) == 0) {
      throw new OBException(
          OBMessageUtils.messageBD("cannotReserveDeliveredSalesOrderLine", false));
    }

    OBDal.getInstance().flush();
    CSResponse cs = null;
    try {
      cs = ReservationUtilsData.createReserveFromSalesOrderLine(
          OBDal.getInstance().getConnection(false), new DalConnectionProvider(false),
          soLine.getId(), doProcess ? "Y" : "N", OBContext.getOBContext().getUser().getId());
    } catch (ServletException e) {
    }

    if (cs != null && cs.returnValue != null) {
      return OBDal.getInstance().get(Reservation.class, cs.returnValue);
    }

    return null;
  }

  public static OBError reserveStockAuto(Reservation reservation) throws OBException {

    OBDal.getInstance().flush();
    CSResponse cs = null;
    try {
      cs = ReservationUtilsData.reserveStockAuto(OBDal.getInstance().getConnection(false),
          new DalConnectionProvider(false), reservation.getId(),
          OBContext.getOBContext().getUser().getId());
    } catch (ServletException e) {
      String message = OBMessageUtils.translateError(e.getMessage()).getMessage();
      throw new OBException(message, e);
    }

    String message = "";
    if (cs != null && cs.returnValue != null) {
      message = cs.returnValue;
    }

    OBError obmessage = new OBError();
    obmessage.setType("SUCCESS");
    obmessage.setMessage(message);
    return obmessage;
  }

  /**
   * Function to reserve in allocated or not allocated given stock or purchase order line. Available
   * OBObject are:<br>
   * - StorageDetail: reserves stock in the warehouse.<br>
   * - OrderLine: reserves stock pending to receipt purchase order line.
   */

  public static ReservationStock reserveStockManual(Reservation reservation, BaseOBObject obObject,
      BigDecimal quantity, String allocated) throws OBException {

    String strType = "";

    if (obObject instanceof OrderLine) {
      strType = "PO";
    } else if (obObject instanceof StorageDetail) {
      strType = "SD";
    } else {
      throw new OBException("notValidReservationType");
    }

    OBDal.getInstance().flush();
    CSResponse cs = null;
    try {
      cs = ReservationUtilsData.reserveStockManual(OBDal.getInstance().getConnection(false),
          new DalConnectionProvider(false), reservation.getId(), strType,
          obObject.getId().toString(), quantity.toString(),
          OBContext.getOBContext().getUser().getId(), allocated);
    } catch (ServletException e) {
      String message = OBMessageUtils.translateError(e.getMessage()).getMessage();
      throw new OBException(message, e);
    }

    if (cs != null && cs.returnValue != null) {
      return OBDal.getInstance().get(ReservationStock.class, cs.returnValue);
    }

    return null;
  }

  /**
   * Allowed actions:
   * <ul>
   * <li>PR Process</li>
   * <li>RE Reactivate</li>
   * <li>HO Put on Hold</li>
   * <li>UNHO Unhold</li>
   * <li>CL Close</li>
   * </ul>
   */
  public static OBError processReserve(Reservation reservation, String action) throws OBException {

    OBContext.setAdminMode(true);
    Process process = null;
    try {
      process = OBDal.getInstance().get(Process.class, "5A2A0AF88AF54BB085DCC52FCC9B17B7");
    } finally {
      OBContext.restorePreviousMode();
    }

    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put("RES_Action", action);

    final ProcessInstance pinstance = CallProcess.getInstance()
        .call(process, reservation.getId(), parameters);

    return OBMessageUtils.getProcessInstanceMessage(pinstance);
  }

  /**
   * Returns a non closed reservation from given Sales Order Line. If no reservation exists it
   * creates a new one in draft status.
   * 
   * @param salesOrderLine
   *          Sales Order Line owner of the reservation.
   * @return a Reservation related to the Sales Order Line
   */
  public static Reservation getReservationFromOrder(OrderLine salesOrderLine) {
    OBDal.getInstance().refresh(salesOrderLine);
    for (Reservation res : salesOrderLine.getMaterialMgmtReservationList()) {
      if (!StringUtils.equals(res.getRESStatus(), "CL")) {
        return res;
      }
    }
    return ReservationUtils.createReserveFromSalesOrderLine(salesOrderLine, false);
  }

  /**
   * Function to reallocate given reservation stock on given attributes and storage bin.
   */

  public static OBError reallocateStock(Reservation reservation, Locator storageBin,
      AttributeSetInstance asi, BigDecimal quantity) throws OBException {

    OBDal.getInstance().flush();
    CSResponse cs = null;
    try {
      cs = ReservationUtilsData.reallocateStock(OBDal.getInstance().getConnection(false),
          new DalConnectionProvider(false), reservation.getId(), storageBin.getId(), asi.getId(),
          quantity.toPlainString(), OBContext.getOBContext().getUser().getId());
    } catch (ServletException e) {
      String message = OBMessageUtils.translateError(e.getMessage()).getMessage();
      throw new OBException(message, e);
    }

    OBError result = new OBError();
    if (cs == null || StringUtils.isEmpty(cs.returnValue)) {
      throw new OBException(OBMessageUtils.messageBD("Error", false));
    }
    result.setType("Success");
    result.setMessage(OBMessageUtils.messageBD("Success", false));
    if (cs.returnValue == "0") {
      result.setType("Error");
    } else if (cs.returnValue == "2") {
      result.setType("Warning");
    }
    if (StringUtils.isNotEmpty(cs.returnValueMsg)) {
      result.setMessage(OBMessageUtils.parseTranslation(cs.returnValueMsg));
    }
    return result;
  }

  /**
   * Returns true if there are any reservations created against the given Storage Detail
   * 
   * @param storageDetail
   *          A StorageDetail object that contains the information about the Stock
   * @return true if there are Reservations created against this Stock, false otherwise
   */
  public static boolean existsReservationForStock(StorageDetail storageDetail) {
    StringBuilder hql = new StringBuilder();
    hql.append("select 1 ");
    hql.append("from MaterialMgmtReservationStock ");
    hql.append("where exists (select 1");
    hql.append("              from MaterialMgmtReservationStock rs");
    hql.append("              join rs.reservation r");
    hql.append("              where r.product = :product");
    hql.append("              and coalesce(rs.storageBin, r.storageBin) = :storageBin");
    hql.append(
        "              and coalesce(rs.attributeSetValue, r.attributeSetValue) = :attributeSetValue");
    hql.append("              and r.uOM = :uom");
    hql.append("              and rs.quantity > rs.released)");

    Query<Object> query = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), Object.class);
    query.setParameter("product", storageDetail.getProduct());
    query.setParameter("storageBin", storageDetail.getStorageBin());
    query.setParameter("attributeSetValue", storageDetail.getAttributeSetValue());
    query.setParameter("uom", storageDetail.getUOM());

    return !query.list().isEmpty();
  }

  /**
   * Returns a list of related Reservations Stock for a given Storage Detail
   * 
   * @param storageDetail
   *          A StorageDetail object that contains the information about the Stock
   * @return a list of related Reservations Stock
   */
  public static List<ReservationStock> getReservationStockFromStorageDetail(
      StorageDetail storageDetail) {
    StringBuilder hql = new StringBuilder();
    hql.append("select rs");
    hql.append(" from MaterialMgmtReservationStock rs");
    hql.append(" join rs.reservation r");
    hql.append(" where r.product = :product");
    hql.append(" and coalesce(rs.storageBin, r.storageBin) = :storageBin");
    hql.append(" and coalesce(rs.attributeSetValue, r.attributeSetValue) = :attributeSetValue");
    hql.append(" and r.uOM = :uom");
    hql.append(" and rs.quantity > rs.released");

    Query<ReservationStock> query = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), ReservationStock.class);
    query.setParameter("product", storageDetail.getProduct());
    query.setParameter("storageBin", storageDetail.getStorageBin());
    query.setParameter("attributeSetValue", storageDetail.getAttributeSetValue());
    query.setParameter("uom", storageDetail.getUOM());

    return query.list();
  }

}
