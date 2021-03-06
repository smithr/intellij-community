/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author nik
 */
public class CreateProjectFromSourcesMode extends CreateFromSourcesMode {
  private static final Icon STEP_ICON = IconLoader.getIcon("/newprojectwizard.png");

  protected Icon getIcon() {
    return STEP_ICON;
  }

  public boolean isAvailable(WizardContext context) {
    return context.isCreatingNewProject();
  }
}
