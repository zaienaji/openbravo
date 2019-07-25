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

QUnit.test('Basic requirements', function() {
  QUnit.expect(2);
  QUnit.ok(isc, 'isc object is present');
  QUnit.ok(document.getElementById, 'getElementById');
});

QUnit.test('Create Canvas', function() {
  var canvasID = 'myCanvas',
    canvas,
    createCanvas;

  QUnit.expect(3);

  createCanvas = function(isc) {
    var c = isc.Canvas.newInstance({
      ID: canvasID,
      width: '100%',
      height: '100%'
    });
    return c;
  };

  canvas = createCanvas(isc);
  canvas.setBackgroundColor('blue');

  QUnit.ok(typeof canvas !== 'undefined', 'Canvas created');

  QUnit.ok(
    (function(c) {
      return c.height !== 0 && c.width !== 0;
    })(canvas),
    'Canvas height and width are not zero'
  );

  QUnit.ok(isc.Log.getStackTrace() !== undefined, 'getStackTrace()');
});
