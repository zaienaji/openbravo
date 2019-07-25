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
 * All portions are Copyright (C) 2018 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.base.expression;

import java.util.Map;
import java.util.Map.Entry;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Class that wraps a ScriptEngine and that should be used to evaluate javascript scripts
 * 
 * It is a singleton, and it takes advantage of the thread safety of ScriptEngine
 * 
 */
public class OBScriptEngine {

  private final ScriptEngine engine;

  private static OBScriptEngine instance = new OBScriptEngine();

  public static OBScriptEngine getInstance() {
    return instance;
  }

  private OBScriptEngine() {
    ScriptEngineManager manager = new ScriptEngineManager();
    engine = manager.getEngineByName("js");
  }

  public Object eval(String script) throws ScriptException {
    return engine.eval(script);
  }

  public Object eval(String script, Map<String, Object> properties) throws ScriptException {
    Bindings bindings = engine.createBindings();
    copyPropertiesToBindings(properties, bindings);
    return engine.eval(script, bindings);
  }

  private void copyPropertiesToBindings(Map<String, Object> properties, Bindings bindings) {
    for (Entry<String, Object> entry : properties.entrySet()) {
      bindings.put(entry.getKey(), entry.getValue());
    }
  }

}
