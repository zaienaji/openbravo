package org.wirabumi.gen.oez.utility;

import java.util.List;

import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;

public interface SalesCommissionRuleInterface {
  public List<BaseOBObject> doExecute(VariablesSecureApp vars, List<BaseOBObject> dataCommission);
}
