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
package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlTagNamesIndex extends XmlIndex<Void> {

  public static Collection<VirtualFile> getFilesByTagName(String tagName, final Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, tagName, createFilter(project));
  }

  public static Collection<String> getAllTagNames(Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }

  private static final ID<String,Void> NAME = ID.create("XmlTagNames");

  public ID<String, Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<String, Void, FileContent>() {
      @NotNull
      public Map<String, Void> map(final FileContent inputData) {
        final Collection<String> tags = XsdTagNameBuilder.computeTagNames(new UnsyncByteArrayInputStream(inputData.getContent()));
        if (tags != null && !tags.isEmpty()) {
          final HashMap<String, Void> map = new HashMap<String, Void>(tags.size());
          for (String tag : tags) {
            map.put(tag, null);
          }
          return map;
        }
        else {
          return Collections.emptyMap();
        }
      }
    };
  }

  public DataExternalizer<Void> getValueExternalizer() {
    return ScalarIndexExtension.VOID_DATA_EXTERNALIZER;
  }

}
