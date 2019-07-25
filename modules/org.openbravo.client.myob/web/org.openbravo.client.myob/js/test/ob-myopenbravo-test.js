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
 * All portions are Copyright (C) 2010-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

/*global QUnit */

QUnit.module('org.openbravo.client.myob');

var d = {
  eventType: '',
  context: {
    adminMode: false
  }
};

OB.MyOB = OB.MyOB || {};

function reloadWidgetsBeforeTest(callback) {
  var post = isc.addProperties({}, d, {
    eventType: 'RELOAD_WIDGETS',
    widgets: []
  });
  OB.RemoteCallManager.call(
    'org.openbravo.client.myob.MyOpenbravoActionHandler',
    post,
    {},
    function(rpcResponse, data, rpcRequest) {
      OB.MyOB.widgets = data && data.widgets;

      callback(data);
    }
  );
}

function checkMissingDbInstanceId(w) {
  var i,
    widgets = w || OB.MyOB.widgets;
  for (i = 0; i < widgets.length; i++) {
    if (!widgets[i].dbInstanceId) {
      // empty string is 'falsy'
      return true;
    }
  }
  return false;
}

QUnit.asyncTest('Add widget', function() {
  QUnit.expect(2);
  reloadWidgetsBeforeTest(function(data) {
    var post = isc.addProperties({}, d, {
        eventType: 'WIDGET_ADDED',
        widgets: OB.MyOB.widgets
      }),
      widget = isc.addProperties({}, OB.MyOB.widgets[0]);

    widget.rowNum += 1;
    widget.dbInstanceId = '';

    OB.MyOB.widgets.push(widget);
    OB.RemoteCallManager.call(
      'org.openbravo.client.myob.MyOpenbravoActionHandler',
      post,
      {},
      function(rpcResponse, data, rpcRequest) {
        QUnit.strictEqual(data.message.type, 'Success', 'Widget added');

        OB.MyOB.widgets =
          data && data.widgets ? eval(data.widgets) : OB.MyOB.widgets; // refreshing widgets
        QUnit.ok(
          !checkMissingDbInstanceId(),
          'All widgets have a dbInstanceId'
        );

        QUnit.start(); // restart the flow
      }
    );
  });
});

// skipping this test because it is unstable depending on factors like
// instance type (community/professional), number of executions, etc.
// TODO: fix it properly to make it stable
QUnit.skip('Move widget', function() {
  var post = isc.addProperties({}, d, {
      eventType: 'WIDGET_MOVED',
      widgets: OB.MyOB.widgets
    }),
    tmp = {
      colNum: 0,
      rowNum: 0
    },
    w1 = OB.MyOB.widgets[0],
    w2 = OB.MyOB.widgets[1];

  if (!w1 || !w2) {
    QUnit.ok(true);
    QUnit.start(); // skip the test
    return;
  }

  QUnit.expect(3);

  tmp.colNum = w1.colNum;
  tmp.rowNum = w1.rowNum;
  w1.colNum = w2.colNum;
  w1.rowNum = w2.colNum;
  w2.colNum = tmp.colNum;
  w2.rowNum = tmp.rowNum;

  QUnit.ok(
    !checkMissingDbInstanceId(post.widgets),
    'All posted widgets have a dbInstanceId'
  );

  OB.RemoteCallManager.call(
    'org.openbravo.client.myob.MyOpenbravoActionHandler',
    post,
    {},
    function(rpcResponse, data, rpcRequest) {
      QUnit.strictEqual(data.message.type, 'Success', 'Widget moved');

      OB.MyOB.widgets =
        data && data.widgets ? eval(data.widgets) : OB.MyOB.widgets; // refreshing widgets
      QUnit.ok(!checkMissingDbInstanceId(), 'All widgets have a dbInstanceId');

      QUnit.start(); // restart the flow
    }
  );
});

QUnit.asyncTest('Remove widget', function() {
  QUnit.expect(2);
  reloadWidgetsBeforeTest(function(data) {
    var post = isc.addProperties({}, d, {
      eventType: 'WIDGET_REMOVED',
      widgets: OB.MyOB.widgets
    });

    OB.MyOB.widgets.splice(-1, 1);

    OB.RemoteCallManager.call(
      'org.openbravo.client.myob.MyOpenbravoActionHandler',
      post,
      {},
      function(rpcResponse, data, rpcRequest) {
        QUnit.strictEqual(data.message.type, 'Success', 'Widget removed');

        OB.MyOB.widgets =
          data && data.widgets ? eval(data.widgets) : OB.MyOB.widgets; // refreshing widgets
        QUnit.ok(
          !checkMissingDbInstanceId(),
          'All widgets have a dbInstanceId'
        );

        QUnit.start(); // restart the flow
      }
    );
  });
});

QUnit.asyncTest('Get user widgets', function() {
  QUnit.expect(2);
  reloadWidgetsBeforeTest(function(data) {
    QUnit.strictEqual(data.message.type, 'Success', 'Widgets reloaded');

    QUnit.ok(!checkMissingDbInstanceId(), 'All widgets have a dbInstanceId');

    QUnit.start(); // restart the flow
  });
});
