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
 * All portions are Copyright (C) 2016-2018 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.base;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Checks Openbravo is deployed on a supported Server version, in case it is not, an error message
 * is logged and deployment is stopped. Currently, checks are performed only for Tomcat.
 * 
 * @author alostale
 *
 */
public class ServerVersionChecker implements ServletContextListener {
  private static final int MINIMUM_TOMCAT_VERSION = 7;

  private static final Logger log = LogManager.getLogger();

  private static String serverName;
  private static String serverVersion;

  @Override
  public void contextInitialized(ServletContextEvent event) {

    String serverInfo = event.getServletContext().getServerInfo();
    log.debug("Server: " + serverInfo);

    setServerInfo(serverInfo);

    if (!isRunningOnTomcat(serverInfo)) {
      log.info("Running on " + serverInfo);
      // we only check Tomcat
      return;
    }

    Integer majorVersion = getMajorVersion(serverInfo);
    if (majorVersion == null) {
      log.info("Unknown Tomcat version " + serverInfo);
      return;
    }

    if (majorVersion.compareTo(MINIMUM_TOMCAT_VERSION) < 0) {
      log.error(
          "The minimum Tomcat version required deploy Openbravo is {}. Trying to deploy it in {} is not allowed. Please, upgrade Tomcat.",
          MINIMUM_TOMCAT_VERSION, serverInfo);
      System.exit(1);
    }
  }

  private void setServerInfo(String serverInfo) {
    try {
      Matcher versionMatcher = Pattern.compile("([^\\d/]*)[/]?([\\d\\.]*)").matcher(serverInfo);
      if (versionMatcher.find()) {
        serverName = versionMatcher.group(1);
        serverVersion = versionMatcher.group(2);
      }
    } catch (Exception ignore) {
    }
  }

  private boolean isRunningOnTomcat(String serverInfo) {
    return serverInfo.toLowerCase().contains("tomcat");
  }

  private Integer getMajorVersion(String serverInfo) {
    try {
      Matcher versionMatcher = Pattern.compile("[^\\d]*([\\d]*)").matcher(serverInfo);
      if (versionMatcher.find()) {
        return Integer.valueOf(versionMatcher.group(1));
      }
    } catch (Exception ignore) {
    }

    return null;
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
  }

  /** Returns current Servlet Container's name (ie. "Apache Tomcat") */
  public static String getServerName() {
    return serverName;
  }

  /** Returns current Servlet Container's full version (ie. "8.5.31") */
  public static String getServerVersion() {
    return serverVersion;
  }

}
