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
 * All portions are Copyright (C) 2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.base.weld.test.testinfrastructure;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import javax.inject.Inject;

import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

/**
 * Test cases for cdi infrastructure. Checking Arquillian works fine and it is possible to inject
 * beans.
 * 
 * @author alostale
 *
 */
public class CdiInfrastructure extends WeldBaseTest {

  @Inject
  private ApplicationScopedBean applicationBean;

  @Inject
  private SessionScopedBean sessionBean;

  @Inject
  private RequestScopedBean requestBean;

  /** beans are correctly injected */
  @Test
  public void beansAreInjected() {
    assertThat("application bean is injected", applicationBean, notNullValue());
    assertThat("session bean is injected", sessionBean, notNullValue());
    assertThat("request bean is injected", requestBean, notNullValue());
  }

  /** starts application and session scopes */
  @Test
  @InSequence(1)
  public void start() {
    applicationBean.setValue("application");
    sessionBean.setValue("session");
    requestBean.setValue("request");

    assertThat(applicationBean.getValue(), equalTo("application"));
    assertThat(sessionBean.getValue(), equalTo("session"));
    assertThat(requestBean.getValue(), equalTo("request"));
  }

  /** application and session scopes are preserved but not request scope */
  @Test
  @InSequence(2)
  public void applicationAndSessionShouldBeKept() {
    assertThat(applicationBean.getValue(), equalTo("application"));
    assertThat(sessionBean.getValue(), equalTo("session"));
    assertThat(requestBean.getValue(), nullValue());
  }
}
