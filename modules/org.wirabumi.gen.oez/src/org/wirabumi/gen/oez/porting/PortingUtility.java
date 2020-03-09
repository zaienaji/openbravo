package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
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
import org.openbravo.dal.service.OBQuery;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.ClientInformation;
import org.openbravo.model.ad.utility.Tree;
import org.openbravo.model.ad.utility.TreeNode;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.financialmgmt.assetmgmt.Asset;
import org.openbravo.model.financialmgmt.assetmgmt.AssetGroup;
import org.openbravo.model.materialmgmt.onhandquantity.Reservation;
import org.openbravo.model.materialmgmt.onhandquantity.ReservationStock;
import org.openbravo.model.materialmgmt.onhandquantity.StorageDetail;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.service.db.DalConnectionProvider;

public class PortingUtility {

  public BigDecimal ad_isorgincluded(Organization orgid, Organization orgparent, Client client) {
    BigDecimal hasil = new BigDecimal(1);
    BigDecimal level = new BigDecimal(0);
    try {
      OBCriteria<ClientInformation> clientInfo = OBDal.getInstance().createCriteria(
          ClientInformation.class);
      Tree treeOrg = null;
      for (ClientInformation ci : clientInfo.list()) {
        treeOrg = ci.getPrimaryTreeOrganization();
      }

      String v_parent = orgid.getId();
      String v_node = "";
      while (v_parent != null) {
        OBCriteria<TreeNode> treeNode = OBDal.getInstance().createCriteria(TreeNode.class);
        treeNode.add(Restrictions.eq(TreeNode.PROPERTY_TREE, treeOrg));
        treeNode.add(Restrictions.eq(TreeNode.PROPERTY_NODE, v_parent));

        for (TreeNode tN : treeNode.list()) {
          v_parent = tN.getReportSet();
          v_node = tN.getNode();
        }
        if (v_node.equals(orgparent.getId())) {
          return level;
        }
        level = level.add(new BigDecimal(1));
      }

    } catch (Exception e) {
      // TODO: handle exception
      e.printStackTrace();
      throw new OBException(e.getMessage());
    }
    return hasil.negate();
  }

  public boolean ad_org_isinnaturaltree(Organization orgid, Organization orgparent, Client client) {
    BigDecimal param = new BigDecimal(1);

    BigDecimal orgInclude = ad_isorgincluded(orgid, orgparent, client);
    BigDecimal orgIncludeParent = ad_isorgincluded(orgparent, orgid, client);
    if ((orgInclude.longValue() != param.negate().longValue())
        || (orgIncludeParent.longValue() != param.negate().longValue())) {
      return true;
    } else {
      return false;
    }
  }

  public int ad_org_chk_documents(String HeaderTable, String LinesTable, String DocumentId,
      String HeaderColumnName, String LinesColumnName) {

    String OrganizationHeaderId;
    String lineOrganization = "";
    int isInclude = 0;
    OrganizationHeaderId = ad_get_doc_le_bu(HeaderTable, DocumentId, HeaderColumnName, "");

    String hql = "SELECT DISTINCT(a.organization) FROM " + LinesTable + " a " + "WHERE a."
        + LinesColumnName + "." + HeaderColumnName + " = '" + DocumentId + "' "
        + "AND a.organization='" + OrganizationHeaderId + "'";
    Query query = OBDal.getInstance().getSession().createQuery(hql);

    ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
    while (rs.next()) {
      Organization organization = (Organization) rs.get()[0];
      boolean isLegalentity = organization.getOrganizationType().isLegalEntity();
      boolean isBusinessUnit = organization.getOrganizationType().isBusinessUnit();
      lineOrganization = organization.getId();

      if (isLegalentity == false && isBusinessUnit == false) {
        String sql = "SELECT hh.parent_id, ad_orgtype.isbusinessunit, ad_orgtype.islegalentity "
            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
            + "AND hh.ad_tree_id = pp.ad_tree_id " + "WHERE hh.node_id='" + lineOrganization
            + "' AND ad_org.isready='Y' " + "AND EXISTS (SELECT 1 FROM ad_tree "
            + "WHERE ad_tree.treetype='OO' " + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

        ConnectionProvider conn = new DalConnectionProvider();
        try {
          Statement st = conn.getStatement();
          ResultSet rs2 = st.executeQuery(sql);

          while (rs2.next()) {
            lineOrganization = rs2.getString("parent_id");
            String bussinesunit = rs2.getString("isbusinessunit");
            String legalenty = rs2.getString("islegalentity");

            if (legalenty.endsWith("Y")) {
              isLegalentity = true;
            } else {
              isLegalentity = false;
            }

            if (bussinesunit.endsWith("Y")) {
              isBusinessUnit = true;
            } else {
              isBusinessUnit = false;
            }
          }

        } catch (Exception e) {
          e.printStackTrace();
          throw new OBException(e.getMessage());
        }
      }
    }

    if (!lineOrganization.endsWith(OrganizationHeaderId)) {
      isInclude = -1;
    }
    return isInclude;
  }

