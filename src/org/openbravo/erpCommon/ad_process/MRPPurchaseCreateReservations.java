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
 * All portions are Copyright (C) 2012-2014 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.erpCommon.ad_process;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.materialmgmt.ReservationUtils;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.materialmgmt.onhandquantity.Reservation;
import org.openbravo.model.mrp.PurchasingRun;
import org.openbravo.model.mrp.PurchasingRunLine;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.CallProcess;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DbUtility;

public class MRPPurchaseCreateReservations extends DalBaseProcess {

  // private ProcessLogger logger;

  @Override
  public void doExecute(ProcessBundle bundle) throws Exception {
    // logger = bundle.getLogger();
    Map<String, Object> params = bundle.getParams();

    String strMRPRunId = (String) params.get("MRP_Run_Purchase_ID");
    PurchasingRun mrpPurchaseRun = OBDal.getInstance().get(PurchasingRun.class, strMRPRunId);

    String strMWarehosueID = (String) params.get("mWarehouseId");

    // Execute Create Orders process.
    OBContext.setAdminMode(true);
    Process process = null;
    try {
      process = OBDal.getInstance().get(Process.class, "800163");
    } finally {
      OBContext.restorePreviousMode();
    }
    Map<String, String> createOrderParams = new HashMap<String, String>();
    createOrderParams.put("M_Warehouse_ID", strMWarehosueID);
    try {
      final ProcessInstance pinstance = CallProcess.getInstance()
          .call(process, strMRPRunId, createOrderParams);

      if (pinstance.getResult() == 0L) {
        OBDal.getInstance().rollbackAndClose();
        OBError oberror = OBMessageUtils.getProcessInstanceMessage(pinstance);
        bundle.setResult(oberror);
        return;
      }
    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      OBError messsage = OBMessageUtils
          .translateError(DbUtility.getUnderlyingSQLException(e).getMessage());
      bundle.setResult(messsage);
      return;
    }

    // Create reservations
    ScrollableResults outgoingRLs = getPRLinesOutgoing(mrpPurchaseRun);
    ScrollableResults incomingRLs = getPRLinesIncoming(mrpPurchaseRun);
    int i = 1;
    BigDecimal currentStock = BigDecimal.ZERO;

    PurchasingRunLine incomingLine = null;
    String productID = "";
    try {
      while (outgoingRLs.next()) {
        PurchasingRunLine outgoingLine = (PurchasingRunLine) outgoingRLs.get(0);
        if (!productID.equals(outgoingLine.getProduct().getId())) {
          productID = outgoingLine.getProduct().getId();
          currentStock = BigDecimal.ZERO;
        }
        BigDecimal quantity = outgoingLine.getQuantity().negate();
        boolean isSalesOrderLine = outgoingLine.getSalesOrderLine() != null
            && outgoingLine.getSalesOrderLine().getSalesOrder().isSalesTransaction();
        while (quantity.signum() == 1) {
          if (currentStock.signum() < 1 && incomingRLs.next()) {
            incomingLine = (PurchasingRunLine) incomingRLs.get(0);
            if (!productID.equals(outgoingLine.getProduct().getId()) && incomingRLs.next()) {
              incomingLine = (PurchasingRunLine) incomingRLs.get(0);
            }
            currentStock = currentStock.add(incomingLine.getQuantity());
            if (incomingLine.getTransactionType().equals("PP")
                && incomingLine.getSalesOrderLine() != null) {
              OBDal.getInstance().refresh(incomingLine.getSalesOrderLine().getSalesOrder());
              if (!incomingLine.getSalesOrderLine().getSalesOrder().isProcessed()) {
                try {
                  processOrder(incomingLine.getSalesOrderLine().getSalesOrder());
                } catch (OBException e) {
                  OBDal.getInstance().rollbackAndClose();
                  OBError error = OBMessageUtils.translateError(e.getMessage());
                  bundle.setResult(error);
                  return;
                }
              }
            }
          }
          BigDecimal consumedQuantity = currentStock.min(quantity);
          currentStock = currentStock.subtract(consumedQuantity);
          quantity = quantity.subtract(consumedQuantity);
          if (isSalesOrderLine) {
            Reservation reservation = ReservationUtils
                .getReservationFromOrder(outgoingLine.getSalesOrderLine());
            if (reservation.getReservedQty().compareTo(reservation.getQuantity()) == -1) {
              if (incomingLine.getTransactionType().equals("PP")
                  && incomingLine.getSalesOrderLine() != null) {
                ReservationUtils.reserveStockManual(reservation, incomingLine.getSalesOrderLine(),
                    consumedQuantity,
                    incomingLine.getSalesOrderLine().getSalesOrder().getWarehouse().isAllocated()
                        ? "Y"
                        : "N");
              }

              if (quantity.signum() < 1 && reservation.getRESStatus().equals("DR")) {
                ReservationUtils.processReserve(reservation, "PR");
              }
            }
            OBDal.getInstance().save(reservation);
            OBDal.getInstance().flush();
          }
        }
        if ((i % 100) == 0) {
          SessionHandler.getInstance().commitAndStart();
          OBDal.getInstance().getSession().clear();
        }
      }
    } finally {
      incomingRLs.close();
      outgoingRLs.close();
    }
    OBError message = new OBError();
    message.setType("Success");
    message.setTitle(OBMessageUtils.messageBD("Success"));
    bundle.setResult(message);
  }

