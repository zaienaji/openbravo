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
 * All portions are Copyright (C) 2014-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.financial.FinancialUtils;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.ConversionRateDoc;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.CostAdjustment;
import org.openbravo.model.materialmgmt.cost.LCDistributionAlgorithm;
import org.openbravo.model.materialmgmt.cost.LCMatched;
import org.openbravo.model.materialmgmt.cost.LCReceipt;
import org.openbravo.model.materialmgmt.cost.LCReceiptLineAmt;
import org.openbravo.model.materialmgmt.cost.LandedCost;
import org.openbravo.model.materialmgmt.cost.LandedCostCost;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;

public class LandedCostProcess {
  private static final Logger log = LogManager.getLogger();
  @Inject
  @Any
  private Instance<LandedCostProcessCheck> landedCostProcessChecks;

  /**
   * Method to process a Landed Cost.
   * 
   * @param _landedCost
   *          the landed cost to be processed.
   * @return the message to be shown to the user properly formatted and translated to the user
   *         language.
   */
  public JSONObject processLandedCost(LandedCost _landedCost) {
    LandedCost landedCost = _landedCost;
    JSONObject message = new JSONObject();
    OBContext.setAdminMode(false);
    try {
      message.put("severity", "success");
      message.put("title", "");
      message.put("text", OBMessageUtils.messageBD("Success"));
      try {
        log.debug("Start doChecks");
        doChecks(landedCost, message);
      } catch (OBException e) {
        message.put("severity", "error");
        message.put("text", e.getMessage());
        return message;
      }
      log.debug("Start Distribute Amounts");
      distributeAmounts(landedCost);
      log.debug("Start generateCostAdjustment");

      landedCost = OBDal.getInstance().get(LandedCost.class, landedCost.getId());

      // If active costing rule uses Standard Algorithm, cost adjustment will not be created
      Organization org = OBContext.getOBContext()
          .getOrganizationStructureProvider(landedCost.getClient().getId())
          .getLegalEntity(landedCost.getOrganization());
      if (!StringUtils.equals(CostingUtils.getCostDimensionRule(org, new Date())
          .getCostingAlgorithm()
          .getJavaClassName(), "org.openbravo.costing.StandardAlgorithm")) {
        CostAdjustment ca = generateCostAdjustment(landedCost.getId(), message);
        landedCost.setCostAdjustment(ca);
        message.put("documentNo", ca.getDocumentNo());
      }

      landedCost.setDocumentStatus("CO");
      landedCost.setProcessed(Boolean.TRUE);
      OBDal.getInstance().save(landedCost);
      OBDal.getInstance().flush();
    } catch (JSONException ignore) {
    } finally {
      OBContext.restorePreviousMode();
    }
    return message;
  }

