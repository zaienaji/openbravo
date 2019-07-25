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
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.CostAdjustment;
import org.openbravo.model.materialmgmt.cost.LCDistributionAlgorithm;
import org.openbravo.model.materialmgmt.cost.LCMatched;
import org.openbravo.model.materialmgmt.cost.LCReceipt;
import org.openbravo.model.materialmgmt.cost.LCReceiptLineAmt;
import org.openbravo.model.materialmgmt.cost.LandedCostCost;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;

public class LCMatchingProcess {
  private static final Logger log = LogManager.getLogger();
  @Inject
  @Any
  private Instance<LCMatchingProcessCheck> LCMatchingProcessChecks;

  /**
   * Method to process a Landed Cost.
   * 
   * @param _lcCost
   *          the landed cost to be processed.
   * @return the message to be shown to the user properly formatted and translated to the user
   *         language.
   */
  public JSONObject processLCMatching(LandedCostCost _lcCost) {
    LandedCostCost lcCost = _lcCost;
    JSONObject message = new JSONObject();
    OBContext.setAdminMode(true);
    try {
      message.put("severity", "success");
      message.put("title", "");
      message.put("text", OBMessageUtils.messageBD("Success"));
      try {
        doChecks(lcCost, message);
      } catch (OBException e) {
        message.put("severity", "error");
        message.put("text", e.getMessage());
        return message;
      }
      OBCriteria<LCMatched> critMatched = OBDal.getInstance().createCriteria(LCMatched.class);
      critMatched.add(Restrictions.eq(LCMatched.PROPERTY_LANDEDCOSTCOST, lcCost));
      critMatched.setProjection(Projections.sum(LCMatched.PROPERTY_AMOUNT));
      BigDecimal matchedAmt = (BigDecimal) critMatched.uniqueResult();
      if (matchedAmt != null) {
        lcCost.setMatchingAmount(matchedAmt);
        OBDal.getInstance().save(lcCost);
      }

      if (lcCost.isMatchingAdjusted() && lcCost.getAmount().compareTo(matchedAmt) != 0) {
        distributeAmounts(lcCost);
        lcCost = OBDal.getInstance().get(LandedCostCost.class, lcCost.getId());
        // If active costing rule uses Standard Algorithm, cost adjustment will not be created
        Organization org = OBContext.getOBContext()
            .getOrganizationStructureProvider(lcCost.getClient().getId())
            .getLegalEntity(lcCost.getOrganization());
        if (!StringUtils.equals(CostingUtils.getCostDimensionRule(org, new Date())
            .getCostingAlgorithm()
            .getJavaClassName(), "org.openbravo.costing.StandardAlgorithm")) {
          String strMatchCAId = generateCostAdjustment(lcCost.getId(), message);
          lcCost.setMatchingCostAdjustment((CostAdjustment) OBDal.getInstance()
              .getProxy(CostAdjustment.ENTITY_NAME, strMatchCAId));
        }
        OBDal.getInstance().save(lcCost);
      }

      lcCost = OBDal.getInstance().get(LandedCostCost.class, lcCost.getId());
      lcCost.setMatched(Boolean.TRUE);
      lcCost.setProcessed(Boolean.TRUE);
      OBDal.getInstance().save(lcCost);
    } catch (JSONException ignore) {
    } finally {
      OBContext.restorePreviousMode();
    }
    return message;
  }

  private void doChecks(LandedCostCost lcCost, JSONObject message) {
    // Check there are Matching Lines.
    OBCriteria<LandedCostCost> critLCMatched = OBDal.getInstance()
        .createCriteria(LandedCostCost.class);
    critLCMatched.add(Restrictions.sizeEq(LandedCostCost.PROPERTY_LANDEDCOSTMATCHEDLIST, 0));
    critLCMatched.add(Restrictions.eq(LandedCostCost.PROPERTY_ID, lcCost.getId()));
    if (critLCMatched.uniqueResult() != null) {
      throw new OBException(OBMessageUtils.messageBD("LCCostNoMatchings"));
    }

    // Execute checks added implementing LandedCostProcessCheck interface.
    for (LCMatchingProcessCheck checksInstance : LCMatchingProcessChecks) {
      checksInstance.doCheck(lcCost, message);
    }
  }

