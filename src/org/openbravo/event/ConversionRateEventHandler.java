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

import java.util.Date;

import javax.enterprise.event.Observes;

import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.Currency;

class ConversionRateEventHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(ConversionRate.ENTITY_NAME) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onNew(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    // Check if exists another record using this currencyFrom - currencyTo in the same dates
    final ConversionRate conversionRate = (ConversionRate) event.getTargetInstance();
    if (existsRecord(conversionRate.getId(), conversionRate.getClient(),
        conversionRate.getCurrency(), conversionRate.getToCurrency(),
        conversionRate.getValidFromDate(), conversionRate.getValidToDate())) {
      throw new OBException(OBMessageUtils.messageBD("20504"));
    }
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    // Check if exists another record using this currencyFrom - currencyTo in the same dates
    final ConversionRate conversionRate = (ConversionRate) event.getTargetInstance();
    if (existsRecord(conversionRate.getId(), conversionRate.getClient(),
        conversionRate.getCurrency(), conversionRate.getToCurrency(),
        conversionRate.getValidFromDate(), conversionRate.getValidToDate())) {
      throw new OBException(OBMessageUtils.messageBD("20504"));
    }
  }

  // Check if exists another record using this currencyFrom - currencyTo in the same dates
  private boolean existsRecord(String id, Client client, Currency currencyFrom, Currency currencyTo,
      Date validFrom, Date validTo) {
    StringBuilder hql = new StringBuilder();
    hql.append(" SELECT t." + ConversionRate.PROPERTY_ID);
    hql.append(" FROM " + ConversionRate.ENTITY_NAME + " as t");
    hql.append(" WHERE :id != t. " + ConversionRate.PROPERTY_ID);
    hql.append(" AND :client = t. " + ConversionRate.PROPERTY_CLIENT);
    hql.append(" AND :currencyFrom = t. " + ConversionRate.PROPERTY_CURRENCY);
    hql.append(" AND :currencyTo = t. " + ConversionRate.PROPERTY_TOCURRENCY);
    hql.append(" AND ((:validFrom between t." + ConversionRate.PROPERTY_VALIDFROMDATE + " AND t."
        + ConversionRate.PROPERTY_VALIDTODATE);
    hql.append(" OR :validTo between t." + ConversionRate.PROPERTY_VALIDFROMDATE + " AND t."
        + ConversionRate.PROPERTY_VALIDTODATE + ")");
    hql.append(" OR (:validFrom < t." + ConversionRate.PROPERTY_VALIDFROMDATE + " AND :validTo > t."
        + ConversionRate.PROPERTY_VALIDTODATE + "))");

    final Query<String> query = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), String.class);
    query.setParameter("id", id);
    query.setParameter("client", client);
    query.setParameter("currencyFrom", currencyFrom);
    query.setParameter("currencyTo", currencyTo);
    query.setParameter("validFrom", validFrom);
    query.setParameter("validTo", validTo);

    query.setMaxResults(1);
    return !query.list().isEmpty();
  }

}
