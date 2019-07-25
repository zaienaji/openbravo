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
 * All portions are Copyright (C) 2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.test.views;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.junit.Before;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.application.ViewComponent;
import org.openbravo.erpCommon.obps.ActivationKey;
import org.openbravo.erpCommon.obps.ActivationKey.FeatureRestriction;
import org.openbravo.test.base.mock.HttpServletRequestMock;

/**
 * Base test class that can be extended when testing features related with the generation of views.
 */
public abstract class ViewGenerationTest extends WeldBaseTest {

  @Inject
  private ViewComponent vc;

  @Before
  public void setRequestContext() {
    HttpServletRequestMock.setRequestMockInRequestContext();
  }

  /**
   * Generates the view definition of a window
   * 
   * @param viewId
   *          The ID of the window to be generated
   * 
   * @return a String containing the view definition
   */
  protected Optional<String> generateView(String viewId) {
    ActivationKey ak = ActivationKey.getInstance();
    if (ak.hasLicenseAccess("MW", viewId) != FeatureRestriction.NO_RESTRICTION) {
      return Optional.empty();
    }
    Map<String, Object> p = new HashMap<>(1);
    p.put("viewId", viewId);
    vc.setParameters(p);
    return Optional.of(vc.generate());
  }
}
