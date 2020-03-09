package org.wirabumi.gen.oez.event;

import java.math.BigDecimal;
import java.util.List;

import javax.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.order.OrderLine;

public class CreditLimitHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = { ModelProvider.getInstance().getEntity(OrderLine.ENTITY_NAME) };
  private Logger log4j = Logger.getLogger(CreditLimitHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final OrderLine salesOrder = (OrderLine) event.getTargetInstance();
    List<OrderLine> otherLine = salesOrder.getSalesOrder().getOrderLineList();
    BigDecimal otherAmount = new BigDecimal(0);
    for (OrderLine oline : otherLine) {
      if (oline == salesOrder) {
        continue;
      } else {
        otherAmount = otherAmount.add(oline.getLineNetAmount());
      }
    }
    BigDecimal currentUpdate = salesOrder.getLineNetAmount();
    BigDecimal creditLimit = new BigDecimal(1);
    BigDecimal creditUsed = new BigDecimal(1);

    try {
      BusinessPartner bpartner = salesOrder.getSalesOrder().getBusinessPartner();
      creditLimit = bpartner.getCreditLimit();
      creditUsed = bpartner.getCreditUsed();

    } catch (Exception e) {
      e.printStackTrace();
      log4j.error(e);
    }

    BigDecimal creditLimitSum = creditUsed.add(otherAmount.add(currentUpdate));

    if (creditLimitSum.doubleValue() > creditLimit.doubleValue()) {
      salesOrder.getSalesOrder().setOezIsvalidrecord(true);
      // throw new OBException("Maximal Limit Net amount " + creditLimit);
      OBDal.getInstance().save(salesOrder.getSalesOrder());
    } else {
      salesOrder.getSalesOrder().setOezIsvalidrecord(false);
    }
    OBDal.getInstance().save(salesOrder.getSalesOrder());
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final OrderLine salesOrder = (OrderLine) event.getTargetInstance();
    List<OrderLine> otherLine = salesOrder.getSalesOrder().getOrderLineList();
    BigDecimal otherAmount = new BigDecimal(0);
    for (OrderLine oline : otherLine) {
      if (oline == salesOrder) {
        continue;
      } else {
        otherAmount = otherAmount.add(oline.getLineNetAmount());
      }
    }
    BigDecimal currentUpdate = salesOrder.getLineNetAmount();
    BigDecimal creditLimit = new BigDecimal(1);
    BigDecimal creditUsed = new BigDecimal(1);

    try {
      BusinessPartner bpartner = salesOrder.getSalesOrder().getBusinessPartner();
      creditLimit = bpartner.getCreditLimit();
      creditUsed = bpartner.getCreditUsed();

    } catch (Exception e) {
      e.printStackTrace();
      log4j.error(e);
    }

    BigDecimal creditLimitSum = creditUsed.add(otherAmount.add(currentUpdate));

    if (creditLimitSum.doubleValue() > creditLimit.doubleValue()) {
      salesOrder.getSalesOrder().setOezIsvalidrecord(true);
      // throw new OBException("Maximal Limit Net amount " + creditLimit);
    } else {
      salesOrder.getSalesOrder().setOezIsvalidrecord(false);
    }
    OBDal.getInstance().save(salesOrder.getSalesOrder());
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final OrderLine salesOrder = (OrderLine) event.getTargetInstance();

    BigDecimal salesOrderCredit = salesOrder.getSalesOrder().getGrandTotalAmount();
    BigDecimal currentUpdate = salesOrder.getLineNetAmount();
    BigDecimal creditLimit = new BigDecimal(1);
    BigDecimal creditUsed = new BigDecimal(1);

    try {
      BusinessPartner bpartner = salesOrder.getSalesOrder().getBusinessPartner();
      creditLimit = bpartner.getCreditLimit();
      creditUsed = bpartner.getCreditUsed();

    } catch (Exception e) {
      e.printStackTrace();
      log4j.error(e);
    }

    BigDecimal creditLimitSum = creditUsed.add(salesOrderCredit.subtract(currentUpdate));

    if (creditLimitSum.doubleValue() > creditLimit.doubleValue()) {
      salesOrder.getSalesOrder().setOezIsvalidrecord(true);
    } else {
      salesOrder.getSalesOrder().setOezIsvalidrecord(false);
    }
    OBDal.getInstance().save(salesOrder.getSalesOrder());
  }
}
