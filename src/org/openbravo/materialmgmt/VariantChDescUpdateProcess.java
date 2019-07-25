/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2013-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.materialmgmt;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.exception.GenericJDBCException;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductCharacteristic;
import org.openbravo.model.common.plm.ProductCharacteristicValue;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class VariantChDescUpdateProcess extends DalBaseProcess {
  private static final Logger log4j = LogManager.getLogger();
  public static final String AD_PROCESS_ID = "58591E3E0F7648E4A09058E037CE49FC";

  @Override
  public void doExecute(ProcessBundle bundle) throws Exception {
    OBError msg = new OBError();
    msg.setType("Success");
    msg.setTitle(OBMessageUtils.messageBD("Success"));

    try {
      // retrieve standard params
      String strProductId = (String) bundle.getParams().get("mProductId");
      String strChValueId = (String) bundle.getParams().get("mChValueId");

      update(strProductId, strChValueId);

      bundle.setResult(msg);

      // Postgres wraps the exception into a GenericJDBCException
    } catch (GenericJDBCException ge) {
      log4j.error("Exception processing variant generation", ge);
      msg.setType("Error");
      msg.setTitle(OBMessageUtils.messageBD(bundle.getConnection(), "Error",
          bundle.getContext().getLanguage()));
      msg.setMessage(ge.getSQLException().getMessage().split("\n")[0]);
      bundle.setResult(msg);
      OBDal.getInstance().rollbackAndClose();
    } catch (final Exception e) {
      log4j.error("Exception processing variant generation", e);
      msg.setType("Error");
      msg.setTitle(OBMessageUtils.messageBD(bundle.getConnection(), "Error",
          bundle.getContext().getLanguage()));
      msg.setMessage(FIN_Utility.getExceptionMessage(e));
      bundle.setResult(msg);
      OBDal.getInstance().rollbackAndClose();
    }

  }

  /**
   * Method to update the Characteristics Description.
   * 
   * @param strProductId
   *          Optional parameter, when given updates only the description of this product.
   * @param strChValueId
   *          Optional parameter, when given updates only products with this characteristic value
   *          assigned.
   */
  public void update(String strProductId, String strChValueId) {
    OBContext.setAdminMode(true);
    try {
      if (StringUtils.isNotBlank(strProductId)) {
        Product product = OBDal.getInstance().get(Product.class, strProductId);
        // In some cases product might have been deleted.
        if (product != null) {
          updateProduct(product);
        }
        return;
      }
      StringBuffer where = new StringBuffer();
      where.append(" as p");
      where.append(" where p." + Product.PROPERTY_PRODUCTCHARACTERISTICLIST + " is not empty");
      if (StringUtils.isNotBlank(strChValueId)) {
        where.append(" and exists (select 1 from p."
            + Product.PROPERTY_PRODUCTCHARACTERISTICVALUELIST + " as chv");
        where.append("    where chv." + ProductCharacteristicValue.PROPERTY_CHARACTERISTICVALUE
            + ".id = :chvid)");
      }
      OBQuery<Product> productQuery = OBDal.getInstance()
          .createQuery(Product.class, where.toString());
      if (StringUtils.isNotBlank(strChValueId)) {
        productQuery.setNamedParameter("chvid", strChValueId);
      }
      productQuery.setFilterOnReadableOrganization(false);
      productQuery.setFilterOnActive(false);

      ScrollableResults products = productQuery.scroll(ScrollMode.FORWARD_ONLY);
      int i = 0;
      try {
        while (products.next()) {
          Product product = (Product) products.get(0);
          updateProduct(product);

          if ((i % 100) == 0) {
            OBDal.getInstance().flush();
            OBDal.getInstance().getSession().clear();
          }
          i++;
        }
      } finally {
        products.close();
      }

    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void updateProduct(Product product) {
    String strChDesc = "";
    StringBuffer where = new StringBuffer();
    where.append(" as pch");
    where.append(" where pch." + ProductCharacteristic.PROPERTY_PRODUCT + " = :product");
    where.append(" order by pch." + ProductCharacteristic.PROPERTY_SEQUENCENUMBER);
    OBQuery<ProductCharacteristic> pchQuery = OBDal.getInstance()
        .createQuery(ProductCharacteristic.class, where.toString());
    pchQuery.setFilterOnActive(false);
    pchQuery.setFilterOnReadableOrganization(false);
    pchQuery.setNamedParameter("product", product);
    for (ProductCharacteristic pch : pchQuery.list()) {
      if (StringUtils.isNotBlank(strChDesc)) {
        strChDesc += ", ";
      }
      strChDesc += pch.getCharacteristic().getName() + ":";
      where = new StringBuffer();
      where.append(" as pchv");
      where.append(
          " where pchv." + ProductCharacteristicValue.PROPERTY_CHARACTERISTIC + ".id = :ch");
      where.append("   and pchv." + ProductCharacteristicValue.PROPERTY_PRODUCT + ".id = :product");
      OBQuery<ProductCharacteristicValue> pchvQuery = OBDal.getInstance()
          .createQuery(ProductCharacteristicValue.class, where.toString());
      pchvQuery.setFilterOnActive(false);
      pchvQuery.setFilterOnReadableOrganization(false);
      pchvQuery.setNamedParameter("ch", pch.getCharacteristic().getId());
      pchvQuery.setNamedParameter("product", product.getId());
      for (ProductCharacteristicValue pchv : pchvQuery.list()) {
        strChDesc += " " + pchv.getCharacteristicValue().getName();
      }
    }
    product.setCharacteristicDescription(strChDesc);
  }
}
