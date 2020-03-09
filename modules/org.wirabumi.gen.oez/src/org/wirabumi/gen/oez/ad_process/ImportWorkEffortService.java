package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.exception.NoConnectionAvailableException;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.manufacturing.transaction.WorkRequirementOperation;
import org.openbravo.model.materialmgmt.transaction.ProductionLine;
import org.openbravo.model.materialmgmt.transaction.ProductionPlan;
import org.openbravo.model.materialmgmt.transaction.ProductionTransaction;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort;

import com.google.common.collect.HashBasedTable;

public class ImportWorkEffortService {
	
	private final static String lineSeparator = System.getProperty("line.separator");
	
	// Prevents instantiation (Item 4)
	private ImportWorkEffortService(){
		
	}
	
	public static OBError doImportProcess(List<org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort> pendingImportWorkEfforts){
		
		String clientID = OBContext.getOBContext().getCurrentClient().getId();
		
		Connection connection;
		try {
			connection = new DalConnectionProvider().getConnection();
		} catch (NoConnectionAvailableException e) {
			e.printStackTrace();
			throw new OBException(e.getMessage());
		}
		
		HashMap<WorkRequirementOperation, PendingWorkRequirementBean> pendingWorkRequirementMap = getPendingWorkRequirementMap(connection, clientID);
		List<DraftWorkEffortBean> draftWorkEffort = getDraftWorkEffort(connection, clientID);
		
		//pisahkan DraftWorkEffortBean menjadi ArrayList kecil kecil
		DecomposeDraftWorkEffortBean ddweb = deComposeWorkEffortBean(draftWorkEffort);
		//documentNo --> work effort object
		HashMap<String, ProductionTransaction> workEffortMap = ddweb.workEffortMap;
		//documentNo+WRNo+WRPhaseSeqno --> production run object
		HashMap<String, ProductionPlan> productionRunMap = ddweb.productionRunMap;
		//production run dan product --> production line object
		HashBasedTable<ProductionPlan, Product, ProductionLine> productionLineMap = ddweb.productionLineMap;
		
		//buat hashmap wr number + seqno --> wr phase object
		HashBasedTable<String, Long, WorkRequirementOperation> wrPhaseMap = getWRPhaseMap(pendingWorkRequirementMap.keySet());
		
		//buat hashmap product key --> product
		HashMap<String, Product> productMap = getProductMap();
		
		//buat hashmap search key --> storagebin
		HashMap<String, Locator> storageBinMap = getStorageBinMap();
		
		long lineno=0;
		ProductionPlan prevProductionRun=null;
		for (org.wirabumi.gen.oez.importmasterdata.ImportWorkEffort pendingImportWorkEffort : pendingImportWorkEfforts){
			boolean isvalid = validatePendingImportWorkEffort(pendingImportWorkEffort, wrPhaseMap, productMap, storageBinMap);
			if (!isvalid)
				continue;
			
			//processing work effort header, jika ada, pakai yg sudah ada. jika blm ada, maka buat object baru.
			String production_documentno = pendingImportWorkEffort.getDocumentno();
			ProductionTransaction production;
			if (workEffortMap.containsKey(production_documentno))
				production = workEffortMap.get(production_documentno);
			else
				production = OBProvider.getInstance().get(ProductionTransaction.class);
			
			production.setDocumentNo(production_documentno);
			Date movementdate = pendingImportWorkEffort.getMovementdate();
			production.setMovementDate(movementdate);
			Calendar cal = Calendar.getInstance();
			cal.setTime(movementdate);
			int starthour = pendingImportWorkEffort.getStartinghour().intValue();
			cal.set(Calendar.HOUR, starthour);
			Timestamp starttime = new Timestamp(cal.getTimeInMillis());
			production.setStartingTime(starttime);
			int endhour = pendingImportWorkEffort.getEndinghour().intValue();
			if (endhour<starthour)
				cal.add(Calendar.DAY_OF_MONTH, 1);
			cal.set(Calendar.HOUR, endhour);
			Timestamp endtime = new Timestamp(cal.getTimeInMillis());
			production.setEndingTime(endtime);
			
			OBDal.getInstance().save(production);
			workEffortMap.put(production_documentno, production);
				
			pendingImportWorkEffort.setProduction(production);
			OBDal.getInstance().save(pendingImportWorkEffort);
			
			//processing work effort || production run
			//pastikan dulu wrPhase dan wrNo nya valid, baru cari apakah sudah ada exisiting object apa belum.
			String wr_docno = pendingImportWorkEffort.getWrdocno();
			Long wr_seqno = pendingImportWorkEffort.getWrseqno();
			WorkRequirementOperation wrPhase;
			if (!wrPhaseMap.contains(wr_docno, wr_seqno)){
				//wrPhase tidak valid, maka tandai.
				pendingImportWorkEffort.setImported(false);
				String errormessage = pendingImportWorkEffort.getImportErrorMessage();
				if (errormessage==null)
					errormessage = "can not find work requirement operation with document no "+wr_docno+" and sequence no "+wr_seqno.toString();
				else
					errormessage = errormessage.concat(lineSeparator).concat("can not find work requirement operation with document no "+wr_docno+" and sequence no "+wr_seqno.toString());
				pendingImportWorkEffort.setImportErrorMessage(errormessage);
				OBDal.getInstance().save(pendingImportWorkEffort);
				continue;
			} else
				wrPhase = wrPhaseMap.get(wr_docno, wr_seqno);
				
			String productionRunIdentifier = production_documentno.concat(wr_docno).concat(wr_seqno.toString());
			ProductionPlan productionRun;
			if (productionRunMap.containsKey(productionRunIdentifier))
				productionRun = productionRunMap.get(productionRunIdentifier);
			else
				productionRun = OBProvider.getInstance().get(ProductionPlan.class);
			
			productionRun.setProduction(production);
			productionRun.setLineNo(wr_seqno);
			productionRun.setWRPhase(wrPhase);
			cal.setTime(starttime);
			productionRun.setStartingTime(cal.getTime());
			cal.setTime(endtime);
			productionRun.setEndingTime(cal.getTime());
			BigDecimal pendingqty = wrPhase.getQuantity().subtract(wrPhase.getCompletedQuantity());
			productionRun.setRequiredQuantity(pendingqty);
			productionRun.setProductionQuantity(pendingImportWorkEffort.getCompleteqty());
			productionRun.setRejectedQuantity(pendingImportWorkEffort.getRejectedqty());
			productionRun.setOutsourced(pendingImportWorkEffort.isOursourced());
			productionRun.setClosephase(pendingImportWorkEffort.isClosephase());
			
			OBDal.getInstance().save(productionRun);
			productionRunMap.put(productionRunIdentifier, productionRun);
			
			pendingImportWorkEffort.setWrphase(wrPhase);
			OBDal.getInstance().save(pendingImportWorkEffort);
			
			//processing work effort || production run
			//pastikan dulu wrPhase dan wrNo nya valid, baru cari apakah sudah ada exisiting object apa belum.
			String productkey = pendingImportWorkEffort.getProductkey();
			if (!productMap.containsKey(productkey)){
				pendingImportWorkEffort.setImported(false);
				String errormessage = pendingImportWorkEffort.getImportErrorMessage();
				if (errormessage==null)
					errormessage = "can not find product with search key "+productkey;
				else
					errormessage = errormessage.concat(lineSeparator).concat("can not find product with search key "+productkey);
				pendingImportWorkEffort.setImportErrorMessage(errormessage);
				OBDal.getInstance().save(pendingImportWorkEffort);
				continue;
			}
			Product product = productMap.get(productkey);
			
			String locatorKey = pendingImportWorkEffort.getStoragekey();
			if (!storageBinMap.containsKey(locatorKey)){
				pendingImportWorkEffort.setImported(false);
				String errormessage = pendingImportWorkEffort.getImportErrorMessage();
				if (errormessage==null)
					errormessage = "can not find storage bin with search key "+locatorKey;
				else
					errormessage = errormessage.concat(lineSeparator).concat("can not find storage bin with search key "+locatorKey);
				pendingImportWorkEffort.setImportErrorMessage(errormessage);
				OBDal.getInstance().save(pendingImportWorkEffort);
				continue;
			}
			Locator storageBin = storageBinMap.get(locatorKey);
			
			
			ProductionLine productionLine;
			if (productionLineMap.contains(productionRun, product))
				productionLine = productionLineMap.get(productionRun, product);
			else
				productionLine = OBProvider.getInstance().get(ProductionLine.class);
			
			productionLine.setProductionPlan(productionRun);
			if (prevProductionRun==null || !prevProductionRun.getId().equals(productionRun.getId()))
				lineno=10;
			else
				lineno +=10;
			productionLine.setLineNo(lineno);
			productionLine.setProduct(product);
			productionLine.setProductionType(pendingImportWorkEffort.getProductiontype());
			productionLine.setMovementQuantity(pendingImportWorkEffort.getQuantity());
			productionLine.setRejectedQuantity(pendingImportWorkEffort.getProductRejectQty());
			productionLine.setStorageBin(storageBin);
			productionLine.setUOM(product.getUOM());
			OBDal.getInstance().save(productionLine);
			productionLineMap.put(productionRun, product, productionLine);
			
			pendingImportWorkEffort.setProduct(product);
			pendingImportWorkEffort.setProductionline(productionLine);
			pendingImportWorkEffort.setImported(true);
			pendingImportWorkEffort.setImportErrorMessage(null);
			OBDal.getInstance().save(pendingImportWorkEffort);
			
			prevProductionRun=productionRun;
				
		}
		
		OBError result = new OBError();
		result.setTitle("Success");
		result.setType("Success");
		result.setMessage("Import work effort executed successfully.");
		
		return result;
	}
	
