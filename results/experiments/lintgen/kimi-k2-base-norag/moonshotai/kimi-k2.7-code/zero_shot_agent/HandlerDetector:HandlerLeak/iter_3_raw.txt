package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaElementVisitor;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HandlerDetector extends Detector implements Detector.JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            HandlerDetector.class,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this Handler is declared as an inner class, it may prevent the " +
            "outer class from being garbage collected. If the Handler is " +
            "using a `Looper` or `MessageQueue` for a thread other than the " +
            "main thread, then there is no issue. If the `Handler` is using " +
            "the `Looper` or `MessageQueue` of the main thread, you need to " +
            "fix your `Handler` declaration, as follows: Declare the " +
            "`Handler` as a static class; In the outer class, instantiate a " +
            "`WeakReference` to the outer class and pass this object to your " +
            "`Handler` when you instantiate the `Handler`; Make all " +
            "references to members of the outer class using the " +
            "`WeakReference` object.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    @Nullable
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.<Class<? extends PsiElement>>singletonList(PsiClass.class);
    }

    @Override
    @Nullable
    public JavaElementVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new HandlerVisitor(context);
    }

    private static class HandlerVisitor extends JavaElementVisitor {
        private final JavaContext mContext;

        HandlerVisitor(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitClass(@NonNull PsiClass aClass) {
            if (!mContext.getEvaluator().extendsClass(aClass, "android.os.Handler", false)) {
                return;
            }

            if (aClass.getContainingClass() == null) {
                return;
            }

            if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }

            if (usesMainThreadLooper(aClass)) {
                mContext.report(ISSUE, aClass, mContext.getNameLocation(aClass),
                        "This Handler class should be static or leaks might occur");
            }
        }

        private static boolean usesMainThreadLooper(@NonNull PsiClass aClass) {
            if (aClass instanceof PsiAnonymousClass) {
                PsiElement parent = aClass.getParent();
                if (parent instanceof PsiNewExpression) {
                    PsiExpressionList argumentList = ((PsiNewExpression) parent).getArgumentList();
                    return isMainThreadLooperArgument(argumentList);
                }
                return true;
            }

            PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length == 0) {
                return true;
            }

            for (PsiMethod constructor : constructors) {
                Collection<PsiMethodCallExpression> calls = PsiTreeUtil.findChildrenOfType(
                        constructor, PsiMethodCallExpression.class);
                boolean hasDelegate = false;
                for (PsiMethodCallExpression call : calls) {
                    PsiReferenceExpression methodExpression = call.getMethodExpression();
                    String referenceName = methodExpression.getReferenceName();
                    if ("super".equals(referenceName)) {
                        hasDelegate = true;
                        if (isMainThreadLooperArgument(call.getArgumentList())) {
                            return true;
                        }
                    } else if ("this".equals(referenceName)) {
                        hasDelegate = true;
                    }
                }
                if (!hasDelegate) {
                    return true;
                }
            }

            return false;
        }

        private static boolean isMainThreadLooperArgument(@Nullable PsiExpressionList argumentList) {
            if (argumentList == null || argumentList.getExpressionCount() == 0) {
                return true;
            }

            PsiExpression firstArgument = argumentList.getExpressions()[0];
            String type = firstArgument.getType() != null
                    ? firstArgument.getType().getCanonicalText()
                    : null;

            if ("android.os.Looper".equals(type)) {
                return isMainLooper(firstArgument);
            }

            if ("android.os.MessageQueue".equals(type)) {
                return isMainLooper(firstArgument) || isMainLooperMessageQueue(firstArgument);
            }

            return true;
        }

        private static boolean isMainLooper(@NonNull PsiExpression expression) {
            if (expression instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
                PsiReferenceExpression methodExpression = call.getMethodExpression();
                return "getMainLooper".equals(methodExpression.getReferenceName());
            }
            return false;
        }

        private static boolean isMainLooperMessageQueue(@NonNull PsiExpression expression) {
            if (expression instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
                PsiReferenceExpression methodExpression = call.getMethodExpression();
                if ("getQueue".equals(methodExpression.getReferenceName())) {
                    PsiExpression qualifier = methodExpression.getQualifierExpression();
                    if (qualifier instanceof PsiMethodCallExpression) {
                        return isMainLooper(qualifier);
                    }
                }
            }
            return false;
        }
    }
}