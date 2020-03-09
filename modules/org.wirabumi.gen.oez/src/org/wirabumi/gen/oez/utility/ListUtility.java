package org.wirabumi.gen.oez.utility;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.data.Sqlc;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Tab;

public class ListUtility {

  protected static void generateVarsParams(Entity entity, String columnProperty, String columnValue) {
    try {
      String columnId = entity.getProperty(columnProperty).getColumnId();
      Column column = OBDal.getInstance().get(Column.class, columnId);
      String dbColumnName = column.getDBColumnName();
      String key = "inp" + Sqlc.TransformaNombreColumna(dbColumnName);
      RequestContext.get().setRequestParameter(key, columnValue);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Create Session Variable For Validation
   * 
   * @param object
   *          Class Object to Load The ID OF Object
   * @param entity
   *          Entity to Get Field To Validate
   */
  public static void setPropertySession(BaseOBObject object, Entity entity) {
    try {
      List<Property> property = entity.getProperties();
      for (Property prop : property) {
        String propert = prop.getName();
        String colName = prop.getColumnName();
        Object objct = object.get(propert);
        String value = " ";
        if (objct != null) {
          if (objct instanceof BaseOBObject) {
            BaseOBObject obj = (BaseOBObject) object.get(propert);
            value = obj.getId().toString();
          } else {
            value = objct.toString();
          }
        } else {
          value = "";
        }
        generateVarsParams(entity, propert, value);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void getValidList(ConnectionProvider connection, VariablesSecureApp vars,
      Entity entity, String tabID, String columnProperty) {
    try {
      OBContext.setAdminMode();
      String columnId = entity.getProperty(columnProperty).getColumnId();
      Column column = OBDal.getInstance().get(Column.class, columnId);
      Tab tab = OBDal.getInstance().get(Tab.class, tabID);
      String windowId = tab.getWindow().getId();
      String colName = column.getDBColumnName().toUpperCase();
      String validationId = column.getValidation() == null ? "" : column.getValidation().getId();
      String referenceType = column.getReference() == null ? "" : column.getReference().getName()
          .toUpperCase();
      String referenceId = column.getReferenceSearchKey() == null ? "" : column
          .getReferenceSearchKey().getId();
      ComboTableData comboTableData = new ComboTableData(vars, connection, referenceType, colName,
          referenceId, validationId, Utility.getContext(connection, vars, "#AccessibleOrgTree",
              windowId), Utility.getContext(connection, vars, "#User_Client", windowId), 0);
      Utility.fillSQLParameters(connection, vars, null, comboTableData, windowId, "");
      FieldProvider[] fpArray = comboTableData.select(false);
      JSONObject valueMap = new JSONObject();
      for (FieldProvider fp : fpArray) {
        String key = fp.getField("id");
        String value = fp.getField("name");
        valueMap.put(key, value);
      }
      System.out.println(valueMap + "");
      OBContext.restorePreviousMode();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Create Sesion Paramates
   * 
   * @param object
   * @param entity
   * @param property
   */
  public static void setParamsSesion(BaseOBObject object, Entity entity, Property property,
      Object refValue) {
    try {
      String propert = property.getName();
      String value = refValue == null ? "" : refValue.toString();
      if (refValue == null) {
        Object objct = object.get(propert);
        if (objct != null) {
          if (objct instanceof BaseOBObject) {
            BaseOBObject obj = (BaseOBObject) object.get(propert);
            value = value.equals("") ? obj.getId().toString() : value;
          } else {
            value = value.equals("") ? objct.toString() : value;
          }
        } else {
          value = value.equals("") ? "" : value;
        }
      }
      generateVarsParams(entity, propert, value);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Get Valid List Of Object By Validation Rule
   * 
   * @param connection
   *          Database Conection
   * @param vars
   *          Current Vars
   * @param destEntity
   *          Entity to lookup
   * @param tabID
   *          Tab For Geting Window Field
   * @param destinaTion
   *          Column To validate
   * @param Key
   *          lookuop Value To lookup by identifer exclude Reference List
   * @param Identifier
   *          Lookup Key field For Lookuping Object
   */
  public static Object getValidList(ConnectionProvider connection, VariablesSecureApp vars,
      String tabID, Entity destEntity, String destinaTion, String keyLookup,
      String columnNameIdentify) {
    Object key = null;
    try {

      OBContext.setAdminMode();
      String columnId = destEntity.getProperty(destinaTion).getColumnId();
      Column column = OBDal.getInstance().get(Column.class, columnId);
      Tab tab = OBDal.getInstance().get(Tab.class, tabID);
      String windowId = tab.getWindow().getId();
      String colName = column.getDBColumnName().toUpperCase();
      String validationId = column.getValidation() == null ? "" : column.getValidation().getId();
      String referenceType = column.getReference() == null ? " " : column.getReference().getName()
          .toUpperCase();
      String referenceId = column.getReferenceSearchKey() == null ? "" : column
          .getReferenceSearchKey().getId();
      ComboTableData comboTableData = new ComboTableData(vars, connection, referenceType, colName,
          referenceId, validationId, Utility.getContext(connection, vars, "#AccessibleOrgTree",
              windowId), Utility.getContext(connection, vars, "#User_Client", windowId), 0);
      Utility.fillSQLParameters(connection, vars, null, comboTableData, windowId, "");
      FieldProvider[] fpArray = comboTableData.select(false);
      for (FieldProvider fp : fpArray) {
        String value = fp.getField("name");
        if (referenceType.equalsIgnoreCase("LIST")) {
          if (value.equalsIgnoreCase(keyLookup)) {
            key = fp.getField("id");
          }
          continue;
        } else {
          if (key == null) {
            String tableReferenceId = referenceType.equalsIgnoreCase("TABLE") ? column
                .getReferenceSearchKey().getName() : colName.replaceFirst("_ID", "");
            Entity referenceEntity = ModelProvider.getInstance().getEntityByTableName(
                tableReferenceId);
            Property columnLook = referenceEntity.getPropertyByColumnName(columnNameIdentify);
            String identifier = columnLook.getGetterSetterName();
            key = getObjectReferenceByIdentifier(referenceEntity.getName(), identifier, keyLookup);
          }
          continue;
        }
      }
      OBContext.restorePreviousMode();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return key;
  }

  public static String getListValueByName(Entity entity, String columnProperty, String actualName,
      String language) {
    String value = "";
    try {
      String columnId = entity.getProperty(columnProperty).getColumnId();
      Column column = OBDal.getInstance().get(Column.class, columnId);
      String referenceId = column.getReferenceSearchKey().getId();
      String lowKey = actualName.toLowerCase();
      final String strQuery = "select refList.searchKey  from ADListTrl listTrl"
          + " left outer join listTrl.listReference refList"
          + " inner join refList.reference reference" + " inner join listTrl.language languageTrl "
          + " where reference.id=?"
          + " and ((lower(listTrl.name)=? and languageTrl.language=?) or lower(refList.name)=?)"
          + " and refList.active=true";

      final Query query = OBDal.getInstance().getSession().createQuery(strQuery);
      query.setParameter(0, referenceId);
      query.setParameter(1, lowKey);
      query.setParameter(2, language);
      query.setParameter(3, lowKey);
      final ScrollableResults result = query.scroll(ScrollMode.FORWARD_ONLY);
      if (result.next()) {
        value = (String) result.get()[0];
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return value;
  }

  /**
   * Get Object By Identifier
   * 
   * @param entityName
   *          String Entity;
   * @param identifier
   *          Define identifier/ Key to lookup
   * @param lookupValue
   *          Define value to lookup
   * @return
   */
  public static BaseOBObject getObjectReferenceByIdentifier(String entityName, String identifier,
      String lookupValue) {
    BaseOBObject reference = null;
    try {
      if (lookupValue == null) {
        return null;
      }
      OBCriteria<BaseOBObject> object = OBDal.getInstance().createCriteria(entityName);
      object.add(Restrictions.eq(identifier, lookupValue).ignoreCase());
      List<BaseOBObject> OBObject = object.list();
      if (OBObject.size() > 0) {
        reference = OBObject.get(0);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return reference;
  }
  /*
   * // nuat validasi di posisi ini final Entity candidateEntity =
   * ModelProvider.getInstance().getEntityByTableName( CostCenter.TABLE_NAME); String entity =
   * candidateEntity.getName(); String columnIDentifer = CostCenter.PROPERTY_NAME; String
   * valueLookup = "Jakarta"; BaseOBObject object =
   * ListUtility.getObjectReferenceByIdentifier(entity, columnIDentifer, valueLookup); String tabID
   * = (String) this.bundled.getParams().get("tabId"); ListUtility.setPropertySession(eCandidates,
   * candidateEntity); ListUtility.getValidList(this.bundled.getConnection(), vars, candidateEntity,
   * tabID, hris_i_employee_candidate.PROPERTY_POSITION);
   * ListUtility.getValidList(this.bundled.getConnection(), vars, candidateEntity, tabID,
   * hris_i_employee_candidate.PROPERTY_EDUCATIONDICIPLINE);
   * ListUtility.getValidList(this.bundled.getConnection(), vars, candidateEntity, tabID,
   * hris_i_employee_candidate.PROPERTY_NATIONALITY);
   */

}
