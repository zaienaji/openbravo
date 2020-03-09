package org.wirabumi.gen.oez.porting;

import java.math.BigDecimal;
import java.sql.Connection;
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
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.ClientInformation;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.enterprise.OrgWarehouse;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.invoice.InvoiceLine;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.openbravo.model.materialmgmt.onhandquantity.Reservation;
import org.openbravo.model.materialmgmt.onhandquantity.ReservationStock;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.model.procurement.POInvoiceMatch;
import org.openbravo.model.procurement.ReceiptInvoiceMatch;
import org.openbravo.service.db.DalConnectionProvider;
import org.wirabumi.gen.oez.event.DocumentRoutingHandlerAction;

public class ShipmentInoutPost extends DocumentRoutingHandlerAction {

  private static final String String = null;

@Override
  public void doRouting(String adWindowId, String adTabId, String doc_status_to,
      VariablesSecureApp vars, List recordId) {
    // TODO Auto-generated method stub
    try {
      Client client = OBContext.getOBContext().getCurrentClient();
      User user = OBContext.getOBContext().getUser();
      // String docAc = doc_status_to;
      for (int i = 0; i < recordId.size(); i = i + 1) {
        String shipmentInoutId = (String) recordId.get(i);
        ShipmentInOut shipmentInout = OBDal.getInstance().get(ShipmentInOut.class, shipmentInoutId);

        CheckShipmentInoutValidation(client, shipmentInout);
        boolean isActive = shipmentInout.isActive();
        boolean isProcessed = shipmentInout.isProcessed();
        String docStatus = shipmentInout.getDocumentStatus();

        if (doc_status_to.equals("CO")) {

          if (!docStatus.endsWith("CL") && !docStatus.endsWith("RE") && !docStatus.endsWith("VO")
              && !docStatus.endsWith("RJ") && !docStatus.endsWith("XL")) {
            if (isActive == true && isProcessed == false) {
              Complete(shipmentInout, user);
            }
          }
        } else if (doc_status_to.equals("VO")) {
          if (docStatus.endsWith("CO")) {
            Void(shipmentInout, user);
          }
        }
      }
      // throw new OBException("Error coba'");
    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      e.printStackTrace();
      throw new OBException(e.getMessage());
    }

  }

