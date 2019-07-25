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
 * All portions are Copyright (C) 2017-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.event;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.Query;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceDiscount;

class InvoiceEventHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = { ModelProvider.getInstance().getEntity(Invoice.ENTITY_NAME) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final Entity invoiceEntity = ModelProvider.getInstance().getEntity(Invoice.ENTITY_NAME);
    final Property businessPartnerProperty = invoiceEntity
        .getProperty(Invoice.PROPERTY_BUSINESSPARTNER);
    String invoiceId = (String) event.getTargetInstance().getId();
    String newBPId = ((BusinessPartner) event.getCurrentState(businessPartnerProperty)).getId();
    String oldBPId = ((BusinessPartner) event.getPreviousState(businessPartnerProperty)).getId();

    // Remove discount information
    if (!StringUtils.equals(newBPId, oldBPId)) {
      StringBuilder deleteHql = new StringBuilder();
      deleteHql.append(" delete from " + InvoiceDiscount.ENTITY_NAME);
      deleteHql.append(" where " + InvoiceDiscount.PROPERTY_INVOICE + ".id = :invoiceId");
      @SuppressWarnings("rawtypes")
      Query deleteQry = OBDal.getInstance().getSession().createQuery(deleteHql.toString());
      deleteQry.setParameter("invoiceId", invoiceId);
      deleteQry.executeUpdate();
    }
  }
}
