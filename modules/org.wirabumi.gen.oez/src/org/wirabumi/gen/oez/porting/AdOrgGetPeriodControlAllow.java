package org.wirabumi.gen.oez.porting;

import java.sql.ResultSet;
import java.sql.Statement;

import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.service.db.DalConnectionProvider;

public class AdOrgGetPeriodControlAllow {

  String parentId = "";
  String nodeId = "";
  Organization organization;
  boolean isPeriodConttrolAllowed = false;

  public AdOrgGetPeriodControlAllow(String OrganizationId) {
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
  }

  public String getOrgPeriodControlAllow() {
    return parentId;
  }
}