  private void CheckShipmentInoutValidation(Client client, ShipmentInOut shipmentInout) {

    PortingUtility portingUtility = new PortingUtility();
    Warehouse warehouse = shipmentInout.getWarehouse();
    Organization organization = shipmentInout.getOrganization();
    Organization bpOrgId = shipmentInout.getBusinessPartner().getOrganization();
    Organization shipmentOrgId = shipmentInout.getOrganization();
    BusinessPartner businessPartner = shipmentInout.getBusinessPartner();
    String docAction = shipmentInout.getDocumentAction();
    DocumentType docType = shipmentInout.getDocumentType();
    Date dateAcct = shipmentInout.getAccountingDate();
    boolean isSOTrx = shipmentInout.isSalesTransaction();
    boolean isReturn = docType.isReturn();
    boolean orgIsReady = shipmentInout.getOrganization().isReady();
    boolean orgIsTrxAllow = shipmentInout.getOrganization().getOrganizationType()
        .isTransactionsAllowed();

    BigDecimal isOrgInclude = portingUtility.ad_isorgincluded(shipmentOrgId, bpOrgId, client);

    OBCriteria<ClientInformation> clientInformation = OBDal.getInstance().createCriteria(
        ClientInformation.class);
    clientInformation.add(Restrictions.eq(ClientInformation.PROPERTY_CHECKSHIPMENTORGANIZATION,
        true));

    if (clientInformation.list().size() > 0) {

      String shipmentInoutClause = "AS mio WHERE mio=? AND -1=?";
      List<Object> shipmentInoutParams = new ArrayList<Object>();
      shipmentInoutParams.add(shipmentInout);
      shipmentInoutParams.add(isOrgInclude.intValue());

      OBQuery<ShipmentInOut> siCheckOrgInclude = OBDal.getInstance().createQuery(
          ShipmentInOut.class, shipmentInoutClause, shipmentInoutParams);

      if (siCheckOrgInclude.list().size() > 0) {
        throw new OBException(
            "The organization of the  Business partner  is different or does not depend on the organization associated with the Shipment");
      } else {

        // Cek warehouse apakah mempunyai organisasi
        OBCriteria<OrgWarehouse> orgWarehouse = OBDal.getInstance().createCriteria(
            OrgWarehouse.class);
        orgWarehouse.add(Restrictions.eq(OrgWarehouse.PROPERTY_ORGANIZATION, organization));
        orgWarehouse.add(Restrictions.eq(OrgWarehouse.PROPERTY_WAREHOUSE, warehouse));

        int countOrgWarehouse = orgWarehouse.list().size();

        if (countOrgWarehouse == 0) {
          throw new OBException(
              "The Warehouse of the document does not belong to the selected Organization ");
        }

        // check organization Warehouse in tree
        if ((portingUtility.ad_org_isinnaturaltree(warehouse.getOrganization(), organization,
            client)) == false && isSOTrx == false) {
          throw new OBException(
              "The Warehouse of the document does not belong to the selected Organization ");
        }

        // get bussines Partner Blocked & goods Shipment/Receipt
        boolean bpartnerBlocked = true;
        boolean goodsBlocked = true;

        if (isSOTrx == true) {
          bpartnerBlocked = businessPartner.isCustomerBlocking();
          goodsBlocked = businessPartner.isGoodsShipment();
        } else {
          bpartnerBlocked = businessPartner.isVendorBlocking();
          goodsBlocked = businessPartner.isGoodsReceipt();
        }

        if (docAction.equals("CO") && bpartnerBlocked == true && goodsBlocked == true
            && isReturn == false) {
          throw new OBException("The business partner : " + businessPartner.getName()
              + " is on hold for this document, therefore it is not possible to complete it.");
        }

        // Checking Restrictions
        BigDecimal isOrgInclude2 = portingUtility.ad_isorgincluded(shipmentOrgId,
            docType.getOrganization(), shipmentInout.getClient());
        String checkRestrictionClause = "AS mio WHERE mio=? AND  mio.documentType.documentCategory IN ('MMR', 'MMS') AND  mio.documentType.salesTransaction = mio.salesTransaction AND -1 != ?";
        List<Object> checkRestrictionParams = new ArrayList<Object>();
        checkRestrictionParams.add(shipmentInout);
        checkRestrictionParams.add(isOrgInclude2.intValue());

        OBQuery<ShipmentInOut> checkRestriction = OBDal.getInstance().createQuery(
            ShipmentInOut.class, checkRestrictionClause, checkRestrictionParams);

        if (checkRestriction.list().size() == 0) {
          throw new OBException(
              "The organization associated with the  Document Type  is different or does not depend on the organization associated with the Shipment");
        }

        // Checking Attribute Set Value
        String checkAttributeClause = "AS miol WHERE miol.shipmentReceipt=? AND miol.product.attributeSet IS NOT NULL AND "
            + "(miol.product.useAttributeSetValueAs IS NULL OR miol.product.useAttributeSetValueAs != 'F') AND "
            + "(SELECT ats.requireAtLeastOneValue FROM AttributeSet ats WHERE ats=miol.product.attributeSet) = true AND "
            + "COALESCE(miol.attributeSetValue.id, '0')='0'";
        List<Object> checkAttributeParams = new ArrayList<Object>();
        checkAttributeParams.add(shipmentInout);

        OBQuery<ShipmentInOutLine> checkAttribute = OBDal.getInstance().createQuery(
            ShipmentInOutLine.class, checkAttributeClause, checkAttributeParams);

        int countAttr = checkAttribute.list().size();
        if (countAttr != 0) {
          long lineNO = checkAttribute.list().get(0).getLineNo();
          throw new OBException("In line " + lineNO + " the product has no attribute set");
        }

        // Checking Locked Product
        String checkLockedClause = "AS miol WHERE miol.shipmentReceipt=? AND "
            + "miol.attributeSetValue.islocked=true AND miol.shipmentReceipt.salesTransaction=true";
        List<Object> checkLockedParams = new ArrayList<Object>();
        checkLockedParams.add(shipmentInout);

        OBQuery<ShipmentInOutLine> checkLocked = OBDal.getInstance().createQuery(
            ShipmentInOutLine.class, checkLockedClause, checkLockedParams);

        if (checkLocked.list().size() != 0) {
          long lineNO = checkLocked.list().get(0).getLineNo();
          throw new OBException("In line " + lineNO
              + " the product is locked and cannot be delivered");
        }

        // Checking Instance Location
        String checkLocationClause = "AS miol WHERE miol.shipmentReceipt=? AND "
            + "miol.storageBin IS NULL AND miol.product.stocked=true AND miol.product.productType='I'";
        List<Object> checkLocationParams = new ArrayList<Object>();
        checkLocationParams.add(shipmentInout);

        OBQuery<ShipmentInOutLine> checkLocation = OBDal.getInstance().createQuery(
            ShipmentInOutLine.class, checkLocationClause, checkLocationParams);

        if (checkLocation.list().size() != 0) {
          long lineNO = checkLocation.list().get(0).getLineNo();
          throw new OBException("In line " + lineNO + " has no storage bin defined");
        }

        // Checking bom non-stockable is exploded
        String checkBomClause = "AS miol WHERE miol.shipmentReceipt=? AND miol.product.stocked=false AND "
            + "miol.product.billOfMaterials=true AND miol.explode=false";
        List<Object> checkBomParams = new ArrayList<Object>();
        checkBomParams.add(shipmentInout);

        OBQuery<ShipmentInOutLine> checkBom = OBDal.getInstance().createQuery(
            ShipmentInOutLine.class, checkBomClause, checkBomParams);

        if (checkBom.list().size() != 0) {
          long lineNO = checkBom.list().get(0).getLineNo();
          throw new OBException("In line " + lineNO
              + " A BOM non-stocked is not exploded. Please, explode it manually.");
        }

        // Check negative quantities on return inouts
        if (isReturn == true) {
          String checkNegativeClause = "AS miol WHERE miol.shipmentReceipt=? AND miol.movementQuantity > '0' AND "
              + "miol.canceledInoutLine IS NULL AND miol.salesOrderLine.orderDiscount IS NULL";
          List<Object> checkNegativeParams = new ArrayList<Object>();
          checkNegativeParams.add(shipmentInout);

          OBQuery<ShipmentInOutLine> checkNegative = OBDal.getInstance().createQuery(
              ShipmentInOutLine.class, checkNegativeClause, checkNegativeParams);

          if (checkNegative.list().size() != 0) {
            throw new OBException(
                "Return Receipts/Shipments do not allow lines with positive quantities.");
          }
        }

        // Check Use Generic Product
        String checkGenericClause = "AS miol WHERE miol.shipmentReceipt=? AND miol.product.isGeneric=true";
        List<Object> checkGenericParams = new ArrayList<Object>();
        checkGenericParams.add(shipmentInout);

        OBQuery<ShipmentInOutLine> checkGeneric = OBDal.getInstance().createQuery(
            ShipmentInOutLine.class, checkGenericClause, checkGenericParams);

        if (checkGeneric.list().size() > 0) {
          throw new OBException(checkGeneric.list().get(0).getProduct().getName()
              + " It is not possible to use generic products in the document.");
        }

        // Check Line Organization
        OBCriteria<ShipmentInOutLine> shipmentInoutLine = OBDal.getInstance().createCriteria(
            ShipmentInOutLine.class);
        shipmentInoutLine.add(Restrictions.eq(ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT,
            shipmentInout));

        for (ShipmentInOutLine shipmentLineList : shipmentInoutLine.list()) {
          BigDecimal isOrgInclude3 = portingUtility.ad_isorgincluded(
              shipmentLineList.getOrganization(), shipmentInout.getOrganization(),
              shipmentInout.getClient());
          String checkLineOrgClause = "AS mio WHERE mio=? AND -1 != ?";
          List<Object> checkLineOrgParams = new ArrayList<Object>();
          checkLineOrgParams.add(shipmentInout);
          checkLineOrgParams.add(isOrgInclude3.intValue());
          OBQuery<ShipmentInOutLine> checkLineOrg = OBDal.getInstance().createQuery(
              ShipmentInOutLine.class, checkLineOrgClause, checkLineOrgParams);

          if (checkLineOrg.list().size() > 0) {
            throw new OBException(
                "The organization of the lines is different and does not depend on the organization associated with the header.");
          }
        }

        // Check Header Organization Is Ready
        if (orgIsReady == false) {
          throw new OBException("The document belongs to a not ready organization");
        }

        // Check Header Organization Transaction Allowed
        if (orgIsTrxAllow == false) {
          throw new OBException(
              "The header document belongs to a organization where transactions are not allowed");
        }

        int orgCheckDoc = portingUtility.ad_org_chk_documents("MaterialMgmtShipmentInOut",
            "MaterialMgmtShipmentInOutLine", shipmentInout.getId(), "id", "shipmentReceipt");

        if (orgCheckDoc == -1) {
          throw new OBException(
              "The document has lines of different business units or legal entities");
        }

        // Check the period control is opened
        String orgBuleId = portingUtility.ad_get_doc_le_bu("MaterialMgmtShipmentInOut",
            shipmentInout.getId(), "id", "LE");
        Organization buleOrganization = OBDal.getInstance().get(Organization.class, orgBuleId);

        boolean isAcctle = buleOrganization.getOrganizationType().isLegalEntityWithAccounting();
        if (isAcctle == true) {
          int availablePeriod = portingUtility.c_chk_open_period(organization.getId(), dateAcct,
              "", docType.getId());

          if (availablePeriod != 1) {
            if (docAction.endsWith("RC")) {
              throw new OBException("The Period does not exist or it is not opened");
            }
          }
        }
      }
    }
  }

