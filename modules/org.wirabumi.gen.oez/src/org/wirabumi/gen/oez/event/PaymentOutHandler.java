package org.wirabumi.gen.oez.event;

import java.util.Date;

import javax.enterprise.event.Observes;
import javax.servlet.http.HttpServletRequest;

import org.openbravo.database.ConnectionProvider;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.service.db.DalConnectionProvider;

public class PaymentOutHandler extends EntityPersistenceEventObserver {
	private static Entity[] entities = { ModelProvider.getInstance().getEntity(FIN_PaymentScheduleDetail.ENTITY_NAME) };
	
	@Override
	protected Entity[] getObservedEntities() {
		// TODO Auto-generated method stub
		return entities;
	}
	
	public void onUpdate(@Observes EntityUpdateEvent event){
		if (!isValidEvent(event)) {
			return;
		}
		FIN_PaymentScheduleDetail psd=(FIN_PaymentScheduleDetail)event.getTargetInstance();
		
		HttpServletRequest request = RequestContext.get().getRequest();
		VariablesSecureApp vars = new VariablesSecureApp(request);
		String kondisi=Utility.getPreference(vars, "OEZ_AVOIDBACKDATE", "6F8F913FA60F4CBD93DC1D3AA696E76E");
		
		if(kondisi.equalsIgnoreCase("Y")){
			Date trxDate=new Date();
			Date poDate=new Date();
			//get target object
			//FIN_PaymentScheduleDetail psd=(FIN_PaymentScheduleDetail)event.getTargetInstance();
			if(!(psd==null)){
				//get invoice date
				if(!(psd.getInvoicePaymentSchedule()==null)){
					trxDate=psd.getInvoicePaymentSchedule().getInvoice().getInvoiceDate();
				}
				if(!(psd.getOrderPaymentSchedule()==null)){
					trxDate=psd.getOrderPaymentSchedule().getOrder().getOrderDate();
				}
				//get payment out date
				if(!(psd.getPaymentDetails()==null)){
					poDate=psd.getPaymentDetails().getFinPayment().getPaymentDate();
				}
				//compare the date
				if(!(poDate==null)&&!(trxDate==null)){
					if(poDate.before(trxDate)){
						String language = OBContext.getOBContext().getLanguage().getLanguage();
						ConnectionProvider conn = new DalConnectionProvider(false);
						throw new OBException(Utility.messageBD(conn, "OEZ_BACKDATEDPAYMENTOUT", language));
					}
				}
			}
		}
	}
	
	public void onSave(@Observes EntityNewEvent event){
		if (!isValidEvent(event)) {
			return;
		}
		HttpServletRequest request = RequestContext.get().getRequest();
		VariablesSecureApp vars = new VariablesSecureApp(request);
		String kondisi=Utility.getPreference(vars, "OEZ_AVOIDBACKDATE", "6F8F913FA60F4CBD93DC1D3AA696E76E");
		
		if(kondisi.equalsIgnoreCase("Y")){
			Date invDate=null;
			Date poDate=null;
			//get target object
			FIN_PaymentScheduleDetail psd=(FIN_PaymentScheduleDetail)event.getTargetInstance();

			if(!(psd==null)){
				//get invoice date
				if(!(psd.getInvoicePaymentSchedule()==null)){
					invDate=psd.getInvoicePaymentSchedule().getInvoice().getInvoiceDate();
				}
				//get payment out date
				if(!(psd.getPaymentDetails()==null)){
					poDate=psd.getPaymentDetails().getFinPayment().getPaymentDate();
				}
				//compare the date
				if(!(poDate==null)&&!(invDate==null)){
					if(poDate.before(invDate)){
						String language = OBContext.getOBContext().getLanguage().getLanguage();
						ConnectionProvider conn = new DalConnectionProvider(false);
						throw new OBException(Utility.messageBD(conn, "OEZ_BACKDATEDPAYMENTOUT", language));
					}
				}
			}
		}
	}
}