	private static HashMap<String, Locator> getStorageBinMap() {
		HashMap<String, Locator> output = new HashMap<String, Locator>();
		OBCriteria<Locator> locatorC = OBDal.getInstance().createCriteria(Locator.class);
		for (Locator locator : locatorC.list()){
			String key = locator.getSearchKey();
			output.put(key, locator);
		}
		return output;
	}

	private static HashMap<String, Product> getProductMap() {
		HashMap<String, Product> output = new HashMap<String, Product>();
		OBCriteria<Product> productC = OBDal.getInstance().createCriteria(Product.class);
		productC.add(Restrictions.eq(Product.PROPERTY_PRODUCTION, true));
		for (Product product : productC.list()){
			String key = product.getSearchKey();
			output.put(key, product);
		}
		return output;
	}

	private static boolean validatePendingImportWorkEffort(ImportWorkEffort pendingImportWorkEffort,
			HashBasedTable<String, Long, WorkRequirementOperation> wrPhaseMap,
			HashMap<String, Product> productMap,
			HashMap<String, Locator> locatorMap) {
		StringBuilder sb = new StringBuilder(0);
		
		//validating header
		String production_documentno = pendingImportWorkEffort.getDocumentno();
		if (production_documentno==null || production_documentno.isEmpty())
			sb.append("document number is empty").append(lineSeparator);
		
		//validating production run
		String wr_number = pendingImportWorkEffort.getWrdocno();
		if (wr_number==null || wr_number.isEmpty())
			sb.append("work requirement number is empty").append(lineSeparator);
		Long wrphase_seqno = pendingImportWorkEffort.getWrseqno();
		if (wrphase_seqno==null)
			sb.append("work requirement operation sequence number is empty").append(lineSeparator);
		if (wr_number!=null && !wr_number.isEmpty() && wrphase_seqno!=null){
			if (!wrPhaseMap.contains(wr_number, wrphase_seqno))
				sb.append("can not find work requirement number ")
				.append(wr_number)
				.append(" with operation sequence number ")
				.append(wrphase_seqno)
				.append(". may be the work requirment is done.")
				.append(lineSeparator);
		}
		
		//validating production line
		String productkey = pendingImportWorkEffort.getProductkey();
		if (productkey==null || productkey.isEmpty())
			sb.append("product key is empty").append(lineSeparator);
		if (!productMap.containsKey(productkey))
			sb.append("can not find product with key ").append(productkey).append(lineSeparator);
		
		//validating storage bin
		String locatorkey = pendingImportWorkEffort.getStoragekey();
		if (locatorkey==null || locatorkey.isEmpty())
			sb.append("storagebin key is empty").append(lineSeparator);
		if (!locatorMap.containsKey(locatorkey))
			sb.append("can not find storage bin with key ").append(locatorkey).append(lineSeparator);
		
		if (sb.length()>0){
			pendingImportWorkEffort.setImported(false);
			pendingImportWorkEffort.setImportErrorMessage(sb.toString());
			OBDal.getInstance().save(pendingImportWorkEffort);
			return false;
		} else
			return true;
	}

