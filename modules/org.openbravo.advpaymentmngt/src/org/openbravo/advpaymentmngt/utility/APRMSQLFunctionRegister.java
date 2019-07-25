/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */

package org.openbravo.advpaymentmngt.utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.StandardBasicTypes;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.SQLFunctionRegister;
import org.openbravo.database.ConnectionProviderImpl;
import org.openbravo.erpCommon.utility.SystemInfo;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * A class in charge of registering APRM SQL functions in Hibernate.
 */
@ApplicationScoped
public class APRMSQLFunctionRegister implements SQLFunctionRegister {
  private static final String RDBMS = new DalConnectionProvider(false).getRDBMS();

  @Override
  public Map<String, SQLFunction> getSQLFunctions() {
    Map<String, SQLFunction> sqlFunctions = new HashMap<>();
    sqlFunctions.put("ad_message_get2",
        new StandardSQLFunction("ad_message_get2", StandardBasicTypes.STRING));
    sqlFunctions.put("hqlagg",
        new SQLFunctionTemplate(StandardBasicTypes.STRING, getAggregationSQL()));
    return sqlFunctions;
  }

  private String getAggregationSQL() {
    if ("ORACLE".equals(RDBMS)) {
      if (is11R2orNewer()) {
        return "listagg(to_char(?1), ',') WITHIN GROUP (ORDER BY ?1)";
      } else if (existsStrAgg()) {
        return "stragg(to_char(?1))";
      } else {
        return "wm_concat(to_char(?1))";
      }
    } else {
      return "array_to_string(array_agg(?1), ',')";
    }
  }

  private boolean existsStrAgg() {
    try {
      ConnectionProviderImpl connectionProvider = new ConnectionProviderImpl(
          OBPropertiesProvider.getInstance().getOpenbravoProperties());
      String sql = "select stragg(1) from dual";
      try (PreparedStatement st = connectionProvider.getPreparedStatement(sql);
          Connection connection = st.getConnection();
          ResultSet result = st.executeQuery()) {
        return true;
      }
    } catch (Exception ignore) {
    }
    return false;
  }

  private boolean is11R2orNewer() {
    String dbVersion = null;
    try {
      dbVersion = SystemInfo.getDatabaseVersion(
          new ConnectionProviderImpl(OBPropertiesProvider.getInstance().getOpenbravoProperties()));
    } catch (Exception ignore) {
    }
    if (dbVersion == null) {
      return false;
    }
    int version = Integer.parseInt(dbVersion.replaceAll("\\.", "").substring(0, 3));
    return version >= 112;
  }
}
