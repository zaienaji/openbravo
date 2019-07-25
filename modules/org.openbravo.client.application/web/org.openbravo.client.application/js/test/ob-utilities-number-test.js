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

/*global QUnit */

QUnit.module('org.openbravo.client.application1');

QUnit.test(
  'OB.Utilities.Number. ScientificToDecimal, OBMaskedToOBPlain and roundJSNumber functions',
  function() {
    QUnit.expect(7);

    var outputText = '';
    var decSeparator = '.';
    var groupSeparator = ',';

    QUnit.ok(
      (function() {
        var i;
        var successText =
          'OB.Utilities.Number.ScientificToDecimal works properly';
        var failureText =
          'OB.Utilities.Number.ScientificToDecimal failed while eval';
        var success = true;
        var normalizedDisplayFormat;
        var list = [
          ['-5E-4', '-0.0005'],
          ['-5E4', '-50000'],
          ['5E7', '50000000'],
          ['-1.056E-5', '-0.00001056'],
          ['-1.056E5', '-105600'],
          ['1.056E-5', '0.00001056'],
          ['1.056E5', '105600'],
          ['-1.2E-5', '-0.000012'],
          ['-1.2E5', '-120000'],
          ['1.2E-5', '0.000012'],
          ['1.2E5', '120000'],
          ['-3.566E-4', '-0.0003566']
        ];
        for (i = 0; i < list.length; i++) {
          normalizedDisplayFormat = OB.Utilities.Number.ScientificToDecimal(
            list[i][0],
            decSeparator
          );
          if (normalizedDisplayFormat !== list[i][1]) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              list[i][0] +
              "') === '" +
              list[i][1] +
              "' (returned: " +
              normalizedDisplayFormat +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );

    QUnit.ok(
      (function() {
        var i;
        var successText =
          'OB.Utilities.Number.OBMaskedToOBPlain works properly when the exponent of the scientific number ends with 0';
        var failureText =
          'OB.Utilities.Number.OBMaskedToOBPlain failed while eval';
        var success = true;
        var normalizedDisplayFormat;
        var list = [
          ['1.12E10', '11200000000'],
          ['1.12E-10', '0.000000000112'],
          ['-1.0564E10', '-10564000000'],
          ['1.056E-5', '0.00001056']
        ];
        for (i = 0; i < list.length; i++) {
          normalizedDisplayFormat = OB.Utilities.Number.OBMaskedToOBPlain(
            list[i][0],
            decSeparator,
            groupSeparator
          );
          if (normalizedDisplayFormat !== list[i][1]) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              list[i][0] +
              "') === '" +
              list[i][1] +
              "' (returned: " +
              normalizedDisplayFormat +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );

    QUnit.ok(
      (function() {
        var i;
        var successText =
          'OB.Utilities.Number.ScientificToDecimal works properly for the conversion of the numbers from scientific notation to decimal notation in the case of the upper case E, lower case e and if a sign is added to the exponent';
        var failureText =
          'OB.Utilities.Number.ScientificToDecimal failed while eval';
        var success = true;
        var normalizedDisplayFormat;
        var list = [
          ['1.12E10', '11200000000'],
          ['-5E20', '-500000000000000000000'],
          ['-1.056E10', '-10560000000'],
          ['1.12E+10', '11200000000'],
          ['-5E+20', '-500000000000000000000'],
          ['-1.056E+10', '-10560000000'],
          ['1.12e10', '11200000000'],
          ['-5e20', '-500000000000000000000'],
          ['-1.056e10', '-10560000000'],
          ['1.12e+10', '11200000000'],
          ['-5e+20', '-500000000000000000000'],
          ['-1.056e+10', '-10560000000']
        ];
        for (i = 0; i < list.length; i++) {
          normalizedDisplayFormat = OB.Utilities.Number.ScientificToDecimal(
            list[i][0],
            decSeparator
          );
          if (normalizedDisplayFormat !== list[i][1]) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              list[i][0] +
              "') === '" +
              list[i][1] +
              "' (returned: " +
              normalizedDisplayFormat +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );

    QUnit.ok(
      (function() {
        var i;
        var successText =
          'OB.Utilities.Number.ScientificToDecimal works properly with numbers with leading zeros';
        var failureText =
          'OB.Utilities.Number.ScientificToDecimal does not work properly with numbers with leading zeros';
        var success = true;
        var normalizedDisplayFormat0;
        var normalizedDisplayFormat1;
        var list = [
          ['03.4e-2', '3.4e-2'],
          ['03.4e+2', '3.4e+2'],
          ['03.4e2', '3.4e2'],
          ['03.4E-2', '3.4E-2'],
          ['03.4E+2', '3.4E+2'],
          ['03.4E2', '3.4E2']
        ];
        for (i = 0; i < list.length; i++) {
          normalizedDisplayFormat0 = OB.Utilities.Number.ScientificToDecimal(
            list[i][0],
            decSeparator
          );
          normalizedDisplayFormat1 = OB.Utilities.Number.ScientificToDecimal(
            list[i][1],
            decSeparator
          );
          if (normalizedDisplayFormat0 !== normalizedDisplayFormat1) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              list[i][0] +
              "') === '" +
              normalizedDisplayFormat1 +
              "' (returned: " +
              normalizedDisplayFormat0 +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );

    QUnit.ok(
      (function() {
        var i;
        var successText =
          'OB.Utilities.Number.ScientificToDecimal works properly';
        var failureText =
          'OB.Utilities.Number.ScientificToDecimal failed while eval';
        var success = true;
        var normalizedDisplayFormat;
        var list = [
          ['1.020050045E7', '10200500.45'],
          ['-1.020050045E7', '-10200500.45'],
          ['1.020050000E7', '10200500.00'],
          ['-1.020050000E7', '-10200500.00']
        ];
        for (i = 0; i < list.length; i++) {
          normalizedDisplayFormat = OB.Utilities.Number.ScientificToDecimal(
            list[i][0],
            decSeparator
          );
          if (normalizedDisplayFormat !== list[i][1]) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              list[i][0] +
              "') === '" +
              list[i][1] +
              "' (returned: " +
              normalizedDisplayFormat +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );

    QUnit.ok(
      (function() {
        var i;
        var successText = 'OB.Utilities.Number.roundJSNumber works properly';
        var failureText = 'OB.Utilities.Number.roundJSNumber failed while eval';
        var success = true;
        var normalizedDisplayFormat;
        var list1 = [
          [0.145, 2],
          [1.145, 2],
          [10.145, 2],
          [14.499999999999998, 2]
        ];
        var list2 = [[0.15], [1.15], [10.15], [14.5]];
        for (i = 0; i < list1.length; i++) {
          normalizedDisplayFormat = OB.Utilities.Number.roundJSNumber(
            list1[i][0],
            list1[i][1]
          );
          if (normalizedDisplayFormat !== list2[i][0]) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              'number ' +
              list1[i][0] +
              ' with ' +
              list1[i][1] +
              'decimals' +
              "') === '" +
              list2[i][0] +
              "' (returned: " +
              normalizedDisplayFormat +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );

    QUnit.ok(
      (function() {
        var i;
        var successText =
          'OB.Utilities.Number.roundJSNumber works properly when the parameter is NaN';
        var failureText = 'OB.Utilities.Number.roundJSNumber failed while eval';
        var success = true;
        var normalizedDisplayFormat;
        var list = [['a', 2], ['12a', 2]];
        for (i = 0; i < list.length; i++) {
          normalizedDisplayFormat = OB.Utilities.Number.roundJSNumber(
            list[i][0],
            list[i][1]
          );
          if (!isNaN(normalizedDisplayFormat)) {
            success = false;
            failureText =
              failureText +
              " normalizeDisplayFormat('" +
              list[i][0] +
              "') === '" +
              "' (returned: " +
              normalizedDisplayFormat +
              ') &';
          }
        }
        if (success) {
          outputText = successText;
        } else {
          failureText = failureText.substring(0, failureText.length - 2);
          outputText = failureText;
        }
        return success;
      })(),
      outputText
    );
  }
);
