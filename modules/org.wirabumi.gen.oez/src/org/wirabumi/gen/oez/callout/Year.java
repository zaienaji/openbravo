package org.wirabumi.gen.oez.callout;

import java.util.Calendar;

import javax.servlet.ServletException;


import org.openbravo.erpCommon.ad_callouts.SimpleCallout;

public class Year extends SimpleCallout {

	@Override
	protected void execute(CalloutInfo info) throws ServletException {
		// TODO Auto-generated method stub
		Calendar calendar = Calendar.getInstance();
		String tahun = Integer.toString(calendar.get(calendar.YEAR));
		info.addResult("inpoezCurrentYear", tahun); 
	}
}
