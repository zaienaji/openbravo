package org.sksoft.skici.erpCommon.ad_process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.AccDefUtility;
import org.openbravo.erpCommon.utility.OBError;
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
    String procedureName = "SKICI_INTERNALCONSUMPTIONPOST1";
    
    OBError result = new OBError();
    result.setType("Success");
    result.setTitle("Success");
    result.setMessage("Internal consumption processed successfully.");
    bundle.setResult(result);
    
    // skici post internal consumption stored procedure related to ad_process_id 800131
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
    
    String docStatus = ic.getStatus();
    String action = null;
    switch (docStatus) {
      case "DR":
        action="CO";
        break;
        
      case "CO":
        action="VO";
        break;
        
      case "VO":
        throw new OBException("cannot proceed internal consumption, it's already voided.");
        
      case "CL":
        throw new OBException("cannot proceed internal consumption, it's already closed.");

      default:
        break;
    }
    
    if (Strings.isNullOrEmpty(action))
      throw new OBException("cannot proceed internal consumption, invalid document status: "+docStatus);
    
    try {
      final Connection connection = OBDal.getInstance().getConnection();
      
      OBContext.setAdminMode();
      String sql = "select * from "+procedureName+"(null, ?, ?)";
      OBContext.restorePreviousMode();
      
      final PreparedStatement preparedStatement = connection.prepareStatement(sql);
      preparedStatement.setString(1, ic.getId());
      preparedStatement.setString(2, action);
      preparedStatement.execute();
      
    } catch (SQLException e) {
      result.setType("Error");
      result.setTitle("Error");
      result.setMessage(e.getMessage());
      
      logger.error("failed to call stored procedure. error message: "+e.getMessage());
      
    } catch (Exception e) {
      logger.error("failed to call stored procedure. error message: "+e.getMessage());
      throw new SQLException(e.getMessage());
      
    }
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
