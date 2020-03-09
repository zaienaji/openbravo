package org.wirabumi.gen.oez.porting;

import java.sql.ResultSet;
import java.sql.Statement;

import org.openbravo.base.exception.OBException;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.service.db.DalConnectionProvider;

public class AdGetOrgLeBu {

  String orgHeaderId;
  boolean isLegalenty;
  boolean isBusinessUnit;

  public AdGetOrgLeBu(String organizationHeader, String type) {
    // TODO Auto-generated constructor stub

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
          // TODO Auto-generated catch block
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
          // TODO Auto-generated catch block
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
          // TODO Auto-generated catch block
          e.printStackTrace();
          throw new OBException(e.getMessage());

        }
      }
    }
  }

  public String getOrganizationHeader() {
    return orgHeaderId;
  }
}
