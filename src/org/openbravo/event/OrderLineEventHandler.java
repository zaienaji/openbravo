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
 * All portions are Copyright (C) 2015-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.event;

import javax.enterprise.event.Observes;

import org.hibernate.query.Query;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.order.OrderLine;

class OrderLineEventHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(OrderLine.ENTITY_NAME) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    removeSoPoReference(event);
  }

  private void removeSoPoReference(EntityDeleteEvent event) {
    try {
      OBContext.setAdminMode(true);
      final OrderLine thisLine = (OrderLine) event.getTargetInstance();
      final StringBuilder hql = new StringBuilder();
      hql.append(" update from OrderLine ol ");
      hql.append(" set ol.sOPOReference.id = null ");
      hql.append(" where ol.sOPOReference.id = :thisLine ");
      hql.append(" and ol.client.id = :clientId ");

      @SuppressWarnings("rawtypes")
      Query query = OBDal.getInstance().getSession().createQuery(hql.toString());
      query.setParameter("thisLine", thisLine.getId());
      query.setParameter("clientId", thisLine.getClient().getId());
      query.executeUpdate();
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