  private ScrollableResults getPRLinesIncoming(PurchasingRun mrpPurchaseRun) {
    StringBuffer where = new StringBuffer();
    where.append(" where " + PurchasingRunLine.PROPERTY_PURCHASINGPLAN + ".id = :purchaserun");
    where.append("   and " + PurchasingRunLine.PROPERTY_QUANTITY + " > 0");
    where.append(" order by " + PurchasingRunLine.PROPERTY_PRODUCT + ","
        + PurchasingRunLine.PROPERTY_PLANNEDDATE + ", CASE "
        + PurchasingRunLine.PROPERTY_TRANSACTIONTYPE
        + " WHEN 'ST' THEN 0 WHEN 'MS' THEN 2 ELSE 1 END");

    OBQuery<PurchasingRunLine> soQry = OBDal.getInstance()
        .createQuery(PurchasingRunLine.class, where.toString());
    soQry.setNamedParameter("purchaserun", mrpPurchaseRun.getId());
    soQry.setFetchSize(1000);
    return soQry.scroll(ScrollMode.FORWARD_ONLY);
  }

  private ScrollableResults getPRLinesOutgoing(PurchasingRun mrpPurchaseRun) {
    StringBuffer where = new StringBuffer();
    where.append(" where " + PurchasingRunLine.PROPERTY_PURCHASINGPLAN + ".id = :purchaserun");
    where.append("   and " + PurchasingRunLine.PROPERTY_QUANTITY + " < 0");
    where.append(" order by " + PurchasingRunLine.PROPERTY_PRODUCT + ","
        + PurchasingRunLine.PROPERTY_PLANNEDDATE + ", CASE "
        + PurchasingRunLine.PROPERTY_TRANSACTIONTYPE
        + " WHEN 'ST' THEN 0 WHEN 'MS' THEN 2 ELSE 3 END");

    OBQuery<PurchasingRunLine> soQry = OBDal.getInstance()
        .createQuery(PurchasingRunLine.class, where.toString());
    soQry.setNamedParameter("purchaserun", mrpPurchaseRun.getId());
    soQry.setFetchSize(1000);
    return soQry.scroll(ScrollMode.FORWARD_ONLY);
  }

  private void processOrder(Order salesOrder) throws OBException {
    OBContext.setAdminMode(true);
    Process process = null;
    try {
      process = OBDal.getInstance().get(Process.class, "104");
    } finally {
      OBContext.restorePreviousMode();
    }
    try {
      final ProcessInstance pinstance = CallProcess.getInstance()
          .call(process, salesOrder.getId(), null);

      if (pinstance.getResult() == 0L) {
        OBError oberror = OBMessageUtils.getProcessInstanceMessage(pinstance);
        throw new OBException(oberror.getMessage());
      }
    } catch (Exception e) {
      Throwable t = DbUtility.getUnderlyingSQLException(e);
      throw new OBException(OBMessageUtils.parseTranslation(t.getMessage()), t);
    }
  }
}
