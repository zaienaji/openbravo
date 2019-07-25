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
 * All portions are Copyright (C) 2010-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.client.application;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.util.Check;
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.window.ApplicationDictionaryCachedStructures;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.JsonUtils;

/**
 * JS - Java binding to use in JavaScript expressions.
 * 
 * @author iperdomo
 */
public class OBBindings {

  private static final Logger log = LogManager.getLogger();

  private OBContext context;
  private Map<String, String> requestMap;
  private HttpSession httpSession;
  private SimpleDateFormat dateFormat = null;
  private SimpleDateFormat dateTimeFormat = null;
  private SimpleDateFormat jsDateTimeFormat = null;

  public OBBindings(OBContext obContext) {
    Check.isNotNull(obContext, "The OBContext parameter cannot be null");
    context = obContext;
  }

  public OBBindings(OBContext obContext, Map<String, String> parameters) {
    Check.isNotNull(obContext, "The OBContext parameter cannot be null");
    context = obContext;
    requestMap = parameters;
  }

  public OBBindings(OBContext obContext, Map<String, String> parameters, HttpSession session) {
    Check.isNotNull(obContext, "The OBContext parameter cannot be null");
    context = obContext;

    Check.isNotNull(session, "The HttpSession parameter cannot be null");
    httpSession = session;

    requestMap = parameters;

    dateFormat = new SimpleDateFormat((String) httpSession.getAttribute("#AD_JAVADATEFORMAT"));

    dateTimeFormat = new SimpleDateFormat(
        (String) httpSession.getAttribute("#AD_JAVADATETIMEFORMAT"));

    jsDateTimeFormat = JsonUtils.createJSTimeFormat();
  }

  public OBContext getContext() {
    return context;
  }

  public String getIdentifier(String entityName, String id) {
    return OBDal.getInstance().get(entityName, id).getIdentifier();
  }

  public Map<String, String> getParameters() {
    return requestMap;
  }

  public HttpSession getSession() {
    return httpSession;
  }

  private boolean checkRequestMap() {
    if (requestMap == null) {
      log.warn("Accessing request parameters map without initializing it");
      return false;
    }
    return true;
  }

