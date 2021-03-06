/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class FloatingPointEqualityInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "floating.point.equality.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "floating.point.equality.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new FloatingPointEqualityComparisonVisitor();
  }

  private static class FloatingPointEqualityComparisonVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();

      if (!isFloatingPointType(lhs) && !isFloatingPointType(rhs)) {
        return;
      }
      if (ExpressionUtils.isZero(lhs) || ExpressionUtils.isZero(rhs)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isFloatingPointType(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return false;
      }
      return PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type);
    }
  }
}