package org.wirabumi.gen.oez.test;

import org.junit.Test;
import org.openbravo.test.base.OBBaseTest;
import org.wirabumi.gen.oez.porting.GLJournalActionHandler;

public class DocumentRoutingTest extends OBBaseTest{
	
	@Test
	public void testDocumentRouting() {
		GLJournalActionHandler gl = new GLJournalActionHandler();
		String windowID="";
		gl.doRouting(windowID, null, null, null, null);
		
	}

}
