package org.wirabumi.gen.oez.ad_process;

import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class ResetDocumentSequence extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		// TODO Auto-generated method stub
		try {
			String Id = (String) bundle.getParams().get("AD_Sequence_ID");
		    String tahun = (String) bundle.getParams().get("year");
		    if(tahun==null || tahun.isEmpty()){
		    	tahun= "";
			}
			Sequence seqNo = OBDal.getInstance().get(Sequence.class, Id);
			Year year = OBDal.getInstance().get(Year.class, tahun);
			long deafulutSeq = seqNo.getStartingNo();
			seqNo.setNextAssignedNumber(deafulutSeq);
			OBDal.getInstance().save(seqNo);
			OBDal.getInstance().commitAndClose();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
