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
 * All portions are Copyright (C) 2009-2010 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.base.model.domaintype;

/**
 * The type of a column which can only have a value from a pre-defined set.
 * 
 * @author mtaal
 */

public class StringEnumerateDomainType extends BaseEnumerateDomainType<String> {

  /**
   * As a standard only a string/varchar column can have enumerates.
   * 
   * @return class of {@link String}.
   */
  @Override
  public Class<?> getPrimitiveType() {
    return String.class;
  }

  @Override
  public Object createFromString(String strValue) {
    if (strValue == null || strValue.length() == 0) {
      return null;
    }
    return strValue;
  }

  @Override
  public String getXMLSchemaType() {
    return "ob:string";
  }
}
