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

package org.openbravo.erpCommon.ad_actionButton;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.Query;
import org.openbravo.base.filter.IsIDFilter;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.costing.CostingUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.FieldProviderFactory;
import org.openbravo.erpCommon.utility.OBDateUtils;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.materialmgmt.InvoiceFromGoodsShipmentUtil;
import org.openbravo.materialmgmt.InvoiceGeneratorFromGoodsShipment;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.service.db.CallProcess;
import org.openbravo.xmlEngine.XmlDocument;

public class ProcessGoods extends HttpSecureAppServlet {
  private static final String GOODS_SHIPMENT_WINDOW = "169";
  private static final long serialVersionUID = 1L;
  private static final String M_Inout_Post_ID = "109";
  private static final String M_Inout_Table_ID = "319";
  private static final String Goods_Document_Action = "135";
  private static final String Goods_Receipt_Window = "184";

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    VariablesSecureApp vars = new VariablesSecureApp(request);

    if (vars.commandIn("DEFAULT")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessGoods|Window_ID",
          IsIDFilter.instance);
      final String strTabId = vars.getGlobalVariable("inpTabId", "ProcessGoods|Tab_ID",
          IsIDFilter.instance);

      final String strM_Inout_ID = vars.getGlobalVariable("inpmInoutId",
          strWindowId + "|M_Inout_ID", "", IsIDFilter.instance);

      final String strdocaction = vars.getStringParameter("inpdocaction");
      final String strProcessing = vars.getStringParameter("inpprocessing", "Y");
      final String strOrg = vars.getRequestGlobalVariable("inpadOrgId", "ProcessGoods|Org_ID",
          IsIDFilter.instance);
      final String strClient = vars.getStringParameter("inpadClientId", IsIDFilter.instance);

      final String strdocstatus = vars.getRequiredStringParameter("inpdocstatus");
      final int accesslevel = 1;

