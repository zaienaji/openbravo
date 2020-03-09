/*
 ************************************************************************************
 * Copyright (C) 2019 Openbravo S.L.U.
 * Licensed under the Openbravo Commercial License version 1.0
 * You may obtain a copy of the License at http://www.openbravo.com/legal/obcl.html
 * or in the legal folder of this module distribution.
 ************************************************************************************
 */
package org.openbravo.common.actionhandler;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.ProductCategory;
import org.openbravo.model.pricing.priceadjustment.PriceAdjustment;

public class OfferPickProductCategoryActionHandler extends OfferPickAndExecBaseActionHandler {

  @Override
  protected void doPickAndExecute(String offerId, PriceAdjustment priceAdjustment, Client client,
      Organization org, JSONArray selectedLines) throws JSONException {
    for (int i = 0; i < selectedLines.length(); i++) {
      JSONObject productCat = selectedLines.getJSONObject(i);
      ProductCategory prdCat = (ProductCategory) OBDal.getInstance().getProxy(
          ProductCategory.ENTITY_NAME, productCat.getString("id"));
      org.openbravo.model.pricing.priceadjustment.ProductCategory item = OBProvider.getInstance()
          .get(org.openbravo.model.pricing.priceadjustment.ProductCategory.class);
      item.setActive(true);
      item.setClient(client);
      item.setOrganization(org);
      item.setPriceAdjustment(priceAdjustment);
      item.setProductCategory(prdCat);
      OBDal.getInstance().save(item);
      if ((i % 100) == 0) {
        OBDal.getInstance().flush();
        OBDal.getInstance().getSession().clear();
      }
    }
  }

  @Override
  protected String getJSONName() {
    return "Confprodcatprocess";
  }

}
