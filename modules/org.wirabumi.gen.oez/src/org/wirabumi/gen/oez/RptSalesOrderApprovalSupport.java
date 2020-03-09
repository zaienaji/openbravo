package org.wirabumi.gen.oez;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.erpCommon.utility.Utility;

import java.util.HashMap;
import net.sf.jasperreports.engine.*;

public class RptSalesOrderApprovalSupport extends HttpSecureAppServlet {
  private static final long serialVersionUID = 1L;

  public void init(ServletConfig config) {
    super.init(config);
    boolHist = false;
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException,
      ServletException {
    VariablesSecureApp vars = new VariablesSecureApp(request);

    if (vars.commandIn("DEFAULT")) {
      String strcOrderId = vars.getGlobalVariable("inpcOrderId", strDefaultServlet);
      printPagePDF(response, vars, strcOrderId);
    } else
      pageError(response);
  }

  private void printPagePDF(HttpServletResponse response, VariablesSecureApp vars,
      String strcOrderId) throws IOException, ServletException {
   if (log4j.isDebugEnabled()) log4j.debug("Output: RptEmployeeCardJR - pdf");
   JasperPrint jasperPrint;    
   String strReportName = "@basedesign@/org/wirabumi/gen/oez/RptSalesOrderApprovalDecisionSupport.jrxml";
   response.setHeader("Content-disposition", "inline; filename=SalesOrderReview.pdf");

   strcOrderId=strcOrderId.replace("(", "");
   strcOrderId=strcOrderId.replace(")", "");
   strcOrderId=strcOrderId.replace("\'", "");

   HashMap<String, Object> parameters = new HashMap<String, Object>();
   parameters.put("DOCUMENT_ID", strcOrderId);   
   renderJR(vars, response, strReportName, "pdf", parameters, null, null );

  }

  public String getServletInfo() {
    return "Servlet that presents the Sales Order Review Report";
  } // End of getServletInfo() method
}
