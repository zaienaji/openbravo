package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.model.financialmgmt.assetmgmt.AssetGroup;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.gen.oez.ImportAsset;

public class ProcessImportAsset extends DalBaseProcess {
	

  @Override
  protected void doExecute(ProcessBundle bundle) {
	  final OBError msg = new OBError();
	  String errorMsg="";
	  
	  OBCriteria<ImportAsset> importAssetCriteria = OBDal.getInstance().createCriteria(ImportAsset.class);
	  importAssetCriteria.add(Restrictions.eq(ImportAsset.PROPERTY_IMPORTPROCESSCOMPLETE, false));
	  List<ImportAsset> importAssetList = importAssetCriteria.list();
	  int importAssetSize=importAssetList.size();
	  int i=0;
	  for (ImportAsset importAsset:importAssetList){
		  i++;
		  
		  //validasi asset group
		  String strAssetGroup = importAsset.getAssetgroupvalue();
		  OBCriteria<AssetGroup> assetGroupList = OBDal.getInstance().createCriteria(AssetGroup.class);
		  assetGroupList.add(Restrictions.eq(AssetGroup.PROPERTY_NAME, strAssetGroup)).list().get(0);
		  AssetGroup assetGroup = assetGroupList.list().get(0);
		  if (assetGroup==null){
			  errorMsg+="invalid asset category key. ";
		  }
		  
		  //validasi currency
		  String strCurrency = importAsset.getCurrencyIsoCode();
		  OBCriteria<Currency> currencyList = OBDal.getInstance().createCriteria(Currency.class);
		  currencyList.add(Restrictions.eq(Currency.PROPERTY_ISOCODE, strCurrency));
		  Currency currency = currencyList.list().get(0);
		  if (currency==null){
			  errorMsg+="invalid currency ISO code. ";
		  }
		  
		  //validasi name
		  String name = importAsset.getName();
		  if (name==null){
			  errorMsg+="asset name is required. ";
		  }
		  
		  //validasi search key
		  String searchKey = importAsset.getSearchKey();
		  if (searchKey==null){
			  errorMsg+="asset search key is required. ";
		  }
		  
		  if (!errorMsg.isEmpty()){
			  continue;
		  }
		  
		  //calculate accumulative depreciation
		  BigDecimal accumDepreciation= new BigDecimal(0);
		  BigDecimal assetValue=importAsset.getAssetValue();
		  BigDecimal depreciationPlan=importAsset.getDepreciatedPlan();
		  if (assetValue!=null && depreciationPlan!=null){
			  accumDepreciation=assetValue.subtract(depreciationPlan);
		  }
		  
		  Asset asset = OBProvider.getInstance().get(Asset.class);
		  asset.setName(name);
		  asset.setSearchKey(searchKey);
		  asset.setAssetCategory(assetGroup);
		  asset.setCurrency(currency);
		  asset.setPreviouslyDepreciatedAmt(accumDepreciation);
		  asset.setAssetValue(assetValue);
		  asset.setDepreciationAmt(assetValue);
		  asset.setDescription(importAsset.getDescription());
		  asset.setDepreciate(importAsset.isDepreciate());
		  asset.setDepreciationType(importAsset.getDepreciationType());
		  asset.setCalculateType(importAsset.getCalculateType());
		  asset.setAmortize(importAsset.getAmortize());
		  asset.setUsableLifeMonths(importAsset.getUsableLifeMonths());
		  asset.setUsableLifeYears(importAsset.getUsableLifeYears());
		  asset.setAnnualDepreciation(importAsset.getAnnualDepreciation());
		  asset.setEveryMonthIs30Days(importAsset.isEveryMonthIs30Days());
		  asset.setPurchaseDate(importAsset.getPurchaseDate());
		  asset.setDepreciationStartDate(importAsset.getDepreciationStartDate());
		  asset.setOwned(importAsset.isOwned());
		  OBDal.getInstance().save(asset);
		  importAsset.setImportProcessComplete(true);
		  OBDal.getInstance().save(importAsset);
	  }
	  OBDal.getInstance().commitAndClose();
	  
	  if (i!=importAssetSize){
		  msg.setType("Warning");
	  } else {
		  msg.setType("Success");
	  }
	  
	  msg.setTitle("Import Asset");
	  msg.setMessage(i+" of "+importAssetSize+" asset(s) imported");
	  bundle.setResult(msg);
	  
  }

  
}
