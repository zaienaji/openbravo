package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.manufacturing.processplan.ProcessPlan;
import org.openbravo.model.manufacturing.transaction.WorkRequirement;
import org.wirabumi.gen.oez.importmasterdata.ImportWorkRequirement;

public class ImportWorkRequirementServices {
	
	// Prevents instantiation (Item 4)
	private ImportWorkRequirementServices(){
		
	}
	
	public static OBError doProcessImport(HashMap<String, ProcessPlan> processPlanMap, 
			List<ImportWorkRequirement> iwrList){
		
		int sukses=0, gagal=0;
		
		final String lineSeparator = System.getProperty("line.separator");
		final Date now = new Date();
		
		for (ImportWorkRequirement iwr: iwrList){
			StringBuilder sb = new StringBuilder(0);
			String ppKey = iwr.getProcessPlanKey();
			if (!processPlanMap.containsKey(ppKey))
				sb.append("invalid process plan key").append(lineSeparator);
			BigDecimal qty = iwr.getQuantity();
			if (qty==null || qty.equals(BigDecimal.ZERO))
				sb.append("QTY is empty or zero").append(lineSeparator);
			Date startdate = iwr.getStartingDate();
			if (startdate==null)
				sb.append("starting date is empty").append(lineSeparator);
			Date enddate = iwr.getEndingDate();
			if (enddate==null)
				sb.append("ending date is empty").append(lineSeparator);
			
			if (sb.length()>0){
				//some error happen
				iwr.setImportProcessComplete(false);
				iwr.setProcessNow(false);
				iwr.setImportErrorMessage(sb.toString());
				OBDal.getInstance().save(iwr);
				
				gagal++;
			} else {
				//validation passed, then do import
				ProcessPlan pp = processPlanMap.get(ppKey);
				
				WorkRequirement wr = OBProvider.getInstance().get(WorkRequirement.class);
				wr.setProcessPlan(pp);
				wr.setQuantity(qty);
				wr.setStartingDate(startdate);
				wr.setEndingDate(enddate);
				wr.setConversionRate(pp.getConversionRate());
				wr.setProcessUnit(pp.getProcessUnit());
				wr.setEstimatedTime(BigDecimal.ZERO);
				wr.setRunTime(BigDecimal.ZERO);
				wr.setClosed(false);
				wr.setIncludePhasesWhenInserting(true);
				wr.setCreationDate(now);
				wr.setDocumentNo("<>");
				OBDal.getInstance().save(wr);
				
				iwr.setProcessNow(false);
				iwr.setImportProcessComplete(true);
				iwr.setImportErrorMessage(null);
				iwr.setWorkRequirement(wr);
				iwr.setDocumentNo(wr.getDocumentNo());
				OBDal.getInstance().save(iwr);
				
				sukses++;
			}
		}
		
		int jumlahrecord = sukses+gagal;
		OBError oberror = new OBError();
		oberror.setTitle("Import Work Requirement successfully executed");
		oberror.setMessage(jumlahrecord+" record(s) processed with "+gagal+" record(s) import failed.");
		if (gagal==0)
			oberror.setType("Success");
		else
			oberror.setType("Warning");
		
		return oberror;
	}
	

}