      if ((org.openbravo.erpCommon.utility.WindowAccessData.hasReadOnlyAccess(this, vars.getRole(),
          strTabId))
          || !(Utility.isElementInList(
              Utility.getContext(this, vars, "#User_Client", strWindowId, accesslevel), strClient)
              && Utility.isElementInList(
                  Utility.getContext(this, vars, "#User_Org", strWindowId, accesslevel), strOrg))) {
        OBError myError = Utility.translateError(this, vars, vars.getLanguage(),
            Utility.messageBD(this, "NoWriteAccess", vars.getLanguage()));
        vars.setMessage(strTabId, myError);
        printPageClosePopUp(response, vars);
      } else {
        printPageDocAction(response, vars, strM_Inout_ID, strdocaction, strProcessing, strdocstatus,
            M_Inout_Table_ID, strWindowId);
      }
    } else if (vars.commandIn("SAVE_BUTTONDocAction109")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessGoods|Window_ID",
          IsIDFilter.instance);
      final String strTabId = vars.getGlobalVariable("inpTabId", "ProcessGoods|Tab_ID",
          IsIDFilter.instance);
      final String receiptId = vars.getGlobalVariable("inpKey", strWindowId + "|M_Inout_ID", "");
      final String strdocaction = vars.getStringParameter("inpdocaction");
      final String strVoidMinoutDate = vars.getStringParameter("inpVoidedDocumentDate");
      final String strVoidMinoutAcctDate = vars.getStringParameter("inpVoidedDocumentAcctDate");

      if (StringUtils.equals(strWindowId, Goods_Receipt_Window)
          && StringUtils.equals(strdocaction, "RC")
          && !CostingUtils.isNegativeStockAllowedForShipmentInout(receiptId)) {
        List<String> receiptLineIdList = getReceiptLinesWithoutStock(receiptId);
        if (!receiptLineIdList.isEmpty()) {
          ShipmentInOut receipt = OBDal.getInstance().get(ShipmentInOut.class, receiptId);
          printPagePhysicalInventoryGrid(response, vars, strWindowId, strTabId,
              receipt.getOrganization().getId(), strdocaction, strVoidMinoutAcctDate,
              strVoidMinoutDate);
        } else {
          processReceipt(response, vars, strdocaction, strVoidMinoutDate, strVoidMinoutAcctDate);
        }
      } else {
        processReceipt(response, vars, strdocaction, strVoidMinoutDate, strVoidMinoutAcctDate);
      }
    } else if (vars.commandIn("LOAD_PHYSICALINVENTORY")) {
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessGoods|Window_ID",
          IsIDFilter.instance);
      final String receiptId = vars.getGlobalVariable("inpKey", strWindowId + "|M_Inout_ID", "");
      List<String> receiptLineIdList = getReceiptLinesWithoutStock(receiptId);
      printGrid(response, vars, receiptLineIdList);
    } else if (vars.commandIn("CANCEL_PHYSICALINVENTORY")) {
      final String strdocaction = vars.getStringParameter("inpdocaction");
      final String strVoidMinoutDate = vars.getStringParameter("inpVoidedDocumentDate");
      final String strVoidMinoutAcctDate = vars.getStringParameter("inpVoidedDocumentAcctDate");
      processReceipt(response, vars, strdocaction, strVoidMinoutDate, strVoidMinoutAcctDate);
    } else if (vars.commandIn("SAVE_PHYSICALINVENTORY")) {
      final String strdocaction = vars.getStringParameter("inpdocaction");
      final String strVoidMinoutDate = vars.getStringParameter("inpVoidedDocumentDate");
      final String strVoidMinoutAcctDate = vars.getStringParameter("inpVoidedDocumentAcctDate");
      final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessGoods|Window_ID",
          IsIDFilter.instance);
      final String receiptId = vars.getGlobalVariable("inpKey", strWindowId + "|M_Inout_ID", "");
      ShipmentInOut receipt = OBDal.getInstance().get(ShipmentInOut.class, receiptId);
      List<String> receiptLineIdList = getReceiptLinesWithoutStock(receiptId);
      createInventory(receipt, receiptLineIdList, vars.getLanguage());
      processReceipt(response, vars, strdocaction, strVoidMinoutDate, strVoidMinoutAcctDate);
    }
  }

  private void processReceipt(HttpServletResponse response, VariablesSecureApp vars,
      String strdocaction, String strVoidMinoutDate, String strVoidMinoutAcctDate)
      throws ServletException, IOException {
    final String strWindowId = vars.getGlobalVariable("inpwindowId", "ProcessGoods|Window_ID",
        IsIDFilter.instance);
    final String strTabId = vars.getGlobalVariable("inpTabId", "ProcessGoods|Tab_ID",
        IsIDFilter.instance);
    final String strM_Inout_ID = vars.getGlobalVariable("inpKey", strWindowId + "|M_Inout_ID", "");
    final boolean invoiceIfPossible = StringUtils
        .equals(vars.getStringParameter("inpInvoiceIfPossible"), "Y");

    OBError myMessage = null;
    try {
      ShipmentInOut goods = (ShipmentInOut) OBDal.getInstance()
          .getProxy(ShipmentInOut.ENTITY_NAME, strM_Inout_ID);
      goods.setDocumentAction(strdocaction);
      OBDal.getInstance().save(goods);
      OBDal.getInstance().flush();

      Process process = null;
      try {
        OBContext.setAdminMode(true);
        process = (Process) OBDal.getInstance().getProxy(Process.ENTITY_NAME, M_Inout_Post_ID);
      } finally {
        OBContext.restorePreviousMode();
      }

      Map<String, String> parameters = null;
      if (!strVoidMinoutDate.isEmpty() && !strVoidMinoutAcctDate.isEmpty()) {
        Date voidDate = null;
        Date voidAcctDate = null;
        try {
          voidDate = OBDateUtils.getDate(strVoidMinoutDate);
          voidAcctDate = OBDateUtils.getDate(strVoidMinoutAcctDate);
        } catch (ParseException pe) {
          voidDate = new Date();
          voidAcctDate = new Date();
          log4j.error("Not possible to parse the following date: " + strVoidMinoutDate, pe);
          log4j.error("Not possible to parse the following date: " + strVoidMinoutAcctDate, pe);
        }
        parameters = new HashMap<String, String>();
        parameters.put("voidedDocumentDate", OBDateUtils.formatDate(voidDate, "yyyy-MM-dd"));
        parameters.put("voidedDocumentAcctDate",
            OBDateUtils.formatDate(voidAcctDate, "yyyy-MM-dd"));
      }

      final ProcessInstance pinstance = CallProcess.getInstance()
          .call(process, strM_Inout_ID, parameters);
      OBDal.getInstance().getSession().refresh(goods);
      goods.setProcessGoodsJava(goods.getDocumentAction());
      OBDal.getInstance().save(goods);
      OBDal.getInstance().flush();
      OBDal.getInstance().commitAndClose();

      final PInstanceProcessData[] pinstanceData = PInstanceProcessData.select(this,
          pinstance.getId());
      myMessage = Utility.getProcessInstanceMessage(this, vars, pinstanceData);
      log4j.debug(myMessage.getMessage());
      vars.setMessage(strTabId, myMessage);

      if (invoiceIfPossible && GOODS_SHIPMENT_WINDOW.equals(strWindowId)
          && !"Error".equalsIgnoreCase(myMessage.getType())) {
        myMessage = createInvoice(vars, goods);
        log4j.debug(myMessage.getMessage());
        vars.setMessage(strTabId, myMessage);
      }

      String strWindowPath = Utility.getTabURL(strTabId, "R", true);
      if (strWindowPath.equals("")) {
        strWindowPath = strDefaultServlet;
      }
      printPageClosePopUp(response, vars, strWindowPath);

    } catch (ServletException | ParseException ex) {
      myMessage = Utility.translateError(this, vars, vars.getLanguage(), ex.getMessage());
      if (!myMessage.isConnectionAvailable()) {
        bdErrorConnection(response);
        return;
      } else {
        vars.setMessage(strTabId, myMessage);
      }
    }
  }

  private OBError createInvoice(final VariablesSecureApp vars, final ShipmentInOut goodShipment)
      throws ParseException {
    OBError myMessage;
    final boolean processInvoice = StringUtils.equals(vars.getStringParameter("inpProcessInvoice"),
        "Y");
    final String invoiceDateStr = vars.getStringParameter("inpInvoiceDate");
    final String priceListStr = vars.getStringParameter("inpPriceList");

    final Date invoiceDate = OBDateUtils.getDate(invoiceDateStr);
    final PriceList priceList = OBDal.getInstance().getProxy(PriceList.class, priceListStr);

    final Invoice invoice = new InvoiceGeneratorFromGoodsShipment(goodShipment.getId(), invoiceDate,
        priceList).createInvoiceConsideringInvoiceTerms(processInvoice);
    myMessage = getResultMessage(invoice);
    return myMessage;
  }

  private OBError getResultMessage(Invoice invoice) {
    OBError message = new OBError();
    message.setType("Success");
    message.setTitle("Success");
    if (invoice != null) {
      message.setMessage(String.format(OBMessageUtils.messageBD("NewInvoiceGenerated"),
          invoice.getDocumentNo(), InvoiceFromGoodsShipmentUtil.getInvoiceStatus(invoice)));
    } else {
      message.setMessage(OBMessageUtils.messageBD("NoInvoiceGenerated"));
    }
    return message;
  }

  void printPageDocAction(HttpServletResponse response, VariablesSecureApp vars,
      String strM_Inout_ID, String strdocaction, String strProcessing, String strdocstatus,
      String stradTableId, String strWindowId) throws IOException, ServletException {
    log4j.debug("Output: Button process 109");
    String[] discard = { "newDiscard" };
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_actionButton/DocAction", discard)
        .createXmlDocument();
    xmlDocument.setParameter("key", strM_Inout_ID);
    xmlDocument.setParameter("processing", strProcessing);
    xmlDocument.setParameter("form", "ProcessGoods.html");
    xmlDocument.setParameter("window", strWindowId);
    xmlDocument.setParameter("css", vars.getTheme());
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("dateDisplayFormat", vars.getSessionValue("#AD_SqlDateFormat"));
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("processId", M_Inout_Post_ID);
    xmlDocument.setParameter("cancel", Utility.messageBD(this, "Cancel", vars.getLanguage()));
    xmlDocument.setParameter("ok", Utility.messageBD(this, "OK", vars.getLanguage()));

    OBError myMessage = vars.getMessage(M_Inout_Post_ID);
    vars.removeMessage(M_Inout_Post_ID);
    if (myMessage != null) {
      xmlDocument.setParameter("messageType", myMessage.getType());
      xmlDocument.setParameter("messageTitle", myMessage.getTitle());
      xmlDocument.setParameter("messageMessage", myMessage.getMessage());
    }

    String processDescription = null;
    try {
      OBContext.setAdminMode(true);
      Process process = (Process) OBDal.getInstance()
          .getProxy(Process.ENTITY_NAME, M_Inout_Post_ID);
      processDescription = process.getDescription();
    } finally {
      OBContext.restorePreviousMode();
    }

    ShipmentInOut shipmentInOut = (ShipmentInOut) OBDal.getInstance()
        .getProxy(ShipmentInOut.ENTITY_NAME, strM_Inout_ID);
    xmlDocument.setParameter("docstatus", strdocstatus);
    if (strWindowId.equals(Goods_Receipt_Window)) {
      // VOID action: Reverse goods receipt/shipment by default inherits the document date and
      // accounting date from the voided document
      String movementDate = OBDateUtils.formatDate(shipmentInOut.getMovementDate());
      String accountingDate = OBDateUtils.formatDate(shipmentInOut.getAccountingDate());
      xmlDocument.setParameter("voidedDocumentDate", movementDate);
      xmlDocument.setParameter("voidedDocumentAcctDate", accountingDate);
      xmlDocument.setParameter("documentDate", movementDate);
      xmlDocument.setParameter("documentAcctDate", accountingDate);
    }
    xmlDocument.setParameter("adTableId", stradTableId);
    xmlDocument.setParameter("processId", M_Inout_Post_ID);
    xmlDocument.setParameter("processDescription", processDescription);
    xmlDocument.setParameter("docaction", (strdocaction.equals("--") ? "CL" : strdocaction));
    FieldProvider[] dataDocAction = ActionButtonUtility.docAction(this, vars, strdocaction,
        Goods_Document_Action, strdocstatus, strProcessing, stradTableId);
    xmlDocument.setData("reportdocaction", "liststructure", dataDocAction);
    StringBuffer dact = new StringBuffer();
    if (dataDocAction != null) {
      dact.append("var arrDocAction = new Array(\n");
      for (int i = 0; i < dataDocAction.length; i++) {
        dact.append("new Array(\"" + dataDocAction[i].getField("id") + "\", \""
            + dataDocAction[i].getField("name") + "\", \""
            + dataDocAction[i].getField("description") + "\")\n");
        if (i < dataDocAction.length - 1) {
          dact.append(",\n");
        }
      }
      dact.append(");");
    } else {
      dact.append("var arrDocAction = null");
    }
    xmlDocument.setParameter("array", dact.toString());

    if (strWindowId.equals(GOODS_SHIPMENT_WINDOW)) {
      xmlDocument.setParameter("invoiceDocumentDate",
          OBDateUtils.formatDate(shipmentInOut.getMovementDate()));
      final FieldProvider[] priceListsForSelector = getPriceListsForSelector(shipmentInOut);
      xmlDocument.setParameter("selectedPriceList",
          getSelectedPriceList(shipmentInOut, priceListsForSelector));
      xmlDocument.setData("priceList", "liststructure", priceListsForSelector);
    }

    out.println(xmlDocument.print());
    out.close();

  }

  void printPagePhysicalInventoryGrid(HttpServletResponse response, VariablesSecureApp vars,
      String strWindowId, String strTabId, String strOrg, String strdocaction,
      String strVoidMinoutAcctDate, String strVoidMinoutDate) throws IOException, ServletException {
    log4j.debug("Output: Credit Payment Grid popup");
    String[] discard = { "" };
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_actionButton/PhysicalInventory", discard)
        .createXmlDocument();
    xmlDocument.setParameter("css", vars.getTheme());
    xmlDocument.setParameter("language", "defaultLang=\"" + vars.getLanguage() + "\";");
    xmlDocument.setParameter("directory", "var baseDirectory = \"" + strReplaceWith + "/\";\n");
    xmlDocument.setParameter("cancel", Utility.messageBD(this, "Cancel", vars.getLanguage()));
    xmlDocument.setParameter("ok", Utility.messageBD(this, "OK", vars.getLanguage()));
    xmlDocument.setParameter("processId", M_Inout_Post_ID);
    xmlDocument.setParameter("docAction", strdocaction);
    xmlDocument.setParameter("minDate", strVoidMinoutDate);
    xmlDocument.setParameter("minAcctDate", strVoidMinoutAcctDate);
    out.println(xmlDocument.print());
    out.close();
  }

  private void printGrid(HttpServletResponse response, VariablesSecureApp vars,
      List<String> receiptLineIdList) throws IOException, ServletException {
    String[] discard = {};
    XmlDocument xmlDocument = xmlEngine
        .readXmlTemplate("org/openbravo/erpCommon/ad_actionButton/PhysicalInventoryGrid", discard)
        .createXmlDocument();
    xmlDocument.setData("structure", getFieldsForGrid(receiptLineIdList));
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    out.println(xmlDocument.print());
    out.close();
  }

  private FieldProvider[] getFieldsForGrid(List<String> receiptLineIdList) {
    FieldProvider[] data = FieldProviderFactory.getFieldProviderArray(receiptLineIdList);
    try {
      OBContext.setAdminMode(true);
      for (int i = 0; i < data.length; i++) {
        ShipmentInOutLine receiptLine = OBDal.getInstance()
            .get(ShipmentInOutLine.class, receiptLineIdList.get(i));
        FieldProviderFactory.setField(data[i], "receiptLineId", receiptLine.getId());
        FieldProviderFactory.setField(data[i], "lineNo", receiptLine.getLineNo().toString());
        FieldProviderFactory.setField(data[i], "product", receiptLine.getProduct().getName());
        FieldProviderFactory.setField(data[i], "attribute",
            receiptLine.getAttributeSetValue() != null
                ? receiptLine.getAttributeSetValue().getDescription()
                : "");
        FieldProviderFactory.setField(data[i], "quantity",
            receiptLine.getMovementQuantity().toString());
        FieldProviderFactory.setField(data[i], "uom", receiptLine.getUOM().getName());
        FieldProviderFactory.setField(data[i], "orderQuantity",
            receiptLine.getOrderQuantity() != null ? receiptLine.getOrderQuantity().toString()
                : "");
        FieldProviderFactory.setField(data[i], "orderUom",
            receiptLine.getOrderUOM() != null ? receiptLine.getOrderUOM().getUOM().getName() : "");
        FieldProviderFactory.setField(data[i], "locator",
            receiptLine.getStorageBin().getSearchKey());
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return data;
  }

  private List<String> getReceiptLinesWithoutStock(String receiptId) {
    StringBuffer where = new StringBuffer();
    where.append(" select iol." + ShipmentInOutLine.PROPERTY_ID);
    where.append(" from " + ShipmentInOutLine.ENTITY_NAME + " as iol");
    where.append(" where iol." + ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT + " = :receipt");
    where.append(" and iol." + ShipmentInOutLine.PROPERTY_ATTRIBUTESETVALUE + " is not null");
    where.append(" and not exists (");
    where.append("   select 1 ");
    where.append("   from " + StorageDetail.ENTITY_NAME + " as sd");
    where.append("   where sd." + StorageDetail.PROPERTY_PRODUCT + " = iol."
        + ShipmentInOutLine.PROPERTY_PRODUCT);
    where.append("   and sd." + StorageDetail.PROPERTY_STORAGEBIN + " = iol."
        + ShipmentInOutLine.PROPERTY_STORAGEBIN);
    where.append("   and sd." + StorageDetail.PROPERTY_ATTRIBUTESETVALUE + " = iol."
        + ShipmentInOutLine.PROPERTY_ATTRIBUTESETVALUE);
    where.append(
        "   and sd." + StorageDetail.PROPERTY_UOM + " = iol." + ShipmentInOutLine.PROPERTY_UOM);
    where.append("   and coalesce(sd." + StorageDetail.PROPERTY_ORDERUOM + ", '0') = coalesce(iol."
        + ShipmentInOutLine.PROPERTY_ORDERUOM + ", '0')");
    where.append(" )");

    Query<String> qry = OBDal.getInstance()
        .getSession()
        .createQuery(where.toString(), String.class);
    qry.setParameter("receipt", OBDal.getInstance().get(ShipmentInOut.class, receiptId));
    return qry.list();
  }

  private void createInventory(ShipmentInOut receipt, List<String> receiptLineIdList,
      String language) {

    // Create physical inventory
    InventoryCount inv = OBProvider.getInstance().get(InventoryCount.class);
    inv.setClient(receipt.getClient());
    inv.setOrganization(receipt.getOrganization());
    inv.setName(OBDateUtils.formatDate(new Date()));
    inv.setWarehouse(receipt.getWarehouse());
    inv.setMovementDate(new Date());
    inv.setInventoryType("N");
    inv.setDescription(Utility.messageBD(this, "AutoInventory", language));
    OBDal.getInstance().save(inv);

    // Add a line for each receipt line without related stock line
    int i = 0;
    for (String receiptLineId : receiptLineIdList) {
      ShipmentInOutLine receiptLine = OBDal.getInstance()
          .get(ShipmentInOutLine.class, receiptLineId);
      InventoryCountLine invLine = OBProvider.getInstance().get(InventoryCountLine.class);
      invLine.setClient(receiptLine.getClient());
      invLine.setOrganization(receiptLine.getOrganization());
      invLine.setPhysInventory(inv);
      invLine.setLineNo((i + 1) * 10L);
      invLine.setProduct(receiptLine.getProduct());
      invLine.setStorageBin(receiptLine.getStorageBin());
      invLine.setAttributeSetValue(receiptLine.getAttributeSetValue());
      invLine.setUOM(receiptLine.getUOM());
      invLine.setOrderUOM(receiptLine.getOrderUOM());
      invLine.setBookQuantity(BigDecimal.ZERO);
      invLine.setQuantityCount(receiptLine.getMovementQuantity());
      invLine.setQuantityOrderBook(BigDecimal.ZERO);
      invLine.setOrderQuantity(receiptLine.getOrderQuantity());
      OBDal.getInstance().save(invLine);
      i++;
    }

    // Process inventory
    OBDal.getInstance().flush();
    new InventoryCountProcess().processInventory(inv);
  }

  @Override
  public String getServletInfo() {
    return "Servlet to Process Goods Shipment and Goods Receipt";
  }

  private FieldProvider[] getPriceListsForSelector(final ShipmentInOut shipment) {
    final List<PriceList> priceLists = getSalesPriceList(shipment);
    return getFieldProviderFromPriceLists(priceLists);
  }

  private List<PriceList> getSalesPriceList(final ShipmentInOut shipment) {
    if (InvoiceFromGoodsShipmentUtil.shipmentLinesFromOrdersWithSamePriceList(shipment)) {
      return InvoiceFromGoodsShipmentUtil.getPriceListFromOrder(shipment);
    }
    return getAvailableSalesPriceList(shipment);
  }

  private List<PriceList> getAvailableSalesPriceList(final ShipmentInOut shipment) {
    String hql = "from PricingPriceList pl" //
        + " where pl.client.id = :clientId" //
        + " and Ad_Isorgincluded(:shipmentOrgId, pl.organization.id, :clientId) <> -1 "//
        + " and pl.salesPriceList = true";

    Query<PriceList> query = OBDal.getInstance().getSession().createQuery(hql, PriceList.class);
    query.setParameter("clientId", shipment.getClient().getId());
    query.setParameter("shipmentOrgId", shipment.getOrganization().getId());

    return query.list();
  }

  private FieldProvider[] getFieldProviderFromPriceLists(final List<PriceList> priceLists) {
    FieldProvider[] data = FieldProviderFactory.getFieldProviderArray(priceLists);
    for (int i = 0; i < data.length; i++) {
      PriceList priceList = priceLists.get(i);
      FieldProviderFactory.setField(data[i], "ID", priceList.getId());
      FieldProviderFactory.setField(data[i], "NAME", priceList.getName());
      FieldProviderFactory.setField(data[i], "DESCRIPTION", priceList.getName());
    }
    return data;
  }

  private String getSelectedPriceList(final ShipmentInOut shipment,
      FieldProvider[] priceListDataForSelector) {
    if (priceListDataForSelector.length == 1) {
      return priceListDataForSelector[0].getField("ID");
    }
    return InvoiceFromGoodsShipmentUtil.getPriceListFromBusinessPartner(shipment);
  }

}
