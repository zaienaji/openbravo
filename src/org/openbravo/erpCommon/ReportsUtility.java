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
 * All portions are Copyright (C) 2013 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */

package org.openbravo.erpCommon;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.CustomerAccounts;
import org.openbravo.model.common.businesspartner.VendorAccounts;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.financialmgmt.accounting.AccountingFact;
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchema;
import org.openbravo.model.financialmgmt.accounting.coa.ElementValue;

public class ReportsUtility {

  public static BigDecimal getBeginningBalance(String orgId, String acctSchemaId, String bpartnerId,
      String dateFrom) {
    return getBeginningBalance(orgId, acctSchemaId, bpartnerId, dateFrom, true);
  }

  public static BigDecimal getBeginningBalance(String orgId, String acctSchemaId, String bpartnerId,
      String dateFrom, boolean isCustomer) {
    if (dateFrom == null || "".equals(dateFrom)) {
      return BigDecimal.ZERO;
    }
    OBCriteria<AccountingFact> obc = OBDal.getInstance().createCriteria(AccountingFact.class);
    obc.add(Restrictions.eq(AccountingFact.PROPERTY_ACCOUNTINGSCHEMA,
        OBDal.getInstance().get(AcctSchema.class, acctSchemaId)));
    obc.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER,
        OBDal.getInstance().get(BusinessPartner.class, bpartnerId)));
    obc.add(Restrictions.in(AccountingFact.PROPERTY_ORGANIZATION, getOrgList(orgId)));
    try {
      obc.add(
          Restrictions.lt(AccountingFact.PROPERTY_ACCOUNTINGDATE, OBDateUtils.getDate(dateFrom)));
    } catch (ParseException pe) {
      // do nothing
    }
    if (isCustomer) {
      List<ElementValue> accL = getValidAccountsList(acctSchemaId, bpartnerId);
      if (!accL.isEmpty()) {
        obc.add(Restrictions.in(AccountingFact.PROPERTY_ACCOUNT, accL));
      }
    } else {
      List<ElementValue> accListV = getValidAccountsListVendor(acctSchemaId, bpartnerId);
      if (!accListV.isEmpty()) {
        obc.add(Restrictions.in(AccountingFact.PROPERTY_ACCOUNT, accListV));
      }
    }
    obc.setFilterOnReadableOrganization(false);

    ProjectionList projections = Projections.projectionList();
    projections.add(Projections.sum(AccountingFact.PROPERTY_DEBIT));
    projections.add(Projections.sum(AccountingFact.PROPERTY_CREDIT));
    obc.setProjection(projections);

    @SuppressWarnings("rawtypes")
    List o = obc.list();
    if (o != null && o.size() > 0) {
      Object[] resultSet = (Object[]) o.get(0);
      BigDecimal debit = (resultSet[0] != null) ? (BigDecimal) resultSet[0] : BigDecimal.ZERO;
      BigDecimal credit = (resultSet[1] != null) ? (BigDecimal) resultSet[1] : BigDecimal.ZERO;
      return debit.subtract(credit);
    }
    return BigDecimal.ZERO;

  }

  private static List<ElementValue> getValidAccountsList(String acctSchemaId, String bpartnerId) {
    List<ElementValue> result = new ArrayList<ElementValue>();
    OBCriteria<CustomerAccounts> obc = OBDal.getInstance().createCriteria(CustomerAccounts.class);
    obc.add(Restrictions.eq(CustomerAccounts.PROPERTY_BUSINESSPARTNER,
        OBDal.getInstance().get(BusinessPartner.class, bpartnerId)));
    obc.add(Restrictions.eq(AccountingFact.PROPERTY_ACCOUNTINGSCHEMA,
        OBDal.getInstance().get(AcctSchema.class, acctSchemaId)));
    obc.setFilterOnReadableOrganization(false);
    obc.setFilterOnActive(false);
    for (CustomerAccounts ca : obc.list()) {
      if (ca.getCustomerReceivablesNo() != null) {
        result.add(ca.getCustomerReceivablesNo().getAccount());
      }
      if (ca.getCustomerPrepayment() != null) {
        result.add(ca.getCustomerPrepayment().getAccount());
      }
    }
    return result;
  }

  private static List<ElementValue> getValidAccountsListVendor(String acctSchemaId,
      String bpartnerId) {
    List<ElementValue> result = new ArrayList<ElementValue>();
    OBCriteria<VendorAccounts> obc = OBDal.getInstance().createCriteria(VendorAccounts.class);
    obc.add(Restrictions.eq(VendorAccounts.PROPERTY_BUSINESSPARTNER,
        OBDal.getInstance().get(BusinessPartner.class, bpartnerId)));
    obc.add(Restrictions.eq(VendorAccounts.PROPERTY_ACCOUNTINGSCHEMA,
        OBDal.getInstance().get(AcctSchema.class, acctSchemaId)));
    obc.setFilterOnReadableOrganization(false);
    obc.setFilterOnActive(false);
    for (VendorAccounts va : obc.list()) {
      if (va.getVendorLiability() != null) {
        result.add(va.getVendorLiability().getAccount());
      }
      if (va.getVendorPrepayment() != null) {
        result.add(va.getVendorPrepayment().getAccount());
      }
    }
    return result;
  }

  private static List<Organization> getOrgList(String orgId) {
    List<Organization> orgList = new ArrayList<Organization>();
    for (String org : new OrganizationStructureProvider().getChildTree(orgId, true)) {
      orgList.add(OBDal.getInstance().get(Organization.class, org));
    }
    return orgList;
  }

}
