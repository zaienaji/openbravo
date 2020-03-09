/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2012-2016 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.time.DateUtils;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBErrorBuilder;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderDiscount;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.order.OrderLineOffer;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.pricing.pricelist.PriceListVersion;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.CallStoredProcedure;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DbUtility;

public class ConvertQuotationIntoOrder extends DalBaseProcess {

  @Override
  public void doExecute(ProcessBundle bundle) throws Exception {

    HttpServletRequest request = RequestContext.get().getRequest();
    VariablesSecureApp vars = new VariablesSecureApp(request);
    boolean recalculatePrices = "N".equals(vars.getStringParameter("inprecalculateprices", false,
        "N"));

    try {
      // Create Sales Order
      String orderId = (String) bundle.getParams().get("C_Order_ID");
      Order objOrder = OBDal.getInstance().get(Order.class, orderId);
      Order objCloneOrder = (Order) DalUtil.copy(objOrder, false);

      if (FIN_Utility.isBlockedBusinessPartner(objOrder.getBusinessPartner().getId(), true, 1)) {
        // If the Business Partner is blocked, the Order should not be completed.
        OBError msg = new OBError();
        msg.setType("Error");
        msg.setMessage(OBMessageUtils.messageBD("ThebusinessPartner") + " "
            + objOrder.getBusinessPartner().getIdentifier() + " "
            + OBMessageUtils.messageBD("BusinessPartnerBlocked"));
        bundle.setResult(msg);
        OBDal.getInstance().rollbackAndClose();
        return;
      }

      // Set status of the new Order to Draft and Processed = N
      objCloneOrder.setDocumentAction("CO");
      objCloneOrder.setDocumentStatus("DR");
      objCloneOrder.setProcessed(false);
      objCloneOrder.setPosted("N");

      // Set the Sales Order Document Type
      DocumentType docType = objCloneOrder.getDocumentType().getDocumentTypeForOrder();
      if (docType == null) {
        OBDal.getInstance().rollbackAndClose();
        OBError result = OBErrorBuilder.buildMessage(null, "error", "@NoOrderDocType@");
        bundle.setResult(result);
        return;
      }

      // Set values of the Sales Order Header
      objCloneOrder.setDocumentType(docType);
      objCloneOrder.setTransactionDocument(docType);
      objCloneOrder.setProcessed(false);
      objCloneOrder.setSalesTransaction(true);
      objCloneOrder.setDocumentNo("<>");
      objCloneOrder.setOrderDate(DateUtils.truncate(new Date(), Calendar.DATE));
      objCloneOrder.setRejectReason(null);
      objCloneOrder.setValidUntil(null);
      objCloneOrder.setSummedLineAmount(BigDecimal.ZERO);
      objCloneOrder.setGrandTotalAmount(BigDecimal.ZERO);
      objCloneOrder.setQuotation(objOrder);
      OBDal.getInstance().save(objCloneOrder);
      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(objCloneOrder);

      Map<String, BigDecimal> taxForDiscounts = new HashMap<String, BigDecimal>();
      int lineNo = 10;
      StringBuilder strMessage = new StringBuilder();

      // Copy the Lines of the Quotation in the new Sales Order.
      for (OrderLine ordLine : objOrder.getOrderLineList()) {
        if (ordLine.getOrderDiscount() != null) {
          // If the line is a discount line do not copy it
          continue;
        }

        // Copy line to the new Sales Order
        OrderLine objCloneOrdLine = (OrderLine) DalUtil.copy(ordLine, false);

        String strCTaxID = objCloneOrdLine.getTax().getId();
        TaxRate lineTax = OBDal.getInstance().get(TaxRate.class, strCTaxID);

        if (lineTax == null) {
          if (strMessage.length() > 0) {
            strMessage = strMessage.append(", ");
          }
          strMessage = strMessage.append(lineNo);
        }

        // Update the HashMap of the Taxes. HashMap<TaxId, TotalAmount>
        BigDecimal price = BigDecimal.ZERO;
        try {
          OBContext.setAdminMode(true);
          if (objCloneOrder.getPriceList().isPriceIncludesTax()) {
            price = objCloneOrdLine.getLineGrossAmount();
          } else {
            price = objCloneOrdLine.getLineNetAmount();
          }
        } finally {
          OBContext.restorePreviousMode();
        }
        if (taxForDiscounts.containsKey(strCTaxID)) {
          taxForDiscounts.put(strCTaxID, taxForDiscounts.get(strCTaxID).add(price));
        } else {
          taxForDiscounts.put(strCTaxID, price);
        }

        if (recalculatePrices) {
          try {
            OBContext.setAdminMode(true);
            recalculatePrices(objOrder, ordLine, objCloneOrder, objCloneOrdLine, lineTax);
          } finally {
            OBContext.restorePreviousMode();
          }
        } else {
          for (OrderLineOffer offer : ordLine.getOrderLineOfferList()) {
            // Copy Promotions and Discounts.
            OrderLineOffer objCloneOffer = (OrderLineOffer) DalUtil.copy(offer, false);
            objCloneOffer.setSalesOrderLine(objCloneOrdLine);
            objCloneOrdLine.getOrderLineOfferList().add(objCloneOffer);
          }
        }
        // Set last values of new Sales Order line
        objCloneOrdLine.setSalesOrder(objCloneOrder);
        objCloneOrdLine.setReservedQuantity(BigDecimal.ZERO);
        objCloneOrdLine.setDeliveredQuantity(BigDecimal.ZERO);
        objCloneOrdLine.setInvoicedQuantity(BigDecimal.ZERO);
        objCloneOrdLine.setQuotationLine(ordLine);
        objCloneOrder.getOrderLineList().add(objCloneOrdLine);
        lineNo = lineNo + 10;
      }

      if (strMessage.length() > 0) {
        OBDal.getInstance().rollbackAndClose();
        String message = "@TaxCategoryWithoutTaxRate@".concat(strMessage.toString());
        OBError result = OBErrorBuilder.buildMessage(null, "error", message);
        bundle.setResult(result);
        return;
      }
      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(objCloneOrder);

      // Delete created discounts for Order
      for (OrderDiscount disCloneLine : objCloneOrder.getOrderDiscountList()) {
        OBDal.getInstance().remove(disCloneLine);
      }
      objCloneOrder.getOrderDiscountList().clear();

      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(objCloneOrder);

      BigDecimal cumulativeDiscount = new BigDecimal(100);

      // Create the discounts to be able to add the appropriate discount_id in c_orderline
      for (OrderDiscount disLine : objOrder.getOrderDiscountList()) {
        // Copy discounts
        OrderDiscount objCloneDiscount = (OrderDiscount) DalUtil.copy(disLine, false);
        objCloneDiscount.setSalesOrder(objCloneOrder);
        objCloneOrder.getOrderDiscountList().add(objCloneDiscount);
        if (!recalculatePrices) {
          // Copy the Invoice Lines that are created from the Discounts
          Iterator<Entry<String, BigDecimal>> it = taxForDiscounts.entrySet().iterator();
          OBDal.getInstance().flush();
          try {
            OBContext.setAdminMode(true);
            while (it.hasNext()) {
              Map.Entry<String, BigDecimal> e = it.next();
              BigDecimal discountAmount = BigDecimal.ZERO;

              if (objCloneDiscount.isCascade()) {
                discountAmount = objCloneDiscount.getDiscount().getDiscount();
                discountAmount = cumulativeDiscount.multiply(discountAmount).divide(
                    new BigDecimal(100));

              } else {
                discountAmount = objCloneDiscount.getDiscount().getDiscount();
              }
              cumulativeDiscount = cumulativeDiscount.subtract(discountAmount);

              OrderLine olDiscount = generateOrderLineDiscount(e, objCloneDiscount, objOrder,
                  objCloneOrder, lineNo, cumulativeDiscount, discountAmount);
              lineNo = lineNo + 10;
              objCloneOrder.getOrderLineList().add(olDiscount);
            }
          } finally {
            OBContext.restorePreviousMode();
          }
        }
      }

      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(objCloneOrder);

      // If prices are going to be recalculated, call C_Order_Post
      callCOrderPost(objCloneOrder, recalculatePrices);
      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(objCloneOrder);

      // Set the Status of the Quotation to Closed - Converted
      objOrder.setDocumentStatus("OEZ_CA");

      OBDal.getInstance().save(objOrder);
      OBDal.getInstance().save(objCloneOrder);

      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(objCloneOrder);
      OBDal.getInstance().refresh(objOrder);

      OBDal.getInstance().commitAndClose();
      OBError result = OBErrorBuilder.buildMessage(null, "success", "@SalesOrderDocumentno@ "
          + objCloneOrder.getDocumentNo() + " @beenCreated@");
      bundle.setResult(result);
    } catch (Exception e) {
      Throwable t = DbUtility.getUnderlyingSQLException(e);
      final OBError error = OBMessageUtils.translateError(bundle.getConnection(), vars,
          vars.getLanguage(), t.getMessage());
      bundle.setResult(error);
    }
  }

