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
 * All portions are Copyright (C) 2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */

package org.openbravo.common.datasource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.query.Query;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.costing.CostAdjustmentUtils;
import org.openbravo.costing.CostingAlgorithm.CostDimension;
import org.openbravo.costing.CostingUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.Costing;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.service.datasource.hql.HqlQueryTransformer;

@ComponentProvider.Qualifier("DFF0A9F7C26C457FA8735A09ACFD5971")
public class CostingTransactionsHQLTransformer extends HqlQueryTransformer {

  private static final String PROP_ADLIST_PRIORITY = org.openbravo.model.ad.domain.List.PROPERTY_SEQUENCENUMBER;
  private static final String PROP_ADLIST_REFERENCE = org.openbravo.model.ad.domain.List.PROPERTY_REFERENCE;
  private static final String PROP_ADLIST_VALUE = org.openbravo.model.ad.domain.List.PROPERTY_SEARCHKEY;
  private static final String MOVEMENTTYPE_REF_ID = "189";

  @Override
  public String transformHqlQuery(String hqlQuery, Map<String, String> requestParameters,
      Map<String, Object> queryNamedParameters) {
    // Sets the named parameters

    Set<String> orgs = null;
    Map<CostDimension, BaseOBObject> costDimensions = null;

    final String costingId = requestParameters.get("@MaterialMgmtCosting.id@");
    String transformedHqlQuery = null;

    if (costingId != null && !costingId.equals("null")) {
      Costing costing = OBDal.getInstance().get(Costing.class, costingId);
      MaterialTransaction transaction = costing.getInventoryTransaction();

      if ("AVA".equals(costing.getCostType()) && transaction != null) {

        // Get cost dimensions
        OrganizationStructureProvider osp = OBContext.getOBContext()
            .getOrganizationStructureProvider(transaction.getClient().getId());

        Organization org = OBContext.getOBContext()
            .getOrganizationStructureProvider(transaction.getClient().getId())
            .getLegalEntity(transaction.getOrganization());

        costDimensions = CostingUtils.getEmptyDimensions();

        CostingRule costingRule = CostingUtils.getCostDimensionRule(org,
            transaction.getTransactionProcessDate());

        if (costing.getProduct().isProduction()) {
          orgs = osp.getChildTree("0", false);
        } else {
          orgs = osp.getChildTree(costing.getOrganization().getId(), true);
          if (costingRule.isWarehouseDimension()) {
            costDimensions.put(CostDimension.Warehouse, transaction.getStorageBin().getWarehouse());
          }
        }

        Costing prevCosting = getPreviousCosting(transaction, orgs, costDimensions);

        if (prevCosting == null || "AVA".equals(prevCosting.getCostType())) {

          // Transform the query
          String previousCostingCost = addCostOnQuery(prevCosting);
          transformedHqlQuery = hqlQuery.replace("@previousCostingCost@", previousCostingCost);

          StringBuilder whereClause = getWhereClause(costing, prevCosting, queryNamedParameters,
              orgs, costDimensions);
          transformedHqlQuery = transformedHqlQuery.replace("@whereClause@",
              whereClause.toString());

          StringBuilder cumQty = addCumQty(costing, queryNamedParameters, orgs, costDimensions);
          transformedHqlQuery = transformedHqlQuery.replace("@cumQty@", cumQty.toString());

          StringBuilder cumCost = addCumCost(cumQty, costing, prevCosting);
          transformedHqlQuery = transformedHqlQuery.replace("@cumCost@", cumCost);

          return transformedHqlQuery;
        }
      }
    }

    transformedHqlQuery = hqlQuery.replace("@whereClause@", " 1 = 2 ");
    transformedHqlQuery = transformedHqlQuery.replace("@previousCostingCost@", "0");
    transformedHqlQuery = transformedHqlQuery.replace("@cumQty@", "0");
    transformedHqlQuery = transformedHqlQuery.replace("@cumCost@", "0");
    return transformedHqlQuery;
  }

  /**
   * Returns the Costing record immediately before than the one selected on the parent tab. This
   * record will be the one displayed in the first position in the grid and the one selected in the
   * parent tab will appear in the last position.
   */

