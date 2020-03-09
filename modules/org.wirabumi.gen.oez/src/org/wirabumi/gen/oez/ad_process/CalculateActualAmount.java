package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Category;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.plm.ProductCategory;
import org.openbravo.model.financialmgmt.accounting.AccountingFact;
import org.openbravo.model.financialmgmt.accounting.Budget;
import org.openbravo.model.financialmgmt.accounting.BudgetLine;
import org.openbravo.model.financialmgmt.accounting.Costcenter;
import org.openbravo.model.financialmgmt.accounting.UserDimension1;
import org.openbravo.model.financialmgmt.accounting.UserDimension2;
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchema;
import org.openbravo.model.financialmgmt.accounting.coa.ElementValue;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.marketing.Campaign;
import org.openbravo.model.materialmgmt.cost.ABCActivity;
import org.openbravo.model.project.Project;
import org.openbravo.model.sales.SalesRegion;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

public class CalculateActualAmount extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    try {
      String budgetID = (String) bundle.getParams().get("C_Budget_ID");
      List<BudgetLine> linesBudget = new ArrayList<BudgetLine>();
      if (budgetID != null) {
        Budget budget = OBDal.getInstance().get(Budget.class, budgetID);
        linesBudget = budget.getFinancialMgmtBudgetLineList();
      } else {
        OBQuery<Budget> calculatedBudget = OBDal.getInstance().createQuery(Budget.class,
            "where oezDocstatus='CO'");
        List<Budget> budgets = calculatedBudget.list();
        for (Budget bdgt : budgets) {
          List<BudgetLine> lines = bdgt.getFinancialMgmtBudgetLineList();
          for (BudgetLine linesBudgets : lines) {
            linesBudget.add(linesBudgets);
          }
        }
      }

      for (BudgetLine line : linesBudget) {
        BigDecimal actualAmount = getActualAmount(null, line.getAccountElement(), line.getPeriod(),
            line.getCurrency(), line.getProduct(), line.getBusinessPartner(),
            line.getOrganization(), line.getSalesRegion(), line.getProject(),
            line.getSalesCampaign(), line.getActivity(), line.getStDimension(),
            line.getNdDimension(), line.getBusinessPartnerCategory(), line.getProductCategory(),
            line.getAsset(), line.getCostcenter());
        line.setActualAmount(actualAmount);
        OBDal.getInstance().save(line);
      }
      OBDal.getInstance().commitAndClose();

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      e.printStackTrace();
    }
  }

  private BigDecimal getActualAmount(AcctSchema schema, ElementValue elementValue, Period period,
      Currency currency, Product product, BusinessPartner bpartner, Organization orgTrx,
      SalesRegion salesRegion, Project project, Campaign campaingn, ABCActivity abcActivity,
      UserDimension1 user1, UserDimension2 user2, Category bpartnerGroup,
      ProductCategory productCategory, Asset asset, Costcenter costcenter) {
    BigDecimal amount = new BigDecimal(0);
    try {
      OBCriteria<AccountingFact> accountingClass = OBDal.getInstance().createCriteria(
          AccountingFact.class);
      // mandatory filter
      accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_ACCOUNTINGSCHEMA, schema));
      accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_CURRENCY, currency));

      // optional Filter
      if (product != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_PRODUCT, product));
      }

      if (bpartner != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, bpartner));
      }

      if (orgTrx != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, orgTrx));
      }

      if (salesRegion != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, salesRegion));
      }

      if (project != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, project));
      }
      if (campaingn != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, campaingn));
      }

      if (abcActivity != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, abcActivity));
      }
      if (user1 != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, user1));
      }
      if (user2 != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, user2));
      }
      if (bpartnerGroup != null) {
        accountingClass
            .add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, bpartnerGroup));
      }
      if (productCategory != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER,
            productCategory));
      }
      if (asset != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, asset));
      }
      if (costcenter != null) {
        accountingClass.add(Restrictions.eq(AccountingFact.PROPERTY_BUSINESSPARTNER, costcenter));
      }
      List<AccountingFact> account = accountingClass.list();
      BigDecimal credit = new BigDecimal(0);
      BigDecimal debit = new BigDecimal(0);
      for (AccountingFact fact : account) {
        debit = debit.add(fact.getDebit());
        credit = credit.add(fact.getCredit());
      }
      amount = debit.subtract(credit);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return amount;
  }

}
