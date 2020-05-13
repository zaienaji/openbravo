/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2014-2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.client.kernel.reference;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.base.session.OBPropertiesProvider;

/**
 * Implementation of the absolute date time ui definition.
 * 
 * @author dbaz
 */
public class AbsoluteDateTimeUIDefinition extends DateUIDefinition {
  private SimpleDateFormat dateFormat = null;

  @Override
  public String getParentType() {
    return "datetime";
  }

  @Override
  public String getFormEditorType() {
    return "OBAbsoluteDateTimeItem";
  }

  @Override
  protected String getClientFormatObject() {
    return "OB.Format.dateTime";
  }

  @Override
  protected SimpleDateFormat getClassicFormat() {
    if (dateFormat == null) {
      String pattern = OBPropertiesProvider.getInstance()
          .getOpenbravoProperties()
          .getProperty("dateTimeFormat.java");
      dateFormat = new SimpleDateFormat(pattern);
      dateFormat.setLenient(true);
    }
    return dateFormat;
  }

  @Override
  public synchronized Object createFromClassicString(String value) {
    try {
      if (value == null || value.length() == 0 || value.equals("null")) {
        return null;
      }
      if (value.contains("T")) {
        return value;
      }
      final Date date = getClassicFormat().parse(value);
      return ((PrimitiveDomainType) getDomainType()).convertToString(date);
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

}
