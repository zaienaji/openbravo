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
 * All portions are Copyright (C) 2012-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */

package org.openbravo.client.application.event;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.model.common.businesspartner.BankAccount;

/**
 * Adds retrocompatibity to code that uses the Show IBAN/Generic columns instead of the new Bank
 * Account Format list
 * 
 * @author openbravo
 * 
 */
class BusinessPartnerBankAccountHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(BankAccount.ENTITY_NAME) };
  private static Logger logger = LogManager.getLogger();

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final BankAccount bankAccount = (BankAccount) event.getTargetInstance();
    if (bankAccount != null && StringUtils.isBlank(bankAccount.getBankFormat())) {
      final Boolean showIBAN = bankAccount.isShowIBAN();
      final Entity bankAccountEntity = ModelProvider.getInstance()
          .getEntity(BankAccount.ENTITY_NAME);
      final Property bankFormatProperty = bankAccountEntity
          .getProperty(BankAccount.PROPERTY_BANKFORMAT);
      event.setCurrentState(bankFormatProperty, showIBAN ? "IBAN" : "GENERIC");
      logger.info(
          "Automatically populated the Bank Account Format based on the Show Generic | Show IBAN info");
    }
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final BankAccount bankAccount = (BankAccount) event.getTargetInstance();
    if (bankAccount != null && StringUtils.isBlank(bankAccount.getBankFormat())) {
      final Boolean showIBAN = bankAccount.isShowIBAN();
      final Entity bankAccountEntity = ModelProvider.getInstance()
          .getEntity(BankAccount.ENTITY_NAME);
      final Property bankFormatProperty = bankAccountEntity
          .getProperty(BankAccount.PROPERTY_BANKFORMAT);
      event.setCurrentState(bankFormatProperty, showIBAN ? "IBAN" : "GENERIC");
      logger.info(
          "Automatically populated the Bank Account Format based on the Show Generic | Show IBAN info");
    }
  }
}