  private void doChecks(LandedCost landedCost, JSONObject message) {
    // Check there are Receipt Lines and Costs.
    OBCriteria<LandedCost> critLC = OBDal.getInstance().createCriteria(LandedCost.class);
    critLC.add(Restrictions.sizeEq(LandedCost.PROPERTY_LANDEDCOSTCOSTLIST, 0));
    critLC.add(Restrictions.eq(LandedCost.PROPERTY_ID, landedCost.getId()));
    if (critLC.uniqueResult() != null) {
      throw new OBException(OBMessageUtils.messageBD("LandedCostNoCosts"));
    }

    critLC = OBDal.getInstance().createCriteria(LandedCost.class);
    critLC.add(Restrictions.sizeEq(LandedCost.PROPERTY_LANDEDCOSTRECEIPTLIST, 0));
    critLC.add(Restrictions.eq(LandedCost.PROPERTY_ID, landedCost.getId()));
    if (critLC.uniqueResult() != null) {
      throw new OBException(OBMessageUtils.messageBD("LandedCostNoReceipts"));
    }

    // Check that all related receipt lines with movementqty >=0 have their cost already calculated.
    StringBuffer where = new StringBuffer();
    where.append("\n  as lcr ");
    where.append("\n  left join lcr." + LCReceipt.PROPERTY_GOODSSHIPMENT + " lcrr");
    where.append("\n  left join lcr." + LCReceipt.PROPERTY_GOODSSHIPMENTLINE + " lcrrl");
    where.append("\n  where exists (");
    where.append("\n    select 1");
    where.append("\n    from " + MaterialTransaction.ENTITY_NAME + " as trx");
    where.append("\n    join trx." + MaterialTransaction.PROPERTY_GOODSSHIPMENTLINE + " as iol");
    where.append("\n    join iol." + ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT + " as io");
    where.append("\n    where trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = false");
    where.append("\n    and iol." + ShipmentInOutLine.PROPERTY_MOVEMENTQUANTITY + " >= 0");
    where.append("\n    and ((lcrrl is not null and lcrrl = iol)");
    where.append("\n    or (lcrrl is null and lcrr = io))");
    where.append("\n  )");
    where.append("\n  and lcr." + LCReceipt.PROPERTY_LANDEDCOST + " = :landedcost");
    OBQuery<LCReceipt> qryTrx = OBDal.getInstance().createQuery(LCReceipt.class, where.toString());
    qryTrx.setNamedParameter("landedcost", landedCost);
    if (qryTrx.count() > 0) {
      String strReceiptNumbers = "";
      for (LCReceipt lcrl : qryTrx.list()) {
        if (strReceiptNumbers.length() > 0) {
          strReceiptNumbers += ", ";
        }
        if (lcrl.getGoodsShipmentLine() != null) {
          strReceiptNumbers += lcrl.getGoodsShipmentLine().getIdentifier();
        } else {
          strReceiptNumbers += lcrl.getGoodsShipment().getIdentifier();
        }
      }
      String errorMsg = OBMessageUtils.messageBD("LandedCostReceiptWithoutCosts");
      log.error("Processed and Cost Calculated check error");
      throw new OBException(errorMsg + "\n" + strReceiptNumbers);
    }

    // Execute checks added implementing LandedCostProcessCheck interface.
    for (LandedCostProcessCheck checksInstance : landedCostProcessChecks) {
      checksInstance.doCheck(landedCost, message);
    }
  }

  private void distributeAmounts(LandedCost landedCost) {
    OBCriteria<LandedCostCost> criteria = OBDal.getInstance().createCriteria(LandedCostCost.class);
    criteria.add(Restrictions.eq(LandedCostCost.PROPERTY_LANDEDCOST, landedCost));
    criteria.addOrderBy(LandedCostCost.PROPERTY_LINENO, true);
    for (LandedCostCost lcCost : criteria.list()) {
      lcCost = OBDal.getInstance().get(LandedCostCost.class, lcCost.getId());
      log.debug("Start Distributing lcCost {}", lcCost.getIdentifier());
      // Load distribution algorithm
      LandedCostDistributionAlgorithm lcDistAlg = getDistributionAlgorithm(
          lcCost.getLandedCostDistributionAlgorithm());

      lcDistAlg.distributeAmount(lcCost, false);
      lcCost = OBDal.getInstance().get(LandedCostCost.class, lcCost.getId());
      if (lcCost.getInvoiceLine() != null) {
        log.debug("Match with invoice line {}", lcCost.getInvoiceLine().getIdentifier());
        matchCostWithInvoiceLine(lcCost);
      }
    }
    OBDal.getInstance().flush();
  }

