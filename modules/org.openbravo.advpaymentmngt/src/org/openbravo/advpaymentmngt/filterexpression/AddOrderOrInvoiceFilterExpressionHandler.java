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
 * All portions are Copyright (C) 2014-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.advpaymentmngt.filterexpression;

import java.util.Map;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openbravo.client.application.OBBindingsConstants;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;

@RequestScoped
abstract class AddOrderOrInvoiceFilterExpressionHandler {
  private static final Logger log = LogManager.getLogger();

  @Inject
  @Any
  private Instance<AddPaymentDefaultValuesHandler> addPaymentDefaultValuesHandlers;

  protected abstract long getSeq();

  /**
   * This method gets called to obtain the default filtering values for the grid. It can be
   * overwritten using Injections.
   * 
   * @return String containing the value for the filter expression
   */
  String getFilterExpression(Map<String, String> requestMap) throws JSONException {
    return getDefaultPaymentMethod(requestMap);
  }

  protected AddPaymentDefaultValuesHandler getDefaultsHandler(String strWindowId) {
    AddPaymentDefaultValuesHandler handler = null;

    for (AddPaymentDefaultValuesHandler nextHandler : addPaymentDefaultValuesHandlers
        .select(new ComponentProvider.Selector(strWindowId))) {
      if (handler == null) {
        handler = nextHandler;
      } else if (nextHandler.getSeq() < handler.getSeq()) {
        handler = nextHandler;
      } else if (nextHandler.getSeq() == handler.getSeq()) {
        log.warn(
            "Trying to get handler for window with id {}, there are more than one instance with the same sequence",
            strWindowId);
      }
    }
    return handler;
  }

  protected String getDefaultPaymentMethod(Map<String, String> requestMap) throws JSONException {
    final String strContext = requestMap.get("context");
    JSONObject context = new JSONObject(strContext);
    final String strWindowId = context.getString(OBBindingsConstants.WINDOW_ID_PARAM);
    AddPaymentDefaultValuesHandler handler = getDefaultsHandler(strWindowId);
    String paymentMethodId = handler.getDefaultPaymentMethod(requestMap);
    if (context.has("inpfinPaymentId") && context.get("inpfinPaymentId") != JSONObject.NULL
        && StringUtils.isNotBlank(context.getString("inpfinPaymentId"))) {
      if (hasDetailsWithDifferentPaymentMethods((String) context.get("inpfinPaymentId"))) {
        return "";
      } else {
        return paymentMethodId;
      }
    }
    return paymentMethodId;
  }

  private boolean hasDetailsWithDifferentPaymentMethods(String paymentId) {
    final StringBuilder hqlString = new StringBuilder();
    hqlString.append("select coalesce(ipspm.id, opspm.id) as pm");
    hqlString.append(" from FIN_Payment_ScheduleDetail as psd");
    hqlString.append(" join psd.paymentDetails as pd");
    hqlString.append(" left join psd.orderPaymentSchedule as ops");
    hqlString.append(" left join ops.finPaymentmethod as opspm");
    hqlString.append(" left join psd.invoicePaymentSchedule as ips");
    hqlString.append(" left join ips.finPaymentmethod as ipspm");
    hqlString.append(" where pd.finPayment.id = :paymentId");
    hqlString.append(" and pd.gLItem is null");
    hqlString.append(" group by coalesce(ipspm, opspm)");

    final Session session = OBDal.getInstance().getSession();
    final Query<String> query = session.createQuery(hqlString.toString(), String.class);
    query.setParameter("paymentId", paymentId);
    FIN_Payment payment = OBDal.getInstance().get(FIN_Payment.class, paymentId);
    for (String pmId : query.list()) {
      if (!payment.getPaymentMethod().getId().equals(pmId)) {
        return true;
      }
    }
    return false;
  }
}
