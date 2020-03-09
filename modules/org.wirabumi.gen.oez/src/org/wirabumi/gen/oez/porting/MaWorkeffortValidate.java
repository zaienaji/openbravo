package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.manufacturing.floorshop.Toolset;
import org.openbravo.model.manufacturing.transaction.ProductionRunToolset;
import org.openbravo.model.manufacturing.transaction.WorkRequirementOperation;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ProductionLine;
import org.openbravo.model.materialmgmt.transaction.ProductionPlan;
import org.openbravo.model.materialmgmt.transaction.ProductionTransaction;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class MaWorkeffortValidate extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    // TODO Auto-generated method stub

    VariablesSecureApp vars = bundle.getContext().toVars();
    // -------inisialisasi variable-----------//
    final OBError message = new OBError();
    boolean finishProcess = false;
    Organization vAdOrg;
    Date vProductionDate;
    boolean vIsReady;
    boolean vIsTrAllow;

    // ---------Mengambil data work effort-----------//
    // bundle.getParams().get
    String productionId = (String) bundle.getParams().get("M_Production_ID");
    String clientId = vars.getClient();
    String organizationId = vars.getOrg();
    String userId = vars.getUser();

    ProductionTransaction productionTransaction = OBDal.getInstance().get(
        ProductionTransaction.class, productionId);

    OBCriteria<ProductionPlan> productionPlan = OBDal.getInstance().createCriteria(
        ProductionPlan.class);
    productionPlan.add(Restrictions.eq(ProductionPlan.PROPERTY_PRODUCTION, productionTransaction));

    boolean process = productionTransaction.isProcessed();
    vProductionDate = productionTransaction.getMovementDate();
    vAdOrg = productionTransaction.getOrganization();
    String documentNo = productionTransaction.getDocumentNo();

    // -------cek apakah work effort sudah di proses?-------------//
    if (process == true) {
      message.setType("Error");
      message.setTitle("work effort is alredy validate");
      message.setMessage("work effort with document no : " + documentNo + "is alredy validate");
      bundle.setResult(message);
    }

    // ---------cek Product bukan product is generic -------------//
    String cekProductWhereClause = "AS pp WHERE pp.production=? AND pp.product.isGeneric=? ";
    List<Object> cekProductParams = new ArrayList<Object>();
    cekProductParams.add(productionTransaction);
    cekProductParams.add(true);

    OBQuery<ProductionPlan> cekProductQuery = OBDal.getInstance().createQuery(ProductionPlan.class,
        cekProductWhereClause, cekProductParams);

    int sumProductGeneric = cekProductQuery.list().size();

    if (sumProductGeneric != 0) {
      message.setType("Error");
      message.setTitle("Cannot Use Generic Product");
      message.setMessage("It is not possible to use generic products in the document");
      bundle.setResult(message);
    }

    if (finishProcess == false) {

      // -------------Mengambil transaction allowed & isready dari organization------------------//
      vIsTrAllow = vAdOrg.getOrganizationType().isTransactionsAllowed();
      vIsReady = vAdOrg.isReady();

      // ------------Cek Organisasi Is Ready------------------//
      if (vIsReady == false) {
        message.setType("Error");
        message.setTitle("Organization is not Ready");
        message.setMessage("The document belongs to a not ready organization");
        bundle.setResult(message);
      }

      // ------------Cek Organisasi Is Transaction Allowed------------------//
      if (vIsTrAllow == false) {
        message.setType("Error");
        message.setTitle("Cannot Use Generic Product");
        message
            .setMessage("The header document belongs to a organization where transactions are not allowed");
        bundle.setResult(message);
      }

      // ------------Cek Organization antara Worh Effort dengan Production Run------------------//
      AdOrgChkDocument checkDocument = new AdOrgChkDocument("MaterialMgmtProductionTransaction",
          "MaterialMgmtProductionPlan", productionId, "id", "production");

      int isInclude = checkDocument.getIsInclude();
      if (isInclude == -1) {
        message.setType("Error");
        message.setTitle("Lines And Header Differet Organization");
        message.setMessage("The document has lines of different business units or legal entities");
        bundle.setResult(message);
      }

      if (productionPlan.list().size() == 0)
        return;

      for (ProductionPlan productionPlanList : productionPlan.list()) {
        String productionPlanId = productionPlanList.getId();
        // ------------Cek Organization antara Production Plan dengan Product ------------------//
        AdOrgChkDocument checkDocument2 = new AdOrgChkDocument("MaterialMgmtProductionPlan",
            "ManufacturingProductionLine", productionPlanId, "id", "productionPlan");
        int isInclude2 = checkDocument2.getIsInclude();
        if (isInclude2 == -1) {
          message.setType("Error");
          message.setTitle("Lines And Header Differet Organization");
          message
              .setMessage("The document has lines of different business units or legal entities");
          bundle.setResult(message);
        }

        // ------------Cek Organization Apakah Legal Entity & Bussines Unit ------------------//
        AdGetDocLeBu adGetLebu = new AdGetDocLeBu("MaterialMgmtProductionTransaction",
            productionId, "id", "LE");
        String organizationBuLe = adGetLebu.getOrganizationHeader();

        Organization organizationLeBu = OBDal.getInstance().get(Organization.class,
            organizationBuLe);

        boolean isAccLe = organizationLeBu.getOrganizationType().isLegalEntityWithAccounting();

        // -----------------Cek Open Periode------------------ //
        if (isAccLe == true) {
          ChkOpenPeriod cekOpenPeriod = new ChkOpenPeriod(vAdOrg.getId(), vProductionDate, "MMP",
              "");
          int openPeriode = cekOpenPeriod.getAvailablePeriod();

          if (openPeriode != 1) {
            message.setType("Error");
            message.setTitle("Periode Not Available");
            message.setMessage("The Period does not exist or it is not opened");
            bundle.setResult(message);
          }
        }
      }

      if (productionPlan.list().size() == 0) {
        message.setType("Error");
        message.setTitle("No Production Run");
        message.setMessage("There are not Production Runs to validate");
        bundle.setResult(message);
        finishProcess = true;
      }
    }

    if (finishProcess == false) {
      // -------Cek Production Plan Apakah sudah di proses?------//
      OBCriteria<ProductionPlan> productionPlanProcessed = OBDal.getInstance().createCriteria(
          ProductionPlan.class);
      productionPlanProcessed.add(Restrictions.eq(ProductionPlan.PROPERTY_PRODUCTION,
          productionTransaction));
      productionPlanProcessed.add(Restrictions.eq(ProductionPlan.PROPERTY_PROCESSED, false));

      if (productionPlanProcessed.list().size() != 0) {

        message.setType("Error");
        message.setTitle("Production Run No Processed");
        message.setMessage("There are production runs  that aren't processed");
        bundle.setResult(message);
        finishProcess = true;
      }
    }

    if (finishProcess == false) {

      // ---------cek Production bukan Group Use-------------//
      String globalUseWhereClause = "AS pp WHERE pp.production=? AND pp.wRPhase.globalUse=? ";
      List<Object> globalUseParams = new ArrayList<Object>();
      globalUseParams.add(productionTransaction);
      globalUseParams.add(true);

      OBQuery<ProductionPlan> globalUseQuery = OBDal.getInstance().createQuery(
          ProductionPlan.class, globalUseWhereClause, globalUseParams);

      int globalUse = globalUseQuery.list().size();

      if (globalUse != 0) {
        MaGlobaluseDistribute globalUseDistribute = new MaGlobaluseDistribute(clientId,
            organizationId, userId, productionId);

        String errorMessage = globalUseDistribute.getMessage();

        if (!errorMessage.endsWith("")) {
          message.setType("Error");
          message.setTitle("Missing Product");
          message.setMessage(errorMessage);
          bundle.setResult(message);
        }
      }
    }

    if (finishProcess == false) {

      // ---------cek ProductionLine Apakah Memiliki Storage Bin-------------//

      String cekLocatorWhereClause = "AS pl WHERE pl.productionPlan.production=? AND pl.storageBin=null";
      List<Object> cekLocatorParams = new ArrayList<Object>();
      cekLocatorParams.add(productionTransaction);

      OBQuery<ProductionLine> cekLocatorQuery = OBDal.getInstance().createQuery(
          ProductionLine.class, cekLocatorWhereClause, cekLocatorParams);

      if (cekLocatorQuery.list().size() != 0) {
        message.setType("Error");
        message.setTitle("Need Locator");
        message.setMessage("Field Locator in Product tab must be filled");
        bundle.setResult(message);

        finishProcess = true;
      }
    }

    if (finishProcess == false) {
      // -----------------Cek productLine apakah pada satu line product P+ & p- sama?

      String cekSameProductWhereClause = "AS pl WHERE pl.productionType=? AND"
          + " pl.productionPlan.production=? AND"
          + " EXISTS (SELECT pl2 FROM ManufacturingProductionLine pl2 WHERE pl2=pl AND pl2.product=pl.product AND pl2.productionType=?)";
      List<Object> cekSameProductParams = new ArrayList<Object>();
      cekSameProductParams.add("+");
      cekSameProductParams.add(productionTransaction);
      cekSameProductParams.add("-");

      OBQuery<ProductionLine> cekSameProductQuery = OBDal.getInstance().createQuery(
          ProductionLine.class, cekSameProductWhereClause, cekSameProductParams);

      if (cekSameProductQuery.list().size() != 0) {
        message.setType("Error");
        message.setTitle("Same product in a production plan");
        message.setMessage("Product defined as P+ and P- within the same operation");
        bundle.setResult(message);
        finishProcess = true;
      }
    }

    if (finishProcess == false) {
      // --------------- Cek Product Attribute Value pada production Line ---------------------//
      String cekAttributeValueWhereClause = "AS pl WHERE pl.product.attributeSet IS NOT NULL"
          + " AND (pl.product.attributeSetValue IS NULL OR pl.product.attributeSetValue != 'F')"
          + " AND (SELECT at.requireAtLeastOneValue FROM AttributeSet at WHERE at = pl.product.attributeSet) = true"
          + " AND (pl.attributeSetValue.id='0' OR pl.attributeSetValue=null)"
          + " AND pl.productionPlan.production = ?";
      List<Object> cekAttributeValueParams = new ArrayList<Object>();
      cekAttributeValueParams.add(productionTransaction);

      OBQuery<ProductionLine> cekAttributeValueQuery = OBDal.getInstance().createQuery(
          ProductionLine.class, cekAttributeValueWhereClause, cekAttributeValueParams);

      if (cekAttributeValueQuery.list().size() != 0) {

        long lineProductionPlan = 0;
        long lineProductionLine = 0;

        for (ProductionLine productionLineList : cekAttributeValueQuery.list()) {
          if (productionLineList.getLineNo() > lineProductionLine) {
            lineProductionLine = productionLineList.getLineNo();
          }
          if (productionLineList.getLineNo() > lineProductionLine) {
            lineProductionLine = productionLineList.getLineNo();
          }
        }
        message.setType("Error");
        message.setTitle("Error");
        message.setMessage("In production run line : " + lineProductionPlan + " In line : "
            + lineProductionLine);
        bundle.setResult(message);
        finishProcess = true;
      }
    }

    if (finishProcess == false) {
      // --------------- Cek All Production Line Where Product is Stocked ---------------------//
      String checkProductStokedWhereClause = "AS pl"
          + " WHERE pl.productionPlan.production=? AND pl.product.stocked=?";
      List<Object> checkProductStokedParams = new ArrayList<Object>();
      checkProductStokedParams.add(productionTransaction);
      checkProductStokedParams.add(true);

      OBQuery<ProductionLine> checkProductStockedQuery = OBDal.getInstance().createQuery(
          ProductionLine.class, checkProductStokedWhereClause, checkProductStokedParams);

      if (checkProductStockedQuery.list().size() != 0) {

        for (ProductionLine StokedProductList : checkProductStockedQuery.list()) {
          double quantityOnHand = 0.00;

          String productType = StokedProductList.getProduct().getProductType();
          Boolean isStocked = StokedProductList.getProduct().isStocked();

          Product product = StokedProductList.getProduct();
          if (productType.endsWith("I") || isStocked == false) {
            quantityOnHand = 99999;
          } else if (isStocked == true) {
            String checkQuantityOnHandWhereClause = "AS s WHERE s.product=? AND"
                + " EXISTS (SELECT l FROM Locator l WHERE s.storageBin=l)";
            List<Object> checkQuantityOnHandParams = new ArrayList<Object>();
            checkQuantityOnHandParams.add(product);

            OBQuery<StorageDetail> checkQuantityOnHandQuery = OBDal.getInstance().createQuery(
                StorageDetail.class, checkQuantityOnHandWhereClause, checkQuantityOnHandParams);

            if (checkProductStockedQuery.list().size() != 0) {
              for (StorageDetail QuantityOnHandList : checkQuantityOnHandQuery.list()) {
                quantityOnHand = quantityOnHand
                    + (QuantityOnHandList.getQuantityOnHand().doubleValue());
              }
            }
          }

          // -----------------Insert Transaction -------------------------//
          MaterialTransaction createTransaction = OBProvider.getInstance().get(
              MaterialTransaction.class);

          AttributeSetInstance attributeValue = StokedProductList.getAttributeSetValue();
          if (attributeValue == null) {
            attributeValue = OBDal.getInstance().get(AttributeSetInstance.class, "0");
          }

          BigDecimal movementQuantity = StokedProductList.getMovementQuantity();
          if (movementQuantity == null) {
            movementQuantity = new BigDecimal(0);
          }
          BigDecimal orderQuantity = StokedProductList.getOrderQuantity();
          if (orderQuantity == null) {
            orderQuantity = new BigDecimal(0);
          }
          if (StokedProductList.getProductionType().endsWith("-")) {
            movementQuantity = movementQuantity.negate();
            orderQuantity = orderQuantity.negate();
          }

          createTransaction.setProductionLine(StokedProductList);
          createTransaction.setClient(StokedProductList.getClient());
          createTransaction.setOrganization(StokedProductList.getOrganization());
          createTransaction.setActive(true);
          createTransaction.setMovementType("P+");
          createTransaction.setStorageBin(StokedProductList.getStorageBin());
          createTransaction.setProduct(product);
          createTransaction.setAttributeSetValue(attributeValue);
          createTransaction.setMovementDate(StokedProductList.getProductionPlan().getProduction()
              .getMovementDate());
          createTransaction.setMovementQuantity(movementQuantity);
          createTransaction.setUOM(StokedProductList.getUOM());

          OBDal.getInstance().save(createTransaction);

          if (StokedProductList.getProduct().isStocked() == true) {
            // ------------------Check Stock Product In Locator ----------------------------//
            MCheckStock checkStock = new MCheckStock(product, StokedProductList.getClient(),
                StokedProductList.getOrganization());

            if (checkStock.getResult() == 0) {
              message.setType("Error");
              message.setTitle("Error");
              message.setMessage(checkStock.getMessage() + " in line : "
                  + StokedProductList.getLineNo());
              bundle.setResult(message);
            }
          }
        }
        OBDal.getInstance().commitAndClose();
      }

      if (finishProcess == false) {
        OBCriteria<Preference> calculateCost = OBDal.getInstance().createCriteria(Preference.class);
        calculateCost.add(Restrictions.eq(Preference.PROPERTY_ATTRIBUTE, "Cost_Eng_Ins_Migrated"));

        int calculateCostCount = 0;// calculateCost.list().size();

        if (calculateCostCount == 0) {
          User user = OBDal.getInstance().get(User.class, userId);
          MaProductionCost productionCost = new MaProductionCost(productionTransaction, user);
          String errorMessage = productionCost.getMessage();

          if (!errorMessage.endsWith("")) {
            message.setType("Error");
            message.setTitle("Error");
            message.setMessage(errorMessage);
            bundle.setResult(message);
          }
        }
      }

      if (finishProcess == false) {
        BigDecimal doneQty = new BigDecimal(0);
        for (ProductionPlan productionPlanList : productionPlan.list()) {
          WorkRequirementOperation wrPhase = OBDal.getInstance().get(
              WorkRequirementOperation.class, productionPlanList.getWRPhase().getId());

          doneQty = doneQty.add(productionPlanList.getProductionQuantity());
          wrPhase.setCompletedQuantity(doneQty);
          wrPhase.setRunTime(new BigDecimal(productionPlanList.getRunTime()));
          OBDal.getInstance().save(wrPhase);

          if (productionPlanList.isClosephase() == true) {
            Process processPi = OBDal.getInstance().get(Process.class, "800118");

            ProcessInstance processInstance = OBProvider.getInstance().get(ProcessInstance.class);

            processInstance.setProcess(processPi);
            processInstance.setRecordID(productionPlanList.getWRPhase().getId());
            processInstance.setActive(true);
            OBDal.getInstance().save(processInstance);

            new MaWrphaseClose(wrPhase, false);
          }
        }
        productionTransaction.setProcessed(true);
        OBDal.getInstance().save(productionTransaction);
        OBDal.getInstance().commitAndClose();
      }

      if (finishProcess == false) {

        String checkUTSWhereClause = "AS WHERE tsu.productionPlan.production=?";
        List<Object> checkUTSParams = new ArrayList<Object>();
        checkUTSParams.add(productionTransaction);

        OBQuery<ProductionRunToolset> UTSQuery = OBDal.getInstance().createQuery(
            ProductionRunToolset.class, checkUTSWhereClause, checkUTSParams);

        for (ProductionRunToolset usedToolset : UTSQuery.list()) {

          Toolset toolSet = usedToolset.getToolset();

          BigDecimal numberUser = toolSet.getUtilization();
          BigDecimal used = usedToolset.getToolsetUses();
          toolSet.setUtilization(numberUser.add(used));

          OBDal.getInstance().save(toolSet);
          OBDal.getInstance().commitAndClose();
        }
      }
    }
  }
}
