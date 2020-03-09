package org.wirabumi.gen.oez.ad_process;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class Bg_RestartDocNo extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		// TODO Auto-generated method stub
		OBCriteria<Sequence> docNo = OBDal.getInstance().createCriteria(Sequence.class);
		docNo.add(Restrictions.eq(Sequence.PROPERTY_RESTARTSEQUENCEEVERYYEAR, true));
		for(Sequence noDoc : docNo.list()){
			long deafultNo = noDoc.getStartingNo();
			noDoc.setNextAssignedNumber(deafultNo);
			OBDal.getInstance().save(noDoc);
		}
		OBDal.getInstance().commitAndClose();
	}

}
