/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.util.messages.MessageBusConnection;

import java.io.File;
import java.io.IOException;

/**
 * @author dsl
 */
public class RootsChangedTest extends ModuleTestCase {
  private MessageBusConnection myConnection;
  private MyModuleRootListener myModuleRootListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myConnection = myProject.getMessageBus().connect();
    myModuleRootListener = new MyModuleRootListener();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, myModuleRootListener);
  }

  @Override
  protected void tearDown() throws Exception {
    myConnection.disconnect();
    super.tearDown();
  }

  public void testProjectLibraryChangeEvent() throws Exception {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    verifyLibraryTableEditing(projectLibraryTable);
  }

  public void testGlobalLibraryChangeEvent() throws Exception {
    final LibraryTable globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    verifyLibraryTableEditing(globalLibraryTable);
  }

  public void testProjectLibraryEventsInUncommitedModel() throws Exception {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    verifyLibraryTableEditingInUncommitedModel(projectLibraryTable);
  }

  public void testGlobalLibraryEventsInUncommitedModel() throws Exception {
    final LibraryTable globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    verifyLibraryTableEditingInUncommitedModel(globalLibraryTable);
  }


  public void testModuleJdkEditing() throws Exception {
    final Module moduleA = createModule("a.iml");
    final Module moduleB = createModule("b.iml");
    assertEventsCount(2);

    final Sdk jdk = IdeaTestUtil.getMockJdk17();
    ProjectJdkTable.getInstance().addJdk(jdk);
    assertEventsCount(0);

    final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
    final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
    rootModelA.setSdk(jdk);
    rootModelB.setSdk(jdk);
    ModifiableRootModel[] rootModels = new ModifiableRootModel[]{rootModelA, rootModelB};
    if (rootModels.length > 0) {
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
    }
    assertEventsCount(1);

    final SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.addRoot(getVirtualFile(createTempDirectory()), OrderRootType.CLASSES);
    sdkModificator.commitChanges();
    assertEventsCount(1);

    ProjectJdkTable.getInstance().removeJdk(jdk);
    assertEventsCount(1);
  }

  public void testInheritedJdkEditing() throws Exception {
    final Module moduleA = createModule("a.iml");
    final Module moduleB = createModule("b.iml");
    assertEventsCount(2);

    final Sdk jdk = IdeaTestUtil.getMockJdk17("AAA");
    ProjectJdkTable.getInstance().addJdk(jdk);
    assertEventsCount(0);

    final Sdk jdkBBB = IdeaTestUtil.getMockJdk17("BBB");
    ProjectJdkTable.getInstance().addJdk(jdk);
    assertEventsCount(0);

    ProjectRootManager.getInstance(myProject).setProjectSdk(jdkBBB);
    assertEventsCount(0);

    final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
    final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
    rootModelA.inheritSdk();
    rootModelB.inheritSdk();
    ModifiableRootModel[] rootModels = new ModifiableRootModel[]{rootModelA, rootModelB};
    if (rootModels.length > 0) {
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
    }
    assertEventsCount(1);

    ProjectRootManager.getInstance(myProject).setProjectSdk(jdk);
    assertEventsCount(1);

    final SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.addRoot(getVirtualFile(createTempDirectory()), OrderRootType.CLASSES);
    sdkModificator.commitChanges();
    assertEventsCount(1);

    ProjectJdkTable.getInstance().removeJdk(jdk);
    assertEventsCount(1);
  }

  private void verifyLibraryTableEditing(final LibraryTable libraryTable) throws IOException {
    final Module moduleA = createModule("a.iml");
    final Module moduleB = createModule("b.iml");
    assertEventsCount(2);

    final Library libraryA = libraryTable.createLibrary("A");
    final Library.ModifiableModel libraryModifiableModel = libraryA.getModifiableModel();
    libraryModifiableModel.addRoot("file:///a", OrderRootType.CLASSES);
    libraryModifiableModel.commit();
    assertEventsCount(0);

    final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
    final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
    rootModelA.addLibraryEntry(libraryA);
    rootModelB.addLibraryEntry(libraryA);
    rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
    rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
    ModifiableRootModel[] rootModels = new ModifiableRootModel[]{rootModelA, rootModelB};
    if (rootModels.length > 0) {
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
    }
    assertEventsCount(1);

    final Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
    final File tmpDir = FileUtil.createTempDirectory(getTestName(true), "");
    try {
      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tmpDir);
      assertNotNull(file);
      
      libraryModifiableModel2.addRoot(file.getUrl(), OrderRootType.CLASSES);
      libraryModifiableModel2.commit();
      assertEventsCount(1);
    }
    finally {
      FileUtil.delete(tmpDir);
    }

    libraryTable.removeLibrary(libraryA);
    assertEventsCount(1);

    final Library libraryQ = libraryTable.createLibrary("Q");
    assertEventsCount(1);

    libraryTable.removeLibrary(libraryQ);
    assertEventsCount(1);
  }

  private void verifyLibraryTableEditingInUncommitedModel(final LibraryTable libraryTable) {
    final Module moduleA = createModule("a.iml");
    final Module moduleB = createModule("b.iml");
    assertEventsCount(2);

    final Library libraryA = libraryTable.createLibrary("A");
    final Library.ModifiableModel libraryModifiableModel = libraryA.getModifiableModel();
    libraryModifiableModel.addRoot("file:///a", OrderRootType.CLASSES);
    libraryModifiableModel.commit();
    assertEventsCount(0);

    final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
    final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
    rootModelA.addLibraryEntry(libraryA);
    rootModelB.addLibraryEntry(libraryA);
    final Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
    libraryModifiableModel2.addRoot("file:///b", OrderRootType.CLASSES);
    libraryModifiableModel2.commit();
    assertEventsCount(0);

    libraryTable.removeLibrary(libraryA);
    assertEventsCount(0);

    rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
    rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
    assertEventsCount(0);

    final Library libraryQ = libraryTable.createLibrary("Q");
    assertEventsCount(0);

    ModifiableRootModel[] rootModels = new ModifiableRootModel[]{rootModelA, rootModelB};
    if (rootModels.length > 0) {
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
    }
    assertEventsCount(1);

    libraryTable.removeLibrary(libraryQ);
    assertEventsCount(1);
  }

  private void assertEventsCount(int count) {
    final int beforeCount = myModuleRootListener.beforeCount;
    final int afterCount = myModuleRootListener.afterCount;
    assertEquals("beforeCount = " + beforeCount + ", afterCount = " + afterCount, beforeCount, afterCount);
    assertEquals(count, beforeCount);
    myModuleRootListener.reset();
  }

  private static class MyModuleRootListener implements ModuleRootListener {
    private int beforeCount = 0;
    private int afterCount = 0;

    @Override
    public void beforeRootsChange(ModuleRootEvent event) {
      beforeCount++;
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      afterCount++;
    }

    private void reset() {
      beforeCount = 0;
      afterCount = 0;
    }
  }
}
