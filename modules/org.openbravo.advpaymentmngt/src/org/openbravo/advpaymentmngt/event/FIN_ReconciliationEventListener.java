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
 * All portions are Copyright (C) 2016-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.advpaymentmngt.event;

import java.math.BigDecimal;

import javax.enterprise.event.Observes;

import org.hibernate.query.Query;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.financialmgmt.payment.FIN_Reconciliation;

class FIN_ReconciliationEventListener extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(FIN_Reconciliation.ENTITY_NAME) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    FIN_Reconciliation rec = OBDal.getInstance()
        .get(FIN_Reconciliation.class, event.getTargetInstance().getId());
    if (!rec.isProcessed()) {
      updateNextReconciliationsBalance(rec);
    }
  }

  /**
   * Update starting balance and ending balance of subsequent reconciliations when one
   * reconciliation is deleted
   * 
   * @param rec
   *          Reconciliation being deleted
   */
  private void updateNextReconciliationsBalance(final FIN_Reconciliation rec) {
    BigDecimal balance = rec.getEndingBalance().subtract(rec.getStartingbalance());
    StringBuilder update = new StringBuilder();
    update.append(" update " + FIN_Reconciliation.ENTITY_NAME);
    update.append(" set " + FIN_Reconciliation.PROPERTY_STARTINGBALANCE + " = "
        + FIN_Reconciliation.PROPERTY_STARTINGBALANCE + " - :balance");
    update.append(" , " + FIN_Reconciliation.PROPERTY_ENDINGBALANCE + " = "
        + FIN_Reconciliation.PROPERTY_ENDINGBALANCE + " - :balance");
    update.append(" where " + FIN_Reconciliation.PROPERTY_ACCOUNT + ".id = :accountId");
    update.append(" and " + FIN_Reconciliation.PROPERTY_TRANSACTIONDATE + " > :date");
    @SuppressWarnings("rawtypes")
    Query updateQry = OBDal.getInstance().getSession().createQuery(update.toString());
    updateQry.setParameter("balance", balance);
    updateQry.setParameter("accountId", rec.getAccount().getId());
    updateQry.setParameter("date", rec.getTransactionDate());
    updateQry.executeUpdate();
  }
}
