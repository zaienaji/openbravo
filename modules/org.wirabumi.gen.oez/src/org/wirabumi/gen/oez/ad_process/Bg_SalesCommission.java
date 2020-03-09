package org.wirabumi.gen.oez.ad_process;

import java.util.ArrayList;
import java.util.List;

import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.gen.oez.SalesCommissionRule;
import org.wirabumi.gen.oez.utility.CreateInvoiceUtility;
import org.wirabumi.gen.oez.utility.SalesCommissionUtility;

public class Bg_SalesCommission extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    // TODO Auto-generated method stub
    List<BaseOBObject> result = new ArrayList<BaseOBObject>();

    OBCriteria<SalesCommissionRule> commList = OBDal.getInstance().createCriteria(
        SalesCommissionRule.class);
    List<String> param = new ArrayList<String>();
    for (SalesCommissionRule rule : commList.list()) {
      param.add(rule.getClassname());
    }

    if (param.size() > 0) {
      result = SalesCommissionUtility.getSalesCommission(bundle.getContext().toVars(), param);
      CreateInvoiceUtility.createPurchaseInvoice(result, bundle.getContext().toVars(), bundle.getConnection());
    } else {
      OBError msg = new OBError();
      msg.setType("Error");
      msg.setTitle("Generate Sales Commission");
      msg.setMessage("No data in 'Sales Commission Rule'");
      bundle.setResult(msg);
    }
  }
}
