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
 * All portions are Copyright (C) 2016 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.modulescript;

import org.openbravo.modulescript.ModuleScript;
import org.openbravo.database.ConnectionProvider;

/*
 * Related to issue: https://issues.openbravo.com/view.php?id=33063
 */
public class ResetAccountingAccess extends ModuleScript {
  @Override
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      ResetAccountingAccessData.insert(cp);
    } catch(Exception e) {
      handleError(e);
    }
  }

  @Override
  protected ModuleScriptExecutionLimits getModuleScriptExecutionLimits() {
    return new ModuleScriptExecutionLimits("0", null, new OpenbravoVersion(3,0,28839));
  }

  @Override
  protected boolean executeOnInstall() {
    return false;
  }
}
