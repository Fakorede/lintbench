package com.android.tools.lint.checks;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

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
    public UastIssueResolutionVisitor createUastScanner(@NotNull Context context) {
        return new JavaPerformanceDetectorVisitor(context);
    }

    private static class JavaPerformanceDetectorVisitor extends AbstractUastVisitor implements UastIssueResolutionVisitor {

        private final Context context;

        JavaPerformanceDetectorVisitor(Context context) {
            this.context = context;
        }

        @Override
        public boolean visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            USimpleNameReferenceExpression qualifier = UastUtils.getQualifier(call);
            if (qualifier != null && HASH_MAP.equals(qualifier.getName())) {
                PsiElement parent = call.getParent();
                while (parent != null && !(parent instanceof PsiMethodCallExpression)) {
                    parent = parent.getParent();
                }
                if (parent == null) {
                    return super.visitMethodCallExpression(call);
                }

                UCallExpression uastCall = context.getUastEquivalent(parent);
                String returnType = uastCall.resolve().getReturnType().getPresentableText();

                if (PRIMITIVE_TYPES_PATTERN.matcher(returnType).find()) {
                    reportIssue(call, "Consider using SparseArray for better performance.");
                }
            }
            return super.visitMethodCallExpression(call);
        }

        private void reportIssue(PsiMethodCallExpression call, String message) {
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