  /**
   * Given an Order Line and a Clone Order Line, it recalculates the prices of the second one
   */
  private void recalculatePrices(Order objOrder, OrderLine ordLine, Order objCloneOrder,
      OrderLine objCloneOrdLine, TaxRate lineTax) {

    String strPriceVersionId = getPriceListVersion(objOrder.getPriceList().getId(), objOrder
        .getClient().getId(), objCloneOrder.getOrderDate());
    BigDecimal bdPriceList = getPriceList(ordLine.getProduct().getId(), strPriceVersionId);
    BigDecimal bdPriceStd = getPriceStd(ordLine.getProduct().getId(), strPriceVersionId);

    if (!"".equals(bdPriceList) && bdPriceList != null
        && !bdPriceList.equals(BigDecimal.ZERO.setScale(bdPriceList.scale()))) {
      // List Price
      if (objOrder.getPriceList().isPriceIncludesTax()) {
        // If is Price Including Taxes, change gross and then Net
        objCloneOrdLine.setGrossListPrice(bdPriceList);
        objCloneOrdLine.setListPrice(getNetFromGross(bdPriceList, lineTax, objCloneOrder
            .getCurrency().getPricePrecision(), objCloneOrdLine.getOrderedQuantity()));
      } else {
        // If is not Price Including Taxes, change only net
        objCloneOrdLine.setListPrice(bdPriceList);
      }
    }

    if (!"".equals(bdPriceStd) && bdPriceStd != null
        && !bdPriceStd.equals(BigDecimal.ZERO.setScale(bdPriceStd.scale()))) {
      // Unit Price
      if (objOrder.getPriceList().isPriceIncludesTax()) {
        // If is Price Including Taxes, change gross and then Net
        objCloneOrdLine.setGrossUnitPrice(bdPriceStd);
        objCloneOrdLine.setUnitPrice(getNetFromGross(bdPriceStd, lineTax, objCloneOrder
            .getCurrency().getPricePrecision(), BigDecimal.ONE));
      } else {
        // If is not Price Including Taxes, change only net
        objCloneOrdLine.setUnitPrice(bdPriceStd);
      }
    }

    // Discount
    if (bdPriceList == null) {
      bdPriceList = BigDecimal.ZERO;
    }
    if (bdPriceStd == null) {
      bdPriceStd = BigDecimal.ZERO;
    }
    BigDecimal discount = BigDecimal.ZERO;
    if (bdPriceList.compareTo(BigDecimal.ZERO) != 0) {
      discount = bdPriceList
          .subtract(bdPriceStd)
          .multiply(new BigDecimal("100"))
          .divide(bdPriceList, objCloneOrder.getCurrency().getStandardPrecision().intValue(),
              BigDecimal.ROUND_HALF_EVEN);
    }
    objCloneOrdLine.setDiscount(discount);
    // Line Price
    if (objOrder.getPriceList().isPriceIncludesTax()) {
      // If is Price Including Taxes, change gross
      objCloneOrdLine.setLineGrossAmount(objCloneOrdLine.getGrossUnitPrice().multiply(
          objCloneOrdLine.getOrderedQuantity()));
    }
    objCloneOrdLine.setLineNetAmount(objCloneOrdLine.getUnitPrice().multiply(
        objCloneOrdLine.getOrderedQuantity()));
  }

