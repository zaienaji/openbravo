package org.wirabumi.gen.oez.ad_process;

import java.util.Calendar;
import java.util.Date;

import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.procurement.Requisition;
import org.openbravo.model.procurement.RequisitionLine;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CopySOtoRequisition extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		Calendar cal = Calendar.getInstance();
		Date date = cal.getTime();
		String orderId = (String)bundle.getParams().get("cOrderId");
		String requisitionId = (String)bundle.getParams().get("M_Requisition_ID");
		OBContext.setAdminMode();
		Requisition requisition = OBDal.getInstance().get(Requisition.class, requisitionId);
		Order order = OBDal.getInstance().get(Order.class, orderId);
		for(OrderLine orderline : order.getOrderLineList()){
			RequisitionLine requisitionline = OBProvider.getInstance().get(RequisitionLine.class);
			requisitionline.setProduct(orderline.getProduct());
			requisitionline.setQuantity(orderline.getOrderedQuantity());
			requisitionline.setNeedByDate(date);
			requisitionline.setRequisition(requisition);
			requisitionline.setOezSalesorderline(orderline);
			requisitionline.setOrganization(requisition.getOrganization());
			requisitionline.setLineNo(orderline.getLineNo());
			OBDal.getInstance().save(requisitionline);
			
		}
		OBDal.getInstance().commitAndClose();
		OBContext.setAdminMode();
		
		//build return message
		final OBError msg = new OBError();
	    msg.setType("Success");
	    msg.setTitle("Success");
	    msg.setMessage("Sales Order "+order.getDocumentNo()+" have been copied");
	    bundle.setResult(msg);
	    
	    return;
		
		
				
	}

}