	private static HashBasedTable<String, Long, WorkRequirementOperation> getWRPhaseMap(
			Set<WorkRequirementOperation> keySet) {
		HashBasedTable<String, Long, WorkRequirementOperation> output = HashBasedTable.create();
		for (WorkRequirementOperation wrPhase : keySet){
			Long seqno = wrPhase.getSequenceNumber();
			String documentno = wrPhase.getWorkRequirement().getDocumentNo();
			output.put(documentno, seqno, wrPhase);
		}
		return output;
	}

	private static HashMap<WorkRequirementOperation, PendingWorkRequirementBean> getPendingWorkRequirementMap(Connection connection, String clientID) {
		HashMap<WorkRequirementOperation, PendingWorkRequirementBean> output = new HashMap<WorkRequirementOperation, PendingWorkRequirementBean>();
		String sql = "select	b.ma_wrphase_id, a.documentno, coalesce(a.quantity,0) as wr_quantity, coalesce(b.quantity,0) as quantity,"
				+ " coalesce(b.donequantity,0) as donequantity, (coalesce(b.quantity,0)-coalesce(b.donequantity,0)) as pendingquantity,"
				+ " a.ma_processplan_id, b.ma_process_id, b.ma_sequence_id,"
				+ " c.value as processplan_code, c.name as processplan_name,"
				+ " d.name as activity_name, d.value as activity_code,"
				+ " e.name as operation_name, e.value as operation_code"
				+ " from MA_WorkRequirement a"
				+ " inner join MA_WRPhase b on b.ma_workrequirement_id=a.ma_workrequirement_id"
				+ " inner join ma_processplan c on c.ma_processplan_id=a.ma_processplan_id"
				+ " inner join ma_process d on d.ma_process_id=b.ma_process_id"
				+ " inner join ma_sequence e on e.ma_sequence_id=b.ma_sequence_id"
				+ " where a.closed='N'"
				+ " and b.quantity!=b.donequantity"
				+ " and a.ad_client_id=?";
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			ps.setString(1, clientID);
			ResultSet rs = ps.executeQuery();
			while (rs.next()){
				String ma_wrphase_id = rs.getString("ma_wrphase_id");
				if (ma_wrphase_id==null || ma_wrphase_id.isEmpty())
					continue;
				WorkRequirementOperation wrphase = OBDal.getInstance().get(WorkRequirementOperation.class, ma_wrphase_id);
				if (wrphase==null)
					continue;
				
				String documentno = rs.getString("documentno");
				BigDecimal wr_quantity = rs.getBigDecimal("wr_quantity");
				BigDecimal quantity = rs.getBigDecimal("quantity");
				BigDecimal donequantity = rs.getBigDecimal("donequantity");
				BigDecimal pendingquantity = rs.getBigDecimal("pendingquantity");
				String ma_processplan_id = rs.getString("ma_processplan_id");
				String ma_process_id = rs.getString("ma_process_id");
				String ma_sequence_id = rs.getString("ma_sequence_id");
				String processplan_code = rs.getString("processplan_code");
				String processplan_name = rs.getString("processplan_name");
				String activity_name = rs.getString("activity_name");
				String activity_code = rs.getString("activity_code");
				String operation_name = rs.getString("operation_name");
				String operation_code = rs.getString("operation_code");
				
				PendingWorkRequirementBean pwrb = new PendingWorkRequirementBean();
				pwrb.setDocumentNo(documentno);
				pwrb.setWr_quantity(wr_quantity.doubleValue());
				pwrb.setQuantity(quantity.doubleValue());
				pwrb.setDonequantity(donequantity.doubleValue());
				pwrb.setPendingquantity(pendingquantity.doubleValue());
				pwrb.setMa_processplan_id(ma_processplan_id);
				pwrb.setMa_process_id(ma_process_id);
				pwrb.setMa_sequence_id(ma_sequence_id);
				pwrb.setProcessplan_code(processplan_code);
				pwrb.setProcessplan_name(processplan_name);
				pwrb.setActivity_code(activity_code);
				pwrb.setActivity_name(activity_name);
				pwrb.setOperation_code(operation_code);
				pwrb.setOperation_name(operation_name);
				
				output.put(wrphase, pwrb);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new OBException(e.getMessage());
		}
		
		return output;
	}
	
