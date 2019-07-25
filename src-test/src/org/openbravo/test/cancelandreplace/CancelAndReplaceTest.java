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

package org.openbravo.test.cancelandreplace;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.junit.Rule;
import org.junit.Test;
import org.openbravo.advpaymentmngt.process.FIN_AddPayment;
import org.openbravo.advpaymentmngt.process.FIN_PaymentProcess;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.test.ParameterCdiTest;
import org.openbravo.base.weld.test.ParameterCdiTestRule;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.CancelAndReplaceUtils;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentSchedule;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.service.db.CallStoredProcedure;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData1;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData10;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData11;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData2;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData3;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData4;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData5;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData6;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData7;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData8;
import org.openbravo.test.cancelandreplace.data.CancelAndReplaceTestData9;

/**
 * Tests cases to check Cancel and Replace development
 * 
 * 
 */
public class CancelAndReplaceTest extends WeldBaseTest {
  final static private Logger log = LogManager.getLogger();
  // User Openbravo
  private final String USER_ID = "100";
  // Client QA Testing
  private final String CLIENT_ID = "4028E6C72959682B01295A070852010D";
  // Organization Spain
  private final String ORGANIZATION_ID = "357947E87C284935AD1D783CF6F099A1";
  // Role QA Testing Admin
  private final String ROLE_ID = "4028E6C72959682B01295A071429011E";
  // English Language code
  private final String LANGUAGE_CODE = "en_US";
  // Sales order: 50017
  private final String SALESORDER_ID = "F1AAB8C608AA434C9FC7FC1D685BA016";
  // Goods Shipment: 500014
  private final static String M_INOUT_ID = "09658144E1AF40AC81A3E5F5C3D0F132";

  public CancelAndReplaceTest() {
  }

  public static final List<CancelAndReplaceTestData> PARAMS = Arrays.asList(
      new CancelAndReplaceTestData1(), new CancelAndReplaceTestData2(),
      new CancelAndReplaceTestData3(), new CancelAndReplaceTestData4(),
      new CancelAndReplaceTestData5(), new CancelAndReplaceTestData6(),
      new CancelAndReplaceTestData7(), new CancelAndReplaceTestData8(),
      new CancelAndReplaceTestData9(), new CancelAndReplaceTestData10(),
      new CancelAndReplaceTestData11());

  /** Defines the values the parameter will take. */
  @Rule
  public ParameterCdiTestRule<CancelAndReplaceTestData> parameterValuesRule = new ParameterCdiTestRule<CancelAndReplaceTestData>(
      PARAMS);

  /** This field will take the values defined by parameterValuesRule field. */
  private @ParameterCdiTest CancelAndReplaceTestData parameter;

