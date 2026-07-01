package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final String CLASS_VIEW = "android.view.View";
    private static final String CLASS_CONTEXT = "android.content.Context";
    private static final String CLASS_ATTRIBUTE_SET = "android.util.AttributeSet";

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Custom views must provide a constructor with one of the signatures used by the "
                    + "layout inflater and layout editor: `View(Context)`, `View(Context, "
                    + "AttributeSet)`, or `View(Context, AttributeSet, int)`. Without such a "
                    + "constructor the view cannot be inflated from XML.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new ViewConstructorHandler(context);
    }

    private static class ViewConstructorHandler extends UElementHandler {
        private final JavaContext context;

        ViewConstructorHandler(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitClass(@NotNull UClass node) {
            if (node.isInterface() || node.isEnum()
                    || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }

            if (CLASS_VIEW.equals(node.getQualifiedName())) {
                return;
            }

            if (!context.getEvaluator().extendsClass(node, CLASS_VIEW, false)) {
                return;
            }

            if (hasInflationConstructor(node)) {
                return;
            }

            String name = node.getName();
            context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "Custom view " + name
                            + " is missing a constructor that can be used for XML inflation; "
                            + "add one of: View(Context), View(Context, AttributeSet), or "
                            + "View(Context, AttributeSet, int)"
            );
        }

        private static boolean hasInflationConstructor(UClass cls) {
            for (PsiMethod method : cls.getMethods()) {
                if (method.isConstructor() && isInflationConstructorSignature(method)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isInflationConstructorSignature(PsiMethod method) {
            PsiParameter[] params = method.getParameterList().getParameters();
            int count = params.length;
            if (count == 0 || count > 3) {
                return false;
            }

            if (!isType(params[0], CLASS_CONTEXT)) {
                return false;
            }
            if (count == 1) {
                return true;
            }

            if (!isType(params[1], CLASS_ATTRIBUTE_SET)) {
                return false;
            }
            if (count == 2) {
                return true;
            }

            PsiType thirdType = params[2].getType();
            return thirdType != null && "int".equals(thirdType.getCanonicalText());
        }

        private static boolean isType(PsiParameter parameter, String fqName) {
            PsiType type = parameter.getType();
            return type != null && fqName.equals(type.getCanonicalText());
        }
    }
}