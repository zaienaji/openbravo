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
 * All portions are Copyright (C) 2012 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.costing;

import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.provider.OBSingleton;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.CostingRule;

public class CostingStatus implements OBSingleton {
  private static CostingStatus instance;
  private static Boolean isMigrated;

  public static synchronized CostingStatus getInstance() {
    if (instance == null) {
      instance = new CostingStatus();
    }
    return instance;
  }

  public Boolean isMigrated() {
    // set in a localIsMigrated to prevent threading issues when
    // reseting it in setMigrated()
    if (isMigrated == null || isMigrated == false) {
      OBContext.setAdminMode(false);
      try {
        //
        OBQuery<Preference> crQry = OBDal.getInstance()
            .createQuery(Preference.class,
                Preference.PROPERTY_ATTRIBUTE + " ='Cost_Eng_Ins_Migrated'");
        crQry.setFilterOnReadableClients(false);
        crQry.setFilterOnReadableOrganization(false);
        crQry.setMaxResult(1);
        isMigrated = crQry.uniqueResult() != null;

        if (!isMigrated) {
          OBQuery<org.openbravo.model.materialmgmt.cost.Costing> costingQry = OBDal.getInstance()
              .createQuery(org.openbravo.model.materialmgmt.cost.Costing.class, "");
          costingQry.setFilterOnReadableClients(false);
          costingQry.setFilterOnReadableOrganization(false);

          OBQuery<CostingRule> cRuleQry = OBDal.getInstance()
              .createQuery(CostingRule.class, CostingRule.PROPERTY_VALIDATED + " = true");
          cRuleQry.setFilterOnReadableClients(false);
          cRuleQry.setFilterOnReadableOrganization(false);

          if (costingQry.count() == 0 || cRuleQry.count() > 0) {
            setMigrated();
          }
        }

      } finally {
        OBContext.restorePreviousMode();
      }
    }
    return isMigrated;
  }

  public void setMigrated() {
    if (isMigrated) {
      return;
    }
    OBContext.setAdminMode(false);
    try {
      Preference migratedPreference = OBProvider.getInstance().get(Preference.class);
      migratedPreference.setAttribute("Cost_Eng_Ins_Migrated");
      migratedPreference.setSearchKey("Y");
      migratedPreference.setClient(OBDal.getInstance().get(Client.class, "0"));
      migratedPreference.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
      migratedPreference.setPropertyList(false);
      OBDal.getInstance().save(migratedPreference);
      OBDal.getInstance().flush();
    } finally {
      OBContext.restorePreviousMode();
    }
    isMigrated = true;
  }
}
