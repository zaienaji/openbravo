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
 * All portions are Copyright (C) 2013-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.event;

import java.util.HashMap;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.client.kernel.event.TransactionBeginEvent;
import org.openbravo.client.kernel.event.TransactionCompletedEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.materialmgmt.VariantChDescUpdateProcess;
import org.openbravo.model.common.plm.Characteristic;
import org.openbravo.model.common.plm.CharacteristicValue;
import org.openbravo.scheduling.OBScheduler;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalConnectionProvider;

class CharacteristicValueEventHandler extends EntityPersistenceEventObserver {
  private static Logger logger = LogManager.getLogger();
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CharacteristicValue.ENTITY_NAME) };
  private static ThreadLocal<String> chvalueUpdated = new ThreadLocal<>();

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onTransactionBegin(@Observes TransactionBeginEvent event) {
    chvalueUpdated.set(null);
  }

  public void onNew(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    CharacteristicValue characteristicValue = (CharacteristicValue) event.getTargetInstance();
    checkForDuplicates(characteristicValue);
  }

  private void checkForDuplicates(CharacteristicValue characteristicValue) {
    Characteristic characteristic = characteristicValue.getCharacteristic();
    if (characteristic.isVariant()) {
      if (existsDuplicateNameInSameCharacteristic(characteristicValue)) {
        throw new OBException(OBMessageUtils.messageBD("DuplicatedChValueName"));
      }
    }
  }

  private boolean existsDuplicateNameInSameCharacteristic(CharacteristicValue characteristicValue) {
    //@formatter:off
    String hql = "select 1 from CharacteristicValue chv join chv.characteristic ch where chv.name = :name and ch.id = :characteristicId";
    //@formatter:on

    Query<Integer> query = OBDal.getInstance()
        .getSession()
        .createQuery(hql, Integer.class)
        .setParameter("name", characteristicValue.getName())
        .setParameter("characteristicId", characteristicValue.getCharacteristic().getId());

    query.setMaxResults(1);
    return !query.list().isEmpty();
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CharacteristicValue chv = (CharacteristicValue) event.getTargetInstance();
    checkForDuplicates(chv);
    chvalueUpdated.set(chv.getId());
    // Update all product characteristics configurations with updated code of the characteristic.
    // Only when product characteristics is not linked with subset.
    final Entity prodchValue = ModelProvider.getInstance()
        .getEntity(CharacteristicValue.ENTITY_NAME);
    final Property codeProperty = prodchValue.getProperty(CharacteristicValue.PROPERTY_CODE);
    if (event.getCurrentState(codeProperty) != event.getPreviousState(codeProperty)) {
      StringBuilder where = new StringBuilder();
      where.append("update ProductCharacteristicConf as pcc ");
      where.append("set code = :code, updated = now(), updatedBy = :user ");
      where.append("where exists ( ");
      where.append("    select 1 ");
      where.append("    from  ProductCharacteristic as pc ");
      where.append("    where pcc.characteristicOfProduct = pc ");
      where.append("    and pcc.characteristicValue = :characteristicValue ");
      where.append("    and pc.characteristicSubset is null ");
      where.append("    and pcc.code <> :code ");
      where.append(")");
      try {
        final Session session = OBDal.getInstance().getSession();
        @SuppressWarnings("rawtypes")
        final Query charConfQuery = session.createQuery(where.toString());
        charConfQuery.setParameter("user", OBContext.getOBContext().getUser());
        charConfQuery.setParameter("characteristicValue", chv);
        charConfQuery.setParameter("code", chv.getCode());
        charConfQuery.executeUpdate();
      } catch (Exception e) {
        logger.error(
            "Error on CharacteristicValueEventHandler. ProductCharacteristicConf could not be updated",
            e);
      }
    }
  }

  public void onTransactionCompleted(@Observes TransactionCompletedEvent event) {
    String strChValueId = chvalueUpdated.get();
    chvalueUpdated.set(null);
    if (StringUtils.isBlank(strChValueId)
        || event.getTransaction().getStatus() == TransactionStatus.ROLLED_BACK) {
      return;
    }
    try {
      VariablesSecureApp vars = null;
      try {
        vars = RequestContext.get().getVariablesSecureApp();
      } catch (Exception e) {
        vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
            OBContext.getOBContext().getCurrentClient().getId(),
            OBContext.getOBContext().getCurrentOrganization().getId(),
            OBContext.getOBContext().getRole().getId(),
            OBContext.getOBContext().getLanguage().getLanguage());
      }

      ProcessBundle pb = new ProcessBundle(VariantChDescUpdateProcess.AD_PROCESS_ID, vars)
          .init(new DalConnectionProvider(false));
      HashMap<String, Object> parameters = new HashMap<>();
      parameters.put("mProductId", "");
      parameters.put("mChValueId", strChValueId);
      pb.setParams(parameters);
      OBScheduler.getInstance().schedule(pb);
    } catch (Exception e) {
      logger.error("Error executing process", e);
    }
  }
}
