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
 * All portions are Copyright (C) 2010-2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

/*global QUnit */

QUnit.module('org.openbravo.client.application');

QUnit.test('Property Store Exists', function() {
  QUnit.expect(2);
  QUnit.ok(OB.PropertyStore, 'PropertyStore is present');
  QUnit.ok(!OB.PropertyStore.get('abc'), 'Test property is not present (okay)');
});

QUnit.test('Set/Get Property', function() {
  QUnit.expect(2);

  var propName = 'CCU';
  var testValue = 'testValue';
  var propValue = OB.PropertyStore.get(propName);
  QUnit.ok(propValue, 'CCU is present, value is ' + propValue);

  OB.PropertyStore.set(propName, testValue);
  propValue = OB.PropertyStore.get(propName);
  QUnit.equal(propValue, testValue, 'Equal values');
  // clear the test property
  // with a short delay to make sure that the previous set does not interfere
  // on the server
  isc.Timer.setTimeout(function() {
    OB.PropertyStore.set(propName, 'Y');
  }, 1000);
});
