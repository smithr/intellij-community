/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PsiChangeHandler extends PsiTreeChangeAdapter implements Disposable {
  private static final ExtensionPointName<ChangeLocalityDetector> EP_NAME = ExtensionPointName.create("com.intellij.daemon.changeLocalityDetector");
  private /*NOT STATIC!!!*/ final Key<Boolean> UPDATE_ON_COMMIT_ENGAGED = Key.create("UPDATE_ON_COMMIT_ENGAGED");

  private final Project myProject;
  private final Map<Document, List<Pair<PsiElement, Boolean>>> changedElements = new THashMap<Document, List<Pair<PsiElement, Boolean>>>();
  private final FileStatusMap myFileStatusMap;

  public PsiChangeHandler(@NotNull Project project,
                          @NotNull final PsiDocumentManagerImpl documentManager,
                          @NotNull EditorFactory editorFactory,
                          @NotNull MessageBusConnection connection,
                          @NotNull FileStatusMap fileStatusMap) {
    myProject = project;
    myFileStatusMap = fileStatusMap;
    editorFactory.getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        final Document document = e.getDocument();
        if (documentManager.getSynchronizer().isInSynchronization(document)) return;
        if (documentManager.getCachedPsiFile(document) == null) return;
        if (document.getUserData(UPDATE_ON_COMMIT_ENGAGED) == null) {
          document.putUserData(UPDATE_ON_COMMIT_ENGAGED, Boolean.TRUE);
          PsiDocumentManagerImpl.addRunOnCommit(document, new Runnable() {
            @Override
            public void run() {
              updateChangesForDocument(document);
              document.putUserData(UPDATE_ON_COMMIT_ENGAGED, null);
            }
          });
        }
      }
    }, this);

    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(final Document doc, final PsiFile file) {
      }

      @Override
      public void transactionCompleted(final Document doc, final PsiFile file) {
        updateChangesForDocument(doc);
      }
    });
  }

  @Override
  public void dispose() {
  }

  private void updateChangesForDocument(@NotNull final Document document) {
    if (DaemonListeners.isUnderIgnoredAction(null)) return;
    List<Pair<PsiElement, Boolean>> toUpdate = changedElements.get(document);
    if (toUpdate == null) return;
    Application application = ApplicationManager.getApplication();
    final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor != null && !application.isUnitTestMode()) {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
          PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
          TrafficLightRenderer.setOrRefreshErrorStripeRenderer(markupModel, myProject, document, file);
        }
      }, ModalityState.stateForComponent(editor.getComponent()), myProject.getDisposed());
    }

    for (Pair<PsiElement, Boolean> changedElement : toUpdate) {
      PsiElement element = changedElement.getFirst();
      Boolean whiteSpaceOptimizationAllowed = changedElement.getSecond();
      updateByChange(element, document, whiteSpaceOptimizationAllowed);
    }
    changedElements.remove(document);
  }

  @Override
  public void childAdded(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  @Override
  public void childRemoved(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  @Override
  public void childReplaced(PsiTreeChangeEvent event) {
    queueElement(event.getNewChild(), typesEqual(event.getNewChild(), event.getOldChild()), event);
  }

  private static boolean typesEqual(final PsiElement newChild, final PsiElement oldChild) {
    return newChild != null && oldChild != null && newChild.getClass() == oldChild.getClass();
  }

  @Override
  public void childrenChanged(PsiTreeChangeEvent event) {
    if (((PsiTreeChangeEventImpl)event).isGenericChildrenChange()) {
      return;
    }
    queueElement(event.getParent(), true, event);
  }

  @Override
  public void beforeChildMovement(PsiTreeChangeEvent event) {
    queueElement(event.getOldParent(), true, event);
    queueElement(event.getNewParent(), true, event);
  }

  @Override
  public void beforeChildrenChange(PsiTreeChangeEvent event) {
    // this event sent always before every PSI change, even not significant one (like after quick typing/backspacing char)
    // mark file dirty just in case
    PsiFile psiFile = event.getFile();
    if (psiFile != null) {
      myFileStatusMap.markFileScopeDirtyDefensively(psiFile);
    }
  }

  @Override
  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
      myFileStatusMap.markAllFilesDirty();
    }
  }

  private void queueElement(PsiElement child, final boolean whitespaceOptimizationAllowed, PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    if (file == null) file = child.getContainingFile();
    if (file == null) {
      myFileStatusMap.markAllFilesDirty();
      return;
    }

    if (!child.isValid()) return;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document != null) {
      List<Pair<PsiElement, Boolean>> toUpdate = changedElements.get(document);
      if (toUpdate == null) {
        toUpdate = new SmartList<Pair<PsiElement, Boolean>>();
        changedElements.put(document, toUpdate);
      }
      toUpdate.add(Pair.create(child, whitespaceOptimizationAllowed));
    }
  }

  private void updateByChange(@NotNull PsiElement child, @NotNull final Document document, final boolean whitespaceOptimizationAllowed) {
    final PsiFile file;
    try {
      file = child.getContainingFile();
    }
    catch (PsiInvalidElementAccessException e) {
      myFileStatusMap.markAllFilesDirty();
      return;
    }
    if (file == null || file instanceof PsiCompiledElement) {
      myFileStatusMap.markAllFilesDirty();
      return;
    }

    int fileLength = file.getTextLength();
    if (!file.getViewProvider().isPhysical()) {
      myFileStatusMap.markFileScopeDirty(document, new TextRange(0, fileLength), fileLength);
      return;
    }

    // optimization
    if (whitespaceOptimizationAllowed && UpdateHighlightersUtil.isWhitespaceOptimizationAllowed(document)) {
      if (child instanceof PsiWhiteSpace ||
          child instanceof PsiComment && !child.getText().contains(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) {
        myFileStatusMap.markFileScopeDirty(document, child.getTextRange(), fileLength);
        return;
      }
    }

    PsiElement element = child;
    while (true) {
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        myFileStatusMap.markAllFilesDirty();
        return;
      }

      final PsiElement scope = getChangeHighlightingScope(element);
      if (scope != null) {
        myFileStatusMap.markFileScopeDirty(document, scope.getTextRange(), fileLength);
        return;
      }

      element = element.getParent();
    }
  }

  @Nullable
  private static PsiElement getChangeHighlightingScope(PsiElement element) {
    for (ChangeLocalityDetector detector : Extensions.getExtensions(EP_NAME)) {
      final PsiElement scope = detector.getChangeHighlightingDirtyScopeFor(element);
      if (scope != null) return scope;
    }
    return null;
  }
}
