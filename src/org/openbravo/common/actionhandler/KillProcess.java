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
 * All portions are Copyright (C) 2014-2017 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.common.actionhandler;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.process.ResponseActionsBuilder.MessageType;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.scheduling.DefaultJob;
import org.openbravo.scheduling.KillableProcess;
import org.openbravo.scheduling.OBScheduler;
import org.openbravo.service.db.DbUtility;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

/**
 * 
 * Kill Process is launched from kill button in the Process Monitor Window. It will try to execute
 * the kill method in the process instance.
 * 
 */
public class KillProcess extends BaseProcessActionHandler {

  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      if (OBScheduler.isNoExecuteBackgroundPolicy()) {
        return getResponseBuilder()
            .showMsgInProcessView(MessageType.ERROR,
                OBMessageUtils.getI18NMessage("BackgroundPolicyNoExecuteMsg", null))
            .build();
      }

      JSONObject request = new JSONObject(content);
      String strProcessRunId = request.getString("inpadProcessRunId");

      // Get Jobs
      Scheduler scheduler = OBScheduler.getInstance().getScheduler();
      @SuppressWarnings("unchecked")
      List<JobExecutionContext> jobs = scheduler.getCurrentlyExecutingJobs();
      if (jobs.size() == 0) {
        throw new Exception(OBMessageUtils.getI18NMessage("ProcessNotFound", null));
      }

      // Look for the job
      for (JobExecutionContext job : jobs) {
        String jobProcessRunId = (String) job.get(org.openbravo.scheduling.Process.EXECUTION_ID);
        if (jobProcessRunId.equals(strProcessRunId)) {
          // Job Found
          DefaultJob jobInstance = (DefaultJob) job.getJobInstance();
          org.openbravo.scheduling.Process process = jobInstance.getProcessInstance();
          if (process instanceof KillableProcess) {
            // Kill Process
            ((KillableProcess) process).kill(jobInstance.getBundle());
            jobInstance.setKilled(true);
            return getResponseBuilder()
                .showMsgInProcessView(MessageType.INFO,
                    OBMessageUtils.getI18NMessage("ProcessKilled", null))
                .build();
          } else {
            // KillableProcess not implemented
            return getResponseBuilder()
                .showMsgInProcessView(MessageType.WARNING,
                    OBMessageUtils.getI18NMessage("KillableProcessNotImplemented", null))
                .build();
          }

        }
      }

      throw new Exception(OBMessageUtils.getI18NMessage("ProcessNotFound", null));

    } catch (Exception ex) {
      Throwable e = DbUtility.getUnderlyingSQLException(ex);
      log.error("Error in Kill Process", e);
      try {
        return getResponseBuilder()
            .showMsgInProcessView(MessageType.ERROR, getTranslatedExceptionMessage(e))
            .build();
      } catch (Exception ignoreException) {
        // do nothing
      }
    }

    return result;

  }

  private String getTranslatedExceptionMessage(Throwable throwable) {
    String message = throwable.getMessage();
    String translatedMessage = OBMessageUtils.getI18NMessage(message, null);
    if (translatedMessage != null) {
      return translatedMessage;
    }
    return message;
  }
}
