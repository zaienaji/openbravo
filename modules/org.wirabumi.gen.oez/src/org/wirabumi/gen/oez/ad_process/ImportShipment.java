package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.gen.oez.OEZ_I_Inout;

public class ImportShipment extends DalBaseProcess {
  private OBError msg = new OBError();

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    int counter = 0;

    try {
      Organization org = OBDal.getInstance().get(Organization.class,
          bundle.getParams().get("adOrgId"));

      // select header by document no
      List<String> dataList = new ArrayList<String>();
      String str = "select coalesce(i.documentno,'') from OEZ_I_Inout i "
          + "where i.isimported=false group by i.documentno";
      Query q = OBDal.getInstance().getSession().createQuery(str);
      ScrollableResults rs = q.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        if (!rs.getString(0).equals(""))
          dataList.add(rs.getString(0));
      }
      rs.close();

      List<ShipmentInOut> headList = new ArrayList<ShipmentInOut>();
      List<ShipmentInOutLine> lineList = new ArrayList<ShipmentInOutLine>();
      for (String id : dataList) {
        OBCriteria<OEZ_I_Inout> inoutList = OBDal.getInstance().createCriteria(OEZ_I_Inout.class);
        inoutList.add(Restrictions.eq(OEZ_I_Inout.PROPERTY_DOCUMENTNO, id));
        ShipmentInOut head = null;
        long lineNo = 0;
        for (OEZ_I_Inout io : inoutList.list()) {
          // cek if header exist
          head = cekHeader(headList, io);
          if (head == null) {
            head = setHeader(io, org);
            headList.add(head);
          }

          lineNo += 10;
          ShipmentInOutLine line = setLines(head, io, lineNo);
          lineList.add(line);

          io.setImported(true);
          counter++;
        }
      }

      for (ShipmentInOut head : headList) {
        OBDal.getInstance().save(head);
        OBDal.getInstance().flush();
      }
      for (ShipmentInOutLine line : lineList) {
        OBDal.getInstance().save(line);
        OBDal.getInstance().flush();
      }

      OBDal.getInstance().commitAndClose();
      msg.setType("success");
      msg.setTitle("Import Shipment");
      msg.setMessage(counter + " data has been imported successfull.");
      bundle.setResult(msg);
    } catch (Exception e) {
      e.printStackTrace();
      msg.setType("error");
      msg.setTitle("Import Shipment");
      msg.setMessage(e.getMessage());
      bundle.setResult(msg);
      OBDal.getInstance().rollbackAndClose();
    }
  }

  private ShipmentInOut setHeader(OEZ_I_Inout io, Organization org) {
    ShipmentInOut head = OBProvider.getInstance().get(ShipmentInOut.class);

    try {
      // get business partner
      OBCriteria<BusinessPartner> bpList = OBDal.getInstance()
          .createCriteria(BusinessPartner.class);
      bpList.add(Restrictions.eq(BusinessPartner.PROPERTY_SEARCHKEY, io.getBpartnervalue()));
      BusinessPartner bp = null;
      if (bpList.list().size() > 0) {
        bp = bpList.list().get(0);
      }

      // get BP location
      List<Object> param = new ArrayList<Object>();
      String str = "as bpl inner join bpl.locationAddress loc inner join loc.region r inner join r.country c ";
      str += "where bpl.businessPartner=? ";
      param.add(bp);
      if (io.getCountryname() != null) {
        str += "and c.iSOCountryCode=? ";
        param.add(io.getCountryname());
      }
      if (io.getRegionname() != null) {
        str += "and r.name=?";
        param.add(io.getRegionname());
      }
      OBQuery<Location> bpLoc = OBDal.getInstance().createQuery(Location.class, str, param);
      Location loc = null;
      if (bpLoc.list().size() > 0) {
        loc = bpLoc.list().get(0);
      }

      // get document type
      OBCriteria<DocumentType> docTypeList = OBDal.getInstance().createCriteria(DocumentType.class);
      docTypeList.add(Restrictions.eq(DocumentType.PROPERTY_NAME, io.getDoctypename()));
      DocumentType docTypeTrx = null;
      if (docTypeList.list().size() > 0) {
        docTypeTrx = docTypeList.list().get(0);
      }

      // get warehouse
      OBCriteria<Warehouse> whList = OBDal.getInstance().createCriteria(Warehouse.class);
      whList.add(Restrictions.eq(Warehouse.PROPERTY_SEARCHKEY, io.getWarehousevalue()));
      Warehouse wh = null;
      if (whList.list().size() > 0) {
        wh = whList.list().get(0);
      }

      Date accDate = io.getMovementdate();

      head.setDocumentNo(io.getDocumentno());
      head.setOrganization(org);
      head.setDocumentType(docTypeTrx);
      head.setDocumentStatus("DR");
      head.setDocumentAction("CO");
      head.setWarehouse(wh);
      head.setBusinessPartner(bp);
      head.setPartnerAddress(loc);
      head.setMovementDate(accDate);
      head.setMovementType(io.getMovementtype());
      head.setLogistic(io.isLogistic());
      head.setAccountingDate(accDate);
      if (io.getDescription() != null) {
        head.setDescription(io.getDescription());
      }
      OBDal.getInstance().save(head);
    } catch (Exception e) {
      e.printStackTrace();
    }

    return head;
  }

  private ShipmentInOutLine setLines(ShipmentInOut header, OEZ_I_Inout io, long lineNo) {
    ShipmentInOutLine line = OBProvider.getInstance().get(ShipmentInOutLine.class);

    // get product
    OBCriteria<Product> produkList = OBDal.getInstance().createCriteria(Product.class);
    produkList.add(Restrictions.eq(Product.PROPERTY_SEARCHKEY, io.getProductvalue()));
    Product produk = null;
    if (produkList.list().size() > 0) {
      produk = produkList.list().get(0);
    }

    // get storage bin
    OBCriteria<Locator> locList = OBDal.getInstance().createCriteria(Locator.class);
    locList.add(Restrictions.eq(Locator.PROPERTY_SEARCHKEY, io.getStoragebinvalue()));
    Locator loc = null;
    if (locList.list().size() > 0) {
      loc = locList.list().get(0);
    }

    line.setOrganization(header.getOrganization());
    line.setLineNo(lineNo);
    line.setShipmentReceipt(header);
    line.setProduct(produk);
    line.setUOM(produk.getUOM());
    line.setMovementQuantity(new BigDecimal(io.getMovementqty()));
    line.setStorageBin(loc);
    if (produk.getAttributeSetValue() != null) {
      line.setAttributeSetValue(produk.getAttributeSetValue());
    }
    OBDal.getInstance().save(line);

    return line;
  }

  private ShipmentInOut cekHeader(List<ShipmentInOut> data, OEZ_I_Inout io) {
    ShipmentInOut hsl = null;
    for (ShipmentInOut head : data) {
      if (head.getDocumentNo().equals(io.getDocumentno())) {
        hsl = head;
        break;
      }
    }
    return hsl;
  }
}
