package org.wirabumi.gen.oez.porting;

import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.process.ProcessInstance;

public class AdUpdatePinstance {

  public AdUpdatePinstance(ProcessInstance processInstance, User user, boolean isprocessing,
      long result, String message, boolean docommit) {

    processInstance.setProcessing(isprocessing);
    processInstance.setResult(result);
    processInstance.setErrorMsg(message);

    OBDal.getInstance().save(processInstance);
    OBDal.getInstance().commitAndClose();
  }
}