  private void distributeAmounts(LandedCostCost lcCost) {
    // Load distribution algorithm
    LandedCostDistributionAlgorithm lcDistAlg = getDistributionAlgorithm(
        lcCost.getLandedCostDistributionAlgorithm());

    lcDistAlg.distributeAmount(lcCost, true);
    OBDal.getInstance().flush();
  }

  private String generateCostAdjustment(String strLCCostId, JSONObject message)
      throws JSONException {
    LandedCostCost lcCost = OBDal.getInstance().get(LandedCostCost.class, strLCCostId);
    Date referenceDate = lcCost.getAccountingDate();
    CostAdjustment ca = CostAdjustmentUtils.insertCostAdjustmentHeader(lcCost.getOrganization(),
        "LC");

    String strResult = OBMessageUtils.messageBD("LCMatchingProcessed");
    Map<String, String> map = new HashMap<String, String>();
    map.put("documentNo", ca.getDocumentNo());
    message.put("title", OBMessageUtils.messageBD("Success"));
    message.put("text", OBMessageUtils.parseTranslation(strResult, map));

    StringBuffer hql = new StringBuffer();
    hql.append(" select sum(rla." + LCReceiptLineAmt.PROPERTY_AMOUNT + ") as amt");
    hql.append("   , rla." + LCReceipt.PROPERTY_GOODSSHIPMENTLINE + ".id as receipt");
    hql.append("   , (select " + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " from "
        + MaterialTransaction.ENTITY_NAME + " as transaction where "
        + MaterialTransaction.PROPERTY_GOODSSHIPMENTLINE + ".id = rla."
        + LCReceipt.PROPERTY_GOODSSHIPMENTLINE + ".id) as trxprocessdate");
    hql.append(" from " + LCReceiptLineAmt.ENTITY_NAME + " as rla");
    hql.append(" where rla." + LCReceiptLineAmt.PROPERTY_LANDEDCOSTCOST + " = :lcc");
    hql.append("   and rla." + LCReceiptLineAmt.PROPERTY_ISMATCHINGADJUSTMENT + " = true ");
    hql.append(" group by rla." + LCReceipt.PROPERTY_GOODSSHIPMENTLINE + ".id");
    hql.append(" order by trxprocessdate, amt");

    Query<Object[]> qryLCRLA = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), Object[].class);
    qryLCRLA.setParameter("lcc", lcCost);

    ScrollableResults receiptamts = qryLCRLA.scroll(ScrollMode.FORWARD_ONLY);
    int i = 0;
    try {
      while (receiptamts.next()) {
        Object[] receiptAmt = receiptamts.get();
        BigDecimal amt = (BigDecimal) receiptAmt[0];
        ShipmentInOutLine receiptLine = OBDal.getInstance()
            .get(ShipmentInOutLine.class, receiptAmt[1]);
        MaterialTransaction trx = receiptLine.getMaterialMgmtMaterialTransactionList().get(0);
        final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(trx,
            amt, ca, lcCost.getCurrency());
        lineParameters.setSource(true);
        lineParameters.setUnitCost(false);
        lineParameters.setNeedPosting(false);
        CostAdjustmentUtils.insertCostAdjustmentLine(lineParameters, referenceDate);

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
    ca = OBDal.getInstance().get(CostAdjustment.class, ca.getId());
    CostAdjustmentProcess.doProcessCostAdjustment(ca);
    return ca.getId();
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

  public static JSONObject doProcessLCMatching(LandedCostCost lcCost) {
    LCMatchingProcess lcp = WeldUtils.getInstanceFromStaticBeanManager(LCMatchingProcess.class);
    JSONObject message = lcp.processLCMatching(lcCost);
    return message;
  }

}
