package org.wirabumi.gen.oez.utility;

import java.math.BigDecimal;

import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.financialmgmt.accounting.Costcenter;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.project.Project;

/**
 * Mandatory Property: Commission, Business Partner, Partner Address, Currency, Price List, Product,
 * Quantity, UOM, isSalesTransaction, Tax, Payment Term, Payment Method, Document, Type
 */
public class InvoiceData {
  // mandatory property
  private Object _commission = null;
  private Object _businessPartner = null;
  private Object _partnerAddress = null;
  private Object _currency = null;
  private Object _priceList = null;
  private Object _product = null;
  private Object _qty = null;
  private Object _uOM = null;
  private Object _tax = null;
  private boolean _isSalesTransaction;
  private Object _paymentTerm = null;
  private Object _paymentMethod = null;
  private Object _docType = null;
  private Object _description = null;
  // optional property
  private Object _taxAmount = null;
  private Object _listPrice = null;
  private Object _unitPrice = null;
  private Object _priceLimit = null;
  private Object _order = null;
  private Object _orderLine = null;
  private Object _shipment = null;
  private Object _shipmentLine = null;
  private Object _attributeSetValue = null;
  private Object _line_businessPartner = null;
  private Object _project = null;
  private Object _period = null;
  private Object _costcenter = null;

  public InvoiceData() {
    this._isSalesTransaction = false;
  }

  public Object getDescription() {
    return this._description;
  }

  public void setDescription(String description) {
    this._description = description;
  }

  public Object getDocumentType() {
    return this._docType;
  }

  public void setDocumentType(DocumentType docType) {
    this._docType = docType;
  }

  public boolean isSalesTransaction() {
    return this._isSalesTransaction;
  }

  public void setSalesTransaction(boolean value) {
    this._isSalesTransaction = value;
  }

  public Object getPaymentTerm() {
    return this._paymentTerm;
  }

  public void setPaymentTerm(PaymentTerm paymentTerm) {
    this._paymentTerm = paymentTerm;
  }

  public Object getPaymentMethod() {
    return this._paymentMethod;
  }

  public void setPaymentMethod(FIN_PaymentMethod paymentMethod) {
    this._paymentMethod = paymentMethod;
  }

  public Object getOrderLine() {
    return this._orderLine;
  }

  public void setOrderLine(OrderLine orderLine) {
    this._orderLine = orderLine;
  }

  public Object getShipmentLine() {
    return this._shipmentLine;
  }

  public void setShipmentLine(ShipmentInOutLine shipmentLine) {
    this._shipmentLine = shipmentLine;
  }

  public Object getCommission() {
    return this._commission;
  }

  public void setCommission(BigDecimal commission) {
    this._commission = commission;
  }

  public Object getQuantity() {
    return this._qty;
  }

  public void setQuantity(BigDecimal qty) {
    this._qty = qty;
  }

  public Object getShipment() {
    return this._shipment;
  }

  public void setShipment(ShipmentInOut shipment) {
    this._shipment = shipment;
  }

  public Object getPriceList() {
    return this._priceList;
  }

  public void setPriceList(PriceList priceList) {
    this._priceList = priceList;
  }

  public Object getBusinessPartner() {
    return this._businessPartner;
  }

  public void setBusinessPartner(BusinessPartner businessPartner) {
    this._businessPartner = businessPartner;
  }

  public Object getPartnerAddress() {
    return this._partnerAddress;
  }

  public void setPartnerAddress(Location partnerAddress) {
    this._partnerAddress = partnerAddress;
  }

  public Object getSalesOrder() {
    return this._order;
  }

  public void setSalesOrder(Order salesOrder) {
    this._order = salesOrder;
  }

  public Object getCurrency() {
    return this._currency;
  }

  public void setCurrency(Currency currency) {
    this._currency = currency;
  }

  public Object getProduct() {
    return this._product;
  }

  public void setProduct(Product product) {
    this._product = product;
  }

  public Object getListPrice() {
    return this._listPrice;
  }

  public void setListPrice(BigDecimal listPrice) {
    this._listPrice = listPrice;
  }

  public Object getUnitPrice() {
    return this._unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this._unitPrice = unitPrice;
  }

  public Object getPriceLimit() {
    return this._priceLimit;
  }

  public void setPriceLimit(BigDecimal priceLimit) {
    this._priceLimit = priceLimit;
  }

  public Object getUOM() {
    return this._uOM;
  }

  public void setUOM(UOM UOM) {
    this._uOM = UOM;
  }

  public Object getTax() {
    return this._tax;
  }

  public void setTax(TaxRate Tax) {
    this._tax = Tax;
  }

  public Object getTaxAmount() {
    return this._taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this._taxAmount = taxAmount;
  }

  public Object getAttributeSetValue() {
    return this._attributeSetValue;
  }

  public void setAttributeSetValue(AttributeSetInstance attributeSetValue) {
    this._attributeSetValue = attributeSetValue;
  }

  public Object getBusinessPartnerLine() {
    return this._line_businessPartner;
  }

  public void setBusinessPartnerLine(BusinessPartner businessPartner) {
    this._line_businessPartner = businessPartner;
  }

  public Object getProject() {
    return this._project;
  }

  public void setProject(Project project) {
    this._project = project;
  }

  public Object getPeriod() {
    return this._period;
  }

  public void setPeriod(Period period) {
    this._period = period;
  }

  public Object getCostCenter() {
    return this._costcenter;
  }

  public void setCostCenter(Costcenter costCenter) {
    this._costcenter = costCenter;
  }
}
