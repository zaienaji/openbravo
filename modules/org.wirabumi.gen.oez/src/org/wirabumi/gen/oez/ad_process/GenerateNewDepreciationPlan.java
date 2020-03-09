package org.wirabumi.gen.oez.ad_process;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_process.assets.AssetLinearDepreciationMethodProcess;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

public class GenerateNewDepreciationPlan extends DalBaseProcess {

	@Override
	protected void doExecute(ProcessBundle bundle) throws Exception {
		//get asset without depreciation
		OBError msg = null;
		ProcessLogger logger = bundle.getLogger();
		String strQuery="select asset from FinancialMgmtAsset as asset"
				+" where not exists (select 1 from FinancialMgmtAmortizationLine as line where line.asset.id=asset.id)";
		final Query query = OBDal.getInstance().getSession().createQuery(strQuery);
		final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
		int i=0;
		while (result.next()){
			Asset asset = (Asset) result.get()[0];
			AssetLinearDepreciationMethodProcess generateDepreciationButton = new AssetLinearDepreciationMethodProcess();
			msg = generateDepreciationButton.generateAmortizationPlan(asset);
			if (msg.getType().equalsIgnoreCase("Error")){
				logger.log("failed on asset "+asset.getName()+": "+msg.getMessage());
			} else {
				i++;
			}
		}
		
		msg.setType("Success");
		msg.setTitle(OBMessageUtils.messageBD("Generate Asset Depreciation Plan"));
		msg.setMessage(i+" asset(s) now have depreciation plan");
		
		bundle.setResult(msg);
	}

}
