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

public class CancelAndReplaceTestData7 extends CancelAndReplaceTestData {

  @Override
  public void initialize() {
    setTestNumber("CANCELREPLACE007");
    setTestDescription(
        "Cancel and Replace of a not paid Order. Leave lines and quantities as were originally. Original Order is fully delivered. Netting shipment is created");
    setBpartnerId(BP_CUSTOMER_A);
    setQuantity(new BigDecimal("2"));
    setOldOrderDeliveredQuantity(new BigDecimal("2"));
    setOldOrderPreviouslyPaidAmount(BigDecimal.ZERO);
    setOldOrderTotalAmount(new BigDecimal("4.14"));
    setInverseOrderTotalAmount(new BigDecimal("-4.14"));
    setNewOrderTotalAmount(new BigDecimal("4.14"));
    setOldOrderStatus("CL");
    setNewOrderStatus("CO");
    setInverseOrderStatus("CL");
    setOldOrderReceivedPayment(new BigDecimal("4.14"));
    setInverseOrderReceivedPayment(new BigDecimal("-4.14"));
    setNewOrderReceivedPayment(BigDecimal.ZERO);
    setOldOrderOutstandingPayment(BigDecimal.ZERO);
    setInverseOrderOutstandingPayment(BigDecimal.ZERO);
    setNewOrderOutstandingPayment(new BigDecimal("4.14"));
    setActivateNettingGoodsShipmentPref(true);
    setActivateAssociateNettingGoodsShipmentPref(false);
    setOldOrderLineDeliveredQuantity(new BigDecimal("2"));
    setInverseOrderLineDeliveredQuantity(new BigDecimal("-2"));
    setNewOrderLineDeliveredQuantity(new BigDecimal("2"));
    setOldOrderLineShipmentLines(BigDecimal.ONE);
    setInverseOrderLineShipmentLines(BigDecimal.ONE);
    setNewOrderLineShipmentLines(BigDecimal.ONE);
  }
}
