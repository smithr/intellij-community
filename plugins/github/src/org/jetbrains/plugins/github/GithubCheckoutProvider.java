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
package org.jetbrains.plugins.github;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.BasicAction;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.checkout.GitCloneDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author oleg
 */
public class GithubCheckoutProvider implements CheckoutProvider {

  @Override
  public void doCheckout(@NotNull final Project project, @Nullable final Listener listener) {
    if (!GithubUtil.testGitExecutable(project)){
      return;
    }
    BasicAction.saveAll();
    final List<RepositoryInfo> availableRepos = GithubUtil.getAvailableRepos(project, false);
    if (availableRepos == null){
      return;
    }
    Collections.sort(availableRepos, new Comparator<RepositoryInfo>() {
      @Override
      public int compare(final RepositoryInfo r1, final RepositoryInfo r2) {
        final int comparedOwners = r1.getOwner().compareTo(r2.getOwner());
        return comparedOwners != 0 ? comparedOwners : r1.getName().compareTo(r2.getName());
      }
    });

    final GitCloneDialog dialog = new GitCloneDialog(project);
    // Add predefined repositories to history
    for (int i = availableRepos.size() - 1; i>=0; i--){
      dialog.prependToHistory(availableRepos.get(i).getUrl());
    }
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    dialog.rememberSettings();
    final VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
    if (destinationParent == null) {
      return;
    }
    final String sourceRepositoryURL = dialog.getSourceRepositoryURL();
    final String directoryName = dialog.getDirectoryName();
    final String parentDirectory = dialog.getParentDirectory();

    GitCheckoutProvider.clone(project, listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory);
  }

  @Override
  public String getVcsName() {
    return "Git_Hub";
  }
}
