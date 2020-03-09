package org.wirabumi.gen.oez.porting;

import java.sql.ResultSet;
import java.sql.Statement;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.service.db.DalConnectionProvider;

public class AdOrgChkDocument {

  String OrganizationHeaderId;
  String lineOrganization = "";
  int isInclude;

  public AdOrgChkDocument(String HeaderTable, String LinesTable, String DocumentId,
      String HeaderColumnName, String LinesColumnName) {

    AdGetDocLeBu leBu = new AdGetDocLeBu(HeaderTable, DocumentId, HeaderColumnName, "");

    OrganizationHeaderId = leBu.getOrganizationHeader();
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
  }

  public int getIsInclude() {
    return isInclude;
  }
}
