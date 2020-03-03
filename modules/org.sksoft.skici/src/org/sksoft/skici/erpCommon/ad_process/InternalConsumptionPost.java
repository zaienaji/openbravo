package org.sksoft.skici.erpCommon.ad_process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.AccDefUtility;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.financialmgmt.calendar.Calendar;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.calendar.PeriodControl;
import org.openbravo.model.materialmgmt.transaction.InternalConsumption;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

import com.google.common.base.Strings;


public class InternalConsumptionPost extends DalBaseProcess {
  
  private static final Logger logger = LogManager.getLogger();

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    OBError result = new OBError();
    result.setType("Success");
    result.setTitle("Success");
    result.setMessage("Internal consumption processed successfully.");
    bundle.setResult(result);
    
    // skici post internal consumption stored procedure related to ad_process_id 800131
    final Process sqlProcess = OBDal.getInstance().get(Process.class, "800131");
    String recordId = (String)bundle.getParams().get("M_Internal_Consumption_ID");
    logger.debug("processing record id "+recordId);
    InternalConsumption ic = OBDal.getInstance().get(InternalConsumption.class, recordId);
    
    //validate period
    String periodStatus = getInternalConsumptionPeriodStatus(ic);
    logger.debug("period status: "+periodStatus);
    if (!periodStatus.equalsIgnoreCase("O"))
    {
      result.setTitle("Error");
      result.setType("Error");
      result.setMessage("period closed");
      return;
    }
    
    OBContext.setAdminMode();
    
    final ProcessInstance pInstance = OBProvider.getInstance().get(ProcessInstance.class);
    pInstance.setProcess(sqlProcess);
    pInstance.setActive(true);
    pInstance.setRecordID(recordId);
    pInstance.setUserContact(OBContext.getOBContext().getUser());
    
    OBDal.getInstance().save(pInstance);
    OBDal.getInstance().flush();
    
    OBContext.restorePreviousMode();
    
    logger.debug("pInstance id "+pInstance.getId());
    
    try {
      final Connection connection = OBDal.getInstance().getConnection();
      
      OBContext.setAdminMode();
      String sql = "select * from "+sqlProcess.getProcedure()+"(?)";
      OBContext.restorePreviousMode();
      
      final PreparedStatement preparedStatement = connection.prepareStatement(sql);
      preparedStatement.setString(1, pInstance.getId());
      preparedStatement.execute();
    } catch (Exception e) {
      logger.error("failed to call stored procedure. error message: "+e.getMessage());
      throw new SQLException(e.getMessage());
    }
    
    OBDal.getInstance().getSession().refresh(pInstance);
    
    OBContext.setAdminMode();
    
    if (pInstance.getResult()==0)
    {
      result.setType("Error");
      result.setTitle("Error");
      logger.debug("some error when call stored procedure. error message: "+pInstance.getErrorMsg());
    }
    
    String errormessage = pInstance.getErrorMsg();
    if (!Strings.isNullOrEmpty(errormessage))
      result.setMessage(pInstance.getErrorMsg());
    
    OBContext.restorePreviousMode();
  }

  private String getInternalConsumptionPeriodStatus(InternalConsumption ic) {
    Calendar fiscalCalendar = AccDefUtility.getCalendar(ic.getOrganization());
    logger.debug("fiscal calendar: "+fiscalCalendar.getId()+" - "+fiscalCalendar.getName());
    Period fiscalPeriod = AccDefUtility.getCurrentPeriod(ic.getMovementDate(), fiscalCalendar);
    logger.debug("fiscal period: "+fiscalPeriod.getId()+" - "+fiscalPeriod.getName());
    
    if (fiscalPeriod.getStatus().equalsIgnoreCase("O"))
      return "O";
    
    List<PeriodControl> periodControls = fiscalPeriod.getFinancialMgmtPeriodControlList();
    List<PeriodControl> pc = new ArrayList<>(periodControls);
    
    CollectionUtils.filter(pc, 
        o -> ((PeriodControl)o).getPeriodStatus().equalsIgnoreCase("O") 
          & ((PeriodControl)o).getDocumentCategory().equalsIgnoreCase("MIC") );
    
    if (pc.size()==0)
      return "C";
    
    return pc.get(0).getPeriodStatus();
  }

}