  /**
   * Verifies Cancel and Replace functionality API. Clone and existing Order. Click on Cancel and
   * Replace process that will create a new Order in temporary status. Update the Order depending on
   * the test executed and finally confirm this order. Different check points have been added in
   * each stage to verify the results of the processes.
   */
  @Test
  public void CancelAndReplaceTests() {
    // Set QA context
    OBContext.setAdminMode();
    OBContext.setOBContext(USER_ID, ROLE_ID, CLIENT_ID, ORGANIZATION_ID, LANGUAGE_CODE);
    try {
      // Clone existing order to use new one in the test
      Order order = OBDal.getInstance().get(Order.class, SALESORDER_ID);
      Order oldOrder = (Order) DalUtil.copy(order, false);
      oldOrder.setDocumentNo("C&R Test " + parameter.getTestNumber());
      oldOrder.setBusinessPartner(
          OBDal.getInstance().get(BusinessPartner.class, parameter.getBpartnerId()));
      oldOrder.setSummedLineAmount(BigDecimal.ZERO);
      oldOrder.setGrandTotalAmount(BigDecimal.ZERO);
      oldOrder.setProcessed(false);
      oldOrder.setDocumentStatus("DR");
      oldOrder.setDocumentAction("CO");
      oldOrder.setCancelled(false);
      oldOrder.setId(SequenceIdData.getUUID());
      oldOrder.setNewOBObject(true);
      OBDal.getInstance().save(oldOrder);

      String oldOrderId = oldOrder.getId();

      OrderLine orderLine = order.getOrderLineList().get(0);
      OrderLine oldOrderLine = (OrderLine) DalUtil.copy(orderLine, false);
      oldOrderLine.setDeliveredQuantity(BigDecimal.ZERO);
      oldOrderLine.setInvoicedQuantity(BigDecimal.ZERO);
      oldOrderLine.setSalesOrder(oldOrder);
      oldOrderLine.setReplacedorderline(null);
      OBDal.getInstance().save(oldOrderLine);

      String oldOrderLineId = oldOrderLine.getId();

      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(oldOrder);

      // Book oldOrder
      callCOrderPost(oldOrder);

      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(oldOrder);

      // Deliver old order if the test is for fully or partially delivered orders
      if (parameter.getOldOrderDeliveredQuantity().compareTo(BigDecimal.ZERO) != 0) {
        createShipment(oldOrder, oldOrderLine, parameter);
      }

      OBDal.getInstance().refresh(oldOrderLine);

      // Pay old order if the test is for fully or partially paid orders
      if (parameter.getOldOrderPreviouslyPaidAmount().compareTo(BigDecimal.ZERO) != 0) {
        createPayment(oldOrder, parameter);
      }

      // Activate "Create netting shipment on Cancel and Replace" and
      // "Cancel and Replace - Associate shipment lines to new ticket" depending on the test

      boolean createNettingGoodsShipment = false;
      try {
        createNettingGoodsShipment = ("Y")
            .equals(Preferences.getPreferenceValue(CancelAndReplaceUtils.CREATE_NETTING_SHIPMENT,
                true, OBContext.getOBContext().getCurrentClient(),
                OBContext.getOBContext().getCurrentOrganization(),
                OBContext.getOBContext().getUser(), null, null));
      } catch (PropertyException e1) {
        createNettingGoodsShipment = false;
      }

      if (parameter.getActivateNettingGoodsShipmentPref() && !createNettingGoodsShipment) {
        Preferences.setPreferenceValue(CancelAndReplaceUtils.CREATE_NETTING_SHIPMENT, "Y", true,
            OBContext.getOBContext().getCurrentClient(),
            OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
            null, null, null);
      } else if (!parameter.getActivateNettingGoodsShipmentPref() && createNettingGoodsShipment) {
        Preferences.setPreferenceValue(CancelAndReplaceUtils.CREATE_NETTING_SHIPMENT, "N", true,
            OBContext.getOBContext().getCurrentClient(),
            OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
            null, null, null);
      }

      boolean associateShipmentToNewReceipt = false;
      try {
        associateShipmentToNewReceipt = ("Y").equals(Preferences.getPreferenceValue(
            CancelAndReplaceUtils.ASSOCIATE_SHIPMENT_TO_REPLACE_TICKET, true,
            OBContext.getOBContext().getCurrentClient(),
            OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
            null, null));
      } catch (PropertyException e1) {
        associateShipmentToNewReceipt = false;
      }

      if (parameter.getActivateAssociateNettingGoodsShipmentPref()) {
        if (!associateShipmentToNewReceipt) {
          Preferences.setPreferenceValue(CancelAndReplaceUtils.ASSOCIATE_SHIPMENT_TO_REPLACE_TICKET,
              "Y", true, OBContext.getOBContext().getCurrentClient(),
              OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
              null, null, null);
        }
      } else if (!parameter.getActivateAssociateNettingGoodsShipmentPref()
          && associateShipmentToNewReceipt) {
        Preferences.setPreferenceValue(CancelAndReplaceUtils.ASSOCIATE_SHIPMENT_TO_REPLACE_TICKET,
            "N", true, OBContext.getOBContext().getCurrentClient(),
            OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
            null, null, null);
      }

      // Create the new replacement order
      Order newOrder = CancelAndReplaceUtils.createReplacementOrder(oldOrder);
      String newOrderId = newOrder.getId();

      log.debug("New order Created:" + newOrder.getDocumentNo());
      log.debug(parameter.getTestDescription());

      // Set Quantity to the orderline of the new order in temporary Status
      orderLine = newOrder.getOrderLineList().get(0);
      orderLine.setOrderedQuantity(parameter.getQuantity());
      OBDal.getInstance().save(orderLine);

      OBDal.getInstance().flush();

      // Cancel and Replace Sales Order
      newOrder = CancelAndReplaceUtils.cancelAndReplaceOrder(newOrder.getId(), null, true);

      oldOrder = OBDal.getInstance().get(Order.class, oldOrderId);
      oldOrderLine = OBDal.getInstance().get(OrderLine.class, oldOrderLineId);
      newOrder = OBDal.getInstance().get(Order.class, newOrderId);

      Order inverseOrder = oldOrder.getOrderCancelledorderList().get(0);
      OrderLine inverseOrderLine = inverseOrder.getOrderLineList().get(0);
      OrderLine newOrderLine = newOrder.getOrderLineList().get(0);

      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(oldOrder);
      OBDal.getInstance().refresh(inverseOrder);
      OBDal.getInstance().refresh(newOrder);

      // Start data checks

      // Sales Orders Grand Total Amounts
      assertEquals(oldOrder.getGrandTotalAmount(), parameter.getOldOrderTotalAmount());
      assertEquals(inverseOrder.getGrandTotalAmount(), parameter.getInverseOrderTotalAmount());
      assertEquals(newOrder.getGrandTotalAmount(), parameter.getNewOrderTotalAmount());

      assertThat("Wrong Old Order Grand Total Amount", oldOrder.getGrandTotalAmount(),
          closeTo(parameter.getOldOrderTotalAmount(), BigDecimal.ZERO));
      assertThat("Wrong Inverse Order Grand Total Amount", inverseOrder.getGrandTotalAmount(),
          closeTo(parameter.getInverseOrderTotalAmount(), BigDecimal.ZERO));
      assertThat("Wrong New Order Grand Total Amount", newOrder.getGrandTotalAmount(),
          closeTo(parameter.getNewOrderTotalAmount(), BigDecimal.ZERO));

      // Sales Orders Statuses
      assertEquals(oldOrder.getDocumentStatus(), parameter.getOldOrderStatus());
      assertEquals(inverseOrder.getDocumentStatus(), parameter.getInverseOrderStatus());
      assertEquals(newOrder.getDocumentStatus(), parameter.getNewOrderStatus());

      // Relations between orders
      assertEquals(newOrder.getReplacedorder().getId(), oldOrder.getId());
      assertEquals(inverseOrder.getCancelledorder().getId(), oldOrder.getId());
      assertEquals(oldOrder.getReplacementorder().getId(), newOrder.getId());

      // Sales Orders Received and Outstanding payments
      FIN_PaymentSchedule paymentScheduleOldOrder = oldOrder.getFINPaymentScheduleList().get(0);
      FIN_PaymentSchedule paymentScheduleInverseOrder = inverseOrder.getFINPaymentScheduleList()
          .get(0);
      FIN_PaymentSchedule paymentScheduleNewOrder = newOrder.getFINPaymentScheduleList().get(0);

      assertThat("Wrong Old Order Paid Amount", paymentScheduleOldOrder.getPaidAmount(),
          closeTo(parameter.getOldOrderReceivedPayment(), BigDecimal.ZERO));
      assertThat("Wrong Old Order Outstanding Amount",
          paymentScheduleOldOrder.getOutstandingAmount(),
          closeTo(parameter.getOldOrderOustandingPayment(), BigDecimal.ZERO));
      assertThat("Wrong Inverse Order Paid Amount", paymentScheduleInverseOrder.getPaidAmount(),
          closeTo(parameter.getInverseOrderReceivedPayment(), BigDecimal.ZERO));
      assertThat("Wrong Inverse Order Outstanding Amount",
          paymentScheduleInverseOrder.getOutstandingAmount(),
          closeTo(parameter.getInverseOrderOutstandingPayment(), BigDecimal.ZERO));
      assertThat("Wrong New Order Paid Amount", paymentScheduleNewOrder.getPaidAmount(),
          closeTo(parameter.getNewOrderReceivedPayment(), BigDecimal.ZERO));
      assertThat("Wrong New Order Outstanding Amount",
          paymentScheduleNewOrder.getOutstandingAmount(),
          closeTo(parameter.getNewOrderOutstandingPayment(), BigDecimal.ZERO));

      // Lines delivered quantities
      assertThat("Wrong Old OrderLine delivered quantity", oldOrderLine.getDeliveredQuantity(),
          closeTo(parameter.getOldOrderLineDeliveredQuantity(), BigDecimal.ZERO));
      assertThat("Wrong Inverse OrderLine delivered quantity",
          inverseOrderLine.getDeliveredQuantity(),
          closeTo(parameter.getInverseOrderLineDeliveredQuantity(), BigDecimal.ZERO));
      assertThat("Wrong New OrderLine delivered quantity", newOrderLine.getDeliveredQuantity(),
          closeTo(parameter.getNewOrderLineDeliveredQuantity(), BigDecimal.ZERO));

      // Get Shipment lines of old order line
      OBCriteria<ShipmentInOutLine> oldOrderLineShipments = OBDal.getInstance()
          .createCriteria(ShipmentInOutLine.class);
      oldOrderLineShipments
          .add(Restrictions.eq(ShipmentInOutLine.PROPERTY_SALESORDERLINE, oldOrderLine));

      // Get Shipment lines of inverse order line
      OBCriteria<ShipmentInOutLine> inverseOrderLineShipments = OBDal.getInstance()
          .createCriteria(ShipmentInOutLine.class);
      inverseOrderLineShipments
          .add(Restrictions.eq(ShipmentInOutLine.PROPERTY_SALESORDERLINE, inverseOrderLine));

      // Get Shipment lines of new order line
      OBCriteria<ShipmentInOutLine> newOrderLineShipments = OBDal.getInstance()
          .createCriteria(ShipmentInOutLine.class);
      newOrderLineShipments
          .add(Restrictions.eq(ShipmentInOutLine.PROPERTY_SALESORDERLINE, newOrderLine));

      assertThat("Wrong Old Orderline Goods shipment lines",
          new BigDecimal(oldOrderLineShipments.list().size()),
          closeTo(parameter.getOldOrderLineShipmentLines(), BigDecimal.ZERO));
      assertThat("Wrong Inverse Orderline Goods shipment lines",
          new BigDecimal(inverseOrderLineShipments.list().size()),
          closeTo(parameter.getInverseOrderLineShipmentLines(), BigDecimal.ZERO));
      assertThat("Wrong New Orderline Goods shipment lines",
          new BigDecimal(newOrderLineShipments.list().size()),
          closeTo(parameter.getNewOrderLineShipmentLines(), BigDecimal.ZERO));

    } catch (Exception e) {
      log.error("Error when executing: " + parameter.getTestDescription(), e);
      assertFalse(true);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  protected static void callCOrderPost(Order order) throws OBException {
    final List<Object> parameters = new ArrayList<Object>();
    parameters.add(null);
    parameters.add(order.getId());
    final String procedureName = "c_order_post1";
    CallStoredProcedure.getInstance().call(procedureName, parameters, null, true, false);
  }

  protected static ShipmentInOut createShipment(Order oldOrder, OrderLine oldOrderLine,
      CancelAndReplaceTestData parameter) throws OBException {
    // Clone existing Goods Shipment to use new one in the test
    ShipmentInOut shipment = OBDal.getInstance().get(ShipmentInOut.class, M_INOUT_ID);
    ShipmentInOut oldShipment = (ShipmentInOut) DalUtil.copy(shipment, false);
    oldShipment.setDocumentNo("C&R Test " + parameter.getTestNumber());
    oldShipment.setBusinessPartner(
        OBDal.getInstance().get(BusinessPartner.class, parameter.getBpartnerId()));
    oldShipment.setId(SequenceIdData.getUUID());
    oldShipment.setNewOBObject(true);
    oldShipment.setMovementDate(new Date());
    oldShipment.setAccountingDate(new Date());
    oldShipment.setCreationDate(new Date());
    oldShipment.setUpdated(new Date());
    OBDal.getInstance().save(oldShipment);

    // Add a line to the shipment
    ShipmentInOutLine oldGoodsShipmentLine = OBProvider.getInstance().get(ShipmentInOutLine.class);
    Product prod = oldOrderLine.getProduct();
    oldGoodsShipmentLine.setOrganization(oldOrderLine.getOrganization());
    oldGoodsShipmentLine.setProduct(prod);
    oldGoodsShipmentLine.setUOM(oldOrderLine.getUOM());
    // Get first storage bin
    Locator locator1 = oldShipment.getWarehouse().getLocatorList().get(0);
    oldGoodsShipmentLine.setStorageBin(locator1);
    oldGoodsShipmentLine.setLineNo(10L);
    oldGoodsShipmentLine.setSalesOrderLine(oldOrderLine);
    oldGoodsShipmentLine.setShipmentReceipt(oldShipment);
    oldGoodsShipmentLine.setMovementQuantity(parameter.getOldOrderDeliveredQuantity());

    if (prod.getAttributeSet() != null
        && (prod.getUseAttributeSetValueAs() == null
            || !"F".equals(prod.getUseAttributeSetValueAs()))
        && prod.getAttributeSet().isRequireAtLeastOneValue()) {
      // Set fake AttributeSetInstance to transaction line for netting shipment as otherwise it
      // will return an error when the product has an attribute set and
      // "Is Required at Least One Value" property of the attribute set is "Y"
      AttributeSetInstance attr = OBProvider.getInstance().get(AttributeSetInstance.class);
      attr.setAttributeSet(prod.getAttributeSet());
      attr.setDescription("1");
      OBDal.getInstance().save(attr);
      oldGoodsShipmentLine.setAttributeSetValue(attr);
    }

    oldShipment.getMaterialMgmtShipmentInOutLineList().add(oldGoodsShipmentLine);
    OBDal.getInstance().save(oldGoodsShipmentLine);
    OBDal.getInstance().flush();

    // Book shipment
    callMINoutPost(oldShipment);
    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(oldShipment);
    return oldShipment;
  }

  protected static void callMINoutPost(ShipmentInOut shipment) throws OBException {
    final List<Object> parameters = new ArrayList<Object>();
    parameters.add(null);
    parameters.add(shipment.getId());
    final String procedureName = "m_inout_post";
    CallStoredProcedure.getInstance().call(procedureName, parameters, null, true, false);
  }

  protected static void createPayment(Order oldOrder, CancelAndReplaceTestData parameter)
      throws OBException {
    DocumentType documentType = FIN_Utility.getDocumentType(oldOrder.getOrganization(), "ARR");
    String strPaymentDocumentNo = FIN_Utility.getDocumentNo(documentType, "FIN_Payment");

    // Get the payment schedule of the order
    FIN_PaymentSchedule paymentSchedule = null;
    OBCriteria<FIN_PaymentSchedule> paymentScheduleCriteria = OBDal.getInstance()
        .createCriteria(FIN_PaymentSchedule.class);
    paymentScheduleCriteria.add(Restrictions.eq(FIN_PaymentSchedule.PROPERTY_ORDER, oldOrder));
    paymentScheduleCriteria.setMaxResults(1);
    paymentSchedule = (FIN_PaymentSchedule) paymentScheduleCriteria.uniqueResult();

    // Get the payment schedule detail of the order
    OBCriteria<FIN_PaymentScheduleDetail> paymentScheduleDetailCriteria = OBDal.getInstance()
        .createCriteria(FIN_PaymentScheduleDetail.class);
    paymentScheduleDetailCriteria.add(
        Restrictions.eq(FIN_PaymentScheduleDetail.PROPERTY_ORDERPAYMENTSCHEDULE, paymentSchedule));
    // There should be only one with null paymentDetails
    paymentScheduleDetailCriteria
        .add(Restrictions.isNull(FIN_PaymentScheduleDetail.PROPERTY_PAYMENTDETAILS));
    List<FIN_PaymentScheduleDetail> paymentScheduleDetailList = paymentScheduleDetailCriteria
        .list();

    HashMap<String, BigDecimal> paymentScheduleDetailAmount = new HashMap<String, BigDecimal>();
    String paymentScheduleDetailId = paymentScheduleDetailList.get(0).getId();
    paymentScheduleDetailAmount.put(paymentScheduleDetailId,
        parameter.getOldOrderPreviouslyPaidAmount());

    FIN_FinancialAccount financialAccount = null;

    if (oldOrder.getBusinessPartner().getAccount() != null) {
      financialAccount = oldOrder.getBusinessPartner().getAccount();
    } else {
      financialAccount = FIN_Utility
          .getFinancialAccountPaymentMethod(oldOrder.getPaymentMethod().getId(), null, true,
              oldOrder.getCurrency().getId(), oldOrder.getOrganization().getId())
          .getAccount();
    }

    // Create the payment
    FIN_Payment newPayment = FIN_AddPayment.savePayment(null, true, documentType,
        strPaymentDocumentNo, oldOrder.getBusinessPartner(), oldOrder.getPaymentMethod(),
        financialAccount, parameter.getOldOrderPreviouslyPaidAmount().toPlainString(),
        oldOrder.getOrderDate(), oldOrder.getOrganization(), null, paymentScheduleDetailList,
        paymentScheduleDetailAmount, false, false, oldOrder.getCurrency(), BigDecimal.ZERO,
        BigDecimal.ZERO);

    // Process the payment
    FIN_PaymentProcess.doProcessPayment(newPayment, "P", null, null);
  }

}
