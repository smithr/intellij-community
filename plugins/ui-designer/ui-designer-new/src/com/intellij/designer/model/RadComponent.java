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
package com.intellij.designer.model;

import com.intellij.designer.propertyTable.Property;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public abstract class RadComponent {
  private RadComponent myParent;
  private final Map<Object, Object> myClientProperties = new HashMap<Object, Object>();

  public RadComponent getRoot() {
    return myParent == null ? this : myParent.getRoot();
  }

  public final RadComponent getParent() {
    return myParent;
  }

  public final void setParent(RadComponent parent) {
    myParent = parent;
  }

  public List<RadComponent> getChildren() {
    return Collections.emptyList();
  }

  public Object[] getTreeChildren() {
    return getChildren().toArray();
  }

  public List<RadComponent> getSurfaceChildren() {
    return getChildren();
  }

  public JComponent getNativeRootComponent() {
    return null;
  }

  public Rectangle getBounds() {
    return null;
  }

  public Point convertPoint(Component component, int x, int y) {
    return null;
  }

  public Point convertPoint(int x, int y, Component component) {
    return null;
  }

  public RadLayout getLayout() {
    return null;
  }

  public RadLayoutData getLayoutData() {
    return null;
  }

  public List<Property> getProperties() {
    return null;
  }

  public final Object getClientProperty(@NotNull Object key) {
    return myClientProperties.get(key);
  }

  public final void putClientProperty(@NotNull Object key, Object value) {
    myClientProperties.put(key, value);
  }
}