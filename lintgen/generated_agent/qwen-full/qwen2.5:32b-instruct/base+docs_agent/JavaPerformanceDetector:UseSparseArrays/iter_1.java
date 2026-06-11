package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class JavaPerformanceDetector extends Detector implements Detector.UastScanner {

    private static final String HASH_MAP = "HashMap";
    private static final String SPARSE_ARRAY = "SparseArray";
    private static final Pattern PRIMITIVE_TYPES_PATTERN = Pattern.compile("int|long|float|double");

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("<init>");
    }

    @Nullable
    @Override
    public JavaContext createUastScanner(@NonNull Context context) {
        return new JavaPerformanceDetectorVisitor(context);
    }

    private static class JavaPerformanceDetectorVisitor extends Detector.UastScanner {

        private final JavaContext context;

        JavaPerformanceDetectorVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitMethodCallExpression(@NonNull UCallExpression node) {
            USimpleNameReferenceExpression qualifier = UastUtils.getQualifier(node);
            if (qualifier != null && HASH_MAP.equals(qualifier.getName())) {
                PsiElement parent = node.getSourcePsi();
                while (parent != null && !(parent instanceof PsiMethodCallExpression)) {
                    parent = parent.getParent();
                }
                if (parent == null) {
                    return super.visitMethodCallExpression(node);
                }

                UMethod method = node.resolve();
                String returnType = method.getReturnType().getPresentableText();

                if (PRIMITIVE_TYPES_PATTERN.matcher(returnType).find()) {
                    reportIssue(node, "Consider using SparseArray for better performance.");
                }
            }
            return super.visitMethodCallExpression(node);
        }

        private void reportIssue(@NonNull UCallExpression call, @NonNull String message) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    message
            );
        }

        public static final Issue ISSUE = Issue.create(
                "UseSparseArray",
                "HashMap can be replaced with SparseArray for better performance.",
                "For maps where the keys are of type integer, it's typically more efficient to use the Android `SparseArray` API. This check identifies scenarios where you might want to consider using `SparseArray` instead of `HashMap` for better performance.",
                Category.PERFORMANCE,
                5,
                Severity.WARNING,
                new Implementation(
                        JavaPerformanceDetector.class,
                        Scope.JAVA_FILE_SCOPE
                )
        );
    }
}