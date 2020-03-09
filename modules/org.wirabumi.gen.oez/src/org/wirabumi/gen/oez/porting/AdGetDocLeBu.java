package org.wirabumi.gen.oez.porting;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.openbravo.dal.service.OBDal;

public class AdGetDocLeBu {

  String organizationHeader;
  boolean isBussinesUnit;
  boolean isLegalEntity;

  public AdGetDocLeBu(String headerTable, String documentId, String headerColumnName, String type) {
    // TODO Auto-generated constructor stub

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
        AdGetOrgLeBu orgLeBu = new AdGetOrgLeBu(organizationHeader, "");
        organizationHeader = orgLeBu.getOrganizationHeader();
      }
    }
    // return organizationHeader;
  }

  public String getOrganizationHeader() {
    return organizationHeader;
  }
}
