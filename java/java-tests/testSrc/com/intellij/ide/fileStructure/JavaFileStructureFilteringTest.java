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
package com.intellij.ide.fileStructure;

/**
 * @author Konstantin Bulenkov
 */
public class JavaFileStructureFilteringTest extends JavaFileStructureTestCase {
  @Override
  protected String getTestDataFolderName() {
    return "filtering";
  }

  public void testSimple()throws Exception{checkTree("f");}
  public void testReturnValue()throws Exception{checkTree("point");}
  public void testAnonymousType()throws Exception{checkTree("point");}
  public void testCamel()throws Exception{checkTree("sohe");}
  public void testCamel2()throws Exception{checkTree("soHe");}
  public void testSelectLeafFirst()throws Exception{checkTree("clear");}
  public void testSelectLeafFirst2()throws Exception{checkTree("clear");}

}