  private Costing getPreviousCosting(MaterialTransaction transaction, Set<String> orgs,
      Map<CostDimension, BaseOBObject> costDimensions) {
    StringBuilder query = new StringBuilder();

    query.append(" select c." + Costing.PROPERTY_ID);
    query.append(" from " + Costing.ENTITY_NAME + " c ");
    query.append(" join c." + Costing.PROPERTY_INVENTORYTRANSACTION + " as trx ");
    query.append(" join trx." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator, ");
    query.append(" " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype ");
    query.append(" where trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :productId ");
    query.append(" and trxtype." + PROP_ADLIST_REFERENCE + ".id = :refid");
    query.append(" and trxtype." + PROP_ADLIST_VALUE + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    query.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    query.append(" and ( ");
    query.append("   trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " < :movementDate");
    // If there are more than one trx on the same movement date and trx process date filter out
    // those types with less priority and / or higher quantity.
    query.append("   or (");
    query.append("     trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :movementDate");
    query.append("     and ( ");
    query.append(
        "     trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < :trxProcessDate");
    query.append("     or (");
    query.append(
        "       trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = :trxProcessDate");
    query.append("       and ( ");
    query.append("         trxtype." + PROP_ADLIST_PRIORITY + " < :trxtypeprio");
    query.append("         or (");
    query.append("           trxtype." + PROP_ADLIST_PRIORITY + " = :trxtypeprio");
    query.append("           and ( ");
    query
        .append("             trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " > :trxqty");
    query.append("             or (");
    query.append(
        "               trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :trxqty");
    query.append("               and trx." + MaterialTransaction.PROPERTY_ID + " <> :trxid");
    query.append(" )))))))) ");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      query.append(" and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse ");
    }
    query.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs) ");
    query.append(" and trx." + MaterialTransaction.PROPERTY_CLIENT + ".id = :clientId ");
    query.append(" order by trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " desc, trx."
        + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " desc, c."
        + Costing.PROPERTY_ENDINGDATE + " desc, trx."
        + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY);

    Query<String> prevCostingQuery = OBDal.getInstance()
        .getSession()
        .createQuery(query.toString(), String.class);
    prevCostingQuery.setParameter("productId", transaction.getProduct().getId());
    prevCostingQuery.setParameter("refid", MOVEMENTTYPE_REF_ID);
    prevCostingQuery.setParameter("movementDate", transaction.getMovementDate());
    prevCostingQuery.setParameter("trxProcessDate", transaction.getTransactionProcessDate());
    prevCostingQuery.setParameter("trxtypeprio",
        CostAdjustmentUtils.getTrxTypePrio(transaction.getMovementType()));
    prevCostingQuery.setParameter("trxqty", transaction.getMovementQuantity());
    prevCostingQuery.setParameter("trxid", transaction.getId());
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      prevCostingQuery.setParameter("warehouse",
          costDimensions.get(CostDimension.Warehouse).getId());
    }
    prevCostingQuery.setParameterList("orgs", orgs);
    prevCostingQuery.setParameter("clientId", transaction.getClient().getId());
    prevCostingQuery.setMaxResults(1);

    final List<String> preCostingIdList = prevCostingQuery.list();

    Costing prevCosting = null;
    if (!preCostingIdList.isEmpty()) {
      prevCosting = OBDal.getInstance().get(Costing.class, preCostingIdList.get(0));
      return prevCosting;
    }
    return null;
  }

  /**
   * Implements the where clause of the hql query. With this where clause all transactions that have
   * happened between the costing record selected in the parent Costing tab and the immediate
   * previous costing record will be displayed. It only takes into account transactions that have
   * its cost calculated.
   */

  private StringBuilder getWhereClause(Costing costing, Costing prevCosting,
      Map<String, Object> queryNamedParameters, Set<String> orgs,
      Map<CostDimension, BaseOBObject> costDimensions) {

    StringBuilder whereClause = new StringBuilder();
    MaterialTransaction transaction = costing.getInventoryTransaction();

    whereClause.append(" trx." + MaterialTransaction.PROPERTY_PRODUCT + ".id = c."
        + MaterialTransaction.PROPERTY_PRODUCT + ".id ");
    whereClause.append(" and trxtype." + PROP_ADLIST_REFERENCE + ".id = :refid");
    whereClause.append(" and trxtype." + PROP_ADLIST_VALUE + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    whereClause.append(" and c.id = :costingId ");
    whereClause.append(" and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    whereClause.append(" and ((trx." + MaterialTransaction.PROPERTY_ID + " = :trxid) ");
    if (prevCosting != null) {
      whereClause.append(" or ((( ");
      whereClause.append("   trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " < trxcosting."
          + MaterialTransaction.PROPERTY_MOVEMENTDATE);
      // If there are more than one trx on the same movement date and trx process date filter out
      // those types with less priority and / or higher quantity.
      whereClause.append("   or (");
      whereClause.append("     trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = trxcosting."
          + MaterialTransaction.PROPERTY_MOVEMENTDATE);
      whereClause.append("     and ( ");
      whereClause.append("       trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE
          + " < trxcosting." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
      whereClause.append("       or (");
      whereClause.append("         trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE
          + " = trxcosting." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
      whereClause.append("         and ( ");
      whereClause.append("         trxtype." + PROP_ADLIST_PRIORITY + " < :trxtypeprio");
      whereClause.append("           or (");
      whereClause.append("             trxtype." + PROP_ADLIST_PRIORITY + " = :trxtypeprio");
      whereClause.append("             and ( ");
      whereClause.append(
          "               trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " > :trxqty");
      whereClause.append("                 or (");
      whereClause.append(
          "                   trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = :trxqty");
      whereClause
          .append("                   and trx." + MaterialTransaction.PROPERTY_ID + " <> :trxid");
      whereClause.append(" )))))))) ");

      whereClause.append(" and ( ");
      whereClause.append(
          "   trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " > :prevCostMovementDate");
      // If there are more than one trx on the same movement date and trx process date filter out
      // those types with higher priority and / or less quantity.
      whereClause.append("   or (");
      whereClause.append(
          "     trx." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = :prevCostMovementDate");
      whereClause.append("     and ( ");
      whereClause.append("       trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE
          + " > :prevCostTrxProcessDate");
      whereClause.append("       or (");
      whereClause.append("         trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE
          + " = :prevCostTrxProcessDate");
      whereClause.append("         and ( ");
      whereClause.append("           trxtype." + PROP_ADLIST_PRIORITY + " > :prevtrxtypeprio");
      whereClause.append("           or (");
      whereClause.append("             trxtype." + PROP_ADLIST_PRIORITY + " = :prevtrxtypeprio");
      whereClause.append("             and ( ");
      whereClause.append(
          "               trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " < :prevtrxqty");
      whereClause.append("               or (");
      whereClause.append("                 trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY
          + " = :prevtrxqty");
      whereClause
          .append("                 and trx." + MaterialTransaction.PROPERTY_ID + " <> :prevtrxid");
      whereClause.append(" ))))))))) ");
      whereClause.append(" or (trx." + MaterialTransaction.PROPERTY_ID + " = :prevtrxid) ");
      whereClause.append(" ) ");
    }
    whereClause.append(" ) ");
    whereClause.append(" and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs) ");
    whereClause.append(" and trx." + MaterialTransaction.PROPERTY_CLIENT + ".id = :clientId ");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      whereClause.append(" and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse ");
    }

    queryNamedParameters.put("refid", MOVEMENTTYPE_REF_ID);
    queryNamedParameters.put("costingId", costing.getId());
    queryNamedParameters.put("orgs", orgs);
    queryNamedParameters.put("clientId", costing.getClient().getId());
    queryNamedParameters.put("trxtypeprio",
        CostAdjustmentUtils.getTrxTypePrio(transaction.getMovementType()));
    queryNamedParameters.put("trxqty", transaction.getMovementQuantity());
    queryNamedParameters.put("trxid", transaction.getId());
    if (prevCosting != null) {
      MaterialTransaction prevCostingTrx = prevCosting.getInventoryTransaction();
      queryNamedParameters.put("prevCostMovementDate", prevCostingTrx.getMovementDate());
      queryNamedParameters.put("prevCostTrxProcessDate",
          prevCostingTrx.getTransactionProcessDate());
      queryNamedParameters.put("prevtrxtypeprio",
          CostAdjustmentUtils.getTrxTypePrio(prevCostingTrx.getMovementType()));
      queryNamedParameters.put("prevtrxqty", prevCostingTrx.getMovementQuantity());
      queryNamedParameters.put("prevtrxid", prevCostingTrx.getId());
    }
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      queryNamedParameters.put("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    return whereClause;
  }

  /**
   * Returns the cost of the previous costing record if exits, if not, 0 is returned.
   */

  private String addCostOnQuery(Costing prevCosting) {

    if (prevCosting != null) {
      return prevCosting.getCost().toString();
    }
    return "0";
  }

  /**
   * Calculates the quantity of the product on the given date and for the given cost dimensions. It
   * only takes into account transactions that have its cost calculated.
   */

  private StringBuilder addCumQty(Costing costing, Map<String, Object> queryNamedParameters,
      Set<String> orgs, Map<CostDimension, BaseOBObject> costDimensions) {

    StringBuilder select = new StringBuilder();
    select.append(" (select sum(trxCost." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + ")");
    select.append("\n from " + MaterialTransaction.ENTITY_NAME + " as trxCost");
    select.append("\n join trxCost." + MaterialTransaction.PROPERTY_STORAGEBIN + " as locator");
    select.append("\n , " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtypeCost");
    select.append("\n where trxtypeCost." + PROP_ADLIST_REFERENCE + ".id = :refid");
    select.append("  and trxtypeCost." + PROP_ADLIST_VALUE + " = trxCost."
        + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    select.append("   and trxCost." + MaterialTransaction.PROPERTY_PRODUCT + ".id = :productId");
    select.append("   and trxCost." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED + " = true");
    select.append("   and ( ");
    select.append("   trxCost." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " < trx."
        + MaterialTransaction.PROPERTY_MOVEMENTDATE);
    select.append("   or (");
    select.append("    trxCost." + MaterialTransaction.PROPERTY_MOVEMENTDATE + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTDATE);
    // If there are more than one trx on the same movement date and trx process date filter out
    // those types with less priority and / or higher quantity.
    select.append("    and (");
    select.append("     trxCost." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " < trx."
        + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
    select.append("     or (");
    select.append("      trxCost." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " = trx."
        + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
    select.append("      and (");
    select.append(
        "       trxtypeCost." + PROP_ADLIST_PRIORITY + " < trxtype." + PROP_ADLIST_PRIORITY);
    select.append("       or (");
    select.append(
        "          trxtypeCost." + PROP_ADLIST_PRIORITY + " = trxtype." + PROP_ADLIST_PRIORITY);
    select.append("        and ( trxCost." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY
        + " > trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY);
    select.append("        or (");
    select.append("         trxCost." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY);
    select.append("         and trxCost." + MaterialTransaction.PROPERTY_ID + " <= trx."
        + MaterialTransaction.PROPERTY_ID);
    select.append("    ))))))))");
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      select.append("  and locator." + Locator.PROPERTY_WAREHOUSE + ".id = :warehouse");
    }
    select.append(" and trxCost." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    select.append(" and trxCost." + MaterialTransaction.PROPERTY_CLIENT + ".id = :clientId )");

    queryNamedParameters.put("refid", MOVEMENTTYPE_REF_ID);
    queryNamedParameters.put("productId", costing.getProduct().getId());
    if (costDimensions.get(CostDimension.Warehouse) != null) {
      queryNamedParameters.put("warehouse", costDimensions.get(CostDimension.Warehouse).getId());
    }
    queryNamedParameters.put("orgs", orgs);
    queryNamedParameters.put("clientId", costing.getClient().getId());

    return select;
  }

  /**
   * Returns the cumulative cost of inventory for the product on a certain date. It is calculated
   * based on the previously calculated quantity and the product cost value at that point.
   */
  private StringBuilder addCumCost(StringBuilder cumQty, Costing costing, Costing prevCosting) {
    StringBuilder cumCost = new StringBuilder();
    cumCost.append(" case when trxcosting.id = trx.id ");
    cumCost.append("   then (");
    cumCost.append(cumQty);
    cumCost.append(" * " + costing.getCost().toString());
    cumCost.append("   ) ");
    cumCost.append("   else ");
    if (prevCosting != null) {
      cumCost.append("   ( ");
      cumCost.append(cumQty);
      cumCost.append(" * " + prevCosting.getCost().toString());
      cumCost.append("   ) ");
    } else {
      cumCost.append(" 0 ");
    }
    cumCost.append(" end ");
    return cumCost;
  }
}
