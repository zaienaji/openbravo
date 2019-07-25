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
 * All portions are Copyright (C) 2010-2017 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

/*global QUnit */

QUnit.module('org.openbravo.client.application.navigationbarcomponents');

QUnit.test('Basic requirements', function() {
  QUnit.expect(1);
  QUnit.ok(OB.RecentUtilities, 'recent utilities defined');
});

QUnit.test('Test user info data read', function(assert) {
  QUnit.expect(15);

  QUnit.ok(OB.User.userInfo.language, 'Language present');
  QUnit.ok(OB.User.userInfo.language.value, 'Language value present');
  QUnit.ok(OB.User.userInfo.language.valueMap, 'Language valueMap present');

  QUnit.ok(OB.User.userInfo.initialValues.role, 'Initial role value set');
  QUnit.ok(OB.User.userInfo.initialValues.client, 'Initial client value set');
  QUnit.ok(
    OB.User.userInfo.initialValues.organization,
    'Initial organization value set'
  );
  QUnit.ok(
    OB.User.userInfo.initialValues.language,
    'Initial language value set'
  );

  QUnit.ok(OB.User.userInfo.role, 'Role set');
  QUnit.ok(OB.User.userInfo.role.value, 'Role value set');
  QUnit.ok(OB.User.userInfo.role.valueMap, 'Role valueMap set');
  QUnit.ok(OB.User.userInfo.role.roles, 'Role info set');
  QUnit.ok(
    OB.User.userInfo.role.roles.length > 0,
    'More than one role present'
  );
  QUnit.ok(OB.User.userInfo.role.roles[0].id, 'Role id set');
  QUnit.ok(
    OB.User.userInfo.role.roles[0].organizationValueMap,
    'Role org value map set'
  );
  QUnit.ok(
    OB.User.userInfo.role.roles[0].warehouseOrgMap,
    'Role wh value map set'
  );
});