  /**
   * Call C_Order_Post
   */
  private void callCOrderPost(Order objCloneOrder, boolean recalculatePrices) {
    try {
      final List<Object> parameters = new ArrayList<Object>();
      parameters.add(null);
      parameters.add(objCloneOrder.getId());
      parameters.add(recalculatePrices ? "Y" : "N");
      final String procedureName = "c_order_post1";
      CallStoredProcedure.getInstance().call(procedureName, parameters, null, true, false);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  /**
   * Get the Current version of a price list
   */
  private String getPriceListVersion(String priceList, String clientId, Date orderDate) {
    try {
      String whereClause = " as plv left outer join plv.priceList pl where pl.active='Y' and plv.active='Y' and "
          + " pl.id = :priceList and plv.client.id = :clientId and plv.validFromDate<= :orderDate  order by plv.validFromDate desc";

      OBQuery<PriceListVersion> ppriceListVersion = OBDal.getInstance().createQuery(
          PriceListVersion.class, whereClause);
      ppriceListVersion.setNamedParameter("priceList", priceList);
      ppriceListVersion.setNamedParameter("clientId", clientId);
      ppriceListVersion.setNamedParameter("orderDate", orderDate);
      ppriceListVersion.setMaxResult(1);

      if (!ppriceListVersion.list().isEmpty()) {
        return ppriceListVersion.list().get(0).getId();
      } else {
        return "0";
      }
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  /**
   * Call Database Procedure to get the List Price of a Product
   */
  private BigDecimal getPriceList(String strProductID, String strPriceVersionId) {
    BigDecimal bdPriceList = null;
    try {
      final List<Object> parameters = new ArrayList<Object>();
      parameters.add(strProductID);
      parameters.add(strPriceVersionId);
      final String procedureName = "M_BOM_PriceList";
      bdPriceList = (BigDecimal) CallStoredProcedure.getInstance().call(procedureName, parameters,
          null);
    } catch (Exception e) {
      throw new OBException(e);
    }

    return bdPriceList;
  }

  /**
   * Call Database Procedure to get the Standard Price of a Product
   */
  private BigDecimal getPriceStd(String strProductID, String strPriceVersionId) {
    BigDecimal bdPriceList = null;
    try {
      final List<Object> parameters = new ArrayList<Object>();
      parameters.add(strProductID);
      parameters.add(strPriceVersionId);
      final String procedureName = "M_BOM_PriceStd";
      bdPriceList = (BigDecimal) CallStoredProcedure.getInstance().call(procedureName, parameters,
          null);
    } catch (Exception e) {
      throw new OBException(e);
    }

    return bdPriceList;
  }

  /**
   * Create a new Invoice Line related to the Discount.
   */
  private OrderLine generateOrderLineDiscount(Entry<String, BigDecimal> e,
      OrderDiscount objCloneDiscount, Order objOrder, Order objCloneOrder, int lineNo,
      BigDecimal cumulativeDiscount, BigDecimal discountAmount) {

    BigDecimal amount = e.getValue();
    BigDecimal discountedAmount = amount.multiply(discountAmount).divide(new BigDecimal(100));

    OrderLine olDiscount = OBProvider.getInstance().get(OrderLine.class);
    olDiscount.setOrderDiscount(objCloneDiscount);
    olDiscount.setTax(OBDal.getInstance().get(TaxRate.class, e.getKey()));
    if (objOrder.getPriceList().isPriceIncludesTax()) {
      olDiscount.setGrossUnitPrice(discountedAmount.negate());
      olDiscount.setLineGrossAmount(discountedAmount.negate());
      olDiscount.setGrossListPrice(discountedAmount.negate());
      BigDecimal net = getNetFromGross(discountedAmount,
          OBDal.getInstance().get(TaxRate.class, e.getKey()), objCloneOrder.getCurrency()
              .getPricePrecision(), BigDecimal.ONE);
      olDiscount.setUnitPrice(net.negate());
      olDiscount.setLineNetAmount(net.negate());
      olDiscount.setListPrice(net.negate());
    } else {
      olDiscount.setUnitPrice(discountedAmount.negate());
      olDiscount.setLineNetAmount(discountedAmount.negate());
      olDiscount.setListPrice(discountedAmount.negate());
    }

    olDiscount.setSalesOrder(objCloneOrder);
    olDiscount.setReservedQuantity(BigDecimal.ZERO);
    olDiscount.setDeliveredQuantity(BigDecimal.ZERO);
    olDiscount.setInvoicedQuantity(BigDecimal.ZERO);
    olDiscount.setOrganization(objCloneDiscount.getOrganization());
    olDiscount.setLineNo((long) lineNo);
    olDiscount.setOrderDate(new Date());
    olDiscount.setWarehouse(objCloneOrder.getWarehouse());
    olDiscount.setUOM(objCloneDiscount.getDiscount().getProduct().getUOM());
    olDiscount.setCurrency(objCloneOrder.getCurrency());
    olDiscount.setProduct(objCloneDiscount.getDiscount().getProduct());
    olDiscount.setDescription(objCloneDiscount.getDiscount().getProduct().getName());
    return olDiscount;
  }

  /**
   * Call Database Procedure to calculate net price based on gross price
   */
  private BigDecimal getNetFromGross(BigDecimal amount, TaxRate tax, Long pricePrecision,
      BigDecimal quantity) {
    BigDecimal netPrice = null;
    try {
      final List<Object> parameters = new ArrayList<Object>();
      parameters.add(tax.getId());
      parameters.add(amount);
      parameters.add(amount);
      parameters.add(pricePrecision);
      parameters.add(quantity);
      final String procedureName = "c_get_net_price_from_gross";
      netPrice = (BigDecimal) CallStoredProcedure.getInstance().call(procedureName, parameters,
          null);
    } catch (Exception e) {
      throw new OBException(e);
    }
    return netPrice;
  }

}
