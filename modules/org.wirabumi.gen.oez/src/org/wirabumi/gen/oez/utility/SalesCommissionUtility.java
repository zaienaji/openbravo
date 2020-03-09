package org.wirabumi.gen.oez.utility;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBDal;

public class SalesCommissionUtility {
  private static Logger log4j = Logger.getLogger(SalesCommissionUtility.class);

  public static List<BaseOBObject> getSalesCommission(VariablesSecureApp vars,List<String> classNameList) {
    List<BaseOBObject> result = new ArrayList<BaseOBObject>();

    try {
      result.clear();
      for (String className : classNameList) {
        Class<?> forname = Class.forName(className);
        if (forname.getInterfaces() == null) {
          log4j.error("Class Name: '" + className + "' has not implemented class.");
          continue;
        } else {
          if (forname.newInstance() instanceof SalesCommissionRuleInterface) {
            SalesCommissionRuleInterface salesCommission = (SalesCommissionRuleInterface) forname.newInstance();
            result = salesCommission.doExecute(vars,result);
            log4j.debug("Sales Commission Rule with classname: " + className + " is successfully.");
          } else {
            log4j.error("Class Name: '" + className + "' has not implemented to 'SalesProcess'.");
            continue;
          }
        }
      }
      return result;
    } catch (Exception e) {
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
      log4j.error(e.getMessage());
      return null;
    }
  }
}
