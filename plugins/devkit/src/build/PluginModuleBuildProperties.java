/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.ModuleBuildProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.descriptors.ConfigFileContainer;
import com.intellij.util.descriptors.ConfigFileFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginDescriptorConstants;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

import java.io.File;

public class PluginModuleBuildProperties extends ModuleBuildProperties implements JDOMExternalizable {
  private Module myModule;
  private ConfigFileContainer myPluginXmlContainer;
  private VirtualFilePointer myPluginXMLPointer;
  private VirtualFilePointer myManifestFilePointer;
  private boolean myUseUserManifest = false;
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String MANIFEST_ATTR = "manifest";
  @NonNls private static final String META_INF = "META-INF";
  @NonNls private static final String PLUGIN_XML = "plugin.xml";

  public PluginModuleBuildProperties(Module module) {
    myModule = module;
    myPluginXmlContainer = ConfigFileFactory.getInstance().createSingleFileContainer(myModule.getProject(), PluginDescriptorConstants.META_DATA);
    Disposer.register(module, myPluginXmlContainer);
  }

  public String getArchiveExtension() {
    return "jar";
  }

  public String getJarPath() {
    return null;
  }

  public String getExplodedPath() {
    return PluginBuildUtil.getPluginExPath(myModule);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public boolean isJarEnabled() {
    return false;
  }

  public boolean isExplodedEnabled() {
    return true;
  }

  public boolean isBuildOnFrameDeactivation() {
    return false;
  }

  public boolean isSyncExplodedDir() {
    return true;
  }

  public boolean isBuildExternalDependencies() {
    return false;
  }

  @Nullable
  public BuildParticipant getBuildParticipant() {
    ProjectJdk jdk = ModuleRootManager.getInstance(myModule).getJdk();
    jdk = IdeaJdk.findIdeaJdk(jdk);
    if (jdk != null && IdeaJdk.isFromIDEAProject(jdk.getHomePath())) {
      return null;
    }
    return new PluginBuildParticipant(myModule);
  }

  @Nullable
  public UnnamedConfigurable getBuildConfigurable(ModifiableRootModel rootModel) {
    return null;
  }

  public void runValidators(File output, CompileContext context) throws Exception {
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public void moduleAdded() {}

  @NotNull
  public String getComponentName() {
    return "DevKit.ModuleBuildProperties";
  }

  public void initComponent() {}

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    String url = element.getAttributeValue(URL_ATTR);
    if (url != null) {
      setPluginXMLUrl(VfsUtil.urlToPath(url));
    }
    url = element.getAttributeValue(MANIFEST_ATTR);
    if (url != null) {
      setManifestUrl(VfsUtil.urlToPath(url));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(URL_ATTR, getPluginXMLPointer().getUrl());
    if (myManifestFilePointer != null){
      element.setAttribute(MANIFEST_ATTR, myManifestFilePointer.getUrl());
    }
  }

  public ConfigFile getPluginXML() {
    final ConfigFile descriptor = myPluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
    if (descriptor == null) {
      return createDescriptor(getPluginXMLPointer().getUrl());
    }
    return descriptor;
  }

  private ConfigFile createDescriptor(final String url) {
    final ConfigFileInfo descriptor = new ConfigFileInfo(PluginDescriptorConstants.META_DATA, url);
    myPluginXmlContainer.getConfiguration().addConfigFile(descriptor);
    ConfigFileFactory.getInstance().createFile(myModule.getProject(), descriptor.getUrl(), PluginDescriptorConstants.META_DATA.getDefaultVersion());
    return myPluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
  }

  public VirtualFilePointer getPluginXMLPointer() {
    if (myPluginXMLPointer == null) {
      final String defaultPluginXMLLocation = new File(myModule.getModuleFilePath()).getParent() + File.separator + META_INF + File.separator + PLUGIN_XML;
      setPluginXMLUrl(defaultPluginXMLLocation);
    }
    return myPluginXMLPointer;
  }

  public String getPluginXmlPath() {
    VirtualFile file = getPluginXMLPointer().getFile();
    if (file == null){ //e.g. file deleted
      myPluginXMLPointer = null;
      file = getPluginXMLPointer().getFile(); //to suggest default location
    }
    assert file != null;
    return FileUtil.toSystemDependentName(file.getPath());
  }

  public void setPluginXMLUrl(final String pluginXMLUrl) {
    final String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(pluginXMLUrl));
    myPluginXmlContainer.getConfiguration().removeConfigFiles(PluginDescriptorConstants.META_DATA);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        createDescriptor(pluginXMLUrl);
        myPluginXMLPointer = VirtualFilePointerManager.getInstance().create(url, null);
      }
    });
  }

  public void setManifestUrl(final String manifestUrl) {
    if (manifestUrl == null || manifestUrl.length() == 0){
      myManifestFilePointer = null;
    } else {

      final VirtualFile manifest = LocalFileSystem.getInstance().findFileByPath(manifestUrl);
      if (manifest == null){
        Messages.showErrorDialog(myModule.getProject(), DevKitBundle.message("error.file.not.found.message", manifestUrl), DevKitBundle.message("error.file.not.found"));
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            myManifestFilePointer = VirtualFilePointerManager.getInstance().create(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(manifestUrl)), null);
          }
        });
      } else {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            myManifestFilePointer = VirtualFilePointerManager.getInstance().create(manifest, null);
          }
        });
      }
    }
  }

  @Nullable
  public String getManifestPath() {
    if (myManifestFilePointer != null){
      return FileUtil.toSystemDependentName(myManifestFilePointer.getPresentableUrl());
    }
    return null;
  }

  @Nullable
  public VirtualFile getManifest(){
    if (myManifestFilePointer != null){
      return myManifestFilePointer.getFile();
    }
    return null;
  }

  public boolean isUseUserManifest() {
    return myUseUserManifest;
  }

  public void setUseUserManifest(final boolean useUserManifest) {
    myUseUserManifest = useUserManifest;
  }
}