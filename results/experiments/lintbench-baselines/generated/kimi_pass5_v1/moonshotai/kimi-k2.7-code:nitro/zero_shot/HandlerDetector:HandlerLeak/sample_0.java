package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UastTunnelException;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;

public class HandlerDetector extends Detector implements SourceCodeScanner {

    private static final String HANDLER_CLASS = "android.os.Handler";
    private static final String LOOPER_CLASS = "android.os.Looper";
    private static final String GET_MAIN_LOOPER = "getMainLooper";
    private static final String CALLBACK_CLASS = "android.os.Handler$Callback";

    private static final String ISSUE_ID = "HandlerLeak";

    private static final Implementation IMPLEMENTATION =
            new Implementation(HandlerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
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
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new HandlerVisitor(context);
    }

    private static class HandlerVisitor extends UElementHandler {
        private final JavaContext mContext;

        HandlerVisitor(@NotNull JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitClass(@NotNull UClass node) {
            PsiClass psiClass = node.getJavaPsi();
            if (psiClass == null) {
                return;
            }

            if (!mContext.getEvaluator().extendsClass(psiClass, HANDLER_CLASS, false)) {
                return;
            }

            if (!hasOuterReference(psiClass)) {
                return;
            }

            if (psiClass instanceof PsiAnonymousClass) {
                PsiElement parent = psiClass.getParent();
                if (parent instanceof PsiNewExpression) {
                    PsiExpression[] args =
                            ((PsiNewExpression) parent).getArgumentList().getExpressions();
                    if (usesMainLooper(args)) {
                        report(mContext, parent);
                    }
                }
                return;
            }

            PsiMethod[] constructors = psiClass.getConstructors();
            if (constructors.length == 0) {
                report(mContext, psiClass);
                return;
            }

            for (PsiMethod constructor : constructors) {
                if (constructorUsesMainLooper(mContext, constructor)) {
                    report(mContext, psiClass);
                    return;
                }
            }
        }
    }

    private static boolean hasOuterReference(@NotNull PsiClass psiClass) {
        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        if (PsiUtil.isTopLevelClass(psiClass)) {
            return false;
        }

        if (psiClass instanceof PsiAnonymousClass) {
            PsiElement parent = psiClass.getParent();
            if (parent instanceof PsiNewExpression) {
                return isInNonStaticContext(parent);
            }
            return true;
        }

        if (PsiUtil.isLocalClass(psiClass)) {
            return isInNonStaticContext(psiClass);
        }

        return true;
    }

    private static boolean isInNonStaticContext(@NotNull PsiElement element) {
        PsiElement current = element.getParent();
        while (current != null) {
            if (current instanceof PsiMethod) {
                return !((PsiMethod) current).hasModifierProperty(PsiModifier.STATIC);
            }
            if (current instanceof PsiField) {
                return !((PsiField) current).hasModifierProperty(PsiModifier.STATIC);
            }
            if (current instanceof PsiClassInitializer) {
                return !((PsiClassInitializer) current).isStaticInitializer();
            }
            if (current instanceof PsiClass) {
                // Reached an enclosing class body without finding a static member context.
                break;
            }
            current = current.getParent();
        }
        return true;
    }

    private static boolean constructorUsesMainLooper(
            @NotNull JavaContext context, @NotNull PsiMethod constructor) {
        Set<PsiMethod> seen = new HashSet<>();
        PsiMethod current = constructor;

        while (current != null) {
            if (!seen.add(current)) {
                return true;
            }

            if (current.getBody() == null) {
                return true;
            }

            PsiMethodCallExpression call = findFirstConstructorCall(current.getBody());
            if (call == null) {
                return true;
            }

            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier instanceof PsiSuperExpression) {
                return usesMainLooper(call);
            }

            if (qualifier instanceof PsiThisExpression) {
                PsiMethod target = (PsiMethod) call.getMethodExpression().resolve();
                if (target == null
                        || !target.isConstructor()
                        || target.getContainingClass() != current.getContainingClass()) {
                    return true;
                }
                current = target;
                continue;
            }

            return true;
        }

        return true;
    }

    private static PsiMethodCallExpression findFirstConstructorCall(
            @NotNull com.intellij.psi.PsiCodeBlock body) {
        final PsiMethodCallExpression[] result = new PsiMethodCallExpression[1];
        body.accept(
                new PsiRecursiveElementVisitor() {
                    @Override
                    public void visitMethodCallExpression(
                            @NotNull PsiMethodCallExpression expression) {
                        if (result[0] != null) {
                            return;
                        }
                        PsiExpression qualifier =
                                expression.getMethodExpression().getQualifierExpression();
                        if (qualifier instanceof PsiThisExpression
                                || qualifier instanceof PsiSuperExpression) {
                            result[0] = expression;
                            return;
                        }
                        super.visitMethodCallExpression(expression);
                    }
                });
        return result[0];
    }

    private static boolean usesMainLooper(@NotNull PsiMethodCallExpression call) {
        return usesMainLooper(call.getArgumentList().getExpressions());
    }

    private static boolean usesMainLooper(@NotNull PsiExpression[] args) {
        if (args.length == 0) {
            return true;
        }

        PsiExpression first = args[0];
        if (isGetMainLooperCall(first)) {
            return true;
        }

        PsiClass typeClass = PsiTypesUtil.getPsiClass(first.getType());
        if (typeClass != null && CALLBACK_CLASS.equals(typeClass.getQualifiedName())) {
            return true;
        }

        return false;
    }

    private static boolean isGetMainLooperCall(@NotNull PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return false;
        }

        PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
        PsiReferenceExpression methodExpression = call.getMethodExpression();

        if (!GET_MAIN_LOOPER.equals(methodExpression.getReferenceName())) {
            return false;
        }

        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return true;
        }

        PsiType qualifierType = qualifier.getType();
        if (qualifierType != null) {
            PsiClass typeClass = PsiTypesUtil.getPsiClass(qualifierType);
            return typeClass != null
                    && LOOPER_CLASS.equals(typeClass.getQualifiedName());
        }

        PsiElement resolved = qualifier.resolve();
        if (resolved instanceof PsiClass) {
            return LOOPER_CLASS.equals(((PsiClass) resolved).getQualifiedName());
        }

        return false;
    }

    private static void report(@NotNull JavaContext context, @NotNull PsiElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "This Handler class should be static or leaks may occur");
    }
}