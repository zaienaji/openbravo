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
 * All portions are Copyright (C) 2012-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.financial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.util.Check;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.currency.ConversionRate;
import org.openbravo.model.common.currency.ConversionRateDoc;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.pricing.pricelist.PriceListVersion;
import org.openbravo.model.pricing.pricelist.ProductPrice;
import org.openbravo.service.db.CallStoredProcedure;

public class FinancialUtils {
  private static final Logger log4j = LogManager.getLogger();

  public static final String PRECISION_STANDARD = "A";
  public static final String PRECISION_COSTING = "C";
  public static final String PRECISION_PRICE = "P";

  /**
   * @see #getProductStdPrice(Product, Date, boolean, PriceList, Currency, Organization)
   */
  public static BigDecimal getProductStdPrice(Product product, Date date, boolean useSalesPriceList,
      Currency currency, Organization organization) throws OBException {
    return getProductStdPrice(product, date, useSalesPriceList, null, currency, organization);
  }

  /**
   * Calculates the Standard Price of the given Product. It uses the
   * {@link #getProductPrice(Product, Date, boolean, PriceList) getProductPrice()} method to get the
   * ProductPrice to be used. In case a conversion is needed it uses the
   * {@link #getConvertedAmount(BigDecimal, Currency, Currency, Date, Organization, String)
   * getConvertedAmount()} method.
   * 
   * @param product
   *          Product to get its ProductPrice.
   * @param date
   *          Date when Product Price is needed.
   * @param useSalesPriceList
   *          boolean to set if the price list should be a sales or purchase price list.
   * @param pricelist
   *          PriceList to get its ProductPrice
   * @param currency
   *          Currency to convert to the returned price.
   * @param organization
   *          Organization where price needs to be used to retrieve the proper conversion rate.
   * @return a BigDecimal with the Standard Price of the Product for the given parameters.
   * @throws OBException
   *           when no valid ProductPrice is found.
   */
  public static BigDecimal getProductStdPrice(Product product, Date date, boolean useSalesPriceList,
      PriceList pricelist, Currency currency, Organization organization) throws OBException {
    ProductPrice pp = getProductPrice(product, date, useSalesPriceList, pricelist);
    BigDecimal price = pp.getStandardPrice();
    if (!pp.getPriceListVersion().getPriceList().getCurrency().getId().equals(currency.getId())) {
      // Conversion is needed.
      price = getConvertedAmount(price, pp.getPriceListVersion().getPriceList().getCurrency(),
          currency, date, organization, PRECISION_PRICE);
    }

    return price;
  }

  /**
   * @see #getProductPrice(Product, Date, boolean, PriceList, boolean)
   */
  public static ProductPrice getProductPrice(Product product, Date date, boolean useSalesPriceList)
      throws OBException {
    return getProductPrice(product, date, useSalesPriceList, null, true);
  }

  /**
   * @see #getProductPrice(Product, Date, boolean, PriceList, boolean)
   */
  public static ProductPrice getProductPrice(Product product, Date date, boolean useSalesPriceList,
      PriceList priceList) throws OBException {
    return getProductPrice(product, date, useSalesPriceList, priceList, true);
  }

  /**
   * @see #getProductPrice(Product, Date, boolean, PriceList, boolean, boolean)
   */
  public static ProductPrice getProductPrice(Product product, Date date, boolean useSalesPriceList,
      PriceList priceList, boolean throwException) throws OBException {
    return getProductPrice(product, date, useSalesPriceList, priceList, throwException, true);
  }