  private CostAdjustment generateCostAdjustment(String strLandedCostId, JSONObject message) {
    LandedCost landedCost = OBDal.getInstance().get(LandedCost.class, strLandedCostId);
    Date referenceDate = landedCost.getReferenceDate();
    CostAdjustment ca = CostAdjustmentUtils.insertCostAdjustmentHeader(landedCost.getOrganization(),
        "LC");

    String strResult = OBMessageUtils.messageBD("LandedCostProcessed");
    Map<String, String> map = new HashMap<String, String>();
    map.put("documentNo", ca.getDocumentNo());
    try {
      message.put("title", OBMessageUtils.messageBD("Success"));
      message.put("text", OBMessageUtils.parseTranslation(strResult, map));
    } catch (JSONException ignore) {
    }

    StringBuffer hql = new StringBuffer();
    hql.append(" select sum(rla." + LCReceiptLineAmt.PROPERTY_AMOUNT + ") as amt");
    hql.append(
        "   , rla." + LCReceiptLineAmt.PROPERTY_LANDEDCOSTCOST + ".currency.id as lcCostCurrency");
    hql.append("   , gsl." + ShipmentInOutLine.PROPERTY_ID + " as receipt");
    hql.append("   , (select " + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " from "
        + MaterialTransaction.ENTITY_NAME + " as transaction where "
        + MaterialTransaction.PROPERTY_GOODSSHIPMENTLINE + ".id = gsl."
        + ShipmentInOutLine.PROPERTY_ID + ") as trxprocessdate");
    hql.append(" from " + LCReceiptLineAmt.ENTITY_NAME + " as rla");
    hql.append("   join rla." + LCReceiptLineAmt.PROPERTY_LANDEDCOSTRECEIPT + " as rl");
    hql.append("   join rl." + LCReceipt.PROPERTY_GOODSSHIPMENT + " as gs");
    hql.append("   join rla." + LCReceiptLineAmt.PROPERTY_GOODSSHIPMENTLINE + " as gsl");
    hql.append(" where rl." + LCReceipt.PROPERTY_LANDEDCOST + " = :lc");
    hql.append("   and rla." + LCReceiptLineAmt.PROPERTY_ISMATCHINGADJUSTMENT + " = false ");
    hql.append(" group by rla." + LCReceiptLineAmt.PROPERTY_LANDEDCOSTCOST + ".currency.id");
    hql.append(" , gsl." + ShipmentInOutLine.PROPERTY_ID);
    hql.append(" , gs." + ShipmentInOut.PROPERTY_DOCUMENTNO);
    hql.append(" , gsl." + ShipmentInOutLine.PROPERTY_LINENO);
    hql.append(" order by trxprocessdate");
    hql.append(" , gs." + ShipmentInOut.PROPERTY_DOCUMENTNO);
    hql.append(" , gsl." + ShipmentInOutLine.PROPERTY_LINENO);
    hql.append(" , amt");

    Query<Object[]> qryLCRLA = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), Object[].class);
    qryLCRLA.setParameter("lc", landedCost);

    ScrollableResults receiptamts = qryLCRLA.scroll(ScrollMode.FORWARD_ONLY);
    int i = 0;
    try {
      while (receiptamts.next()) {
        log.debug("Process receipt amounts");
        Object[] receiptAmt = receiptamts.get();
        BigDecimal amt = (BigDecimal) receiptAmt[0];
        Currency lcCostCurrency = OBDal.getInstance().get(Currency.class, receiptAmt[1]);
        ShipmentInOutLine receiptLine = OBDal.getInstance()
            .get(ShipmentInOutLine.class, receiptAmt[2]);
        // MaterialTransaction receiptLine = (MaterialTransaction) record[1];
        MaterialTransaction trx = receiptLine.getMaterialMgmtMaterialTransactionList().get(0);
        final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(trx,
            amt, ca, lcCostCurrency);
        lineParameters.setSource(true);
        lineParameters.setUnitCost(false);
        lineParameters.setNeedPosting(false);
        Long lineNo = (i + 1) * 10L;
        CostAdjustmentUtils.insertCostAdjustmentLine(lineParameters, referenceDate, lineNo);

        if (i % 100 == 0) {
          OBDal.getInstance().flush();
          OBDal.getInstance().getSession().clear();
          ca = OBDal.getInstance().get(CostAdjustment.class, ca.getId());
        }
        i++;
      }
    } finally {
      receiptamts.close();
    }

    CostAdjustmentProcess.doProcessCostAdjustment(ca);

    return ca;
  }

  private LandedCostDistributionAlgorithm getDistributionAlgorithm(
      LCDistributionAlgorithm lcDistAlg) {
    LandedCostDistributionAlgorithm lcDistAlgInstance;
    try {
      Class<?> clz = null;
      clz = OBClassLoader.getInstance().loadClass(lcDistAlg.getJavaClassName());
      lcDistAlgInstance = (LandedCostDistributionAlgorithm) WeldUtils
          .getInstanceFromStaticBeanManager(clz);
    } catch (Exception e) {
      log.error("Error loading distribution algorithm: " + lcDistAlg.getJavaClassName(), e);
      String strError = OBMessageUtils.messageBD("LCDistributionAlgorithmNotFound");
      Map<String, String> map = new HashMap<String, String>();
      map.put("distalg", lcDistAlg.getIdentifier());
      throw new OBException(OBMessageUtils.parseTranslation(strError, map));
    }
    return lcDistAlgInstance;
  }

  public static JSONObject doProcessLandedCost(LandedCost landedCost) {
    LandedCostProcess lcp = WeldUtils.getInstanceFromStaticBeanManager(LandedCostProcess.class);
    JSONObject message = lcp.processLandedCost(landedCost);
    return message;
  }

  private void matchCostWithInvoiceLine(LandedCostCost lcc) {
    LCMatched lcm = OBProvider.getInstance().get(LCMatched.class);
    lcm.setOrganization(lcc.getOrganization());
    lcm.setLandedCostCost(lcc);
    lcm.setAmount(lcc.getAmount());
    lcm.setInvoiceLine(lcc.getInvoiceLine());
    OBDal.getInstance().save(lcm);

    final OBCriteria<ConversionRateDoc> conversionRateDoc = OBDal.getInstance()
        .createCriteria(ConversionRateDoc.class);
    conversionRateDoc.add(
        Restrictions.eq(ConversionRateDoc.PROPERTY_INVOICE, lcm.getInvoiceLine().getInvoice()));
    ConversionRateDoc invoiceconversionrate = (ConversionRateDoc) conversionRateDoc.uniqueResult();
    Currency currency = lcc.getOrganization().getCurrency() != null
        ? lcc.getOrganization().getCurrency()
        : lcc.getOrganization().getClient().getCurrency();
    ConversionRate landedCostrate = FinancialUtils.getConversionRate(
        lcc.getLandedCost().getReferenceDate(), lcc.getCurrency(), currency, lcc.getOrganization(),
        lcc.getClient());

    if (invoiceconversionrate != null
        && invoiceconversionrate.getRate() != landedCostrate.getMultipleRateBy()) {
      BigDecimal amount = lcc.getAmount()
          .multiply(invoiceconversionrate.getRate())
          .subtract(lcc.getAmount().multiply(landedCostrate.getMultipleRateBy()))
          .divide(landedCostrate.getMultipleRateBy(), currency.getStandardPrecision().intValue(),
              RoundingMode.HALF_UP);
      LCMatched lcmCm = OBProvider.getInstance().get(LCMatched.class);
      lcmCm.setOrganization(lcc.getOrganization());
      lcmCm.setLandedCostCost(lcc);
      lcmCm.setAmount(amount);
      lcmCm.setInvoiceLine(lcc.getInvoiceLine());
      lcmCm.setConversionmatching(true);
      OBDal.getInstance().save(lcmCm);

      lcc.setMatched(Boolean.FALSE);
      lcc.setProcessed(Boolean.FALSE);
      lcc.setMatchingAdjusted(true);
      OBDal.getInstance().flush();
      LCMatchingProcess.doProcessLCMatching(lcc);
    }

    lcc.setMatched(Boolean.TRUE);
    lcc.setProcessed(Boolean.TRUE);
    OBCriteria<LCMatched> critMatched = OBDal.getInstance().createCriteria(LCMatched.class);
    critMatched.add(Restrictions.eq(LCMatched.PROPERTY_LANDEDCOSTCOST, lcc));
    critMatched.setProjection(Projections.sum(LCMatched.PROPERTY_AMOUNT));
    BigDecimal matchedAmt = (BigDecimal) critMatched.uniqueResult();
    if (matchedAmt == null) {
      matchedAmt = lcc.getAmount();
    }
    lcc.setMatchingAmount(matchedAmt);
  }
}
