package com.android.tools.lint.detector.api;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public interface SourceCodeScanner extends ClassScanner {
    @Nullable
    List<String> getApplicableMethodNames();
    void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod method);
    @Nullable
    List<String> getApplicableConstructorTypes();
    void visitConstructor(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod constructor);
    @Nullable
    List<String> getApplicableReferenceNames();
    void visitReference(@NotNull JavaContext context, @NotNull UReferenceExpression node, @Nullable PsiElement referenced);
    @Nullable
    List<Class<? extends UElement>> getApplicableUastTypes();
    @NotNull
    UElementHandler createUastHandler(@NotNull JavaContext context);

    // Maybe default void visitSimpleNameReferenceExpression(...)
}