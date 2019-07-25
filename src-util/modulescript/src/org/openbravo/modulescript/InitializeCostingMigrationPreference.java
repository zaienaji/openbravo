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
 * All portions are Copyright (C) 2012-2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.modulescript;

import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;
import java.sql.Connection;
import javax.servlet.ServletException;

// FIXME: https://issues.openbravo.com/view.php?id=30706
// Costing Migration process should create preference properly and modulescript should be removed
public class InitializeCostingMigrationPreference extends ModuleScript {

  /**
   * This modulescript takes into account if the costing engine's migration has been made and it generates
   * a preference Cost_Eng_Ins_Migrated='Y' if the costing engine's migration has been made
   */
  @Override
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      // If the preference does not exist in the database yet the modulescript must be executed.
      boolean isMigrated = InitializeCostingMigrationPreferenceData.isMigrated(cp);
      if (!isMigrated) {
        if (!InitializeCostingMigrationPreferenceData.hasMCostingData(cp)) {
          InitializeCostingMigrationPreferenceData.createPreference(cp);
          return;
        } else if (InitializeCostingMigrationPreferenceData.hasMCostingRuleData(cp)){
          InitializeCostingMigrationPreferenceData.createPreference(cp);
          return;
        }
      }
    } catch (Exception e) {
      handleError(e);
    }
  }
}
