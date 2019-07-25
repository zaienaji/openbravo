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

package org.openbravo.test.cancelandreplace.data;

import java.math.BigDecimal;

public abstract class CancelAndReplaceTestData {

  /*
   * CONSTANTS:
   */
  public final String BP_CUSTOMER_A = "4028E6C72959682B01295F40C3CB02EC";
  public final String FINAL_GOOD_A = "4028E6C72959682B01295ADC1D07022A";

  private String testNumber;
  private String testDescription;
  private String bpartnerId;
  private BigDecimal quantity;
  private BigDecimal oldOrderPreviouslyPaidAmount;
  private BigDecimal oldOrderDeliveredQuantity;

  private BigDecimal oldOrderTotalAmount;
  private BigDecimal inverseOrderTotalAmount;
  private BigDecimal newOrderTotalAmount;
  private String oldOrderStatus;
  private String inverseOrderStatus;
  private String newOrderStatus;
  private BigDecimal oldOrderReceivedPayment;
  private BigDecimal inverseOrderReceivedPayment;
  private BigDecimal newOrdeReceivedPayment;
  private BigDecimal oldOrderOutstandingPayment;
  private BigDecimal inverseOrderOutstandingPayment;
  private BigDecimal newOrderOutstandingPayment;

  private boolean activateNettingGoodsShipmentPref;
  private boolean activateAssociateNettingGoodsShipmentPref;

  private BigDecimal oldOrderLineDeliveredQuantity;
  private BigDecimal inverseOrderLineDeliveredQuantity;
  private BigDecimal newOrderLineDeliveredQuantity;

  private BigDecimal oldOrderLineShipmentLines;
  private BigDecimal inverseOrderLineShipmentLines;
  private BigDecimal newOrderLineShipmentLines;

  private String errorMessage;

  public String getBpartnerId() {
    return bpartnerId;
  }