  public String ad_get_doc_le_bu(String headerTable, String documentId, String headerColumnName,
      String type) {

    String organizationHeader = "";
    boolean isBussinesUnit;
    boolean isLegalEntity;

    String hql = "SELECT a.organization.id, a.organization.organizationType.businessUnit, a.organization.organizationType.legalEntity "
        + "FROM " + headerTable + " a  WHERE a." + headerColumnName + " = '" + documentId + "'";
    Query query = OBDal.getInstance().getSession().createQuery(hql);

    ScrollableResults rs = query.scroll(ScrollMode.FORWARD_ONLY);
    while (rs.next()) {
      isBussinesUnit = (Boolean) rs.get()[1];
      isLegalEntity = (Boolean) rs.get()[2];
      if ((isBussinesUnit == true && (type.endsWith("BU") || type.endsWith("")))
          || (isLegalEntity == true && (type.endsWith("LE") || type.endsWith("")))) {
        organizationHeader = (String) rs.get()[0];
      } else {
        organizationHeader = (String) rs.get()[0];
        organizationHeader = ad_get_org_le_bu(organizationHeader, "");
      }
    }
    return organizationHeader;
  }

  public String ad_get_org_le_bu(String organizationHeader, String type) {
    String orgHeaderId;
    boolean isLegalenty = false;
    boolean isBusinessUnit = false;
    orgHeaderId = organizationHeader;
    ConnectionProvider conn = new DalConnectionProvider();
    Statement st;

    if (type.endsWith("")) {
      if (isLegalenty == false && isBusinessUnit == false) {
        String sqlCekLebu = "SELECT hh.parent_id, ad_orgtype.isbusinessunit, ad_orgtype.islegalentity "
            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
            + "AND hh.ad_tree_id = pp.ad_tree_id "
            + "WHERE hh.node_id='"
            + orgHeaderId
            + "' AND ad_org.isready='Y' "
            + "AND EXISTS (SELECT 1 FROM ad_tree "
            + "WHERE ad_tree.treetype='OO' "
            + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

        try {
          st = conn.getStatement();
          ResultSet rsCekLebu = st.executeQuery(sqlCekLebu);
          while (rsCekLebu.next()) {
            orgHeaderId = rsCekLebu.getString("parent_id");
            String bussinesunit = rsCekLebu.getString("isbusinessunit");
            String legalenty = rsCekLebu.getString("islegalentity");

            if (legalenty.endsWith("Y")) {
              isLegalenty = true;
            } else {
              isLegalenty = false;
            }

            if (bussinesunit.endsWith("Y")) {
              isBusinessUnit = true;
            } else {
              isBusinessUnit = false;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          throw new OBException(e.getMessage());
        }
      }
    } else if (type.endsWith("LE")) {
      if (isLegalenty == false) {
        String sqlCekLe = "SELECT hh.parent_id, ad_orgtype.islegalentity "
            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
            + "AND hh.ad_tree_id = pp.ad_tree_id " + "WHERE hh.node_id='" + orgHeaderId
            + "' AND ad_org.isready='Y' " + "AND EXISTS (SELECT 1 FROM ad_tree "
            + "WHERE ad_tree.treetype='OO' " + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

        try {
          st = conn.getStatement();
          ResultSet rsCekLe = st.executeQuery(sqlCekLe);
          while (rsCekLe.next()) {
            orgHeaderId = rsCekLe.getString("parent_id");
            String legalenty = rsCekLe.getString("islegalentity");

            if (legalenty.endsWith("Y")) {
              isLegalenty = true;
            } else {
              isLegalenty = false;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();

          throw new OBException(e.getMessage());
        }
      }
    } else if (type.endsWith("BU")) {
      if (isBusinessUnit == false) {
        String sqlCekBu = "SELECT hh.parent_id, ad_orgtype.isbusinessunit "
            + "FROM ad_org JOIN ad_orgtype ON ad_org.ad_orgtype_id=ad_orgtype.ad_orgtype_id "
            + "JOIN ad_treenode pp ON pp.node_id=ad_org.ad_org_id "
            + "JOIN ad_treenode hh ON pp.node_id = hh.parent_id "
            + "AND hh.ad_tree_id = pp.ad_tree_id " + "WHERE hh.node_id='" + orgHeaderId
            + "' AND ad_org.isready='Y' " + "AND EXISTS (SELECT 1 FROM ad_tree "
            + "WHERE ad_tree.treetype='OO' " + "AND hh.ad_tree_id=ad_tree.ad_tree_id "
            + "AND hh.ad_client_id=ad_tree.ad_client_id)";

        try {
          st = conn.getStatement();
          ResultSet rsCekBu = st.executeQuery(sqlCekBu);
          while (rsCekBu.next()) {
            orgHeaderId = rsCekBu.getString("parent_id");
            String businessunit = rsCekBu.getString("isbusinessunit");

            if (businessunit.endsWith("Y")) {
              isBusinessUnit = true;
            } else {
              isBusinessUnit = false;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          throw new OBException(e.getMessage());
        }
      }
    }
    return orgHeaderId;
  }

  public int c_chk_open_period(String adOrgId, Date ProductionDate, String docType, String docTypeId) {
    ConnectionProvider conn = new DalConnectionProvider();
    int availablePeriod = 0;
    String organizationPeriodeAllow = ad_org_getperiodcontrolallow(adOrgId);

    if (docTypeId != "") {

      String sql = "SELECT COUNT(p.c_period_id) AS periodeActive FROM c_period p WHERE '"
          + ProductionDate + "' >= p.startdate AND '" + ProductionDate + "' <=p.enddate+1 "
          + "AND EXISTS (SELECT 1 FROM c_periodcontrol pc WHERE pc.c_period_id = p.c_period_id "
          + "AND pc.docbasetype=(SELECT docbasetype FROM c_doctype WHERE c_doctype_id='"
          + docTypeId + "') " + "AND pc.ad_org_id= '" + organizationPeriodeAllow
          + "' AND pc.periodstatus='O')";

      try {
        Statement st = conn.getStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
          availablePeriod = rs.getInt("periodeActive");
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new OBException(e.getMessage());
      }
    } else if (docType != "") {
      String sql = "SELECT COUNT(p.c_period_id) AS periodeActive FROM c_period p WHERE '"
          + ProductionDate + "' >= p.startdate AND '" + ProductionDate + "' <=p.enddate+1 "
          + "AND EXISTS (SELECT 1 FROM c_periodcontrol pc WHERE pc.c_period_id = p.c_period_id "
          + "AND pc.docbasetype='" + docType + "' " + "AND pc.ad_org_id= '"
          + organizationPeriodeAllow + "' AND pc.periodstatus='O')";

      try {
        Statement st = conn.getStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
          availablePeriod = rs.getInt("periodeActive");
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new OBException(e.getMessage());
      }
    } else {
      availablePeriod = 0;
    }

    return availablePeriod;
  }

  public String ad_org_getperiodcontrolallow(String OrganizationId) {

    String parentId = "";
    String nodeId = "";
    Organization organization;
    boolean isPeriodConttrolAllowed = false;

    organization = OBDal.getInstance().get(Organization.class, OrganizationId);
    isPeriodConttrolAllowed = organization.isAllowPeriodControl();

    if (isPeriodConttrolAllowed == true) {
      parentId = organization.getId();
    } else {
      if (!parentId.endsWith("") && !nodeId.endsWith("")) {

        String sql = "SELECT paren_id FROM ad_treenode t Where t.node_id='"
            + nodeId
            + "'"
            + " AND EXIST (SELECT 1 FROM ad_tree t2, ad_org o WHERE t2.ad_client_id=o.ad_client_id"
            + " AND t2.ad_client_id=t.ad_client_id AND t2.treetype='00' AND t.ad_tree_id=t2.ad_tree_id)";

        ConnectionProvider conn = new DalConnectionProvider();
        try {
          Statement st = conn.getStatement();
          ResultSet rs2 = st.executeQuery(sql);
          while (rs2.next()) {
            parentId = rs2.getString("paren_id");
            organization = OBDal.getInstance().get(Organization.class, parentId);
            isPeriodConttrolAllowed = organization.isAllowPeriodControl();
            nodeId = parentId;
          }
        } catch (Exception e) {
          throw new OBException(e.getMessage());
        }
      }
    }
    return parentId;
  }

  public String[] m_reservation_consumption(Reservation reservation, Locator locator,
      AttributeSetInstance attributeSetInstance, BigDecimal qty, User user) {

    // Karena left join maka tidak di perlukan
    // OBCriteria<ReservationStock> reservationStock =
    // OBDal.getInstance().createCriteria(ReservationStock.class);
    // reservationStock.add(Restrictions.eq(ReservationStock.PROPERTY_RESERVATION, reservation));
    // reservationStock.add(Restrictions.eq(ReservationStock.PROPERTY_ALLOCATED, false));

    Product product;
    UOM uom;
    BigDecimal pendingQty = new BigDecimal(0);
    BigDecimal quantity = new BigDecimal(0);
    BigDecimal releaseQty = new BigDecimal(0);
    BigDecimal pendingtoRelease = qty;
    Warehouse dimWarehouse;
    Locator dimLocator;
    AttributeSetInstance dimAsi;
    String resStatus;
    String productValue;
    String attrDescription = "-";
    String uomValue;
    String locatorValue = "-";
    Warehouse warehouse = null;

    String result = "";
    String message = "";
    // if( reservationStock.list().size() != 0){
    product = reservation.getProduct();
    uom = reservation.getUOM();
    if (!reservation.getQuantity().equals(null)) {
      quantity = reservation.getQuantity();
    }
    if (!reservation.getReservedQty().equals(null)) {
      quantity = reservation.getReleased();
    }
    pendingQty = quantity.subtract(releaseQty);
    dimWarehouse = reservation.getWarehouse();
    dimLocator = reservation.getStorageBin();
    dimAsi = reservation.getAttributeSetValue();
    resStatus = reservation.getRESStatus();

    productValue = product.getSearchKey();
    uomValue = uom.getSymbol();

    if (!attributeSetInstance.equals(null)) {
      attrDescription = attributeSetInstance.getDescription();
    }

    if (!locator.equals(null)) {
      locatorValue = locator.getSearchKey();
      warehouse = locator.getWarehouse();
    }

    if (resStatus.endsWith("HO")) {
      throw new OBException("It is not possible to modify a On Hold reservation of Product "
          + productValue + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    if (!dimWarehouse.equals(warehouse) || !dimLocator.equals(locator)
        || !dimAsi.equals(attributeSetInstance)) {
      throw new OBException("Wrong stock dimension mismatch of Product " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    if (pendingtoRelease.intValue() > pendingQty.intValue()) {
      pendingtoRelease = pendingQty;
      result = "2";
      message = "There is more quantity to release than pending quantity " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue;
    }

    if (attributeSetInstance.equals(null)) {
      attributeSetInstance = OBDal.getInstance().get(AttributeSetInstance.class, "0");
    }
    String reservedClause = "AS rs WHERE rs.storageBin=? AND rs.reservation=? AND "
        + "COALESCE(rs.attributeSetValue.id, '0' )=? ";
    List<Object> reservedParams = new ArrayList<Object>();
    reservedParams.add(locator);
    reservedParams.add(reservation);
    reservedParams.add(attributeSetInstance.getId());

    OBQuery<ReservationStock> reserved = OBDal.getInstance().createQuery(ReservationStock.class,
        reservedClause, reservedParams);
    int sdReserved = reserved.list().size();
    if (pendingtoRelease.intValue() > sdReserved) {
      String[] ar = m_reservation_reallocate(reservation, locator, attributeSetInstance, qty, user);
      result = ar[0];
      message = ar[1];
    }

    for (ReservationStock reservedStock : reserved.list()) {
      BigDecimal reservedQty = new BigDecimal(0);
      if (reservedStock.getReleased().equals(null)) {
        reservedQty = reservedStock.getReleased();
      }
      BigDecimal qtytorelease = quantity.subtract(reservedQty);
      if (qtytorelease.doubleValue() > pendingtoRelease.doubleValue()) {
        qtytorelease = pendingtoRelease;
      }
      Date date = new Date();
      reservedStock.setReleased(releaseQty.add(qtytorelease));
      reservedStock.setUpdated(date);
      reservedStock.setUpdatedBy(user);
      OBDal.getInstance().save(reservedStock);

      pendingtoRelease = pendingtoRelease.subtract(qtytorelease);
    }
    if (pendingtoRelease.intValue() > 0) {
      throw new OBException("Cannot consume all the stock " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }
    // }
    OBDal.getInstance().commitAndClose();
    return new String[] { result, message };
  }

  public String[] m_reservation_reallocate(Reservation reservation, Locator locator,
      AttributeSetInstance attributeSetInstance, BigDecimal qty, User user) {
    String result = "";
    String message = "";

    OBCriteria<ReservationStock> reservationStock = OBDal.getInstance().createCriteria(
        ReservationStock.class);
    reservationStock.add(Restrictions.eq(ReservationStock.PROPERTY_RESERVATION, reservation));
    reservationStock.add(Restrictions.eq(ReservationStock.PROPERTY_ALLOCATED, true));

    ReservationStock resStock = null;
    Product product = reservation.getProduct();
    UOM uom = reservation.getUOM();
    Warehouse dimWarehouse = reservation.getWarehouse();
    Locator dimLocator = reservation.getStorageBin();
    AttributeSetInstance dimAsi = reservation.getAttributeSetValue();
    String resStatus = reservation.getRESStatus();
    String productValue = product.getSearchKey();
    String uomValue = uom.getSymbol();
    String attrDescription = "-";
    String locatorValue = "-";
    BigDecimal reserveQty = new BigDecimal(0);
    BigDecimal qtyTounReserve = new BigDecimal(0);

    if (!reservation.getReservedQty().equals(null)) {
      reserveQty = reservation.getReservedQty();
    }
    BigDecimal releaseQty = new BigDecimal(0);
    if (!reservation.getReleased().equals(null)) {
      releaseQty = reservation.getReleased();
    }

    BigDecimal quantity = reservation.getQuantity();
    BigDecimal notReserve = quantity.subtract(reserveQty);
    BigDecimal allocated = new BigDecimal(0);
    BigDecimal qtyOnHand = new BigDecimal(0);
    StorageDetail storageDetail = null;
    Warehouse Warehouse = null;
    if (reservationStock.list().size() != 0) {
      BigDecimal alloctQty = reservationStock.list().get(0).getQuantity();
      BigDecimal alloctReleased = new BigDecimal(0);
      if (reservationStock.list().get(0).getReleased().equals(null)) {
        alloctReleased = reservationStock.list().get(0).getQuantity();
      }
      allocated = alloctQty.subtract(alloctReleased);
    }

    if (!attributeSetInstance.equals(null)) {
      attrDescription = attributeSetInstance.getDescription();
    }

    if (!locator.equals(null)) {
      locatorValue = locator.getSearchKey();
    }

    if (resStatus.endsWith("HO")) {
      throw new OBException("It is not possible to modify a On Hold reservation " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    OBCriteria<StorageDetail> storageDetaillist = OBDal.getInstance().createCriteria(
        StorageDetail.class);
    storageDetaillist.add(Restrictions.eq(StorageDetail.PROPERTY_PRODUCT, product));
    storageDetaillist.add(Restrictions.eq(StorageDetail.PROPERTY_UOM, uom));
    storageDetaillist.add(Restrictions.eq(StorageDetail.PROPERTY_ATTRIBUTESETVALUE,
        attributeSetInstance));
    storageDetaillist.add(Restrictions.eq(StorageDetail.PROPERTY_STORAGEBIN, locator));

    if (storageDetaillist.list().size() != 0) {
      qtyOnHand = storageDetaillist.list().get(0).getQuantityOnHand();
      storageDetail = storageDetaillist.list().get(0);
      Warehouse = storageDetaillist.list().get(0).getStorageBin().getWarehouse();
    }

    String rsClause = "AS rs WHERE rs.attributeSetValue=? AND rs.storageBin=? AND rs.reservation.uOM=? AND "
        + "rs.reservation.rESStatus NOT IN ('CL', 'DR') AND rs.reservation.product=?";
    List<Object> rsParams = new ArrayList<Object>();
    rsParams.add(attributeSetInstance);
    rsParams.add(locator);
    rsParams.add(uom);
    rsParams.add(product);

    OBQuery<ReservationStock> rs = OBDal.getInstance().createQuery(ReservationStock.class,
        rsClause, rsParams);

    BigDecimal noAlloc = reserveQty.subtract(allocated);
    BigDecimal stockRes = new BigDecimal(0);
    BigDecimal stockAlloc = new BigDecimal(0);
    BigDecimal sdAlloc = new BigDecimal(0);
    BigDecimal sdNoAlloc = new BigDecimal(0);
    for (ReservationStock rsList : rs.list()) {
      stockRes = stockRes.add(rsList.getQuantity().subtract(rsList.getReleased()));
      if (rsList.isAllocated() == true) {
        stockAlloc = stockRes;
      }
      if (rsList.getReservation().equals(reservation) && rsList.isAllocated() == true) {
        sdAlloc = stockRes;
      }
      if (rsList.getReservation().equals(reservation) && rsList.isAllocated() == false) {
        sdNoAlloc = stockRes;
      }
    }

    if (!dimWarehouse.equals(Warehouse) || !dimLocator.equals(locator)
        || !dimAsi.equals(attributeSetInstance)) {
      throw new OBException("Wrong stock dimension mismatch " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    BigDecimal pendingToRealLocate = qty;
    if (pendingToRealLocate.intValue() > (reserveQty.add(notReserve)).subtract(releaseQty)
        .intValue()) {
      pendingToRealLocate = (reserveQty.add(notReserve)).subtract(releaseQty);
      result = "2";
      message = "There is more quantity to release than pending quantity " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue;
    }

    if (pendingToRealLocate.intValue() > qtyOnHand.subtract((stockAlloc.subtract(sdAlloc)))
        .intValue()) {
      throw new OBException("Cannot reallocate all the required quantity " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    if (pendingToRealLocate.intValue() > sdAlloc.add(noAlloc).add(notReserve).intValue()) {
      throw new OBException("Cannot modify allocated reservation " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    pendingToRealLocate = pendingToRealLocate.subtract(sdAlloc).subtract(sdNoAlloc);
    if (pendingToRealLocate.intValue() <= 0) {
      return new String[] { result, message };
    }
    BigDecimal qtyAvailableToReserve = qtyOnHand.add(stockRes);

    if (qtyAvailableToReserve.intValue() > pendingToRealLocate.intValue()) {
      qtyAvailableToReserve = pendingToRealLocate;
    }

    if (qtyAvailableToReserve.intValue() > 0) {
      if (notReserve.intValue() >= qtyAvailableToReserve.intValue()) {
        notReserve = notReserve.subtract(qtyAvailableToReserve);
      } else {
        qtyTounReserve = qtyAvailableToReserve.subtract(notReserve);
        notReserve = new BigDecimal(0);

        String rs2Clause = "AS rs WHERE rs.reservation=? rs.allocated = false AND rs.quantity != ? AND "
            + "((rs.attributeSetValue != ? OR rs.attributeSetValue IS NULL) OR rs.storageBin != ? )";
        List<Object> rs2Params = new ArrayList<Object>();
        rs2Params.add(reservation);
        rs2Params.add(releaseQty);
        rs2Params.add(attributeSetInstance);
        rs2Params.add(locator);

        OBQuery<ReservationStock> rs2 = OBDal.getInstance().createQuery(ReservationStock.class,
            rs2Clause, rs2Params);
        BigDecimal reservedQty = new BigDecimal(0);
        for (ReservationStock rs2List : rs2.list()) {
          BigDecimal release = new BigDecimal(0);
          if (!rs2List.getReleased().equals(null)) {
            release = rs2List.getReleased();
          }
          reservedQty = rs2List.getQuantity().subtract(release);

          BigDecimal qtytounReserve_aux = qtyTounReserve;
          if (qtytounReserve_aux.intValue() > reservedQty.intValue()) {
            qtytounReserve_aux = reservedQty;
          }

          rs2List.setQuantity(quantity.subtract(qtytounReserve_aux));
          OBDal.getInstance().save(rs2List);

          qtyTounReserve = qtyTounReserve.subtract(qtytounReserve_aux);

        }
      }

      resStock = m_reserve_stock_manual(reservation, "SD", storageDetail, qtyAvailableToReserve,
          user, false);
      pendingToRealLocate = pendingToRealLocate.subtract(qtyAvailableToReserve);
    }

    if (pendingToRealLocate.intValue() >= 0) {
      OBCriteria<ReservationStock> realLocation = OBDal.getInstance().createCriteria(
          ReservationStock.class);
      realLocation.add(Restrictions.eq(ReservationStock.PROPERTY_RESERVATION, reservation));
      realLocation.add(Restrictions.eq(ReservationStock.PROPERTY_QUANTITY, new BigDecimal(0)));

      for (ReservationStock listRs : realLocation.list()) {
        OBDal.getInstance().remove(listRs);
      }
      OBDal.getInstance().commitAndClose();
      return new String[] { result, message };
    }

    // Finally take other reservation's not allocated stock.
    // Reserve Other ReservationStock

    if (attributeSetInstance.equals(null)) {
      attributeSetInstance = OBDal.getInstance().get(AttributeSetInstance.class, "0");
    }
    String otherRsClause = "AS rs WHERE rs.reservation != ? AND rs.reservation.product=? "
        + "AND rs.reservation.uOM=? AND rs.storageBin=? AND COALESCE(rs.attributeSetValue.id, '0')=COALESCE(?,'0') "
        + "AND  rs.reservation.rESStatus NOT IN('HO', 'CL', 'DR') AND rs.quantity != ?";
    List<Object> otherRsParams = new ArrayList<Object>();
    otherRsParams.add(reservation);
    otherRsParams.add(product);
    otherRsParams.add(uom);
    otherRsParams.add(locator);
    otherRsParams.add(attributeSetInstance.getId());
    otherRsParams.add(releaseQty);
    OBQuery<ReservationStock> otherRs = OBDal.getInstance().createQuery(ReservationStock.class,
        otherRsClause, otherRsParams);

    // SELECT rs FROM MaterialMgmtReservationStock
    for (ReservationStock allocatedSock : otherRs.list()) {
      BigDecimal qtyToRealease = allocatedSock.getQuantity().subtract(allocatedSock.getReleased());

      if (qtyToRealease.doubleValue() > pendingToRealLocate.doubleValue()) {
        qtyToRealease = pendingToRealLocate;
      }

      if (notReserve.doubleValue() >= qtyToRealease.doubleValue()) {
        notReserve = notReserve.subtract(qtyToRealease);
      } else {
        qtyTounReserve = qtyToRealease.subtract(notReserve);
        notReserve = new BigDecimal(0);

        String reservedStockClause = "AS rs WHERE rs.reservation=?  AND rs.allocated=? AND "
            + "((rs.attributeSetValue != ? OR rs.attributeSetValue=null) OR rs.storageBin != ? ) AND rs.quantity != ?";

        // SELECT rs FROM MaterialMgmtReservationStock
        List<Object> reservedStockParams = new ArrayList<Object>();
        reservedStockParams.add(reservation);
        reservedStockParams.add(false);
        reservedStockParams.add(attributeSetInstance);
        reservedStockParams.add(locator);
        reservedStockParams.add(releaseQty);

        OBQuery<ReservationStock> reservedStock = OBDal.getInstance().createQuery(
            ReservationStock.class, reservedStockClause, reservedStockParams);

        for (ReservationStock reservedStockList : reservedStock.list()) {
          BigDecimal qtyTounReserveAux = reservedStockList.getQuantity().subtract(releaseQty);
          if (qtyTounReserveAux.doubleValue() > qtyTounReserve.doubleValue()) {
            qtyTounReserveAux = qtyTounReserve;
          }
          reservedStockList.setQuantity(quantity.subtract(qtyTounReserveAux));

          OBDal.getInstance().save(reservedStockList);
        }
      }

      allocatedSock.setQuantity(quantity.subtract(qtyToRealease));
      resStock = m_reserve_stock_manual(reservation, "SD", storageDetail, qtyToRealease, user,
          false);

      pendingToRealLocate = pendingToRealLocate.subtract(qtyToRealease);
      if (pendingToRealLocate.intValue() <= 0) {
        OBCriteria<ReservationStock> realLocation = OBDal.getInstance().createCriteria(
            ReservationStock.class);
        realLocation.add(Restrictions.eq(ReservationStock.PROPERTY_RESERVATION, reservation));
        realLocation.add(Restrictions.eq(ReservationStock.PROPERTY_QUANTITY, new BigDecimal(0)));

        for (ReservationStock listRs : realLocation.list()) {
          OBDal.getInstance().remove(listRs);
        }

        OBCriteria<ReservationStock> realLocation2 = OBDal.getInstance().createCriteria(
            ReservationStock.class);
        realLocation2.add(Restrictions.eq(ReservationStock.PROPERTY_ID, allocatedSock.getId()));
        realLocation2.add(Restrictions.eq(ReservationStock.PROPERTY_QUANTITY, new BigDecimal(0)));

        for (ReservationStock listRs2 : realLocation2.list()) {
          OBDal.getInstance().remove(listRs2);
        }

        resStock.setQuantity(quantity.subtract(qtyToRealease));
        OBDal.getInstance().save(resStock);
        OBDal.getInstance().commitAndClose();
        return new String[] { result, message };
      }
    }

    if (pendingToRealLocate.intValue() > 0) {
      throw new OBException("Cannot reallocate all the required quantity " + productValue
          + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
          + " and Storage Bin " + locatorValue);
    }

    OBDal.getInstance().commitAndClose();
    return new String[] { result, message };
  }

  public ReservationStock m_reserve_stock_manual(Reservation reservation, String type,
      StorageDetail storagedetail, BigDecimal qty, User user, boolean allocated) {

    Product reserveProduct = reservation.getProduct();
    UOM reserveUom = reservation.getUOM();
    Locator reserveLocator = reservation.getStorageBin();
    AttributeSetInstance reserveAsi = reservation.getAttributeSetValue();
    ReservationStock auxRs = null;
    String attrDescription = "-";
    String uomValue = "-";
    String locatorValue = "-";
    String poLineId = "";
    Locator locator = storagedetail.getStorageBin();
    AttributeSetInstance asi = storagedetail.getAttributeSetValue();
    if (asi.equals(null)) {
      asi = OBDal.getInstance().get(AttributeSetInstance.class, "0");
    }

    if (type.endsWith("SD")) {

      String sdClause = "AS sd WHERE sd=? AND sd.product=? AND sd.attributeSetValue = COALESCE(?, sd.attributeSetValue) AND "
          + "sd.storageBin = COALESCE(?, sd.storageBin) AND COALESCE(?, sd.storageBin.warehouse)";
      List<Object> sdParams = new ArrayList<Object>();
      sdParams.add(storagedetail);
      sdParams.add(reserveProduct);
      sdParams.add(reserveAsi);
      sdParams.add(reserveLocator);
      sdParams.add(reserveLocator.getWarehouse());
      OBQuery<ReservationStock> sd = OBDal.getInstance().createQuery(ReservationStock.class,
          sdClause, sdParams);

      if (sd.list().size() == 0) {
        Product productValue = reserveProduct;

        if (!reserveAsi.equals(null)) {
          attrDescription = reserveAsi.getDescription();
        }

        uomValue = reserveUom.getSymbol();

        if (!reserveLocator.equals(null)) {
          locatorValue = reserveLocator.getSearchKey();
        }
        throw new OBException("Given storage detail does not match reservation requirements "
            + productValue + ", Attribute Set Instance " + attrDescription + ", UOM " + uomValue
            + " and Storage Bin " + locatorValue);

      }

      String hqlRs = "SELECT MAX(rs) FROM MaterialMgmtReservationStock rs WHERE rs.reservation='"
          + reservation + "' AND rs.storageBin='" + locator + "'"
          + " AND COALESCE(rs.attributeSetValue.id, '0') = '" + asi.getId() + "' AND rs.allocated="
          + allocated;
      Query queryRs = OBDal.getInstance().getSession().createQuery(hqlRs);

      ScrollableResults rs = queryRs.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        auxRs = (ReservationStock) rs.get()[0];
      }

    } else if (type.endsWith("PO")) {
      poLineId = storagedetail.getId();
      String hqlRs = "SELECT MAX(rs) FROM MaterialMgmtReservationStock rs WHERE rs.reservation='"
          + reservation + "' AND rs.salesOrderLine.id='" + storagedetail.getId()
          + "' AND rs.allocated=" + allocated;
      Query queryRs = OBDal.getInstance().getSession().createQuery(hqlRs);

      ScrollableResults rs = queryRs.scroll(ScrollMode.FORWARD_ONLY);
      while (rs.next()) {
        auxRs = (ReservationStock) rs.get()[0];
      }
    } else {
      throw new OBException("Unsupported reservation type");
    }

    if (!auxRs.equals(null)) {
      BigDecimal quantity = auxRs.getQuantity();
      auxRs.setQuantity(quantity.add(qty));
      OBDal.getInstance().save(auxRs);
    }
    OrderLine orderline = OBDal.getInstance().get(OrderLine.class, poLineId);

    ReservationStock reservationStock = OBProvider.getInstance().get(ReservationStock.class);
    reservationStock.setActive(true);
    reservationStock.setReservation(reservation);
    reservationStock.setAttributeSetValue(asi);
    reservationStock.setStorageBin(locator);
    reservationStock.setSalesOrderLine(orderline);
    reservationStock.setQuantity(qty);
    reservationStock.setReleased(new BigDecimal(0));
    reservationStock.setAllocated(allocated);

    OBDal.getInstance().save(auxRs);
    OBDal.getInstance().commitAndClose();

    return reservationStock;
  }

  public void a_asset_create(ShipmentInOutLine shipmentInOutLine) {
    String shipLineClause = "AS iol WHERE iol.product.productCategory.assetCategory IS NOT NULL AND iol=? "
        + "AND iol.movementQuantity > '0'";
    List<Object> shipLineParams = new ArrayList<Object>();
    shipLineParams.add(shipmentInOutLine);
    OBQuery<ShipmentInOutLine> shipLine = OBDal.getInstance().createQuery(ShipmentInOutLine.class,
        shipLineClause, shipLineParams);

    for (ShipmentInOutLine shipLineList : shipLine.list()) {
      Client client = shipLineList.getShipmentReceipt().getClient();
      Organization organization = shipLineList.getShipmentReceipt().getOrganization();
      String documentNo = shipLineList.getShipmentReceipt().getDocumentNo();
      Date movementDate = shipLineList.getShipmentReceipt().getMovementDate();
      BusinessPartner businessPartner = shipLineList.getShipmentReceipt().getBusinessPartner();
      Location bpLocation = shipLineList.getShipmentReceipt().getPartnerAddress();
      User user = shipLineList.getShipmentReceipt().getUserContact();
      String bpValue = shipLineList.getShipmentReceipt().getBusinessPartner().getSearchKey();
      String bpName = shipLineList.getShipmentReceipt().getBusinessPartner().getName();
      String value = shipLineList.getProduct().getSearchKey();
      String name = shipLineList.getProduct().getName();
      String description = shipLineList.getProduct().getDescription();
      String help = shipLineList.getProduct().getHelpComment();
      String versionNo = shipLineList.getProduct().getVersionNo();
      AssetGroup assetCategory = shipLineList.getProduct().getProductCategory().getAssetCategory();
      Date guaranteeDate = null;

      if (!shipLineList.getProduct().getGuaranteedDays().equals(null)) {
        long guarantee = shipLineList.getProduct().getGuaranteedDays();
        guaranteeDate.setDate((int) (movementDate.getDay() + guarantee));
      }
      Product product = shipLineList.getProduct();
      String lineDescription = shipLineList.getDescription();
      BigDecimal movementQty = shipLineList.getMovementQuantity();

      documentNo = "_" + documentNo;
      value = "_" + value + "" + documentNo;
      name = "_" + name + "" + documentNo;
      value.length();
      String assetValue = bpValue.substring(0, 39 - value.length());
      Asset asset = OBProvider.getInstance().get(Asset.class);

      asset.setClient(client);
      asset.setOrganization(organization);
      asset.setActive(true);
      asset.setSearchKey(assetValue.substring(0, 39));
      asset.setName(bpName.substring(0, 60 - name.length()) + "" + name);
      String desc = description + " " + lineDescription;
      asset.setDescription(desc.substring(0, 254));
      asset.setHelpComment(help);
      asset.setAssetCategory(assetCategory);
      asset.setProduct(product);
      asset.setVersionNo(versionNo);
      asset.setExpirationDate(guaranteeDate);
      asset.setInServiceDate(movementDate);
      asset.setOwned(false);
      asset.setDepreciate(false);
      asset.setAssetDepreciationDate(null);
      asset.setInPossession(false);
      asset.setLocationComment(documentNo);
      asset.setBusinessPartner(businessPartner);
      asset.setPartnerAddress(bpLocation);
      asset.setUserContact(user);
      OBDal.getInstance().save(asset);

    }
  }

  public String ad_sequence_doctype(String DoctypeId, String Id, String updateNext) {
    ConnectionProvider conn = new DalConnectionProvider();
    Statement st;

    String sqlDocType = "SELECT p_documentno FROM Ad_Sequence_Doctype('" + DoctypeId + "', '" + Id
        + "', '" + updateNext + "')";
    String documentNo = "";
    try {
      st = conn.getStatement();
      ResultSet rsDocType = st.executeQuery(sqlDocType);
      while (rsDocType.next()) {
        documentNo = rsDocType.getString("p_documentno");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return documentNo;
  }

  public String ad_sequence_doc(String sequenceName, String ClientId, String updateNext) {
    ConnectionProvider conn = new DalConnectionProvider();
    Statement st;

    String sqlDoc = "SELECT p_documentno FROM Ad_Sequence_Doc('" + sequenceName + "', '" + ClientId
        + "', '" + updateNext + "')";
    String documentNo = "";
    try {
      st = conn.getStatement();
      ResultSet rsDoc = st.executeQuery(sqlDoc);
      while (rsDoc.next()) {
        documentNo = rsDoc.getString("p_documentno");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return documentNo;
  }
}
