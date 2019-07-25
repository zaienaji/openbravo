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
 * All portions are Copyright (C) 2009-2018 Openbravo SLU
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.openbravo.erpCommon.info.ClassicSelectorTest;
import org.openbravo.test.centralbroker.CentralBrokerTest;
import org.openbravo.test.dal.AdminContextTest;
import org.openbravo.test.dal.DalConnectionProviderTest;
import org.openbravo.test.dal.DalPerformanceProxyTest;
import org.openbravo.test.dal.DalStoredProcedureTest;
import org.openbravo.test.dal.DalTest;
import org.openbravo.test.dal.DalUtilTest;
import org.openbravo.test.dal.OBContextTest;
import org.openbravo.test.dal.ValidationTest;
import org.openbravo.test.model.ClassLoaderTest;
import org.openbravo.test.model.UniqueConstraintTest;
import org.openbravo.test.modularity.DBPrefixTest;
import org.openbravo.test.preference.PreferenceTest;
import org.openbravo.test.security.AccessLevelTest;
import org.openbravo.test.security.AllowedOrganizationsTest;
import org.openbravo.test.security.EntityAccessTest;
import org.openbravo.test.security.OBContextCollectionsTest;
import org.openbravo.test.security.PasswordStrengthCheckerTest;
import org.openbravo.test.security.WritableReadableOrganizationClientTest;
import org.openbravo.test.system.ErrorTextParserIntegrationTest;
import org.openbravo.test.system.ErrorTextParserTest;
import org.openbravo.test.system.ImportEntrySizeTest;
import org.openbravo.test.system.Issue29934Test;
import org.openbravo.test.system.SystemServiceTest;
import org.openbravo.test.system.SystemValidatorTest;
import org.openbravo.test.system.TestInfrastructure;
import org.openbravo.test.xml.DefaultsDataset;
import org.openbravo.test.xml.EntityXMLImportTestBusinessObject;
import org.openbravo.test.xml.EntityXMLImportTestReference;
import org.openbravo.test.xml.EntityXMLImportTestSingle;
import org.openbravo.test.xml.EntityXMLImportTestWarning;
import org.openbravo.test.xml.EntityXMLIssues;
import org.openbravo.test.xml.UniqueConstraintImportTest;

/**
 * This test suite should only contain test cases which are quick to run. This makes it possible as
 * a developer to run tests in between without waiting to long for results. Testcases which should
 * not be here is for example the import of a complete client.
 * 
 * NOTE: this suite should not contact test classes which have side-effects (change the database
 * without cleaning up).
 * 
 * @author mtaal
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({
    // dal
    DalPerformanceProxyTest.class, //
    DalTest.class, //
    CentralBrokerTest.class, //
    DalUtilTest.class, //
    DalConnectionProviderTest.class, //
    ValidationTest.class, //
    OBContextTest.class, //
    DalStoredProcedureTest.class, //
    AdminContextTest.class,

    // model
    UniqueConstraintTest.class, //
    ClassLoaderTest.class,

    // modularity
    DBPrefixTest.class,

    // security
    AccessLevelTest.class, //
    AllowedOrganizationsTest.class, //
    EntityAccessTest.class, //
    WritableReadableOrganizationClientTest.class, //
    OBContextCollectionsTest.class, //
    PasswordStrengthCheckerTest.class, //

    // system
    SystemServiceTest.class, //
    SystemValidatorTest.class, //
    ErrorTextParserTest.class, //
    ErrorTextParserIntegrationTest.class, //
    TestInfrastructure.class, //
    Issue29934Test.class, //
    ImportEntrySizeTest.class, //

    // xml
    EntityXMLImportTestBusinessObject.class, //
    EntityXMLImportTestReference.class, //
    EntityXMLImportTestSingle.class, //
    EntityXMLImportTestWarning.class, //
    EntityXMLIssues.class, //
    UniqueConstraintImportTest.class, //
    DefaultsDataset.class, //

    // preferences
    PreferenceTest.class, //
    ClassicSelectorTest.class })
public class AllQuickAntTaskTests {
}
