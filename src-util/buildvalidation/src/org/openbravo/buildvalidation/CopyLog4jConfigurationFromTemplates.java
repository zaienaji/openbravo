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
 * All portions are Copyright (C) 2018-2019 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.buildvalidation;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * This script will be executed only when migrating from a version which still supports log4j 1.x
 * and copies all new configuration files from the template
 */
public class CopyLog4jConfigurationFromTemplates extends BuildValidation {

  private static final String CONFIG_DIR = "/config/";
  private static final String TEST_SRC_DIR = "/src-test/src/";
  private static final String LOG4J_CONF_FILE = "log4j2.xml";
  private static final String LOG4J_WEB_CONF_FILE = "log4j2-web.xml";
  private static final String LOG4J_TEST_CONF_FILE = "log4j2-test.xml";

  @Override
  public List<String> execute() {
    try {
      String sourcePath = getSourcePath();
      copyFromTemplateFile(sourcePath + CONFIG_DIR + LOG4J_CONF_FILE);
      copyFromTemplateFile(sourcePath + CONFIG_DIR + LOG4J_WEB_CONF_FILE);
      copyFromTemplateFile(sourcePath + TEST_SRC_DIR + LOG4J_TEST_CONF_FILE);
    } catch (Exception e) {
      System.out.println(
          "Copy log4j config from templates failed: Log4j may not be properly configured. Please check your configuration files manually.");
    }

    return new ArrayList<>();
  }

  private void copyFromTemplateFile(String targetPath) throws Exception {
    Path source = Paths.get(targetPath + ".template");
    Path target = Paths.get(targetPath);

    if (Files.notExists(target)) {
      Files.copy(source, target);
      System.out.println(targetPath
          + " is copied from template file. Please check this configuration is correct.");
    }
  }

  /**
   * Get the source path using the user.dir System Property. Navigates two folders backwards and
   * checks the config directory is available to ensure the directory is an Openbravo instance,
   * throwing an exception otherwise
   * 
   * @return String the source path
   * @throws NoSuchFileException
   *           when the source path directory is not valid
   */
  private String getSourcePath() throws NoSuchFileException {
    String userDir = System.getProperty("user.dir");
    Path sourcePath = Paths.get(userDir, "/../..").normalize();

    Path configDir = sourcePath.resolve("config");
    if (Files.exists(configDir)) {
      return sourcePath.toString();
    }

    System.out.println(String.format("Config folder not found: %s", configDir.toString()));
    throw new NoSuchFileException(configDir.toString());
  }

}