  /**
   * Method to get a valid ProductPrice for the given Product. It only considers PriceList versions
   * valid on the given date. If a PriceList is given it searches on that one. If PriceList null is
   * passed it search on any Sales or Purchase PriceList based on the useSalesPriceList.
   * 
   * @param product
   *          Product to get its ProductPrice.
   * @param date
   *          Date when Product Price is needed.
   * @param useSalesPriceList
   *          boolean to set if the price list should be a sales or purchase price list.
   * @param priceList
   *          PriceList to get its ProductPrice
   * @param throwException
   *          boolean to determine if an exception has to be thrown when no pricelist is found.
   * @param usePriceIncludeTax
   *          boolean to set if price lists including taxes should be considered or not.
   * @return a valid ProductPrice for the given parameters. Null is no exception is to be thrown.
   * @throws OBException
   *           when no valid ProductPrice is found and throwException is true.
   */
  public static ProductPrice getProductPrice(Product product, Date date, boolean useSalesPriceList,
      PriceList priceList, boolean throwException, boolean usePriceIncludeTax) throws OBException {
    StringBuffer where = new StringBuffer();
    where.append(" as pp");
    where.append("   join pp." + ProductPrice.PROPERTY_PRICELISTVERSION + " as plv");
    where.append("   join plv." + PriceListVersion.PROPERTY_PRICELIST + " as pl");
    where.append(" where pp." + ProductPrice.PROPERTY_PRODUCT + " = :product");
    where.append("   and plv." + PriceListVersion.PROPERTY_VALIDFROMDATE + " <= :date");
    if (priceList != null) {
      where.append("   and pl = :pricelist");
    } else {
      where.append("   and pl." + PriceList.PROPERTY_SALESPRICELIST + " = :salespricelist");
    }
    if (!usePriceIncludeTax) {
      where.append("   and pl." + PriceList.PROPERTY_PRICEINCLUDESTAX + " = false");
    }
    where.append(" order by pl." + PriceList.PROPERTY_DEFAULT + " desc, plv."
        + PriceListVersion.PROPERTY_VALIDFROMDATE + " desc");

    OBQuery<ProductPrice> ppQry = OBDal.getInstance()
        .createQuery(ProductPrice.class, where.toString());
    ppQry.setNamedParameter("product", product);
    ppQry.setNamedParameter("date", date);
    if (priceList != null) {
      ppQry.setNamedParameter("pricelist", priceList);
    } else {
      ppQry.setNamedParameter("salespricelist", useSalesPriceList);
    }

    List<ProductPrice> ppList = ppQry.list();
    if (ppList.isEmpty()) {
      // No product price found.
      if (throwException) {
        throw new OBException("@PriceListVersionNotFound@. @Product@: " + product.getIdentifier()
            + " @Date@: " + OBDateUtils.formatDate(date));
      } else {
        return null;
      }
    }
    return ppList.get(0);
  }

