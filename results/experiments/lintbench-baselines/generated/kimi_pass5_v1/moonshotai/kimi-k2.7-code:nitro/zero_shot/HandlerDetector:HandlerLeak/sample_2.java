package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

public class HandlerDetector extends Detector implements Detector.ClassScanner {

    private static final String ANDROID_OS_HANDLER = "android.os.Handler";
    private static final String ANDROID_OS_LOOPER = "android.os.Looper";
    private static final String METHOD_GET_MAIN_LOOPER = "getMainLooper";

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "HandlerLeak",
                    "Handler reference leaks",
                    "Since this Handler is declared as an inner class, it may prevent the "
                            + "outer class from being garbage collected. If the Handler is "
                            + "using a Looper or MessageQueue for a thread other than the "
                            + "main thread, then there is no issue. If the Handler is using "
                            + "the Looper or MessageQueue of the main thread, you need to "
                            + "fix your Handler declaration, as follows: Declare the Handler "
                            + "as a static class; In the outer class, instantiate a "
                            + "WeakReference to the outer class and pass this object to your "
                            + "Handler when you instantiate the Handler; Make all references "
                            + "to members of the outer class using the WeakReference object.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_OS_HANDLER);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass node) {
        PsiClass psiClass = node.getJavaPsi();
        if (psiClass == null || psiClass instanceof PsiAnonymousClass) {
            return;
        }

        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        if (psiClass.getContainingClass() == null) {
            return;
        }

        if (context.getDriver().isSuppressed(context, ISSUE, psiClass)) {
            return;
        }

        PsiMethod[] constructors = psiClass.getConstructors();
        if (constructors.length == 0) {
            report(context, psiClass);
            return;
        }

        boolean hasMainThreadHandler = false;
        for (PsiMethod constructor : constructors) {
            PsiCodeBlock body = constructor.getBody();
            if (body == null) {
                hasMainThreadHandler = true;
                break;
            }

            PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                hasMainThreadHandler = true;
                break;
            }

            PsiStatement first = statements[0];
            if (first instanceof PsiExpressionStatement) {
                PsiExpression expr = ((PsiExpressionStatement) first).getExpression();
                if (expr instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression call = (PsiMethodCallExpression) expr;
                    if (call.isConstructorCall()) {
                        String name = call.getMethodExpression().getReferenceName();
                        if ("super".equals(name)) {
                            if (usesMainThreadLooper(call)) {
                                hasMainThreadHandler = true;
                                break;
                            }
                        } else if (!"this".equals(name)) {
                            hasMainThreadHandler = true;
                            break;
                        }
                    } else {
                        hasMainThreadHandler = true;
                        break;
                    }
                } else {
                    hasMainThreadHandler = true;
                    break;
                }
            } else {
                hasMainThreadHandler = true;
                break;
            }
        }

        if (hasMainThreadHandler) {
            report(context, psiClass);
        }
    }

    private void report(@NotNull JavaContext context, @NotNull PsiClass node) {
        context.report(
                ISSUE,
                context.getNameLocation(node),
                "This Handler class should be static or leaks may occur");
    }

    private boolean usesMainThreadLooper(@NotNull PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) {
            return true;
        }

        PsiExpression firstArg = args[0];
        PsiType firstType = firstArg.getType();
        if (firstType != null && firstType.equalsToText(ANDROID_OS_LOOPER)) {
            return isMainLooper(firstArg);
        }

        return true;
    }

    private boolean isMainLooper(@NotNull PsiExpression expression) {
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (METHOD_GET_MAIN_LOOPER.equals(methodExpression.getReferenceName())) {
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (qualifier == null) {
                    return true;
                }
                String text = qualifier.getText();
                return ANDROID_OS_LOOPER.equals(text) || "Looper".equals(text);
            }
        }
        return false;
    }
}