package org.wirabumi.gen.oez.utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceLine;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.service.db.DalConnectionProvider;

public class CreateInvoiceUtility {
  public static void createPurchaseInvoice(List<BaseOBObject> param, VariablesSecureApp vars) {
    createInvoice(param, vars, new DalConnectionProvider(), false);
  }

  public static void createPurchaseInvoice(List<BaseOBObject> param, VariablesSecureApp vars,
      ConnectionProvider conn) {
    createInvoice(param, vars, conn, false);
  }

  public static void createSalesInvoice(List<BaseOBObject> param, VariablesSecureApp vars) {
    createInvoice(param, vars, new DalConnectionProvider(), true);
  }

  public static void createSalesInvoice(List<BaseOBObject> param, VariablesSecureApp vars,
      ConnectionProvider conn) {
    createInvoice(param, vars, conn, true);
  }

  private static void createInvoice(List<BaseOBObject> param, VariablesSecureApp vars,
      ConnectionProvider conn, boolean isSOTrx) {
    List<Invoice> invHeadList = new ArrayList<Invoice>();
    List<InvoiceLine> invLineList = new ArrayList<InvoiceLine>();

    try {
      Client klien = OBDal.getInstance().get(Client.class, vars.getClient());
      Organization org = OBDal.getInstance().get(Organization.class, vars.getOrg());
      User user = OBDal.getInstance().get(User.class, vars.getUser());
      Role role = OBDal.getInstance().get(Role.class, vars.getRole());

      Invoice invHead;
      InvoiceLine invLine;
      long lineNo = 0;
      for (BaseOBObject data : param) {
        InvoiceLine line = (InvoiceLine) data;
        BusinessPartner bp = (line.getBusinessPartner() == null) ? null : line.getBusinessPartner();
        if (bp == null) {
          continue;
        }

        if (invHeadList.size() <= 0) {
          lineNo += 10;
          line.setLineNo(lineNo);
          invHead = createInvoiceHeader(line, klien, org, user, role, conn, isSOTrx);
          invLine = createInvoiceLine(line, invHead);
          invHeadList.add(invHead);
          invLineList.add(invLine);
        } else {
          boolean containt = false;
          for (Invoice inv : invHeadList) {
            if (inv.getBusinessPartner().getId().equals(bp.getId())) {
              lineNo += 10;
              line.setLineNo(lineNo);
              invLine = createInvoiceLine(line, inv);
              invLineList.add(invLine);
              containt = true;
            } else {
              lineNo = 0;
              containt = false;
            }
          }
          if (!containt) {
            lineNo += 10;
            line.setLineNo(lineNo);
            invHead = createInvoiceHeader(line, klien, org, user, role, conn, isSOTrx);
            invLine = createInvoiceLine(line, invHead);
            invHeadList.add(invHead);
            invLineList.add(invLine);
          }
        }
      }

      // flush header
      for (Invoice inv : invHeadList) {
        OBDal.getInstance().save(inv);
        OBDal.getInstance().flush();
      }
      // flush line
      for (InvoiceLine line : invLineList) {
        OBDal.getInstance().save(line);
        OBDal.getInstance().flush();
      }

      OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
    }
  }

  private static Invoice createInvoiceHeader(InvoiceLine line, Client klien, Organization org,
      User user, Role role, ConnectionProvider conn, boolean isSOTrx) {
    Invoice inv = OBProvider.getInstance().get(Invoice.class);

    try {
      // get data from parameter
      BusinessPartner bp = line.getBusinessPartner();
      OBCriteria<Location> locList = OBDal.getInstance().createCriteria(Location.class);
      locList.add(Restrictions.eq(Location.PROPERTY_BUSINESSPARTNER, bp));
      Location bpAddress = locList.list().get(0);

      // set data to object header
      String docNo = Utility.getDocumentNo(conn, klien.getId(), "C_Invoice", true);
      Date tgl = new Date();
      Calendar cal = Calendar.getInstance();
      cal.setTime(tgl);
      cal.set(Calendar.MILLISECOND, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      tgl = cal.getTime();
      String docTypeID = Preferences.getPreferenceValue("OEZ_INVDOCTYPE", true, klien, org, user,
          role, null);
      DocumentType docType = OBDal.getInstance().get(DocumentType.class, docTypeID);
      FIN_PaymentMethod paymentMethod = bp.getPaymentMethod();
      PaymentTerm payterm = bp.getPaymentTerms();
      PriceList pricelist = bp.getPriceList();
      Currency cur = pricelist.getCurrency();

      inv.setClient(klien);
      inv.setOrganization(org);
      inv.setCreatedBy(user);
      inv.setUpdatedBy(user);
      inv.setSalesTransaction(isSOTrx);
      inv.setDocumentNo(docNo);
      inv.setDocumentStatus("DR");
      inv.setDocumentAction("CO");
      inv.setDocumentType(docType);
      inv.setTransactionDocument(docType);
      inv.setPrint(false);
      inv.setInvoiceDate(tgl);
      inv.setAccountingDate(tgl);
      inv.setBusinessPartner(bp);
      inv.setPartnerAddress(bpAddress);
      inv.setCurrency(cur);
      inv.setFormOfPayment("P");
      inv.setPaymentTerms(payterm);
      inv.setChargeAmount(new BigDecimal(0));
      inv.setSummedLineAmount(new BigDecimal(0));
      inv.setGrandTotalAmount(new BigDecimal(0));
      inv.setPriceList(pricelist);
      inv.setPriceIncludesTax(false);
      inv.setCreateLinesFrom(false);
      inv.setGenerateTo(false);
      inv.setCopyFrom(false);
      inv.setSelfService(false);
      inv.setPaymentComplete(false);
      inv.setTotalPaid(new BigDecimal(0));
      inv.setOutstandingAmount(new BigDecimal(0));
      inv.setDaysTillDue(Long.parseLong("0"));
      inv.setDueAmount(new BigDecimal(0));
      inv.setUpdatePaymentMonitor(false);
      inv.setPaymentMethod(paymentMethod);
      inv.setCalculatePromotions(false);
      inv.setCashVAT(false);
      inv.setAPRMProcessinvoice("CO");
      inv.setOezCreateSalesorder(false);

      // save and commit to DB
      OBDal.getInstance().save(inv);
      // OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
    }

    return inv;
  }

  private static InvoiceLine createInvoiceLine(InvoiceLine invLine, Invoice invHead) {
    try {
      if (invHead.getDescription() == null || invHead.getDescription().equals("")) {
        invHead.setDescription(invLine.getDescription());
      } else {
        String txt = invHead.getDescription();
        invHead.setDescription(txt + ",\n" + invLine.getDescription());
      }

      double total = invHead.getGrandTotalAmount().doubleValue(), lineAmount = invLine
          .getLineNetAmount().doubleValue();
      invLine.setInvoice(invHead);
      invHead.setGrandTotalAmount(new BigDecimal(total + lineAmount));

      OBDal.getInstance().save(invLine);
      OBDal.getInstance().save(invHead);
    } catch (Exception e) {
      e.printStackTrace();
      OBDal.getInstance().rollbackAndClose();
    }
    return invLine;
  }

}
