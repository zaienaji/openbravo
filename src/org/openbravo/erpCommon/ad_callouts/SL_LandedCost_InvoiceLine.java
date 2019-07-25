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
package org.openbravo.erpCommon.ad_callouts;

import java.math.BigDecimal;

import javax.servlet.ServletException;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.invoice.InvoiceLine;

public class SL_LandedCost_InvoiceLine extends SimpleCallout {

  @Override
  protected void execute(CalloutInfo info) throws ServletException {
    VariablesSecureApp vars = info.vars;
    String strInvLineId = vars.getStringParameter("inpcInvoicelineId");
    String strAmount = "";
    String strCurrencyId = "";
    if (!"".equals(strInvLineId)) {
      InvoiceLine invoiceLine = OBDal.getInstance().get(InvoiceLine.class, strInvLineId);
      strAmount = getAmount(invoiceLine);
      strCurrencyId = invoiceLine.getInvoice().getCurrency().getId();
      info.addResult("inpcCurrencyId", strCurrencyId);
    } else {
      strAmount = "0";
    }
    info.addResult("inpamount", strAmount);
  }

  private String getAmount(InvoiceLine invoiceLine) {
    BigDecimal totalAssigned = BigDecimal.ZERO;
    OBContext.setAdminMode(false);
    try {
      final StringBuilder hqlString = new StringBuilder();
      hqlString.append("select coalesce(sum(e.amount),0)");
      hqlString.append(" from LandedCostCost as e");
      hqlString.append(" where e.invoiceLine = :invoiceLine");
      final Session session = OBDal.getInstance().getSession();
      final Query<BigDecimal> query = session.createQuery(hqlString.toString(), BigDecimal.class);
      query.setParameter("invoiceLine", invoiceLine);
      totalAssigned = (BigDecimal) query.uniqueResult();

    } finally {
      OBContext.restorePreviousMode();
    }
    return invoiceLine.getLineNetAmount().subtract(totalAssigned).toString();
  }
}
