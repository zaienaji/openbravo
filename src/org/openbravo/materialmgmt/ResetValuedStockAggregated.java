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
 * All portions are Copyright (C) 2016-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */

package org.openbravo.materialmgmt;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.onhandquantity.ValuedStockAggregated;
import org.openbravo.service.db.DalConnectionProvider;

public class ResetValuedStockAggregated extends BaseProcessActionHandler {

  private static final Logger log4j = LogManager.getLogger();

  /*
   * Resets the values of the Aggregated Table for the selected Legal Entity
   */
  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject request;
    JSONObject result = new JSONObject();

    try {
      OBContext.setAdminMode(true);

      request = new JSONObject(content);
      JSONObject params = request.getJSONObject("_params");
      result.put("retryExecution", true);

      JSONObject msg = new JSONObject();
      Organization legalEntity = OBDal.getInstance()
          .get(Organization.class, params.getString("ad_org_id"));

      // Remove existing data in Aggregated Table
      deleteAggregatedValuesFromDate(null, legalEntity);

      // Get Closed Periods that need to be aggregated
      List<Period> periodList = getClosedPeriodsToAggregate(new Date(),
          legalEntity.getClient().getId(), legalEntity.getId());

      DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
      Date startingDate = formatter.parse("01-01-0000");
      int totalNumberOfPeriods = periodList.size();
      int contPeriodNumber = 0;
      long start = System.currentTimeMillis();

      log4j.debug("[ResetValuedStockAggregated] Total number of Periods to aggregate: "
          + totalNumberOfPeriods);

      for (Period period : periodList) {
        long startPeriod = System.currentTimeMillis();
        if (noAggregatedDataForPeriod(period)
            && costingRuleDefindedForPeriod(legalEntity, period)) {
          insertValuesIntoValuedStockAggregated(legalEntity, period, startingDate);
          startingDate = period.getEndingDate();
        }
        long elapsedTimePeriod = (System.currentTimeMillis() - startPeriod);
        contPeriodNumber++;
        log4j.debug("[ResetValuedStockAggregated] Periods processed: " + contPeriodNumber + " of "
            + totalNumberOfPeriods);
        log4j.debug("[ResetValuedStockAggregated] Time to process period: " + elapsedTimePeriod);
      }
      long elapsedTime = (System.currentTimeMillis() - start);
      log4j.debug("[ResetValuedStockAggregated] Time to process all periods: " + elapsedTime);

      msg.put("severity", "success");
      msg.put("text", OBMessageUtils.messageBD("Success"));
      result.put("message", msg);
      return result;

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      log4j.error("Error in doExecute() method of ResetValuedStockAggregated class", e);
      try {
        JSONObject msg = new JSONObject();
        msg.put("severity", "error");
        msg.put("text", OBMessageUtils.messageBD("ErrorAggregatingData"));
        result.put("message", msg);
      } catch (JSONException e1) {
        log4j.error("Error in doExecute() method of ResetValuedStockAggregated class", e1);
      }
      return result;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /*
   * Remove aggregated values for the selected Legal Entity and from the selected date
   */
  private void deleteAggregatedValuesFromDate(Date date, Organization legalEntity) {
    try {
      Date dateFrom = date;
      if (dateFrom == null) {
        DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        dateFrom = formatter.parse("01-01-0000");
      }
      OrganizationStructureProvider osp = OBContext.getOBContext()
          .getOrganizationStructureProvider(legalEntity.getClient().getId());
      Set<String> orgs = osp.getNaturalTree(legalEntity.getId());

      StringBuffer delete = new StringBuffer();
      delete.append(" delete from " + ValuedStockAggregated.ENTITY_NAME);
      delete.append(" where " + ValuedStockAggregated.PROPERTY_STARTINGDATE + " >= :dateFrom");
      delete.append(" and " + ValuedStockAggregated.PROPERTY_ORGANIZATION + ".id in :orgs");

      @SuppressWarnings("rawtypes")
      Query queryDelete = OBDal.getInstance().getSession().createQuery(delete.toString());
      queryDelete.setParameter("dateFrom", dateFrom);
      queryDelete.setParameterList("orgs", orgs);
      int deleted = queryDelete.executeUpdate();
      log4j.debug(
          "[ResetValuedStockAggregated] No. of records deleted from aggregated table: " + deleted);

    } catch (ParseException e) {
      log4j.error(
          "Error in deleteAggregatedValuesFromDate() method of ResetValuedStockAggregated class",
          e);
    }
  }

  /*
   * Return true if there is not Aggregated data for the selected Period
   */
  public static boolean noAggregatedDataForPeriod(Period period) {
    OBCriteria<ValuedStockAggregated> obc = OBDal.getInstance()
        .createCriteria(ValuedStockAggregated.class);
    obc.add(Restrictions.eq(ValuedStockAggregated.PROPERTY_PERIOD, period));
    return obc.list().isEmpty();
  }

  // Creates aggregated information for the selected Legal Entity and Period
  public static void insertValuesIntoValuedStockAggregated(Organization legalEntity, Period period,
      Date startingDate) {
    try {

      OrganizationStructureProvider osp = OBContext.getOBContext()
          .getOrganizationStructureProvider(legalEntity.getClient().getId());
      Set<String> orgs = osp.getNaturalTree(legalEntity.getId());
      String orgIds = Utility.getInStrSet(orgs);

      List<CostingRule> costingRulesList = getCostingRules(legalEntity, period.getStartingDate(),
          period.getEndingDate());
      for (CostingRule costingRule : costingRulesList) {
        String crStartingDate = costingRule.getStartingDate() == null ? null
            : OBDateUtils.formatDate(costingRule.getStartingDate());
        String crEndingDate = costingRule.getEndingDate() == null ? null
            : OBDateUtils.formatDate(costingRule.getEndingDate());
        GenerateValuedStockAggregatedData.insertData(OBDal.getInstance().getConnection(),
            new DalConnectionProvider(), legalEntity.getId(), period.getId(),
            OBDateUtils.formatDate(period.getStartingDate()),
            OBDateUtils.formatDate(period.getEndingDate()), legalEntity.getCurrency().getId(),
            costingRule.getId(), startingDate == null ? null : OBDateUtils.formatDate(startingDate),
            crStartingDate, crEndingDate, legalEntity.getClient().getId(), orgIds,
            legalEntity.getId());
      }

    } catch (ServletException e) {
      log4j.error(
          "Error in insertValuesIntoValuedStockAggregated() method of ResetValuedStockAggregated class",
          e);
    }
  }

  private static List<CostingRule> getCostingRules(Organization legalEntity, Date startingDate,
      Date endingDate) {

    StringBuilder where = new StringBuilder();
    where.append(" as cr");
    where.append(" where cr.organization.id = :org");
    where.append(" and");
    where.append(" (((cr.startingDate <= :startingDate or cr.startingDate is null)");
    where.append(" and (cr.endingDate > :startingDate or cr.endingDate is null))");
    where.append(" or");
    where.append(" ((cr.startingDate < :endingDate or cr.startingDate is null)");
    where.append(" and (cr.endingDate >= :endingDate or cr.endingDate is null)))");

    OBQuery<CostingRule> query = OBDal.getInstance()
        .createQuery(CostingRule.class, where.toString());
    query.setNamedParameter("org", legalEntity.getId());
    query.setNamedParameter("startingDate", startingDate);
    query.setNamedParameter("endingDate", endingDate);

    return query.list();
  }

  public static boolean costingRuleDefindedForPeriod(Organization legalEntity, Period period) {
    StringBuilder where = new StringBuilder();
    where.append(" as cr");
    where.append(" where cr.organization.id in (:org)");
    where.append(" and");
    where.append(" (cr.startingDate is null or cr.startingDate <= :endingDate)");
    where.append(" and");
    where.append(" (cr.endingDate is null or cr.endingDate >= :startingDate)");

    OBQuery<CostingRule> query = OBDal.getInstance()
        .createQuery(CostingRule.class, where.toString());
    query.setNamedParameter("org", legalEntity.getId());
    query.setNamedParameter("endingDate", period.getEndingDate());
    query.setNamedParameter("startingDate", period.getStartingDate());

    return !query.list().isEmpty();
  }

  /*
   * Returns a list of the Periods that needs to be aggregated for the selected Legal Entity
   */
  public static List<Period> getClosedPeriodsToAggregate(Date endDate, String clientId,
      String organizationID) {

    Organization org = OBDal.getInstance().get(Organization.class, organizationID);
    Organization legalEntity = OBContext.getOBContext()
        .getOrganizationStructureProvider(clientId)
        .getLegalEntity(org);

    Date firstNotClosedPeriodStartingDate = getStartingDateFirstNotClosedPeriod(legalEntity);
    Date lastAggregatedPeriodDateTo = getLastDateToFromAggregatedTable(legalEntity);

    StringBuffer where = new StringBuffer();
    where.append(" as p");
    where.append(" where p.organization.id in (:org)");
    where.append(" and p.periodType = 'S'");
    where.append(" and p.endingDate <= :endDate");
    where.append(" and p.endingDate <= :firstNotClosedPeriodStartingDate");
    where.append(" and p.startingDate >= :lastAggregatedPeriodDateTo");
    where.append(" order by p.endingDate asc");

    OBQuery<Period> query = OBDal.getInstance().createQuery(Period.class, where.toString());
    query.setNamedParameter("org", legalEntity.getId());
    query.setNamedParameter("endDate", endDate);
    query.setNamedParameter("firstNotClosedPeriodStartingDate", firstNotClosedPeriodStartingDate);
    query.setNamedParameter("lastAggregatedPeriodDateTo", lastAggregatedPeriodDateTo);

    return query.list();
  }

  /*
   * Get last Date of for which the data has been aggregated for this Legal Entity
   */
  private static Date getLastDateToFromAggregatedTable(Organization legalEntity) {
    Date dateTo = null;

    OBCriteria<ValuedStockAggregated> obc = OBDal.getInstance()
        .createCriteria(ValuedStockAggregated.class);
    obc.add(Restrictions.eq(ValuedStockAggregated.PROPERTY_ORGANIZATION, legalEntity));
    obc.setProjection(Projections.max(ValuedStockAggregated.PROPERTY_ENDINGDATE));
    try {
      dateTo = (Date) obc.uniqueResult();
      if (dateTo == null) {
        DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        dateTo = (Date) formatter.parse("01-01-0001");
      }
    } catch (Exception e) {
      log4j.error(
          "Error in getDateToFromLastAggregatedPeriod() method of ResetValuedStockAggregated class",
          e);
    }
    return dateTo;
  }

  /*
   * Get the starting date of the first Period that is not closed for this Legal Entity
   */
  private static Date getStartingDateFirstNotClosedPeriod(Organization legalEntity) {
    Date startingDate = null;

    StringBuffer select = new StringBuffer();
    select.append(" select min(p.startingDate)");
    select.append(" from FinancialMgmtPeriod p");
    select.append(" where p.periodType = 'S'");
    select.append(" and   ( 'C' <> (select case");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'O') then 'O'");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'C') then 'C'");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'P') then 'P'");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'N') then 'N'");
    select.append("                   else 'M' end");
    select.append("                 from FinancialMgmtPeriodControl pc");
    select.append("                 where pc.period = p)");
    select.append("    and 'P' <> (select case");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'O') then 'O'");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'C') then 'C'");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'P') then 'P'");
    select.append(
        "                    when (max(pc.periodStatus) = min(pc.periodStatus) and min(pc.periodStatus) = 'N') then 'N'");
    select.append("                   else 'M' end");
    select.append("                 from FinancialMgmtPeriodControl pc");
    select.append("                 where pc.period = p)");
    select.append("       )");
    select.append(" and p.organization.id in (:org)");

    Query<Date> trxQry = OBDal.getInstance()
        .getSession()
        .createQuery(select.toString(), Date.class);
    trxQry.setParameter("org", legalEntity.getId());
    trxQry.setMaxResults(1);

    try {
      List<Date> objetctList = trxQry.list();
      if (objetctList.size() > 0) {
        startingDate = objetctList.get(0);
      } else {
        DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        startingDate = (Date) formatter.parse("01-01-9999");
      }
    } catch (Exception e) {
      log4j.error(
          "Error in getStartingDateFirstNotClosedPeriod() method of ResetValuedStockAggregated class",
          e);
    }
    return startingDate;
  }
}
