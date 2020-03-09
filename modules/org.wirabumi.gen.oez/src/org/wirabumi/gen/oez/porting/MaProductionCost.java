package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.manufacturing.cost.CostcenterVersion;
import org.openbravo.model.manufacturing.transaction.ProductionRunEmployee;
import org.openbravo.model.manufacturing.transaction.ProductionRunIndirectCosts;
import org.openbravo.model.manufacturing.transaction.ProductionRunInvoiceLine;
import org.openbravo.model.manufacturing.transaction.ProductionRunMachine;
import org.openbravo.model.manufacturing.transaction.WorkRequirementProduct;
import org.openbravo.model.materialmgmt.cost.Costing;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ProductionLine;
import org.openbravo.model.materialmgmt.transaction.ProductionPlan;
import org.openbravo.model.materialmgmt.transaction.ProductionTransaction;

public class MaProductionCost {

  Client client;
  Organization organization;
  Date productionDate;
  Date costingDate;
  String documentNo;
  long countPl = 0;
  boolean costMigrated;
  String errorMessage = "";
  BigDecimal Cost = new BigDecimal(0);
  String Result = "";
  BigDecimal qty = new BigDecimal(0);
  Date endingDate = new Date();

  public MaProductionCost(ProductionTransaction production, User user) {

    try {
      SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");
      String dateInString = "02-Jun-9999";
      endingDate = (Date) formatter.parse(dateInString);
    } catch (ParseException e) {
      e.printStackTrace();
      throw new OBException(e.getMessage());
    }

    // Update ProductionLine Calculate
    OBCriteria<ProductionPlan> productionPlan1 = OBDal.getInstance().createCriteria(
        ProductionPlan.class);
    productionPlan1.add(Restrictions.eq(ProductionPlan.PROPERTY_PRODUCTION, production));

    for (ProductionPlan productionPlanList : productionPlan1.list()) {
      OBCriteria<ProductionLine> productionLine = OBDal.getInstance().createCriteria(
          ProductionLine.class);
      productionLine.add(Restrictions
          .eq(ProductionLine.PROPERTY_PRODUCTIONPLAN, productionPlanList));
      productionLine.add(Restrictions.eq(ProductionLine.PROPERTY_MOVEMENTQUANTITY,
          new BigDecimal(0)));
      productionLine.add(Restrictions.eq(ProductionLine.PROPERTY_PRODUCTIONTYPE, "+"));

      for (ProductionLine productionLineList : productionLine.list()) {
        productionLineList.setCalculated(true);
        OBDal.getInstance().save(productionLineList);
      }

      String productionLine2WhereClause = "AS pl WHERE pl.productionType='-' AND "
          + "pl.productionPlan=? AND "
          + "NOT EXISTS (SELECT pl2 FROM ManufacturingProductionLine pl2 "
          + "WHERE pl2.productionPlan.production=? AND "
          + "pl2.productionType='+' AND pl2.calculated=false AND pl.product = pl2.product)";
      List<Object> productionLine2Params = new ArrayList<Object>();
      productionLine2Params.add(productionPlanList);
      productionLine2Params.add(production);

      OBQuery<ProductionLine> productionLine2 = OBDal.getInstance().createQuery(
          ProductionLine.class, productionLine2WhereClause, productionLine2Params);
      for (ProductionLine productionLineList : productionLine2.list()) {
        productionLineList.setCalculated(true);
        OBDal.getInstance().save(productionLineList);
      }
    }

    OBCriteria<ProductionPlan> productionPlan2 = OBDal.getInstance().createCriteria(
        ProductionPlan.class);
    productionPlan2.add(Restrictions.eq(ProductionPlan.PROPERTY_PRODUCTION, production));
    productionPlan2.add(Restrictions.eq(ProductionPlan.PROPERTY_COSTCENTERVERSION, null));

    for (ProductionPlan productionPlanList : productionPlan2.list()) {
      OBCriteria<ProductionLine> productionLine = OBDal.getInstance().createCriteria(
          ProductionLine.class);
      productionLine.add(Restrictions
          .eq(ProductionLine.PROPERTY_PRODUCTIONPLAN, productionPlanList));
      for (ProductionLine productionLineList : productionLine.list()) {
        productionLineList.setCalculated(true);
        OBDal.getInstance().save(productionLineList);
      }

    }

    OBCriteria<Preference> calculateCost = OBDal.getInstance().createCriteria(Preference.class);
    calculateCost.add(Restrictions.eq(Preference.PROPERTY_ATTRIBUTE, "Cost_Eng_Ins_Migrated"));

    int calculateCostCount = 1; // calculateCost.list().size();
    // -------------------Costing engine migrated---------------------//
    if (calculateCostCount > 0) {
      final String hql = "SELECT MAX(pl.productionPlan.production.client), "
          + "MAX(pl.productionPlan.production.organization), MAX(pl.productionPlan.production.movementDate), "
          + "MAX(pl.productionPlan.production.documentNo), COUNT(pl) FROM ManufacturingProductionLine pl "
          + "WHERE pl.calculated=false AND pl.productionPlan.production='" + production + "'";
      Query query = OBDal.getInstance().getSession().createQuery(hql);

      ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        client = (Client) rs.get()[0];
        organization = (Organization) rs.get()[1];
        productionDate = (Date) rs.get(2);
        documentNo = (String) rs.get(3);
        countPl = (Long) rs.get(4);

        final String hql2 = "SELECT MAX(mmt.transactionProcessDate) FROM MaterialMgmtMaterialTransaction mmt "
            + "WHERE mmt.productionLine.productionPlan.production='"
            + production
            + "' AND mmt.productionLine.calculated=false ";
        Query query2 = OBDal.getInstance().getSession().createQuery(hql2);
        ScrollableResults rs2 = query2.scroll(ScrollMode.FORWARD_ONLY);
        while (rs2.next()) {
          costingDate = (Date) rs2.get(0);
        }
      }
    } else {
      final String hql = "SELECT MAX(pl.productionPlan.production.client), "
          + "MAX(pl.productionPlan.production.organization), MAX(pl.productionPlan.production.movementDate), "
          + "MAX(pl.productionPlan.production.documentNo), COUNT(pl) FROM ManufacturingProductionLine pl "
          + "WHERE pl.calculated=false AND pl.productionPlan.production='" + production + "'";
      Query query = OBDal.getInstance().getSession().createQuery(hql);

      ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        client = (Client) rs.get()[0];
        organization = (Organization) rs.get()[1];
        productionDate = (Date) rs.get(2);
        documentNo = (String) rs.get(3);
        countPl = (Long) rs.get(4);
        costingDate = productionDate;
      }
    }

    OBCriteria<CostingRule> costingRule = OBDal.getInstance().createCriteria(CostingRule.class);
    costingRule.add(Restrictions.eq(CostingRule.PROPERTY_VALIDATED, true));

    if (costingRule.list().size() == 0) {
      costMigrated = false;
    } else {
      costMigrated = true;
    }

    // -------------------------- Calculate Cost --------------------------//
    for (int i = 0; i < calculateCostCount; i++) {

      BigDecimal productionCost = new BigDecimal(0);
      BigDecimal productionTime;
      BigDecimal producedKg;
      BigDecimal producedUd;
      BigDecimal compCostSum = new BigDecimal(1);

      String calculateCostWhereClause = "AS pl WHERE pl.productionPlan.production=? AND "
          + "pl.productionPlan.costCenterVersion IS NOT NULL AND "
          + "NOT EXISTS (SELECT pl2 FROM ManufacturingProductionLine pl2 WHERE pl2.productionType='+' AND pl2.calculated=false AND  "
          + "pl2.productionPlan.production=pl.productionPlan.production AND "
          + "EXISTS (SELECT pl3 FROM ManufacturingProductionLine pl3 WHERE pl2.product=pl3.product AND "
          + "pl2.productionPlan =pl3.productionPlan AND pl2.productionType='-' ))";
      List<Object> calculateCostParams = new ArrayList<Object>();
      calculateCostParams.add(production);

      OBQuery<ProductionLine> calculateCostQuery = OBDal.getInstance().createQuery(
          ProductionLine.class, calculateCostWhereClause, calculateCostParams);
      for (ProductionLine calculateCostList : calculateCostQuery.list()) {
        productionTime = calculateCostList.getProductionPlan().getCostCenterUse();

        OBCriteria<WorkRequirementProduct> workRequirementProduct = OBDal.getInstance()
            .createCriteria(WorkRequirementProduct.class);
        workRequirementProduct.add(Restrictions.eq(WorkRequirementProduct.PROPERTY_WRPHASE,
            calculateCostList.getProductionPlan().getWRPhase()));

        if (calculateCostList.getProductionType() == "+") {
          producedKg = (calculateCostList.getProduct().getWeight()).multiply(calculateCostList
              .getMovementQuantity());
          producedUd = calculateCostList.getMovementQuantity();

          if (workRequirementProduct.list().size() != 0) {
            compCostSum = workRequirementProduct.list().get(0).getComponentCost();
          }
          // compCostSum =
        } else {
          producedKg = new BigDecimal(0);
          producedUd = new BigDecimal(0);
          compCostSum = new BigDecimal(0);
        }
        // --Check that the products in production run exist in the correspondent work
        // requirement---//
        if (calculateCostList.getProductionPlan().getWRPhase() != null) {
          String checkCorrespondentWhereClause = "AS pl WHERE pl.productionPlan = ? AND "
              + "EXISTS (SELECT wp FROM ManufacturingWorkRequirementProduct wp WHERE wp.product = pl.product AND wp.wRPhase = ?)";

          List<Object> checkCorrespondentParams = new ArrayList<Object>();
          checkCorrespondentParams.add(calculateCostList);
          checkCorrespondentParams.add(calculateCostList.getProductionPlan().getWRPhase());

          OBQuery<ProductionLine> checkCorrespondent = OBDal.getInstance().createQuery(
              ProductionLine.class, checkCorrespondentWhereClause, checkCorrespondentParams);
          if (checkCorrespondent.list().size() != 0) {
            errorMessage = "In production run "
                + documentNo
                + " - "
                + calculateCostList.getLineNo()
                + " there are products that don't exist in the correspondent work requirement phase";
            throw new OBException(errorMessage);
          }
        }

        // -------Sums the cost of the used raw material and WIP------------//
        BigDecimal productionCostTmp = new BigDecimal(0);

        String wipCostWhereClause = "AS c WHERE c.productionLine = ? AND "
            + "c.production=true AND c.productionLine.productionType='-' AND TRUNC(c.startingDate) <= ? AND "
            + "TRUNC(c.endingDate) > ? AND c.product = c.productionLine.product";

        List<Object> wipCostParams = new ArrayList<Object>();
        wipCostParams.add(calculateCostList);
        wipCostParams.add(costingDate);
        wipCostParams.add(costingDate);

        OBQuery<Costing> wipCost = OBDal.getInstance().createQuery(Costing.class,
            wipCostWhereClause, wipCostParams);

        String CostType;
        if (wipCost.list().size() != 0) {
          CostType = wipCost.list().get(0).getCostType();

          if ((CostType == "AVA" && costMigrated == true)
              || (CostType == "AV" && costMigrated == false)) {

          } else {
            String wipCostWhereClause2 = "AS c WHERE c.productionLine = ? AND "
                + "c.production=false AND c.productionLine.productionType='-' AND TRUNC(c.startingDate) <= ? AND "
                + "TRUNC(c.endingDate) > ? AND c.product = c.productionLine.product";

            List<Object> wipCostParams2 = new ArrayList<Object>();
            wipCostParams2.add(calculateCostList);
            wipCostParams2.add(costingDate);
            wipCostParams2.add(costingDate);

            OBQuery<Costing> wipCost2 = OBDal.getInstance().createQuery(Costing.class,
                wipCostWhereClause2, wipCostParams2);

            String CostType2;
            if (wipCost2.list().size() != 0) {
              CostType2 = wipCost2.list().get(0).getCostType();

              if ((CostType2 == "AVA" && costMigrated == true)
                  || (CostType2 == "AV" && costMigrated == false)) {
                productionCostTmp = productionCostTmp.add(wipCost2.list().get(0).getCost());

                ProductionLine productionLine = OBDal.getInstance().get(ProductionLine.class,
                    wipCost2.list().get(0).getProductionLine());

                if (productionLine != null) {
                  productionLine.setEstimatedCost(productionCostTmp);
                  OBDal.getInstance().save(productionLine);
                }
              }
            }
          }
        }

        productionCost = productionCost.add(productionCostTmp);
        productionCostTmp = new BigDecimal(0);

        if (costMigrated == true) {
          final String hql = "SELECT COUNT(pl), MAX(pl.product.name) FROM ManufacturingProductionLine "
              + "pl WHERE pl.productionPlan='"
              + calculateCostList.getProductionPlan()
              + "' AND pl.productionType='-' AND pl.estimatedCost IS NULL";
          Query query = OBDal.getInstance().getSession().createQuery(hql);

          ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
          while (rs.next()) {
            long count = (Long) rs.get()[0];
            String productName = (String) rs.get()[1];
            if (count != 0) {
              errorMessage = "No average cost found for given product and date Product : "
                  + productName + ", Date : " + costingDate.toString();
            }
          }

          CostcenterVersion costCenterVersion = OBDal.getInstance().get(CostcenterVersion.class,
              calculateCostList.getProductionPlan().getCostCenterVersion());

          if (costCenterVersion != null) {
            String costUOM = costCenterVersion.getCostUOM();

            if (costUOM == "H") {
              productionCostTmp = productionTime.multiply(calculateCostList.getProductionPlan()
                  .getCostCenterVersion().getCost());
            } else if (costUOM == "K") {
              productionCostTmp = producedKg.multiply(calculateCostList.getProductionPlan()
                  .getCostCenterVersion().getCost());
            } else if (costUOM == "U") {
              productionCostTmp = producedUd.multiply(calculateCostList.getProductionPlan()
                  .getCostCenterVersion().getCost());
            }

            ProductionPlan productionPlan = OBDal.getInstance().get(ProductionPlan.class,
                calculateCostList.getProductionPlan());
            productionPlan.setEstimatedCost(productionCostTmp);

            productionCost = productionCost.add(productionCostTmp);
            productionCostTmp = new BigDecimal(0);
          }

          // ------------Calculate Salary Cost------------------- //
          final String hqlSalaryCost = "SELECT ple, scc.costUOM, scc.cost FROM ManufacturingProductionRunEmployee "
              + "ple JOIN ple.salaryCategory.salaryCategoryCostList scc "
              + "WHERE ple.productionPlan='"
              + calculateCostList.getProductionPlan()
              + "' "
              + "AND scc=(SELECT MAX(scc1) FROM SalaryCategoryCost  scc1 WHERE scc=scc1 AND scc1.startingDate = "
              + "(SELECT MAX(scc2.startingDate) FROM SalaryCategoryCost scc2 WHERE scc1=scc2 ))";

          Query querySalaryCost = OBDal.getInstance().getSession().createQuery(hqlSalaryCost);

          ScrollableResults rsSalaryCost = querySalaryCost.scroll(ScrollMode.FORWARD_ONLY);
          while (rsSalaryCost.next()) {
            ProductionRunEmployee ple = (ProductionRunEmployee) rsSalaryCost.get()[0];
            String costUOM = (String) rsSalaryCost.get()[1];
            BigDecimal sccCost = (BigDecimal) rsSalaryCost.get().clone()[2];
            BigDecimal cost = new BigDecimal(0);

            if (costUOM == "H") {
              cost = productionTime.multiply(sccCost.multiply(ple.getQuantity()));
            } else if (costUOM == "K") {
              cost = producedKg.multiply(sccCost.multiply(ple.getQuantity()));
            } else if (costUOM == "U") {
              cost = producedUd.multiply(sccCost.multiply(ple.getQuantity()));
            }

            productionCostTmp = productionCostTmp.add(cost);
            ple.setEstimatedCost(cost);

            OBDal.getInstance().save(ple);
          }
          productionCost = productionCost.add(productionCostTmp);
          productionCostTmp = new BigDecimal(0);

          // ------------Calculate Machine Cost------------------- //
          final String hqlMachineCost = "SELECT plm, mc.costUOM, mc.cost  FROM ManufacturingProductionRunMachine plm JOIN "
              + "plm.machine.manufacturingMachineCostList mc WHERE "
              + "plm.productionPlan='"
              + calculateCostList.getProductionPlan()
              + "' AND "
              + "mc=(SELECT MAX(mc1) FROM ManufacturingMachineCost mc1 WHERE mc=mc1 AND "
              + "mc1.validFromDate=(SELECT MAX(mc2.validFromDate) FROM ManufacturingMachineCost mc2 WHERE mc1=mc2))";

          Query queryMachineCost = OBDal.getInstance().getSession().createQuery(hqlMachineCost);

          ScrollableResults rsMachineCost = queryMachineCost.scroll(ScrollMode.FORWARD_ONLY);
          while (rsMachineCost.next()) {
            ProductionRunMachine plm = (ProductionRunMachine) rsMachineCost.get()[0];
            String costUOM = (String) rsMachineCost.get()[1];
            BigDecimal mccCost = (BigDecimal) rsMachineCost.get().clone()[2];
            BigDecimal cost = new BigDecimal(0);

            if (costUOM == "H") {
              cost = productionTime.multiply(mccCost.multiply(plm.getUsageCoefficient()));
            } else if (costUOM == "K") {
              cost = producedKg.multiply(mccCost.multiply(plm.getUsageCoefficient()));
            } else if (costUOM == "U") {
              cost = producedUd.multiply(mccCost.multiply(plm.getUsageCoefficient()));
            }

            productionCostTmp = productionCostTmp.add(cost);
            plm.setEstimatedCost(cost);

            OBDal.getInstance().save(plm);
          }

          productionCost = productionCost.add(productionCostTmp);
          productionCostTmp = new BigDecimal(0);
        }

        // ------------Calculate Invoice Lines Cost------------------- //
        OBCriteria<ProductionRunInvoiceLine> productionInvoiceLine = OBDal.getInstance()
            .createCriteria(ProductionRunInvoiceLine.class);
        productionInvoiceLine.add(Restrictions.eq(ProductionRunInvoiceLine.PROPERTY_PRODUCTIONPLAN,
            calculateCostList.getProductionPlan()));

        productionCostTmp = new BigDecimal(productionInvoiceLine.list().size());
        productionCost = productionCost.add(productionCostTmp);

        productionCostTmp = new BigDecimal(0);

        // ----------Calculate indirect costs ----------//
        final String hqlIndirectCost = "SELECT plic, icv.costUOM, icv.cost  FROM ManufacturingIndirectCostValue icv "
            + "JOIN icv.indirectCost.manufacturingProductionRunIndirectCostsList plic "
            + "WHERE plic.productionPlan='"
            + calculateCostList.getProductionPlan()
            + "' AND icv.startingDate < '"
            + productionDate
            + "' AND icv.endingDate > '"
            + productionDate + "' AND icv.indirectCost.costType = 'P'";

        Query queryIndirectCost = OBDal.getInstance().getSession().createQuery(hqlIndirectCost);

        ScrollableResults rsIndirectCost = queryIndirectCost.scroll(ScrollMode.FORWARD_ONLY);
        while (rsIndirectCost.next()) {
          ProductionRunIndirectCosts plic = (ProductionRunIndirectCosts) rsIndirectCost.get()[0];
          String costUOM = (String) rsIndirectCost.get()[1];
          BigDecimal icvCost = (BigDecimal) rsIndirectCost.get().clone()[2];
          BigDecimal cost = new BigDecimal(0);

          if (costUOM == "H") {
            cost = productionTime.multiply(icvCost);
          } else if (costUOM == "K") {
            cost = producedKg.multiply(icvCost);
          } else if (costUOM == "U") {
            cost = producedUd.multiply(icvCost);
          }

          productionCostTmp = productionCostTmp.add(cost);
          plic.setEstimatedCost(cost);

          OBDal.getInstance().save(plic);
        }

        productionCost = productionCost.add(productionCostTmp);
        productionCostTmp = new BigDecimal(0);

        // ----------Calculate porcentual indirect costs ----------//
        final String hqlPorcentualCost = "SELECT plic, icv.costUOM, icv.cost FROM ManufacturingIndirectCostValue icv "
            + "JOIN icv.indirectCost.manufacturingProductionRunIndirectCostsList plic "
            + "WHERE plic.productionPlan='"
            + calculateCostList.getProductionPlan()
            + "' AND icv.startingDate < '"
            + productionDate
            + "' AND icv.endingDate > '"
            + productionDate + "' AND icv.costUOM='P'";

        Query queryPorcentualCost = OBDal.getInstance().getSession().createQuery(hqlPorcentualCost);

        ScrollableResults rsPorcentualCost = queryPorcentualCost.scroll(ScrollMode.FORWARD_ONLY);
        while (rsPorcentualCost.next()) {
          ProductionRunIndirectCosts plic = (ProductionRunIndirectCosts) rsPorcentualCost.get()[0];
          String costUOM = (String) rsPorcentualCost.get()[1];
          BigDecimal icvCost = (BigDecimal) rsPorcentualCost.get().clone()[2];
          BigDecimal cost = new BigDecimal(0);

          if (costUOM == "H") {
            cost = productionTime.multiply(icvCost);
          } else if (costUOM == "K") {
            cost = producedKg.multiply(icvCost);
          } else if (costUOM == "U") {
            cost = producedUd.multiply(icvCost);
          }

          productionCostTmp = productionCostTmp.add(cost);
          plic.setEstimatedCost(cost);

          OBDal.getInstance().save(plic);
        }

        productionCost = productionCost.add(productionCostTmp);
        productionCostTmp = new BigDecimal(0);

        // ----------Calculate Cost for Each Produced Product ----------//
        // ----------Calculate the Proportional factor of the cost----------//
        String calculateProportionalWhereClause = "AS pl WHERE pl.productionPlan=? "
            + "AND pl.calculated=false AND pl.productionType='+' AND pl.movementQuantity != '0'";
        List<Object> calculateProportionalParams = new ArrayList<Object>();
        calculateCostParams.add(calculateCostList.getProductionPlan());

        OBQuery<ProductionLine> calculateProportionalQuery = OBDal.getInstance().createQuery(
            ProductionLine.class, calculateProportionalWhereClause, calculateProportionalParams);
        for (ProductionLine calculatePropotionalList : calculateProportionalQuery.list()) {
          OBCriteria<WorkRequirementProduct> proportionalFactorCost = OBDal.getInstance()
              .createCriteria(WorkRequirementProduct.class);
          proportionalFactorCost.add(Restrictions.eq(WorkRequirementProduct.PROPERTY_WRPHASE,
              calculateCostList.getProductionPlan().getWRPhase()));
          proportionalFactorCost.add(Restrictions.eq(WorkRequirementProduct.PROPERTY_PRODUCT,
              calculatePropotionalList.getProduct()));
          proportionalFactorCost.add(Restrictions.eq(
              WorkRequirementProduct.PROPERTY_PRODUCTIONTYPE, "+"));

          // ----------Calculate proportional cost of the production ----------//
          BigDecimal compCost = new BigDecimal(0);
          for (WorkRequirementProduct wrProduct : proportionalFactorCost.list()) {
            compCost = compCost.add(wrProduct.getComponentCost());
          }
          if (proportionalFactorCost.list().size() != 0) {
            compCostSum = compCost;
          }
          calculateCostList.setComponentCost(compCostSum);
          OBDal.getInstance().save(calculateCostList);
          Cost = productionCost.multiply(compCost);

          // ----------Calculate Previous cost of the production ----------//
          String calculatePreviousWhereClause = "AS c WHERE c.product=?"
              + "TRUNC(c.endingDate) > ? AND TRUNC(c.startingDate) <= ? AND c.production=?";
          List<Object> calculatePreviousParams = new ArrayList<Object>();
          calculatePreviousParams.add(calculatePropotionalList.getProduct());
          calculatePreviousParams.add(costingDate);
          calculatePreviousParams.add(costingDate);
          calculatePreviousParams.add(true);

          BigDecimal costOld = new BigDecimal(0);

          OBQuery<Costing> calculatePreviousQuery = OBDal.getInstance().createQuery(Costing.class,
              calculatePreviousWhereClause, calculatePreviousParams);
          if (calculatePreviousQuery.list().size() != 0) {
            costOld = calculatePreviousQuery.list().get(0).getCost();
          } else {
            costOld = new BigDecimal(0);
          }

          // ----------Check if on this production plan is used WIP----------//
          String usedWipWhereClause = "AS ppl WHERE ppl.productionPlan=? AND ppl.productionType=? "
              + "AND EXISTS (SELECT ppl2 FROM ManufacturingProductionLine ppl2 WHERE ppl2.productionType=? "
              + "AND ppl2.productionPlan.production=? AND ppl2.product=ppl.product AND ppl.storageBin=ppl2.storageBin "
              + "AND ppl2.calculated=? AND ppl2.productionPlan.id != ?)";
          List<Object> usedWipParams = new ArrayList<Object>();
          usedWipParams.add(calculatePropotionalList.getProductionPlan());
          usedWipParams.add("-");
          usedWipParams.add("+");
          usedWipParams.add(production);
          usedWipParams.add(false);
          usedWipParams.add(calculatePropotionalList.getProductionPlan());
          OBQuery<ProductionLine> usedWip = OBDal.getInstance().createQuery(ProductionLine.class,
              usedWipWhereClause, usedWipParams);
          // --IF every raw material and WIP is calculated the calculation process continues--//
          if (usedWip.list().size() != 0) {
            BigDecimal calcost = Cost.divide(calculatePropotionalList.getMovementQuantity());
            calculatePropotionalList.setEstimatedCost(calcost);
            OBDal.getInstance().save(calculatePropotionalList);

            // --Check if the product has any cost previously calculated--//
            OBCriteria<Costing> checkCostPreviously = OBDal.getInstance().createCriteria(
                Costing.class);
            checkCostPreviously.add(Restrictions.eq(Costing.PROPERTY_PRODUCT,
                calculateCostList.getProduct()));
            checkCostPreviously.add(Restrictions.eq(Costing.PROPERTY_PRODUCTION, true));

            if (costMigrated == true) {
              Result = "new engine in use";
            } else if (checkCostPreviously.list().size() == 0) {
              Result = "new cost";

              Costing costing = OBProvider.getInstance().get(Costing.class);
              costing.setCreatedBy(user);
              costing.setUpdatedBy(user);
              costing.setClient(calculatePropotionalList.getClient());
              costing.setOrganization(calculatePropotionalList.getOrganization());
              costing.setProduct(calculatePropotionalList.getProduct());
              costing.setEndingDate(endingDate);
              costing.setStartingDate(costingDate);
              costing.setManual(false);
              costing.setProductionLine(calculatePropotionalList);
              costing.setQuantity(calculatePropotionalList.getMovementQuantity());
              costing.setPrice(Cost.divide(calculatePropotionalList.getMovementQuantity()));
              costing.setTotalMovementQuantity(qty.add(calculatePropotionalList
                  .getMovementQuantity()));
              if (calculatePropotionalList.getMovementQuantity().intValue() == 0) {
                costing.setCost(new BigDecimal(0));
              } else {
                costing.setCost(Cost.divide(calculatePropotionalList.getMovementQuantity()));
              }
              costing.setPermanent(false);
              costing.setProduction(true);
              costing.setCostType("AV");
              OBDal.getInstance().save(costing);
            } else {
              Result = "update cost";
              // ----Check if costing is correct----//
              String checkCostingWhereClause = "AS mc WHERE mc.product=? AND mc.endingDate > ? AND mc.startingDate <= ? AND production=?";
              List<Object> checkCostingParams = new ArrayList<Object>();
              checkCostingParams.add(calculatePropotionalList.getProduct());
              checkCostingParams.add(costingDate);
              checkCostingParams.add(costingDate);
              checkCostingParams.add(true);

              OBQuery<Costing> checkCosting = OBDal.getInstance().createQuery(Costing.class,
                  checkCostingWhereClause, checkCostingParams);

              if (checkCosting.list().size() == 1) {
                endingDate = checkCosting.list().get(0).getEndingDate();
                Costing costing = checkCosting.list().get(0);
                costing.setEndingDate(costingDate);
                OBDal.getInstance().save(costing);

                String calculateStockWhereClause1 = "AS  WHERE t.productionLine != ? AND t.movementDate < ? AND t.product = ? ";
                List<Object> calculateStockParams1 = new ArrayList<Object>();
                calculateStockParams1.add(calculatePropotionalList);
                calculateStockParams1.add(productionDate);
                calculateStockParams1.add(calculatePropotionalList.getProduct());

                OBQuery<MaterialTransaction> calculateStock1 = OBDal.getInstance().createQuery(
                    MaterialTransaction.class, calculateStockWhereClause1, calculateStockParams1);

                for (MaterialTransaction calculateStockList : calculateStock1.list()) {
                  qty = qty.add(calculateStockList.getMovementQuantity());
                }

                String calculateStockWhereClause2 = "AS  WHERE t.productionLine != ? AND t.movementDate < ? AND t.product = ? AND t.productionLine.calculated = ?";
                List<Object> calculateStockParams2 = new ArrayList<Object>();
                calculateStockParams2.add(calculatePropotionalList);
                calculateStockParams2.add(productionDate);
                calculateStockParams2.add(calculatePropotionalList.getProduct());
                calculateStockParams2.add(true);

                OBQuery<MaterialTransaction> calculateStock2 = OBDal.getInstance().createQuery(
                    MaterialTransaction.class, calculateStockWhereClause2, calculateStockParams2);

                for (MaterialTransaction calculateStockList : calculateStock2.list()) {
                  qty = qty.add(calculateStockList.getMovementQuantity());
                }

                Result = "insert costing";
                Costing costing2 = OBProvider.getInstance().get(Costing.class);
                costing2.setCreatedBy(user);
                costing2.setUpdatedBy(user);
                costing2.setClient(calculatePropotionalList.getClient());
                costing2.setOrganization(calculatePropotionalList.getOrganization());
                costing2.setProduct(calculatePropotionalList.getProduct());
                costing2.setEndingDate(endingDate);
                costing2.setStartingDate(costingDate);
                costing2.setManual(false);
                costing2.setProductionLine(calculatePropotionalList);
                costing2.setQuantity(calculatePropotionalList.getMovementQuantity());
                costing2.setPrice(Cost.divide(calculatePropotionalList.getMovementQuantity()));
                costing2.setTotalMovementQuantity(qty.add(calculatePropotionalList
                    .getMovementQuantity()));
                if (qty.add(calculatePropotionalList.getMovementQuantity()).intValue() == 0) {
                  costing2.setCost(new BigDecimal(0));
                } else {
                  costing2.setCost((qty.multiply(costOld).add(Cost)).divide(qty
                      .add(calculatePropotionalList.getMovementQuantity())));
                }
                costing2.setPermanent(false);
                costing2.setProduction(true);
                costing2.setCostType("AV");
                OBDal.getInstance().save(costing2);
              } else if (checkCosting.list().size() == 0) {

                Date dateto = endingDate;
                final String hql = "SELECT MIN(m.startingDate) FROM MaterialMgmtCosting m where m.product='"
                    + calculateCostList.getProduct()
                    + "' AND m.startingDate > '"
                    + costingDate
                    + "'";
                Query query = OBDal.getInstance().getSession().createQuery(hql);

                ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
                while (rs.next()) {
                  dateto = (Date) rs.get()[0];
                }

                String calculateStockWhereClause1 = "AS  WHERE t.productionLine != ? AND t.movementDate <= ? AND t.product = ? ";
                List<Object> calculateStockParams1 = new ArrayList<Object>();
                calculateStockParams1.add(calculatePropotionalList);
                calculateStockParams1.add(productionDate);
                calculateStockParams1.add(calculatePropotionalList.getProduct());

                OBQuery<MaterialTransaction> calculateStock1 = OBDal.getInstance().createQuery(
                    MaterialTransaction.class, calculateStockWhereClause1, calculateStockParams1);

                for (MaterialTransaction calculateStockList : calculateStock1.list()) {
                  if (calculateStockList.getMovementQuantity().intValue() > 0) {
                    qty = qty.add(calculateStockList.getMovementQuantity());
                  }
                }
                Costing costing2 = OBProvider.getInstance().get(Costing.class);
                costing2.setCreatedBy(user);
                costing2.setUpdatedBy(user);
                costing2.setClient(calculatePropotionalList.getClient());
                costing2.setOrganization(calculatePropotionalList.getOrganization());
                costing2.setProduct(calculatePropotionalList.getProduct());
                costing2.setEndingDate(dateto);
                costing2.setStartingDate(costingDate);
                costing2.setManual(false);
                costing2.setProductionLine(calculatePropotionalList);
                costing2.setQuantity(calculatePropotionalList.getMovementQuantity());
                costing2.setPrice(Cost.divide(calculatePropotionalList.getMovementQuantity()));
                costing2.setTotalMovementQuantity(qty.add(calculatePropotionalList
                    .getMovementQuantity()));
                if (qty.add(calculatePropotionalList.getMovementQuantity()).intValue() == 0) {
                  costing2.setCost(new BigDecimal(0));
                } else {
                  costing2.setCost((qty.multiply(costOld).add(Cost)).divide(qty
                      .add(calculatePropotionalList.getMovementQuantity())));
                }
                costing2.setPermanent(false);
                costing2.setProduction(true);
                costing2.setCostType("AV");
                OBDal.getInstance().save(costing2);

              } else {
                errorMessage = calculatePropotionalList.getProduct().getSearchKey()
                    + "The costing of the product is wrong. Recalculate the average cost, please.";
                throw new OBException(errorMessage);
              }
            }

            // ----------Line Is Calculate ----------------//

            String calculateLineWhereClause = "AS pl WHERE pl=? OR (pl.product=? AND pl.productionType = ? AND pl IN "
                + "( SELECT pl2 FROM ManufacturingProductionLine pl2 WHERE pl2.productionPlan=?))";
            List<Object> calculateLineParams = new ArrayList<Object>();
            calculateLineParams.add(calculatePropotionalList);
            calculateLineParams.add(calculatePropotionalList.getProduct());
            calculateLineParams.add("-");
            calculateLineParams.add(production);

            OBQuery<ProductionLine> calculateLineQuery = OBDal.getInstance().createQuery(
                ProductionLine.class, calculateLineWhereClause, calculateLineParams);
            for (ProductionLine calculateLine : calculateLineQuery.list()) {
              calculateLine.setCalculated(true);
              OBDal.getInstance().save(calculateLine);
            }

          }
        }
      }

      // --Check if there still remain production lines not calculated--//
      final String hqlCheckNotCalculate = "SELECT COUNT(pl) AS count FROM ManufacturingProductionLine pl WHERE pl.productionPlan.production='"
          + production + "' AND pl.calculated=false";
      Query queryCheckNotCalculate = OBDal.getInstance().getSession()
          .createQuery(hqlCheckNotCalculate);

      ScrollableResults rs = queryCheckNotCalculate.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        long count = (Long) rs.get()[0];

        if (countPl == count) {
          errorMessage = "In production "
              + documentNo
              + "A product being produced is also defined as a raw material required for its own production. Please, check your work effort and production plans.";
        }
      }

    }
  }

  public String getMessage() {
    return errorMessage;
  }
}
