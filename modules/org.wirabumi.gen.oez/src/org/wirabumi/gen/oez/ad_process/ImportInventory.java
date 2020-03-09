package org.wirabumi.gen.oez.ad_process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;
import org.openbravo.model.materialmgmt.transaction.InventoryCountLine;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;
import org.wirabumi.gen.oez.importtrialbalance.oez_i_inventory;

public class ImportInventory extends DalBaseProcess {
  private OBError msg = new OBError();

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    int counter = 0;

    try {
      Organization org = OBDal.getInstance().get(Organization.class,
          bundle.getParams().get("adOrgId"));

      // select header by document no
      List<String> headList = new ArrayList<String>();
      String str = "select coalesce(i.name,'') from oez_i_inventory i "
          + "where i.isimported=false group by i.name";
      Query q = OBDal.getInstance().getSession().createQuery(str);
      ScrollableResults rs = q.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        if (!rs.getString(0).equals(""))
          headList.add(rs.getString(0));
      }
      rs.close();

      List<InventoryCount> invList = new ArrayList<InventoryCount>();
      List<InventoryCountLine> lineList = new ArrayList<InventoryCountLine>();
      for (String id : headList) {
        OBCriteria<oez_i_inventory> dataList = OBDal.getInstance().createCriteria(
            oez_i_inventory.class);
        dataList.add(Restrictions.eq(oez_i_inventory.PROPERTY_NAME, id));
        InventoryCount head = null;
        long lineNo = 0;
        for (oez_i_inventory inv : dataList.list()) {
          // cek if header exist
          head = cekHeader(invList, inv);
          if (head == null) {
            head = setHeader(inv, org);
            invList.add(head);
          }

          lineNo += 10;
          InventoryCountLine line = setLines(head, inv, lineNo);
          lineList.add(line);

          inv.setImported(true);
          inv.setStorageBin(line.getStorageBin());
          inv.setPhysInventory(head);
          OBDal.getInstance().save(inv);
          counter++;
        }
      }
      for (InventoryCount inv : invList) {
        OBDal.getInstance().save(inv);
        OBDal.getInstance().flush();
      }
      for (InventoryCountLine line : lineList) {
        OBDal.getInstance().save(line);
        OBDal.getInstance().flush();
      }
      OBDal.getInstance().commitAndClose();
      msg.setType("success");
      msg.setTitle("Import Inventory");
      msg.setMessage(counter + " data has been imported successfull.");
      bundle.setResult(msg);
    } catch (Exception e) {
      e.printStackTrace();
      msg.setType("error");
      msg.setTitle("Import Inventory");
      msg.setMessage(e.getMessage());
      bundle.setResult(msg);
      OBDal.getInstance().rollbackAndClose();
    }
  }

  private InventoryCount setHeader(oez_i_inventory inv, Organization org) {
    InventoryCount hsl = null;

    try {
      // get warehouse
      String storageBinID = inv.getStoragebinvalue();
      
      OBCriteria<Locator> locatorList = OBDal.getInstance().createCriteria(Locator.class);
      locatorList.add(Restrictions.eq(Locator.PROPERTY_SEARCHKEY, storageBinID));
      Locator locator = null;
      if (locatorList.list().size() > 0)
    	  locator = locatorList.list().get(0);
      else
    	  throw new OBException("oez_cannotfindstoragebin");
      Warehouse warehouse = locator.getWarehouse();

      // get movement date
      Date moveDate = inv.getMovementDate();

      // create header
      InventoryCount header = OBProvider.getInstance().get(InventoryCount.class);
      header.setOrganization(org);
      header.setMovementDate(moveDate);
      header.setWarehouse(warehouse);
      header.setName(inv.getName());
      if (inv.getDescription() != null) {
        header.setDescription(inv.getDescription());
      }

      OBDal.getInstance().save(header);
      hsl = header;
    } catch (Exception e) {
      e.printStackTrace();
    }

    return hsl;
  }

  private InventoryCountLine setLines(InventoryCount header, oez_i_inventory inv, long lineNo) {
    InventoryCountLine line = OBProvider.getInstance().get(InventoryCountLine.class);

    // get Product
    OBCriteria<Product> productList = OBDal.getInstance().createCriteria(Product.class);
    productList.add(Restrictions.eq(Product.PROPERTY_SEARCHKEY, inv.getProductSearchKey()));
    Product produk = null;
    if (productList.list().size() > 0)
    	produk = productList.list().get(0);
    else
    	throw new OBException("@oez_cannotfindproduct@");
    inv.setProduct(produk);

    // get storage
    OBCriteria<Locator> locList = OBDal.getInstance().createCriteria(Locator.class);
    locList.add(Restrictions.eq(Locator.PROPERTY_SEARCHKEY, inv.getStoragebinvalue()));
    Locator loc = null;
    if (locList.list().size() > 0) {
      loc = locList.list().get(0);
    }

    // get book quantity
    OBCriteria<StorageDetail> bookQty = OBDal.getInstance().createCriteria(StorageDetail.class);
    bookQty.add(Restrictions.eq(StorageDetail.PROPERTY_PRODUCT, produk));
    double qty = 0;
    for (StorageDetail book : bookQty.list()) {
      qty += book.getQuantityOnHand().doubleValue();
    }

    line.setOrganization(header.getOrganization());
    line.setPhysInventory(header);
    line.setLineNo(lineNo);
    line.setProduct(produk);
    if (produk.getAttributeSetValue() != null) {
      line.setAttributeSetValue(produk.getAttributeSetValue());
    }
    line.setStorageBin(loc);
    line.setUOM(produk.getUOM());
    line.setQuantityCount(inv.getMovementQuantity());
    line.setBookQuantity(new BigDecimal(qty));

    OBDal.getInstance().save(header);
    return line;
  }

  private InventoryCount cekHeader(List<InventoryCount> data, oez_i_inventory inv) {
    InventoryCount hsl = null;
    for (InventoryCount head : data) {
      if (head.getName().equals(inv.getName())) {
        hsl = head;
        break;
      }
    }
    return hsl;
  }
}
