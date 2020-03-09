package org.wirabumi.gen.oez.utility;

import java.io.PrintWriter;
import java.net.Socket;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.erpCommon.utility.Utility;

public class RawPrintUtility {
	static Logger log4j = Logger.getLogger(RawPrintUtility.class);

    public static void sendToPrint(String textToPrint, VariablesSecureApp vars) throws Exception {
    	
    	//get printer
    	String printerAddress = Utility.getPreference(vars, "RAWTEXTPRINTERADDRESS", null);
		if (printerAddress==null || printerAddress.isEmpty())
			throw new OBException("printer address not defined");
		
		String strPrinterPort = Utility.getPreference(vars, "RAWTEXTPRINTERPORT", null);
		if (strPrinterPort==null || strPrinterPort.isEmpty())
			throw new OBException("printer address not defined");
		
		sendToPrint(textToPrint, printerAddress, strPrinterPort);
    }
    
    public static void sendToPrint(String textToPrint, String printerAddress, String strPrinterPort) throws Exception {
    	
    	//get printer
		if (printerAddress==null || printerAddress.isEmpty())
			throw new OBException("printer address not defined");
		
		if (strPrinterPort==null || strPrinterPort.isEmpty())
			throw new OBException("printer address not defined");
		
		Integer printerPort = null;
		try {
			printerPort=Integer.parseInt(strPrinterPort);
		} catch (NumberFormatException e){
			throw new OBException("printer port "+strPrinterPort+" is not valid port");
		}
		
        if (log4j.isDebugEnabled())
        	log4j.debug(textToPrint);
        
        // print to socket (for production)
        Socket sock = new Socket(printerAddress, printerPort);
        PrintWriter output = new PrintWriter(sock.getOutputStream());
        output.print(textToPrint);
        output.flush();
        output.close();
        sock.close();
    }

}
