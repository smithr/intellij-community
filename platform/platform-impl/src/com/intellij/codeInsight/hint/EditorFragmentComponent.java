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
package com.intellij.codeInsight.hint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;

public class EditorFragmentComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.EditorFragmentComponent");

  private EditorFragmentComponent(EditorEx editor, int startLine, int endLine, boolean showFolding, boolean showGutter) {
    Document doc = editor.getDocument();
    final int endOffset = endLine < doc.getLineCount() ? doc.getLineEndOffset(endLine) : doc.getTextLength();
    int textWidth = Math.min(editor.getMaxWidthInRange(doc.getLineStartOffset(startLine), endOffset), ScreenUtil.getScreenRectangle(1, 1).width);
    LOG.assertTrue(textWidth > 0, "TextWidth: "+textWidth+"; startLine:" + startLine + "; endLine:" + endLine + ";");

    FoldingModelEx foldingModel = editor.getFoldingModel();
    boolean isFoldingEnabled = foldingModel.isFoldingEnabled();
    if (!showFolding) {
      foldingModel.setFoldingEnabled(false);
    }

    Point p1 = editor.logicalPositionToXY(new LogicalPosition(startLine, 0));
    Point p2 = editor.logicalPositionToXY(new LogicalPosition(Math.max(endLine, startLine + 1), 0));
    int y1 = p1.y;
    int y2 = p2.y;
    int height = y2 - y1 == 0 ? editor.getLineHeight() : y2 - y1;
    LOG.assertTrue(height > 0, "Height: " + height + "; startLine:" + startLine + "; endLine:" + endLine + "; p1:" + p1 + "; p2:" + p2);

    int savedScrollOffset = editor.getScrollingModel().getHorizontalScrollOffset();
    if (savedScrollOffset > 0) {
      editor.stopOptimizedScrolling();
      editor.getScrollingModel().scrollHorizontally(0);
    }

    final Image textImage = new BufferedImage(textWidth, height, BufferedImage.TYPE_INT_RGB);
    Graphics textGraphics = textImage.getGraphics();

    final JComponent rowHeader;
    final Image markersImage;
    if (showGutter) {
      rowHeader = editor.getGutterComponentEx();
      markersImage = new BufferedImage(Math.max(1, rowHeader.getWidth()), height, BufferedImage.TYPE_INT_RGB);
      Graphics markerGraphics = markersImage.getGraphics();

      markerGraphics.translate(0, -y1);
      markerGraphics.setClip(0, y1, rowHeader.getWidth(), height);
      markerGraphics.setColor(getBackgroundColor(editor));
      markerGraphics.fillRect(0, y1, rowHeader.getWidth(), height);
      rowHeader.paint(markerGraphics);
    }
    else {
      rowHeader = null;
      markersImage = null;
    }

    textGraphics.translate(0, -y1);
    textGraphics.setClip(0, y1, textWidth, height);
    final boolean wasVisible = editor.setCaretVisible(false);
    editor.getContentComponent().paint(textGraphics);
    if (wasVisible) {
      editor.setCaretVisible(true);
    }

    if (!showFolding) {
      foldingModel.setFoldingEnabled(isFoldingEnabled);
    }

    if (savedScrollOffset > 0) {
      editor.stopOptimizedScrolling();
      editor.getScrollingModel().scrollHorizontally(savedScrollOffset);
    }

    JComponent component = new JComponent() {
      public Dimension getPreferredSize() {
        return new Dimension(textImage.getWidth(null) +(markersImage == null ? 0 : markersImage.getWidth(null)), textImage.getHeight(null));
      }

      protected void paintComponent(Graphics graphics) {
        if (markersImage != null) {
          graphics.drawImage(markersImage, 0, 0, null);
          graphics.drawImage(textImage, rowHeader.getWidth(), 0, null);
        }
        else {
          graphics.drawImage(textImage, 0, 0, null);
        }
      }
    };

    setLayout(new BorderLayout());
    add(component);

    final Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);

    Border outsideBorder = BorderFactory.createLineBorder(borderColor, 1);
    Border insideBorder = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
  }

  /**
   * @param hideByAnyKey
   * @param x <code>x</code> coordinate in layered pane coordinate system.
   * @param y <code>y</code> coordinate in layered pane coordinate system.
   */
  @Nullable
  public static LightweightHint showEditorFragmentHintAt(Editor editor,
                                                         TextRange range,
                                                         int x,
                                                         int y,
                                                         boolean showUpward,
                                                         boolean showFolding,
                                                         boolean hideByAnyKey) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    Document document = editor.getDocument();

    int startOffset = range.getStartOffset();
    int startLine = document.getLineNumber(startOffset);
    CharSequence text = document.getCharsSequence();
    // There is a possible case that we have a situation like below:
    //    line 1
    //    line 2 <fragment start>
    //    line 3<fragment end>
    // We don't want to include 'line 2' to the target fragment then.
    boolean incrementLine = false;
    for (int offset = startOffset, max = Math.min(range.getEndOffset(), text.length()); offset < max; offset++) {
      char c = text.charAt(offset);
      incrementLine = StringUtil.isWhiteSpace(c);
      if (!incrementLine || c == '\n') {
        break;
      } 
    }
    if (incrementLine) {
      startLine++;
    } 
    
    int endLine = Math.min(document.getLineNumber(range.getEndOffset()) + 1, document.getLineCount() - 1);

    //if (editor.logicalPositionToXY(new LogicalPosition(startLine, 0)).y >= editor.logicalPositionToXY(new LogicalPosition(endLine, 0)).y) return null;
    if (startLine >= endLine) return null;

    EditorFragmentComponent fragmentComponent = createEditorFragmentComponent(editor, startLine, endLine, showFolding, true);


    if (showUpward) {
      y -= fragmentComponent.getPreferredSize().height + 10;
      y  = Math.max(0,y);
    }

    final JComponent c = editor.getComponent();
    x = SwingUtilities.convertPoint(c, new Point(-3,0), UIUtil.getRootPane(c)).x; //IDEA-68016

    Point p = new Point(x, y);
    LightweightHint hint = new MyComponentHint(fragmentComponent);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, (hideByAnyKey ? HintManager.HIDE_BY_ANY_KEY : 0) |
                                                                      HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_MOUSEOVER,
                                                     0, false, new HintHint(editor, p));
    return hint;
  }

  public static EditorFragmentComponent createEditorFragmentComponent(Editor editor,
                                                                      int startLine,
                                                                      int endLine,
                                                                      boolean showFolding, boolean showGutter) {
    final EditorEx editorEx = (EditorEx)editor;
    final Color old = editorEx.getBackgroundColor();
    Color backColor = getBackgroundColor(editor);
    editorEx.setBackgroundColor(backColor);
    EditorFragmentComponent fragmentComponent = new EditorFragmentComponent(editorEx, startLine, endLine,
                                                                            showFolding, showGutter);
    fragmentComponent.setBackground(backColor);

    editorEx.setBackgroundColor(old);
    return fragmentComponent;
  }

  @Nullable
  public static LightweightHint showEditorFragmentHint(Editor editor, TextRange range, boolean showFolding, boolean hideByAnyKey){

    JComponent editorComponent = editor.getComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) return null;
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    int x = -2;
    int y = 0;
    Point point = SwingUtilities.convertPoint(editorComponent, x, y, layeredPane);

    return showEditorFragmentHintAt(editor, range, point.x, point.y, true, showFolding, hideByAnyKey);
  }

  public static Color getBackgroundColor(Editor editor){
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    Color color = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    if (color == null){
      color = colorsScheme.getDefaultBackground();
    }
    return color;
  }

  private static class MyComponentHint extends LightweightHint {
    public MyComponentHint(JComponent component) {
      super(component);
      setForceLightweightPopup(true);
    }

    public void hide() {
      // needed for Alt-Q multiple times
      // Q: not good?
      SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            MyComponentHint.super.hide();
          }
        }
      );
    }
  }
}
