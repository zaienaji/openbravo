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
 * All portions are Copyright (C) 2008-2018 Openbravo SLU
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.openbravo.advpaymentmngt.test.FundsTransferTest;
import org.openbravo.base.weld.test.testinfrastructure.CdiInfrastructure;
import org.openbravo.erpCommon.info.ClassicSelectorTest;
import org.openbravo.test.centralbroker.CentralBrokerTest;
import org.openbravo.test.dal.ComputedColumnsTest;
import org.openbravo.test.dal.DalConnectionProviderTest;
import org.openbravo.test.dal.DalCopyTest;
import org.openbravo.test.dal.DalPerformanceProxyTest;
import org.openbravo.test.dal.DalQueryTest;
import org.openbravo.test.dal.DalStoredProcedureTest;
import org.openbravo.test.dal.DalTest;
import org.openbravo.test.dal.DalUtilTest;
import org.openbravo.test.dal.DynamicEntityTest;
import org.openbravo.test.dal.HiddenUpdateTest;
import org.openbravo.test.dal.IssuesTest;
import org.openbravo.test.dal.MappingGenerationTest;
import org.openbravo.test.dal.ReadByNameTest;
import org.openbravo.test.dal.ValidationTest;
import org.openbravo.test.expression.EvaluationTest;
import org.openbravo.test.expression.OBBindingsTest;
import org.openbravo.test.model.ClassLoaderTest;
import org.openbravo.test.model.IndexesTest;
import org.openbravo.test.model.OneToManyTest;
import org.openbravo.test.model.RuntimeModelTest;
import org.openbravo.test.model.TrlColumnsOraTypeTest;
import org.openbravo.test.security.AccessLevelTest;
import org.openbravo.test.security.AllowedOrganizationsTest;
import org.openbravo.test.security.EntityAccessTest;
import org.openbravo.test.security.OBContextCollectionsTest;
import org.openbravo.test.security.PasswordStrengthCheckerTest;
import org.openbravo.test.security.WritableReadableOrganizationClientTest;
import org.openbravo.test.xml.DefaultsDataset;
import org.openbravo.test.xml.EntityXMLImportTestBusinessObject;
import org.openbravo.test.xml.EntityXMLImportTestReference;
import org.openbravo.test.xml.EntityXMLImportTestSingle;
import org.openbravo.test.xml.EntityXMLImportTestWarning;
import org.openbravo.test.xml.EntityXMLIssues;
import org.openbravo.test.xml.UniqueConstraintImportTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({

    // security
    EntityAccessTest.class, //
    AccessLevelTest.class, //
    AllowedOrganizationsTest.class, //
    WritableReadableOrganizationClientTest.class, //
    OBContextCollectionsTest.class, //
    PasswordStrengthCheckerTest.class, //

    // dal
    HiddenUpdateTest.class, //
    MappingGenerationTest.class, //
    ValidationTest.class, //
    DynamicEntityTest.class, //
    DalTest.class, //
    CentralBrokerTest.class, //
    DalUtilTest.class, //
    IssuesTest.class, //
    DalQueryTest.class, //
    DalConnectionProviderTest.class, //
    DalCopyTest.class, //
    DalStoredProcedureTest.class, //
    ReadByNameTest.class, //
    DalPerformanceProxyTest.class, //
    ComputedColumnsTest.class, //

    // model
    RuntimeModelTest.class, //
    OneToManyTest.class, //
    ClassLoaderTest.class, //
    IndexesTest.class, //
    TrlColumnsOraTypeTest.class,

    // expression
    EvaluationTest.class, //
    OBBindingsTest.class,

    // xml
    EntityXMLImportTestBusinessObject.class, //
    EntityXMLImportTestReference.class, //
    EntityXMLImportTestSingle.class, //
    EntityXMLImportTestWarning.class, //
    EntityXMLIssues.class, //
    UniqueConstraintImportTest.class, //
    DefaultsDataset.class, //
    ClassicSelectorTest.class,

    // cdi
    CdiInfrastructure.class,

    // Funds transfer
    FundsTransferTest.class

})
public class AllTests {
}