  private void Complete(ShipmentInOut shipmentInout, User user) {

    boolean isSoTrx = shipmentInout.isSalesTransaction();

    BigDecimal qtySO, qtyPO;// , qtyOrderSO, qtyOrderPO;
    String result = "";
    String message = "";

    OBCriteria<ShipmentInOutLine> shipmentInoutLine = OBDal.getInstance().createCriteria(
        ShipmentInOutLine.class);
    shipmentInoutLine.add(Restrictions
        .eq(ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT, shipmentInout));

    if (shipmentInoutLine.list().size() == 0) {
      throw new OBException("The document cannot be completed because it has no lines");
    }

    for (ShipmentInOutLine shipmentInoutList : shipmentInoutLine.list()) {
      // Check Incomming or Outgoing
      PortingUtility portingUtility = new PortingUtility();
      BigDecimal qty = shipmentInoutList.getMovementQuantity();
      BigDecimal qtyOrder = shipmentInoutList.getOrderQuantity();
      String movementType = shipmentInout.getMovementType().substring(1);
      ShipmentInOutLine canceledInout = shipmentInoutList.getCanceledInoutLine();
      Locator locator = shipmentInoutList.getStorageBin();
      AttributeSetInstance attributeSetInstance = shipmentInoutList.getAttributeSetValue();
      ReservationStock resStock = null;
      if (movementType.endsWith("-")) {
        qty = qty.negate();
        qtyOrder = qtyOrder.negate();
      }

      if (isSoTrx == false) {
        qtySO = new BigDecimal(0);
        qtyPO = shipmentInoutList.getMovementQuantity();
        // qtyOrderSO = new BigDecimal(0);
        // qtyOrderPO = shipmentInoutList.getOrderQuantity();
      } else {
        qtySO = shipmentInoutList.getMovementQuantity();
        qtyPO = new BigDecimal(0);
        // qtyOrderSO = shipmentInoutList.getOrderQuantity();
        // qtyOrderPO = new BigDecimal(0);
      }

      // UOM Conversion
      // Is it a standard stocked product
      Product product = shipmentInoutList.getProduct();
      List<OrderLine> orderLine = shipmentInoutList.getOrderLineList();
      boolean isStocked = product.isStocked();
      String productType = product.getProductType();
      boolean isDescription = shipmentInoutList.isDescriptionOnly();

      int countStocked = 0;

      if (isStocked == true && productType.endsWith("I")) {
        countStocked = 1;
      }

      // Create Transaction for stocked product
      if (!product.equals(null) && countStocked == 1 && isDescription != true) {
        if (isSoTrx == true && !orderLine.equals(null) && qty.intValue() < 0
            && canceledInout == null) {
          // Manage Reservations
          OBCriteria<Reservation> reservation = OBDal.getInstance().createCriteria(
              Reservation.class);
          reservation.add(Restrictions.eq(Reservation.PROPERTY_SALESORDERLINE, orderLine.get(0)));
          int reservationCount = reservation.list().size();
          if (reservationCount > 1) {
            throw new OBException("There is a sales order line with more than one open reservation");
          } else if (reservationCount == 1) {
            String[] consumption = portingUtility.m_reservation_consumption(
                reservation.list().get(0), locator, attributeSetInstance, qty, user);
            result = consumption[0];
            message = consumption[1];
          }
        } else if (isSoTrx == true && !orderLine.equals(null) && qty.intValue() > 0
            && !canceledInout.equals(null)) {
          // Undo Reservation
          OBCriteria<Reservation> reservation = OBDal.getInstance().createCriteria(
              Reservation.class);
          reservation.add(Restrictions.eq(Reservation.PROPERTY_SALESORDERLINE, orderLine.get(0)));
          int reservationCount = reservation.list().size();

          if (attributeSetInstance == null) {
            attributeSetInstance = OBDal.getInstance().get(AttributeSetInstance.class, "0");
          }
          if (reservationCount > 1) {
            throw new OBException("There is a sales order line with more than one open reservation");
          } else if (reservationCount == 1) {
            String releaseStockClause = "AS rs FROM MaterialMgmtReservationStock rs WHERE rs.storageBin=? "
                + "AND COALESCE(rs.attributeSetValue.id, '0')=? AND rs.reservation=? "
                + "AND COALESCE(rs.released, '0') > '0'";
            List<Object> releaseStockParams = new ArrayList<Object>();
            releaseStockParams.add(locator);
            releaseStockParams.add(attributeSetInstance.getId());
            releaseStockParams.add(reservation);
            BigDecimal qtyAux = qty;
            OBQuery<ReservationStock> releaseStock = OBDal.getInstance().createQuery(
                ReservationStock.class, releaseStockClause, releaseStockParams);
            for (ReservationStock releaseList : releaseStock.list()) {
              BigDecimal undoQty = releaseList.getReleased();
              BigDecimal releasedQty = new BigDecimal(0);
              if (!releaseList.getReleased().equals(null)) {
                releasedQty = releaseList.getReleased();
              }
              if (undoQty.intValue() > qtyAux.intValue()) {
                undoQty = qtyAux;
              }

              releaseList.setReleased(releasedQty.subtract(undoQty));
              OBDal.getInstance().save(releaseList);
              qtyAux = qtyAux.subtract(undoQty);
            }
          }
        } else if (isSoTrx == false && canceledInout == null) {
          // Manage Reservation
          BigDecimal pendingQty = qty;

          String reservedStockClause = "AS rs WHERE rs.salesOrderLine=? AND rs.quantity <> COALESCE(rs.released, '0') "
              + "AND rs.storageBin IS NULL AND rs.reservation.rESStatus NOT IN ('DR', 'CL')";
          List<Object> reservedStockParams = new ArrayList<Object>();
          reservedStockParams.add(shipmentInoutList.getSalesOrderLine());

          OBQuery<ReservationStock> releaseStock = OBDal.getInstance().createQuery(
              ReservationStock.class, reservedStockClause, reservedStockParams);
          for (ReservationStock reservedList : releaseStock.list()) {
            BigDecimal relesedQty = new BigDecimal(0);
            if (!reservedList.equals(null)) {
              relesedQty = reservedList.getReleased();
            }
            BigDecimal qtyAux = reservedList.getQuantity().subtract(relesedQty);

            if (qtyAux.intValue() > pendingQty.intValue()) {
              qtyAux = pendingQty;
            }

            if (attributeSetInstance == null) {
              attributeSetInstance = OBDal.getInstance().get(AttributeSetInstance.class, "0");
            }

            final String checkRsHql = "SELECT COUNT(rs), MAX(rs) FROM MaterialMgmtReservationStock rs WHERE rs.salesOrderLine='"
                + shipmentInoutList.getSalesOrderLine()
                + "' "
                + "AND rs.storageBin = '"
                + locator
                + "' AND rs.reservation='"
                + reservedList.getReservation()
                + "' AND "
                + "rs.allocated=false AND COALESCE(rs.attributeSetValue.id, '0') = COALESCE('"
                + attributeSetInstance.getId() + "', '0')";
            Query checkRsQuery = OBDal.getInstance().getSession().createQuery(checkRsHql);

            ScrollableResults checkRsrs = checkRsQuery.scroll(ScrollMode.FORWARD_ONLY);
            long countRs = 0;

            while (checkRsrs.next()) {
              countRs = (Long) checkRsrs.get(0);
              resStock = (ReservationStock) checkRsrs.get(1);
            }

            // Update existing prereserved stock to decrease reserved qty
            BigDecimal quantity = resStock.getQuantity();
            resStock.setQuantity(quantity.subtract(qtyAux));

            // Insert or update reserved stock by same quantity
            if (countRs > 0) {
              resStock.setQuantity(quantity.add(qtyAux));
            } else {
              ReservationStock rs2 = OBProvider.getInstance().get(ReservationStock.class);
              rs2.setActive(true);
              rs2.setReservation(reservedList.getReservation());
              rs2.setAttributeSetValue(attributeSetInstance);
              rs2.setStorageBin(locator);
              rs2.setSalesOrderLine(shipmentInoutList.getSalesOrderLine());
              rs2.setQuantity(qtyAux);
              rs2.setReleased(new BigDecimal(0));
              rs2.setAllocated(true);
              OBDal.getInstance().save(rs2);
            }

            OBDal.getInstance().save(resStock);

            pendingQty = pendingQty.subtract(qtyAux);

            if (pendingQty.intValue() <= 0) {
              return;
            }
          }

          String deleteRsClause = "AS rs WHERE rs.salesOrderLine=? AND rs.quantity='0' AND COALESCE(rs.released, '0') = '0'";
          List<Object> deleteRsParams = new ArrayList<Object>();
          deleteRsParams.add(shipmentInoutList.getSalesOrderLine());

          OBQuery<ReservationStock> deleteRs = OBDal.getInstance().createQuery(
              ReservationStock.class, deleteRsClause, deleteRsParams);

          for (ReservationStock delRs : deleteRs.list()) {
            OBDal.getInstance().remove(delRs);
          }
        } else if (isSoTrx == false && !canceledInout.equals(null) && qty.intValue() < 0) {
          // Revert to pre-reservations
          BigDecimal pendingQty = qty.negate();
          BigDecimal auxReleased = new BigDecimal(0);

          String reservedStockClause = "AS rs WHERE rs.salesOrderLine=? AND rs.storageBin.id=? AND "
              + "rs.reservation.rESStatus NOT IN ('DR', 'CL')";
          List<Object> reservedStockParams = new ArrayList<Object>();
          reservedStockParams.add(shipmentInoutList.getSalesOrderLine());
          reservedStockParams.add(locator);
          OBQuery<ReservationStock> releaseStock = OBDal.getInstance().createQuery(
              ReservationStock.class, reservedStockClause, reservedStockParams);

          for (ReservationStock reservedList : releaseStock.list()) {
            BigDecimal releseQty = new BigDecimal(0);
            if (!reservedList.getReleased().equals(null)) {
              releseQty = reservedList.getReleased();
            }
            BigDecimal qtyAux = reservedList.getQuantity().subtract(releseQty);
            if (qtyAux.intValue() > pendingQty.intValue()) {
              qtyAux = pendingQty;
            }
            auxReleased = auxReleased.add(releseQty);
            if (reservedList.getQuantity().intValue() != releseQty.intValue()) {
              // Check if exists a prereservation for the same orderline, attributes and locator in
              // the reservation
              final String checkRsHql = "SELECT COUNT(rs), MAX(rs) FROM MaterialMgmtReservationStock rs WHERE rs.salesOrderLine='"
                  + shipmentInoutList.getSalesOrderLine()
                  + "' "
                  + "AND rs.storageBin IS NULL AND rs.reservation='"
                  + reservedList.getReservation() + "' ";
              Query checkRsQuery = OBDal.getInstance().getSession().createQuery(checkRsHql);

              ScrollableResults checkRsrs = checkRsQuery.scroll(ScrollMode.FORWARD_ONLY);
              long countRs = 0;

              while (checkRsrs.next()) {
                countRs = (Long) checkRsrs.get(0);
                resStock = (ReservationStock) checkRsrs.get(1);
              }
              // Update existing prereserved stock to decrease reserved qty
              BigDecimal quantity = resStock.getQuantity();
              resStock.setQuantity(quantity.subtract(qtyAux));

              // Insert or update reserved stock by same quantity
              if (countRs > 0) {
                resStock.setQuantity(quantity.add(qtyAux));
              } else {
                ReservationStock rs2 = OBProvider.getInstance().get(ReservationStock.class);
                rs2.setActive(true);
                rs2.setReservation(reservedList.getReservation());
                attributeSetInstance = OBDal.getInstance().get(AttributeSetInstance.class, "0");
                rs2.setAttributeSetValue(attributeSetInstance);
                rs2.setStorageBin(null);
                rs2.setSalesOrderLine(shipmentInoutList.getSalesOrderLine());
                rs2.setQuantity(qtyAux);
                rs2.setReleased(new BigDecimal(0));
                rs2.setAllocated(true);
                OBDal.getInstance().save(rs2);
              }

              OBDal.getInstance().save(resStock);
              pendingQty = pendingQty.subtract(qtyAux);

              if (pendingQty.intValue() <= 0) {
                return;
              }
            }
          }
          if (pendingQty.intValue() > 0 && auxReleased.intValue() > 0) {
            throw new OBException(
                "There are related Pre Reservations linked with this Goods Receipt that are released. Please void the needed shipments to undo the release before voiding this Receipt.");
          }
          String deleteRsClause = "AS rs WHERE rs.salesOrderLine=? AND rs.quantity='0' AND COALESCE(rs.released, '0') = '0'";
          List<Object> deleteRsParams = new ArrayList<Object>();
          deleteRsParams.add(shipmentInoutList.getSalesOrderLine());

          OBQuery<ReservationStock> deleteRs = OBDal.getInstance().createQuery(
              ReservationStock.class, deleteRsClause, deleteRsParams);
          OBDal.getInstance().remove(deleteRs);
        }
      }

      // Create Transaction
      MaterialTransaction transaction = OBProvider.getInstance().get(MaterialTransaction.class);
      transaction.setGoodsShipmentLine(shipmentInoutList);
      transaction.setActive(true);
      transaction.setMovementType(shipmentInout.getMovementType());
      transaction.setStorageBin(shipmentInoutList.getStorageBin());
      transaction.setProduct(shipmentInoutList.getProduct());
      if (attributeSetInstance == null) {
        attributeSetInstance = OBDal.getInstance().get(AttributeSetInstance.class, "0");
      }
      transaction.setAttributeSetValue(attributeSetInstance);
      transaction.setMovementDate(shipmentInout.getMovementDate());
      transaction.setMovementQuantity(qty);
      transaction.setOrderUOM(shipmentInoutList.getOrderUOM());
      transaction.setOrderQuantity(qtyOrder);
      transaction.setUOM(shipmentInoutList.getUOM());

      OBDal.getInstance().save(transaction);

      // Create Asset
      if (!shipmentInoutList.getProduct().equals(null) && isSoTrx == true) {
        portingUtility.a_asset_create(shipmentInoutList);
      }

      if (!shipmentInoutList.getSalesOrderLine().equals(null)) {
        OrderLine orderLine2 = shipmentInoutList.getSalesOrderLine();
        String docStatus = orderLine2.getSalesOrder().getDocumentStatus();
        BigDecimal qtyDelivered = orderLine2.getDeliveredQuantity();
        BigDecimal qtyReserved = orderLine2.getReservedQuantity();
        Date date = new Date();
        // stocked product
        if (!shipmentInoutList.getProduct().equals(null) && countStocked == 1) {

          if (docStatus.endsWith("DR")) {
            orderLine2.setDeliveredQuantity(qtyDelivered.add(qtySO));
            orderLine2.setUpdated(date);
            orderLine2.setUpdatedBy(user);
          } else {
            orderLine2.setReservedQuantity(qtyReserved.subtract(qtyPO).subtract(qtySO));
            orderLine2.setDeliveredQuantity(qtyDelivered.add(qtySO));
            orderLine2.setUpdated(date);
            orderLine2.setUpdatedBy(user);
          }

          OBDal.getInstance().save(orderLine2);
        } else {
          // Products not stocked
          orderLine2.setDeliveredQuantity(qtyDelivered.add(qtySO));
          orderLine2.setUpdated(date);
          orderLine2.setUpdatedBy(user);
          OBDal.getInstance().save(orderLine2);
        }
        // OBDal.getInstance().commitAndClose();
      }

      if (!shipmentInoutList.getProduct().equals(null) && countStocked == 1) {
        ConnectionProvider conn = new DalConnectionProvider();
        Statement st;
        String sqlCheckStock = "SELECT p_result, p_message FROM M_Check_Stock('"
            + shipmentInoutList.getProduct().getId() + "','"
            + shipmentInoutList.getClient().getId() + "','"
            + shipmentInoutList.getOrganization().getId() + "')";

        try {
          st = conn.getStatement();
          ResultSet rsCheckStock = st.executeQuery(sqlCheckStock);
          while (rsCheckStock.next()) {
            result = rsCheckStock.getString("p_result");
            message = rsCheckStock.getString("p_message");
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        int intResult = Integer.parseInt(result);
        if (intResult == 0) {
          throw new OBException(message + " line " + shipmentInoutList.getLineNo() + ", Product "
              + shipmentInoutList.getProduct().getName());
        }
      }

    }

    if (isSoTrx == false) {
      // PO Matching
      String documentNo = shipmentInout.getId();
      String SLinesClause = "AS iol WHERE iol.product = iol.salesOrderLine.product "
          + "AND iol.shipmentReceipt.id=?";
      List<Object> SLinesParams = new ArrayList<Object>();
      SLinesParams.add(shipmentInout.getId());
      OBQuery<ShipmentInOutLine> SLinesQuery = OBDal.getInstance().createQuery(
          ShipmentInOutLine.class, SLinesClause, SLinesParams);

      for (ShipmentInOutLine SLines : SLinesQuery.list()) {

        // INSERT Insert Match PO
        POInvoiceMatch matchPO = OBProvider.getInstance().get(POInvoiceMatch.class);
        if (SLines.getMovementQuantity() != null) {
          BigDecimal qty = SLines.getMovementQuantity();
          matchPO.setQuantity(qty);
        }
        matchPO.setClient(SLines.getClient());
        matchPO.setOrganization(SLines.getOrganization());
        matchPO.setActive(true);
        matchPO.setGoodsShipmentLine(SLines);
        matchPO.setSalesOrderLine(SLines.getSalesOrderLine());
        matchPO.setProduct(SLines.getProduct());
        Date date = new Date();
        matchPO.setTransactionDate(date);
        matchPO.setProcessNow(false);
        matchPO.setProcessed(true);
        matchPO.setPosted("N");

        OBDal.getInstance().save(matchPO);
      }

      // PInvoice Matching
      String ILinesClause = " AS il WHERE il.goodsShipmentLine.shipmentReceipt.id=? ";
      List<Object> ILinesParams = new ArrayList<Object>();
      ILinesParams.add(shipmentInout.getId());
      OBQuery<InvoiceLine> ILinesQuery = OBDal.getInstance().createQuery(InvoiceLine.class,
          ILinesClause, ILinesParams);

      for (InvoiceLine ILines : ILinesQuery.list()) {

        // INSERT Insert Match PI
        ReceiptInvoiceMatch matchPI = OBProvider.getInstance().get(ReceiptInvoiceMatch.class);

        if (ILines.getGoodsShipmentLine().getMovementQuantity() != null) {
          BigDecimal qty = ILines.getGoodsShipmentLine().getMovementQuantity();
          matchPI.setQuantity(qty);
        }
        matchPI.setClient(ILines.getGoodsShipmentLine().getClient());
        matchPI.setOrganization(ILines.getGoodsShipmentLine().getOrganization());
        matchPI.setActive(true);
        matchPI.setGoodsShipmentLine(ILines.getGoodsShipmentLine());
        matchPI.setInvoiceLine(ILines);
        matchPI.setProduct(ILines.getGoodsShipmentLine().getProduct());
        Date date = new Date();
        matchPI.setTransactionDate(date);
        matchPI.setProcessNow(false);
        matchPI.setProcessed(true);
        matchPI.setPosted("N");
        OBDal.getInstance().save(matchPI);
      }
    } else {
      // Check delivery rule for sales orders
      String MessageAux = "";
      String orderIdOld = "0";
      String orderClause = "AS iol WHERE iol.shipmentReceipt= ? AND (( iol.salesOrderLine.salesOrder.deliveryTerms='O' AND "
          + "EXISTS (SELECT ol FROM OrderLine ol WHERE ol= iol.salesOrderLine AND ol.orderQuantity > ol.deliveredQuantity) "
          + "OR (iol.salesOrderLine.salesOrder.deliveryTerms='L' "
          + "AND iol.salesOrderLine.orderQuantity > iol.salesOrderLine.deliveredQuantity)))";
      List<Object> orderParams = new ArrayList<Object>();
      orderParams.add(shipmentInout);
      OBQuery<ShipmentInOutLine> orderQuery = OBDal.getInstance().createQuery(
          ShipmentInOutLine.class, orderClause, orderParams);

      for (ShipmentInOutLine order : orderQuery.list()) {
        if (!orderIdOld.equals(order.getSalesOrderLine().getSalesOrder().getId())
            || !order.getSalesOrderLine().getSalesOrder().getDeliveryTerms().equals("O")) {
          MessageAux = MessageAux + "Shipment " + shipmentInout.getDocumentNo();
          MessageAux = MessageAux + " line " + order.getLineNo() + " : ";
          MessageAux = MessageAux + "Sales Order No."
              + order.getSalesOrderLine().getSalesOrder().getDocumentNo();

          if (order.getSalesOrderLine().getSalesOrder().getDeliveryTerms().equals("O")) {
            MessageAux = MessageAux + " not completely delivered (Delivery rule: Complete Order) ";
          } else {
            MessageAux = MessageAux + " line "
                + order.getSalesOrderLine().getSalesOrder().getDocumentNo();
            MessageAux = MessageAux + " not completely delivered (Delivery rule: Complete Line) ";
          }
        }
        orderIdOld = order.getSalesOrderLine().getSalesOrder().getId();
      }

      if (!MessageAux.equals("") || MessageAux != "") {
        throw new OBException(MessageAux);
      }

    }

    shipmentInout.setProcessed(true);
    shipmentInout.setPosted("N");
    shipmentInout.setDocumentStatus("CO");
    shipmentInout.setDocumentAction("--");
    shipmentInout.setProcessGoodsJava("--");
    OBDal.getInstance().save(shipmentInout);

    OBDal.getInstance().commitAndClose();

    final OBError msg = new OBError();
    msg.setType("Success");
    msg.setTitle("Complete");
    msg.setMessage("Document no " + shipmentInout.getDocumentNo() + " completed sucessfully");
    // throw new OBException("oke da");
  }

  private void Void(ShipmentInOut shipmentInout, User user) {

    Date voidMovementDate = shipmentInout.getMovementDate();
    // Check if the m_inoutlines has an invoice lines related. In this case is not possible to void
    // the m_inout.

    String checkInvoiceLineClause = "AS il WHERE il.invoice.documentStatus != 'VO' AND il.goodsShipmentLine.shipmentReceipt=?";
    List<Object> checkInvoiceLineParams = new ArrayList<Object>();
    checkInvoiceLineParams.add(shipmentInout);
    OBQuery<InvoiceLine> checkInvoiceLineQuery = OBDal.getInstance().createQuery(InvoiceLine.class,
        checkInvoiceLineClause, checkInvoiceLineParams);

    if (checkInvoiceLineQuery.list().size() != 0) {
      throw new OBException(
          "It is not possible to void a shipment because it is related to some invoice/s. Review the linked items.");
    }

    // Check that there isn't any line with an invoice if the order's
    String checkOrderLineClause = "AS il WHERE il.goodsShipmentLine.shipmentReceipt=? AND il.goodsShipmentLine.shipmentReceipt.salesTransaction=true AND il.salesOrderLine.salesOrder.invoiceTerms IN ('D', 'O', 'S') AND il.invoice.processed=true";
    List<Object> checkOrderLineParams = new ArrayList<Object>();
    checkOrderLineParams.add(shipmentInout);
    OBQuery<InvoiceLine> checkOrderLineQuery = OBDal.getInstance().createQuery(InvoiceLine.class,
        checkOrderLineClause, checkOrderLineParams);
    int count = 0;
    BigDecimal qtyInvoiced = new BigDecimal(0);
    long lineNo = 0;
    for (InvoiceLine checkOrder : checkOrderLineQuery.list()) {
      count++;
      qtyInvoiced = qtyInvoiced.add(checkOrder.getInvoicedQuantity());
      lineNo = checkOrder.getLineNo();
    }
    if (qtyInvoiced.intValue() != 0) {
      if (count > 0) {
        throw new OBException(
            "Shipment No.:"
                + shipmentInout.getDocumentNo()
                + " line: "
                + lineNo
                + ". It is not possible to void a shipment of an order that has an invoice rule after delivery having invoiced lines.");
      }
    }

    DocumentType docTypeReversed = shipmentInout.getDocumentType().getDocumentCancelled();
    if (docTypeReversed == null) {
      docTypeReversed = shipmentInout.getDocumentType();
    }

    PortingUtility portingUtility = new PortingUtility();
    String documenNo = portingUtility.ad_sequence_doctype(docTypeReversed.getId(),
        shipmentInout.getId(), "Y");
    if (documenNo == null) {
      documenNo = portingUtility.ad_sequence_doc("DocumentNo_M_InOut", shipmentInout.getClient()
          .getId(), "Y");
    }

    // Indicate that it is invoiced (i.e. not printed on invoices)
    OBCriteria<ShipmentInOutLine> shipmentInOutLine = OBDal.getInstance().createCriteria(
        ShipmentInOutLine.class);
    shipmentInOutLine.add(Restrictions
        .eq(ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT, shipmentInout));

    for (ShipmentInOutLine mioLine : shipmentInOutLine.list()) {
      mioLine.setReinvoice(true);
      OBDal.getInstance().save(mioLine);
    }

    // Insert Shipment Inout
    ShipmentInOut mio = OBProvider.getInstance().get(ShipmentInOut.class);
    mio.setSalesOrder(shipmentInout.getSalesOrder());
    mio.setSalesTransaction(shipmentInout.isSalesTransaction());
    mio.setClient(shipmentInout.getClient());
    mio.setOrganization(shipmentInout.getOrganization());
    mio.setActive(true);
    mio.setDocumentNo(documenNo);
    mio.setDocumentType(docTypeReversed);
    mio.setDescription("(*R*: " + shipmentInout.getDocumentNo() + ") "
        + shipmentInout.getDescription());
    mio.setPrint(false);
    mio.setMovementType(shipmentInout.getMovementType());
    mio.setMovementDate(shipmentInout.getMovementDate());
    mio.setAccountingDate(shipmentInout.getAccountingDate());
    mio.setBusinessPartner(shipmentInout.getBusinessPartner());
    mio.setPartnerAddress(shipmentInout.getPartnerAddress());
    mio.setUserContact(shipmentInout.getUserContact());
    mio.setWarehouse(shipmentInout.getWarehouse());
    mio.setOrderReference(shipmentInout.getOrderReference());
    mio.setOrderDate(shipmentInout.getOrderDate());
    mio.setDeliveryTerms(shipmentInout.getDeliveryTerms());
    mio.setFreightCostRule(shipmentInout.getFreightCostRule());
    mio.setFreightAmount(shipmentInout.getFreightAmount().multiply(new BigDecimal(-1)));
    mio.setProject(shipmentInout.getProject());
    mio.setActivity(shipmentInout.getActivity());
    mio.setSalesCampaign(shipmentInout.getSalesCampaign());
    mio.setTrxOrganization(shipmentInout.getTrxOrganization());
    mio.setStDimension(shipmentInout.getStDimension());
    mio.setNdDimension(shipmentInout.getNdDimension());
    mio.setCostcenter(shipmentInout.getCostcenter());
    mio.setAsset(shipmentInout.getAsset());
    mio.setDeliveryMethod(shipmentInout.getDeliveryMethod());
    mio.setShippingCompany(shipmentInout.getShippingCompany());
    mio.setCharge(shipmentInout.getCharge());
    mio.setChargeAmount(shipmentInout.getChargeAmount().multiply(new BigDecimal(-1)));
    mio.setPriority(shipmentInout.getPriority());
    mio.setDocumentStatus("DR");
    mio.setDocumentAction("CO");
    mio.setProcessNow(false);
    mio.setProcessed(false);
    mio.setLogistic(shipmentInout.isLogistic());
    mio.setProcessGoodsJava("CO");

    OBDal.getInstance().save(mio);

    shipmentInout.setDescription(shipmentInout.getDescription() + " (*R*=" + documenNo + ")");
    shipmentInout.setProcessed(true);
    shipmentInout.setDocumentStatus("VO");
    shipmentInout.setDocumentAction("--");
    shipmentInout.setProcessGoodsJava("--");
    OBDal.getInstance().save(shipmentInout);

    OBCriteria<ShipmentInOutLine> miol = OBDal.getInstance()
        .createCriteria(ShipmentInOutLine.class);
    miol.add(Restrictions.eq(ShipmentInOutLine.PROPERTY_SHIPMENTRECEIPT, shipmentInout));
    miol.add(Restrictions.eq(ShipmentInOutLine.PROPERTY_ACTIVE, true));

    long lineNo2 = 10;
    for (ShipmentInOutLine mioLine : miol.list()) {
      ShipmentInOutLine mioLine2 = OBProvider.getInstance().get(ShipmentInOutLine.class);

      mioLine2.setLineNo(lineNo);
      mioLine2.setShipmentReceipt(mio);
      mioLine2.setSalesOrderLine(mioLine.getSalesOrderLine());
      mioLine2.setClient(mioLine.getClient());
      mioLine2.setOrganization(mioLine.getOrganization());
      mioLine2.setActive(true);
      mioLine2.setProduct(mioLine.getProduct());
      mioLine2.setAttributeSetValue(mioLine.getAttributeSetValue());
      mioLine2.setUOM(mioLine.getUOM());
      mioLine2.setStorageBin(mioLine.getStorageBin());
      BigDecimal movementQty = new BigDecimal(0);
      if (mioLine.getMovementQuantity() != null) {
        movementQty = mioLine.getMovementQuantity();
        mioLine2.setMovementQuantity(movementQty.multiply(new BigDecimal(-1)));
      }
      mioLine2.setDescription("*R*: " + mioLine.getDescription());
      mioLine2.setReinvoice(mioLine.isReinvoice());
      BigDecimal orderQty = new BigDecimal(0);
      if (mioLine.getOrderQuantity() != null) {
        orderQty = mioLine.getOrderQuantity();
        mioLine2.setOrderQuantity(orderQty.multiply(new BigDecimal(-1)));
      }
      mioLine2.setOrderUOM(mioLine.getOrderUOM());
      mioLine2.setDescriptionOnly(mioLine.isDescriptionOnly());
      mioLine2.setCanceledInoutLine(mioLine.getCanceledInoutLine());
      mioLine2.setAsset(mioLine.getAsset());
      mioLine2.setProject(mioLine.getProject());
      mioLine2.setBusinessPartner(mioLine.getBusinessPartner());
      mioLine2.setStDimension(mioLine.getStDimension());
      mioLine2.setNdDimension(mioLine.getNdDimension());
      mioLine2.setCostcenter(mioLine.getCostcenter());
      mioLine2.setExplode(mioLine.isExplode());

      OBDal.getInstance().save(mioLine2);

      mioLine.setDescription(mioLine.getDescription() + " : *R* ");
      OBDal.getInstance().save(mioLine);

      OBCriteria<ReceiptInvoiceMatch> miCriteria = OBDal.getInstance().createCriteria(
          ReceiptInvoiceMatch.class);
      miol.add(Restrictions.eq(ReceiptInvoiceMatch.PROPERTY_GOODSSHIPMENTLINE, mioLine));

      lineNo2 = lineNo2 + 10;
      for (ReceiptInvoiceMatch mi : miCriteria.list()) {
        ReceiptInvoiceMatch matchPI = OBProvider.getInstance().get(ReceiptInvoiceMatch.class);
        matchPI.setClient(mi.getClient());
        matchPI.setOrganization(mi.getOrganization());
        matchPI.setActive(mi.isActive());
        User user2 = OBDal.getInstance().get(User.class, "0");
        matchPI.setCreatedBy(user2);
        matchPI.setUpdatedBy(user2);
        matchPI.setGoodsShipmentLine(mioLine2);
        matchPI.setInvoiceLine(mi.getInvoiceLine());
        matchPI.setProduct(mi.getProduct());
        matchPI.setTransactionDate(mi.getTransactionDate());
        matchPI.setQuantity(mi.getQuantity().negate());
        matchPI.setProcessNow(false);
        matchPI.setProcessed(true);
        matchPI.setPosted("N");
        OBDal.getInstance().save(matchPI);
      }
    }

    OBDal.getInstance().commitAndClose();

    Complete(mio, user);
    mio.setDocumentStatus("VO");
    OBDal.getInstance().save(mio);

    OBDal.getInstance().commitAndClose();

    final OBError msg = new OBError();
    msg.setType("Success");
    msg.setTitle("Complete");
    msg.setMessage("Document no " + shipmentInout.getDocumentNo() + " Void sucessfully");
    // throw new OBException("Error coba'");
  }

@Override
public String getCoDocumentNo(String recordID, Tab tab) {
	// TODO Auto-generated method stub
	// Masih Uji coba ^_^
	//=========================================//
	ShipmentInOut minout = OBDal.getInstance().get(ShipmentInOut.class, recordID);
	
	DocumentType docType = minout.getDocumentType();
	Sequence sequence = docType.getOEZDocumentSequenceForComplete();
	String prefix = "";
	if(sequence.getPrefix() != null){
		prefix = sequence.getPrefix();
	}
	String suffix = "";
	if(sequence.getSuffix() != null){
		suffix = sequence.getSuffix();
	}
	Long number = sequence.getNextAssignedNumber();
	String documentNo = prefix+""+number+""+suffix;
	
	Long currentNext = number+1;
	sequence.setNextAssignedNumber(currentNext);
	OBDal.getInstance().save(sequence);
	OBDal.getInstance().commitAndClose();
	return documentNo;
}
	
}
