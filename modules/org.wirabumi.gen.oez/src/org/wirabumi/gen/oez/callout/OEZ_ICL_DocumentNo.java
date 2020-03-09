package org.wirabumi.gen.oez.callout;

import javax.servlet.ServletException;

import org.openbravo.erpCommon.ad_callouts.SimpleCallout;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.service.db.DalConnectionProvider;

public class OEZ_ICL_DocumentNo extends SimpleCallout {

	@Override
	protected void execute(CalloutInfo info) throws ServletException {
		String doktype = info.vars.getStringParameter("inpemOezDoctype");

		//kalau tidak dapat document no maka buat dahulu document sequence untuk dengan nama DocumentNo_m_internal_consumption
		String docno=Utility.getDocumentNo( new DalConnectionProvider(), info.vars , "800076", "m_internal_consumption", doktype, "", true, true);
		info.addResult("inpemOezDocumentno", docno);

	}

}
