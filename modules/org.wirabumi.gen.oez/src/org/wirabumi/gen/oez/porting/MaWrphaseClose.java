package org.wirabumi.gen.oez.porting;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.manufacturing.transaction.WorkRequirement;
import org.openbravo.model.manufacturing.transaction.WorkRequirementOperation;

public class MaWrphaseClose {
  public MaWrphaseClose(WorkRequirementOperation wrphase, boolean calledfromapp) {

    WorkRequirement workRequirement = wrphase.getWorkRequirement();
    workRequirement.setClosed(false);
    OBDal.getInstance().save(workRequirement);

    OBCriteria<WorkRequirementOperation> wrPhaseCriteria = OBDal.getInstance().createCriteria(
        WorkRequirementOperation.class);
    wrPhaseCriteria.add(Restrictions.eq(WorkRequirementOperation.PROPERTY_WORKREQUIREMENT,
        workRequirement));
    wrPhaseCriteria.add(Restrictions.eq(WorkRequirementOperation.PROPERTY_CLOSED, false));

    if (wrPhaseCriteria.list().size() == 0) {
      workRequirement.setClosed(true);
      OBDal.getInstance().save(workRequirement);
    }

    OBDal.getInstance().commitAndClose();
  }
}
