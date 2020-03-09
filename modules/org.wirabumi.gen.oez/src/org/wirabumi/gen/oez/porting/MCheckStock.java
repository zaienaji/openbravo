package org.wirabumi.gen.oez.porting;

import java.util.ArrayList;
import java.util.List;

import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.ClientInformation;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;

public class MCheckStock {

  boolean vStock = false;
  int result = 1;
  String errorMessage;

  public MCheckStock(Product product, Client client, Organization organization) {

    String StockWhereClause = "AS ci WHERE ci.client=?";
    List<Object> StockParams = new ArrayList<Object>();
    StockParams.add(client);

    OBQuery<ClientInformation> checkStock = OBDal.getInstance().createQuery(
        ClientInformation.class, StockWhereClause, StockParams);

    if (checkStock.list().size() != 0) {
      vStock = checkStock.list().get(0).isAllowNegativeStock();
      if (vStock == false) {
        String checkStockWhereClause = "AS s WHERE s.product=? AND"
            + " s.client=? AND s.organization=?";
        List<Object> checkStockParams = new ArrayList<Object>();
        checkStockParams.add(product);
        checkStockParams.add(client);
        checkStockParams.add(organization);

        OBQuery<StorageDetail> checkStockQuery = OBDal.getInstance().createQuery(
            StorageDetail.class, checkStockWhereClause, checkStockParams);

        if (checkStockQuery.list().size() != 0) {
          for (StorageDetail stockList : checkStockQuery.list()) {
            double orderOnHand = 0.00;
            double onHand = 0.00;
            if (stockList.getOnHandOrderQuanity() != null) {
              orderOnHand = stockList.getOnHandOrderQuanity().doubleValue();
            }
            if (stockList.getQuantityOnHand() != null) {
              onHand = stockList.getQuantityOnHand().doubleValue();
            }
            if (orderOnHand < 0 || onHand < 0) {
              result = 0;
              errorMessage = "Insufficient stock:";
            }
          }
        }
      }
    }
  }

  public int getResult() {
    return result;
  }

  public String getMessage() {
    return errorMessage;
  }
}