  public void setBpartnerId(String bpartnerId) {
    this.bpartnerId = bpartnerId;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public String getTestDescription() {
    return testDescription;
  }

  public void setTestDescription(String testDescription) {
    this.testDescription = testDescription;
  }

  public String getTestNumber() {
    return testNumber;
  }

  public void setTestNumber(String testNumber) {
    this.testNumber = testNumber;
  }

  public BigDecimal getOldOrderDeliveredQuantity() {
    return oldOrderDeliveredQuantity;
  }

  public void setOldOrderDeliveredQuantity(BigDecimal oldOrderDeliveredQuantity) {
    this.oldOrderDeliveredQuantity = oldOrderDeliveredQuantity;
  }

  public BigDecimal getOldOrderTotalAmount() {
    return oldOrderTotalAmount;
  }

  public void setOldOrderTotalAmount(BigDecimal oldOrderTotalAmount) {
    this.oldOrderTotalAmount = oldOrderTotalAmount;
  }

  public BigDecimal getInverseOrderTotalAmount() {
    return inverseOrderTotalAmount;
  }

  public void setInverseOrderTotalAmount(BigDecimal inverseOrderTotalAmount) {
    this.inverseOrderTotalAmount = inverseOrderTotalAmount;
  }

  public BigDecimal getNewOrderTotalAmount() {
    return newOrderTotalAmount;
  }

  public void setNewOrderTotalAmount(BigDecimal newOrderTotalAmount) {
    this.newOrderTotalAmount = newOrderTotalAmount;
  }

  public String getOldOrderStatus() {
    return oldOrderStatus;
  }

  public void setOldOrderStatus(String oldOrderStatus) {
    this.oldOrderStatus = oldOrderStatus;
  }

  public String getInverseOrderStatus() {
    return inverseOrderStatus;
  }

  public void setInverseOrderStatus(String inverseOrderStatus) {
    this.inverseOrderStatus = inverseOrderStatus;
  }

  public String getNewOrderStatus() {
    return newOrderStatus;
  }

  public void setNewOrderStatus(String newOrderStatus) {
    this.newOrderStatus = newOrderStatus;
  }

  public BigDecimal getOldOrderReceivedPayment() {
    return oldOrderReceivedPayment;
  }

  public void setOldOrderReceivedPayment(BigDecimal oldOrderReceivedPayment) {
    this.oldOrderReceivedPayment = oldOrderReceivedPayment;
  }

  public BigDecimal getInverseOrderReceivedPayment() {
    return inverseOrderReceivedPayment;
  }

  public void setInverseOrderReceivedPayment(BigDecimal inverseOrderReceivedPayment) {
    this.inverseOrderReceivedPayment = inverseOrderReceivedPayment;
  }

  public BigDecimal getNewOrderReceivedPayment() {
    return newOrdeReceivedPayment;
  }

  public void setNewOrderReceivedPayment(BigDecimal newOrdeReceivedPayment) {
    this.newOrdeReceivedPayment = newOrdeReceivedPayment;
  }

  public BigDecimal getOldOrderOustandingPayment() {
    return oldOrderOutstandingPayment;
  }

  public void setOldOrderOutstandingPayment(BigDecimal oldOrderOutstandingPayment) {
    this.oldOrderOutstandingPayment = oldOrderOutstandingPayment;
  }

  public BigDecimal getInverseOrderOutstandingPayment() {
    return inverseOrderOutstandingPayment;
  }

  public void setInverseOrderOutstandingPayment(BigDecimal inverseOrderOutstandingPayment) {
    this.inverseOrderOutstandingPayment = inverseOrderOutstandingPayment;
  }

  public BigDecimal getNewOrderOutstandingPayment() {
    return newOrderOutstandingPayment;
  }

  public void setNewOrderOutstandingPayment(BigDecimal newOrderOutstandingPayment) {
    this.newOrderOutstandingPayment = newOrderOutstandingPayment;
  }

  public BigDecimal getOldOrderPreviouslyPaidAmount() {
    return oldOrderPreviouslyPaidAmount;
  }

  public void setOldOrderPreviouslyPaidAmount(BigDecimal olOrderPreviouslyPaidAmount) {
    this.oldOrderPreviouslyPaidAmount = olOrderPreviouslyPaidAmount;
  }

  public void setActivateNettingGoodsShipmentPref(boolean activateNettingGoodsShipmentPref) {
    this.activateNettingGoodsShipmentPref = activateNettingGoodsShipmentPref;
  }

  public boolean getActivateNettingGoodsShipmentPref() {
    return activateNettingGoodsShipmentPref;
  }

  public void setActivateAssociateNettingGoodsShipmentPref(
      boolean activateAssociateNettingGoodsShipmentPref) {
    this.activateAssociateNettingGoodsShipmentPref = activateAssociateNettingGoodsShipmentPref;
  }

  public boolean getActivateAssociateNettingGoodsShipmentPref() {
    return activateAssociateNettingGoodsShipmentPref;
  }

  public void setOldOrderLineDeliveredQuantity(BigDecimal oldOrderLineDeliveredQuantity) {
    this.oldOrderLineDeliveredQuantity = oldOrderLineDeliveredQuantity;
  }

  public BigDecimal getOldOrderLineDeliveredQuantity() {
    return oldOrderLineDeliveredQuantity;
  }

  public void setInverseOrderLineDeliveredQuantity(BigDecimal inverseOrderLineDeliveredQuantity) {
    this.inverseOrderLineDeliveredQuantity = inverseOrderLineDeliveredQuantity;
  }

  public BigDecimal getInverseOrderLineDeliveredQuantity() {
    return inverseOrderLineDeliveredQuantity;
  }

  public void setNewOrderLineDeliveredQuantity(BigDecimal newOrderLineDeliveredQuantity) {
    this.newOrderLineDeliveredQuantity = newOrderLineDeliveredQuantity;
  }

  public BigDecimal getNewOrderLineDeliveredQuantity() {
    return newOrderLineDeliveredQuantity;
  }

  public void setOldOrderLineShipmentLines(BigDecimal oldOrderLineShipmentLines) {
    this.oldOrderLineShipmentLines = oldOrderLineShipmentLines;
  }

  public BigDecimal getOldOrderLineShipmentLines() {
    return oldOrderLineShipmentLines;
  }

  public void setInverseOrderLineShipmentLines(BigDecimal inverseOrderLineShipmentLines) {
    this.inverseOrderLineShipmentLines = inverseOrderLineShipmentLines;
  }

  public BigDecimal getInverseOrderLineShipmentLines() {
    return inverseOrderLineShipmentLines;
  }

  public void setNewOrderLineShipmentLines(BigDecimal newOrderLineShipmentLines) {
    this.newOrderLineShipmentLines = newOrderLineShipmentLines;
  }

  public BigDecimal getNewOrderLineShipmentLines() {
    return newOrderLineShipmentLines;
  }

  public CancelAndReplaceTestData() {
    initialize();
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public abstract void initialize();

}
