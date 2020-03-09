package org.wirabumi.gen.oez.ad_process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.manufacturing.processplan.ProcessPlan;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DalConnectionProvider;

public class ImportWorkRequirement extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		//load predefined import processplan
		String clientID = OBContext.getOBContext().getCurrentClient().getId();
		String sql = "select distinct b.ma_processplan_id, b.value as processplankey"
				+ " from oez_i_workrequirement a"
				+ " inner join ma_processplan b on b.ad_client_id=a.ad_client_id and b.value=a.processplankey"
				+ " where a.ad_client_id=?";
		ConnectionProvider conn = new DalConnectionProvider();
		Connection connection = conn.getConnection();
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setString(1, clientID);
		ResultSet rs = ps.executeQuery();
		HashMap<String, ProcessPlan> processPlanMap = new HashMap<String, ProcessPlan>();
		while (rs.next()){
			String ppkey = rs.getString("processplankey");
			String ppID = rs.getString("ma_processplan_id");
			ProcessPlan pp = OBDal.getInstance().get(ProcessPlan.class, ppID);
			processPlanMap.put(ppkey, pp);
		}
		
		//cari unprocessed import
		OBCriteria<org.wirabumi.gen.oez.importmasterdata.ImportWorkRequirement> iwrC = 
				OBDal.getInstance().createCriteria(org.wirabumi.gen.oez.importmasterdata.ImportWorkRequirement.class);
		iwrC.add(Restrictions.eq(org.wirabumi.gen.oez.importmasterdata.ImportWorkRequirement.PROPERTY_IMPORTPROCESSCOMPLETE, false));
		List<org.wirabumi.gen.oez.importmasterdata.ImportWorkRequirement> iwrL = iwrC.list();
		
		//do import
		OBError oberror = ImportWorkRequirementServices.doProcessImport(processPlanMap, iwrL);
		bundle.setResult(oberror);
	}

}
