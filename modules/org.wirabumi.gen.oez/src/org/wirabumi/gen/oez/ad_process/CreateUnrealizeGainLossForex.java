package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.common.businesspartner.CustomerAccounts;
import org.openbravo.model.common.businesspartner.VendorAccounts;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.financialmgmt.accounting.coa.AccountingCombination;
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchema;
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchemaGL;
import org.openbravo.model.financialmgmt.accounting.coa.ElementValue;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.openbravo.model.financialmgmt.gl.GLBatch;
import org.openbravo.model.financialmgmt.gl.GLJournal;
import org.openbravo.model.financialmgmt.gl.GLJournalLine;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CreateUnrealizeGainLossForex extends DalBaseProcess {
	final long oneDay=24*60*60*1000;

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		//vars object
		HttpServletRequest request = RequestContext.get().getRequest();
        VariablesSecureApp vars = new VariablesSecureApp(request);
        
		//return message object
		final OBError msg = new OBError();
	    String pesan = "";
		
	    //get parameter
		String dateFormat = bundle.getContext().getJavaDateFormat();
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
	    String strDateAcct = (String) bundle.getParams().get("dateacct");
	    String orgId = (String) bundle.getParams().get("adOrgId");
	    Organization organization = OBDal.getInstance().get(Organization.class, orgId);
	    String docTypeId = (String) bundle.getParams().get("cDoctypeId");
	    DocumentType documentType = OBDal.getInstance().get(DocumentType.class, docTypeId);
	    Date dateAcct = sdf.parse(strDateAcct);
	    Date nextDateAcct = new Date(dateAcct.getTime()+oneDay);
	    
	    //retrieve distinct period and currency, then create GL Journal batch
	    final String strQuery="select distinct fa.accountingSchema.id, fa.period.year.id"
	    		+" from FinancialMgmtAccountingFact fa, Invoice i"
	    		+" where fa.recordID = i.id"
	    		+" and i.client.id=?"
	    		+" and i.outstandingAmount>0"
	    		+" and i.posted='Y'"
	    		+" and fa.accountingSchema.currency.id<>i.currency.id";
		final Query query = OBDal.getInstance().getSession().createQuery(strQuery);
		query.setParameter(0, OBContext.getOBContext().getCurrentClient().getId());
    	final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
    	
    	//loop through possible currency and period, to create GL Journal batch
    	while (result.next()) {
    		AcctSchema acctSchema = OBDal.getInstance().get(AcctSchema.class, result.get()[0]);
    		Year year = OBDal.getInstance().get(Year.class, result.get()[1]);
    		OBCriteria<Period> periodHeader = OBDal.getInstance().createCriteria(Period.class);
    		periodHeader.add(Restrictions.eq(Period.PROPERTY_YEAR, year));
    		periodHeader.add(Restrictions.le(Period.PROPERTY_STARTINGDATE, dateAcct));
    		periodHeader.add(Restrictions.ge(Period.PROPERTY_ENDINGDATE, dateAcct));
    		List<Period> periods = periodHeader.list();
    		if (periods.size()>1) {
    			throw new OBException("more than one period exisit on date " +dateAcct.toString()+" in year "+year.getFiscalYear());
    		} else if (periods.size()==0){
    			throw new OBException("no period found on date " +dateAcct.toString()+" in year "+year.getFiscalYear());
    		}
    		
	    	//get GL Journal batch document no
	    	String docnoBatch=Utility.getDocumentNo(bundle.getConnection(), OBContext.getOBContext().getCurrentClient().getId(), "GL_JournalBatch", true);
    		//create GL journal batch object
	    	GLBatch batch = OBProvider.getInstance().get(GLBatch.class);
	    	Period period = periods.get(0);
	    	OBCriteria<Period> nextPeriods = OBDal.getInstance().createCriteria(Period.class);
	    	nextPeriods.add(Restrictions.eq(Period.PROPERTY_YEAR, year));
	    	nextPeriods.add(Restrictions.le(Period.PROPERTY_STARTINGDATE, nextDateAcct));
	    	nextPeriods.add(Restrictions.ge(Period.PROPERTY_ENDINGDATE, nextDateAcct));
	    	List<Period> nextPeriodsList=nextPeriods.list();
	    	if (nextPeriodsList.size()==0) {
	    		throw new OBException("can not find next accounting period on "+nextDateAcct);
	    	}
	    	Period nextPeriod = nextPeriodsList.get(0);
	    	batch.setOrganization(organization);
	    	batch.setDescription("Unrealized gain/forex on "+period.getName());
	    	batch.setPostingType("A");
	    	batch.setDocumentDate(dateAcct);
	    	batch.setAccountingDate(dateAcct);
	    	batch.setPeriod(period);
	    	batch.setCurrency(acctSchema.getCurrency());
	    	batch.setDocumentNo(docnoBatch);
	    	OBDal.getInstance().save(batch);
	    	OBDal.getInstance().flush();
	    	pesan += docnoBatch+" ";
	    	
	    	//create query for GL journal header creation
	    	final String strQueryHeader="select distinct i.id"
		    		+" from FinancialMgmtAccountingFact fa, Invoice i"
		    		+" where fa.recordID = i.id"
		    		+" and fa.accountingSchema.id=?"
		    		+" and fa.period.year.id=?"
		    		+" and i.outstandingAmount>0"
		    		+" and i.posted='Y'"
		    		+" and fa.accountingSchema.currency.id<>i.currency.id";
	    	final Query queryHeader = OBDal.getInstance().getSession().createQuery(strQueryHeader);
	    	queryHeader.setParameter(0, acctSchema.getId());
	    	queryHeader.setParameter(1, year.getId());
	    	final ScrollableResults resultHeader = queryHeader.scroll(ScrollMode.FORWARD_ONLY);
	    	while (resultHeader.next()) {
	    		String invoiceID=(String) resultHeader.get()[0];
	    		Invoice invoice = OBDal.getInstance().get(Invoice.class, invoiceID);
	    		
	    		//calculate invoice amount in book currency on end of period
		    	BigDecimal endOfPeriodAmount=ConvertCurrency(invoice.getCurrency(), acctSchema.getCurrency(), dateAcct, invoice.getGrandTotalAmount());
		    	BigDecimal bookAmount=getInvoiceBookAmount(invoice, acctSchema);
		    	BigDecimal unrealizedAmount=endOfPeriodAmount.subtract(bookAmount);
		    	//continue if zero unrealizedAmount
		    	if (unrealizedAmount.signum()==0){
		    		continue;
		    	}
	    		
	    		//create GL journal header object for end of period
	    		GLJournal headerA = OBProvider.getInstance().get(GLJournal.class);
	    		headerA.setOrganization(organization);
	    		headerA.setAccountingSchema(acctSchema);
	    		headerA.setDocumentType(documentType);
	    		String docNoGLJournalHeaderA = Utility.getDocumentNo(bundle.getConnection(), vars, "132", "GL_Journal",
	    	            documentType.getId(), "0", true, true);
	    		headerA.setDocumentNo(docNoGLJournalHeaderA);
	    		headerA.setDocumentStatus("DR");
	    		headerA.setDocumentAction("CO");
	    		headerA.setApproved(false);
	    		headerA.setPrint(false);
	    		headerA.setDescription("Unrealized gain/forex for "
	    				+invoice.getTransactionDocument().getPrintText()
	    				+" "+invoice.getDocumentNo());
	    		headerA.setPostingType("A");
	    		headerA.setGLCategory(documentType.getGLCategory());
	    		headerA.setDocumentDate(dateAcct);
	    		headerA.setAccountingDate(dateAcct);
	    		headerA.setPeriod(period);
	    		headerA.setCurrency(acctSchema.getCurrency());
	    		headerA.setCurrencyRateType("S");
	    		headerA.setJournalBatch(batch);
	    		headerA.setOpening(false);
	    		OBDal.getInstance().save(headerA);
		    	OBDal.getInstance().flush();
		    	
		    	//create GL journal header object for beginning of next period
		    	GLJournal headerB = OBProvider.getInstance().get(GLJournal.class);
	    		headerB.setOrganization(organization);
	    		headerB.setAccountingSchema(acctSchema);
	    		headerB.setDocumentType(documentType);
	    		String docNoGLJournalHeaderB = Utility.getDocumentNo(bundle.getConnection(), vars, "132", "GL_Journal",
	    	            documentType.getId(), "0", true, true);
	    		headerB.setDocumentNo(docNoGLJournalHeaderB);
	    		headerB.setDocumentStatus("DR");
	    		headerB.setDocumentAction("CO");
	    		headerB.setApproved(false);
	    		headerB.setPrint(false);
	    		headerB.setDescription("Unrealized gain/forex for "
	    				+invoice.getTransactionDocument().getPrintText()
	    				+" "+invoice.getDocumentNo());
	    		headerB.setPostingType("A");
	    		headerB.setGLCategory(documentType.getGLCategory());
	    		headerB.setDocumentDate(nextDateAcct);
	    		headerB.setAccountingDate(nextDateAcct);
	    		headerB.setPeriod(nextPeriod);
	    		headerB.setCurrency(acctSchema.getCurrency());
	    		headerB.setCurrencyRateType("S");
	    		headerB.setJournalBatch(batch);
	    		headerB.setOpening(false);
	    		OBDal.getInstance().save(headerB);
		    	OBDal.getInstance().flush();
		    	
		    	//create 2 lines GL Journal Lines
		    	AccountingCombination unrealizedGainLossForex;
		    	AccountingCombination unrealizedForex;
		    	  
		    	  //get unrealized account element
		    	  List<AcctSchemaGL> acctschemaGL = acctSchema.getFinancialMgmtAcctSchemaGLList();
		    	  if (acctschemaGL.size()==0) {
		    		  throw new OBException("no general ledger found account found on General Leder "+acctSchema.getName());
		    	  }
	    		  //get realized AR/AP forex
		    	  if (invoice.isSalesTransaction()==true){
		    		  unrealizedForex=acctschemaGL.get(0).getOezRealizedArForex();
		    	  } else {
		    		  unrealizedForex=acctschemaGL.get(0).getOezRealizedApForex();
		    	  }
		    	  
	    		  if (unrealizedForex==null) {
	    			  throw new OBException("no unrealized forex account found on General Leder "+acctSchema.getName());
	    		  }
	    		  
	    		  //get unrealized gain (loss) forex account
	    		  if (invoice.isSalesTransaction()==true){
	    			  if(unrealizedAmount.signum()==1){	    				  
	    				  unrealizedGainLossForex=acctschemaGL.get(0).getOezUnrealizedgainforex();
	    			  } else {
	    				  unrealizedGainLossForex=acctschemaGL.get(0).getOezUnrealizedlossforex();
	    			  }
	    		  } else{
	    			  if(unrealizedAmount.signum()==1){
	    				  unrealizedGainLossForex=acctschemaGL.get(0).getOezUnrealizedlossforex();
	    			  } else {
	    				  unrealizedGainLossForex=acctschemaGL.get(0).getOezUnrealizedgainforex();
	    			  }
	    		  }
	    		  
	    		  if (unrealizedGainLossForex==null) {
	    			  throw new OBException("no unrealized gain (loss) forex account found on General Leder "+acctSchema.getName());
	    		  }
	    		  //create 1st line: debet end of month
	    		  GLJournalLine line = OBProvider.getInstance().get(GLJournalLine.class);
	    		  line.setJournalEntry(headerA);
	    		  line.setLineNo(new Long(10));
	    		  line.setGenerated(true);
	    		  if ((invoice.isSalesTransaction()==true && unrealizedAmount.signum()==1) || (invoice.isSalesTransaction()==false && unrealizedAmount.signum()==-1)){
	    			  line.setForeignCurrencyDebit(unrealizedAmount);
		    		  line.setDebit(unrealizedAmount);
	    		  } else {
	    			  line.setForeignCurrencyCredit(unrealizedAmount);
	    			  line.setCredit(unrealizedAmount);		    		  
	    		  }
	    		  line.setCurrency(acctSchema.getCurrency());
	    		  line.setCurrencyRateType("S");
	    		  line.setRate(new BigDecimal(1));
	    		  line.setAccountingDate(dateAcct);	    		  
	    		  line.setAccountingCombination(unrealizedForex);
	    		  OBDal.getInstance().save(line);
	    		  OBDal.getInstance().flush();
	    		  line=null;
	    		  //create 2nd line: credit end of month	    		  
	    		  line=OBProvider.getInstance().get(GLJournalLine.class);
	    		  line.setJournalEntry(headerA);
	    		  line.setLineNo(new Long(20));
	    		  line.setGenerated(true);
	    		  if ((invoice.isSalesTransaction()==true && unrealizedAmount.signum()==1) || (invoice.isSalesTransaction()==false && unrealizedAmount.signum()==-1)){
	    			  line.setForeignCurrencyCredit(unrealizedAmount);
	    			  line.setCredit(unrealizedAmount);
	    		  } else {
	    			  line.setForeignCurrencyDebit(unrealizedAmount);
	    			  line.setDebit(unrealizedAmount);
	    		  }	    		  
	    		  line.setCurrency(acctSchema.getCurrency());	    		  
	    		  line.setCurrencyRateType("S");
	    		  line.setRate(new BigDecimal(1));
	    		  line.setAccountingDate(dateAcct);
	    		  line.setAccountingCombination(unrealizedGainLossForex);
	    		  OBDal.getInstance().save(line);
	    		  OBDal.getInstance().flush();
	    		  line=null;
	    		  //create 3rd line: debet beginning of next month
	    		  line = OBProvider.getInstance().get(GLJournalLine.class);
	    		  line.setJournalEntry(headerB);
	    		  line.setLineNo(new Long(10));
	    		  line.setGenerated(true);
	    		  if ((invoice.isSalesTransaction()==true && unrealizedAmount.signum()==1) || (invoice.isSalesTransaction()==false && unrealizedAmount.signum()==-1)){
	    			  line.setForeignCurrencyDebit(unrealizedAmount);
	    			  line.setDebit(unrealizedAmount);
	    		  } else {
	    			  line.setForeignCurrencyCredit(unrealizedAmount);
		    		  line.setCredit(unrealizedAmount);		    		  
	    		  }	    		  
	    		  line.setCurrency(acctSchema.getCurrency());	    		  
	    		  line.setCurrencyRateType("S");
	    		  line.setRate(new BigDecimal(1));
	    		  line.setAccountingDate(nextDateAcct);
	    		  line.setAccountingCombination(unrealizedGainLossForex);
	    		  OBDal.getInstance().save(line);
	    		  OBDal.getInstance().flush();
	    		  line=null;
	    		  //create 4th line: credit end of month	    		  
	    		  line=OBProvider.getInstance().get(GLJournalLine.class);
	    		  line.setJournalEntry(headerB);
	    		  line.setLineNo(new Long(20));
	    		  line.setGenerated(true);
	    		  if ((invoice.isSalesTransaction()==true && unrealizedAmount.signum()==1) || (invoice.isSalesTransaction()==false && unrealizedAmount.signum()==-1)){
	    			  line.setForeignCurrencyCredit(unrealizedAmount);
		    		  line.setCredit(unrealizedAmount);	    			  
	    		  } else {
	    			  line.setForeignCurrencyDebit(unrealizedAmount);
	    			  line.setDebit(unrealizedAmount);	    			  		    		  
	    		  }
	    		  line.setCurrency(acctSchema.getCurrency());
	    		  line.setCredit(unrealizedAmount);
	    		  line.setCurrencyRateType("S");
	    		  line.setRate(new BigDecimal(1));
	    		  line.setAccountingDate(nextDateAcct);
	    		  line.setAccountingCombination(unrealizedForex);
	    		  OBDal.getInstance().save(line);
	    		  OBDal.getInstance().flush();
	    		  	    		
	    	}
    	}
    	
    	OBDal.getInstance().commitAndClose();
    	
    	//create return message
    	msg.setType("Success");
        msg.setTitle("Unrealized gain (loss) forex GL Journal");
        msg.setMessage("Unrealized gain (loss) forex GL Journal has been created sucessfull. GL Journal Batch Document No:" + pesan
            + ".");
        bundle.setResult(msg);

	}
	
	private BigDecimal getInvoiceBookAmount(Invoice invoice, AcctSchema acctSchema){
		
		ElementValue bpAccount;
		BigDecimal invoiceBookAmount=null;
		if (invoice.isSalesTransaction()==true){
			OBCriteria<CustomerAccounts> customeraccount = OBDal.getInstance().createCriteria(CustomerAccounts.class);
			customeraccount.add(Restrictions.eq(CustomerAccounts.PROPERTY_ACCOUNTINGSCHEMA, acctSchema));
			customeraccount.add(Restrictions.eq(CustomerAccounts.PROPERTY_BUSINESSPARTNER, invoice.getBusinessPartner()));
			List<CustomerAccounts> customeraccounts=customeraccount.list();
			if (customeraccounts.size()==0){
				throw new OBException("no receivable account found for "+invoice.getBusinessPartner().getName()
						+" on General Ledger "+acctSchema.getName());
			}
			bpAccount = customeraccounts.get(0).getCustomerReceivablesNo().getAccount();
		} else {
			OBCriteria<VendorAccounts> vendoraccount = OBDal.getInstance().createCriteria(VendorAccounts.class);
			vendoraccount.add(Restrictions.eq(CustomerAccounts.PROPERTY_ACCOUNTINGSCHEMA, acctSchema));
			vendoraccount.add(Restrictions.eq(CustomerAccounts.PROPERTY_BUSINESSPARTNER, invoice.getBusinessPartner()));
			List<VendorAccounts> vendoraccounts=vendoraccount.list();
			if (vendoraccounts.size()==0){
				throw new OBException("no payable account found for "+invoice.getBusinessPartner().getName()
						+" on General Ledger "+acctSchema.getName());
			}
			bpAccount = vendoraccounts.get(0).getVendorLiability().getAccount();
		}
		
		final String strQueryInvoice="select sum(debit-credit) as amount"
				+" from FinancialMgmtAccountingFact fa"
				+" where id.recordID=?"
				+" and account.id=?";
		final Query queryInvoice = OBDal.getInstance().getSession().createQuery(strQueryInvoice);
    	queryInvoice.setParameter(0, invoice.getId());
    	queryInvoice.setParameter(1, bpAccount.getId());
    	final ScrollableResults resultInvoice = queryInvoice.scroll(ScrollMode.FORWARD_ONLY);
    	while (resultInvoice.next()) {
    		invoiceBookAmount=(BigDecimal) resultInvoice.get()[0];
    		break;
    	}
		
		return invoiceBookAmount;
	}
	
	private BigDecimal ConvertCurrency(Currency currencyFrom, Currency currencyTo, Date date, BigDecimal amount){
		OBCriteria<ConversionRate> conversionrate = OBDal.getInstance().createCriteria(ConversionRate.class);
		conversionrate.add(Restrictions.eq(ConversionRate.PROPERTY_CURRENCY, currencyFrom));
		conversionrate.add(Restrictions.eq(ConversionRate.PROPERTY_TOCURRENCY, currencyTo));
		conversionrate.add(Restrictions.le(ConversionRate.PROPERTY_VALIDFROMDATE, date));
		conversionrate.add(Restrictions.ge(ConversionRate.PROPERTY_VALIDTODATE, date));
		List<ConversionRate> conversionrateList= conversionrate.list();
		if (conversionrateList.size()>1){
			throw new OBException("more than conversion rate from "+currencyFrom.getISOCode()+" to "+currencyTo.getISOCode()+" on "+date);
		}else if (conversionrateList.size()==0){
			throw new OBException("no conversion rate from "+currencyFrom.getISOCode()+" to "+currencyTo.getISOCode()+" on "+date);
		}
		BigDecimal multilpyByRate=conversionrateList.get(0).getMultipleRateBy();
		
		return multilpyByRate.multiply(amount);
	}
	
}
