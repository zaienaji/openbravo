package org.wirabumi.gen.oez;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.BaseComponentProvider;
import org.openbravo.client.kernel.Component;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.client.kernel.KernelConstants;

//import org.openbravo.client.kernel.ComponentProvider;

@ApplicationScoped
@ComponentProvider.Qualifier(OezApplicationProvider.OEZSTATICCOMPONENT)
public class OezApplicationProvider extends BaseComponentProvider {
  public static final String OEZSTATICCOMPONENT = "OEZSTATICCOMPONENT";

  @Override
  public List<ComponentResource> getGlobalComponentResources() {
    final List<ComponentResource> globalResources = new ArrayList<ComponentResource>();
    globalResources.add(createStaticResource("web/org.wirabumi.gen.oez/js/OezDocRoutingToolbar.js", true));
    globalResources.add(createStaticResource("web/org.wirabumi.gen.oez/js/toobar-button.js", true));
    globalResources.add(createStyleSheetResource(
            "web/org.openbravo.userinterface.smartclient/wirabumi/skins/"
            + KernelConstants.SKIN_PARAMETER+
           "/org.wirabumi.gen.oez/styles.css", true));
    return globalResources;
            
            
  }

  @Override
  public Component getComponent(String componentId, Map<String, Object> parameters) {
    return null;
  }

}
