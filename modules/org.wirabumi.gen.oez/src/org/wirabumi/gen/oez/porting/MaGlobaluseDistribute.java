package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.manufacturing.transaction.GlobalUse;
import org.openbravo.model.manufacturing.transaction.WorkRequirementOperation;
import org.openbravo.model.manufacturing.transaction.WorkRequirementProduct;
import org.openbravo.model.materialmgmt.transaction.ProductionLine;
import org.openbravo.model.materialmgmt.transaction.ProductionPlan;
import org.openbravo.model.materialmgmt.transaction.ProductionTransaction;
import org.openbravo.service.db.DalConnectionProvider;

public class MaGlobaluseDistribute {

  String Message = "";

  public MaGlobaluseDistribute(String clientId, String OrganizationId, String userId,
      String productionId) {

    BigDecimal jumlah = new BigDecimal(0);
    String productName = "";

    ConnectionProvider conn = new DalConnectionProvider();
    ProductionTransaction productionTransaction = OBDal.getInstance().get(
        ProductionTransaction.class, productionId);

    String cekGlobalUseWhereClause = "AS pl WHERE pl.productionPlan.wRPhase.globalUse=? AND pl.productionType=? AND pl.productionPlan.production=? ";
    List<Object> cekGlobalUseParams = new ArrayList<Object>();
    cekGlobalUseParams.add(true);
    cekGlobalUseParams.add("-");
    cekGlobalUseParams.add(productionTransaction);

    OBQuery<ProductionLine> cekGlobalUseQuery = OBDal.getInstance().createQuery(
        ProductionLine.class, cekGlobalUseWhereClause, cekGlobalUseParams);

    if (cekGlobalUseQuery.list().size() == 0)
      return;

    for (ProductionLine productionLineList : cekGlobalUseQuery.list()) {
      OBDal.getInstance().remove(productionLineList);
    }

    String sql = "SELECT COUNT(*) AS jumlah , MAX(p.NAME) AS name"
        + " FROM MA_WRPHASEPRODUCT wpp, MA_WRPHASE wp, M_PRODUCTIONPLAN pp, M_PRODUCT p"
        + " WHERE pp.MA_WRPHASE_ID=wp.MA_WRPHASE_ID" + " AND wp.MA_WRPHASE_ID=wpp.MA_WRPHASE_ID"
        + " AND wpp.M_PRODUCT_ID = p.M_PRODUCT_ID" + " AND pp.M_PRODUCTION_ID=p_Production_ID"
        + " AND wp.GROUPUSE='Y'" + " AND wpp.PRODUCTIONTYPE='-'"
        + " AND wpp.M_PRODUCT_ID NOT IN (SELECT M_Product_ID" + " FROM MA_GLOBALUSE"
        + " WHERE M_Production_ID='" + productionId + "')";

    try {
      Statement st = conn.getStatement();
      ResultSet rs = st.executeQuery(sql);
      while (rs.next()) {
        jumlah = new BigDecimal(rs.getInt("jumlah"));
        productName = rs.getString("name");
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new OBException(e.getMessage());
    }

    if (jumlah.intValue() != 0) {
      Message = "In the global use tab is missing the product " + productName;
    }

    OBCriteria<GlobalUse> globalUse = OBDal.getInstance().createCriteria(GlobalUse.class);
    globalUse.add(Restrictions.eq(GlobalUse.PROPERTY_PRODUCTION, productionTransaction));

    for (GlobalUse globalUseList : globalUse.list()) {

      Product product = globalUseList.getProduct();

      String cekProductWhereClause = "AS pp JOIN wp.manufacturingWorkRequirementProductList wpp"
          + " JOIN wp.materialMgmtProductionPlanList pp"
          + " WHERE wp.globalUse=? AND pp.production=? AND wpp.product=?";

      List<Object> cekProductParams = new ArrayList<Object>();
      cekProductParams.add(true);
      cekProductParams.add(productionTransaction);
      cekProductParams.add(product);

      OBQuery<WorkRequirementOperation> cekProductQuery = OBDal.getInstance().createQuery(
          WorkRequirementOperation.class, cekProductWhereClause, cekProductParams);

      if (cekProductQuery.list().size() == 0)
        return;

      for (WorkRequirementOperation cekProductList : cekProductQuery.list()) {
        BigDecimal movementQty = cekProductList.getManufacturingWorkRequirementProductList().get(0)
            .getOrderQuantity();
        BigDecimal productionQty = cekProductList.getMaterialMgmtProductionPlanList().get(0)
            .getProductionQuantity();
        jumlah = jumlah.add(movementQty.multiply(productionQty));
      }

      OBCriteria<WorkRequirementProduct> workRequirementProduct = OBDal.getInstance()
          .createCriteria(WorkRequirementProduct.class);
      workRequirementProduct.add(Restrictions.eq(WorkRequirementProduct.PROPERTY_PRODUCT, product));

      if (workRequirementProduct.list().size() != 0)
        return;

      for (WorkRequirementProduct workRequirementProductList : workRequirementProduct.list()) {

        ProductionPlan productionPlan = workRequirementProductList.getWRPhase()
            .getMaterialMgmtProductionPlanList().get(0);

        String calculateFactorWhereClause = "AS wp JOIN wp.manufacturingWorkRequirementProductList wpp"
            + " JOIN wp.materialMgmtProductionPlanList pp"
            + " WHERE wp.globalUse=? AND pp=? AND wpp.product=?";

        List<Object> calculateFactorParams = new ArrayList<Object>();
        calculateFactorParams.add(true);
        calculateFactorParams.add(productionPlan);
        calculateFactorParams.add(product);

        OBQuery<WorkRequirementOperation> calculateFactorQuery = OBDal.getInstance().createQuery(
            WorkRequirementOperation.class, calculateFactorWhereClause, calculateFactorParams);

        if (calculateFactorQuery.list().size() == 0)
          return;

        BigDecimal factor = new BigDecimal(0.00);
        int countLine = 0;
        long lineNo = 10;
        for (WorkRequirementOperation calculateFactorList : calculateFactorQuery.list()) {
          BigDecimal movementQty = calculateFactorList.getManufacturingWorkRequirementProductList()
              .get(0).getOrderQuantity();
          BigDecimal productionQty = calculateFactorList.getMaterialMgmtProductionPlanList().get(0)
              .getProductionQuantity();
          factor = (movementQty.multiply(productionQty)).divide(jumlah);
          OBCriteria<ProductionLine> cekProductionLine = OBDal.getInstance().createCriteria(
              ProductionLine.class);
          cekProductionLine.add(Restrictions.eq(ProductionLine.PROPERTY_PRODUCTIONPLAN,
              productionPlan));
          cekProductionLine.add(Restrictions.eq(ProductionLine.PROPERTY_PRODUCT, product));

          for (ProductionLine productLineList : cekProductionLine.list()) {
            countLine++;
            lineNo = productLineList.getLineNo().intValue();
          }

          if (factor.intValue() != 0 && countLine == 0) {
            ProductionLine productionLine = OBProvider.getInstance().get(ProductionLine.class);
            BigDecimal guMovementQty = globalUseList.getMovementQuantity();
            BigDecimal sumMovementQty = guMovementQty.multiply(factor);

            BigDecimal guOrderQty = globalUseList.getOrderQuantity();
            BigDecimal sumOrderQty = guOrderQty.multiply(factor);

            productionLine.setLineNo(lineNo);
            productionLine.setProductionPlan(productionPlan);
            productionLine.setActive(true);
            productionLine.setProductionType("-");
            productionLine.setAttributeSetValue(globalUseList.getAttributeSetValue());
            productionLine.setMovementQuantity(sumMovementQty);
            productionLine.setUOM(globalUseList.getUOM());
            productionLine.setStorageBin(globalUseList.getStorageBin());
            productionLine.setOrderUOM(globalUseList.getOrderUOM());
            productionLine.setOrderQuantity(sumOrderQty);
            OBDal.getInstance().save(productionLine);

            try {
              OBDal.getInstance().commitAndClose();
            } catch (Exception e) {
              e.printStackTrace();
              throw new OBException(e.getMessage());
            }
          }
        }
      }
    }
  }

  public String getMessage() {
    return Message;
  }
}
