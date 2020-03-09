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
 * All portions are Copyright (C) 2008-2019 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.base.gen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.Task;
import org.openbravo.base.AntExecutor;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.session.OBPropertiesProvider;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateException;

/**
 * Task generates the entities using the freemarker template engine.
 * 
 * @author Martin Taal
 * @author Stefan Huehner
 */
public class GenerateEntitiesTask extends Task {
  private static final Logger log = LogManager.getLogger();

  private String basePath;
  private String srcGenPath;
  private String propertiesFile;
  boolean generateAllChildProperties;

  public static void main(String[] args) {
    final String srcPath = args[0];
    String friendlyWarnings = "false";
    if (args.length >= 2) {
      friendlyWarnings = args[0];
    }
    final File srcDir = new File(srcPath);
    final File baseDir = srcDir.getParentFile();
    try {
      final AntExecutor antExecutor = new AntExecutor(baseDir.getAbsolutePath());
      antExecutor.setProperty("friendlyWarnings", friendlyWarnings);
      antExecutor.runTask("generate.entities.quick.forked");
    } catch (final Exception e) {
      throw new OBException(e);
    }
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public boolean getFriendlyWarnings() {
    return OBPropertiesProvider.isFriendlyWarnings();
  }

  public void setFriendlyWarnings(boolean doFriendlyWarnings) {
    OBPropertiesProvider.setFriendlyWarnings(doFriendlyWarnings);
  }

  public String getPropertiesFile() {
    return propertiesFile;
  }

  public void setPropertiesFile(String propertiesFile) {
    this.propertiesFile = propertiesFile;
  }

  public String getSrcGenPath() {
    return srcGenPath;
  }

  public void setSrcGenPath(String srcGenPath) {
    this.srcGenPath = srcGenPath;
  }

  @Override
  public void execute() {
    if (getBasePath() == null) {
      setBasePath(super.getProject().getBaseDir().getAbsolutePath());
    }

    // the beautifier uses the source.path if it is not set
    log.debug("initializating dal layer, getting properties from " + getPropertiesFile());
    OBPropertiesProvider.getInstance().setProperties(getPropertiesFile());

    if (!hasChanged()) {
      log.info("Model has not changed since last run, not re-generating entities");
      return;
    }

    generateAllChildProperties = OBPropertiesProvider.getInstance()
        .getBooleanProperty("hb.generate.all.parent.child.properties");

    // read and parse template
    String ftlFilename = "org/openbravo/base/gen/entity.ftl";
    File ftlFile = new File(getBasePath(), ftlFilename);
    freemarker.template.Template template = createTemplateImplementation(ftlFile);

    // template for computed columns entities
    String ftlComputedFilename = "org/openbravo/base/gen/entityComputedColumns.ftl";
    File ftlComputedFile = new File(getBasePath(), ftlComputedFilename);
    freemarker.template.Template templateComputed = createTemplateImplementation(ftlComputedFile);

    // process template & write file for each entity
    List<Entity> entities = ModelProvider.getInstance().getModel();
    for (Entity entity : entities) {
      // If the entity is associated with a datasource based table or based on an HQL query, do not
      // generate a Java file
      if (entity.isDataSourceBased() || entity.isHQLBased()) {
        continue;
      }

      File outFile;
      String classfileName;

      if (!entity.isVirtualEntity()) {
        classfileName = entity.getClassName().replaceAll("\\.", "/") + ".java";
        log.debug("Generating file: " + classfileName);
        outFile = new File(srcGenPath, classfileName);
        new File(outFile.getParent()).mkdirs();

        try (Writer outWriter = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
          Map<String, Object> data = new HashMap<>();
          data.put("entity", entity);
          data.put("util", this);
          processTemplate(template, data, outWriter);
        } catch (IOException e) {
          log.error("Error generating file: " + classfileName, e);
        }
      }

      if (entity.hasComputedColumns()) {
        classfileName = entity.getPackageName().replaceAll("\\.", "/") + "/"
            + entity.getSimpleClassName() + Entity.COMPUTED_COLUMNS_CLASS_APPENDIX + ".java";
        log.debug("Generating file: " + classfileName);
        outFile = new File(srcGenPath, classfileName);
        new File(outFile.getParent()).mkdirs();
        try (Writer outWriter = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
          Map<String, Object> data = new HashMap<>();
          data.put("entity", entity);

          List<Property> properties = entity.getComputedColumnProperties();

          if (entity.hasProperty("client")) {
            properties.add(entity.getProperty("client"));
            data.put("implementsClientEnabled", "implements ClientEnabled ");
          } else {
            data.put("implementsClientEnabled", "");
          }
          if (entity.hasProperty("organization")) {
            properties.add(entity.getProperty("organization"));
            if (entity.hasProperty("client")) {
              data.put("implementsOrgEnabled", ", OrganizationEnabled ");
            } else {
              data.put("implementsOrgEnabled", "implements OrganizationEnabled ");
            }
          } else {
            data.put("implementsOrgEnabled", "");
          }

          data.put("properties", properties);
          List<String> imports = entity.getJavaImports(properties);
          imports.remove("import org.openbravo.base.structure.ActiveEnabled;");
          imports.remove("import org.openbravo.base.structure.Traceable;");
          data.put("javaImports", imports);
          processTemplate(templateComputed, data, outWriter);
        } catch (IOException e) {
          log.error("Error generating file: " + classfileName, e);
        }
      }
    }
    log.info("Generated " + entities.size() + " entities");
  }

  public boolean isDeprecated(Property p) {
    if (!generateAllChildProperties) {
      return false;
    }

    Property refPropery = p.getReferencedProperty();
    if (refPropery == null) {
      return false;
    }

    boolean generatedInAnyCase = ModelProvider.getInstance()
        .shouldGenerateChildPropertyInParent(refPropery, false);

    boolean generatedDueToPreference = ModelProvider.getInstance()
        .shouldGenerateChildPropertyInParent(refPropery, true);
    return !generatedInAnyCase && generatedDueToPreference;
  }

  public String getDeprecationMessage(Property p) {
    return "Child property in parent entity generated for backward compatibility, it will be removed in future releases.";
  }

  private boolean hasChanged() {
    // first check if there is a directory
    // already in the src-gen
    // if not then regenerate anyhow
    final File modelDir = new File(getSrcGenPath(),
        "org" + File.separator + "openbravo" + File.separator + "model" + File.separator + "ad");
    if (!modelDir.exists()) {
      return true;
    }

    // check if the logic to generate has changed...
    final String sourceDir = getBasePath();
    long lastModifiedPackage = 0;
    lastModifiedPackage = getLastModifiedPackage("org.openbravo.base.model", sourceDir,
        lastModifiedPackage);
    lastModifiedPackage = getLastModifiedPackage("org.openbravo.base.gen", sourceDir,
        lastModifiedPackage);
    lastModifiedPackage = getLastModifiedPackage("org.openbravo.base.structure", sourceDir,
        lastModifiedPackage);

    // check if there is a sourcefile which was updated before the last
    // time the model was created. In this case that sourcefile (and
    // all source files need to be regenerated
    final long lastModelUpdateTime = ModelProvider.getInstance().computeLastUpdateModelTime();
    final long lastModified;
    if (lastModelUpdateTime > lastModifiedPackage) {
      lastModified = lastModelUpdateTime;
    } else {
      lastModified = lastModifiedPackage;
    }
    return isSourceFileUpdatedBeforeModelChange(modelDir, lastModified);
  }

  private boolean isSourceFileUpdatedBeforeModelChange(File file, long modelUpdateTime) {
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        if (isSourceFileUpdatedBeforeModelChange(child, modelUpdateTime)) {
          return true;
        }
      }
      return false;
    }
    return file.lastModified() < modelUpdateTime;
  }

  private long getLastModifiedPackage(String pkg, String baseSourcePath, long prevLastModified) {
    final File file = new File(baseSourcePath, pkg.replaceAll("\\.", "/"));
    final long lastModified = getLastModifiedRecursive(file);
    if (lastModified > prevLastModified) {
      return lastModified;
    }
    return prevLastModified;
  }

  private long getLastModifiedRecursive(File file) {
    long lastModified = file.lastModified();
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        final long childLastModified = getLastModifiedRecursive(child);
        if (lastModified < childLastModified) {
          lastModified = childLastModified;
        }
      }
    }
    return lastModified;
  }

  private void processTemplate(freemarker.template.Template templateImplementation,
      Map<String, Object> data, Writer output) {
    try {
      templateImplementation.process(data, output);
    } catch (IOException | TemplateException e) {
      throw new IllegalStateException(e);
    }
  }

  private freemarker.template.Template createTemplateImplementation(File file) {
    try (FileReader reader = new FileReader(file)) {
      return new freemarker.template.Template("template", reader, getNewConfiguration());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Configuration getNewConfiguration() {
    final Configuration cfg = new Configuration();
    cfg.setObjectWrapper(new DefaultObjectWrapper());
    return cfg;
  }
}