  /**
   * Method to get the conversion rate defined at system level. If there is not a conversion rate
   * defined on the given Organization it is searched recursively on its parent organization until
   * one is found. If no conversion rate is found null is returned.
   * 
   * @param date
   *          Date conversion is being performed.
   * @param fromCurrency
   *          Currency to convert from.
   * @param toCurrency
   *          Currency to convert to.
   * @param org
   *          Organization of the document that needs to be converted.
   * @return a valid ConversionRate for the given parameters, null if none is found.
   */
  public static ConversionRate getConversionRate(Date date, Currency fromCurrency,
      Currency toCurrency, Organization org, Client client) {
    ConversionRate conversionRate;
    // Conversion rate records do not get into account timestamp.
    Date dateWithoutTimestamp = DateUtils.setHours(
        DateUtils.setMinutes(DateUtils.setSeconds(DateUtils.setMilliseconds(date, 0), 0), 0), 0);
    // Readable Client Org filters to false as organization is filtered explicitly.
    OBContext.setAdminMode(false);
    try {
      final OBCriteria<ConversionRate> obcConvRate = OBDal.getInstance()
          .createCriteria(ConversionRate.class);
      obcConvRate.add(Restrictions.eq(ConversionRate.PROPERTY_ORGANIZATION, org));
      obcConvRate.add(Restrictions.eq(ConversionRate.PROPERTY_CLIENT, client));
      obcConvRate.add(Restrictions.eq(ConversionRate.PROPERTY_CURRENCY, fromCurrency));
      obcConvRate.add(Restrictions.eq(ConversionRate.PROPERTY_TOCURRENCY, toCurrency));
      obcConvRate.add(Restrictions.le(ConversionRate.PROPERTY_VALIDFROMDATE, dateWithoutTimestamp));
      obcConvRate.add(Restrictions.ge(ConversionRate.PROPERTY_VALIDTODATE, dateWithoutTimestamp));
      obcConvRate.setFilterOnReadableClients(false);
      obcConvRate.setFilterOnReadableOrganization(false);
      conversionRate = (ConversionRate) obcConvRate.uniqueResult();
      if (conversionRate != null) {
        return conversionRate;
      }
      if ("0".equals(org.getId())) {
        return null;
      } else {
        return getConversionRate(date, fromCurrency, toCurrency,
            OBContext.getOBContext()
                .getOrganizationStructureProvider(client.getId())
                .getParentOrg(org),
            client);
      }
    } catch (Exception e) {
      log4j.error("Exception calculating conversion rate.", e);
      return null;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * It calls
   * {@link FinancialUtils#getConvertedAmount(BigDecimal, Currency, Currency, Date, Organization, String, List)}
   * with an empty list of conversion rates at document level
   */
  public static BigDecimal getConvertedAmount(BigDecimal amount, Currency curFrom, Currency curTo,
      Date date, Organization org, String strPrecision) throws OBException {
    return getConvertedAmount(amount, curFrom, curTo, date, org, strPrecision,
        Collections.<ConversionRateDoc> emptyList());
  }

  /**
   * Converts an amount.
   * 
   * @param amount
   *          BigDecimal amount to convert.
   * @param curFrom
   *          Currency to convert from.
   * @param curTo
   *          Currency to convert to.
   * @param date
   *          Date conversion is being performed.
   * @param org
   *          Organization of the document that needs to be converted.
   * @param strPrecision
   *          type of precision to be used to round the converted amount.
   * @param rateDocs
   *          list of conversion rates defined on the document of the amount that needs to be
   *          converted.
   * @return a BigDecimal representing the converted amount.
   * @throws OBException
   *           when no Conversion Rate is found for the given parameters.
   */
  public static BigDecimal getConvertedAmount(BigDecimal amount, Currency curFrom, Currency curTo,
      Date date, Organization org, String strPrecision, List<ConversionRateDoc> rateDocs)
      throws OBException {
    Check.isNotNull(rateDocs, OBMessageUtils.messageBD("ParameterMissing") + " (rateDocs)");
    if (curFrom.getId().equals(curTo.getId()) || amount.signum() == 0) {
      return amount;
    }
    BigDecimal rate = null;
    if (rateDocs.size() > 0) {
      for (ConversionRateDoc rateDoc : rateDocs) {
        if (curFrom.getId().equals(rateDoc.getCurrency().getId())
            && curTo.getId().equals(rateDoc.getToCurrency().getId())) {
          rate = rateDoc.getRate();
          break;
        }
      }
    }
    if (rate == null) {
      ConversionRate cr = getConversionRate(date, curFrom, curTo, org, org.getClient());
      if (cr == null) {
        throw new OBException("@NoCurrencyConversion@ " + curFrom.getISOCode() + " @to@ "
            + curTo.getISOCode() + " @ForDate@ " + OBDateUtils.formatDate(date)
            + " @And@ @ACCS_AD_ORG_ID_D@ " + org.getIdentifier());
      }
      rate = cr.getMultipleRateBy();
    }
    Long precision = curTo.getStandardPrecision();
    if (PRECISION_COSTING.equals(strPrecision)) {
      precision = curTo.getCostingPrecision();
    } else if (PRECISION_PRICE.equals(strPrecision)) {
      precision = curTo.getPricePrecision();
    }
    return amount.multiply(rate).setScale(precision.intValue(), RoundingMode.HALF_UP);
  }

  /**
   * Returns the Currency of a Legal Entity. If there is no one defined, returns the currency of the
   * Client.
   */
  public static Currency getLegalEntityCurrency(Organization organization) {
    Organization legalEntity = OBContext.getOBContext()
        .getOrganizationStructureProvider(organization.getClient().getId())
        .getLegalEntity(organization);
    Currency currency = (legalEntity.getCurrency() != null) ? legalEntity.getCurrency()
        : organization.getClient().getCurrency();
    return currency;
  }

  /**
   * Calculates the net unit price using the C_GET_NET_PRICE_FROM_GROSS stored procedure.
   * 
   * @param strTaxId
   *          Tax that applies to the price.
   * @param grossAmount
   *          Gross Amount to calculate the net unit price from.
   * @param pricePrecision
   *          Precision to round the result to.
   * @param alternateAmount
   *          alternate amount in case the tax uses it.
   * @param quantity
   *          number of units to divide the amount to get the price.
   * @return the net unit price
   * 
   * @deprecated Use {@link #calculateNetAmtFromGross} instead
   */
  @Deprecated
  public static BigDecimal calculateNetFromGross(String strTaxId, BigDecimal grossAmount,
      int pricePrecision, BigDecimal alternateAmount, BigDecimal quantity) {
    if (grossAmount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    final List<Object> parameters = new ArrayList<Object>();
    parameters.add(strTaxId);
    parameters.add(grossAmount);
    // TODO: Alternate Base Amount
    parameters.add(alternateAmount);
    parameters.add(pricePrecision);
    parameters.add(quantity);

    final String procedureName = "C_GET_NET_PRICE_FROM_GROSS";
    final BigDecimal lineNetAmount = (BigDecimal) CallStoredProcedure.getInstance()
        .call(procedureName, parameters, null);
    return lineNetAmount;
  }

  /**
   * Calculates the net unit price using the C_GET_NET_AMOUNT_FROM_GROSS stored procedure.
   * 
   * @param strTaxId
   *          Tax that applies to the price.
   * @param grossAmount
   *          Gross Amount to calculate the net unit price from.
   * @param stdPrecision
   *          Standard to round the result to.
   * @param alternateAmount
   *          alternate amount in case the tax uses it.
   * @return the net unit price
   */
  public static BigDecimal calculateNetAmtFromGross(String strTaxId, BigDecimal grossAmount,
      int stdPrecision, BigDecimal alternateAmount) {
    if (grossAmount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    final List<Object> parameters = new ArrayList<Object>();
    parameters.add(strTaxId);
    parameters.add(grossAmount);
    parameters.add(alternateAmount);
    parameters.add(stdPrecision);

    final String procedureName = "C_GET_NET_AMOUNT_FROM_GROSS";
    final BigDecimal lineNetAmount = (BigDecimal) CallStoredProcedure.getInstance()
        .call(procedureName, parameters, null);
    return lineNetAmount;
  }

  /**
   * Get all the payment details with available credit
   */
  public static ScrollableResults getPaymentsWithCredit(String businessPartnerId,
      String currencyId) {
    StringBuilder hql = new StringBuilder();
    hql.append(" SELECT t1." + FIN_Payment.PROPERTY_ID);
    hql.append(" FROM " + FIN_Payment.ENTITY_NAME + " as t1");
    hql.append(" WHERE t1." + FIN_Payment.PROPERTY_BUSINESSPARTNER + ".id = :businessPartnerId");
    hql.append(" AND t1." + FIN_Payment.PROPERTY_CURRENCY + ".id = :currencyId");
    hql.append(" AND t1." + FIN_Payment.PROPERTY_GENERATEDCREDIT + " <> 0");
    hql.append(" AND t1." + FIN_Payment.PROPERTY_GENERATEDCREDIT + " <> t1."
        + FIN_Payment.PROPERTY_USEDCREDIT);
    final Query<String> query = OBDal.getInstance()
        .getSession()
        .createQuery(hql.toString(), String.class);
    query.setParameter("businessPartnerId", businessPartnerId);
    query.setParameter("currencyId", currencyId);
    ScrollableResults scroll = query.scroll(ScrollMode.SCROLL_SENSITIVE);
    return scroll;
  }
}
