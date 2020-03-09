package org.wirabumi.gen.oez.ad_process;

import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class ImportWorkEffort extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
	  
	  List<org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort> pendingImportWorkEffort = getPendingImportWorkEffort();
	  OBError result = ImportWorkEffortService.doImportProcess(pendingImportWorkEffort);
	  
	  bundle.setResult(result);
	  
  }

  private List<org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort> getPendingImportWorkEffort() {
	  OBCriteria<org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort> iweC =
			  OBDal.getInstance().createCriteria(org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort.class);
	  iweC.add(Restrictions.eq(org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort.PROPERTY_ISIMPORTED, false));
	  iweC.addOrderBy(org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort.PROPERTY_DOCUMENTNO, true);
	  iweC.addOrderBy(org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort.PROPERTY_WRDOCNO, true);
	  iweC.addOrderBy(org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort.PROPERTY_WRSEQNO, true);
	  return iweC.list();
  }

}
