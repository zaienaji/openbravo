package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceLine;
import org.openbravo.model.financialmgmt.gl.GLItem;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.importmasterdata.oez_i_bpartnerbalance;

import com.google.common.collect.HashBasedTable;

public class ImportBPBalance extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		
		//preloaded variable
		final HashBasedTable<Currency, Boolean, PriceList> priceListMap = loadPriceListMap(); //maping of currency, isSalesPriceList --> PriceList
		final DocumentType newDocType = OBDal.getInstance().get(DocumentType.class, "0");
		final DocumentType arInvoice = getDefaultDocumentType("ARI");
		if (arInvoice==null)
			throw new OBException("can not find document type for AR invocie");
		final DocumentType apInvoice = getDefaultDocumentType("API");
		if (apInvoice==null)
			throw new OBException("can not find document type for AP invocie");
		final PaymentTerm paymentTerm = getDefaultPaymentTerm();
		if (paymentTerm==null)
			throw new OBException("payment term not defined");
		final TaxRate taxrate = getDefaultTaxRate(true);
		if (taxrate==null)
			throw new OBException("tax rate (tax exempt) not defined");
		final FIN_PaymentMethod paymentMethod = getDefaultPaymentMethod();
		if (paymentMethod==null)
			throw new OBException("payment method not defined");
		
		//get valid import bp balance to be executed
		String query = "select a.oez_i_bpartnerbalance_id, a.value as bpkey, b.c_bpartner_id,"
				+ " a.glitemname, c.c_glitem_id, a.currencycode, d.c_currency_id,"
				+ " a.dateplanned, a.amount, a.isreceipt,"
				+ " (select c_bpartner_location_id from c_bpartner_location where c_bpartner_id=b.c_bpartner_id limit 1) as c_bpartner_location_id"
				+ " from oez_i_bpartnerbalance a"
				+ " left join c_bpartner b on b.value=a.value and b.ad_client_id=a.ad_client_id"
				+ " left join c_glitem c on c.name=a.glitemname and c.ad_client_id=a.ad_client_id"
				+ " left join c_currency d on d.iso_code=a.currencycode and d.ad_client_id in ('0', a.ad_client_id)"
				+ " where a.processed='N'";
		ConnectionProvider conn = new DalConnectionProvider();
		Connection connection = conn.getConnection();
		PreparedStatement ps = connection.prepareStatement(query);
		ResultSet rs = ps.executeQuery();
		while (rs.next()){
			//executing for each valid record
			
			//validating parameters
			StringBuilder sb = new StringBuilder();
			String recordID = rs.getString("oez_i_bpartnerbalance_id");
			oez_i_bpartnerbalance bpbalance = OBDal.getInstance().get(oez_i_bpartnerbalance.class, recordID);
			
			if (bpbalance==null)
				continue; //invalid entiti ID
			
			String bpID = rs.getString("c_bpartner_id");
			BusinessPartner bp = null;
			if (bpID==null || bpID.isEmpty() || bpID.length()==0){
				String bpKey = rs.getString("bpkey");
				sb.append("can not find BP ID with bp key "+bpKey).append("\n");
			} else {
				bp = OBDal.getInstance().get(BusinessPartner.class, bpID);
			}
			if (bp==null)
				sb.append(bpID+" is not valid BP ID").append("\n");
			
			String glID = rs.getString("c_glitem_id");
			GLItem gl = null;
			if (glID==null || glID.isEmpty() || glID.length()==0){
				String glKey = rs.getString("glitemname");
				sb.append("can not find GL item with name "+glKey).append("\n");
			} else {
				gl = OBDal.getInstance().get(GLItem.class, glID);
			}
			if (gl==null)
				sb.append(glID+" is not valid GL ID").append("\n");
			
			String isreceipt = rs.getString("isreceipt");
			boolean isAR = false;
			if (isreceipt!=null && isreceipt.equalsIgnoreCase("Y"))
				isAR=true;
			
			String currencyID = rs.getString("c_currency_id");
			Currency currency = null; 
			if (currencyID==null || currencyID.isEmpty() || currencyID.length()==0){
				String currencyCode = rs.getString("currencycode");
				sb.append("can not find currency with iso code "+currencyCode).append("\n");
			} else {
				currency = OBDal.getInstance().get(Currency.class, currencyID);
			}
			if (currency==null)
				sb.append(currencyID+" is not valid currency ID").append("\n");
			if (!priceListMap.contains(currency, isAR)){
				if (isAR)
					sb.append("can not find sales price list with currency "+currency.getISOCode()+"").append("\n");
				else
					sb.append("can not find purchase price list with currency "+currency.getISOCode()+"").append("\n");
			}
			
			Date dateplanned = rs.getDate("dateplanned");
			if (dateplanned==null)
				dateplanned = new Date(); //jika null, pakai tanggal hari ini
			
			BigDecimal amount = rs.getBigDecimal("amount");
			if (amount==null)
				amount = BigDecimal.ZERO;
			
			String bpLocationID = rs.getString("c_bpartner_location_id");
			if (bpLocationID==null || bpLocationID.isEmpty() || bpLocationID.length()==0){
				String bpKey = rs.getString("bpkey");
				sb.append("can not find business partner address with bp key "+bpKey).append("\n");
			}
			Location bpAddress = OBDal.getInstance().get(Location.class, bpLocationID);
			if (bpAddress==null)
				sb.append(bpLocationID+" is not valid business partner address ID").append("\n");
			
			//eror handling
			if (sb.length()>0){
				//ada error
				String errormessage = sb.toString();
				bpbalance.setImportErrorMessage(errormessage);
				bpbalance.setImportProcessComplete(false);
				bpbalance.setProcessed(false);
				OBDal.getInstance().save(bpbalance);
				continue;
			}
			
			//no error, execute business logic
			//1 of 4: create invoice header
			Invoice invoice = OBProvider.getInstance().get(Invoice.class);
			invoice.setDocumentNo("<>");
			invoice.setBusinessPartner(bp);
			invoice.setInvoiceDate(dateplanned);
			invoice.setAccountingDate(dateplanned);
			invoice.setSalesTransaction(isAR);
			invoice.setCurrency(currency);
			PriceList priceList = priceListMap.get(currency, isAR);
			invoice.setPriceList(priceList);
			invoice.setPriceIncludesTax(priceList.isPriceIncludesTax());
			invoice.setDocumentStatus("DR");
			invoice.setDocumentAction("CO");
			invoice.setProcessNow(false);
			invoice.setProcessed(false);
			invoice.setDocumentType(newDocType);
			if (isAR)
				invoice.setTransactionDocument(arInvoice);
			else
				invoice.setTransactionDocument(apInvoice);
			invoice.setDescription("opening balance entry");
			invoice.setPartnerAddress(bpAddress);
			invoice.setFormOfPayment("P");
			invoice.setPaymentTerms(paymentTerm);
			invoice.setPaymentMethod(paymentMethod);
			
			OBDal.getInstance().save(invoice);
			
			//2 of 4: create invoice line
			InvoiceLine invoiceLine = OBProvider.getInstance().get(InvoiceLine.class);
			invoiceLine.setInvoice(invoice);
			invoiceLine.setFinancialInvoiceLine(true);
			invoiceLine.setAccount(gl);
			invoiceLine.setInvoicedQuantity(new BigDecimal(1));
			invoiceLine.setUnitPrice(amount);
			invoiceLine.setLineNetAmount(amount);
			invoiceLine.setTaxableAmount(amount);
			invoiceLine.setTaxAmount(BigDecimal.ZERO);
			invoiceLine.setTax(taxrate);
			invoiceLine.setLineNo(new Long(10));
			invoiceLine.setBusinessPartner(bp);
			
			OBDal.getInstance().save(invoiceLine);
			
			//update temp table
			bpbalance.setBusinessPartner(bp);
			bpbalance.setGLItem(gl);
			bpbalance.setCurrency(currency);
			bpbalance.setImportProcessComplete(true);
			bpbalance.setImportErrorMessage(null);
			bpbalance.setProcessed(true);
			OBDal.getInstance().save(bpbalance);
			
			//3 fo 4: commit transaction
			ConnectionProvider conn2 = new DalConnectionProvider();
			Connection connection2 = conn2.getConnection();
			connection2.commit();
			
			//4 of 4: doComplete
			String query2 = "select * from c_invoice_post(null, ?)";
			PreparedStatement ps2 = connection2.prepareStatement(query2);
			ps2.setString(1, invoice.getId());
			ps2.execute();
			connection2.commit();
				
		}

	}

	private TaxRate getDefaultTaxRate(boolean isTaxExempt) {
		TaxRate output = null;
		OBCriteria<TaxRate> trC = OBDal.getInstance().createCriteria(TaxRate.class);
		trC.add(Restrictions.eq(TaxRate.PROPERTY_TAXEXEMPT, isTaxExempt));
		trC.addOrderBy(TaxRate.PROPERTY_DEFAULT, false);
		trC.setFetchSize(1);
		for (TaxRate tr : trC.list()){
			output = tr;
		}
		return output;
	}

	private FIN_PaymentMethod getDefaultPaymentMethod() {
		FIN_PaymentMethod output = null;
		OBCriteria<FIN_PaymentMethod> pmC = OBDal.getInstance().createCriteria(FIN_PaymentMethod.class);
		pmC.setFetchSize(1);
		for (FIN_PaymentMethod pm : pmC.list()){
			output = pm;
		}
		return output;
	}

	private PaymentTerm getDefaultPaymentTerm() {
		PaymentTerm output = null;
		OBCriteria<PaymentTerm> ptC = OBDal.getInstance().createCriteria(PaymentTerm.class);
		ptC.addOrderBy(PaymentTerm.PROPERTY_DEFAULT, false);
		ptC.setFetchSize(1);
		for (PaymentTerm pt : ptC.list()){
			output = pt;
			break;
		}
		return output;
	}

	private DocumentType getDefaultDocumentType(String docBaseType) {
		DocumentType output = null;
		OBCriteria<DocumentType> dtC = OBDal.getInstance().createCriteria(DocumentType.class);
		dtC.add(Restrictions.eq(DocumentType.PROPERTY_DOCUMENTCATEGORY, docBaseType)); //ar invoice
		dtC.addOrderBy(DocumentType.PROPERTY_DEFAULT, false);
		dtC.setFetchSize(1);
		for (DocumentType dt : dtC.list()){
			output = dt;
			break;
		}
			
		return output;
	}

	private HashBasedTable<Currency, Boolean, PriceList> loadPriceListMap() {
		HashBasedTable<Currency, Boolean, PriceList> output = HashBasedTable.create();
		OBCriteria<PriceList> plC = OBDal.getInstance().createCriteria(PriceList.class);
		for (PriceList pl : plC.list()){
			Currency currency = pl.getCurrency();
			boolean isSalePriceList = pl.isSalesPriceList();
			output.put(currency, isSalePriceList, pl);
		}
		return output;
	}

}
