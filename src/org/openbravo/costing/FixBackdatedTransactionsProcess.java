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

import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.CostAdjustment;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.json.JsonUtils;

public class FixBackdatedTransactionsProcess extends BaseProcessActionHandler {
  private static final Logger log4j = LogManager.getLogger();
  private static CostAdjustment costAdjHeader = null;

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    costAdjHeader = null;
    JSONObject jsonResponse = new JSONObject();

    OBError msg = new OBError();
    JSONObject jsonRequest;
    try {
      jsonRequest = new JSONObject(content);
      JSONObject jsonparams = jsonRequest.getJSONObject("_params");
      final String ruleId = jsonRequest.getString("M_Costing_Rule_ID");
      Date fixbackdatedfrom = null;
      CostingRule rule = OBDal.getInstance().get(CostingRule.class, ruleId);
      rule.setBackdatedTransactionsFixed(Boolean.TRUE);
      OBDal.getInstance().save(rule);

      if (jsonparams.has("fixbackdatedfrom")
          && !jsonparams.getString("fixbackdatedfrom").equals("null")) {
        try {
          final String repairedfixbackdatedfrom = JsonUtils
              .convertFromXSDToJavaFormat(jsonparams.getString("fixbackdatedfrom"));
          fixbackdatedfrom = JsonUtils.createDateTimeFormat().parse(repairedfixbackdatedfrom);
        } catch (ParseException ignore) {
        }
      } else {
        fixbackdatedfrom = CostingUtils.getCostingRuleStartingDate(rule);
      }
      rule.setFixbackdatedfrom(fixbackdatedfrom);
      try {
        OBContext.setAdminMode(false);

        if (rule.getStartingDate() != null && rule.getFixbackdatedfrom() != null
            && rule.isBackdatedTransactionsFixed()
            && rule.getFixbackdatedfrom().before(rule.getStartingDate())) {
          throw new OBException(
              OBMessageUtils.parseTranslation("@FixBackdateFromBeforeStartingDate2@"));
        }
        CostAdjustmentProcess
            .doGetAlgorithmAdjustmentImp(rule.getCostingAlgorithm().getJavaClassName());

        OrganizationStructureProvider osp = OBContext.getOBContext()
            .getOrganizationStructureProvider(rule.getClient().getId());
        final Set<String> childOrgs = osp.getChildTree(rule.getOrganization().getId(), true);
        ScrollableResults transactions = getTransactions(childOrgs, fixbackdatedfrom,
            rule.getEndingDate());
        int i = 0;
        try {
          while (transactions.next()) {
            MaterialTransaction trx = (MaterialTransaction) transactions.get()[0];
            if (CostAdjustmentUtils.isNeededBackdatedCostAdjustment(trx,
                rule.isWarehouseDimension(), CostingUtils.getCostingRuleStartingDate(rule))) {
              createCostAdjustmenHeader(rule.getOrganization());
              final CostAdjustmentLineParameters lineParameters = new CostAdjustmentLineParameters(
                  trx, null, costAdjHeader);
              lineParameters.setSource(true);
              lineParameters.setBackdatedTransaction(true);
              CostAdjustmentUtils.insertCostAdjustmentLine(lineParameters, trx.getMovementDate());
              i++;
              OBDal.getInstance().flush();
              if ((i % 100) == 0) {
                OBDal.getInstance().getSession().clear();
                // Reload rule after clear session.
                rule = OBDal.getInstance().get(CostingRule.class, ruleId);
              }
            }
          }
        } finally {
          transactions.close();
        }

      } catch (final Exception e) {
        OBDal.getInstance().rollbackAndClose();
        String message = DbUtility.getUnderlyingSQLException(e).getMessage();
        log4j.error(message, e);

        JSONObject errorMessage = new JSONObject();
        errorMessage.put("severity", "error");
        errorMessage.put("title", OBMessageUtils.messageBD("Error"));
        errorMessage.put("text", message);
        jsonResponse.put("message", errorMessage);
        return jsonResponse;

      } finally {
        OBContext.restorePreviousMode();
      }

      if (costAdjHeader != null) {
        try {
          JSONObject message = CostAdjustmentProcess.doProcessCostAdjustment(costAdjHeader);

          if (message.get("severity") != "success") {
            throw new OBException(OBMessageUtils.parseTranslation("@ErrorProcessingCostAdj@") + ": "
                + costAdjHeader.getDocumentNo() + " - " + message.getString("text"));
          }

          msg.setType((String) message.get("severity"));
          msg.setTitle((String) message.get("title"));
          msg.setMessage((String) message.get("text"));
        } catch (JSONException e) {
          throw new OBException(OBMessageUtils.parseTranslation("@ErrorProcessingCostAdj@"));
        } catch (Exception e) {
          OBDal.getInstance().rollbackAndClose();
          String message = DbUtility.getUnderlyingSQLException(e).getMessage();
          log4j.error(message, e);
          JSONObject errorMessage = new JSONObject();

          errorMessage.put("severity", "error");
          errorMessage.put("title", OBMessageUtils.messageBD("Error"));
          errorMessage.put("text", message);
          jsonResponse.put("message", errorMessage);
          return jsonResponse;

        }
      } else {
        msg.setType("Success");
        msg.setMessage(OBMessageUtils.messageBD("Success"));
      }

      JSONObject errorMessage = new JSONObject();

      errorMessage.put("severity", "success");
      errorMessage.put("text", msg.getMessage());
      jsonResponse.put("message", errorMessage);

    } catch (JSONException e2) {

      e2.printStackTrace();
    }
    return jsonResponse;

  }

  private ScrollableResults getTransactions(Set<String> childOrgs, Date startDate, Date endDate) {
    StringBuffer select = new StringBuffer();
    select.append("select trx as trx");
    select.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    select.append(" where trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    select.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    select.append(
        " and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " >= (:startDate)");
    if (endDate != null) {
      select.append(
          " and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < (:endDate)");
    }
    select.append(" order by trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE);

    Query<MaterialTransaction> stockLinesQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), MaterialTransaction.class);
    stockLinesQry.setParameterList("orgs", childOrgs);
    stockLinesQry.setParameter("startDate", startDate);
    if (endDate != null) {
      stockLinesQry.setParameter("endDate", endDate);
    }

    stockLinesQry.setFetchSize(1000);
    ScrollableResults stockLines = stockLinesQry.scroll(ScrollMode.FORWARD_ONLY);
    return stockLines;
  }

  private static void createCostAdjustmenHeader(Organization org) {
    if (costAdjHeader == null) {
      costAdjHeader = CostAdjustmentUtils.insertCostAdjustmentHeader(org, "BDT");
    }
  }

}
