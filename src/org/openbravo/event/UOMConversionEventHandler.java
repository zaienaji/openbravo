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
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.common.uom.UOMConversion;

class UOMConversionEventHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(UOMConversion.ENTITY_NAME) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onNew(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    // Check if exists another record using this uomFrom - uomTo
    final UOMConversion uomConversion = (UOMConversion) event.getTargetInstance();
    if (existsRecord(uomConversion.getClient(), uomConversion.getUOM(), uomConversion.getToUOM())) {
      throw new OBException(String.format(OBMessageUtils.messageBD("CannotInsertUOMConversion"),
          uomConversion.getUOM().getName(), uomConversion.getToUOM().getName()));
    }
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final Entity entity = ModelProvider.getInstance().getEntity(UOMConversion.class);
    final Property uom = entity.getProperty(UOMConversion.PROPERTY_TOUOM);
    final Property active = entity.getProperty(UOMConversion.PROPERTY_ACTIVE);
    final Property multipleBy = entity.getProperty(UOMConversion.PROPERTY_MULTIPLERATEBY);
    final Property divideBy = entity.getProperty(UOMConversion.PROPERTY_DIVIDERATEBY);

    // Check if ToUOM has changed
    if (!event.getCurrentState(uom).equals(event.getPreviousState(uom))) {
      final UOMConversion uomConversion = (UOMConversion) event.getTargetInstance();

      // Check if exists another record using this uomFrom - uomTo
      if (existsRecord(uomConversion.getClient(), uomConversion.getUOM(),
          uomConversion.getToUOM())) {
        throw new OBException(String.format(OBMessageUtils.messageBD("CannotInsertUOMConversion"),
            uomConversion.getUOM().getName(), uomConversion.getToUOM().getName()));
      }
    }

    // Check if Active has changed
    else if (!event.getCurrentState(active).equals(event.getPreviousState(active))) {
      final UOMConversion uomConversion = (UOMConversion) event.getTargetInstance();

      // If changing from inactive to active, check if exists another record using this uomFrom -
      // uomTo
      if (uomConversion.isActive() && existsRecord(uomConversion.getClient(),
          uomConversion.getUOM(), uomConversion.getToUOM())) {
        throw new OBException(String.format(OBMessageUtils.messageBD("CannotInsertUOMConversion"),
            uomConversion.getUOM().getName(), uomConversion.getToUOM().getName()));
      }
    }

    // Check if MultipleRateBy or DivideRateBy have changed
    else if (!event.getCurrentState(multipleBy).equals(event.getPreviousState(multipleBy))
        || !event.getCurrentState(divideBy).equals(event.getPreviousState(divideBy))) {
      throw new OBException(OBMessageUtils.messageBD("CannotUpdateUOMConversion"));
    }
  }

  // Check if exists another record using this uomFrom - uomTo
  private boolean existsRecord(Client client, UOM uomFrom, UOM uomTo) {
    StringBuilder hql = new StringBuilder();
    hql.append("SELECT t1." + UOMConversion.PROPERTY_ID + " ");
    hql.append("FROM " + UOMConversion.ENTITY_NAME + " as t1 ");
    hql.append("WHERE t1." + UOMConversion.PROPERTY_UOM + " = :uomFrom ");
    hql.append("AND t1." + UOMConversion.PROPERTY_TOUOM + " = :uomTo ");
    hql.append("AND t1." + UOMConversion.PROPERTY_ACTIVE + " = 'Y' ");
    if (!client.getId().equals("0")) {
      hql.append("AND t1." + UOMConversion.PROPERTY_CLIENT + " = :client");
    }

    final Query<String> query = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), String.class);
    query.setParameter("uomFrom", uomFrom);
    query.setParameter("uomTo", uomTo);
    if (!client.getId().equals("0")) {
      query.setParameter("client", client);
    }
    query.setMaxResults(1);
    return !query.list().isEmpty();
  }

}
