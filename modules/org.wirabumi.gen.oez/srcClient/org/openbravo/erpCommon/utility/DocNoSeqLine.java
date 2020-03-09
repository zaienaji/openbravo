package org.openbravo.erpCommon.utility;

import java.sql.Connection;

import javax.servlet.ServletException;

import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;

import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.DocumentNoData;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.wirabumi.gen.oez.oez_sequence_line;

public class DocNoSeqLine implements FieldProvider {
	static Logger log4j = Logger.getLogger(DocumentNoData.class);
	  public String a;
	@Override
	public String getField(String fieldName) {
		if (fieldName.equalsIgnoreCase("a"))
		      return a;
		   else {
		     log4j.debug("Field does not exist: " + fieldName);
		     return null;
		   }		  
		}

	public static CSResponse nextDocType(ConnectionProvider connectionProvider,
			String cDocTypeId, String adClientId, String updateNext,Period period,Year year)throws ServletException {
	   CSResponse objectCSResponse = new CSResponse();
	   Sequence seq = null;
	   String prefix ="";
	   String suffix ="";
	   String command = "";
	   DocumentType doctype = OBDal.getInstance().get(DocumentType.class,cDocTypeId);
	   if(doctype!=null){
			   seq = doctype.getDocumentSequence();
			   if(seq==null){
				   objectCSResponse.razon = null;
				   return (objectCSResponse);
			   }
			   command = seq.getOezResettype();
			   prefix = seq.getPrefix();
			   suffix = seq.getSuffix();
			   if(suffix==null){
				   suffix="";
			   }
			   if(prefix==null){
				   prefix="";
			   }
		   
		   long nextNumber =0;
		   String Tahun = "";
		   String month = "";
		   if(command.equalsIgnoreCase("montly")){
			   OBCriteria<oez_sequence_line> docSeqLineList = OBDal.getInstance().createCriteria(oez_sequence_line.class);
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_SEQUENCE, seq));
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_PERIOD,period));
			   for(oez_sequence_line docSeqLine :docSeqLineList.list()){
				   nextNumber = docSeqLine.getNextAssignedNumber();
				   Tahun = docSeqLine.getYear().getFiscalYear();
				   if(Tahun==null){
					   Tahun = ""; 
				   }
				   month = docSeqLine.getPeriod().getPeriodNo().toString();
				   if(month==null){
					   month = ""; 
				   }
				   long tmbh = nextNumber;
				   if(updateNext.equalsIgnoreCase("Y")){
					   tmbh = nextNumber;
				   }else{
					  tmbh = nextNumber+1;   
				   }			  
				   docSeqLine.setNextAssignedNumber(tmbh);
				   OBDal.getInstance().save(docSeqLine);
			   }
		   }else if(command.equalsIgnoreCase("yearly")){
			   OBCriteria<oez_sequence_line> docSeqLineList = OBDal.getInstance().createCriteria(oez_sequence_line.class);
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_SEQUENCE, seq));
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_YEAR,year));
			   for(oez_sequence_line docSeqLine :docSeqLineList.list()){
				   nextNumber = docSeqLine.getNextAssignedNumber();
				   Tahun = docSeqLine.getYear().getFiscalYear();
				   if(Tahun==null){
					   Tahun = ""; 
				   }
					   month = ""; 
				   long tmbh = nextNumber;
				   if(updateNext.equalsIgnoreCase("Y")){
					   tmbh = nextNumber;
				   }else{
					  tmbh = nextNumber+1;   
				   }			  
				   docSeqLine.setNextAssignedNumber(tmbh);
				   OBDal.getInstance().save(docSeqLine);
			   }
		   }else{
			   objectCSResponse.razon = null;
		   }
		    objectCSResponse.razon = prefix+Tahun+"/"+month+"/"+nextNumber+suffix;
	   }else{
		   objectCSResponse.razon = null;
	   }
	   
	    return(objectCSResponse);
	  }

	public static CSResponse nextDoc(ConnectionProvider connectionProvider, String cDocTypeTableName, String adClientId, String updateNext,Period period,Year year)throws ServletException {
	   Sequence seq = null;
	   String prefix ="";
	   String suffix ="";
	   String command = "";
	   OBCriteria<DocumentType> docTypeList = OBDal.getInstance().createCriteria(DocumentType.class);
	   docTypeList.add(Restrictions.eq(DocumentType.PROPERTY_NAME, cDocTypeTableName));
	   docTypeList.add(Restrictions.eq(DocumentType.PROPERTY_SEQUENCEDDOCUMENT, true));
	   docTypeList.add(Restrictions.eq(DocumentType.PROPERTY_ACTIVE, true));
	   for(DocumentType doctype : docTypeList.list()){
		   seq = doctype.getDocumentSequence();
		   command = seq.getOezResettype();
		   prefix = seq.getPrefix();
		   suffix = seq.getSuffix();
		   if(suffix==null){
			   suffix="";
		   }
		   if(prefix==null){
			   prefix="";
		   }
	   }
	   long nextNumber =0;
	   String Tahun = "";
	   String month = "";
	   if(command.equalsIgnoreCase("monthly")){
		   OBCriteria<oez_sequence_line> docSeqLineList = OBDal.getInstance().createCriteria(oez_sequence_line.class);
		   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_SEQUENCE, seq));
		   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_PERIOD,period));
		   for(oez_sequence_line docSeqLine :docSeqLineList.list()){
			   nextNumber = docSeqLine.getNextAssignedNumber();
			   Tahun = docSeqLine.getYear().getFiscalYear();		   
			   if(Tahun==null){
				   Tahun = ""; 
			   }
			   month = docSeqLine.getPeriod().getPeriodNo().toString();
			   if(month==null){
				   month = ""; 
			   }
			   long tmbh = nextNumber;
			   if(updateNext.equalsIgnoreCase("Y")){
				   tmbh = nextNumber;
			   }else{
				  tmbh = nextNumber+1;   
			   }
			   docSeqLine.setNextAssignedNumber(tmbh);
			   OBDal.getInstance().save(docSeqLine); 
		   }
	   }
	   else{
		   OBCriteria<oez_sequence_line> docSeqLineList = OBDal.getInstance().createCriteria(oez_sequence_line.class);
		   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_SEQUENCE, seq));
		   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_YEAR,year));
		   for(oez_sequence_line docSeqLine :docSeqLineList.list()){
			   nextNumber = docSeqLine.getNextAssignedNumber();
			   Tahun = docSeqLine.getYear().getFiscalYear();		   
			   if(Tahun==null){
				   Tahun = ""; 
			   }
				   month = ""; 
			   long tmbh = nextNumber;
			   if(updateNext.equalsIgnoreCase("Y")){
				   tmbh = nextNumber;
			   }else{
				  tmbh = nextNumber+1;   
			   }
			   docSeqLine.setNextAssignedNumber(tmbh);
			   OBDal.getInstance().save(docSeqLine);
	   }
	   
	   }
	    CSResponse objectCSResponse = new CSResponse();
	    objectCSResponse.razon = prefix+Tahun+"/"+month+"/"+nextNumber+suffix;
	    return(objectCSResponse);
	  }

	public static CSResponse nextDocConnection(Connection conn, ConnectionProvider connectionProvider,
			String cDocTypeTableName, String adClientId, String updateNext,
			Period period,Year year)throws ServletException {
		   Sequence seq = null;
		   String prefix ="";
		   String suffix ="";
		   String command = "";
		   Client client = OBDal.getInstance().get(Client.class, adClientId);
		   OBCriteria<Sequence> seqList = OBDal.getInstance().createCriteria(Sequence.class);
		   seqList.add(Restrictions.eq(Sequence.PROPERTY_NAME, cDocTypeTableName));
		   seqList.add(Restrictions.eq(Sequence.PROPERTY_ACTIVE, true));
		   seqList.add(Restrictions.eq(Sequence.PROPERTY_USEDFORRECORDID, false));
		   seqList.add(Restrictions.eq(Sequence.PROPERTY_AUTONUMBERING, true));
		   seqList.add(Restrictions.eq(Sequence.PROPERTY_CLIENT, client));
		   for(Sequence sequence : seqList.list()){
			   seq = sequence;
			   command = seq.getOezResettype();
			   prefix = sequence.getPrefix();
			   suffix = sequence.getSuffix();
			   if(suffix==null){
				   suffix="";
			   }
			   if(prefix==null){
				   prefix="";
			   }
			   break;
		   }
		   long nextNumber =0;
		   String Tahun = null;
		   String month = null;
		   if(command.equalsIgnoreCase("monthly")){
			   OBCriteria<oez_sequence_line> docSeqLineList = OBDal.getInstance().createCriteria(oez_sequence_line.class);
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_SEQUENCE, seq));
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_PERIOD,period));
			   for(oez_sequence_line docSeqLine :docSeqLineList.list()){
				   nextNumber = docSeqLine.getNextAssignedNumber();
				   Tahun = docSeqLine.getYear().getFiscalYear();
				   if(Tahun==null){
					   Tahun = ""; 
				   }
				   month = docSeqLine.getPeriod().getPeriodNo().toString();
				   if(month==null){
					   month = ""; 
				   }
				   long tmbh = nextNumber;
				   if(updateNext.equalsIgnoreCase("Y")){
					   tmbh = nextNumber;
				   }else{
					  tmbh = nextNumber+1;   
				   }
				   docSeqLine.setNextAssignedNumber(tmbh);
				   OBDal.getInstance().save(docSeqLine);
				   }
			   }else{
			   OBCriteria<oez_sequence_line> docSeqLineList = OBDal.getInstance().createCriteria(oez_sequence_line.class);
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_SEQUENCE, seq));
			   docSeqLineList.add(Restrictions.eq(oez_sequence_line.PROPERTY_YEAR,year));
			   for(oez_sequence_line docSeqLine :docSeqLineList.list()){
				   nextNumber = docSeqLine.getNextAssignedNumber();
				   Tahun = docSeqLine.getYear().getFiscalYear();
				   if(Tahun==null){
					   Tahun = ""; 
				   }
				   if(month==null){
					   month="";
				   }
				   long tmbh = nextNumber;
				   if(updateNext.equalsIgnoreCase("Y")){
					   tmbh = nextNumber;
				   }else{
					  tmbh = nextNumber+1;   
				   }
				   docSeqLine.setNextAssignedNumber(tmbh);
				   OBDal.getInstance().save(docSeqLine);
			   	}
			   }
		    CSResponse objectCSResponse = new CSResponse();
		    objectCSResponse.razon = prefix+Tahun+"/"+month+"/"+nextNumber+suffix;
		    return(objectCSResponse);
		  }
}