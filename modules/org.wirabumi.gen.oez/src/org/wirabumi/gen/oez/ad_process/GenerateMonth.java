package org.wirabumi.gen.oez.ad_process;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.gen.oez.oez_sequence_line;

public class GenerateMonth extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		OBError msg = new OBError();
		
		try {
			String Id = (String) bundle.getParams().get("AD_Sequence_ID");
		    String tahun = (String) bundle.getParams().get("cYearId");
		    Sequence adSeq = OBDal.getInstance().get(Sequence.class, Id);
		    long startNo = adSeq.getStartingNo();
		    long nextNumber = adSeq.getNextAssignedNumber();
		    long currentNext = adSeq.getCurrentNextSystem();
		    String resetType = null;
		    resetType = adSeq.getOezResettype();
		    Year year = OBDal.getInstance().get(Year.class, tahun);
		    if (resetType==null){
		    	msg.setType("Error");
		    	msg.setMessage("Reset Type is Empty");
		    	bundle.setResult(msg);

		    }else if(resetType.equalsIgnoreCase("monthly")){
		    	OBCriteria<Period> periodList = OBDal.getInstance().createCriteria(Period.class);
			    periodList.add(Restrictions.eq(Period.PROPERTY_YEAR, year));
			    for(Period bulan : periodList.list()){
			    	Period month = bulan;
			    	oez_sequence_line seqLine = OBProvider.getInstance().get(oez_sequence_line.class);
			    	seqLine.setSequence(adSeq);
			    	seqLine.setYear(year);
			    	seqLine.setPeriod(month);
			    	seqLine.setStartingNo(startNo);
			    	seqLine.setNextAssignedNumber(nextNumber);
			    	seqLine.setCurrentNextSystem(currentNext);
			    	OBDal.getInstance().save(seqLine);
			    }
		    }else{
		    	oez_sequence_line seqLine = OBProvider.getInstance().get(oez_sequence_line.class);
		    	seqLine.setSequence(adSeq);
		    	seqLine.setYear(year);
		    	seqLine.setStartingNo(startNo);
		    	seqLine.setNextAssignedNumber(nextNumber);
		    	seqLine.setCurrentNextSystem(currentNext);
		    	OBDal.getInstance().save(seqLine);
		    }
		    OBDal.getInstance().commitAndClose();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