  /**
   * Checks if is a Sales Order transaction, based on the parameters of the HTTP request
   * 
   * @return null if there is no request parameters, or if both the inpissotrx and inpwindowId
   *         request parameters are not available
   */
  public Boolean isSalesTransaction() {
    if (requestMap == null) {
      log.warn("Requesting isSOTrx check without request parameters");
      return null;
    }

    String value = requestMap.get(OBBindingsConstants.SO_TRX_PARAM);
    if (value != null) {
      return "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }
    if (httpSession == null) {
      log.warn("Requesting isSOTrx check without request parameters and session");
      return null;
    }

    value = (String) httpSession.getAttribute("inpisSOTrxTab");
    if (value != null) {
      return "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    String windowId = getWindowId();
    if (windowId == null) {
      return null;
    }

    value = (String) httpSession.getAttribute(windowId + "|ISSOTRX");
    if (value != null) {
      return "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    ApplicationDictionaryCachedStructures adcs = WeldUtils
        .getInstanceFromStaticBeanManager(ApplicationDictionaryCachedStructures.class);

    Window w = adcs.getWindow(windowId);
    if (w != null) {
      return w.isSalesTransaction();
    }
    return null;
  }

  public String getWindowId() {
    if (!checkRequestMap()) {
      return null;
    }
    return requestMap.get(OBBindingsConstants.WINDOW_ID_PARAM);
  }

  public String getTabId() {
    if (!checkRequestMap()) {
      return null;
    }
    return requestMap.get(OBBindingsConstants.TAB_ID_PARAM);
  }

  public String getCommandType() {
    if (!checkRequestMap()) {
      return null;
    }
    return requestMap.get(OBBindingsConstants.COMMAND_TYPE_PARAM);
  }

  public Boolean isPosted() {
    if (!checkRequestMap()) {
      return null;
    }
    return "Y".equalsIgnoreCase(requestMap.get(OBBindingsConstants.POSTED_PARAM));
  }

  public Boolean isProcessed() {
    if (!checkRequestMap()) {
      return null;
    }
    return "Y".equalsIgnoreCase(requestMap.get(OBBindingsConstants.PROCESSED_PARAM));
  }

  public String formatDate(Date d) {
    return dateFormat.format(d);
  }

  public String formatDate(Object d) {
    return formatDate(getTimeFromNashornNativeDate(d));
  }

  private Date getTimeFromNashornNativeDate(Object d) {
    // Since Java 8, Nashorn is the Javascript engine used by default. When returning Javascript
    // date objects they are represented by jdk.nashorn.internal.objects.NativeDate.
    //
    // Although being a public class, it is in an internal module which is not opened by default in
    // JDK9+ therefore we still need to use reflection to access to it.
    //
    // Note Nashorn is marked as deprecated for deletion in JDK11
    // -> TODO: look for a valid alternative in future releases.
    try {
      Method m = d.getClass().getMethod("callMember", String.class, Object[].class);
      long localTime = ((Double) m.invoke(d, "getTime", null)).longValue();
      return new Date(localTime);
    } catch (Exception ex) {
      log.error("Error getting javascript date from object {} of class {}", d,
          d.getClass().getName());
      throw new OBException(ex.getMessage(), ex);
    }
  }

  public String formatDateTime(Date d) {
    return dateTimeFormat.format(d);
  }

  public String formatDateTime(Object d) {
    return formatDateTime(getTimeFromNashornNativeDate(d));
  }

  public Date parseDate(String date) {
    try {
      return dateFormat.parse(date);
    } catch (Exception e) {
      log.error("Error parsing string date " + date + " with format: " + dateFormat, e);
    }
    return null;
  }

  public Date parseDateTime(String dateTime) {
    try {
      Date result = convertToLocalTime(jsDateTimeFormat.parse(dateTime));
      return result;
    } catch (Exception e) {
      try {
        Date result = convertToLocalTime(dateTimeFormat.parse(dateTime));
        return result;
      } catch (Exception ex) {
        log.error("Error parsing string date " + dateTime + " with format: " + dateTimeFormat, e);
      }
    }
    return null;
  }

  private static Date convertToLocalTime(Date UTCTime) {
    Calendar localTime = Calendar.getInstance();
    localTime.setTime(UTCTime);

    int gmtMillisecondOffset = (localTime.get(Calendar.ZONE_OFFSET)
        + localTime.get(Calendar.DST_OFFSET));
    localTime.add(Calendar.MILLISECOND, gmtMillisecondOffset);

    return localTime.getTime();
  }

  public String formatDate(Date d, String format) {
    Check.isNotNull(format, "Format is a required parameter");
    SimpleDateFormat df = new SimpleDateFormat(format);
    return df.format(d);
  }

  public String formatDate(Object d, String format) {
    return formatDate(getTimeFromNashornNativeDate(d), format);
  }

  public Date parseDate(String date, String format) {
    Check.isNotNull(format, "Format is a required parameter");
    try {
      SimpleDateFormat df = new SimpleDateFormat(format);
      return df.parse(date);
    } catch (Exception e) {
      log.error("Error parsing string " + date + " with format: " + format, e);
    }
    return null;
  }

  public String getFilterExpression(String className) {
    Check.isNotNull(className, "The class name must not be null");
    FilterExpression expr;
    try {
      try {
        expr = (FilterExpression) WeldUtils
            .getInstanceFromStaticBeanManager(Class.forName(className));
      } catch (IllegalArgumentException e) {
        // try with OBClassLoader in case package is excluded by Weld
        expr = (FilterExpression) OBClassLoader.getInstance()
            .loadClass(className)
            .getDeclaredConstructor()
            .newInstance();
      }
      return expr.getExpression(requestMap);
    } catch (Exception e) {
      log.error("Error trying to get filter expression from class: " + className, e);
    }
    return "";
  }
}
