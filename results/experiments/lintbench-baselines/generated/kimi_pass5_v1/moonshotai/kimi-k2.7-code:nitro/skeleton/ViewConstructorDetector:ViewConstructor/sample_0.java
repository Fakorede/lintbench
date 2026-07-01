package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_VIEW_VIEW = "android.view.View";
    private static final String ANDROID_CONTENT_CONTEXT = "android.content.Context";
    private static final String ANDROID_UTIL_ATTRIBUTE_SET = "android.util.AttributeSet";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Custom views should provide a constructor with the signature "
                            + "<code>View(Context, AttributeSet)</code> or "
                            + "<code>View(Context, AttributeSet, int)</code> "
                            + "so that layout tools can instantiate them from XML layout files. "
                            + "Initialization that should not run in a layout editor can be guarded "
                            + "with a check for <code>View#isInEditMode()</code>.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList(ANDROID_VIEW_VIEW);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isEnum()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)
                || declaration.getName() == null) {
            return;
        }

        for (PsiMethod constructor : declaration.getConstructors()) {
            if (isXmlInflationConstructor(constructor)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                String.format(
                        "Custom view `%1$s` is missing a constructor with the signature "
                                + "View(Context, AttributeSet) or View(Context, AttributeSet, int), "
                                + "which is required for XML inflation.",
                        declaration.getName()));
    }

    private static boolean isXmlInflationConstructor(PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        int count = parameters.length;
        if (count != 2 && count != 3) {
            return false;
        }

        if (!ANDROID_CONTENT_CONTEXT.equals(parameters[0].getType().getCanonicalText())) {
            return false;
        }
        if (!ANDROID_UTIL_ATTRIBUTE_SET.equals(parameters[1].getType().getCanonicalText())) {
            return false;
        }
        if (count == 3 && !PsiType.INT.equals(parameters[2].getType())) {
            return false;
        }

        return true;
    }
}