	private static List<DraftWorkEffortBean> getDraftWorkEffort(Connection connection, String clientID) {
		List<DraftWorkEffortBean> output = new ArrayList<DraftWorkEffortBean>();
		String sql ="select	a.m_production_id, a.documentno, a.starttime, a.endtime, b.m_productionplan_id, b.processed, b.ma_wrphase_id,"
				+ "	coalesce(b.neededquantity,0) as neededquantity, coalesce(b.rejectedquantity,0) as rejectedquantity,"
				+ " e.ma_workrequirement_id, e.documentno as wr_number,d.ma_wrphase_id, d.seqno as wrphase_seqno,"
				+ " c.m_productionline_id, c.m_product_id, f.name as product_name, f.value as product_code"
				+ " from M_Production a"
				+ " left join M_ProductionPlan b on b.m_production_id=a.m_production_id"
				+ " left join M_ProductionLine c on c.m_productionplan_id=b.m_productionplan_id and b.processed='N'"
				+ " left join ma_wrphase d on d.ma_wrphase_id=b.ma_wrphase_id"
				+ " left join ma_workrequirement e on e.ma_workrequirement_id=d.ma_workrequirement_id"
				+ " left join m_product f on f.m_product_id=c.m_product_id"
				+ " where a.ad_client_id=?"
				+ " and a.processed='N'";
		PreparedStatement ps;
		try {
			ps = connection.prepareStatement(sql);
			ps.setString(1, clientID);
			ResultSet rs = ps.executeQuery();
			while (rs.next()){
				String m_production_id = rs.getString("m_production_id");
				String documentno = rs.getString("documentno");
				Date starttime = rs.getDate("starttime");
				Date endtime = rs.getDate("endtime");
				String m_productionplan_id = rs.getString("m_productionplan_id");
				boolean processed = rs.getBoolean("processed");
				String ma_wrphase_id = rs.getString("ma_wrphase_id");
				BigDecimal neededquantity = rs.getBigDecimal("neededquantity");
				BigDecimal rejectedquantity = rs.getBigDecimal("rejectedquantity");
				String ma_workrequirement_id = rs.getString("ma_workrequirement_id");
				String wr_number = rs.getString("wr_number");
				long wrphase_seqno = rs.getLong("wrphase_seqno");
				String m_productionline_id = rs.getString("m_productionline_id");
				String m_product_id = rs.getString("m_product_id");
				String product_name = rs.getString("product_name");
				String product_code = rs.getString("product_code");
				
				DraftWorkEffortBean dweb = new DraftWorkEffortBean();
				dweb.setM_production_id(m_production_id);
				dweb.setDocumentno(documentno);
				dweb.setStarttime(starttime);
				dweb.setEndtime(endtime);
				dweb.setM_productionplan_id(m_productionplan_id);
				dweb.setProcessed(processed);
				dweb.setMa_wrphase_id(ma_wrphase_id);
				dweb.setNeededquantity(neededquantity.doubleValue());
				dweb.setRejectedquantity(rejectedquantity.doubleValue());
				dweb.setMa_workrequirement_id(ma_workrequirement_id);
				dweb.setWr_number(wr_number);
				dweb.setWrphase_seqno(wrphase_seqno);
				dweb.setM_productionline_id(m_productionline_id);
				dweb.setM_product_id(m_product_id);
				dweb.setProduct_name(product_name);
				dweb.setProduct_code(product_code);
				
				if (!output.contains(dweb))
					output.add(dweb);
				
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new OBException(e.getMessage());
		}
		
		return output;
	}
	
	private static DecomposeDraftWorkEffortBean deComposeWorkEffortBean(List<DraftWorkEffortBean> draftWorkEffort) {
		//documentNo --> work effort object
		HashMap<String, ProductionTransaction> workEffortMap = new HashMap<String, ProductionTransaction>();
		//documentNo+WRNo+WRPhaseSeqno --> production run object
		HashMap<String, ProductionPlan> productionRunMap = new HashMap<String, ProductionPlan>();
		//production run dan product --> production line object
		HashBasedTable<ProductionPlan, Product, ProductionLine> productionLineMap = HashBasedTable.create();
		for (DraftWorkEffortBean dweb : draftWorkEffort){

			//get distinct work effort header
			String documentno = dweb.getDocumentno();
			String m_production_id = dweb.getM_production_id();
			if (m_production_id==null || m_production_id.isEmpty())
				continue;
			ProductionTransaction workEffort = OBDal.getInstance().get(ProductionTransaction.class, m_production_id);
			if (workEffort==null)
				continue;
			if (!workEffortMap.containsKey(documentno))
				workEffortMap.put(documentno, workEffort);

			//get distinct production run
			String wr_number = dweb.getWr_number();
			if (wr_number==null)
				continue;
			Long wr_seqno = dweb.getWrphase_seqno();
			String productionRunIdentifier = documentno.concat(wr_number).concat(wr_seqno.toString());
			String m_productionplan_id = dweb.getM_productionplan_id();
			if (m_productionplan_id==null || m_productionplan_id.isEmpty())
				continue;
			ProductionPlan productionRun = OBDal.getInstance().get(ProductionPlan.class, m_productionplan_id);
			if (productionRun==null)
				continue;
			if (!productionRunMap.containsKey(productionRunIdentifier))
				productionRunMap.put(productionRunIdentifier, productionRun);

			String m_product_id = dweb.getM_product_id();
			if (m_product_id == null || m_product_id.isEmpty())
				continue;
			Product product = OBDal.getInstance().get(Product.class, m_product_id);
			if (product==null)
				continue;
			String m_productionline_id = dweb.getM_productionline_id();
			if (m_productionline_id==null || m_productionline_id.isEmpty())
				continue;
			ProductionLine productionline = OBDal.getInstance().get(ProductionLine.class, m_productionline_id);
			if (productionline==null)
				continue;
			if (!productionLineMap.contains(productionRun, product))
				productionLineMap.put(productionRun, product, productionline);
		}
		
		DecomposeDraftWorkEffortBean ddweb = new DecomposeDraftWorkEffortBean();
		ddweb.workEffortMap=workEffortMap;
		ddweb.productionRunMap=productionRunMap;
		ddweb.productionLineMap=productionLineMap;

		return ddweb;
	}

	private static class DecomposeDraftWorkEffortBean{
		HashMap<String, ProductionTransaction> workEffortMap;
		HashMap<String, ProductionPlan> productionRunMap;
		HashBasedTable<ProductionPlan, Product, ProductionLine> productionLineMap;
	}
}
